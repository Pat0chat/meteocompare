package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.Immutable
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.ModelReliability
import com.meteocompare.app.domain.model.ModelReliabilityCalculator
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import java.time.Instant
import java.time.LocalDate

/** Une ligne du classement local pour une variable météo. */
@Immutable
internal data class LocalModelRankingEntry(
    val rank: Int,
    val model: WeatherModel,
    val reliability: ModelReliability
)

/** Classement d'une variable, calculé uniquement avec l'historique de la ville. */
@Immutable
internal data class LocalVariableRanking(
    val variable: BiasVariable,
    val entries: List<LocalModelRankingEntry>
) {
    val winner: LocalModelRankingEntry? get() = entries.firstOrNull()
}

/** Les trois classements locaux affichés dans la fiche ville et dans la sheet. */
@Immutable
internal data class LocalModelRankings(
    val temperature: LocalVariableRanking,
    val precipitation: LocalVariableRanking,
    val wind: LocalVariableRanking
) {
    fun forVariable(variable: BiasVariable): LocalVariableRanking = when (variable) {
        BiasVariable.TEMPERATURE -> temperature
        BiasVariable.PRECIPITATION -> precipitation
        BiasVariable.WIND_SPEED -> wind
    }

    val hasAnyRanking: Boolean
        get() = temperature.entries.isNotEmpty() ||
            precipitation.entries.isNotEmpty() ||
            wind.entries.isNotEmpty()

    /**
     * Performance J+1 passée, distincte de la convergence instantanée. Les
     * scores des modèles frères sont d'abord agrégés par lignée afin de ne pas
     * compter plusieurs fois le même système numérique.
     */
    val historicalConfidencePercent: Int?
        get() {
            val perVariable = listOf(temperature, precipitation, wind).mapNotNull { ranking ->
                val familyScores = ranking.entries.groupBy { ForecastConsensus.groupFor(it.model) }
                    .values.map { siblings -> siblings.map { it.reliability.score }.average() }
                if (familyScores.size < 2) null else familyScores.average()
            }
            return if (perVariable.isEmpty()) null else perVariable.average().toInt().coerceIn(0, 100)
        }

    val historicalFamilyCount: Int
        get() = listOf(temperature, precipitation, wind).flatMap { it.entries }
            .map { ForecastConsensus.groupFor(it.model) }.distinct().size

    val firstAvailableVariable: BiasVariable
        get() = when {
            temperature.entries.isNotEmpty() -> BiasVariable.TEMPERATURE
            precipitation.entries.isNotEmpty() -> BiasVariable.PRECIPITATION
            wind.entries.isNotEmpty() -> BiasVariable.WIND_SPEED
            else -> BiasVariable.TEMPERATURE
        }
}

/**
 * Construit les classements à partir de journées strictement comparables.
 *
 * Tous les modèles d'un classement sont évalués sur le même ensemble de dates.
 * On choisit le plus grand groupe possédant au moins quatorze dates communes,
 * puis on trie par score décroissant, MAE croissante et nom d'affichage.
 */
internal fun buildLocalModelRankings(state: BiasScreenState): LocalModelRankings =
    LocalModelRankings(
        temperature = buildLocalVariableRanking(
            variable = BiasVariable.TEMPERATURE,
            state = state.temperature
        ),
        precipitation = buildLocalVariableRanking(
            variable = BiasVariable.PRECIPITATION,
            state = state.precipitation
        ),
        wind = buildLocalVariableRanking(
            variable = BiasVariable.WIND_SPEED,
            state = state.wind
        )
    )

