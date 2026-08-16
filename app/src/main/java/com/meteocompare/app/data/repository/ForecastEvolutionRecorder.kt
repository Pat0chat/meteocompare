package com.meteocompare.app.data.repository

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
        val entities = buildList {
            forecast.seriesByModel.forEach { (model, series) ->
                series.daily.dates.forEachIndexed { index, date ->
                    series.daily.tempMax.getOrNull(index)
                        ?.takeIf(Double::isFinite)
                        ?.let { value ->
                            add(entity(forecast.city.id, model.name, ForecastEvolutionVariable.TEMPERATURE, date.toEpochDay(), bucket, capturedMs, value))
                        }
                    series.daily.precipitationSum.getOrNull(index)
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?.let { value ->
                            add(entity(forecast.city.id, model.name, ForecastEvolutionVariable.PRECIPITATION, date.toEpochDay(), bucket, capturedMs, value))
                        }
                    series.daily.windSpeedMax.getOrNull(index)
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                        ?.let { value ->
                            add(entity(forecast.city.id, model.name, ForecastEvolutionVariable.WIND, date.toEpochDay(), bucket, capturedMs, value))
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

    suspend fun clearCity(cityId: String) = withContext(io) {
        dao.deleteForCity(cityId)
    }

    private fun entity(
        cityId: String,
        modelKey: String,
        variable: ForecastEvolutionVariable,
        targetDateEpochDay: Long,
        snapshotBucket: Long,
        snapshotAtEpochMs: Long,
        value: Double
    ) = ForecastEvolutionEntity(
        cityId = cityId,
        modelKey = modelKey,
        variable = variable.name,
        targetDateEpochDay = targetDateEpochDay,
        snapshotBucket = snapshotBucket,
        snapshotAtEpochMs = snapshotAtEpochMs,
        value = value
    )

    companion object {
        /** Au plus un snapshot utile par tranche de 3 h et par valeur métier. */
        internal const val SNAPSHOT_BUCKET_MS = 3L * 60L * 60L * 1000L
        private const val RETENTION_MS = 5L * 24L * 60L * 60L * 1000L
    }
}
