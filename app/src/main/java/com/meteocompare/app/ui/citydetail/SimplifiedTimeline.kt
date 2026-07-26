package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Vue chronologique légère placée avant les tableaux détaillés.
 *
 * Les valeurs principales sont des médianes multi-modèles. Les plages et le
 * badge d'accord rendent la dispersion visible sans remplacer les tableaux.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SimplifiedTimelineCard(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    timezone: String?,
    modifier: Modifier = Modifier,
    focusPoint: SimplifiedTimelinePoint? = null,
    focusRequestId: Int = 0
) {
    if (points.isEmpty()) return

    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val hourFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val precipitationAccent = precipitationMetricAccent()
    val windAccent = windMetricAccent()
    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(listState)
    var highlightedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusRequestId, focusPoint, points) {
        if (focusRequestId <= 0 || focusPoint == null) return@LaunchedEffect
        val index = points.indexOfFirst { sameTimelinePoint(it, focusPoint) }
        if (index < 0) return@LaunchedEffect
        val key = timelinePointKey(points[index])
        listState.animateScrollToItem(index)
        highlightedKey = key
        delay(FOCUS_HIGHLIGHT_MILLIS)
        if (highlightedKey == key) highlightedKey = null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TAG_SIMPLIFIED_TIMELINE),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(
                        if (mode == DisplayMode.HOURLY) {
                            R.string.timeline_title_hourly
                        } else {
                            R.string.timeline_title_daily
                        }
                    ),
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

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                flingBehavior = snapFlingBehavior
            ) {
                itemsIndexed(
                    items = points,
                    key = { _, point -> timelinePointKey(point) }
                ) { index, point ->
                    val labels = timelineLabels(
                        point = point,
                        index = index,
                        points = points,
                        mode = mode,
                        zone = zone,
                        hourFormatter = hourFormatter,
                        dayFormatter = dayFormatter
                    )
                    TimelinePointCard(
                        point = point,
                        mode = mode,
                        label = labels.timeLabel,
                        dayLabel = labels.dayLabel,
                        precipitationAccent = precipitationAccent,
                        windAccent = windAccent,
                        isFirst = index == 0,
                        isFocused = highlightedKey == timelinePointKey(point)
                    )
                }
            }

            if (points.any { it.isDivergent }) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
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

private data class TimelineLabels(
    val timeLabel: String,
    val dayLabel: String?
)

@Composable
private fun timelineLabels(
    point: SimplifiedTimelinePoint,
    index: Int,
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    zone: ZoneId,
    hourFormatter: DateTimeFormatter,
    dayFormatter: DateTimeFormatter
): TimelineLabels {
    if (mode == DisplayMode.DAILY) {
        val date = point.date
        val firstDate = points.firstOrNull()?.date
        val label = when {
            date == null -> "—"
            index == 0 -> stringResource(R.string.timeline_today)
            firstDate != null && date == firstDate.plusDays(1) -> stringResource(R.string.timeline_tomorrow)
            else -> date.format(dayFormatter).replaceFirstChar { it.uppercase() }
        }
        return TimelineLabels(label, null)
    }

    val zoned = point.instant?.atZone(zone)
    val currentDate = zoned?.toLocalDate()
    val previousDate = points.getOrNull(index - 1)?.instant?.atZone(zone)?.toLocalDate()
    val firstDate = points.firstOrNull()?.instant?.atZone(zone)?.toLocalDate()
    val dayLabel = when {
        index == 0 || currentDate == null || currentDate == previousDate -> null
        firstDate != null && currentDate == firstDate.plusDays(1) -> stringResource(R.string.timeline_tomorrow)
        else -> currentDate.format(dayFormatter).replaceFirstChar { it.uppercase() }
    }
    val timeLabel = when {
        index == 0 -> stringResource(R.string.timeline_now)
        zoned != null -> zoned.format(hourFormatter)
        else -> "—"
    }
    return TimelineLabels(timeLabel, dayLabel)
}

@Composable
private fun TimelinePointCard(
    point: SimplifiedTimelinePoint,
    mode: DisplayMode,
    label: String,
    dayLabel: String?,
    precipitationAccent: Color,
    windAccent: Color,
    isFirst: Boolean,
    isFocused: Boolean
) {
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isFirst -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    Column(
        modifier = Modifier
            .width(TIMELINE_CARD_WIDTH)
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 7.dp, vertical = 8.dp)
            .testTag(
                if (isFocused) TAG_TIMELINE_POINT_FOCUSED
                else "${TAG_TIMELINE_POINT_PREFIX}${timelinePointKey(point)}"
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.height(17.dp), contentAlignment = Alignment.Center) {
            dayLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isFirst || isFocused) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isFirst || isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )

        Spacer(Modifier.height(5.dp))

        point.condition?.let {
            WeatherIconDecorative(
                condition = it,
                size = 28.dp,
                tint = it.semanticTint()
            )
        } ?: Spacer(Modifier.height(28.dp))

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

        val temperatureRange = temperatureRangeLabel(point, mode)
        Box(modifier = Modifier.height(16.dp), contentAlignment = Alignment.Center) {
            temperatureRange?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        TimelineMetric(
            icon = Icons.Outlined.WaterDrop,
            value = point.precipitationPercent?.let { "$it%" } ?: "—",
            tint = precipitationAccent
        )
        Text(
            text = precipitationSourceLabel(point),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(16.dp)
        )

        TimelineMetric(
            icon = Icons.Outlined.Air,
            value = point.windKmh?.let { "${it.roundToInt()} km/h" } ?: "—",
            tint = windAccent
        )

        Spacer(Modifier.height(6.dp))
        ConsensusBadge(point)
    }
}

