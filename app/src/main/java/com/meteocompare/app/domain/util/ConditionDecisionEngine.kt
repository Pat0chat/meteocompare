package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.SolarSkyInterpretation
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.effectiveCloudCover
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * A decision, not a calibrated probability. `agreement` is deliberately separate from
 * confidence: ensemble members can share biases and numeric model lineage.
 * Never convert a lack of observations into a seemingly precise weather icon.
 */
enum class ConditionStatus { RELIABLE, ESTIMATED, INCOMPLETE, AMBIGUOUS, UNAVAILABLE }
enum class ConditionOrigin { HOURLY_WMO, SAME_MODEL_VARIABLES, HOURLY_DOMINANT, DAILY_WMO_MAXIMUM, MODEL_CONSENSUS, NONE }
enum class ConditionReason {
    MISSING_DATA, INVALID_WMO, INVALID_PHYSICAL_VALUE, PARTIAL_CLOUD_LAYERS,
    PRECEDING_HOUR_PRECIPITATION, PRECIPITATION_PHASE_UNCERTAIN,
    PARTIAL_PERIOD, IRREVERSIBLE_MAJORITY, REPRESENTATIVE_SAMPLES,
    NO_DOMINANT_CONDITION, MISSING_SOLAR_WINDOW, MISSING_FOLLOWING_SUNRISE,
    MODEL_DISAGREEMENT, DAILY_WMO_IS_MAXIMUM
}
data class ConditionDecision(
    val condition: WeatherCondition?,
    val status: ConditionStatus,
    val origin: ConditionOrigin,
    val coverage: Double = 0.0,
    val agreement: Double? = null,
    val alternatives: List<WeatherCondition> = emptyList(),
    val reasons: Set<ConditionReason> = emptySet(),
    /** Most severe *represented* hourly phenomenon; never substitutes the dominant icon. */
    val notablePhenomenon: WeatherCondition? = null
) {
    init {
        require(coverage.isFinite() && coverage in 0.0..1.0)
        require(agreement == null || (agreement.isFinite() && agreement in 0.0..1.0))
        require(condition != WeatherCondition.UNKNOWN)
    }
}

/**
 * One source of truth for model-local hourly and day/night classification.
 * No interpolation across time or between models; precipitation accumulations refer
 * to the *preceding* hour and are weaker evidence than an instantaneous WMO code.
 */
internal object ConditionDecisionEngine {
    private const val HOUR_SECONDS = 3600L
    private const val MIN_COVERAGE = 0.75
    private const val EPS = 1e-9

