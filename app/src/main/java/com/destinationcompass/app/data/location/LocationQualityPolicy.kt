package com.destinationcompass.app.data.location

import kotlin.math.abs
import kotlin.math.max

/** Pure quality checks shared by the Baidu location callback and JVM tests. */
internal object LocationQualityPolicy {
    const val MAX_RELIABLE_ACCURACY_METERS = GPS_WEAK_SIGNAL_THRESHOLD_METERS
    private const val MAX_REASONABLE_SPEED_METERS_PER_SECOND = 55f
    private const val MIN_JUMP_ALLOWANCE_METERS = 20.0
    private const val MAX_COMPARABLE_FIX_GAP_SECONDS = 15.0

    fun isReliableAccuracy(accuracyMeters: Float): Boolean =
        accuracyMeters.isFinite() && accuracyMeters in 1f..MAX_RELIABLE_ACCURACY_METERS

    fun shouldResetTracking(elapsedSeconds: Double): Boolean =
        elapsedSeconds.isFinite() && elapsedSeconds > MAX_COMPARABLE_FIX_GAP_SECONDS

    fun isImplausibleJump(
        distanceMeters: Double,
        elapsedSeconds: Double,
        currentAccuracyMeters: Float,
        previousAccuracyMeters: Float
    ): Boolean {
        if (!distanceMeters.isFinite() || !elapsedSeconds.isFinite() || elapsedSeconds <= 0.0) return true
        // After a long signal outage the old point is no longer a safe baseline for rejecting a new fix.
        if (shouldResetTracking(elapsedSeconds)) return false
        val accuracyAllowance = max(currentAccuracyMeters, previousAccuracyMeters)
            .coerceIn(1f, MAX_RELIABLE_ACCURACY_METERS)
            .toDouble()
        val allowedDistance = max(
            MIN_JUMP_ALLOWANCE_METERS,
            MAX_REASONABLE_SPEED_METERS_PER_SECOND * elapsedSeconds + accuracyAllowance
        )
        return distanceMeters > allowedDistance
    }

    fun resolveSpeedMetersPerSecond(
        sdkSpeedKilometersPerHour: Float?,
        distanceMeters: Double,
        elapsedSeconds: Double,
        currentAccuracyMeters: Float,
        previousAccuracyMeters: Float
    ): Float {
        val uncertaintyMeters = max(3f, max(currentAccuracyMeters, previousAccuracyMeters)).toDouble()
        val displacementSpeedMetersPerSecond = if (
            distanceMeters.isFinite() && elapsedSeconds.isFinite() && elapsedSeconds > 0.0
        ) {
            ((distanceMeters - uncertaintyMeters).coerceAtLeast(0.0) / elapsedSeconds)
                .coerceIn(0.0, MAX_REASONABLE_SPEED_METERS_PER_SECOND.toDouble())
                .toFloat()
        } else {
            0f
        }
        val rawCoordinateSpeedMetersPerSecond = if (
            distanceMeters.isFinite() && elapsedSeconds.isFinite() && elapsedSeconds > 0.0
        ) {
            (distanceMeters / elapsedSeconds).toFloat()
        } else {
            0f
        }
        val sdkSpeedMetersPerSecond = sdkSpeedKilometersPerHour
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.div(3.6f)
            ?.takeIf { it <= MAX_REASONABLE_SPEED_METERS_PER_SECOND }
            ?.takeIf {
                if (displacementSpeedMetersPerSecond == 0f) {
                    false
                } else {
                    val accuracyAllowance = if (elapsedSeconds > 0.0) {
                        (uncertaintyMeters / elapsedSeconds).coerceAtMost(10.0).toFloat()
                    } else {
                        0f
                    }
                    abs(it - rawCoordinateSpeedMetersPerSecond) <= 5f + accuracyAllowance
                }
            }
        return sdkSpeedMetersPerSecond ?: displacementSpeedMetersPerSecond
    }
}
