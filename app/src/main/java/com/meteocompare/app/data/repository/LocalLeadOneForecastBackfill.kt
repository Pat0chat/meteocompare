package com.meteocompare.app.data.repository

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEndpoint
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Alimente la fiabilité locale des modèles sans archive Previous Runs (modèles
 * publiés uniquement en ensemble, ex. WeatherNext 2).
 *
 * Source : les instantanés quotidiens (Tmax, cumul de pluie, vent max) que
 * [ForecastEvolutionRecorder] enregistre déjà après chaque actualisation
 * fraîche, conservés 5 jours. Pour chaque date cible D, la dernière prévision
 * capturée la veille (D-1, fuseau de la ville) devient un échantillon J+1,
 * au même titre qu'une série Previous Runs `_previous_day1`. Les références
 * (réanalyse) sont ensuite complétées par `FetchBiasObservationsUseCase`
 * comme pour les autres modèles.
 *
 * Aucun appel réseau. Conséquence assumée : un jour sans actualisation de la
 * ville la veille ne produit pas d'échantillon, et le rattrapage manuel ne
 * peut remonter qu'aux 5 jours d'instantanés disponibles.
 */
@Singleton
class LocalLeadOneForecastBackfill @Inject constructor(
    private val evolutionDao: ForecastEvolutionDao,
    private val biasRepository: BiasSampleRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher
) {
    /** Enregistre les échantillons J+1 disponibles ; retourne leur nombre. */
    suspend operator fun invoke(
        city: City,
        models: List<WeatherModel>,
        today: LocalDate
    ): Int = withContext(io) {
        val localModels = models.filter { it.endpoint != ForecastEndpoint.FORECAST }
        if (localModels.isEmpty()) return@withContext 0

        val rows = evolutionDao.getHistoryWindow(
            cityId = city.id,
            modelKeys = localModels.map(WeatherModel::name),
            startEpochDay = today.minusDays(LOOKBACK_DAYS).toEpochDay(),
            endEpochDay = today.toEpochDay(),
            minSnapshotBucket = 0L,
            maxSnapshotBucket = Long.MAX_VALUE
        )
        val records = selectLocalLeadOneRecords(city, rows, localModels)
        biasRepository.recordForecasts(records)
        records.size
    }

    companion object {
        /** Rétention des instantanés d'évolution (voir [ForecastEvolutionRecorder]). */
        internal const val LOOKBACK_DAYS = 5L
    }
}

/**
 * Garde, pour chaque (modèle, variable, date cible), le dernier instantané
 * capturé la veille de la date cible dans le fuseau de la ville. Les
 * instantanés d'une autre source (migration de clé API) sont ignorés.
 */
internal fun selectLocalLeadOneRecords(
    city: City,
    rows: List<ForecastEvolutionEntity>,
    models: List<WeatherModel>
): List<ForecastBiasRecord> {
    val zone = resolveZoneOrUtc(city.timezone)
    val modelsByName = models.associateBy(WeatherModel::name)
    return rows
        .mapNotNull { row ->
            val model = modelsByName[row.modelKey] ?: return@mapNotNull null
            if (row.sourceApiKey != null && !model.matchesApiKey(row.sourceApiKey)) return@mapNotNull null
            val variable = row.biasVariable() ?: return@mapNotNull null
            if (!row.value.isFinite()) return@mapNotNull null
            val targetDate = LocalDate.ofEpochDay(row.targetDateEpochDay)
            val issuedAt = Instant.ofEpochMilli(row.snapshotAtEpochMs)
            if (issuedAt.atZone(zone).toLocalDate() != targetDate.minusDays(1)) return@mapNotNull null
            ForecastBiasRecord(
                cityId = city.id,
                model = model,
                variable = variable,
                targetDate = targetDate,
                issuedAt = issuedAt,
                value = row.value,
                leadDay = 1
            )
        }
        .groupBy { Triple(it.model, it.variable, it.targetDate) }
        .values
        .map { candidates -> candidates.maxBy(ForecastBiasRecord::issuedAt) }
        .sortedWith(compareBy({ it.targetDate }, { it.model.name }, { it.variable.name }))
}

/** Les trois métriques d'évolution sont exactement celles du suivi de biais. */
private fun ForecastEvolutionEntity.biasVariable(): BiasVariable? =
    when (ForecastEvolutionVariable.entries.firstOrNull { it.name == variable }) {
        ForecastEvolutionVariable.TEMPERATURE -> BiasVariable.TEMPERATURE
        ForecastEvolutionVariable.PRECIPITATION -> BiasVariable.PRECIPITATION
        ForecastEvolutionVariable.WIND -> BiasVariable.WIND_SPEED
        null -> null
    }
