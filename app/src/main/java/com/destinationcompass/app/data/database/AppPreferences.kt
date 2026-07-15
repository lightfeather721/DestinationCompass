package com.destinationcompass.app.data.database

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.destinationcompass.app.model.Destination
import com.destinationcompass.app.model.DestinationIcon
import com.destinationcompass.app.model.DistanceUnit
import com.destinationcompass.app.model.LocationRefreshInterval
import com.destinationcompass.app.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("destination_compass")

data class UserPreferences(
    val destination: Destination,
    val favorites: List<Destination>,
    val themeMode: ThemeMode,
    val distanceUnit: DistanceUnit,
    val locationRefreshIntervalMillis: Long
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val destination = stringPreferencesKey("destination")
        val favorites = stringPreferencesKey("favorites")
        val theme = stringPreferencesKey("theme")
        val unit = stringPreferencesKey("unit")
        val locationRefreshRate = stringPreferencesKey("location_refresh_rate")
    }

    private val defaultDestination = Destination(
        id = "huaian-municipal-government",
        name = "淮安市人民政府",
        address = "江苏省淮安市淮安区翔宇南道1号",
        latitude = 33.551495,
        longitude = 119.113166
    )

    private val defaults = emptyList<Destination>()

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { values ->
        UserPreferences(
            destination = decodeDestination(values[Keys.destination]) ?: defaultDestination,
            favorites = values[Keys.favorites]?.split("\n")?.mapNotNull(::decodeDestination) ?: defaults,
            themeMode = values[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            distanceUnit = values[Keys.unit]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.KILOMETERS,
            locationRefreshIntervalMillis = decodeRefreshInterval(values[Keys.locationRefreshRate])
        )
    }

    suspend fun setDestination(destination: Destination) = context.dataStore.edit { it[Keys.destination] = encode(destination) }
    suspend fun setFavorites(favorites: List<Destination>) = context.dataStore.edit { it[Keys.favorites] = favorites.joinToString("\n", transform = ::encode) }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setUnit(unit: DistanceUnit) = context.dataStore.edit { it[Keys.unit] = unit.name }
    suspend fun setLocationRefreshInterval(intervalMillis: Long) = context.dataStore.edit {
        it[Keys.locationRefreshRate] = LocationRefreshInterval.normalize(intervalMillis).toString()
    }

    private fun encode(value: Destination): String = listOf(
        value.id, value.name, value.address, value.latitude.toString(), value.longitude.toString(), value.icon.name
    ).joinToString("|") { Uri.encode(it) }

    private fun decodeDestination(raw: String?): Destination? {
        val parts = raw?.split("|")?.map(Uri::decode) ?: return null
        if (parts.size != 6) return null
        return runCatching {
            Destination(parts[0], parts[1], parts[2], parts[3].toDouble(), parts[4].toDouble(), DestinationIcon.valueOf(parts[5]))
        }.getOrNull()
    }

    private fun decodeRefreshInterval(raw: String?): Long {
        val persisted = raw?.toLongOrNull()
            ?: raw?.removePrefix("MS_")?.toLongOrNull()
            ?: LocationRefreshInterval.DEFAULT_MILLIS
        return LocationRefreshInterval.normalize(persisted)
    }
}
