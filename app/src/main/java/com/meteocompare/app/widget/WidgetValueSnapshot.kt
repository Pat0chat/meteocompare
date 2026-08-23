package com.meteocompare.app.widget

import com.meteocompare.app.core.util.resolveZoneOrUtc
import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.DivergenceReason
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.ForecastInsight
import com.meteocompare.app.ui.citydetail.ForecastInsightKind
import com.meteocompare.app.ui.citydetail.ForecastInsightLevel
import com.meteocompare.app.ui.citydetail.ForecastMetric
import com.meteocompare.app.ui.citydetail.OverviewTimeline
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.buildForecastInsights
import com.meteocompare.app.ui.citydetail.buildOverviewTimeline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class WidgetInsightIcon {
    RAIN,
    WIND,
    TEMPERATURE,
    WEATHER,
    UNCERTAINTY,
    STABLE
}

internal data class WidgetKeyInsight(
    val title: String,
    val detail: String?,
    val timeLabel: String?,
    val level: ForecastInsightLevel,
    val icon: WidgetInsightIcon
)

internal enum class WidgetMetricType {
    TEMPERATURE,
    PRECIPITATION,
    WIND
}

internal data class WidgetMetricSnapshot(
    val type: WidgetMetricType,
    val consensusPercent: Int?,
    val divergent: Boolean
)

internal data class WidgetComparisonSnapshot(
    val atLabel: String,
    val overallConsensusPercent: Int?,
    val metrics: List<WidgetMetricSnapshot>
)

internal data class WidgetValueSnapshot(
    val keyInsight: WidgetKeyInsight?,
    val comparison: WidgetComparisonSnapshot?
)

/**
 * Construit le signal éditorial et ses indicateurs compacts depuis la même
 * chaîne d'agrégation que CityDetail : points multi-modèles -> événements -> insights.
 */
internal fun buildWidgetValueSnapshot(
    context: Context,
    forecast: CityForecast,
    now: Instant = Instant.now(),
    engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
): WidgetValueSnapshot {
    val overview = buildOverviewTimeline(forecast, now, engineContext)
    val insights = buildForecastInsights(overview)
    val comparisonPoint = overview.analysisPoints.firstOrNull { it.hasMultiModelEvidence }
        ?: overview.analysisPoints.firstOrNull()
    val comparison = comparisonPoint?.toWidgetComparisonSnapshot(context, overview, now)
    val keyInsight = selectWidgetKeyInsight(insights)?.toWidgetKeyInsight(context, overview)
        ?: comparison?.let { snapshot ->
            WidgetKeyInsight(
                title = context.getString(R.string.forecast_insight_title_stable_compact),
                detail = snapshot.overallConsensusPercent?.let { percent ->
                    context.getString(R.string.widget_value_detail_consensus, percent)
                } ?: context.getString(R.string.forecast_insight_detail_stable_compact),
                timeLabel = snapshot.atLabel,
                level = ForecastInsightLevel.POSITIVE,
                icon = WidgetInsightIcon.STABLE
            )
        }
    return WidgetValueSnapshot(
        keyInsight = keyInsight,
        comparison = comparison
    )
}

/** Le widget met en avant l'impact, pas la simple proximité temporelle. */
internal fun selectWidgetKeyInsight(insights: List<ForecastInsight>): ForecastInsight? =
    insights.maxWithOrNull(
        compareBy<ForecastInsight> { widgetInsightLevelWeight(it.level) }
            .thenBy(ForecastInsight::priority)
            .thenByDescending { insightInstantKey(it) }
    )

private fun widgetInsightLevelWeight(level: ForecastInsightLevel): Int = when (level) {
    ForecastInsightLevel.ALERT -> 4
    ForecastInsightLevel.WATCH -> 3
    ForecastInsightLevel.INFO -> 2
    ForecastInsightLevel.POSITIVE -> 1
}

private fun insightInstantKey(insight: ForecastInsight): Long =
    insight.point?.instant?.toEpochMilli()
        ?: insight.point?.date?.toEpochDay()
        ?: Long.MAX_VALUE

