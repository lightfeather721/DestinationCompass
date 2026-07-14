package com.destinationcompass.app.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationKalmanFilterTest {
    @Test
    fun stationarySubAccuracyDriftIsSuppressed() {
        val filter = LocationKalmanFilter()
        val first = filter.filter(33.552723, 119.107550, 8f, 1_000_000_000L, 0f)
        val second = filter.filter(33.552730, 119.107550, 8f, 2_000_000_000L, 0.1f)

        assertEquals(first.latitude, second.latitude, 0.0)
        assertEquals(first.longitude, second.longitude, 0.0)
    }

    @Test
    fun movingMeasurementAdvancesSmoothlyWithoutJumpingToRawPoint() {
        val filter = LocationKalmanFilter()
        val first = filter.filter(33.552723, 119.107550, 5f, 1_000_000_000L, 0f)
        val rawLatitude = 33.552823
        val second = filter.filter(rawLatitude, 119.107550, 5f, 2_000_000_000L, 5f)

        assertTrue(second.latitude > first.latitude)
        assertTrue(second.latitude < rawLatitude)
    }
}