    fun hourly(hourly: HourlyForecast, index: Int): ConditionDecision {
        if (index !in hourly.timestamps.indices) return missing()
        val code = hourly.weatherCode.getOrNull(index)
        val native = WeatherCondition.fromWmoCode(code)?.takeUnless { it == WeatherCondition.UNKNOWN }
        if (native != null) {
            val cloud = hourly.effectiveCloudCover(index)
            val next = hourly.timestamps.indexOf(hourly.timestamps[index].plusSeconds(HOUR_SECONDS))
            val veil = native == WeatherCondition.OVERCAST && SolarSkyInterpretation.isVeiledSky(
                cloud, hourly.cloudCoverLow.getOrNull(index), hourly.cloudCoverMid.getOrNull(index),
                hourly.cloudCoverHigh.getOrNull(index), hourly.sunshineDuration.getOrNull(next),
                hourly.directNormalIrradiance.getOrNull(next)
            )
            return ConditionDecision(
                if (veil) WeatherCondition.VEILED else native,
                ConditionStatus.RELIABLE, ConditionOrigin.HOURLY_WMO, coverage = 1.0
            )
        }
        val reasons = mutableSetOf<ConditionReason>()
        if (code != null) reasons += ConditionReason.INVALID_WMO
        val precipitation = hourly.precipitation.getOrNull(index)
        if (precipitation != null && (!precipitation.isFinite() || precipitation < 0.0 || precipitation > 1000.0)) {
            reasons += ConditionReason.INVALID_PHYSICAL_VALUE
        }
        val validPrecipitation = precipitation?.takeIf { it.isFinite() && it in 0.0..1000.0 }
        if (validPrecipitation != null && validPrecipitation > 0.1) {
            // Near freezing, 2m temperature alone cannot establish the precipitation phase.
            // For warm air, a liquid classification is an explicitly marked estimate.
            // At/below +2C return an ambiguous result rather than falsely asserting snow.
            val temperature = hourly.temperature2m.getOrNull(index)?.takeIf(Double::isFinite)
            val estimatedLiquid = when {
                validPrecipitation >= 5.0 -> WeatherCondition.RAIN
                validPrecipitation >= 1.0 -> WeatherCondition.RAIN_SHOWERS
                else -> WeatherCondition.DRIZZLE
            }
            reasons += ConditionReason.PRECEDING_HOUR_PRECIPITATION
            if (temperature == null || temperature <= 2.0) {
                return ConditionDecision(null, ConditionStatus.AMBIGUOUS,
                    ConditionOrigin.SAME_MODEL_VARIABLES, coverage = 1.0,
                    alternatives = listOf(estimatedLiquid, WeatherCondition.SNOW),
                    reasons = reasons + ConditionReason.PRECIPITATION_PHASE_UNCERTAIN)
            }
            return ConditionDecision(estimatedLiquid, ConditionStatus.ESTIMATED,
                ConditionOrigin.SAME_MODEL_VARIABLES, coverage = 1.0, reasons = reasons)
        }

        // The total is authoritative; if absent, all 3 layers are needed for the
        // legacy estimate. With fewer layers, infer a sky *class* only if every
        // physically admissible total yields exactly the same class.
        val cloud = hourly.effectiveCloudCover(index)
        val sky = if (cloud != null) WeatherCondition.fromCloudCover(cloud.toDouble()) else {
            val layers = listOf(hourly.cloudCoverLow.getOrNull(index),
                hourly.cloudCoverMid.getOrNull(index), hourly.cloudCoverHigh.getOrNull(index))
            val valid = layers.mapNotNull { it?.takeIf { n -> n in 0..100 } }
            if (valid.isNotEmpty()) {
                reasons += ConditionReason.PARTIAL_CLOUD_LAYERS
                val lower = valid.maxOrNull() ?: 0
                // Unknown layers could cover the entire sky; no optimistic fill.
                val upper = if (valid.size < 3) 100 else min(100, valid.sum())
                WeatherCondition.fromCloudCover(lower.toDouble())
                    .takeIf { it == WeatherCondition.fromCloudCover(upper.toDouble()) }
            } else null
        }
        if (sky != null) {
            val next = hourly.timestamps.indexOf(hourly.timestamps[index].plusSeconds(HOUR_SECONDS))
            val veiled = SolarSkyInterpretation.isVeiledSky(
                cloud, hourly.cloudCoverLow.getOrNull(index), hourly.cloudCoverMid.getOrNull(index),
                hourly.cloudCoverHigh.getOrNull(index), hourly.sunshineDuration.getOrNull(next),
                hourly.directNormalIrradiance.getOrNull(next)
            )
            return ConditionDecision(SolarSkyInterpretation.refine(sky, veiled),
                ConditionStatus.ESTIMATED, ConditionOrigin.SAME_MODEL_VARIABLES,
                coverage = 1.0, reasons = reasons)
        }
        return missing(reasons + ConditionReason.MISSING_DATA)
    }

