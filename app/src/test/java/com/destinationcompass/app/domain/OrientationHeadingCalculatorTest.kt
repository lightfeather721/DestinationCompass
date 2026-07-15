package com.destinationcompass.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationHeadingCalculatorTest {
    @Test
    fun flatPortraitUsesTopEdgeOfDisplay() {
        val flatFacingEast = floatArrayOf(
            0f, 1f, 0f,
            -1f, 0f, 0f,
            0f, 0f, 1f
        )

        assertEquals(90f, OrientationHeadingCalculator.headingDegrees(flatFacingEast), 0.01f)
    }

    @Test
    fun uprightPortraitUsesDirectionThroughBackOfPhone() {
        val uprightFacingNorth = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f
        )

        assertEquals(0f, OrientationHeadingCalculator.headingDegrees(uprightFacingNorth), 0.01f)
    }

    @Test
    fun uprightPortraitNormalizesWestHeading() {
        val uprightFacingWest = floatArrayOf(
            0f, 0f, 1f,
            1f, 0f, 0f,
            0f, 1f, 0f
        )

        assertEquals(270f, OrientationHeadingCalculator.headingDegrees(uprightFacingWest), 0.01f)
    }
}
