package com.destinationcompass.app.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

/** Converts GPS/WGS-84 coordinates to the GCJ-02 mode configured for the map SDK in China. */
object CoordinateTransform {
    private const val SEMI_MAJOR_AXIS = 6_378_245.0
    private const val ECCENTRICITY_SQUARED = 0.006693421622965943

    fun wgs84ToGcj02(latitude: Double, longitude: Double): GeoPoint {
        if (outsideChina(latitude, longitude)) return GeoPoint(latitude, longitude)

        var latitudeOffset = transformLatitude(longitude - 105.0, latitude - 35.0)
        var longitudeOffset = transformLongitude(longitude - 105.0, latitude - 35.0)
        val latitudeRadians = latitude / 180.0 * PI
        var magic = sin(latitudeRadians)
        magic = 1 - ECCENTRICITY_SQUARED * magic * magic
        val sqrtMagic = sqrt(magic)
        latitudeOffset = latitudeOffset * 180.0 /
            ((SEMI_MAJOR_AXIS * (1 - ECCENTRICITY_SQUARED)) / (magic * sqrtMagic) * PI)
        longitudeOffset = longitudeOffset * 180.0 /
            (SEMI_MAJOR_AXIS / sqrtMagic * cos(latitudeRadians) * PI)
        return GeoPoint(latitude + latitudeOffset, longitude + longitudeOffset)
    }

    private fun outsideChina(latitude: Double, longitude: Double): Boolean =
        longitude !in 72.004..137.8347 || latitude !in 0.8293..55.8271

    private fun transformLatitude(x: Double, y: Double): Double {
        var result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y +
            0.1 * x * y + 0.2 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        result += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        result += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return result
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var result = 300.0 + x + 2.0 * y + 0.1 * x * x +
            0.1 * x * y + 0.1 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        result += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        result += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return result
    }
}
