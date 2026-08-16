package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.ForecastEvolutionHistoryData
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Relit les snapshots locaux proches de H-24/H-48/H-72.
 *
 * C'est volontairement différent de l'API Open-Meteo Previous Runs : le but
 * de cette carte est de comparer les prévisions que MeteoCompare a enregistrées
 * lors de refreshs antérieurs pour la même date cible, pas d'évaluer un modèle
 * à lead-time fixe.
 */
@Singleton
class ForecastEvolutionRepositoryImpl @Inject constructor(
    private val dao: ForecastEvolutionDao,
    @param:IoDispatcher private val io: CoroutineDispatcher
) : ForecastEvolutionRepository {

    override suspend fun getPreviousForecasts(
        city: City,
        models: List<WeatherModel>,
        startDate: LocalDate,
        endDate: LocalDate,
        referenceAt: Instant
    ): ApiResult<ForecastEvolutionHistoryData> = withContext(io) {
        if (models.isEmpty() || endDate < startDate) {
            return@withContext ApiResult.Success(ForecastEvolutionHistoryData(emptyList(), null))
        }

        try {
            val modelKeys = models.map(WeatherModel::name)
            val referenceMs = referenceAt.toEpochMilli()
            val minMs = referenceMs - (MAX_TARGET_AGE_HOURS + MATCH_TOLERANCE_HOURS) * HOUR_MS
            val maxMs = referenceMs - (MIN_TARGET_AGE_HOURS - MATCH_TOLERANCE_HOURS) * HOUR_MS
            val minBucket = minMs / ForecastEvolutionRecorder.SNAPSHOT_BUCKET_MS
            val maxBucket = maxMs / ForecastEvolutionRecorder.SNAPSHOT_BUCKET_MS

            val entities = dao.getHistoryWindow(
                cityId = city.id,
                modelKeys = modelKeys,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = endDate.toEpochDay(),
                minSnapshotBucket = minBucket,
                maxSnapshotBucket = maxBucket
            )
            val oldest = dao.oldestSnapshotAt(city.id, modelKeys)?.let(Instant::ofEpochMilli)
            val samples = selectHistorySamples(entities, referenceAt)
            ApiResult.Success(ForecastEvolutionHistoryData(samples, oldest))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ApiResult.Error(t, t.message ?: "Forecast evolution history unavailable")
        }
    }

    private fun selectHistorySamples(
        entities: List<ForecastEvolutionEntity>,
        referenceAt: Instant
    ): List<ForecastEvolutionSample> {
        if (entities.isEmpty()) return emptyList()

        val buckets = entities.groupBy(ForecastEvolutionEntity::snapshotBucket)
            .map { (bucket, rows) ->
                BucketSnapshot(
                    bucket = bucket,
                    capturedAt = representativeInstant(rows),
                    rows = rows
                )
            }
        val used = mutableSetOf<Long>()
        return buildList {
            TARGET_AGE_HOURS.forEachIndexed { index, targetAgeHours ->
                val targetMs = referenceAt.toEpochMilli() - targetAgeHours * HOUR_MS
                val candidate = buckets.asSequence()
                    .filter { it.bucket !in used }
                    .map { snapshot -> snapshot to abs(snapshot.capturedAt.toEpochMilli() - targetMs) }
                    .filter { (_, distance) -> distance <= MATCH_TOLERANCE_HOURS * HOUR_MS }
                    .sortedWith(
                        compareBy<Pair<BucketSnapshot, Long>> { it.second }
                            .thenByDescending { (snapshot, _) -> snapshot.rows.map { it.modelKey }.distinct().size }
                    )
                    .firstOrNull()
                    ?.first
                    ?: return@forEachIndexed

                used += candidate.bucket
                val actualAgeHours = (
                    (referenceAt.toEpochMilli() - candidate.capturedAt.toEpochMilli()).toDouble() / HOUR_MS
                ).roundToInt().coerceAtLeast(1)
                candidate.rows.mapNotNullTo(this) { entity ->
                    entity.toDomain(
                        slotDaysAgo = index + 1,
                        ageHours = actualAgeHours,
                        capturedAt = candidate.capturedAt
                    )
                }
            }
        }
    }

    private fun representativeInstant(rows: List<ForecastEvolutionEntity>): Instant {
        val sorted = rows.map(ForecastEvolutionEntity::snapshotAtEpochMs).sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            val lower = sorted[middle - 1]
            val upper = sorted[middle]
            lower + (upper - lower) / 2L
        }
        return Instant.ofEpochMilli(median)
    }

    private fun ForecastEvolutionEntity.toDomain(
        slotDaysAgo: Int,
        ageHours: Int,
        capturedAt: Instant
    ): ForecastEvolutionSample? {
        val model = runCatching { WeatherModel.valueOf(modelKey) }.getOrNull() ?: return null
        val variable = runCatching { ForecastEvolutionVariable.valueOf(variable) }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        return ForecastEvolutionSample(
            model = model,
            variable = variable,
            targetDate = LocalDate.ofEpochDay(targetDateEpochDay),
            daysAgo = slotDaysAgo,
            value = value,
            ageHours = ageHours,
            capturedAt = capturedAt
        )
    }

    private data class BucketSnapshot(
        val bucket: Long,
        val capturedAt: Instant,
        val rows: List<ForecastEvolutionEntity>
    )

    companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val MIN_TARGET_AGE_HOURS = 24L
        private const val MAX_TARGET_AGE_HOURS = 72L
        private const val MATCH_TOLERANCE_HOURS = 8L
        private val TARGET_AGE_HOURS = listOf(24L, 48L, 72L)
    }
}
