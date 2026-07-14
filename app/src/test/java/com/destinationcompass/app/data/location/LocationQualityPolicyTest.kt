package com.destinationcompass.app.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQualityPolicyTest {
    @Test
    fun weakAccuracyIsNotReliable() {
        assertTrue(LocationQualityPolicy.isReliableAccuracy(5f))
        assertFalse(LocationQualityPolicy.isReliableAccuracy(80f))
    }

    @Test
    fun oneSecondHundredMeterJumpIsRejected() {
        assertTrue(
            LocationQualityPolicy.isImplausibleJump(
                distanceMeters = 100.0,
                elapsedSeconds = 1.0,
                currentAccuracyMeters = 5f,
                previousAccuracyMeters = 5f
            )
        )
    }

    @Test
    fun reliableFixCanRecoverAfterLongSignalOutage() {
        assertFalse(
            LocationQualityPolicy.isImplausibleJump(
                distanceMeters = 2_000.0,
                elapsedSeconds = 20.0,
                currentAccuracyMeters = 5f,
                previousAccuracyMeters = 5f
            )
        )
    }

    @Test
    fun coordinateNoiseDoesNotBecomeSpeed() {
        assertEquals(
            0f,
            LocationQualityPolicy.resolveSpeedMetersPerSecond(
                sdkSpeedKilometersPerHour = null,
                distanceMeters = 7.0,
                elapsedSeconds = 1.0,
                currentAccuracyMeters = 8f,
                previousAccuracyMeters = 6f
            ),
            0f
        )
    }

    @Test
    fun impossibleSdkSpeedFallsBackToDisplacement() {
        val speed = LocationQualityPolicy.resolveSpeedMetersPerSecond(
            sdkSpeedKilometersPerHour = 900f,
            distanceMeters = 15.0,
            elapsedSeconds = 2.0,
            currentAccuracyMeters = 5f,
            previousAccuracyMeters = 5f
        )

        assertEquals(5f, speed, 0.001f)
    }

    @Test
    fun highSdkSpeedIsRejectedWhenPositionIsStationary() {
        val speed = LocationQualityPolicy.resolveSpeedMetersPerSecond(
            sdkSpeedKilometersPerHour = 100f,
            distanceMeters = 2.0,
            elapsedSeconds = 1.0,
            currentAccuracyMeters = 5f,
            previousAccuracyMeters = 5f
        )

        assertEquals(0f, speed, 0f)
    }

    @Test
    fun moderateSdkSpeedIsRejectedInsideAccuracyEnvelope() {
        val speed = LocationQualityPolicy.resolveSpeedMetersPerSecond(
            sdkSpeedKilometersPerHour = 50f,
            distanceMeters = 0.0,
            elapsedSeconds = 1.0,
            currentAccuracyMeters = 30f,
            previousAccuracyMeters = 30f
        )

        assertEquals(0f, speed, 0f)
    }

    @Test
    fun fourPointThreeKilometersPerHourIsZeroWhileStationary() {
        val speed = LocationQualityPolicy.resolveSpeedMetersPerSecond(
            sdkSpeedKilometersPerHour = 4.3f,
            distanceMeters = 2.0,
            elapsedSeconds = 1.0,
            currentAccuracyMeters = 5f,
            previousAccuracyMeters = 5f
        )

        assertEquals(0f, speed, 0f)
    }

    @Test
    fun walkingSpeedIsKeptWhenMovementExceedsAccuracyEnvelope() {
        val speed = LocationQualityPolicy.resolveSpeedMetersPerSecond(
            sdkSpeedKilometersPerHour = 4.3f,
            distanceMeters = 5.0,
            elapsedSeconds = 1.0,
            currentAccuracyMeters = 3f,
            previousAccuracyMeters = 3f
        )

        assertEquals(4.3f / 3.6f, speed, 0.001f)
    }
}
