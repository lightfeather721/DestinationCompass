package com.destinationcompass.app.data.location

import com.destinationcompass.app.model.LocationRefreshInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStateTest {
    @Test
    fun locationRefreshDefaultsToOneSecond() {
        assertEquals(1_000L, LocationState().updateIntervalMillis)
    }

    @Test
    fun refreshIntervalSliderCoversHalfToFiveSeconds() {
        assertEquals(500L, LocationRefreshInterval.normalize(450L))
        assertEquals(2_300L, LocationRefreshInterval.normalize(2_260L))
        assertEquals(5_000L, LocationRefreshInterval.normalize(5_100L))
    }

    @Test
    fun accuracyAboveThirtyMetersIsNotAccurate() {
        val weak = LocationState(latitude = 1.0, longitude = 1.0, accuracyMeters = 31f, isValid = false)
        val precise = LocationState(latitude = 1.0, longitude = 1.0, accuracyMeters = 5f, isValid = true)
        assertFalse(weak.isAccurate)
        assertTrue(precise.isAccurate)
    }
}
