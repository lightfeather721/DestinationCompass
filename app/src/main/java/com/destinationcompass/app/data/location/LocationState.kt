package com.destinationcompass.app.data.location

import com.destinationcompass.app.model.LocationRefreshInterval

enum class MotionState { STATIONARY, WALKING, FAST }

data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float? = null,
    val speedMetersPerSecond: Float = 0f,
    val bearingDegrees: Float? = null,
    val bearingAccuracyDegrees: Float? = null,
    val timestampMillis: Long? = null,
    val isValid: Boolean = false,
    val isLocationEnabled: Boolean = true,
    val motionState: MotionState = MotionState.WALKING,
    val updateIntervalMillis: Long = LocationRefreshInterval.DEFAULT_MILLIS,
    val errorMessage: String? = null
) {
    val hasFix: Boolean get() = latitude != null && longitude != null
    val isAccurate: Boolean get() = isValid && (accuracyMeters ?: Float.MAX_VALUE) <= 30f
}
