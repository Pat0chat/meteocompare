package com.meteocompare.app.data.repository

import android.content.Context
import com.meteocompare.app.data.remote.ElevationApi
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.CalibrationPoint
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.MetarStation
import com.meteocompare.app.domain.model.MetarStationSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Choisit le point de calibration de chaque ville et retient celui sur lequel
 * son historique de biais a été construit.
 *
 * La liste des stations est embarquée (`assets/metar_stations.tsv`) : le choix
 * se fait sur l'appareil, seule l'altitude de la ville est demandée une fois à
 * Open-Meteo, puis mise en cache.
 */
@Singleton
class CalibrationPointRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val elevationApi: ElevationApi,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {
    /**
     * @property changed vrai quand l'historique en base a été construit sur une
     *   autre référence : il doit être effacé puis reconstruit en entier, sans
     *   quoi la fenêtre de 30 jours mélangerait deux références.
     */
    data class Resolution(val point: CalibrationPoint, val changed: Boolean)

    private val stations: List<MetarStation> by lazy { loadStations() }
    private val prefs by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    suspend fun resolve(city: City): Resolution = withContext(io) {
        val station = elevationOf(city)?.let { elevation ->
            MetarStationSelector.select(city.latitude, city.longitude, elevation, stations)
        }
        val point = CalibrationPoint.of(city, station)
        // Historique antérieur à cette fonctionnalité : toujours sur la réanalyse.
        val stored = prefs.getString(pointKey(city), CalibrationPoint.REANALYSIS_KEY)
        Resolution(point, changed = stored != point.key)
    }

    /** À appeler une fois l'historique reconstruit sur [point]. */
    suspend fun commit(city: City, point: CalibrationPoint) = withContext(io) {
        prefs.edit().putString(pointKey(city), point.key).apply()
    }

    private suspend fun elevationOf(city: City): Double? {
        val cacheKey = "elevation:%.4f,%.4f".format(java.util.Locale.ROOT, city.latitude, city.longitude)
        if (prefs.contains(cacheKey)) return prefs.getFloat(cacheKey, 0f).toDouble()
        val elevation = elevationApi.getElevation(city.latitude, city.longitude)
            .elevation.firstOrNull()?.takeIf(Double::isFinite) ?: return null
        prefs.edit().putFloat(cacheKey, elevation.toFloat()).apply()
        return elevation
    }

    private fun loadStations(): List<MetarStation> =
        context.assets.open(STATIONS_ASSET).bufferedReader().useLines { lines ->
            lines.filterNot { it.isBlank() || it.startsWith("#") }
                .mapNotNull { line ->
                    val cells = line.split('\t')
                    MetarStation(
                        icao = cells.getOrNull(0) ?: return@mapNotNull null,
                        latitude = cells.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null,
                        longitude = cells.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null,
                        elevationM = cells.getOrNull(3)?.toDoubleOrNull() ?: return@mapNotNull null
                    )
                }
                .toList()
        }

    private fun pointKey(city: City) = "point:${city.id}"

    private companion object {
        const val PREFERENCES = "bias_calibration_points_v1"
        const val STATIONS_ASSET = "metar_stations.tsv"
    }
}
