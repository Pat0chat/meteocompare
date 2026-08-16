package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayForecastEvolution
import com.meteocompare.app.domain.model.ForecastEvolutionHighlight
import com.meteocompare.app.domain.model.ForecastEvolutionReport
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.ForecastEvolutionSnapshot
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastRevision
import com.meteocompare.app.domain.model.VariableForecastEvolution
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Compare le forecast courant déjà chargé avec les snapshots Previous Runs.
 * Aucun score de "probabilité de justesse" n'est produit ici : il s'agit
 * uniquement de mesurer l'amplitude et la direction des révisions.
 */
@Singleton
class ComputeForecastEvolutionUseCase @Inject constructor() {

    operator fun invoke(
        currentForecast: CityForecast,
        previousSamples: List<ForecastEvolutionSample>,
        fetchedAt: Instant? = null,
        fromCache: Boolean = false
    ): ForecastEvolutionReport {
        val previousByKey = previousSamples.groupBy { sample ->
            SampleKey(sample.targetDate, sample.variable, sample.daysAgo)
        }
        val dates = currentForecast.seriesByModel.values
            .asSequence()
            .flatMap { it.daily.dates.asSequence() }
            .distinct()
            .sorted()
            .toList()

        val days = dates.mapNotNull { date ->
            val variables = ForecastEvolutionVariable.entries.mapNotNull variableLoop@ { variable ->
                val currentValues = currentValues(currentForecast, date, variable)
                if (currentValues.isEmpty()) return@variableLoop null

                val current = ForecastEvolutionSnapshot(
                    daysAgo = 0,
                    medianValue = median(currentValues.values),
                    valuesByModel = currentValues
                )
                val previous = (1..3).mapNotNull { daysAgo ->
                    val values = previousByKey[SampleKey(date, variable, daysAgo)]
                        .orEmpty()
                        .associate { it.model to it.value }
                    if (values.isEmpty()) null else ForecastEvolutionSnapshot(
                        daysAgo = daysAgo,
                        medianValue = median(values.values),
                        valuesByModel = values
                    )
                }
                if (previous.isEmpty()) return@variableLoop null

                val comparisonSnapshot = previous.minByOrNull(ForecastEvolutionSnapshot::daysAgo)
                val revision = comparisonSnapshot?.let { old ->
                    computeRevision(variable, currentValues, old)
                }
                variable to VariableForecastEvolution(
                    variable = variable,
                    targetDate = date,
                    current = current,
                    previous = previous.sortedByDescending(ForecastEvolutionSnapshot::daysAgo),
                    revision = revision
                )
            }.toMap()
            DayForecastEvolution(date, variables).takeIf { variables.isNotEmpty() }
        }

        return ForecastEvolutionReport(
            days = days,
            fetchedAt = fetchedAt,
            fromCache = fromCache
        )
    }

    fun buildHighlight(
        report: ForecastEvolutionReport,
        fromDate: LocalDate,
        maxDaysAhead: Int = 5
    ): ForecastEvolutionHighlight? {
        val limit = fromDate.plusDays(maxDaysAhead.toLong())
        return report.days
            .asSequence()
            .filter { it.date in fromDate..limit }
            .flatMap { day -> day.variables.values.asSequence() }
            .mapNotNull { evolution ->
                val revision = evolution.revision ?: return@mapNotNull null
                if (!isHighlightWorthy(evolution.variable, revision)) return@mapNotNull null
                val score = highlightScore(evolution.variable, revision)
                score to ForecastEvolutionHighlight(
                    targetDate = evolution.targetDate,
                    variable = evolution.variable,
                    trend = revision.trend,
                    medianDelta = revision.medianDelta,
                    comparedModels = revision.comparedModels,
                    dominantModels = revision.dominantModels,
                    previousDaysAgo = revision.previousDaysAgo
                )
            }
            .maxByOrNull { it.first }
            ?.second
    }

