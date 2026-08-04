package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.Immutable
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.ModelReliability
import com.meteocompare.app.domain.model.ModelReliabilityCalculator
import com.meteocompare.app.domain.model.ReliabilityRank
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant

/** Données complètes nécessaires au tableau de fiabilité d'une variable. */
@Immutable
internal data class BiasSelection(
    val model: WeatherModel,
    val bias: ModelBias,
    val reliability: ModelReliability,
    val localRank: ReliabilityRank?,
    val multiModelReliability: ModelReliability?,
    val dailyForecast: List<Double>,
    val dailyObservation: List<Double>,
    val yDomainMin: Double,
    val yDomainMax: Double
)

/**
 * Construit la page de biais avec une source statistique unique.
 *
 * Lorsqu'un modèle participe au classement, biais, score, graphique, rang et
 * référence multi-modèles reposent tous sur les mêmes dates communes. Sans
 * cohorte comparable, la page conserve l'historique propre au modèle et
 * n'affiche pas de rang trompeur.
 */
internal fun buildBiasSelection(
    model: WeatherModel,
    variable: BiasVariable,
    state: VariableBiasState
): BiasSelection? {
    val sourceBias = state.biasByModel[model] ?: return null
    val sourceSamples = state.historyByModel[model] ?: return null

    val comparableHistories = comparableHistoriesForRanking(state.historyByModel)
    val ranking = buildLocalVariableRanking(variable, state, comparableHistories)
    val rankingEntry = ranking.entries.firstOrNull { it.model == model }
    val displayedSamples = comparableHistories[model] ?: sourceSamples
    val perDay = displayedSamples
        .groupBy(BiasSample::targetDate)
        .values
        .mapNotNull { sameDate -> sameDate.maxByOrNull { it.issuedAt ?: Instant.MIN } }
        .sortedBy(BiasSample::targetDate)
    val reliability = rankingEntry?.reliability ?: ModelReliabilityCalculator.compute(
        variable = variable,
        samples = perDay,
        windowDays = sourceBias.windowDays
    ) ?: return null

    val displayedBias = if (rankingEntry != null) {
        ModelBias(
            variable = variable,
            meanBias = reliability.meanBias,
            stdDev = reliability.standardDeviation,
            sampleSize = reliability.sampleSize,
            windowDays = sourceBias.windowDays
        )
    } else {
        sourceBias
    }

    val chartHistories = if (rankingEntry != null) comparableHistories else mapOf(model to perDay)
    val chartValues = chartHistories.values.asSequence()
        .flatten()
        .flatMap { sample -> sequenceOf(sample.forecast, sample.observation) }
        .toList()
    val yDomain = biasChartDomain(chartValues, variable) ?: return null

    return BiasSelection(
        model = model,
        bias = displayedBias,
        reliability = reliability,
        localRank = rankingEntry?.let { entry ->
            ReliabilityRank(rank = entry.rank, modelCount = ranking.entries.size)
        },
        multiModelReliability = if (rankingEntry != null) {
            ModelReliabilityCalculator.computeMultiModelBaseline(
                variable = variable,
                historyByModel = comparableHistories,
                windowDays = sourceBias.windowDays
            )
        } else {
            null
        },
        dailyForecast = perDay.map { it.forecast },
        dailyObservation = perDay.map { it.observation },
        yDomainMin = yDomain.first,
        yDomainMax = yDomain.second
    )
}

/** Bornes du graphique calculées sur le même échantillon que les statistiques. */
private fun biasChartDomain(
    values: List<Double>,
    variable: BiasVariable
): Pair<Double, Double>? {
    if (values.isEmpty()) return null
    val min = values.min()
    val max = values.max()
    return when (variable) {
        BiasVariable.TEMPERATURE -> (min - 1.0) to (max + 1.0)
        BiasVariable.PRECIPITATION -> 0.0 to (max + 0.5)
        BiasVariable.WIND_SPEED -> 0.0 to (max + 3.0)
    }
}