    fun period(
        series: ForecastSeries,
        date: LocalDate,
        zone: ZoneId,
        period: DailyConditionPeriod = DailyConditionPeriod.FULL_DAY,
        solarWindow: ForecastSolarWindow? = null,
        nextSolarWindow: ForecastSolarWindow? = null
    ): ConditionDecision {
        val daily = series.daily
        val dailyIndex = daily.dates.indexOf(date)
        val window = solarWindow ?: dailyIndex.takeIf { it >= 0 }?.let {
            ForecastSolarWindow(daily.sunrise.getOrNull(it), daily.sunset.getOrNull(it))
        }
        val nextIndex = daily.dates.indexOf(date.plusDays(1))
        val following = nextSolarWindow ?: nextIndex.takeIf { it >= 0 }?.let {
            ForecastSolarWindow(daily.sunrise.getOrNull(it), daily.sunset.getOrNull(it))
        }
        val start = when (period) {
            DailyConditionPeriod.FULL_DAY -> date.atStartOfDay(zone).toInstant()
            DailyConditionPeriod.DAY -> window?.takeIf { it.isValid }?.sunrise
            DailyConditionPeriod.NIGHT -> window?.takeIf { it.isValid }?.sunset
        } ?: return missing(setOf(ConditionReason.MISSING_SOLAR_WINDOW))
        val end = when (period) {
            DailyConditionPeriod.FULL_DAY -> date.plusDays(1).atStartOfDay(zone).toInstant()
            DailyConditionPeriod.DAY -> window?.sunset
            DailyConditionPeriod.NIGHT -> following?.takeIf { it.isValid }?.sunrise
        } ?: return missing(setOf(if (period == DailyConditionPeriod.NIGHT)
            ConditionReason.MISSING_FOLLOWING_SUNRISE else ConditionReason.MISSING_SOLAR_WINDOW))
        if (end <= start) return missing(setOf(ConditionReason.MISSING_SOLAR_WINDOW))
        return periodBetween(series, start, end, dailyIndex, period == DailyConditionPeriod.FULL_DAY)
    }