    private fun currentValues(
        forecast: CityForecast,
        date: LocalDate,
        variable: ForecastEvolutionVariable
    ): Map<WeatherModel, Double> = buildMap {
        forecast.seriesByModel.forEach { (model, series) ->
            val index = series.daily.dates.indexOf(date)
            if (index < 0) return@forEach
            val value = when (variable) {
                ForecastEvolutionVariable.TEMPERATURE -> series.daily.tempMax.getOrNull(index)
                ForecastEvolutionVariable.PRECIPITATION -> series.daily.precipitationSum.getOrNull(index)
                ForecastEvolutionVariable.WIND -> series.daily.windSpeedMax.getOrNull(index)
            }?.takeIf { it.isFinite() && (variable == ForecastEvolutionVariable.TEMPERATURE || it >= 0.0) }
            if (value != null) put(model, value)
        }
    }

    private fun computeRevision(
        variable: ForecastEvolutionVariable,
        currentValues: Map<WeatherModel, Double>,
        previous: ForecastEvolutionSnapshot
    ): ForecastRevision? {
        val comparable = currentValues.keys.intersect(previous.valuesByModel.keys)
        if (comparable.isEmpty()) return null
        val deltas = comparable.associateWith { model ->
            currentValues.getValue(model) - previous.valuesByModel.getValue(model)
        }
        val threshold = stableThreshold(variable)
        val increased = deltas.values.count { it > threshold }
        val decreased = deltas.values.count { it < -threshold }
        val stable = deltas.size - increased - decreased
        val trend = classifyTrend(increased, decreased, stable, deltas.size)
        return ForecastRevision(
            previousDaysAgo = previous.daysAgo,
            medianDelta = median(deltas.values),
            medianAbsoluteDelta = median(deltas.values.map(::abs)),
            increasedModels = increased,
            decreasedModels = decreased,
            stableModels = stable,
            comparedModels = deltas.size,
            deltasByModel = deltas,
            trend = trend
        )
    }

    private fun classifyTrend(
        increased: Int,
        decreased: Int,
        stable: Int,
        count: Int
    ): ForecastEvolutionTrend {
        if (count < MIN_COMPARABLE_MODELS) return ForecastEvolutionTrend.INSUFFICIENT_DATA
        val required = kotlin.math.ceil(count * DOMINANT_RATIO).toInt()
        return when {
            increased >= required -> ForecastEvolutionTrend.INCREASING
            decreased >= required -> ForecastEvolutionTrend.DECREASING
            stable >= required -> ForecastEvolutionTrend.STABLE
            else -> ForecastEvolutionTrend.VOLATILE
        }
    }

    private fun isHighlightWorthy(
        variable: ForecastEvolutionVariable,
        revision: ForecastRevision
    ): Boolean {
        if (revision.comparedModels < MIN_COMPARABLE_MODELS) return false
        if (revision.trend == ForecastEvolutionTrend.STABLE ||
            revision.trend == ForecastEvolutionTrend.INSUFFICIENT_DATA
        ) return false
        return revision.medianAbsoluteDelta >= notableThreshold(variable)
    }

    private fun highlightScore(
        variable: ForecastEvolutionVariable,
        revision: ForecastRevision
    ): Double {
        val amplitude = revision.medianAbsoluteDelta / notableThreshold(variable)
        val consensus = revision.dominantModels.toDouble() / revision.comparedModels
        val volatilityBoost = if (revision.trend == ForecastEvolutionTrend.VOLATILE) 0.35 else 0.0
        return amplitude + consensus + volatilityBoost
    }

    private fun stableThreshold(variable: ForecastEvolutionVariable): Double = when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> 0.5
        ForecastEvolutionVariable.PRECIPITATION -> 1.0
        ForecastEvolutionVariable.WIND -> 3.0
    }

    private fun notableThreshold(variable: ForecastEvolutionVariable): Double = when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> 1.0
        ForecastEvolutionVariable.PRECIPITATION -> 2.0
        ForecastEvolutionVariable.WIND -> 5.0
    }

    private data class SampleKey(
        val date: LocalDate,
        val variable: ForecastEvolutionVariable,
        val daysAgo: Int
    )

    companion object {
        private const val MIN_COMPARABLE_MODELS = 2
        private const val DOMINANT_RATIO = 0.60
    }
}

private fun median(values: Collection<Double>): Double {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle]
    else (sorted[middle - 1] + sorted[middle]) / 2.0
}
