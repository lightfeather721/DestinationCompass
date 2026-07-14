package com.destinationcompass.app.utils

import com.destinationcompass.app.domain.BearingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BearingCalculatorTest {
    @Test
    fun cardinalBearingsAreCorrect() {
        assertEquals(0f, BearingCalculator.initialBearing(0.0, 0.0, 1.0, 0.0), .1f)
        assertEquals(90f, BearingCalculator.initialBearing(0.0, 0.0, 0.0, 1.0), .1f)
        assertEquals(180f, BearingCalculator.initialBearing(0.0, 0.0, -1.0, 0.0), .1f)
    }

    @Test
    fun rotationAlwaysUsesShortestPathAcrossNorth() {
        assertEquals(2f, BearingCalculator.shortestRotation(359f, 1f), .001f)
        assertEquals(-2f, BearingCalculator.shortestRotation(1f, 359f), .001f)
    }

    @Test
    fun shanghaiLandmarksHavePlausibleDistance() {
        val meters = BearingCalculator.distanceMeters(31.2397, 121.4998, 31.2304, 121.4737)
        assertTrue(meters in 2_000.0..3_500.0)
    }

    @Test
    fun directionNamesCoverIntercardinals() {
        assertEquals("北", BearingCalculator.directionName(359f))
        assertEquals("东北", BearingCalculator.directionName(45f))
        assertEquals("西南", BearingCalculator.directionName(225f))
    }

    @Test
    fun bearingCrossesInternationalDateLineOnShortestArc() {
        assertEquals(90f, BearingCalculator.initialBearing(0.0, 179.5, 0.0, -179.5), .2f)
        assertEquals(270f, BearingCalculator.initialBearing(0.0, -179.5, 0.0, 179.5), .2f)
        val distance = BearingCalculator.distanceMeters(0.0, 179.5, 0.0, -179.5)
        assertTrue(distance in 110_000.0..112_500.0)
    }
}
