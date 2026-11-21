package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.util.windCardinal

import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat

import com.meteocompare.app.ui.components.UvIndexCompact
import com.meteocompare.app.ui.components.formatSunshineDuration
import com.meteocompare.app.ui.components.localizedSunshineDurationFormats
import com.meteocompare.app.ui.components.HumidityMetricIcon
import com.meteocompare.app.ui.components.DewPointMetricIcon

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.LocalWeatherUnits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.components.CollapsibleSectionHeader
import com.meteocompare.app.ui.components.ConditionStatusIcon
import com.meteocompare.app.ui.components.blendedHeatmapColor
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * Vue chronologique légère placée avant les tableaux détaillés.
 *
 * Les valeurs principales sont des médianes multi-modèles. Les plages et le
 * indicateur de convergence rendent la dispersion visible sans remplacer les tableaux.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SimplifiedTimelineCard(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    timezone: String?,
    modifier: Modifier = Modifier,
    events: List<ForecastEvent> = emptyList(),
    focusPoint: SimplifiedTimelinePoint? = null,
    focusRequestId: Int = 0,
    range: TimelineRange = TimelineRange.defaultFor(mode),
    onRangeChange: ((TimelineRange) -> Unit)? = null,
    availableRanges: Set<TimelineRange> = setOf(range),
    layout: TimelineLayout = TimelineLayout.COLUMNS,
    onLayoutChange: ((TimelineLayout) -> Unit)? = null,
    showDailyRanges: Boolean = false,
    onShowDailyRangesChange: ((Boolean) -> Unit)? = null,
    now: Instant = Instant.now(),
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    if (points.isEmpty()) return

    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val today = remember(timezone, now) { cityLocalDate(timezone, now) }
    val currentHour = remember(timezone, now) { computeHourlyHorizon(timezone, now).first }
    val clockPreference = LocalClockFormatPreference.current
    val hourFormatter = remember(locale, clockPreference) { WeatherTimeFormat.hourPadded(locale, clockPreference) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val precipitationAccent = precipitationMetricAccent()
    val windAccent = windMetricAccent()
    val listState = rememberLazyListState()
    val chronoScrollState = rememberScrollState()
    val snapFlingBehavior = rememberSnapFlingBehavior(listState)
    val density = LocalDensity.current
    var highlightedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusRequestId, focusPoint, points, layout, mode, showDailyRanges, expanded) {
        if (!expanded || focusRequestId <= 0 || focusPoint == null) return@LaunchedEffect
        val index = nearestTimelineDisplayIndex(points, focusPoint)
        if (index < 0) return@LaunchedEffect
        val key = timelinePointKey(points[index])
        when (layout) {
            TimelineLayout.COLUMNS -> listState.animateScrollToItem(index)
            TimelineLayout.CHRONO -> {
                val target = with(density) {
                    chronoFocusScrollOffset(
                        index = index,
                        pointWidthPx = chronoPointWidth(mode, showDailyRanges).roundToPx()
                    )
                }
                chronoScrollState.animateScrollTo(target)
            }
        }
        highlightedKey = key
        delay(FOCUS_HIGHLIGHT_MILLIS)
        if (highlightedKey == key) highlightedKey = null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .testTag(TAG_SIMPLIFIED_TIMELINE),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            CollapsibleSectionHeader(
                text = stringResource(
                    if (mode == DisplayMode.HOURLY) {
                        R.string.timeline_title_hourly
                    } else {
                        R.string.timeline_title_daily
                    }
                ),
                subtitle = stringResource(
                    if (mode == DisplayMode.HOURLY) {
                        R.string.timeline_subtitle_hourly
                    } else {
                        R.string.timeline_subtitle_daily
                    }
                ),
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) }
            )

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                if (
                    onLayoutChange != null ||
                    (onRangeChange != null && availableRanges.size > 1)
                ) {
                    val showModeSelector = onRangeChange != null && availableRanges.size > 1
                    val showLayoutSelector = onLayoutChange != null

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showModeSelector) {
                            TimelineRangeSelector(
                                range = range,
                                availableRanges = availableRanges,
                                onRangeChange = requireNotNull(onRangeChange)
                            )
                        }

                        if (showLayoutSelector) {
                            TimelineLayoutSelector(
                                layout = layout,
                                onLayoutChange = requireNotNull(onLayoutChange)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                when (layout) {
                    TimelineLayout.COLUMNS -> LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
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
                                dayFormatter = dayFormatter,
                                today = today,
                                currentHour = currentHour
                            )
                            val slotEvents = eventsAssignedToPoint(
                                point = point,
                                displayPoints = points,
                                events = events
                            )
                            TimelinePointColumn(
                                point = point,
                                mode = mode,
                                label = labels.timeLabel,
                                contextLabel = labels.contextLabel,
                                events = slotEvents,
                                precipitationAccent = precipitationAccent,
                                windAccent = windAccent,
                                isFirst = index == 0,
                                isLast = index == points.lastIndex,
                                isFocused = highlightedKey == timelinePointKey(point),
                                showDailyRanges = showDailyRanges
                            )
                        }
                    }

                    TimelineLayout.CHRONO -> ChronoTimelineView(
                        points = points,
                        mode = mode,
                        timezone = timezone,
                        now = now,
                        scrollState = chronoScrollState,
                        highlightedKey = highlightedKey,
                        showDailyRanges = showDailyRanges
                    )
                }

                // Keep this control immediately below either visualization and
                // immediately above the convergence explanation, never in the
                // mode/layout selector row.
                if (mode == DisplayMode.DAILY && onShowDailyRangesChange != null) {
                    TimelineDailyRangesToggle(
                        checked = showDailyRanges,
                        onCheckedChange = onShowDailyRangesChange
                    )
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
}