private fun ForecastInsight.toWidgetKeyInsight(
    context: Context,
    overview: OverviewTimeline
): WidgetKeyInsight {
    val evidence = event?.evidence
    val consensus = evidence?.consensus?.percent
    val range = widgetEvidenceRange(context, this)
    val models = evidence?.contributingModelCount?.takeIf { it > 0 }
    val detail = when {
        range != null && consensus != null -> context.getString(
            R.string.widget_value_detail_range_consensus,
            range,
            consensus
        )
        range != null && models != null -> context.resources.getQuantityString(
            R.plurals.widget_value_detail_range_models,
            models,
            range,
            models
        )
        consensus != null -> context.getString(
            R.string.widget_value_detail_consensus,
            consensus
        )
        kind == ForecastInsightKind.HIGH_AGREEMENT ->
            context.getString(R.string.forecast_insight_detail_stable_compact)
        else -> null
    }

    return WidgetKeyInsight(
        title = widgetInsightTitle(context, this),
        detail = detail,
        timeLabel = widgetInsightTimeLabel(context, this, overview),
        level = level,
        icon = when (kind) {
            ForecastInsightKind.RAIN_LIKELY,
            ForecastInsightKind.RAIN_UNCERTAIN -> WidgetInsightIcon.RAIN
            ForecastInsightKind.WIND_EVENT -> WidgetInsightIcon.WIND
            ForecastInsightKind.TEMPERATURE_CHANGE -> WidgetInsightIcon.TEMPERATURE
            ForecastInsightKind.WEATHER_CHANGE -> WidgetInsightIcon.WEATHER
            ForecastInsightKind.DISAGREEMENT -> WidgetInsightIcon.UNCERTAINTY
            ForecastInsightKind.HIGH_AGREEMENT -> WidgetInsightIcon.STABLE
        }
    )
}

private fun widgetInsightTitle(context: Context, insight: ForecastInsight): String = when (insight.kind) {
    ForecastInsightKind.HIGH_AGREEMENT ->
        context.getString(R.string.forecast_insight_title_stable_compact)
    ForecastInsightKind.DISAGREEMENT -> context.getString(
        when (primaryDivergenceReason(insight.divergenceReasons)) {
            DivergenceReason.PRECIPITATION -> R.string.forecast_insight_title_disagreement_rain
            DivergenceReason.WIND -> R.string.forecast_insight_title_disagreement_wind
            DivergenceReason.TEMPERATURE -> R.string.forecast_insight_title_disagreement_temperature
            DivergenceReason.CONDITION -> R.string.forecast_insight_title_disagreement_condition
            null -> R.string.forecast_insight_title_disagreement
        }
    )
    ForecastInsightKind.RAIN_LIKELY -> context.getString(
        when (insight.targetCondition) {
            WeatherCondition.THUNDERSTORM -> R.string.forecast_insight_title_weather_thunderstorm
            WeatherCondition.FREEZING_RAIN -> R.string.forecast_insight_title_weather_freezing_rain
            WeatherCondition.SNOW,
            WeatherCondition.SNOW_SHOWERS -> R.string.forecast_insight_title_weather_snow
            else -> if (insight.isStrengtheningRainSignal) {
                R.string.forecast_insight_title_rain_strengthening
            } else {
                R.string.forecast_insight_title_rain_likely
            }
        }
    )
    ForecastInsightKind.RAIN_UNCERTAIN ->
        context.getString(R.string.forecast_insight_title_rain_uncertain)
    ForecastInsightKind.WIND_EVENT -> {
        val baseline = insight.value
        val target = insight.secondaryValue
        val delta = if (baseline != null && target != null) target - baseline else 0
        context.getString(
            when {
                (target ?: 0) >= 60 -> R.string.forecast_insight_title_wind_very_strong
                delta >= 15 -> R.string.forecast_insight_title_wind_rising
                else -> R.string.forecast_insight_title_wind_strong
            }
        )
    }
    ForecastInsightKind.TEMPERATURE_CHANGE -> {
        val target = insight.targetValue
        val delta = insight.value ?: 0
        val uncertain = DivergenceReason.TEMPERATURE in insight.divergenceReasons
        context.getString(
            when {
                target != null && target <= 0 -> R.string.forecast_insight_title_temperature_frost
                target != null && target >= 35 -> R.string.forecast_insight_title_temperature_extreme_heat
                target != null && target >= 30 -> R.string.forecast_insight_title_temperature_heat
                uncertain -> R.string.forecast_insight_title_temperature_uncertain
                delta < 0 -> R.string.forecast_insight_title_temperature_unusual_cooling
                else -> R.string.forecast_insight_title_temperature_unusual_warming
            }
        )
    }
    ForecastInsightKind.WEATHER_CHANGE -> context.getString(
        when (insight.targetCondition) {
            WeatherCondition.FOG -> R.string.forecast_insight_title_weather_fog
            WeatherCondition.THUNDERSTORM -> R.string.forecast_insight_title_weather_thunderstorm
            WeatherCondition.FREEZING_RAIN -> R.string.forecast_insight_title_weather_freezing_rain
            WeatherCondition.SNOW,
            WeatherCondition.SNOW_SHOWERS -> R.string.forecast_insight_title_weather_snow
            else -> {
                val reference = insight.referenceCondition
                val target = insight.targetCondition
                val improves = reference != null && target != null &&
                    target.severityRank < reference.severityRank
                when {
                    improves -> R.string.forecast_insight_title_weather_improving
                    insight.level == ForecastInsightLevel.INFO ->
                        R.string.forecast_insight_title_weather_change
                    else -> R.string.forecast_insight_title_weather_worsening
                }
            }
        }
    )
}

