package com.meteocompare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilanceInterval
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.VigilancePhenomenonAlert
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

const val TAG_VIGILANCE_HOME = "vigilance-home"
const val TAG_VIGILANCE_DETAIL = "vigilance-detail"
const val TAG_VIGILANCE_MARINE = "vigilance-marine"

@Composable
fun VigilanceCompactBanner(
    vigilance: VigilanceForecast,
    timezone: String?,
    modifier: Modifier = Modifier
) {
    val alert = vigilance.activeAlerts.firstOrNull() ?: return
    val color = vigilanceColor(alert.maxColor)
    val content = readableContentColor(color)
    val interval = alert.intervals.firstOrNull()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_VIGILANCE_HOME),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.34f else 0.20f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.vigilance_compact_title,
                        vigilanceColorLabel(alert.maxColor)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(vigilancePhenomenonLabel(alert.phenomenon))
                        interval?.let {
                            val timing = compactIntervalLabel(it, timezone)
                            if (timing.isNotBlank()) append(" · ").append(timing)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (vigilance.isStale) {
                    stringResource(R.string.vigilance_source_cached_short)
                } else {
                    stringResource(R.string.vigilance_source_short)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VigilanceDetailCard(
    vigilance: VigilanceForecast,
    timezone: String?,
    modifier: Modifier = Modifier
) {
    val alerts = vigilance.activeAlerts
    if (alerts.isEmpty()) return
    val maxColor = vigilance.maxAlertColor ?: return
    val color = vigilanceColor(maxColor)
    val content = readableContentColor(color)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_VIGILANCE_DETAIL),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vigilance_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.vigilance_official_source),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val updatedAt = vigilance.updateTime ?: vigilance.productDatetime ?: vigilance.generationTimestamp
                    if (updatedAt != null) {
                        Text(
                            text = stringResource(
                                R.string.vigilance_updated_at,
                                vigilanceUpdateLabel(updatedAt, timezone)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                VigilanceLevelPill(maxColor)
            }

            if (vigilance.isStale) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = stringResource(R.string.vigilance_cached_warning),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(Modifier.height(12.dp))

            alerts.forEachIndexed { index, alert ->
                VigilanceAlertRow(alert = alert, vigilance = vigilance, timezone = timezone)
                if (index != alerts.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun MarineCoastalVigilanceBanner(
    alert: VigilancePhenomenonAlert?,
    timezone: String?,
    modifier: Modifier = Modifier
) {
    alert ?: return
    val color = vigilanceColor(alert.maxColor)
    val content = readableContentColor(color)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_VIGILANCE_MARINE),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.28f else 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Waves, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.vigilance_marine_title,
                        vigilanceColorLabel(alert.maxColor)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val intervalText = alert.intervals.firstOrNull()?.let { compactIntervalLabel(it, timezone) }.orEmpty()
                Text(
                    text = if (intervalText.isBlank()) {
                        stringResource(R.string.vigilance_phenomenon_coastal_flooding)
                    } else {
                        stringResource(R.string.vigilance_marine_timing, intervalText)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VigilanceAlertRow(
    alert: VigilancePhenomenonAlert,
    vigilance: VigilanceForecast,
    timezone: String?
) {
    val color = vigilanceColor(alert.maxColor)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(50)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = vigilancePhenomenonLabel(alert.phenomenon),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = vigilanceColorLabel(alert.maxColor),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(7.dp))
        VigilanceTimeline(
            alert = alert,
            periodsBegin = vigilance.periods.mapNotNull { it.begin }.minOrNull(),
            periodsEnd = vigilance.periods.mapNotNull { it.end }.maxOrNull(),
            timezone = timezone
        )
        if (alert.intervals.any(VigilanceInterval::timingApproximate)) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.vigilance_timing_approximate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VigilanceTimeline(
    alert: VigilancePhenomenonAlert,
    periodsBegin: Instant?,
    periodsEnd: Instant?,
    timezone: String?
) {
    val intervalInstants = alert.intervals.flatMap { listOfNotNull(it.begin, it.end) }
    val start = periodsBegin ?: intervalInstants.minOrNull() ?: return
    val end = periodsEnd ?: intervalInstants.maxOrNull() ?: return
    val totalMs = max(1L, end.toEpochMilli() - start.toEpochMilli())
    val locale = LocalLocale.current.platformLocale
    val zone = resolveZone(timezone)
    val tickFormatter = DateTimeFormatter.ofPattern("EEE HH'h'", locale).withZone(zone)

    Column {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val width = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(5.dp))
            )
            alert.intervals
                .filter { it.color.id >= VigilanceColor.YELLOW.id }
                .forEach { interval ->
                    val begin = interval.begin ?: start
                    val finish = interval.end ?: end
                    val startFraction = ((begin.toEpochMilli() - start.toEpochMilli()).toDouble() / totalMs)
                        .coerceIn(0.0, 1.0)
                    val endFraction = ((finish.toEpochMilli() - start.toEpochMilli()).toDouble() / totalMs)
                        .coerceIn(startFraction, 1.0)
                    val segmentWidth = width * (endFraction - startFraction).toFloat()
                    val offset = width * startFraction.toFloat()
                    Box(
                        modifier = Modifier
                            .offset(x = offset)
                            .width(segmentWidth.coerceAtLeast(3.dp))
                            .height(10.dp)
                            .background(vigilanceColor(interval.color), RoundedCornerShape(5.dp))
                    )
                }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = tickFormatter.format(start),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = tickFormatter.format(start.plusMillis(totalMs / 2)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = tickFormatter.format(end),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VigilanceLevelPill(color: VigilanceColor) {
    val base = vigilanceColor(color)
    val content = readableContentColor(base)
    Surface(color = base, shape = RoundedCornerShape(50)) {
        Text(
            text = vigilanceColorLabel(color),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

@Composable
private fun vigilanceColorLabel(color: VigilanceColor): String = stringResource(
    when (color) {
        VigilanceColor.GREEN -> R.string.vigilance_color_green
        VigilanceColor.YELLOW -> R.string.vigilance_color_yellow
        VigilanceColor.ORANGE -> R.string.vigilance_color_orange
        VigilanceColor.RED -> R.string.vigilance_color_red
    }
)

@Composable
private fun vigilancePhenomenonLabel(phenomenon: VigilancePhenomenon): String = stringResource(
    when (phenomenon) {
        VigilancePhenomenon.WIND -> R.string.vigilance_phenomenon_wind
        VigilancePhenomenon.RAIN_FLOOD -> R.string.vigilance_phenomenon_rain_flood
        VigilancePhenomenon.THUNDERSTORMS -> R.string.vigilance_phenomenon_thunderstorms
        VigilancePhenomenon.FLOODS -> R.string.vigilance_phenomenon_floods
        VigilancePhenomenon.SNOW_ICE -> R.string.vigilance_phenomenon_snow_ice
        VigilancePhenomenon.HEATWAVE -> R.string.vigilance_phenomenon_heatwave
        VigilancePhenomenon.EXTREME_COLD -> R.string.vigilance_phenomenon_extreme_cold
        VigilancePhenomenon.AVALANCHES -> R.string.vigilance_phenomenon_avalanches
        VigilancePhenomenon.COASTAL_FLOODING -> R.string.vigilance_phenomenon_coastal_flooding
        VigilancePhenomenon.UNKNOWN -> R.string.vigilance_phenomenon_unknown
    }
)

private fun vigilanceColor(color: VigilanceColor): Color = when (color) {
    VigilanceColor.GREEN -> Color(0xFF43A047)
    VigilanceColor.YELLOW -> Color(0xFFFFC928)
    VigilanceColor.ORANGE -> Color(0xFFF57C00)
    VigilanceColor.RED -> Color(0xFFD32F2F)
}

private fun readableContentColor(background: Color): Color =
    if (background.luminance() > 0.52f) Color(0xFF1A1A1A) else Color.White

@Composable
private fun compactIntervalLabel(interval: VigilanceInterval, timezone: String?): String {
    val begin = interval.begin ?: return ""
    val end = interval.end ?: return ""
    val locale = LocalLocale.current.platformLocale
    val formatter = DateTimeFormatter.ofPattern("HH'h'", locale).withZone(resolveZone(timezone))
    return stringResource(R.string.vigilance_interval_compact, formatter.format(begin), formatter.format(end))
}

private fun vigilanceUpdateLabel(instant: Instant, timezone: String?): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM · HH:mm").withZone(resolveZone(timezone))
    return formatter.format(instant)
}

private fun resolveZone(timezone: String?): ZoneId = runCatching {
    ZoneId.of(timezone ?: "Europe/Paris")
}.getOrDefault(ZoneId.of("Europe/Paris"))
