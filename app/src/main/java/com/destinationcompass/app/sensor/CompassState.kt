package com.destinationcompass.app.sensor

enum class CompassAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE, UNKNOWN }

data class CompassState(
    val heading: Float = 0f,
    val rawHeading: Float = 0f,
    val accuracy: CompassAccuracy = CompassAccuracy.UNKNOWN,
    val isAvailable: Boolean = true,
    val timestampMillis: Long? = null,
    val hasValidHeading: Boolean = false,
    val sensorType: Int? = null,
    val usesMagneticNorth: Boolean = true
) {
    val calibrationRequired: Boolean
        get() = accuracy == CompassAccuracy.LOW || accuracy == CompassAccuracy.UNRELIABLE
}