private fun widgetEvidenceRange(context: Context, insight: ForecastInsight): String? {
    val evidence = insight.event?.evidence ?: return null
    val min = evidence.minimumValue
    val max = evidence.maximumValue
    return when (evidence.metric) {
        ForecastMetric.TEMPERATURE -> if (min != null && max != null) {
            context.getString(
                R.string.widget_value_temperature_range,
                min.roundToInt(),
                max.roundToInt()
            )
        } else null
        ForecastMetric.WIND -> if (min != null && max != null) {
            context.getString(
                R.string.widget_value_wind_range,
                min.roundToInt(),
                max.roundToInt()
            )
        } else null
        ForecastMetric.PRECIPITATION -> {
            val pMin = evidence.probabilityMinimum
            val pMax = evidence.probabilityMaximum
            when {
                pMin != null && pMax != null -> context.getString(
                    R.string.widget_value_probability_range,
                    pMin,
                    pMax
                )
                min != null && max != null -> context.getString(
                    R.string.widget_value_precip_range,
                    min,
                    max
                )
                else -> null
            }
        }
        ForecastMetric.CONDITION,
        null -> null
    }
}

private fun widgetInsightTimeLabel(
    context: Context,
    insight: ForecastInsight,
    overview: OverviewTimeline
): String? {
    val point = insight.point ?: return null
    return when (overview.mode) {
        DisplayMode.HOURLY -> {
            val instant = point.instant ?: return null
            val zone = resolveWidgetZone(overview.timezone)
            context.getString(R.string.widget_value_hour, instant.atZone(zone).hour)
        }
        DisplayMode.DAILY -> {
            val date = point.date ?: return null
            val formatter = DateTimeFormatter.ofPattern("EEE d", currentLocale(context))
            date.format(formatter).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(currentLocale(context)) else char.toString()
            }
        }
    }
}

private fun SimplifiedTimelinePoint.toWidgetComparisonSnapshot(
    context: Context,
    overview: OverviewTimeline,
    now: Instant
): WidgetComparisonSnapshot {
    val metrics = buildList {
        if (temperatureC != null || tempMaxC != null) {
            val consensus = consensusFor(ForecastMetric.TEMPERATURE)
            add(
                WidgetMetricSnapshot(
                    type = WidgetMetricType.TEMPERATURE,
                    consensusPercent = consensus?.percent,
                    divergent = consensus?.isDivergent == true ||
                        DivergenceReason.TEMPERATURE in divergenceReasons
                )
            )
        }

        if (precipitationPercent != null || precipitationMm != null) {
            val consensus = consensusFor(ForecastMetric.PRECIPITATION)
            add(
                WidgetMetricSnapshot(
                    type = WidgetMetricType.PRECIPITATION,
                    consensusPercent = consensus?.percent,
                    divergent = consensus?.isDivergent == true ||
                        DivergenceReason.PRECIPITATION in divergenceReasons
                )
            )
        }

        if (windKmh != null) {
            val consensus = consensusFor(ForecastMetric.WIND)
            add(
                WidgetMetricSnapshot(
                    type = WidgetMetricType.WIND,
                    consensusPercent = consensus?.percent,
                    divergent = consensus?.isDivergent == true ||
                        DivergenceReason.WIND in divergenceReasons
                )
            )
        }
    }

    return WidgetComparisonSnapshot(
        atLabel = comparisonTimeLabel(context, overview, this, now),
        overallConsensusPercent = consensusPercent,
        metrics = metrics
    )
}

private fun comparisonTimeLabel(
    context: Context,
    overview: OverviewTimeline,
    point: SimplifiedTimelinePoint,
    now: Instant
): String = when (overview.mode) {
    DisplayMode.HOURLY -> {
        val instant = point.instant
        if (instant != null && abs(instant.toEpochMilli() - now.toEpochMilli()) <= 90 * 60 * 1000L) {
            context.getString(R.string.widget_value_now)
        } else if (instant != null) {
            val hour = instant.atZone(resolveWidgetZone(overview.timezone)).hour
            context.getString(R.string.widget_value_hour, hour)
        } else {
            context.getString(R.string.widget_value_next)
        }
    }
    DisplayMode.DAILY -> point.date?.format(
        DateTimeFormatter.ofPattern("EEE d", currentLocale(context))
    ) ?: context.getString(R.string.widget_value_next)
}

private fun primaryDivergenceReason(reasons: Set<DivergenceReason>): DivergenceReason? =
    listOf(
        DivergenceReason.PRECIPITATION,
        DivergenceReason.WIND,
        DivergenceReason.TEMPERATURE,
        DivergenceReason.CONDITION
    ).firstOrNull { it in reasons }

private fun resolveWidgetZone(timezone: String?): ZoneId = resolveZoneOrUtc(timezone)

private fun currentLocale(context: Context): Locale =
    context.resources.configuration.locales[0]
