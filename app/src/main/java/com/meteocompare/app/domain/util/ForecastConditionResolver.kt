package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import java.time.LocalDate
import java.time.ZoneId

/** Shared daily/solar-period decision for the timeline and engine comparison.
 * Conditions are resolved per model first; never combine unrelated model variables.
 */
internal object ForecastConditionResolver {
    fun periodConsensus(
        forecast: CityForecast,
        date: LocalDate,
        zone: ZoneId,
        period: DailyConditionPeriod = DailyConditionPeriod.FULL_DAY,
        solar: ForecastSolarWindow? = null,
        nextSolar: ForecastSolarWindow? = null,
        cloudCoverPercent: Double? = null,
        cloudCoverFamilyCount: Int = 0
    ): ConditionDecision {
        val byModel = forecast.seriesByModel.map { (model, series) ->
            model to ConditionDecisionEngine.period(series, date, zone, period, solar, nextSolar)
        }
        val native = byModel.mapNotNull { (model, decision) ->
            decision.condition?.takeIf {
                decision.status == ConditionStatus.RELIABLE && decision.origin == ConditionOrigin.HOURLY_DOMINANT
            }?.let { ForecastConsensus.Entry(model, it) }
        }
        val derived = byModel.mapNotNull { (model, decision) ->
            decision.condition?.takeUnless {
                decision.status == ConditionStatus.RELIABLE && decision.origin == ConditionOrigin.HOURLY_DOMINANT
            }?.let { ForecastConsensus.Entry(model, it) }
        }
        val result = WeatherConditionConsensus.resolveDecision(
            nativeEntries = native,
            derivedEntries = derived,
            cloudCoverPercent = cloudCoverPercent,
            cloudCoverFamilyCount = cloudCoverFamilyCount,
            supportModels = byModel.map { it.first }
        )
        val winners = byModel.map { it.second }.filter { it.condition != null && it.condition == result.condition }
        val allAlternatives = byModel.flatMap { (_, d) -> listOfNotNull(d.condition) + d.alternatives }
            .filterNot { it == WeatherCondition.UNKNOWN }.distinct()
        val partial = winners.any { it.status == ConditionStatus.INCOMPLETE }
        val noneUsable = result.condition == null
        val status = when {
            noneUsable && allAlternatives.isNotEmpty() -> ConditionStatus.AMBIGUOUS
            noneUsable && byModel.any { it.second.status == ConditionStatus.INCOMPLETE } -> ConditionStatus.INCOMPLETE
            noneUsable -> ConditionStatus.UNAVAILABLE
            partial -> ConditionStatus.INCOMPLETE
            else -> result.status
        }
        return result.copy(
            status = status,
            alternatives = if (noneUsable) allAlternatives else result.alternatives,
            reasons = result.reasons + if (noneUsable) byModel.flatMap { it.second.reasons }
                else winners.flatMap { it.reasons },
            notablePhenomenon = winners.mapNotNull { it.notablePhenomenon }
                .maxByOrNull { it.severityRank }
        )
    }
}
