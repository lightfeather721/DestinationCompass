package com.destinationcompass.app.domain

import kotlin.math.abs

/** Adaptive circular low-pass filter: stable for small noise, responsive to deliberate turns. */
class CompassProcessor {
    private var initialized = false
    private var filteredHeading = 0f

    fun process(rawHeading: Float): Float {
        val normalized = BearingCalculator.normalizeDegrees(rawHeading)
        if (!initialized) {
            initialized = true
            filteredHeading = normalized
            return filteredHeading
        }

        val delta = BearingCalculator.shortestRotation(filteredHeading, normalized)
        val alpha = when {
            abs(delta) < 2f -> .07f
            abs(delta) < 12f -> .16f
            abs(delta) < 35f -> .34f
            else -> .62f
        }
        filteredHeading = BearingCalculator.normalizeDegrees(filteredHeading + delta * alpha)
        return filteredHeading
    }

    fun reset() {
        initialized = false
        filteredHeading = 0f
    }
}
