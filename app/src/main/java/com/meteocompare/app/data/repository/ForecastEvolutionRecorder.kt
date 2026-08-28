package com.meteocompare.app.data.repository

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enregistre localement les prévisions fraîchement récupérées par le Forecast API.
 *
 * Aucun appel réseau n'est déclenché ici. Le recorder est appelé uniquement
 * après un fetch météo réussi et transforme le forecast déjà en mémoire en
 * trois métriques quotidiennes : Tmax, cumul de précipitations et vent max.
 */
@Singleton
class ForecastEvolutionRecorder @Inject constructor(
    private val dao: ForecastEvolutionDao,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {
    suspend fun record(forecast: CityForecast) = withContext(io) {
        val capturedAt = forecast.fetchedAt ?: return@withContext
        val capturedMs = capturedAt.toEpochMilli()
        val bucket = capturedMs / SNAPSHOT_BUCKET_MS
        val zone = resolveZoneOrUtc(forecast.city.timezone)
        val entities = buildList {
            forecast.seriesByModel.forEach { (model, series) ->
                series.daily.dates.forEachIndexed { index, date ->
                    // Une valeur daily située à la limite d'horizon peut être
                    // calculée sur une journée partielle. Pour l'évolution,
                    // cela créerait de faux changements (surtout sur le cumul
                    // pluie). On n'historise donc chaque métrique que si sa
                    // série horaire couvre entièrement la journée civile.
                    if (hasCompleteHourlyCoverage(series, date, ForecastEvolutionVariable.TEMPERATURE, zone)) {
                        series.daily.tempMax.getOrNull(index)
                            ?.takeIf(Double::isFinite)
                            ?.let { value ->
                                add(entity(forecast.city.id, model, ForecastEvolutionVariable.TEMPERATURE, date.toEpochDay(), bucket, capturedMs, value))
                            }
                    }
                    if (hasCompleteHourlyCoverage(series, date, ForecastEvolutionVariable.PRECIPITATION, zone)) {
                        series.daily.precipitationSum.getOrNull(index)
                            ?.takeIf { it.isFinite() && it >= 0.0 }
                            ?.let { value ->
                                add(entity(forecast.city.id, model, ForecastEvolutionVariable.PRECIPITATION, date.toEpochDay(), bucket, capturedMs, value))
                            }
                    }
                    if (hasCompleteHourlyCoverage(series, date, ForecastEvolutionVariable.WIND, zone)) {
                        series.daily.windSpeedMax.getOrNull(index)
                            ?.takeIf { it.isFinite() && it >= 0.0 }
                            ?.let { value ->
                                add(entity(forecast.city.id, model, ForecastEvolutionVariable.WIND, date.toEpochDay(), bucket, capturedMs, value))
                            }
                    }
                }
            }
        }
        val inserted = dao.insertSnapshotBucketIfAbsent(
            cityId = forecast.city.id,
            snapshotBucket = bucket,
            samples = entities
        )
        if (inserted) dao.purgeCapturedBefore(capturedMs - RETENTION_MS)
    }

    private fun hasCompleteHourlyCoverage(
        series: com.meteocompare.app.domain.model.ForecastSeries,
        date: java.time.LocalDate,
        variable: ForecastEvolutionVariable,
        zone: java.time.ZoneId
    ): Boolean {
        val indices = series.hourly.timestamps.indices.filter { index ->
            series.hourly.timestamps[index].atZone(zone).toLocalDate() == date
        }
        if (indices.size !in COMPLETE_CIVIL_DAY_HOURS) return false

        val validCount = indices.count { index ->
            when (variable) {
                ForecastEvolutionVariable.TEMPERATURE ->
                    series.hourly.temperature2m.getOrNull(index)?.isFinite() == true
                ForecastEvolutionVariable.PRECIPITATION ->
                    series.hourly.precipitation.getOrNull(index)
                        ?.let { it.isFinite() && it >= 0.0 } == true
                ForecastEvolutionVariable.WIND ->
                    series.hourly.windSpeed10m.getOrNull(index)
                        ?.let { it.isFinite() && it >= 0.0 } == true
            }
        }
        return validCount == indices.size
    }

    suspend fun clearCity(cityId: String) = withContext(io) {
        dao.deleteForCity(cityId)
    }

    private fun entity(
        cityId: String,
        model: com.meteocompare.app.domain.model.WeatherModel,
        variable: ForecastEvolutionVariable,
        targetDateEpochDay: Long,
        snapshotBucket: Long,
        snapshotAtEpochMs: Long,
        value: Double
    ) = ForecastEvolutionEntity(
        cityId = cityId,
        modelKey = model.name,
        variable = variable.name,
        targetDateEpochDay = targetDateEpochDay,
        snapshotBucket = snapshotBucket,
        snapshotAtEpochMs = snapshotAtEpochMs,
        value = value,
        sourceApiKey = model.apiKey,
        resolutionKm = model.resolutionKm
    )

    companion object {
        /** Au plus un snapshot utile par tranche de 3 h et par valeur métier. */
        internal const val SNAPSHOT_BUCKET_MS = 3L * 60L * 60L * 1000L
        private val COMPLETE_CIVIL_DAY_HOURS = 23..25
        private const val RETENTION_MS = 5L * 24L * 60L * 60L * 1000L
    }
}
