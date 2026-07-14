package com.destinationcompass.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.destinationcompass.app.domain.BearingCalculator
import com.destinationcompass.app.model.LocationRefreshInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Baidu Location SDK lifecycle and publishes GCJ-02 coordinates.
 * Map rendering and marker animation intentionally stay in the presentation layer.
 */
class LocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(LocationState(isLocationEnabled = isLocationEnabled()))
    val state: StateFlow<LocationState> = _state.asStateFlow()

    private val client: LocationClient = LocationClient(appContext)
    private val locationFilter = LocationKalmanFilter()
    private var previousRawLocation: RawLocation? = null
    private var previousFilteredCoordinate: FilteredCoordinate? = null
    private var smoothedSpeedMetersPerSecond = 0f
    private var smoothedBearingDegrees: Float? = null
    private var selectedIntervalMillis = LocationRefreshInterval.DEFAULT_MILLIS
    private var activeProviderIntervalMillis = BAIDU_MIN_PERIODIC_INTERVAL_MILLIS
    private var stationarySamples = 0
    private var pendingOutlier: RawLocation? = null
    private var pendingOutlierSamples = 0
    private var started = false
    private var receiverRegistered = false

    private val listener = object : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation?) {
            if (location == null) return
            if (!isUsableFix(location)) {
                smoothedSpeedMetersPerSecond = 0f
                smoothedBearingDegrees = null
                _state.value = _state.value.copy(
                    isValid = false,
                    speedMetersPerSecond = 0f,
                    bearingDegrees = null,
                    errorMessage = locationError(location.locType)
                )
                return
            }
            acceptLocation(location)
        }
    }

    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val enabled = isLocationEnabled()
            if (!enabled) {
                smoothedSpeedMetersPerSecond = 0f
                smoothedBearingDegrees = null
            }
            _state.value = _state.value.copy(
                isLocationEnabled = enabled,
                isValid = enabled && _state.value.isValid,
                speedMetersPerSecond = if (enabled) _state.value.speedMetersPerSecond else 0f,
                bearingDegrees = if (enabled) _state.value.bearingDegrees else null,
                errorMessage = if (enabled) null else "系统定位服务已关闭"
            )
            if (enabled && started) restartProvider()
        }
    }

    private val staleCheck = object : Runnable {
        override fun run() {
            val current = _state.value
            val timestamp = current.timestampMillis
            if (timestamp != null && System.currentTimeMillis() - timestamp > STALE_FIX_MILLIS) {
                smoothedSpeedMetersPerSecond = 0f
                smoothedBearingDegrees = null
                _state.value = current.copy(
                    isValid = false,
                    speedMetersPerSecond = 0f,
                    bearingDegrees = null,
                    errorMessage = "定位数据已超过 15 秒未更新"
                )
            }
            if (started) mainHandler.postDelayed(this, STALE_CHECK_INTERVAL_MILLIS)
        }
    }

    init {
        client.registerLocationListener(listener)
    }

    fun hasPermission(): Boolean =
        hasFinePermission() ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun setUpdateIntervalMillis(intervalMillis: Long) {
        val normalized = LocationRefreshInterval.normalize(intervalMillis)
        if (normalized == selectedIntervalMillis) return
        selectedIntervalMillis = normalized
        _state.value = _state.value.copy(updateIntervalMillis = normalized)
        if (started) updateProviderInterval(providerIntervalFor(_state.value.motionState))
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!hasPermission()) {
            _state.value = _state.value.copy(isValid = false, errorMessage = "需要定位权限")
            return
        }
        val enabled = isLocationEnabled()
        _state.value = _state.value.copy(
            isLocationEnabled = enabled,
            errorMessage = if (enabled) null else "系统定位服务已关闭"
        )
        if (!enabled) return

        if (!started) {
            started = true
            registerProviderReceiver()
            mainHandler.post(staleCheck)
        }
        activeProviderIntervalMillis = providerIntervalFor(_state.value.motionState)
        client.locOption = buildLocationOptions(activeProviderIntervalMillis)
        if (client.isStarted) {
            client.restart()
        } else {
            client.start()
        }
        client.requestLocation()
    }

    private fun buildLocationOptions(intervalMillis: Long) = LocationClientOption().apply {
        locationMode = LocationClientOption.LocationMode.Hight_Accuracy
        coorType = "gcj02"
        scanSpan = intervalMillis.toInt()
        setOpenGnss(true)
        isLocationNotify = false
        firstLocType = LocationClientOption.FirstLocType.ACCURACY_IN_FIRST_LOC
        setNeedDeviceDirect(true)
        setIsNeedAltitude(true)
        setIsNeedAddress(false)
        setOnceLocation(false)
        setIgnoreKillProcess(false)
        if (_state.value.motionState == MotionState.STATIONARY) {
            setOpenAutoNotifyMode(
                BAIDU_MIN_PERIODIC_INTERVAL_MILLIS.toInt(),
                STATIONARY_WAKEUP_DISTANCE_METERS,
                LocationClientOption.LOC_SENSITIVITY_HIGHT
            )
        }
    }

    private fun acceptLocation(candidate: BDLocation) {
        val elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        val accuracy = candidate.radius.coerceAtLeast(1f)
        if (!LocationQualityPolicy.isReliableAccuracy(accuracy)) {
            smoothedSpeedMetersPerSecond = 0f
            smoothedBearingDegrees = null
            _state.value = _state.value.copy(
                accuracyMeters = accuracy,
                speedMetersPerSecond = 0f,
                bearingDegrees = null,
                timestampMillis = System.currentTimeMillis(),
                isValid = false,
                isLocationEnabled = isLocationEnabled(),
                errorMessage = "GPS 信号较弱"
            )
            return
        }
        var previous = previousRawLocation
        val raw = RawLocation(
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            accuracyMeters = accuracy,
            elapsedRealtimeNanos = elapsedRealtimeNanos
        )
        var elapsedSeconds = previous?.let {
            (elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1_000_000_000.0
        }
        var distanceMeters = previous?.let {
            BearingCalculator.distanceMeters(
                previous.latitude,
                previous.longitude,
                raw.latitude,
                raw.longitude
            )
        }
        val longFixGap = elapsedSeconds?.let(LocationQualityPolicy::shouldResetTracking) == true
        val closeToLastAccepted = previous != null && distanceMeters != null &&
            distanceMeters <= maxOf(
                MIN_RECOVERY_PROXIMITY_METERS,
                maxOf(accuracy, previous.accuracyMeters) * 2.0
            )
        val implausibleFromLastAccepted = previous != null && elapsedSeconds != null && distanceMeters != null &&
            if (longFixGap) {
                !closeToLastAccepted
            } else {
                LocationQualityPolicy.isImplausibleJump(
                distanceMeters = distanceMeters,
                elapsedSeconds = elapsedSeconds,
                currentAccuracyMeters = accuracy,
                previousAccuracyMeters = previous.accuracyMeters
            )
            }
        val needsOutlierConfirmation = implausibleFromLastAccepted ||
            (pendingOutlier != null && !closeToLastAccepted)
        if (needsOutlierConfirmation) {
            if (!confirmOutlierCluster(raw)) {
                smoothedSpeedMetersPerSecond = 0f
                smoothedBearingDegrees = null
                _state.value = _state.value.copy(
                    accuracyMeters = accuracy,
                    speedMetersPerSecond = 0f,
                    bearingDegrees = null,
                    timestampMillis = System.currentTimeMillis(),
                    isValid = false,
                    errorMessage = "定位数据波动，已忽略异常位置"
                )
                return
            }
            resetTrackingForRecovery()
            previous = null
            elapsedSeconds = null
            distanceMeters = null
        } else {
            pendingOutlier = null
            pendingOutlierSamples = 0
            if (longFixGap) {
                resetTrackingForRecovery()
                previous = null
                elapsedSeconds = null
                distanceMeters = null
            }
        }

        val speed = estimateSpeed(candidate, accuracy, previous, elapsedSeconds, distanceMeters)
        smoothedSpeedMetersPerSecond = if (previous == null) {
            speed
        } else {
            smoothedSpeedMetersPerSecond * SPEED_SMOOTHING_RETAINED + speed * SPEED_SMOOTHING_NEW
        }
        val motion = classifyMotion(smoothedSpeedMetersPerSecond, speed)
        if (motion == MotionState.STATIONARY) {
            smoothedSpeedMetersPerSecond = 0f
        }
        val filtered = locationFilter.filter(
            latitude = raw.latitude,
            longitude = raw.longitude,
            accuracyMeters = accuracy,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            speedMetersPerSecond = smoothedSpeedMetersPerSecond
        )
        val priorFiltered = previousFilteredCoordinate
        val traveledMeters = priorFiltered?.let {
            BearingCalculator.distanceMeters(it.latitude, it.longitude, filtered.latitude, filtered.longitude)
        } ?: 0.0
        val sdkDirection = candidate.direction.takeIf {
            it.isFinite() && it >= 0f &&
                smoothedSpeedMetersPerSecond >= MIN_RELIABLE_BEARING_SPEED_METERS_PER_SECOND
        }
        val rawBearing = sdkDirection ?: when {
            smoothedSpeedMetersPerSecond < MIN_RELIABLE_BEARING_SPEED_METERS_PER_SECOND -> null
            priorFiltered != null && traveledMeters >= MIN_BEARING_DISTANCE_METERS -> BearingCalculator.initialBearing(
                priorFiltered.latitude,
                priorFiltered.longitude,
                filtered.latitude,
                filtered.longitude
            )
            else -> null
        }
        if (rawBearing == null && motion == MotionState.STATIONARY) smoothedBearingDegrees = null
        val movementBearing = rawBearing?.let { smoothBearing(it, smoothedSpeedMetersPerSecond) }

        previousRawLocation = raw
        previousFilteredCoordinate = filtered
        val enabled = isLocationEnabled()
        _state.value = LocationState(
            latitude = filtered.latitude,
            longitude = filtered.longitude,
            altitudeMeters = candidate.altitude,
            accuracyMeters = accuracy,
            speedMetersPerSecond = smoothedSpeedMetersPerSecond,
            bearingDegrees = movementBearing,
            bearingAccuracyDegrees = null,
            timestampMillis = System.currentTimeMillis(),
            isValid = enabled && hasFinePermission(),
            isLocationEnabled = enabled,
            motionState = motion,
            updateIntervalMillis = selectedIntervalMillis,
            errorMessage = when {
                !hasFinePermission() -> "未开启精确位置权限"
                else -> null
            }
        )
        updateProviderInterval(providerIntervalFor(motion))
    }

    private fun estimateSpeed(
        candidate: BDLocation,
        accuracyMeters: Float,
        previous: RawLocation?,
        elapsedSeconds: Double?,
        distanceMeters: Double?
    ): Float {
        if (previous == null) return 0f
        val sdkSpeed = candidate.speed.takeIf {
            candidate.hasSpeed() &&
                (candidate.locType == BDLocation.TypeGpsLocation || candidate.locType == BDLocation.TypeGnssLocation)
        }
        return LocationQualityPolicy.resolveSpeedMetersPerSecond(
            sdkSpeedKilometersPerHour = sdkSpeed,
            distanceMeters = distanceMeters ?: 0.0,
            elapsedSeconds = elapsedSeconds ?: 0.0,
            currentAccuracyMeters = accuracyMeters,
            previousAccuracyMeters = previous.accuracyMeters
        )
    }

    private fun classifyMotion(smoothedSpeedMetersPerSecond: Float, rawSpeedMetersPerSecond: Float): MotionState {
        val classificationSpeed = if (
            _state.value.motionState == MotionState.STATIONARY &&
            rawSpeedMetersPerSecond >= EXIT_STATIONARY_SPEED_METERS_PER_SECOND
        ) {
            rawSpeedMetersPerSecond
        } else {
            smoothedSpeedMetersPerSecond
        }
        val instant = when {
            classificationSpeed < STATIONARY_SPEED_METERS_PER_SECOND -> MotionState.STATIONARY
            classificationSpeed < FAST_SPEED_METERS_PER_SECOND -> MotionState.WALKING
            else -> MotionState.FAST
        }
        if (instant == MotionState.STATIONARY) {
            stationarySamples++
            return if (stationarySamples >= STATIONARY_CONFIRMATION_SAMPLES) {
                MotionState.STATIONARY
            } else {
                _state.value.motionState.takeUnless { it == MotionState.STATIONARY } ?: MotionState.WALKING
            }
        }
        stationarySamples = 0
        return instant
    }

    private fun confirmOutlierCluster(candidate: RawLocation): Boolean {
        val pending = pendingOutlier
        val consistent = pending != null && run {
            val elapsedSeconds = (candidate.elapsedRealtimeNanos - pending.elapsedRealtimeNanos) / 1_000_000_000.0
            val distanceMeters = BearingCalculator.distanceMeters(
                pending.latitude,
                pending.longitude,
                candidate.latitude,
                candidate.longitude
            )
            elapsedSeconds in MIN_OUTLIER_SAMPLE_GAP_SECONDS..MAX_OUTLIER_SAMPLE_GAP_SECONDS &&
                !LocationQualityPolicy.isImplausibleJump(
                distanceMeters = distanceMeters,
                elapsedSeconds = elapsedSeconds,
                currentAccuracyMeters = candidate.accuracyMeters,
                previousAccuracyMeters = pending.accuracyMeters
            )
        }
        pendingOutlierSamples = if (consistent) pendingOutlierSamples + 1 else 1
        pendingOutlier = candidate
        return pendingOutlierSamples >= OUTLIER_CONFIRMATION_SAMPLES
    }

    private fun resetTrackingForRecovery() {
        previousRawLocation = null
        previousFilteredCoordinate = null
        smoothedSpeedMetersPerSecond = 0f
        smoothedBearingDegrees = null
        stationarySamples = 0
        pendingOutlier = null
        pendingOutlierSamples = 0
        locationFilter.reset()
    }

    private fun providerIntervalFor(motionState: MotionState): Long = when (motionState) {
        MotionState.STATIONARY -> LocationRefreshInterval.MAX_MILLIS
        MotionState.WALKING, MotionState.FAST -> selectedIntervalMillis.coerceAtLeast(BAIDU_MIN_PERIODIC_INTERVAL_MILLIS)
    }

    private fun updateProviderInterval(intervalMillis: Long) {
        if (!started || intervalMillis == activeProviderIntervalMillis) return
        activeProviderIntervalMillis = intervalMillis
        mainHandler.post(::restartProvider)
    }

    private fun restartProvider() {
        if (!started || !hasPermission() || !isLocationEnabled()) return
        client.locOption = buildLocationOptions(activeProviderIntervalMillis)
        if (client.isStarted) client.restart() else client.start()
    }

    private fun smoothBearing(rawBearing: Float, speedMetersPerSecond: Float): Float {
        val normalized = BearingCalculator.normalizeDegrees(rawBearing)
        val previous = smoothedBearingDegrees
        if (previous == null) {
            smoothedBearingDegrees = normalized
            return normalized
        }
        val alpha = when {
            speedMetersPerSecond >= 8f -> 0.72f
            speedMetersPerSecond >= 3f -> 0.52f
            else -> 0.30f
        }
        val smoothed = BearingCalculator.normalizeDegrees(
            previous + BearingCalculator.shortestRotation(previous, normalized) * alpha
        )
        smoothedBearingDegrees = smoothed
        return smoothed
    }

    private fun isUsableFix(location: BDLocation): Boolean {
        val supportedType = location.locType == BDLocation.TypeGpsLocation ||
            location.locType == BDLocation.TypeGnssLocation ||
            location.locType == BDLocation.TypeNetWorkLocation
        return supportedType &&
            location.latitude in -90.0..90.0 &&
            location.longitude in -180.0..180.0 &&
            location.radius in 0f..MAX_ACCEPTED_ACCURACY_METERS
    }

    private fun locationError(type: Int): String = when (type) {
        BDLocation.TypeNetWorkException -> "网络不可用，正在等待 GPS 定位"
        BDLocation.TypeCriteriaException -> "定位参数或权限不可用"
        BDLocation.TypeServerError -> "百度定位服务暂时不可用"
        else -> "暂时无法获取当前位置（错误码 $type）"
    }

    private fun isLocationEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    private fun registerProviderReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(providerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(providerReceiver, filter)
        }
        receiverRegistered = true
    }

    fun stop() {
        started = false
        mainHandler.removeCallbacks(staleCheck)
        client.stop()
        client.unRegisterLocationListener(listener)
        previousRawLocation = null
        previousFilteredCoordinate = null
        smoothedSpeedMetersPerSecond = 0f
        smoothedBearingDegrees = null
        stationarySamples = 0
        pendingOutlier = null
        pendingOutlierSamples = 0
        locationFilter.reset()
        if (receiverRegistered) {
            appContext.unregisterReceiver(providerReceiver)
            receiverRegistered = false
        }
    }

    private data class RawLocation(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val elapsedRealtimeNanos: Long
    )

    private companion object {
        const val BAIDU_MIN_PERIODIC_INTERVAL_MILLIS = 1_000L
        const val MAX_ACCEPTED_ACCURACY_METERS = 5_000f
        const val MIN_RELIABLE_BEARING_SPEED_METERS_PER_SECOND = 1.0f
        const val MIN_BEARING_DISTANCE_METERS = 3.0
        const val STATIONARY_SPEED_METERS_PER_SECOND = 0.45f
        const val EXIT_STATIONARY_SPEED_METERS_PER_SECOND = 0.65f
        const val FAST_SPEED_METERS_PER_SECOND = 3.2f
        const val STATIONARY_WAKEUP_DISTANCE_METERS = 3
        const val STATIONARY_CONFIRMATION_SAMPLES = 3
        const val OUTLIER_CONFIRMATION_SAMPLES = 3
        const val MIN_RECOVERY_PROXIMITY_METERS = 20.0
        const val MIN_OUTLIER_SAMPLE_GAP_SECONDS = 0.05
        const val MAX_OUTLIER_SAMPLE_GAP_SECONDS = 5.0
        const val SPEED_SMOOTHING_RETAINED = 0.62f
        const val SPEED_SMOOTHING_NEW = 0.38f
        const val STALE_FIX_MILLIS = 15_000L
        const val STALE_CHECK_INTERVAL_MILLIS = 5_000L
    }
}
