package com.destinationcompass.app.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class MapPickerProximityTest {
    @Test
    fun proximityUsesTenAndFiftyMeterBoundaries() {
        assertEquals(DestinationProximity.FAR, destinationProximityForDistance(null))
        assertEquals(DestinationProximity.FAR, destinationProximityForDistance(50.01))
        assertEquals(DestinationProximity.NEAR, destinationProximityForDistance(50.0))
        assertEquals(DestinationProximity.NEAR, destinationProximityForDistance(10.01))
        assertEquals(DestinationProximity.ARRIVED, destinationProximityForDistance(10.0))
        assertEquals(DestinationProximity.ARRIVED, destinationProximityForDistance(0.0))
    }
}
