package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Vue chronologique légère placée avant les tableaux détaillés.
 *
 * Les valeurs sont des médianes multi-modèles. Elles ne remplacent pas les
 * tableaux : elles donnent une lecture immédiate avant l'analyse détaillée.
 */
@Composable
internal fun SimplifiedTimelineCard(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    timezone: String?,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val precipitationAccent = precipitationMetricAccent()
    val windAccent = windMetricAccent()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.timeline_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        if (mode == DisplayMode.HOURLY) {
                            R.string.timeline_subtitle_hourly
                        } else {
                            R.string.timeline_subtitle_daily
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                points.forEachIndexed { index, point ->
                    TimelinePointCard(
                        point = point,
                        mode = mode,
                        label = when {
                            point.instant != null -> point.instant.atZone(zone).format(hourFormatter)
                            point.date != null -> point.date.format(dayFormatter)
                                .replaceFirstChar { it.uppercase() }
                            else -> "—"
                        },
                        precipitationAccent = precipitationAccent,
                        windAccent = windAccent,
                        isFirst = index == 0
                    )
                }
            }

            if (points.any { it.isDivergent }) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.timeline_disagreement_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelinePointCard(
    point: SimplifiedTimelinePoint,
    mode: DisplayMode,
    label: String,
    precipitationAccent: Color,
    windAccent: Color,
    isFirst: Boolean
) {
    val background = if (isFirst) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .width(82.dp)
            .background(background, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isFirst) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isFirst) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
            if (point.isDivergent) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        point.condition?.let {
            WeatherIconDecorative(
                condition = it,
                size = 27.dp,
                tint = it.semanticTint()
            )
        } ?: Spacer(Modifier.height(27.dp))

        Spacer(Modifier.height(4.dp))

        Text(
            text = when (mode) {
                DisplayMode.HOURLY -> point.temperatureC?.let { "${it.roundToInt()}°" } ?: "—"
                DisplayMode.DAILY -> {
                    val min = point.tempMinC?.roundToInt()?.toString() ?: "—"
                    val max = point.tempMaxC?.roundToInt()?.toString() ?: "—"
                    "$min° / $max°"
                }
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        Spacer(Modifier.height(5.dp))

        TimelineMetric(
            icon = Icons.Outlined.WaterDrop,
            value = when (point.precipitationSource) {
                PrecipitationSignalSource.MODEL_PROBABILITY ->
                    point.precipitationPercent?.let { "$it%" } ?: "—"
                PrecipitationSignalSource.MODEL_AGREEMENT ->
                    if (point.precipitationModelCount >= 2) {
                        "${point.wetModelCount}/${point.precipitationModelCount}"
                    } else {
                        "—"
                    }
                null -> "—"
            },
            tint = precipitationAccent
        )
        Spacer(Modifier.height(2.dp))
        TimelineMetric(
            icon = Icons.Outlined.Air,
            value = point.windKmh?.let { "${it.roundToInt()} km/h" } ?: "—",
            tint = windAccent
        )
    }
}

@Composable
private fun TimelineMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
