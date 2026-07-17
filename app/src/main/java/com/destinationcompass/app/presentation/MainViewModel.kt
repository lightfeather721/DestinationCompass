package com.destinationcompass.app.presentation

import android.app.Application
import android.hardware.GeomagneticField
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.destinationcompass.app.data.database.AppPreferences
import com.destinationcompass.app.data.location.LocationRepository
import com.destinationcompass.app.data.location.LocationState
import com.destinationcompass.app.data.network.NetworkMonitor
import com.destinationcompass.app.domain.BearingCalculator
import com.destinationcompass.app.model.Destination
import com.destinationcompass.app.model.DistanceUnit
import com.destinationcompass.app.model.LocationRefreshInterval
import com.destinationcompass.app.model.ThemeMode
import com.destinationcompass.app.sensor.CompassSensorManager
import com.destinationcompass.app.sensor.CompassState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CompassMetrics(
    val bearing: Float = 0f,
    val heading: Float = 0f,
    val relativeDirection: Float = 0f,
    val distanceMeters: Double? = null,
    val locationAccuracy: Float? = null,
    val isDirectionReliable: Boolean = false,
    val hasTargetDirection: Boolean = false
)

data class CompassUiState(
    val isAvailable: Boolean = true,
    val calibrationRequired: Boolean = false,
    val hasHeading: Boolean = false,
    val usesMagneticNorth: Boolean = true
)

