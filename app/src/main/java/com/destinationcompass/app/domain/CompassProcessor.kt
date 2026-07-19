package com.destinationcompass.app.domain

import kotlin.math.exp

/** Time-based circular low-pass filter with immediate first-heading initialization. */
class CompassProcessor {
    private var initialized = false
    private var filteredHeading = 0f
    private var lastTimestampNanos = 0L

    fun process(rawHeading: Float, timestampNanos: Long): Float {
        if (!rawHeading.isFinite()) return filteredHeading
        val normalized = BearingCalculator.normalizeDegrees(rawHeading)
        if (!initialized) {
            initialized = true
            filteredHeading = normalized
            lastTimestampNanos = timestampNanos
            return filteredHeading
        }

        val elapsedSeconds = ((timestampNanos - lastTimestampNanos).coerceAtLeast(1L) / 1_000_000_000f)
            .coerceIn(MIN_DELTA_SECONDS, MAX_DELTA_SECONDS)
        lastTimestampNanos = timestampNanos
        val smoothingFactor = (1f - exp(-elapsedSeconds / FILTER_TIME_CONSTANT_SECONDS))
            .coerceIn(MIN_SMOOTHING_FACTOR, MAX_SMOOTHING_FACTOR)
        val difference = BearingCalculator.shortestRotation(filteredHeading, normalized)
        filteredHeading = BearingCalculator.normalizeDegrees(filteredHeading + difference * smoothingFactor)
        return filteredHeading
    }

    /** Convenience overload for deterministic unit tests and non-sensor callers. */
    fun process(rawHeading: Float): Float {
        val timestamp = if (lastTimestampNanos == 0L) DEFAULT_SAMPLE_NANOS else lastTimestampNanos + DEFAULT_SAMPLE_NANOS
        return process(rawHeading, timestamp)
    }

    fun reset() {
        initialized = false
        filteredHeading = 0f
        lastTimestampNanos = 0L
    }

    private companion object {
        const val FILTER_TIME_CONSTANT_SECONDS = 0.05f
        const val MIN_SMOOTHING_FACTOR = 0.12f
        const val MAX_SMOOTHING_FACTOR = 0.45f
        const val MIN_DELTA_SECONDS = 0.005f
        const val MAX_DELTA_SECONDS = 0.1f
        const val DEFAULT_SAMPLE_NANOS = 20_000_000L
    }
}
