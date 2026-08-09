package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun ForecastInsightsSection(
    insights: List<ForecastInsight>,
    timezone: String?,
    modifier: Modifier = Modifier,
    modelCount: Int? = null,
    referencePoint: SimplifiedTimelinePoint? = null,
    onInsightClick: ((ForecastInsight) -> Unit)? = null
) {
    if (insights.isEmpty()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TAG_FORECAST_INSIGHTS_SECTION),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            ForecastInsightsHeader(
                insights = insights,
                availableModelCount = modelCount
            )

            val stableOnly = insights.size == 1 &&
                insights.first().kind == ForecastInsightKind.HIGH_AGREEMENT
            if (stableOnly) {
                StableInsightRow(insight = insights.first())
            } else {
                insights.forEach { insight ->
                    ForecastInsightRow(
                        insight = insight,
                        timezone = timezone,
                        referencePoint = referencePoint,
                        isHighlighted = insight.level == ForecastInsightLevel.ALERT,
                        isWatch = insight.level == ForecastInsightLevel.WATCH,
                        onClick = onInsightClick?.let { callback -> { callback(insight) } }
                    )
                }
            }

            if (onInsightClick != null && !stableOnly) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                Text(
                    text = stringResource(R.string.forecast_insights_timeline_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_FORECAST_INSIGHTS_TIMELINE_HINT)
                )
            }
        }
    }
}

@Composable
private fun ForecastInsightsHeader(
    insights: List<ForecastInsight>,
    availableModelCount: Int?
) {
    val modelCount = availableModelCount
        ?.takeIf { it > 0 }
        ?: (insights.maxOfOrNull { it.point?.modelCount ?: 0 } ?: 0)
    val hasHourlyPoint = insights.any { it.point?.instant != null }
    val hasDailyPoint = insights.any { it.point?.date != null }
    val modelsLabel = if (modelCount > 0) {
        pluralStringResource(
            R.plurals.forecast_insights_models,
            modelCount,
            modelCount
        )
    } else {
        null
    }
    val subtitle = when {
        modelsLabel == null -> stringResource(R.string.forecast_insights_subtitle_generic)
        hasHourlyPoint -> stringResource(
            R.string.forecast_insights_subtitle_hourly,
            modelsLabel
        )
        hasDailyPoint -> stringResource(
            R.string.forecast_insights_subtitle_daily,
            modelsLabel
        )
        else -> stringResource(R.string.forecast_insights_subtitle_generic)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.forecast_insights_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_FORECAST_INSIGHTS_SUMMARY)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastInsightRow(
    insight: ForecastInsight,
    timezone: String?,
    referencePoint: SimplifiedTimelinePoint?,
    isHighlighted: Boolean,
    isWatch: Boolean,
    onClick: (() -> Unit)?
) {
    val visual = insightVisual(insight)
    val presentation = forecastInsightPresentation(insight, timezone, referencePoint)
    val metrics = forecastInsightMetrics(insight)
    val isEmphasized = isHighlighted || isWatch
    val iconContainerSize = if (isHighlighted) 42.dp else 36.dp
    val iconSize = if (isHighlighted) 22.dp else 19.dp
    val shape = RoundedCornerShape(if (isHighlighted) 15.dp else 13.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isHighlighted -> Modifier.background(visual.color.copy(alpha = 0.10f), shape)
                    isWatch -> Modifier.background(visual.color.copy(alpha = 0.045f), shape)
                    else -> Modifier
                }
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (isEmphasized) 12.dp else 2.dp,
                vertical = if (isHighlighted) 12.dp else if (isWatch) 9.dp else 4.dp
            )
            .testTag(
                if (isHighlighted) TAG_FORECAST_INSIGHT_PRIMARY
                else TAG_FORECAST_INSIGHT_SECONDARY
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(iconContainerSize)
                .background(
                    visual.color.copy(alpha = if (isEmphasized) 0.17f else 0.11f),
                    RoundedCornerShape(if (isHighlighted) 14.dp else 12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.color,
                modifier = Modifier.size(iconSize)
            )
        }

        Spacer(Modifier.width(if (isEmphasized) 12.dp else 11.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isEmphasized) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = insightLevelLabel(insight.level),
                        style = MaterialTheme.typography.labelSmall,
                        color = visual.color,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    presentation.timeLabel?.let { time ->
                        InsightTimeChip(time = time, color = visual.color)
                    }
                }

                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                // Sans badge de niveau, le titre prend sa place dans la même
                // ligne que l'heure. Il reste ainsi aligné avec l'icône au lieu
                // d'être repoussé sous une ligne vide.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = presentation.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    presentation.timeLabel?.let { time ->
                        InsightTimeChip(time = time, color = visual.color)
                    }
                }
            }

            presentation.detail?.takeIf(String::isNotBlank)?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = if (isEmphasized) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (metrics.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .testTag(TAG_FORECAST_INSIGHT_METRICS),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    metrics.forEach { metric ->
                        InsightMetricChip(metric = metric, color = visual.color)
                    }
                }
            }
        }
    }
}