private data class DirectionInput(
    val destination: Destination?,
    val location: LocationState,
    val compass: CompassState
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val sensors = CompassSensorManager(application)
    private val locations = LocationRepository(application)
    private val networkMonitor = NetworkMonitor(application)
    private val _mapFollowMyLocation = MutableStateFlow(false)
    private val _mapHeadingUp = MutableStateFlow(false)
    private val _mapZoomLevel = MutableStateFlow(DEFAULT_MAP_ZOOM_LEVEL)
    private var cachedDeclination = 0f
    private var declinationLatitude: Double? = null
    private var declinationLongitude: Double? = null
    private var declinationAltitude = 0.0
    private var declinationTimestampMillis = 0L
    private var locationStartJob: Job? = null
    private var lastCompassDebugLogMillis = 0L
    private val userPreferences = preferences.preferences.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val destination = userPreferences.map { it?.destination }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val favorites = userPreferences.map { it?.favorites.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val themeMode = userPreferences.map { it?.themeMode ?: ThemeMode.SYSTEM }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val distanceUnit = userPreferences.map { it?.distanceUnit ?: DistanceUnit.KILOMETERS }.stateIn(viewModelScope, SharingStarted.Eagerly, DistanceUnit.KILOMETERS)
    val locationRefreshIntervalMillis = userPreferences.map {
        it?.locationRefreshIntervalMillis ?: LocationRefreshInterval.DEFAULT_MILLIS
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocationRefreshInterval.DEFAULT_MILLIS)
    val sensorAvailable = sensors.available
    val locationState = locations.state
    val compassState = sensors.state
    val compassUiState = compassState.map {
        CompassUiState(
            isAvailable = it.isAvailable,
            calibrationRequired = it.calibrationRequired,
            hasHeading = it.hasValidHeading,
            usesMagneticNorth = it.usesMagneticNorth
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, CompassUiState(isAvailable = sensors.available))
    val isOnline = networkMonitor.isOnline
    val mapFollowMyLocation = _mapFollowMyLocation.asStateFlow()
    val mapHeadingUp = _mapHeadingUp.asStateFlow()
    val mapZoomLevel = _mapZoomLevel.asStateFlow()

    val metrics = combine(destination, locationState, compassState) { target, location, compass ->
        DirectionInput(target, location, compass)
    }.runningFold(CompassMetrics()) { previous, input ->
        val latitude = input.location.latitude
        val longitude = input.location.longitude
        val declination = if (input.compass.usesMagneticNorth) declinationFor(input.location) else 0f
        val trueHeading = BearingCalculator.normalizeDegrees(input.compass.heading + declination)

        logCompassDiagnostics(input.compass.heading, trueHeading, declination, input.location)
        val hasHeading = input.compass.hasValidHeading && input.compass.isAvailable
        val hasAbsoluteHeading = hasHeading && input.compass.usesMagneticNorth
        val hasReliableLocation = input.location.isValid && latitude != null && longitude != null
        val target = input.destination
        if (target == null) {
            CompassMetrics(
                heading = trueHeading,
                relativeDirection = BearingCalculator.shortestRotation(trueHeading, 0f),
                locationAccuracy = input.location.accuracyMeters,
                isDirectionReliable = false
            )
        } else if (!hasReliableLocation) {
            // Keep the last trustworthy target bearing while GPS is unavailable, but continue
            // applying live device heading so an existing arrow never freezes on the dial.
            previous.copy(
                heading = trueHeading,
                relativeDirection = if (previous.hasTargetDirection && hasHeading) {
                    BearingCalculator.shortestRotation(trueHeading, previous.bearing)
                } else {
                    previous.relativeDirection
                },
                locationAccuracy = input.location.accuracyMeters,
                isDirectionReliable = false
            )
        } else {
            val bearing = BearingCalculator.initialBearing(
                latitude,
                longitude,
                target.latitude,
                target.longitude
            )
            CompassMetrics(
                bearing = bearing,
                heading = trueHeading,
                relativeDirection = BearingCalculator.shortestRotation(trueHeading, bearing),
                distanceMeters = BearingCalculator.distanceMeters(
                    latitude,
                    longitude,
                    target.latitude,
                    target.longitude
                ),
                locationAccuracy = input.location.accuracyMeters,
                // Sensor accuracy controls the calibration notice, never direction visibility.
                isDirectionReliable = hasAbsoluteHeading,
                hasTargetDirection = hasAbsoluteHeading
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CompassMetrics())

    init {
        // Baidu LocationClient service/auth/native initialization stays off the main thread.
        // MainActivity owns foreground sensor registration through onResume/onPause.
        networkMonitor.start()
        startLocationUpdatesIfPermitted()
        viewModelScope.launch {
            locationRefreshIntervalMillis.collect(locations::setUpdateIntervalMillis)
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) startLocationUpdatesIfPermitted()
    }
    fun startCompass() = sensors.start()
    fun stopCompass() = sensors.stop()
    fun setDestination(value: Destination) = viewModelScope.launch { preferences.setDestination(value) }
    fun clearDestination() = viewModelScope.launch { preferences.clearDestination() }
    fun addFavorite(value: Destination) = viewModelScope.launch {
        val list = favorites.value.filterNot { it.id == value.id } + value
        preferences.setFavorites(list)
    }
    fun updateFavorite(value: Destination) = viewModelScope.launch {
        preferences.setFavorites(favorites.value.map { if (it.id == value.id) value else it })
    }
    fun deleteFavorite(id: String) = viewModelScope.launch { preferences.setFavorites(favorites.value.filterNot { it.id == id }) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setTheme(mode) }
    fun setUnit(unit: DistanceUnit) = viewModelScope.launch { preferences.setUnit(unit) }
    fun setLocationRefreshInterval(intervalMillis: Long) = viewModelScope.launch {
        preferences.setLocationRefreshInterval(intervalMillis)
    }
    fun setMapFollowMyLocation(enabled: Boolean) {
        _mapFollowMyLocation.value = enabled
        if (!enabled) _mapHeadingUp.value = false
    }
    fun setMapHeadingUp(enabled: Boolean) {
        _mapHeadingUp.value = enabled
    }
    fun setMapZoomLevel(zoomLevel: Float) {
        if (zoomLevel.isFinite()) {
            _mapZoomLevel.value = zoomLevel.coerceIn(MIN_MAP_ZOOM_LEVEL, MAX_MAP_ZOOM_LEVEL)
        }
    }

    private fun declinationFor(location: LocationState): Float {
        val latitude = location.latitude ?: return 0f
        val longitude = location.longitude ?: return 0f
        val altitude = location.altitudeMeters
        val now = System.currentTimeMillis()
        val needsRefresh = declinationLatitude == null ||
            abs(latitude - (declinationLatitude ?: latitude)) > 0.0001 ||
            abs(longitude - (declinationLongitude ?: longitude)) > 0.0001 ||
            abs(altitude - declinationAltitude) > 10.0 ||
            now - declinationTimestampMillis >= DECLINATION_REFRESH_MILLIS
        if (needsRefresh) {
            cachedDeclination = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitude.toFloat(),
                now
            ).declination
            declinationLatitude = latitude
            declinationLongitude = longitude
            declinationAltitude = altitude
            declinationTimestampMillis = now
        }
        return cachedDeclination
    }

    private fun startLocationUpdatesIfPermitted() {
        if (!locations.hasPermission() || locationStartJob?.isActive == true) return
        locationStartJob = viewModelScope.launch(Dispatchers.IO) {
            locations.start()
        }
    }

    private fun logCompassDiagnostics(
        magneticHeading: Float,
        trueHeading: Float,
        declination: Float,
        location: LocationState
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCompassDebugLogMillis < COMPASS_DEBUG_LOG_INTERVAL_MILLIS) return
        lastCompassDebugLogMillis = now
        Log.d(
            COMPASS_DEBUG_TAG,
            "magneticHeading=$magneticHeading trueHeading=$trueHeading declination=$declination hasLocation=${location.hasFix} validLocation=${location.isValid}"
        )
    }

    override fun onCleared() {
        locationStartJob?.cancel()
        sensors.stop()
        locations.stop()
        networkMonitor.stop()
    }
}

private const val DEFAULT_MAP_ZOOM_LEVEL = 15f
private const val MIN_MAP_ZOOM_LEVEL = 4f
private const val MAX_MAP_ZOOM_LEVEL = 21f
private const val DECLINATION_REFRESH_MILLIS = 60_000L
private const val COMPASS_DEBUG_LOG_INTERVAL_MILLIS = 500L
private const val COMPASS_DEBUG_TAG = "CompassDebug"
