package com.destinationcompass.app.sensor

enum class CompassAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE, UNKNOWN }

data class CompassState(
    val heading: Float = 0f,
    val accuracy: CompassAccuracy = CompassAccuracy.UNKNOWN,
    val isAvailable: Boolean = true,
    val timestampMillis: Long? = null
) {
    val calibrationRequired: Boolean
        get() = accuracy == CompassAccuracy.LOW || accuracy == CompassAccuracy.UNRELIABLE
}