internal fun buildLocalVariableRanking(
    variable: BiasVariable,
    state: VariableBiasState,
    comparableHistories: Map<WeatherModel, List<BiasSample>> =
        comparableHistoriesForRanking(state.historyByModel)
): LocalVariableRanking {
    val reliabilities = comparableHistories.mapNotNull { (model, samples) ->
        val windowDays = state.biasByModel[model]?.windowDays ?: DEFAULT_RANKING_WINDOW_DAYS
        ModelReliabilityCalculator.compute(
            variable = variable,
            samples = samples,
            windowDays = windowDays
        )?.let { model to it }
    }

    val sorted = reliabilities.sortedWith(
        compareByDescending<Pair<WeatherModel, ModelReliability>> { it.second.score }
            .thenBy { it.second.meanAbsoluteError }
            .thenBy { it.first.displayName }
    )

    return LocalVariableRanking(
        variable = variable,
        entries = sorted.mapIndexed { index, (model, reliability) ->
            LocalModelRankingEntry(
                rank = index + 1,
                model = model,
                reliability = reliability
            )
        }
    )
}

/**
 * Retourne le plus grand groupe de modèles comparable sur au moins 14 dates.
 *
 * Les doublons sont normalisés en gardant la capture la plus récente. Un rang
 * nécessite au moins deux modèles : un modèle seul conserve sa page de biais,
 * mais aucun rang artificiel 1/1 n'est affiché.
 */
internal fun comparableHistoriesForRanking(
    historyByModel: Map<WeatherModel, List<BiasSample>>,
    minimumSamples: Int = ModelBias.MIN_SAMPLES_FOR_BIAS
): Map<WeatherModel, List<BiasSample>> {
    require(minimumSamples > 0) { "minimumSamples must be positive" }

    val normalized = historyByModel
        .mapValues { (_, history) -> normalizeHistory(history) }
        .filterValues { it.size >= minimumSamples }
    if (normalized.size < MIN_MODELS_FOR_RANKING) return emptyMap()

    val models = normalized.keys.sortedBy(WeatherModel::name)
    for (cohortSize in models.size downTo MIN_MODELS_FOR_RANKING) {
        val candidates = combinations(models, cohortSize).mapNotNull { cohort ->
            val commonDates = cohort
                .map { model ->
                    normalized.getValue(model).mapTo(linkedSetOf(), BiasSample::targetDate)
                }
                .reduce { common, dates -> common.apply { retainAll(dates) } }
            if (commonDates.size < minimumSamples) null else ComparableCohort(cohort, commonDates)
        }
        val best = candidates.sortedWith(
            compareByDescending<ComparableCohort> { it.commonDates.size }
                .thenBy { cohort -> cohort.models.joinToString("|") { it.name } }
        ).firstOrNull() ?: continue

        return best.models.associateWith { model ->
            normalized.getValue(model).filter { it.targetDate in best.commonDates }
        }
    }
    return emptyMap()
}

private data class ComparableCohort(
    val models: List<WeatherModel>,
    val commonDates: Set<LocalDate>
)

private fun normalizeHistory(history: List<BiasSample>): List<BiasSample> = history
    .groupBy(BiasSample::targetDate)
    .values
    .mapNotNull { sameDate -> sameDate.maxByOrNull { it.issuedAt ?: Instant.MIN } }
    .sortedBy(BiasSample::targetDate)

private fun <T> combinations(values: List<T>, size: Int): Sequence<List<T>> = sequence {
    if (size <= 0 || size > values.size) return@sequence
    val selected = ArrayList<T>(size)

    suspend fun SequenceScope<List<T>>.visit(start: Int) {
        if (selected.size == size) {
            yield(selected.toList())
            return
        }
        val remaining = size - selected.size
        for (index in start..values.size - remaining) {
            selected += values[index]
            visit(index + 1)
            selected.removeAt(selected.lastIndex)
        }
    }

    visit(0)
}

private const val DEFAULT_RANKING_WINDOW_DAYS = 30
private const val MIN_MODELS_FOR_RANKING = 2

/**
 * Variable à ouvrir depuis le bouton global du bloc fiabilité.
 */
internal fun rankingVariableFor(
    activeVariable: BiasVariable,
    rankings: LocalModelRankings
): BiasVariable =
    if (rankings.forVariable(activeVariable).entries.isNotEmpty()) {
        activeVariable
    } else {
        rankings.firstAvailableVariable
    }
