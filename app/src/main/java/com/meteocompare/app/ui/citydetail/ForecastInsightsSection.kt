package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.format.DateTimeFormatter

@Composable
internal fun ForecastInsightsSection(
    insights: List<ForecastInsight>,
    timezone: String?,
    modifier: Modifier = Modifier,
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
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(
                text = stringResource(R.string.forecast_insights_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            insights.forEachIndexed { index, insight ->
                ForecastInsightRow(
                    insight = insight,
                    timezone = timezone,
                    referencePoint = referencePoint,
                    isHighlighted = index == 0,
                    onClick = onInsightClick?.let { callback -> { callback(insight) } }
                )
            }
        }
    }
}

@Composable
private fun ForecastInsightRow(
    insight: ForecastInsight,
    timezone: String?,
    referencePoint: SimplifiedTimelinePoint?,
    isHighlighted: Boolean,
    onClick: (() -> Unit)?
) {
    val visual = insightVisual(insight)
    val presentation = forecastInsightPresentation(insight, timezone, referencePoint)
    val iconContainerSize = if (isHighlighted) 42.dp else 36.dp
    val iconSize = if (isHighlighted) 22.dp else 19.dp
    val shape = RoundedCornerShape(if (isHighlighted) 15.dp else 13.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) {
                    Modifier.background(visual.color.copy(alpha = 0.09f), shape)
                } else {
                    Modifier
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
                horizontal = if (isHighlighted) 12.dp else 2.dp,
                vertical = if (isHighlighted) 12.dp else 4.dp
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
                    visual.color.copy(alpha = if (isHighlighted) 0.17f else 0.11f),
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

        Spacer(Modifier.width(if (isHighlighted) 12.dp else 11.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isHighlighted) {
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
                    maxLines = if (isHighlighted) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
            Icons.Outlined.WaterDrop,
            if (insight.level == ForecastInsightLevel.ALERT) levelColor else precipitationMetricAccent()
        )
        ForecastInsightKind.WIND_RISING -> InsightVisual(
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
    return when (insight.kind) {
        ForecastInsightKind.HIGH_AGREEMENT -> ForecastInsightPresentation(
            title = stringResource(R.string.forecast_insight_title_high_agreement),
            detail = stringResource(R.string.forecast_insight_detail_high_agreement),
            timeLabel = timeLabel
        )
        ForecastInsightKind.DISAGREEMENT -> ForecastInsightPresentation(
            title = disagreementTitle(insight.divergenceReasons),
            detail = disagreementDetail(insight.divergenceReasons),
            timeLabel = timeLabel
        )
        ForecastInsightKind.RAIN_LIKELY -> ForecastInsightPresentation(
            title = stringResource(R.string.forecast_insight_title_rain_likely),
            detail = rainDetail(insight, uncertain = false),
            timeLabel = timeLabel
        )
        ForecastInsightKind.RAIN_UNCERTAIN -> ForecastInsightPresentation(
            title = stringResource(R.string.forecast_insight_title_rain_uncertain),
            detail = rainDetail(insight, uncertain = true),
            timeLabel = timeLabel
        )
        ForecastInsightKind.WIND_RISING -> ForecastInsightPresentation(
            title = stringResource(R.string.forecast_insight_title_wind_rising),
            detail = stringResource(
                R.string.forecast_insight_detail_wind_rising,
                insight.value ?: 0,
                insight.secondaryValue ?: insight.value ?: 0
            ),
            timeLabel = timeLabel
        )
        ForecastInsightKind.TEMPERATURE_CHANGE -> {
            val delta = insight.value ?: 0
            val (referenceLabel, targetLabel) = forecastComparisonLabels(
                referencePoint = insight.referencePoint,
                targetPoint = insight.point,
                timezone = timezone
            )
            val referenceTemperature = insight.referenceValue
            val targetTemperature = insight.targetValue
            ForecastInsightPresentation(
                title = stringResource(
                    if (delta >= 0) R.string.forecast_insight_title_temperature_rising
                    else R.string.forecast_insight_title_temperature_falling
                ),
                detail = when {
                    referenceTemperature == null || targetTemperature == null -> stringResource(
                        if (delta >= 0) R.string.forecast_insight_temperature_rising_fallback
                        else R.string.forecast_insight_temperature_falling_fallback,
                        kotlin.math.abs(delta),
                        targetLabel
                    )
                    delta >= 0 -> stringResource(
                        R.string.forecast_insight_temperature_rising,
                        referenceLabel,
                        targetLabel,
                        referenceTemperature,
                        targetTemperature,
                        delta
                    )
                    else -> stringResource(
                        R.string.forecast_insight_temperature_falling,
                        referenceLabel,
                        targetLabel,
                        referenceTemperature,
                        targetTemperature,
                        -delta
                    )
                },
                timeLabel = timeLabel
            )
        }
    }
}

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
private fun rainDetail(insight: ForecastInsight, uncertain: Boolean): String =
    when (insight.precipitationSource) {
        PrecipitationSignalSource.MODEL_PROBABILITY -> stringResource(
            if (uncertain) R.string.forecast_insight_detail_rain_probability_uncertain
            else R.string.forecast_insight_detail_rain_probability,
            insight.value ?: 0,
            insight.secondaryValue ?: 0
        )
        PrecipitationSignalSource.MODEL_AGREEMENT -> stringResource(
            if (uncertain) R.string.forecast_insight_detail_rain_models_uncertain
            else R.string.forecast_insight_detail_rain_models,
            insight.value ?: 0,
            insight.secondaryValue ?: 0
        )
        null -> stringResource(R.string.forecast_insight_detail_rain_generic)
    }

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
private fun forecastComparisonLabels(
    referencePoint: SimplifiedTimelinePoint?,
    targetPoint: SimplifiedTimelinePoint?,
    timezone: String?
): Pair<String, String> {
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val crossDayFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE HH'h'", locale)
    }
    val referenceInstant = referencePoint?.instant
    val targetInstant = targetPoint?.instant
    if (referenceInstant != null && targetInstant != null) {
        val referenceDate = referenceInstant.atZone(zone).toLocalDate()
        val targetDate = targetInstant.atZone(zone).toLocalDate()
        if (referenceDate != targetDate) {
            return referenceInstant.atZone(zone).format(crossDayFormatter) to
                targetInstant.atZone(zone).format(crossDayFormatter)
        }
    }

    return forecastPointLabel(referencePoint, null, timezone).orEmpty() to
        forecastPointLabel(targetPoint, null, timezone).orEmpty()
}

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
internal const val TAG_FORECAST_INSIGHT_PRIMARY = "forecast_insight_primary"
internal const val TAG_FORECAST_INSIGHT_SECONDARY = "forecast_insight_secondary"
