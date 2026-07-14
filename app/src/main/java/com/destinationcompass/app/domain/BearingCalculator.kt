package com.destinationcompass.app.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object BearingCalculator {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun initialBearing(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Float {
        val fromLat = Math.toRadians(fromLatitude)
        val toLat = Math.toRadians(toLatitude)
        val deltaLon = Math.toRadians(normalizeLongitudeDelta(toLongitude - fromLongitude))
        val y = sin(deltaLon) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(deltaLon)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)).toFloat())
    }

    fun distanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double
    ): Double {
        val deltaLat = Math.toRadians(toLatitude - fromLatitude)
        val deltaLon = Math.toRadians(normalizeLongitudeDelta(toLongitude - fromLongitude))
        val fromLat = Math.toRadians(fromLatitude)
        val toLat = Math.toRadians(toLatitude)
        val a = sin(deltaLat / 2).pow(2) + cos(fromLat) * cos(toLat) * sin(deltaLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
    }

    fun normalizeDegrees(value: Float): Float = (value % 360f + 360f) % 360f

    fun shortestRotation(from: Float, to: Float): Float = ((to - from + 540f) % 360f) - 180f

    fun directionName(degrees: Float): String {
        val names = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        return names[floor((normalizeDegrees(degrees) + 22.5f) / 45f).toInt() % 8]
    }

    fun walkingMinutes(distanceMeters: Double): Int = (distanceMeters / 80.0).roundToInt().coerceAtLeast(1)

    fun destinationPoint(latitude: Double, longitude: Double, bearing: Float, distanceMeters: Double): Pair<Double, Double> {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val bearingRadians = Math.toRadians(bearing.toDouble())
        val latitudeRadians = Math.toRadians(latitude)
        val longitudeRadians = Math.toRadians(longitude)
        val destinationLatitude = asin(
            sin(latitudeRadians) * cos(angularDistance) +
                cos(latitudeRadians) * sin(angularDistance) * cos(bearingRadians)
        )
        val destinationLongitude = longitudeRadians + atan2(
            sin(bearingRadians) * sin(angularDistance) * cos(latitudeRadians),
            cos(angularDistance) - sin(latitudeRadians) * sin(destinationLatitude)
        )
        return Math.toDegrees(destinationLatitude) to Math.toDegrees(destinationLongitude)
    }

    private fun normalizeLongitudeDelta(delta: Double): Double = ((delta + 540.0) % 360.0) - 180.0
}