    /** For deterministic fixture testing, independent of sunrise/sunset lookup. */
    internal fun periodBetween(
        series: ForecastSeries, start: Instant, end: Instant,
        dailyIndex: Int = -1, allowDailyMaximum: Boolean = false
    ): ConditionDecision {
        val duration = Duration.between(start, end).seconds
        if (duration <= 0) return missing()
        // WMO at H describes the weather at H, represented for the bucket [H,H+1h).
        // Use real UTC instants: DST creates 23/25 distinct buckets, never duplicates.
        // Deduplicate repeated instants; native WMO supersedes derived evidence.
        val buckets = linkedMapOf<Instant, ConditionDecision>()
        series.hourly.timestamps.indices.forEach { i ->
            val timestamp = series.hourly.timestamps[i].truncatedTo(ChronoUnit.HOURS)
            if (timestamp >= end || timestamp.plusSeconds(HOUR_SECONDS) <= start) return@forEach
            val decision = hourly(series.hourly, i)
            val previous = buckets[timestamp]
            if (previous == null || (previous.origin != ConditionOrigin.HOURLY_WMO &&
                decision.origin == ConditionOrigin.HOURLY_WMO) ||
                (previous.condition == null && decision.condition != null)) {
                buckets[timestamp] = decision
            }
        }
        val counts = mutableMapOf<WeatherCondition, Long>()
        val coveredRanges = mutableListOf<Pair<Instant, Instant>>()
        var hasDerived = false
        buckets.toSortedMap().forEach { (hour, decision) ->
            val c = decision.condition ?: return@forEach
            val sliceStart = if (hour > start) hour else start
            val bucketEnd = hour.plusSeconds(HOUR_SECONDS)
            val sliceEnd = if (bucketEnd < end) bucketEnd else end
            val seconds = Duration.between(sliceStart, sliceEnd).seconds
            if (seconds <= 0) return@forEach
            counts[c] = (counts[c] ?: 0L) + seconds
            coveredRanges.add(sliceStart to sliceEnd)
            if (decision.origin != ConditionOrigin.HOURLY_WMO) hasDerived = true
        }
        val covered = counts.values.sum().coerceAtMost(duration)
        val coverage = covered.toDouble() / duration
        val risk = counts.keys.maxByOrNull { it.severityRank }
        val reasons = mutableSetOf<ConditionReason>()
        if (covered < duration) reasons += ConditionReason.PARTIAL_PERIOD
        val origin = if (hasDerived) ConditionOrigin.SAME_MODEL_VARIABLES else ConditionOrigin.HOURLY_DOMINANT
        // A leaf that occupies >half the complete period wins regardless of *any*
        // unknown hours, gaps or contradictory daily maximum.
        val certain = counts.entries.firstOrNull { it.value.toDouble() > duration / 2.0 + EPS }?.key
        if (certain != null) return ConditionDecision(certain,
            when {
                covered < duration -> ConditionStatus.INCOMPLETE
                hasDerived -> ConditionStatus.ESTIMATED
                else -> ConditionStatus.RELIABLE
            },
            origin, coverage, reasons = reasons + ConditionReason.IRREVERSIBLE_MAJORITY,
            notablePhenomenon = risk)

        val ranges = coveredRanges.sortedBy { it.first }
        val maxGapSeconds = (listOf(start to (ranges.firstOrNull()?.first ?: end)) +
            ranges.zipWithNext().map { (a,b) -> a.second to b.first } +
            listOf((ranges.lastOrNull()?.second ?: start) to end))
            .maxOfOrNull { (a,b) -> max(0L, Duration.between(a,b).seconds) } ?: duration
        val edgesRepresented = ranges.firstOrNull()?.first?.let { Duration.between(start, it).seconds <= 7200L } == true &&
            ranges.lastOrNull()?.second?.let { Duration.between(it, end).seconds <= 7200L } == true
        val representative = coverage >= MIN_COVERAGE && edgesRepresented && maxGapSeconds <= 10800L
        if (representative) {
            // Same hierarchical logic as other views. Repeat per integral hour only;
            // for fractional sunrise/sunset the weighted root majority is validated
            // separately, so a 5-minute edge never outweighs 55 minutes.
            val drySeconds = counts.filterKeys { it.isSky || it == WeatherCondition.FOG }.values.sum()
            val wetSeconds = covered - drySeconds
            if (kotlin.math.abs(drySeconds - wetSeconds) > 0L) {
                val weighted = counts.flatMap { (condition, seconds) ->
                    List(max(1, (seconds / 900L).toInt())) { condition }
                }
                val proposed = WeatherConditionConsensus.resolveUnweighted(weighted)
                if (proposed != null) return ConditionDecision(proposed,
                    when {
                        covered < duration -> ConditionStatus.INCOMPLETE
                        hasDerived -> ConditionStatus.ESTIMATED
                        else -> ConditionStatus.RELIABLE
                    },
                    origin, coverage, reasons = reasons + ConditionReason.REPRESENTATIVE_SAMPLES,
                    notablePhenomenon = risk)
            }
            return ConditionDecision(null, ConditionStatus.AMBIGUOUS, origin, coverage,
                alternatives = counts.entries.sortedByDescending { it.value }.map { it.key }.take(3),
                reasons = reasons + ConditionReason.NO_DOMINANT_CONDITION,
                notablePhenomenon = risk)
        }
        // A dry/wet family may already dominate, but no sub-type is proven.
        // Never replace this by a *daily severe maximum* of the opposite family.
        val drySeconds = counts.filterKeys { it.isSky || it == WeatherCondition.FOG }.values.sum()
        val wetSeconds = covered - drySeconds
        if (max(drySeconds, wetSeconds).toDouble() > duration / 2.0 + EPS) {
            return ConditionDecision(null, ConditionStatus.INCOMPLETE, origin, coverage,
                alternatives = counts.entries.sortedByDescending { it.value }.map { it.key }.take(3),
                reasons = reasons + ConditionReason.NO_DOMINANT_CONDITION, notablePhenomenon = risk)
        }
        val fallback = if (allowDailyMaximum) WeatherCondition.fromWmoCode(
            dailyIndex.takeIf { it >= 0 }?.let { series.daily.weatherCode.getOrNull(it) }
        )?.takeUnless { it == WeatherCondition.UNKNOWN } else null
        if (fallback != null) return ConditionDecision(fallback, ConditionStatus.ESTIMATED,
            ConditionOrigin.DAILY_WMO_MAXIMUM, coverage,
            reasons = reasons + ConditionReason.DAILY_WMO_IS_MAXIMUM,
            notablePhenomenon = risk ?: fallback)
        return ConditionDecision(null,
            if (covered > 0L) ConditionStatus.INCOMPLETE else ConditionStatus.UNAVAILABLE,
            ConditionOrigin.NONE, coverage, reasons = reasons + ConditionReason.MISSING_DATA,
            notablePhenomenon = risk)
    }

    private fun missing(reasons: Set<ConditionReason> = setOf(ConditionReason.MISSING_DATA)) =
        ConditionDecision(null, ConditionStatus.UNAVAILABLE, ConditionOrigin.NONE, reasons = reasons)
}