private fun temperatureRangeLabel(
    point: SimplifiedTimelinePoint,
    mode: DisplayMode
): String? {
    if (mode != DisplayMode.HOURLY) return null
    val min = point.temperatureMinAcrossModels?.roundToInt() ?: return null
    val max = point.temperatureMaxAcrossModels?.roundToInt() ?: return null
    if (max - min < 1) return null
    return "$min–$max°"
}

@Composable
private fun precipitationSourceLabel(point: SimplifiedTimelinePoint): String = when {
    point.precipitationSource == PrecipitationSignalSource.MODEL_PROBABILITY ->
        stringResource(R.string.timeline_precip_probability_source)
    point.precipitationSource == PrecipitationSignalSource.MODEL_AGREEMENT &&
        point.precipitationModelCount >= 2 -> stringResource(
            R.string.timeline_precip_models_source,
            point.wetModelCount,
            point.precipitationModelCount
        )
    else -> ""
}

@Composable
private fun ConsensusBadge(point: SimplifiedTimelinePoint) {
    val reasons = orderedDivergenceReasons(point.divergenceReasons)
    val hasDisagreement = point.isDivergent || reasons.isNotEmpty()
    // Un désaccord ciblé ne doit jamais être masqué par une bonne moyenne sur
    // les autres variables : on rabaisse visuellement « élevé » à « moyen ».
    val displayLevel = when {
        hasDisagreement && point.consensusLevel == ModelConsensusLevel.HIGH ->
            ModelConsensusLevel.MEDIUM
        else -> point.consensusLevel
    }
    val (agreementLabel, color) = when (displayLevel) {
        ModelConsensusLevel.HIGH -> stringResource(R.string.timeline_consensus_high) to
            MaterialTheme.colorScheme.primary
        ModelConsensusLevel.MEDIUM -> stringResource(R.string.timeline_consensus_medium) to
            MaterialTheme.colorScheme.tertiary
        ModelConsensusLevel.LOW -> stringResource(R.string.timeline_consensus_low) to
            MaterialTheme.colorScheme.error
        null -> stringResource(R.string.timeline_consensus_limited) to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    val reasonItems = reasons.map { reason ->
        when (reason) {
            DivergenceReason.PRECIPITATION -> Triple(
                reason,
                Icons.Outlined.WaterDrop,
                stringResource(R.string.timeline_divergence_rain)
            )
            DivergenceReason.WIND -> Triple(
                reason,
                Icons.Outlined.Air,
                stringResource(R.string.timeline_divergence_wind)
            )
            DivergenceReason.TEMPERATURE -> Triple(
                reason,
                Icons.Outlined.Thermostat,
                stringResource(R.string.timeline_divergence_temperature)
            )
            DivergenceReason.CONDITION -> Triple(
                reason,
                Icons.Outlined.Cloud,
                stringResource(R.string.timeline_divergence_condition)
            )
        }
    }
    val reasonContentDescription = if (reasonItems.isNotEmpty()) {
        stringResource(
            R.string.timeline_divergence_variables_accessibility,
            reasonItems.joinToString(", ") { it.third }
        )
    } else {
        null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.09f)
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = CONSENSUS_BADGE_MIN_HEIGHT)
                .padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (hasDisagreement) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = if (hasDisagreement) {
                        stringResource(R.string.timeline_disagreement_badge)
                    } else {
                        agreementLabel
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (hasDisagreement) 1 else 2,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center
                )
            }

            if (reasonItems.isNotEmpty() && reasonContentDescription != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .testTag(TAG_TIMELINE_DIVERGENCE_REASON)
                        .semantics {
                            contentDescription = reasonContentDescription
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    reasonItems.forEachIndexed { index, (reason, icon, _) ->
                        if (index > 0) Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier
                                .size(14.dp)
                                .testTag(divergenceIconTag(reason))
                        )
                    }
                }
            } else if (hasDisagreement) {
                Text(
                    text = stringResource(R.string.timeline_divergence_multiple),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(TAG_TIMELINE_DIVERGENCE_REASON)
                )
            }
        }
    }
}

private fun orderedDivergenceReasons(
    reasons: Set<DivergenceReason>
): List<DivergenceReason> = listOf(
    DivergenceReason.PRECIPITATION,
    DivergenceReason.WIND,
    DivergenceReason.TEMPERATURE,
    DivergenceReason.CONDITION
).filter { it in reasons }

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

private fun divergenceIconTag(reason: DivergenceReason): String = when (reason) {
    DivergenceReason.PRECIPITATION -> TAG_TIMELINE_DIVERGENCE_ICON_RAIN
    DivergenceReason.WIND -> TAG_TIMELINE_DIVERGENCE_ICON_WIND
    DivergenceReason.TEMPERATURE -> "timeline_divergence_icon_temperature"
    DivergenceReason.CONDITION -> "timeline_divergence_icon_condition"
}

internal const val TAG_SIMPLIFIED_TIMELINE = "simplified_timeline"
internal const val TAG_TIMELINE_POINT_FOCUSED = "timeline_point_focused"
internal const val TAG_TIMELINE_DIVERGENCE_REASON = "timeline_divergence_reason"
internal const val TAG_TIMELINE_DIVERGENCE_ICON_RAIN = "timeline_divergence_icon_rain"
internal const val TAG_TIMELINE_DIVERGENCE_ICON_WIND = "timeline_divergence_icon_wind"
private const val TAG_TIMELINE_POINT_PREFIX = "timeline_point_"
private val TIMELINE_CARD_WIDTH = 108.dp
private val CONSENSUS_BADGE_MIN_HEIGHT = 44.dp
private const val FOCUS_HIGHLIGHT_MILLIS = 1_800L
