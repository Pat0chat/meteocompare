package com.meteocompare.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.domain.model.City
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.meteocompare.app.domain.util.LocationFixPolicy
import com.meteocompare.app.domain.util.LocationPrivacyGrid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Distinct from favorites and explicitly excluded from Android backup / device transfer. */
private val Context.currentLocationDataStore by preferencesDataStore(name = "current_location")

/** Store GRID CELL only, never the GPS fix, its accuracy, its provider or a trajectory. */
data class CurrentLocationSnapshot(
    val enabled: Boolean = false,
    val latCell: Int? = null,
    val lonCell: Int? = null,
    val updatedAt: Long = 0L
) {
    fun asCity(context: Context): City? {
        if (!enabled || latCell == null || lonCell == null) return null
        val localized = applyPersistedLocale(context)
        val stale = LocationFixPolicy.isStoredCellStale(System.currentTimeMillis(), updatedAt)
        val displayName = localized.getString(R.string.current_location_title) +
            if (stale) " · " + localized.getString(R.string.current_location_stale) else ""
        return City(
            id = CurrentLocationStore.idFor(latCell, lonCell),
            name = displayName,
            country = "",
            latitude = latCell * CurrentLocationStore.GRID_DEGREES,
            longitude = lonCell * CurrentLocationStore.GRID_DEGREES
        )
    }
}

@Singleton
class CurrentLocationStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    companion object {
        const val GRID_DEGREES = LocationPrivacyGrid.STEP_DEGREES
        const val CITY_PREFIX = LocationPrivacyGrid.ID_PREFIX
        const val WIDGET_CITY_ID = "current"
        fun isCurrentCity(id: String) = LocationPrivacyGrid.isCellId(id)
        fun idFor(latCell: Int, lonCell: Int) = LocationPrivacyGrid.key(latCell to lonCell)
        fun grid(latitude: Double, longitude: Double): Pair<Int, Int> =
            LocationPrivacyGrid.cell(latitude, longitude)
    }

    private val enabledKey = booleanPreferencesKey("enabled")
    private val latKey = intPreferencesKey("lat_cell")
    private val lonKey = intPreferencesKey("lon_cell")
    private val timeKey = longPreferencesKey("updated_at")

    val state: Flow<CurrentLocationSnapshot> = context.currentLocationDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            CurrentLocationSnapshot(
                enabled = prefs[enabledKey] ?: false,
                latCell = prefs[latKey],
                lonCell = prefs[lonKey],
                updatedAt = prefs[timeKey] ?: 0L
            )
        }

    suspend fun snapshot(): CurrentLocationSnapshot = state.first()

    suspend fun save(cell: Pair<Int, Int>, now: Long) {
        context.currentLocationDataStore.edit { prefs ->
            prefs[enabledKey] = true
            prefs[latKey] = cell.first
            prefs[lonKey] = cell.second
            prefs[timeKey] = now
        }
    }

    /** Removes even the last approximated cell, not just the enabled flag. */
    suspend fun clear() {
        context.currentLocationDataStore.edit { it.clear() }
    }
}