/** A themed, accessible switch shared by Columns and Timeline. */
@Composable
private fun TimelineDailyRangesToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = if (checked) scheme.primaryContainer.copy(alpha = 0.72f)
                else scheme.surfaceContainerHigh.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier
                    .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                    .testTag(TAG_TIMELINE_RANGES_TOGGLE)
                    .padding(start = 11.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.timeline_show_ranges),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                )
                // The parent is the only clickable/switch semantics target.
                Switch(checked = checked, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun TimelineRangeSelector(
    range: TimelineRange,
    availableRanges: Set<TimelineRange>,
    onRangeChange: (TimelineRange) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val selectedShape = RoundedCornerShape(11.dp)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(
                scheme.surfaceContainerLow.copy(alpha = 0.78f)
            )
            .padding(3.dp)
            .selectableGroup()
            .testTag(TAG_TIMELINE_MODE_SELECTOR),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineRange.entries
            .filter { it in availableRanges }
            .forEach { option ->

                val selected = option == range

                val label = stringResource(
                    when (option) {
                        TimelineRange.HOURLY -> R.string.display_mode_hourly
                        TimelineRange.DAILY -> R.string.display_mode_daily
                    }
                )

                Box(
                    modifier = Modifier
                        .clip(selectedShape)
                        .background(
                            if (selected) {
                                scheme.primaryContainer.copy(alpha = 0.84f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!selected) {
                                    onRangeChange(option)
                                }
                            }
                        )
                        .padding(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Medium
                        },
                        color = if (selected) {
                            scheme.onPrimaryContainer
                        } else {
                            scheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
    }
}

@Composable
private fun TimelineLayoutSelector(
    layout: TimelineLayout,
    onLayoutChange: (TimelineLayout) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val selectedShape = RoundedCornerShape(11.dp)
    val ariaLabel = stringResource(R.string.timeline_layout_aria)

    Row(
        modifier = Modifier
            .clip(shape)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.78f))
            .padding(3.dp)
            .selectableGroup()
            .semantics { contentDescription = ariaLabel }
            .testTag(TAG_TIMELINE_LAYOUT_SELECTOR),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimelineLayout.entries.forEach { option ->
            val selected = option == layout
            val label = stringResource(
                when (option) {
                    TimelineLayout.COLUMNS -> R.string.timeline_layout_columns
                    TimelineLayout.CHRONO -> R.string.timeline_layout_chrono
                }
            )
            Box(
                modifier = Modifier
                    .clip(selectedShape)
                    .background(
                        if (selected) scheme.primaryContainer.copy(alpha = 0.84f)
                        else Color.Transparent
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { if (!selected) onLayoutChange(option) }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TimelineEventRulerSegment(
    events: List<ForecastEvent>,
    showStartCap: Boolean,
    showEndCap: Boolean
) {
    val markerLabels = mutableListOf<String>()
    for (event in events.take(MAX_MARKERS_PER_SLOT)) {
        markerLabels += eventMarkerLabel(event)
    }
    val markerDescription = markerLabels.joinToString(", ")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EVENT_RULER_HEIGHT)
            .semantics {
                if (markerDescription.isNotBlank()) contentDescription = markerDescription
            }
            .testTag(TAG_TIMELINE_EVENT_RULER),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (showStartCap) TIMELINE_RULER_EDGE_INSET else 0.dp,
                    end = if (showEndCap) TIMELINE_RULER_EDGE_INSET else 0.dp
                ),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        if (events.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag(TAG_TIMELINE_EVENT_MARKER)
            ) {
                events.take(MAX_MARKERS_PER_SLOT).forEach { event ->
                    val color = eventMarkerColor(event)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = color,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = eventMarkerIcon(event),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
private fun eventMarkerLabel(event: ForecastEvent): String = stringResource(
    when (event.kind) {
        ForecastEventKind.PRECIPITATION -> R.string.timeline_event_marker_precipitation
        ForecastEventKind.WIND -> R.string.timeline_event_marker_wind
        ForecastEventKind.TEMPERATURE -> R.string.timeline_event_marker_temperature
        ForecastEventKind.WEATHER_TRANSITION -> R.string.timeline_event_marker_weather
        ForecastEventKind.UNCERTAINTY -> R.string.timeline_event_marker_uncertainty
        ForecastEventKind.STABLE -> R.string.forecast_insight_title_stable_compact
    }
)

@Composable
private fun eventMarkerColor(event: ForecastEvent): Color = when (event.impact) {
    ForecastInsightLevel.ALERT -> MaterialTheme.colorScheme.error
    ForecastInsightLevel.WATCH -> MaterialTheme.colorScheme.tertiary
    ForecastInsightLevel.INFO -> MaterialTheme.colorScheme.secondary
    ForecastInsightLevel.POSITIVE -> MaterialTheme.colorScheme.primary
}

private fun eventMarkerIcon(event: ForecastEvent) = when (event.kind) {
    ForecastEventKind.PRECIPITATION -> Icons.Outlined.WaterDrop
    ForecastEventKind.WIND -> Icons.Outlined.Air
    ForecastEventKind.TEMPERATURE -> Icons.Outlined.Thermostat
    ForecastEventKind.WEATHER_TRANSITION -> Icons.Outlined.Cloud
    ForecastEventKind.UNCERTAINTY -> Icons.Outlined.WarningAmber
    ForecastEventKind.STABLE -> Icons.Outlined.CheckCircle
}

private fun eventsAssignedToPoint(
    point: SimplifiedTimelinePoint,
    displayPoints: List<SimplifiedTimelinePoint>,
    events: List<ForecastEvent>
): List<ForecastEvent> = events.filter { event ->
    val nearest = displayPoints.minByOrNull { candidate ->
        timelineDistance(candidate, event.peakPoint)
    }
    nearest != null && sameTimelinePoint(nearest, point) && event.kind != ForecastEventKind.STABLE
}

internal fun nearestTimelineDisplayIndex(
    points: List<SimplifiedTimelinePoint>,
    target: SimplifiedTimelinePoint
): Int = points.indices.minByOrNull { index -> timelineDistance(points[index], target) } ?: -1

/** Conserve une échéance de contexte avant le point ciblé dans la vue frise. */
internal fun chronoFocusScrollOffset(index: Int, pointWidthPx: Int): Int =
    (index - 1).coerceAtLeast(0) * pointWidthPx.coerceAtLeast(0)

private fun timelineDistance(
    first: SimplifiedTimelinePoint,
    second: SimplifiedTimelinePoint
): Long = when {
    first.instant != null && second.instant != null ->
        kotlin.math.abs(first.instant.toEpochMilli() - second.instant.toEpochMilli())
    first.date != null && second.date != null ->
        kotlin.math.abs(first.date.toEpochDay() - second.date.toEpochDay()) * 86_400_000L
    else -> Long.MAX_VALUE
}

private data class TimelineLabels(
    val timeLabel: String,
    /** Jour affiché uniquement lors d’un changement de date. */
    val contextLabel: String?
)

@Composable
private fun timelineLabels(
    point: SimplifiedTimelinePoint,
    index: Int,
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    zone: ZoneId,
    hourFormatter: DateTimeFormatter,
    dayFormatter: DateTimeFormatter,
    today: java.time.LocalDate,
    currentHour: Instant
): TimelineLabels {
    if (mode == DisplayMode.DAILY) {
        val date = point.date
        val label = when {
            date == null -> "—"
            date == today -> stringResource(R.string.timeline_today)
            date == today.plusDays(1) -> stringResource(R.string.timeline_tomorrow)
            else -> date.format(dayFormatter).replaceFirstChar { it.uppercase() }
        }
        return TimelineLabels(label, null)
    }

    val zoned = point.instant?.atZone(zone)
    val currentDate = zoned?.toLocalDate()
    val previousDate = points.getOrNull(index - 1)?.instant?.atZone(zone)?.toLocalDate()
    val dayLabel = when {
        currentDate == null || currentDate == previousDate || currentDate == today -> null
        currentDate == today.plusDays(1) -> stringResource(R.string.timeline_tomorrow)
        else -> currentDate.format(dayFormatter).replaceFirstChar { it.uppercase() }
    }
    val contextLabel = dayLabel
    val timeLabel = when {
        point.instant == currentHour -> stringResource(R.string.timeline_now)
        zoned != null -> zoned.format(hourFormatter)
        else -> "—"
    }
    return TimelineLabels(timeLabel, contextLabel)
}

@Composable
private fun TimelinePointColumn(
    point: SimplifiedTimelinePoint,
    mode: DisplayMode,
    label: String,
    contextLabel: String?,
    events: List<ForecastEvent>,
    precipitationAccent: Color,
    windAccent: Color,
    isFirst: Boolean,
    isLast: Boolean,
    isFocused: Boolean,
    showDailyRanges: Boolean = false,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    val separatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    val focusColor = MaterialTheme.colorScheme.primary
    val focusShape = RoundedCornerShape(14.dp)
    val pointTag = if (isFocused) {
        TAG_TIMELINE_POINT_FOCUSED
    } else {
        "${TAG_TIMELINE_POINT_PREFIX}${timelinePointKey(point)}"
    }
    val focusModifier = if (isFocused) {
        Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f),
                shape = focusShape
            )
            .border(
                width = 1.dp,
                color = focusColor.copy(alpha = 0.42f),
                shape = focusShape
            )
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .width(timelineColumnWidth(mode, showDailyRanges))
            .then(focusModifier)
            .drawBehind {
                if (!isLast) {
                    drawLine(
                        color = separatorColor,
                        start = Offset(size.width, EVENT_RULER_HEIGHT.toPx() + 36.dp.toPx()),
                        end = Offset(size.width, size.height - 8.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .testTag(pointTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.height(17.dp),
            contentAlignment = Alignment.Center
        ) {
            contextLabel?.let {
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

        TimelineEventRulerSegment(
            events = events,
            showStartCap = isFirst,
            showEndCap = isLast
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.height(if (mode == DisplayMode.DAILY) 48.dp else 34.dp), contentAlignment = Alignment.Center) {
                if (mode == DisplayMode.DAILY) {
                    DayNightConditionIcons(
                        point.dayCondition, point.nightCondition,
                        dayDiagnostic = point.dayConditionDiagnostic,
                        nightDiagnostic = point.nightConditionDiagnostic
                    )
                } else {
                    ConditionStatusIcon(
                        condition = point.condition,
                        decision = point.conditionDiagnostic,
                        size = 32.dp,
                        tint = if (point.isNight == true && point.condition == com.meteocompare.app.domain.model.WeatherCondition.CLEAR)
                            Color.Unspecified else point.condition?.semanticTint() ?: Color.Unspecified,
                        isNight = point.isNight == true
                    )
                }
            }
            UvIndexCompact(point.uvIndex)
        }

        TemperatureHeatmapBand(
            point = point,
            mode = mode,
            isFirst = isFirst,
            isLast = isLast,
            showDailyRanges = showDailyRanges
        )
        if (mode == DisplayMode.HOURLY) {
            TimelineSupportingText(
                text = point.apparentTemperatureC?.takeIf(Double::isFinite)?.let {
                    stringResource(R.string.apparent_temperature_short, units.temp(it))
                } ?: "",
                modifier = Modifier.testTag(TAG_TIMELINE_COLUMNS_APPARENT)
            )
        }

        PrecipitationHeatIndicator(
            probability = point.precipitationPercent,
            accent = precipitationAccent
        )

        // Daily readings are paired with their ranges rather than inserting ten
        // additional empty rows. Every range obeys the same visibility switch.
        TimelineMetric(
            icon = Icons.Outlined.WaterDrop,
            value = point.precipitationPercent?.let { "$it%" } ?: "—",
            tint = precipitationAccent,
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.RAIN_PROBABILITY, units, displayLocale) else null,
            rangeTag = "columns_daily_rain_probability_range",
            iconTag = "columns_daily_rain_probability_icon",
            valueTag = "columns_daily_rain_probability_value"
        )
        TimelineSupportingText(
            text = timelinePrecipitationAmountLabel(point),
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.RAIN_AMOUNT, units, displayLocale) else null,
            rangeTag = "columns_daily_rain_amount_range"
        )
        TimelineMetric(
            icon = Icons.Outlined.Cloud,
            value = point.cloudCoverPercent?.let { "$it%" } ?: "—",
            tint = MaterialTheme.colorScheme.secondary,
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.CLOUD_COVER, units, displayLocale) else null,
            rangeTag = "columns_daily_cloud_range",
            iconTag = "columns_daily_cloud_icon",
            valueTag = "columns_daily_cloud_value"
        )
        TimelineMetric(
            icon = Icons.Filled.WbSunny,
            value = formatSunshineDuration(
                point.estimatedSunHours, hourly = point.instant != null, units, LocalConfiguration.current.locales[0], localizedSunshineDurationFormats()
            ),
            tint = MaterialTheme.colorScheme.tertiary,
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            semanticLabel = stringResource(R.string.compare_sunshine)
        )
        TimelineMetric(
            icon = HumidityMetricIcon,
            value = point.humidityPercent?.let { "$it%" } ?: "—",
            tint = MaterialTheme.colorScheme.secondary,
            semanticLabel = stringResource(R.string.metric_humidity),
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.HUMIDITY, units, displayLocale) else null,
            rangeTag = "columns_daily_humidity_range"
        )
        TimelineMetric(
            icon = DewPointMetricIcon,
            value = point.dewPointC?.let { units.temp(it) } ?: "—",
            tint = MaterialTheme.colorScheme.tertiary,
            semanticLabel = stringResource(R.string.metric_dew_point),
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.DEW_POINT, units, displayLocale) else null,
            rangeTag = "columns_daily_dew_point_range"
        )
        TimelineMetric(
            icon = Icons.Outlined.Speed,
            value = point.pressureHpa?.let {
                units.format(it, WeatherUnit.PRESSURE, if (units.pressureInInHg) 2 else 0)
            } ?: "—",
            tint = MaterialTheme.colorScheme.tertiary,
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.PRESSURE, units, displayLocale) else null,
            rangeTag = "columns_daily_pressure_range"
        )
        TimelineMetric(
            icon = Icons.Outlined.Air,
            value = point.windKmh?.let { speed ->
                val formatted = units.speed(speed)
                windCardinal(point.windDirectionDeg, displayLocale)?.let { "$formatted · $it" } ?: formatted
            } ?: "—",
            tint = windAccent,
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.WIND, units, displayLocale) else null,
            rangeTag = "columns_daily_wind_range"
        )
        TimelineSupportingText(
            text = point.windGustKmh?.let { stringResource(R.string.timeline_wind_gust, it) } ?: "—",
            compact = showDailyRanges && mode == DisplayMode.DAILY,
            range = if (showDailyRanges && mode == DisplayMode.DAILY)
                dailyMetricRange(point, DailyRangeMetric.WIND_GUST, units, displayLocale) else null,
            rangeTag = "columns_daily_gust_range"
        )

        Spacer(Modifier.height(6.dp))
        TimelineConsensus(point)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TemperatureHeatmapBand(
    point: SimplifiedTimelinePoint,
    mode: DisplayMode,
    isFirst: Boolean,
    isLast: Boolean,
    showDailyRanges: Boolean = false,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val noData = MaterialTheme.colorScheme.surfaceVariant
    val heatStrength = 0.54f

    val rawTopColor: Color
    val rawBottomColor: Color
    val representative: Color
    when (mode) {
        DisplayMode.HOURLY -> {
            val temp = point.temperatureC
            val raw = temp?.let(::temperatureHeatmapColor) ?: noData
            rawTopColor = raw
            rawBottomColor = raw
            representative = blendedHeatmapColor(surface, raw, heatStrength)
        }
        DisplayMode.DAILY -> {
            val high = point.tempMaxC
            val low = point.tempMinC
            val top = high?.let(::temperatureHeatmapColor)
                ?: low?.let(::temperatureHeatmapColor)
                ?: noData
            val bottom = low?.let(::temperatureHeatmapColor)
                ?: high?.let(::temperatureHeatmapColor)
                ?: noData
            rawTopColor = top
            rawBottomColor = bottom
            val midpointTemp = when {
                high != null && low != null -> (high + low) / 2.0
                high != null -> high
                low != null -> low
                else -> null
            }
            representative = midpointTemp?.let {
                blendedHeatmapColor(surface, temperatureHeatmapColor(it), heatStrength)
            } ?: noData
        }
    }

    val topColor = blendedHeatmapColor(surface, rawTopColor, heatStrength)
    val bottomColor = blendedHeatmapColor(surface, rawBottomColor, heatStrength)
    val contentColor = if (representative.luminance() > 0.54f) {
        Color.Black.copy(alpha = 0.82f)
    } else {
        Color.White.copy(alpha = 0.94f)
    }
    val shape = RoundedCornerShape(
        topStart = if (isFirst) 12.dp else 0.dp,
        topEnd = if (isLast) 12.dp else 0.dp,
        bottomEnd = if (isLast) 12.dp else 0.dp,
        bottomStart = if (isFirst) 12.dp else 0.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (mode == DisplayMode.DAILY) { if (showDailyRanges) 70.dp else 64.dp } else 46.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
            .testTag(TAG_TIMELINE_HEATMAP_BAND),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            DisplayMode.HOURLY -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = point.temperatureC?.let { units.temp(it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1
                    )
                    temperatureRangeLabel(point, mode, units = units)?.let { range ->
                        Text(
                            text = range,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.82f),
                            maxLines = 1
                        )
                    }
                }
            }
            DisplayMode.DAILY -> {
                val high = units.temp(point.tempMaxC)
                val low = units.temp(point.tempMinC)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DailyRangeLabel(
                        value = high,
                        range = if (showDailyRanges)
                            dailyMetricRange(point, DailyRangeMetric.HIGH_TEMPERATURE, units, displayLocale) else null,
                        rangeTag = "columns_daily_temp_max_range",
                        modifier = Modifier.fillMaxWidth(),
                        valueStyle = MaterialTheme.typography.labelLarge,
                        valueColor = contentColor,
                        rangeColor = contentColor.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(3.dp))
                    DailyRangeLabel(
                        value = low,
                        range = if (showDailyRanges)
                            dailyMetricRange(point, DailyRangeMetric.LOW_TEMPERATURE, units, displayLocale) else null,
                        rangeTag = "columns_daily_temp_min_range",
                        modifier = Modifier.fillMaxWidth(),
                        valueStyle = MaterialTheme.typography.labelSmall,
                        valueColor = contentColor.copy(alpha = 0.90f),
                        rangeColor = contentColor.copy(alpha = 0.80f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrecipitationHeatIndicator(
    probability: Int?,
    accent: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val value = probability?.coerceIn(0, 100)
        if (value != null && value >= 30) {
            val fraction = ((value - 30) / 70f).coerceIn(0f, 1f)
            val dotSize = (4f + 4f * fraction).dp
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(
                        color = accent.copy(alpha = 0.55f + 0.40f * fraction),
                        shape = CircleShape
                    )
                    .testTag(TAG_TIMELINE_PRECIP_HEAT_DOT)
            )
        }
    }
}

private fun temperatureRangeLabel(
    point: SimplifiedTimelinePoint,
    mode: DisplayMode,
    units: WeatherUnits
): String? {
    if (mode != DisplayMode.HOURLY) return null
    val min = point.temperatureMinAcrossModels ?: return null
    val max = point.temperatureMaxAcrossModels ?: return null
    if (max - min < 1) return null
    return "${units.value(min, WeatherUnit.TEMPERATURE_COMPACT)}–${units.temp(max)}"
}

@Composable
private fun timelinePrecipitationAmountLabel(point: SimplifiedTimelinePoint): String {
    val conditional = point.precipitationConditionalMm
        ?.takeIf { it.isFinite() && it >= 0.05 }
    if (conditional != null) {
        return stringResource(R.string.timeline_precip_amount_if_rain, conditional)
    }
    val central = point.precipitationMm?.takeIf { it.isFinite() && it >= 0.0 }
    return if (central != null) {
        stringResource(R.string.timeline_precip_amount, central)
    } else {
        "—"
    }
}

@Composable
private fun TimelineConsensus(point: SimplifiedTimelinePoint) {
    val reasons = orderedDivergenceReasons(point.divergenceReasons)
    val hasDisagreement = reasons.isNotEmpty()
    val displayLevel = timelineConsensusDisplayLevel(point)
    val colorAnchor = timelineConsensusColorAnchor(displayLevel)
    val color = if (colorAnchor != null) {
        confidenceColor(colorAnchor)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val agreementLabel = when (displayLevel) {
        ModelConsensusLevel.HIGH -> stringResource(R.string.timeline_consensus_high)
        ModelConsensusLevel.MEDIUM -> stringResource(R.string.timeline_consensus_medium)
        ModelConsensusLevel.LOW -> stringResource(R.string.timeline_consensus_low)
        null -> stringResource(R.string.timeline_consensus_limited)
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
    val agreementContentDescription = point.consensusPercent?.let { percent ->
        stringResource(
            R.string.timeline_consensus_accessibility,
            agreementLabel,
            percent
        )
    } ?: agreementLabel
    val reasonContentDescription = if (reasonItems.isNotEmpty()) {
        stringResource(
            R.string.timeline_divergence_variables_accessibility,
            reasonItems.joinToString(", ") { it.third }
        )
    } else {
        null
    }

    Row(
        modifier = Modifier.height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .testTag(TAG_TIMELINE_CONSENSUS_BADGE)
                .semantics { contentDescription = agreementContentDescription },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = point.consensusPercent?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        if (hasDisagreement && reasonContentDescription != null) {
            Spacer(Modifier.width(5.dp))
            Row(
                modifier = Modifier
                    .testTag(TAG_TIMELINE_DIVERGENCE_REASON)
                    .semantics { contentDescription = reasonContentDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
                reasonItems.forEach { (reason, icon, _) ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .size(11.dp)
                            .testTag(divergenceIconTag(reason))
                    )
                }
            }
        }
    }
}

/** Dégrade une convergence haute dès qu'une variable explicite diverge. */
internal fun timelineConsensusDisplayLevel(point: SimplifiedTimelinePoint): ModelConsensusLevel? =
    if (point.divergenceReasons.isNotEmpty() && point.consensusLevel == ModelConsensusLevel.HIGH) {
        ModelConsensusLevel.MEDIUM
    } else {
        point.consensusLevel
    }

/**
 * Valeur d'ancrage utilisée uniquement pour sélectionner la palette
 * vert / orange / rouge de [confidenceColor].
 */
internal fun timelineConsensusColorAnchor(level: ModelConsensusLevel?): Int? = when (level) {
    ModelConsensusLevel.HIGH -> 80
    ModelConsensusLevel.MEDIUM -> 50
    ModelConsensusLevel.LOW -> 0
    null -> null
}

private fun orderedDivergenceReasons(
    reasons: Set<DivergenceReason>
): List<DivergenceReason> = listOf(
    DivergenceReason.PRECIPITATION,
    DivergenceReason.WIND,
    DivergenceReason.TEMPERATURE,
    DivergenceReason.CONDITION
).filter { it in reasons }

/**
 * On the opt-in daily layout every icon sits on the same left rail. The value
 * starts immediately after the icon; the range may use the remaining width or
 * wrap to a tiny second line. Rows with no range retain the same alignment.
 */
@Composable
private fun TimelineMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    tint: Color,
    semanticLabel: String? = null,
    range: String? = null,
    rangeTag: String = "",
    compact: Boolean = false,
    iconTag: String? = null,
    valueTag: String? = null
) {
    val semanticModifier = if (semanticLabel != null) Modifier.semantics(mergeDescendants = true) {
        contentDescription = "$semanticLabel $value${range?.let { " $it" } ?: ""}"
    } else Modifier
    if (!compact && range == null) {
        // Preserve the established layout for hourly and ranges-off daily.
        Row(modifier = Modifier.height(20.dp).then(semanticModifier),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint,
                modifier = Modifier.size(12.dp).then(iconTag?.let { Modifier.testTag(it) } ?: Modifier))
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium, maxLines = 1,
                modifier = valueTag?.let(Modifier::testTag) ?: Modifier)
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 19.dp)
            .padding(horizontal = 8.dp).then(semanticModifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint,
            modifier = Modifier.size(12.dp).then(iconTag?.let { Modifier.testTag(it) } ?: Modifier))
        Spacer(Modifier.width(3.dp))
        DailyRangeLabel(
            value = value,
            valueTag = valueTag,
            range = range,
            rangeTag = rangeTag,
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
            valueStyle = MaterialTheme.typography.labelSmall,
            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
            rangeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
            valueWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TimelineSupportingText(
    text: String,
    modifier: Modifier = Modifier,
    range: String? = null,
    rangeTag: String = "",
    compact: Boolean = false
) {
    if (!compact && range == null) {
        Box(modifier = modifier.fillMaxWidth().height(18.dp).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    // The inset equals the icon + the gap in TimelineMetric, so consecutive
    // figures have the same starting x position even without an icon.
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 18.dp)
            .padding(start = 23.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        DailyRangeLabel(
            value = text,
            range = range,
            rangeTag = rangeTag,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            valueStyle = MaterialTheme.typography.labelSmall,
            valueColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            rangeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            valueWeight = FontWeight.Normal
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
internal const val TAG_TIMELINE_MODE_SELECTOR = "timeline_mode_selector"
internal const val TAG_TIMELINE_LAYOUT_SELECTOR = "timeline_layout_selector"
internal const val TAG_TIMELINE_RANGES_TOGGLE = "timeline_daily_ranges_toggle"
internal const val TAG_TIMELINE_EVENT_RULER = "timeline_event_ruler"
internal const val TAG_TIMELINE_EVENT_MARKER = "timeline_event_marker"
internal const val TAG_TIMELINE_POINT_FOCUSED = "timeline_point_focused"
internal const val TAG_TIMELINE_DIVERGENCE_REASON = "timeline_divergence_reason"
internal const val TAG_TIMELINE_CONSENSUS_BADGE = "timeline_consensus_badge"
internal const val TAG_TIMELINE_DIVERGENCE_ICON_RAIN = "timeline_divergence_icon_rain"
internal const val TAG_TIMELINE_DIVERGENCE_ICON_WIND = "timeline_divergence_icon_wind"
internal const val TAG_TIMELINE_COLUMNS_APPARENT = "timeline_columns_apparent"
internal const val TAG_TIMELINE_HEATMAP_BAND = "timeline_heatmap_band"
internal const val TAG_TIMELINE_PRECIP_HEAT_DOT = "timeline_precip_heat_dot"
private const val TAG_TIMELINE_POINT_PREFIX = "timeline_point_"
internal fun timelineColumnWidth(mode: DisplayMode, showDailyRanges: Boolean): androidx.compose.ui.unit.Dp =
    if (mode == DisplayMode.DAILY && showDailyRanges) 136.dp else 112.dp
private val EVENT_RULER_HEIGHT = 24.dp
private val TIMELINE_RULER_EDGE_INSET = 56.dp
private const val MAX_MARKERS_PER_SLOT = 4
private const val FOCUS_HIGHLIGHT_MILLIS = 1_800L