@Composable
private fun StableInsightRow(insight: ForecastInsight) {
    val visual = insightVisual(insight)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(TAG_FORECAST_INSIGHT_STABLE),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = visual.color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.forecast_insight_title_stable_compact),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.forecast_insight_detail_stable_compact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InsightTimeChip(time: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.10f)
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

private data class InsightMetric(
    val icon: ImageVector,
    val text: String? = null,
    val contentDescription: String? = null,
    val testTag: String? = null
)

@Composable
private fun forecastInsightMetrics(insight: ForecastInsight): List<InsightMetric> {
    val point = insight.point
    return when (insight.kind) {
        ForecastInsightKind.HIGH_AGREEMENT -> buildList {
            (insight.value ?: point?.consensusPercent)?.let { percent ->
                add(
                    InsightMetric(
                        icon = Icons.Outlined.CheckCircle,
                        text = stringResource(R.string.forecast_insight_metric_consensus, percent)
                    )
                )
            }
        }
        ForecastInsightKind.DISAGREEMENT -> buildList {
            insight.divergenceReasons.forEach { reason ->
                add(
                    InsightMetric(
                        icon = divergenceReasonIcon(reason),
                        contentDescription = divergenceReasonLabel(reason),
                        testTag = "${TAG_FORECAST_INSIGHT_REASON_PREFIX}${reason.name.lowercase()}"
                    )
                )
            }
            (insight.event?.evidence?.consensus?.percent ?: point?.consensusPercent)?.let { percent ->
                add(
                    InsightMetric(
                        icon = Icons.Outlined.WarningAmber,
                        text = stringResource(R.string.forecast_insight_metric_consensus, percent),
                        testTag = TAG_FORECAST_INSIGHT_CONSENSUS
                    )
                )
            }
        }
        ForecastInsightKind.RAIN_LIKELY,
        ForecastInsightKind.RAIN_UNCERTAIN -> buildList {
            val evidence = insight.event?.evidence
            val probabilityMin = evidence?.probabilityMinimum
            val probabilityMax = evidence?.probabilityMaximum
            when {
                probabilityMin != null && probabilityMax != null && probabilityMax - probabilityMin >= 10 -> {
                    add(
                        InsightMetric(
                            icon = Icons.Outlined.WaterDrop,
                            text = stringResource(
                                R.string.forecast_insight_metric_probability_range,
                                probabilityMin,
                                probabilityMax
                            )
                        )
                    )
                }
                insight.precipitationSource == PrecipitationSignalSource.MODEL_PROBABILITY -> {
                    (insight.targetValue ?: insight.value)?.let { probability ->
                        add(
                            InsightMetric(
                                icon = Icons.Outlined.WaterDrop,
                                text = stringResource(
                                    R.string.forecast_insight_metric_probability,
                                    probability
                                )
                            )
                        )
                    }
                }
                insight.precipitationSource == PrecipitationSignalSource.MODEL_AGREEMENT -> {
                    val wetModels = evidence?.wetModelCount ?: insight.value
                    val totalModels = evidence?.contributingModelCount ?: insight.secondaryValue
                    if (wetModels != null && totalModels != null && totalModels > 0) {
                        add(
                            InsightMetric(
                                icon = Icons.Outlined.WaterDrop,
                                text = stringResource(
                                    R.string.forecast_insight_metric_model_ratio,
                                    wetModels,
                                    totalModels
                                )
                            )
                        )
                    }
                }
            }
            val rainConsensus = evidence?.consensus
            if (rainConsensus != null && (rainConsensus.isDivergent || rainConsensus.percent < 75)) {
                add(
                    InsightMetric(
                        icon = Icons.Outlined.WarningAmber,
                        text = stringResource(
                            R.string.forecast_insight_metric_consensus,
                            rainConsensus.percent
                        )
                    )
                )
            } else {
                val minimumMm = evidence?.minimumValue
                val maximumMm = evidence?.maximumValue
                val medianMm = evidence?.medianValue
                when {
                    minimumMm != null && maximumMm != null && maximumMm - minimumMm >= 1.0 -> {
                        add(
                            InsightMetric(
                                icon = Icons.Outlined.WaterDrop,
                                text = stringResource(
                                    R.string.forecast_insight_metric_precipitation_range,
                                    minimumMm,
                                    maximumMm
                                )
                            )
                        )
                    }
                    medianMm != null && medianMm >= 0.2 -> {
                        add(
                            InsightMetric(
                                icon = Icons.Outlined.WaterDrop,
                                text = stringResource(
                                    R.string.forecast_insight_metric_precipitation,
                                    medianMm
                                )
                            )
                        )
                    }
                    else -> {
                        val contributors = evidence?.contributingModelCount ?: insight.secondaryValue
                        val available = evidence?.availableModelCount ?: point?.modelCount
                        if (contributors != null && available != null && contributors in 1 until available) {
                            add(
                                InsightMetric(
                                    icon = Icons.Outlined.CheckCircle,
                                    text = stringResource(
                                        R.string.forecast_insight_metric_coverage,
                                        contributors,
                                        available
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
        ForecastInsightKind.WEATHER_CHANGE -> emptyList()
        ForecastInsightKind.WIND_EVENT -> buildList {
            val evidence = insight.event?.evidence
            val min = evidence?.minimumValue?.roundToInt()
            val max = evidence?.maximumValue?.roundToInt()
            val target = insight.secondaryValue ?: evidence?.medianValue?.roundToInt()
            if (min != null && max != null && max - min >= 8) {
                add(
                    InsightMetric(
                        icon = Icons.Outlined.Air,
                        text = stringResource(R.string.forecast_insight_metric_wind_range, min, max)
                    )
                )
            } else target?.let { targetWind ->
                add(
                    InsightMetric(
                        icon = Icons.Outlined.Air,
                        text = stringResource(R.string.forecast_insight_metric_wind, targetWind)
                    )
                )
            }
            evidence?.consensus?.takeIf { it.isDivergent || it.percent < 75 }?.let { consensus ->
                add(
                    InsightMetric(
                        icon = Icons.Outlined.WarningAmber,
                        text = stringResource(R.string.forecast_insight_metric_consensus, consensus.percent)
                    )
                )
            }
        }
        ForecastInsightKind.TEMPERATURE_CHANGE -> buildList {
            val evidence = insight.event?.evidence
            val min = evidence?.minimumValue?.roundToInt()
            val max = evidence?.maximumValue?.roundToInt()
            if (min != null && max != null && max - min >= 2) {
                add(
                    InsightMetric(
                        icon = Icons.Outlined.Thermostat,
                        text = stringResource(
                            R.string.forecast_insight_metric_temperature_scenarios,
                            min,
                            max
                        )
                    )
                )
            } else {
                val reference = insight.referenceValue
                val target = insight.targetValue
                if (reference != null && target != null) {
                    add(
                        InsightMetric(
                            icon = Icons.Outlined.Thermostat,
                            text = stringResource(
                                R.string.forecast_insight_metric_temperature_range,
                                reference,
                                target
                            )
                        )
                    )
                }
            }
            evidence?.consensus?.takeIf { it.isDivergent || it.percent < 75 }?.let { consensus ->
                add(
                    InsightMetric(
                        icon = Icons.Outlined.WarningAmber,
                        text = stringResource(R.string.forecast_insight_metric_consensus, consensus.percent)
                    )
                )
            }
        }
    }
}

@Composable
private fun InsightMetricChip(metric: InsightMetric, color: Color) {
    val chipModifier = Modifier
        .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
        .then(metric.testTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier)

    Surface(
        modifier = chipModifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.085f)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (metric.text == null) 7.dp else 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = metric.icon,
                contentDescription = metric.contentDescription,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            metric.text?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun divergenceReasonLabel(reason: DivergenceReason): String = stringResource(
    when (reason) {
        DivergenceReason.PRECIPITATION -> R.string.timeline_divergence_rain
        DivergenceReason.WIND -> R.string.timeline_divergence_wind
        DivergenceReason.TEMPERATURE -> R.string.timeline_divergence_temperature
        DivergenceReason.CONDITION -> R.string.timeline_divergence_condition
    }
)

private fun divergenceReasonIcon(reason: DivergenceReason): ImageVector = when (reason) {
    DivergenceReason.PRECIPITATION -> Icons.Outlined.WaterDrop
    DivergenceReason.WIND -> Icons.Outlined.Air
    DivergenceReason.TEMPERATURE -> Icons.Outlined.Thermostat
    DivergenceReason.CONDITION -> Icons.Outlined.Cloud
}

private data class InsightVisual(
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun insightVisual(insight: ForecastInsight): InsightVisual {
    val levelColor = when (insight.level) {
        ForecastInsightLevel.ALERT -> MaterialTheme.colorScheme.error
        ForecastInsightLevel.WATCH -> MaterialTheme.colorScheme.tertiary
        ForecastInsightLevel.INFO -> MaterialTheme.colorScheme.secondary
        ForecastInsightLevel.POSITIVE -> MaterialTheme.colorScheme.primary
    }
    return when (insight.kind) {
        ForecastInsightKind.HIGH_AGREEMENT -> InsightVisual(
            Icons.Outlined.CheckCircle,
            levelColor
        )
        ForecastInsightKind.DISAGREEMENT -> InsightVisual(
            Icons.Outlined.WarningAmber,
            levelColor
        )
        ForecastInsightKind.RAIN_LIKELY,
        ForecastInsightKind.RAIN_UNCERTAIN -> InsightVisual(
            icon = precipitationInsightIcon(insight.targetCondition),
            color = if (insight.level == ForecastInsightLevel.ALERT) {
                levelColor
            } else {
                precipitationMetricAccent()
            }
        )
        ForecastInsightKind.WEATHER_CHANGE -> InsightVisual(
            Icons.Outlined.Cloud,
            levelColor
        )
        ForecastInsightKind.WIND_EVENT -> InsightVisual(
            Icons.Outlined.Air,
            if (insight.level == ForecastInsightLevel.ALERT) levelColor else windMetricAccent()
        )
        ForecastInsightKind.TEMPERATURE_CHANGE -> InsightVisual(
            Icons.Outlined.Thermostat,
            if (insight.level == ForecastInsightLevel.ALERT) levelColor else temperatureMetricAccent()
        )
    }
}

private data class ForecastInsightPresentation(
    val title: String,
    val detail: String?,
    val timeLabel: String?
)

@Composable
private fun forecastInsightPresentation(
    insight: ForecastInsight,
    timezone: String?,
    referencePoint: SimplifiedTimelinePoint?
): ForecastInsightPresentation {
    val timeLabel = forecastPointLabel(insight.point, referencePoint, timezone)
    val endLabel = insight.endPoint
        ?.takeUnless { end -> insight.point?.let { sameTimelinePoint(it, end) } == true }
        ?.let { end -> forecastPointLabel(end, referencePoint, timezone) }

    return when (insight.kind) {
        ForecastInsightKind.HIGH_AGREEMENT -> ForecastInsightPresentation(
            title = stringResource(R.string.forecast_insight_title_high_agreement),
            detail = stringResource(R.string.forecast_insight_detail_high_agreement),
            timeLabel = timeLabel
        )
        ForecastInsightKind.DISAGREEMENT -> ForecastInsightPresentation(
            title = if (insight.divergenceReasons.size >= 2) {
                stringResource(R.string.forecast_insight_title_disagreement_multiple)
            } else {
                disagreementTitle(insight.divergenceReasons)
            },
            detail = if (insight.divergenceReasons.size >= 2) {
                stringResource(R.string.forecast_insight_detail_disagreement_multiple)
            } else {
                disagreementDetail(insight.divergenceReasons)
            },
            timeLabel = timeLabel
        )
        ForecastInsightKind.RAIN_LIKELY -> ForecastInsightPresentation(
            title = likelyPrecipitationTitle(insight),
            detail = likelyPrecipitationDetail(insight, endLabel),
            timeLabel = timeLabel
        )
        ForecastInsightKind.RAIN_UNCERTAIN -> ForecastInsightPresentation(
            title = stringResource(R.string.forecast_insight_title_rain_uncertain),
            detail = if (insight.isPersistent && endLabel != null) {
                stringResource(R.string.forecast_insight_detail_rain_uncertain_persistent, endLabel)
            } else {
                stringResource(R.string.forecast_insight_detail_rain_fused_uncertainty)
            },
            timeLabel = timeLabel
        )
        ForecastInsightKind.WEATHER_CHANGE -> {
            val referenceCondition = insight.referenceCondition
            val targetCondition = insight.targetCondition
            val improves = referenceCondition != null && targetCondition != null &&
                targetCondition.severityRank < referenceCondition.severityRank
            ForecastInsightPresentation(
                title = weatherChangeTitle(targetCondition, improves, insight.level),
                detail = if (referenceCondition != null && targetCondition != null) {
                    stringResource(
                        R.string.forecast_insight_detail_weather_transition,
                        weatherConditionLabel(referenceCondition),
                        weatherConditionLabel(targetCondition)
                    )
                } else {
                    null
                },
                timeLabel = timeLabel
            )
        }
        ForecastInsightKind.WIND_EVENT -> {
            val baseline = insight.value
            val target = insight.secondaryValue
            val delta = if (baseline != null && target != null) target - baseline else 0
            ForecastInsightPresentation(
                title = stringResource(
                    when {
                        (target ?: 0) >= 60 -> R.string.forecast_insight_title_wind_very_strong
                        delta >= 15 -> R.string.forecast_insight_title_wind_rising
                        else -> R.string.forecast_insight_title_wind_strong
                    }
                ),
                detail = stringResource(
                    when {
                        delta >= 15 -> R.string.forecast_insight_detail_wind_rising_short
                        insight.isPersistent -> R.string.forecast_insight_detail_wind_strong
                        else -> R.string.forecast_insight_detail_wind_peak
                    }
                ),
                timeLabel = timeLabel
            )
        }
        ForecastInsightKind.TEMPERATURE_CHANGE -> {
            val delta = insight.value ?: 0
            val target = insight.targetValue
            val hasUncertainty = DivergenceReason.TEMPERATURE in insight.divergenceReasons
            ForecastInsightPresentation(
                title = stringResource(
                    when {
                        target != null && target <= 0 -> R.string.forecast_insight_title_temperature_frost
                        target != null && target >= 35 -> R.string.forecast_insight_title_temperature_extreme_heat
                        target != null && target >= 30 -> R.string.forecast_insight_title_temperature_heat
                        hasUncertainty -> R.string.forecast_insight_title_temperature_uncertain
                        delta < 0 -> R.string.forecast_insight_title_temperature_unusual_cooling
                        else -> R.string.forecast_insight_title_temperature_unusual_warming
                    }
                ),
                detail = stringResource(
                    when {
                        target != null && target <= 0 -> R.string.forecast_insight_detail_temperature_frost
                        target != null && target >= 30 -> R.string.forecast_insight_detail_temperature_heat
                        hasUncertainty -> R.string.forecast_insight_detail_temperature_uncertain
                        else -> R.string.forecast_insight_detail_temperature_unusual
                    }
                ),
                timeLabel = timeLabel
            )
        }
    }
}

private fun precipitationInsightIcon(condition: WeatherCondition?): ImageVector = when (condition) {
    WeatherCondition.THUNDERSTORM,
    WeatherCondition.FREEZING_RAIN -> Icons.Outlined.WarningAmber
    WeatherCondition.SNOW,
    WeatherCondition.SNOW_SHOWERS -> Icons.Outlined.Cloud
    else -> Icons.Outlined.WaterDrop
}

@Composable
private fun likelyPrecipitationTitle(insight: ForecastInsight): String = stringResource(
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

@Composable
private fun likelyPrecipitationDetail(
    insight: ForecastInsight,
    endLabel: String?
): String {
    val uncertain = DivergenceReason.PRECIPITATION in insight.divergenceReasons ||
        insight.event?.evidence?.consensus?.isDivergent == true
    return when (insight.targetCondition) {
        WeatherCondition.THUNDERSTORM ->
            stringResource(
                if (uncertain) R.string.forecast_insight_detail_rain_thunderstorm_uncertain
                else R.string.forecast_insight_detail_rain_thunderstorm
            )
        WeatherCondition.FREEZING_RAIN ->
            stringResource(R.string.forecast_insight_detail_rain_freezing)
        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS ->
            stringResource(R.string.forecast_insight_detail_rain_snow)
        else -> when {
            uncertain -> stringResource(R.string.forecast_insight_detail_rain_fused_uncertainty)
            insight.isStrengtheningRainSignal -> stringResource(
                R.string.forecast_insight_detail_rain_strengthening
            )
            insight.isPersistent && endLabel != null -> stringResource(
                R.string.forecast_insight_detail_rain_persistent,
                endLabel
            )
            else -> stringResource(R.string.forecast_insight_detail_rain_likely_short)
        }
    }
}

@Composable
private fun weatherChangeTitle(
    targetCondition: WeatherCondition?,
    improves: Boolean,
    level: ForecastInsightLevel
): String = stringResource(
    when {
        targetCondition == WeatherCondition.FOG -> R.string.forecast_insight_title_weather_fog
        targetCondition == WeatherCondition.THUNDERSTORM ->
            R.string.forecast_insight_title_weather_thunderstorm
        targetCondition == WeatherCondition.FREEZING_RAIN ->
            R.string.forecast_insight_title_weather_freezing_rain
        targetCondition in setOf(WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS) ->
            R.string.forecast_insight_title_weather_snow
        improves -> R.string.forecast_insight_title_weather_improving
        level == ForecastInsightLevel.INFO -> R.string.forecast_insight_title_weather_change
        else -> R.string.forecast_insight_title_weather_worsening
    }
)

@Composable
private fun insightLevelLabel(level: ForecastInsightLevel): String = stringResource(
    when (level) {
        ForecastInsightLevel.ALERT -> R.string.forecast_insight_level_alert
        ForecastInsightLevel.WATCH -> R.string.forecast_insight_level_watch
        ForecastInsightLevel.INFO -> R.string.forecast_insight_level_info
        ForecastInsightLevel.POSITIVE -> R.string.forecast_insight_level_positive
    }
)


@Composable
private fun weatherConditionLabel(condition: WeatherCondition): String = stringResource(
    when (condition) {
        WeatherCondition.CLEAR -> R.string.weather_clear
        WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherCondition.OVERCAST -> R.string.weather_overcast
        WeatherCondition.FOG -> R.string.weather_fog
        WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
        WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
        WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
        WeatherCondition.UNKNOWN -> R.string.weather_unknown
    }
)

@Composable
private fun disagreementTitle(reasons: Set<DivergenceReason>): String = stringResource(
    when (primaryDivergenceReason(reasons)) {
        DivergenceReason.PRECIPITATION -> R.string.forecast_insight_title_disagreement_rain
        DivergenceReason.WIND -> R.string.forecast_insight_title_disagreement_wind
        DivergenceReason.TEMPERATURE -> R.string.forecast_insight_title_disagreement_temperature
        DivergenceReason.CONDITION -> R.string.forecast_insight_title_disagreement_condition
        null -> R.string.forecast_insight_title_disagreement
    }
)

@Composable
private fun disagreementDetail(reasons: Set<DivergenceReason>): String = stringResource(
    when (primaryDivergenceReason(reasons)) {
        DivergenceReason.PRECIPITATION -> R.string.forecast_insight_detail_disagreement_rain
        DivergenceReason.WIND -> R.string.forecast_insight_detail_disagreement_wind
        DivergenceReason.TEMPERATURE -> R.string.forecast_insight_detail_disagreement_temperature
        DivergenceReason.CONDITION -> R.string.forecast_insight_detail_disagreement_condition
        null -> R.string.forecast_insight_detail_disagreement
    }
)

private fun primaryDivergenceReason(reasons: Set<DivergenceReason>): DivergenceReason? =
    listOf(
        DivergenceReason.PRECIPITATION,
        DivergenceReason.WIND,
        DivergenceReason.TEMPERATURE,
        DivergenceReason.CONDITION
    ).firstOrNull { it in reasons }


@Composable
private fun forecastPointLabel(
    point: SimplifiedTimelinePoint?,
    referencePoint: SimplifiedTimelinePoint?,
    timezone: String?
): String? {
    if (point == null) return null
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }
    val shortDayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val fullDayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }

    point.instant?.let { instant ->
        if (referencePoint?.instant == instant) return stringResource(R.string.timeline_now)
        val target = instant.atZone(zone)
        val referenceDate = referencePoint?.instant?.atZone(zone)?.toLocalDate()
        val hour = target.format(hourFormatter)
        return when {
            referenceDate == null || target.toLocalDate() == referenceDate -> hour
            target.toLocalDate() == referenceDate.plusDays(1) ->
                stringResource(R.string.timeline_tomorrow_at, hour)
            else -> "${target.format(shortDayFormatter).replaceFirstChar { it.uppercase() }} · $hour"
        }
    }

    point.date?.let { date ->
        val referenceDate = referencePoint?.date
        return when {
            referenceDate == date -> stringResource(R.string.timeline_today)
            referenceDate != null && date == referenceDate.plusDays(1) ->
                stringResource(R.string.timeline_tomorrow)
            else -> date.format(fullDayFormatter).replaceFirstChar { it.uppercase() }
        }
    }

    return null
}

internal const val TAG_FORECAST_INSIGHTS_SECTION = "forecast_insights_section"
internal const val TAG_FORECAST_INSIGHTS_SUMMARY = "forecast_insights_summary"
internal const val TAG_FORECAST_INSIGHTS_TIMELINE_HINT = "forecast_insights_timeline_hint"
internal const val TAG_FORECAST_INSIGHT_PRIMARY = "forecast_insight_primary"
internal const val TAG_FORECAST_INSIGHT_STABLE = "forecast_insight_stable"
internal const val TAG_FORECAST_INSIGHT_SECONDARY = "forecast_insight_secondary"
internal const val TAG_FORECAST_INSIGHT_METRICS = "forecast_insight_metrics"
internal const val TAG_FORECAST_INSIGHT_REASON_PREFIX = "forecast_insight_reason_"
internal const val TAG_FORECAST_INSIGHT_CONSENSUS = "forecast_insight_consensus"
