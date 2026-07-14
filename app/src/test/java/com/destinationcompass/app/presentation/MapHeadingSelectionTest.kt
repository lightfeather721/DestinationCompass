package com.destinationcompass.app.presentation

import com.destinationcompass.app.data.location.LocationState
import com.destinationcompass.app.data.location.MotionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapHeadingSelectionTest {
    @Test
    fun stationaryLocationAlwaysUsesDeviceHeading() {
        val state = LocationState(
            speedMetersPerSecond = 1.2f,
            bearingDegrees = 90f,
            motionState = MotionState.STATIONARY
        )

        assertFalse(shouldUseMovementHeading(state))
    }

    @Test
    fun reliableMovingLocationUsesGpsHeading() {
        val state = LocationState(
            speedMetersPerSecond = 2f,
            bearingDegrees = 90f,
            motionState = MotionState.WALKING
        )

        assertTrue(shouldUseMovementHeading(state))
    }

    @Test
    fun missingMovementBearingUsesDeviceHeading() {
        val state = LocationState(
            speedMetersPerSecond = 2f,
            bearingDegrees = null,
            motionState = MotionState.WALKING
        )

        assertFalse(shouldUseMovementHeading(state))
    }
}
