package com.destinationcompass.app.presentation

import android.app.Application
import android.hardware.GeomagneticField
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CompassMetrics(
    val bearing: Float = 0f,
    val heading: Float = 0f,
    val relativeDirection: Float = 0f,
    val distanceMeters: Double? = null,
    val locationAccuracy: Float? = null,
    val isDirectionReliable: Boolean = false
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
    val isOnline = networkMonitor.isOnline
    val mapFollowMyLocation = _mapFollowMyLocation.asStateFlow()
    val mapHeadingUp = _mapHeadingUp.asStateFlow()
    val mapZoomLevel = _mapZoomLevel.asStateFlow()

    val metrics = combine(destination, locationState, compassState) { target, location, compass ->
        DirectionInput(target, location, compass)
    }.runningFold(CompassMetrics()) { previous, input ->
        val latitude = input.location.latitude
        val longitude = input.location.longitude
        val trueHeading = if (latitude != null && longitude != null) {
            val declination = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                input.location.altitudeMeters.toFloat(),
                System.currentTimeMillis()
            ).declination
            BearingCalculator.normalizeDegrees(input.compass.heading + declination)
        } else {
            input.compass.heading
        }

        val reliable = input.location.isValid &&
            latitude != null && longitude != null &&
            input.compass.isAvailable && !input.compass.calibrationRequired
        val target = input.destination
        if (target == null) {
            CompassMetrics(
                heading = trueHeading,
                locationAccuracy = input.location.accuracyMeters,
                isDirectionReliable = false
            )
        } else if (!reliable) {
            // Keep the last trustworthy target vector while GPS or magnetic data is unreliable.
            previous.copy(
                heading = trueHeading,
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
                isDirectionReliable = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CompassMetrics())

    init {
        sensors.start()
        networkMonitor.start()
        if (locations.hasPermission()) locations.start()
        viewModelScope.launch {
            locationRefreshIntervalMillis.collect(locations::setUpdateIntervalMillis)
        }
    }

    fun onLocationPermissionResult(granted: Boolean) { if (granted) locations.start() }
    fun setDestination(value: Destination) = viewModelScope.launch { preferences.setDestination(value) }
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

    override fun onCleared() {
        sensors.stop()
        locations.stop()
        networkMonitor.stop()
    }
}

private const val DEFAULT_MAP_ZOOM_LEVEL = 15f
private const val MIN_MAP_ZOOM_LEVEL = 4f
private const val MAX_MAP_ZOOM_LEVEL = 21f
