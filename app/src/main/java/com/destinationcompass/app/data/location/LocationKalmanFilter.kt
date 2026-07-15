package com.destinationcompass.app.data.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class FilteredCoordinate(val latitude: Double, val longitude: Double)

/** Lightweight two-dimensional Kalman filter with a stationary drift gate. */
class LocationKalmanFilter {
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var varianceMetersSquared = 0.0
    private var lastElapsedRealtimeNanos = 0L

    fun filter(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        elapsedRealtimeNanos: Long,
        speedMetersPerSecond: Float
    ): FilteredCoordinate {
        val currentLatitude = this.latitude
        val currentLongitude = this.longitude
        if (currentLatitude == null || currentLongitude == null || elapsedRealtimeNanos <= lastElapsedRealtimeNanos) {
            return reset(latitude, longitude, accuracyMeters, elapsedRealtimeNanos)
        }

        val elapsedSeconds = ((elapsedRealtimeNanos - lastElapsedRealtimeNanos) / 1_000_000_000.0)
            .coerceIn(0.05, 10.0)
        lastElapsedRealtimeNanos = elapsedRealtimeNanos

        val processDistance = max(0.7, speedMetersPerSecond.toDouble()) * elapsedSeconds
        varianceMetersSquared += processDistance * processDistance

        val measuredDistance = distanceMeters(currentLatitude, currentLongitude, latitude, longitude)
        val stationaryGateMeters = max(2.0, accuracyMeters * 0.35).toDouble()
        if (speedMetersPerSecond < 0.8f && measuredDistance < stationaryGateMeters) {
            return FilteredCoordinate(currentLatitude, currentLongitude)
        }

        val measurementVariance = max(1f, accuracyMeters).toDouble().let { it * it }
        var gain = varianceMetersSquared / (varianceMetersSquared + measurementVariance)
        gain = when {
            speedMetersPerSecond >= 8f -> gain.coerceAtLeast(0.85)
            speedMetersPerSecond >= 3f -> gain.coerceAtLeast(0.68)
            else -> gain.coerceIn(0.12, 0.72)
        }

        this.latitude = currentLatitude + gain * (latitude - currentLatitude)
        this.longitude = currentLongitude + gain * (longitude - currentLongitude)
        varianceMetersSquared = (1.0 - gain) * varianceMetersSquared
        return FilteredCoordinate(this.latitude!!, this.longitude!!)
    }

    fun reset() {
        latitude = null
        longitude = null
        varianceMetersSquared = 0.0
        lastElapsedRealtimeNanos = 0L
    }

    private fun reset(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        elapsedRealtimeNanos: Long
    ): FilteredCoordinate {
        this.latitude = latitude
        this.longitude = longitude
        varianceMetersSquared = max(1f, accuracyMeters).toDouble().let { it * it }
        lastElapsedRealtimeNanos = elapsedRealtimeNanos
        return FilteredCoordinate(latitude, longitude)
    }

    private fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val fromLatitude = Math.toRadians(fromLat)
        val toLatitude = Math.toRadians(toLat)
        val deltaLatitude = Math.toRadians(toLat - fromLat)
        val deltaLongitude = Math.toRadians(toLon - fromLon)
        val a = sin(deltaLatitude / 2) * sin(deltaLatitude / 2) +
            cos(fromLatitude) * cos(toLatitude) * sin(deltaLongitude / 2) * sin(deltaLongitude / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
