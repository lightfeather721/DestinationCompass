package com.destinationcompass.app.model

data class Destination(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val icon: DestinationIcon = DestinationIcon.PLACE
)

enum class DestinationIcon { HOME, WORK, PARKING, PLACE }

enum class DistanceUnit { KILOMETERS, MILES }

object LocationRefreshInterval {
    const val MIN_MILLIS = 500L
    const val MAX_MILLIS = 5_000L
    const val DEFAULT_MILLIS = 1_000L
    const val STEP_MILLIS = 100L

    fun normalize(value: Long): Long =
        ((value.coerceIn(MIN_MILLIS, MAX_MILLIS) + STEP_MILLIS / 2) / STEP_MILLIS) * STEP_MILLIS
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
