package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
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
                    isHighlighted = index == 0
                )
            }
        }
    }
}

@Composable
private fun ForecastInsightRow(
    insight: ForecastInsight,
    timezone: String?,
    isHighlighted: Boolean
) {
    val visual = insightVisual(insight.kind)
    val iconContainerSize = if (isHighlighted) 40.dp else 34.dp
    val iconSize = if (isHighlighted) 21.dp else 18.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) {
                    Modifier
                        .background(
                            color = visual.color.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                } else {
                    Modifier
                }
            )
            .testTag(
                if (isHighlighted) {
                    TAG_FORECAST_INSIGHT_PRIMARY
                } else {
                    TAG_FORECAST_INSIGHT_SECONDARY
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(iconContainerSize)
                .background(
                    visual.color.copy(alpha = if (isHighlighted) 0.16f else 0.11f),
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
        Text(
            text = forecastInsightText(insight, timezone),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class InsightVisual(
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun insightVisual(kind: ForecastInsightKind): InsightVisual = when (kind) {
    ForecastInsightKind.HIGH_AGREEMENT -> InsightVisual(
        Icons.Outlined.CheckCircle,
        MaterialTheme.colorScheme.primary
    )
    ForecastInsightKind.DISAGREEMENT -> InsightVisual(
        Icons.Outlined.WarningAmber,
        MaterialTheme.colorScheme.error
    )
    ForecastInsightKind.RAIN_LIKELY,
    ForecastInsightKind.RAIN_UNCERTAIN -> InsightVisual(
        Icons.Outlined.WaterDrop,
        precipitationMetricAccent()
    )
    ForecastInsightKind.WIND_RISING -> InsightVisual(
        Icons.Outlined.Air,
        windMetricAccent()
    )
    ForecastInsightKind.TEMPERATURE_CHANGE -> InsightVisual(
        Icons.Outlined.Thermostat,
        temperatureMetricAccent()
    )
}

@Composable
private fun forecastInsightText(
    insight: ForecastInsight,
    timezone: String?
): String {
    val whenLabel = forecastPointLabel(insight.point, timezone)
    return when (insight.kind) {
        ForecastInsightKind.HIGH_AGREEMENT ->
            stringResource(R.string.forecast_insight_high_agreement, whenLabel)
        ForecastInsightKind.DISAGREEMENT ->
            stringResource(R.string.forecast_insight_disagreement, whenLabel)
        ForecastInsightKind.RAIN_LIKELY ->
            when (insight.precipitationSource) {
                PrecipitationSignalSource.MODEL_AGREEMENT -> stringResource(
                    R.string.forecast_insight_rain_models_likely,
                    insight.value ?: 0,
                    insight.secondaryValue ?: 0,
                    whenLabel
                )
                else -> stringResource(
                    R.string.forecast_insight_rain_likely,
                    insight.value ?: 0,
                    whenLabel
                )
            }
        ForecastInsightKind.RAIN_UNCERTAIN ->
            when (insight.precipitationSource) {
                PrecipitationSignalSource.MODEL_AGREEMENT -> stringResource(
                    R.string.forecast_insight_rain_models_split,
                    insight.value ?: 0,
                    insight.secondaryValue ?: 0,
                    whenLabel
                )
                else -> stringResource(
                    R.string.forecast_insight_rain_uncertain,
                    insight.value ?: 0,
                    whenLabel
                )
            }
        ForecastInsightKind.WIND_RISING ->
            stringResource(
                R.string.forecast_insight_wind_rising,
                insight.secondaryValue ?: insight.value ?: 0,
                whenLabel
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

            if (referenceTemperature == null || targetTemperature == null) {
                // Compatibilité défensive avec un éventuel état ancien reconstruit sans
                // les nouvelles valeurs explicites. Le texte reste compréhensible et
                // indique clairement que la comparaison part de la première échéance.
                if (delta >= 0) {
                    stringResource(
                        R.string.forecast_insight_temperature_rising_fallback,
                        delta,
                        targetLabel
                    )
                } else {
                    stringResource(
                        R.string.forecast_insight_temperature_falling_fallback,
                        -delta,
                        targetLabel
                    )
                }
            } else if (delta >= 0) {
                stringResource(
                    R.string.forecast_insight_temperature_rising,
                    referenceLabel,
                    targetLabel,
                    referenceTemperature,
                    targetTemperature,
                    delta
                )
            } else {
                stringResource(
                    R.string.forecast_insight_temperature_falling,
                    referenceLabel,
                    targetLabel,
                    referenceTemperature,
                    targetTemperature,
                    -delta
                )
            }
        }
    }
}

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

    return forecastPointLabel(referencePoint, timezone) to
        forecastPointLabel(targetPoint, timezone)
}

@Composable
private fun forecastPointLabel(
    point: SimplifiedTimelinePoint?,
    timezone: String?
): String {
    if (point == null) return stringResource(R.string.forecast_insight_soon)
    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }

    return when {
        point.instant != null -> point.instant.atZone(zone).format(hourFormatter)
        point.date != null -> point.date.format(dayFormatter).replaceFirstChar { it.uppercase() }
        else -> stringResource(R.string.forecast_insight_soon)
    }
}

internal const val TAG_FORECAST_INSIGHTS_SECTION = "forecast_insights_section"
internal const val TAG_FORECAST_INSIGHT_PRIMARY = "forecast_insight_primary"
internal const val TAG_FORECAST_INSIGHT_SECONDARY = "forecast_insight_secondary"
