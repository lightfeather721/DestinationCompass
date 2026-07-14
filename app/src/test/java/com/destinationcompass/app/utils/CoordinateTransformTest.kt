package com.destinationcompass.app.utils

import com.destinationcompass.app.domain.BearingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateTransformTest {
    @Test
    fun huaianGpsCoordinateMovesToGcj02CoordinateSystem() {
        val source = GeoPoint(33.552723, 119.10755)
        val converted = CoordinateTransform.wgs84ToGcj02(source.latitude, source.longitude)

        assertTrue(converted.latitude < source.latitude)
        assertTrue(converted.longitude > source.longitude)
        val shift = BearingCalculator.distanceMeters(
            source.latitude, source.longitude, converted.latitude, converted.longitude
        )
        assertTrue(shift in 400.0..700.0)
    }

    @Test
    fun coordinatesOutsideChinaRemainUnchanged() {
        val converted = CoordinateTransform.wgs84ToGcj02(37.7749, -122.4194)
        assertEquals(37.7749, converted.latitude, 0.0)
        assertEquals(-122.4194, converted.longitude, 0.0)
    }
}
