package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.util.windCardinal

import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat

import com.meteocompare.app.ui.components.UvIndexCompact
import com.meteocompare.app.ui.components.formatSunshineDuration
import com.meteocompare.app.ui.components.localizedSunshineDurationFormats
import com.meteocompare.app.ui.components.HumidityMetricIcon
import com.meteocompare.app.ui.components.DewPointMetricIcon

import com.meteocompare.app.core.charts.metricPlotValue
import com.meteocompare.app.core.charts.canonicalChartRange
import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.LocalWeatherUnits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.components.ConditionStatusIcon
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Vue « frise » de la chronologie.
 *
 * Toutes les métriques partagent exactement la même grille temporelle. La colonne
 * des libellés reste fixe et seule la zone des échéances défile horizontalement.
 */
@Composable
internal fun ChronoTimelineView(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    timezone: String?,
    now: Instant,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    highlightedKey: String? = null,
    showDailyRanges: Boolean = false,
    onConvergenceClick: (() -> Unit)? = null
) {
    if (points.isEmpty()) return

    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val today = remember(timezone, now) { cityLocalDate(timezone, now) }
    val currentHour = remember(timezone, now) { computeHourlyHorizon(timezone, now).first }
    val clockPreference = LocalClockFormatPreference.current
    val hourFormatter = remember(locale, clockPreference) { WeatherTimeFormat.hourPadded(locale, clockPreference) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val pointWidth = chronoPointWidth(mode, showDailyRanges)
    val contentWidth = pointWidth * points.size.toFloat()
    val availableWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val labelWidth = chronoLabelColumnWidth(
        availableWidthDp.toInt(), compact = mode == DisplayMode.DAILY && showDailyRanges
    )
    val scheme = MaterialTheme.colorScheme
    val highlightedIndex = remember(points, highlightedKey) {
        highlightedKey?.let { key -> points.indexOfFirst { timelinePointKey(it) == key } }
            ?.takeIf { it >= 0 }
    }
    val ariaLabel = stringResource(R.string.timeline_layout_chrono)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .testTag(TAG_TIMELINE_CHRONO_VIEW)
            .semantics { contentDescription = ariaLabel },
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ChronoLabelsColumn(
                width = labelWidth,
                mode = mode,
                showDailyRanges = showDailyRanges,
                onConvergenceClick = onConvergenceClick
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Box(modifier = Modifier.width(contentWidth)) {
                    ChronoGridBackdrop(
                        mode = mode,
                        showDailyRanges = showDailyRanges,
                        pointCount = points.size,
                        highlightedIndex = highlightedIndex,
                        modifier = Modifier.matchParentSize()
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        ChronoDateLane(
                            points = points,
                            mode = mode,
                            zone = zone,
                            hourFormatter = hourFormatter,
                            dayFormatter = dayFormatter,
                            today = today,
                            currentHour = currentHour,
                            highlightedIndex = highlightedIndex
                        )
                        ChronoTemperaturePlot(
                            points = points,
                            mode = mode,
                            showDailyRanges = showDailyRanges
                        )
                        if (mode == DisplayMode.DAILY) {
                            Row(Modifier.fillMaxWidth().height(chronoDailyApparentHeight(showDailyRanges, LocalDensity.current.fontScale))
                                .testTag("timeline_chrono_daily_apparent_lane")) {
                                points.forEach { point ->
                                    ChronoCell {
                                        if (showDailyRanges) {
                                            DailyApparentRangeLabels(point, LocalWeatherUnits.current, locale,
                                                "timeline_daily", modifier = Modifier.padding(horizontal = 4.dp))
                                        } else {
                                            Text(dailyApparentReading(point, LocalWeatherUnits.current),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        ChronoConditionsLane(points = points, mode = mode)
                        ChronoRainLane(points = points, mode = mode, showDailyRanges = showDailyRanges)
                        ChronoCloudLane(points = points, mode = mode, showDailyRanges = showDailyRanges)
                        ChronoSunshineLane(points = points)
                        ChronoPressureLane(points = points, mode = mode, showDailyRanges = showDailyRanges)
                        ChronoMoistureLane(points = points, humidity = true, mode = mode, showDailyRanges = showDailyRanges)
                        ChronoMoistureLane(points = points, humidity = false, mode = mode, showDailyRanges = showDailyRanges)
                        ChronoWindLane(points = points, mode = mode, showDailyRanges = showDailyRanges)
                        ChronoAgreementLane(points = points, onConvergenceClick = onConvergenceClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronoGridBackdrop(
    mode: DisplayMode,
    showDailyRanges: Boolean,
    pointCount: Int,
    highlightedIndex: Int?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val rowDivider = scheme.outlineVariant.copy(alpha = 0.12f)
    val highlight = scheme.primaryContainer.copy(alpha = 0.16f)
    val rowHeights = chronoRowHeights(mode, showDailyRanges, LocalDensity.current.fontScale)

    Canvas(modifier = modifier) {
        if (pointCount <= 0) return@Canvas
        val step = size.width / pointCount

        highlightedIndex?.let { index ->
            drawRoundRect(
                color = highlight,
                topLeft = Offset(index * step + 2.dp.toPx(), 3.dp.toPx()),
                size = Size(
                    width = (step - 4.dp.toPx()).coerceAtLeast(0f),
                    height = (size.height - 6.dp.toPx()).coerceAtLeast(0f)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
            )
        }

        var y = 0f
        rowHeights.dropLast(1).forEach { height ->
            y += height.toPx()
            drawLine(
                color = rowDivider,
                start = Offset(8.dp.toPx(), y),
                end = Offset(size.width - 8.dp.toPx(), y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun ChronoLabelsColumn(
    width: Dp,
    mode: DisplayMode,
    showDailyRanges: Boolean,
    onConvergenceClick: (() -> Unit)? = null,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .width(width)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.42f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ChronoRowLabel(
                icon = Icons.Outlined.DateRange,
                title = stringResource(R.string.timeline_date_label),
                support = stringResource(
                    if (mode == DisplayMode.HOURLY) {
                        R.string.display_mode_hourly
                    } else {
                        R.string.display_mode_daily
                    }
                ),
                height = CHRONO_DATE_HEIGHT,
                tint = scheme.primary
            )
            ChronoRowLabel(
                icon = Icons.Outlined.Thermostat,
                title = stringResource(R.string.metric_temperature),
                support = if (mode == DisplayMode.HOURLY) {
                    units.temperatureUnit + " · " + stringResource(R.string.apparent_temperature_widget_prefix)
                } else units.temperatureUnit,
                height = chronoTemperatureRowHeight(mode),
                tint = temperatureMetricAccent()
            )
            if (mode == DisplayMode.DAILY) {
                ChronoRowLabel(
                    icon = Icons.Outlined.Thermostat,
                    title = stringResource(R.string.apparent_temperature_widget_prefix),
                    support = units.temperatureUnit,
                    height = chronoDailyApparentHeight(showDailyRanges, LocalDensity.current.fontScale),
                    tint = temperatureMetricAccent()
                )
            }
            ChronoRowLabel(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.detail_tab_conditions),
                support = if (mode == DisplayMode.HOURLY) {
                    "24 h"
                } else {
                    stringResource(R.string.display_mode_daily)
                },
                height = CHRONO_CONDITIONS_HEIGHT,
                tint = scheme.onSurfaceVariant
            )
            ChronoRowLabel(
                icon = Icons.Outlined.WaterDrop,
                title = stringResource(R.string.metric_precipitation),
                support = "% · ${units.precipitationUnit}",
                height = chronoRainRowHeight(mode, showDailyRanges),
                tint = precipitationMetricAccent(),
                iconTag = "timeline_title_rain_icon"
            )
            ChronoRowLabel(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.engine_metric_cloud),
                support = units.label(WeatherUnit.PERCENT),
                height = chronoCloudRowHeight(mode, showDailyRanges),
                tint = scheme.secondary
            )
            ChronoRowLabel(
                icon = Icons.Filled.WbSunny,
                title = stringResource(R.string.compare_sunshine),
                support = "h",
                height = CHRONO_SUNSHINE_HEIGHT,
                tint = scheme.tertiary
            )
            ChronoRowLabel(
                icon = Icons.Outlined.Speed,
                title = stringResource(R.string.metric_pressure_short),
                support = units.label(WeatherUnit.PRESSURE),
                height = chronoPressureRowHeight(mode, showDailyRanges),
                tint = scheme.tertiary
            )
            ChronoRowLabel(
                icon = HumidityMetricIcon,
                title = stringResource(R.string.metric_humidity),
                support = "%",
                height = chronoMoistureRowHeight(mode, showDailyRanges),
                tint = scheme.secondary
            )
            ChronoRowLabel(
                icon = DewPointMetricIcon,
                title = stringResource(R.string.metric_dew_point),
                support = units.temperatureUnit,
                height = chronoMoistureRowHeight(mode, showDailyRanges),
                tint = scheme.tertiary
            )
            ChronoRowLabel(
                icon = Icons.Outlined.Air,
                title = stringResource(R.string.metric_wind),
                support = units.windUnit,
                height = chronoWindRowHeight(mode, showDailyRanges),
                tint = windMetricAccent(),
                iconTag = "timeline_title_wind_icon"
            )
            ChronoRowLabel(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.home_agreement_label),
                support = units.label(WeatherUnit.PERCENT),
                height = CHRONO_AGREEMENT_HEIGHT,
                tint = confidenceColor(80),
                onClick = onConvergenceClick,
                interactionTag = TAG_TIMELINE_CHRONO_CONVERGENCE_TITLE
            )
        }

        ChronoLabelGrid(mode = mode, showDailyRanges = showDailyRanges, modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun ChronoLabelGrid(mode: DisplayMode, showDailyRanges: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val divider = scheme.outlineVariant.copy(alpha = 0.10f)
    val rowHeights = chronoRowHeights(mode, showDailyRanges, LocalDensity.current.fontScale)

    Canvas(modifier = modifier) {
        var y = 0f
        rowHeights.dropLast(1).forEach { height ->
            y += height.toPx()
            drawLine(
                color = divider,
                start = Offset(12.dp.toPx(), y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        drawLine(
            color = scheme.outlineVariant.copy(alpha = 0.20f),
            start = Offset(size.width, 10.dp.toPx()),
            end = Offset(size.width, size.height - 10.dp.toPx()),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
private fun ChronoRowLabel(
    icon: ImageVector,
    title: String,
    support: String,
    height: Dp,
    tint: Color,
    iconTag: String? = null,
    onClick: (() -> Unit)? = null,
    interactionTag: String? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.timeline_convergence_open_info),
            onClick = onClick
        )
    } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(clickModifier)
            .then(interactionTag?.let { Modifier.testTag(it) } ?: Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                        .then(iconTag?.let { Modifier.testTag(it) } ?: Modifier)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = support,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ChronoDateLane(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    zone: ZoneId,
    hourFormatter: DateTimeFormatter,
    dayFormatter: DateTimeFormatter,
    today: LocalDate,
    currentHour: Instant,
    highlightedIndex: Int?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_DATE_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.22f))
            .testTag(TAG_TIMELINE_CHRONO_DATE_LANE)
    ) {
        points.forEachIndexed { index, point ->
            val labels = chronoLabels(
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
            ChronoCell(
                modifier = if (index == highlightedIndex) {
                    Modifier.testTag(TAG_TIMELINE_POINT_FOCUSED)
                } else {
                    Modifier
                }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    labels.contextLabel?.let { context ->
                        Text(
                            text = context,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = labels.timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (index == 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ChronoTemperaturePlot(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    showDailyRanges: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    val centralValues = remember(points, mode) { points.map { metricPlotValue(chronoTemperature(it, mode)) } }
    val apparentValues = remember(points, mode) { chronoApparentTemperatureSeries(points, mode) }
    val minValues = remember(points, mode) {
        if (mode == DisplayMode.DAILY) points.map { metricPlotValue(it.tempMinC) } else emptyList()
    }
    val maxValues = remember(points, mode) {
        if (mode == DisplayMode.DAILY) points.map { metricPlotValue(it.tempMaxC) } else emptyList()
    }
    val bounds = remember(centralValues, apparentValues, minValues, maxValues, mode) {
        chronoTemperaturePlotBounds(centralValues, apparentValues, minValues, maxValues, mode)
    }
    val min = bounds.min
    val max = bounds.max
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val apparentDashEffect = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) }
    }
    val fallbackLineColor = scheme.onSurfaceVariant.copy(alpha = 0.34f)
    val lineColors = remember(centralValues, fallbackLineColor) {
        chronoTemperatureHeatmapColors(centralValues, fallbackLineColor)
    }
    val minLineColors = remember(minValues, fallbackLineColor) {
        chronoTemperatureHeatmapColors(minValues, fallbackLineColor)
    }
    val maxLineColors = remember(maxValues, fallbackLineColor) {
        chronoTemperatureHeatmapColors(maxValues, fallbackLineColor)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(chronoTemperatureRowHeight(mode))
            .testTag(TAG_TIMELINE_CHRONO_TEMPERATURE_PLOT)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (points.isEmpty()) return@Canvas
            val stepPx = size.width / points.size
            val top = chronoTemperaturePlotTop(mode).toPx()
            val bottom = chronoTemperaturePlotBottom(mode).toPx()
            val plotHeight = (bottom - top).coerceAtLeast(1f)

            fun y(value: Double): Float =
                (top + ((max - value) / (max - min) * plotHeight)).toFloat()

            centralValues.forEachIndexed { index, value ->
                if (value != null && value.isFinite()) {
                    val inset = 4.dp.toPx()
                    drawRoundRect(
                        color = temperatureHeatmapColor(value).copy(alpha = 0.10f),
                        topLeft = Offset(index * stepPx + inset, 6.dp.toPx()),
                        size = Size(
                            (stepPx - inset * 2).coerceAtLeast(0f),
                            size.height - 12.dp.toPx()
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                    )
                }
            }

            if (mode == DisplayMode.DAILY) {
                drawChronoTemperatureSeries(
                    values = maxValues,
                    lineColors = maxLineColors,
                    stepPx = stepPx,
                    pointHaloColor = scheme.surfaceContainerLowest,
                    y = ::y
                )
                drawChronoTemperatureSeries(
                    values = minValues,
                    lineColors = minLineColors,
                    stepPx = stepPx,
                    pointHaloColor = scheme.surfaceContainerLowest,
                    y = ::y
                )
            } else {
                val path = Path()
                var started = false
                centralValues.forEachIndexed { index, value ->
                    if (value == null || !value.isFinite()) return@forEachIndexed
                    val x = index * stepPx + stepPx / 2f
                    val pointY = y(value)
                    if (!started) {
                        path.moveTo(x, pointY)
                        started = true
                    } else {
                        path.lineTo(x, pointY)
                    }
                }

                // The derived feels-like curve is deliberately secondary: same temperature
                // axis, no additional convergence metric or per-frame forecast calculation.
                var apparentPath = Path()
                var previousValid = false
                apparentValues.forEachIndexed { index, value ->
                    if (value == null || !value.isFinite()) {
                        previousValid = false
                        return@forEachIndexed
                    }
                    val x = index * stepPx + stepPx / 2f
                    if (previousValid) apparentPath.lineTo(x, y(value))
                    else apparentPath.moveTo(x, y(value))
                    previousValid = true
                }
                drawPath(
                    path = apparentPath,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.58f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = apparentDashEffect
                    )
                )
                if (started) {
                    val brush = Brush.horizontalGradient(
                        colors = if (lineColors.size >= 2) lineColors else lineColors + lineColors
                    )
                    drawPath(
                        path = path,
                        brush = brush,
                        style = Stroke(
                            width = CHRONO_TEMP_LINE_WIDTH.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    centralValues.forEachIndexed { index, value ->
                        if (value == null || !value.isFinite()) return@forEachIndexed
                        val x = index * stepPx + stepPx / 2f
                        val pointY = y(value)
                        drawCircle(
                            color = scheme.surfaceContainerLowest,
                            radius = CHRONO_TEMP_POINT_HALO_RADIUS.toPx(),
                            center = Offset(x, pointY)
                        )
                        drawCircle(
                            color = temperatureHeatmapColor(value),
                            radius = CHRONO_TEMP_POINT_RADIUS.toPx(),
                            center = Offset(x, pointY)
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            points.forEachIndexed { index, point ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (mode == DisplayMode.DAILY) {
                        val high = maxValues.getOrNull(index)
                        val low = minValues.getOrNull(index)
                        high?.takeIf(Double::isFinite)?.let { value ->
                            val range = if (showDailyRanges)
                                dailyMetricRange(point, DailyRangeMetric.HIGH_TEMPERATURE, units, displayLocale)
                                else null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.offset(y = chronoDailyHighTemperatureLabelOffset(
                                    value, min, max, withRange = range != null))
                            ) {
                                // The range is ABOVE the central high. Neither text row crosses the high curve.
                                if (range != null) Text(
                                    text = range,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("timeline_daily_temp_max_range")
                                )
                                Text(
                                    text = units.temp(value),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = temperatureHeatmapColor(value),
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                )
                            }
                        }
                        low?.takeIf(Double::isFinite)?.let { value ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.offset(y = chronoDailyLowTemperatureLabelOffset(value, min, max))
                            ) {
                                Text(
                                    text = units.temp(value),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = temperatureHeatmapColor(value),
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                )
                                (if (showDailyRanges) dailyMetricRange(
                                    point, DailyRangeMetric.LOW_TEMPERATURE, units, displayLocale
                                ) else null)?.let { range ->
                                    Text(
                                        text = range,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.testTag("timeline_daily_temp_min_range")
                                    )
                                }
                            }
                        }
                    } else {
                        apparentValues.getOrNull(index)?.takeIf(Double::isFinite)?.let { apparent ->
                            Text(
                                text = stringResource(R.string.apparent_temperature_short, units.temp(apparent)),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = (-6).dp)
                                    .testTag(TAG_TIMELINE_CHRONO_APPARENT)
                                    .padding(horizontal = 2.dp)
                            )
                        }
                        val value = centralValues.getOrNull(index)
                        if (value != null && value.isFinite()) {
                            val labelY = chronoTemperatureLabelOffset(value, min, max)
                            Text(
                                text = chronoTemperatureLabel(point, mode, units = units),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier
                                    .offset(y = labelY)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChronoTemperatureSeries(
    values: List<Double?>,
    lineColors: List<Color>,
    stepPx: Float,
    pointHaloColor: Color,
    y: (Double) -> Float
) {
    val path = Path()
    var started = false
    values.forEachIndexed { index, value ->
        if (value == null || !value.isFinite()) return@forEachIndexed
        val x = index * stepPx + stepPx / 2f
        val pointY = y(value)
        if (!started) {
            path.moveTo(x, pointY)
            started = true
        } else {
            path.lineTo(x, pointY)
        }
    }
    if (!started) return

    // Même grammaire visuelle que la courbe horaire : trait arrondi de 3 dp,
    // puis point cerclé par la couleur de fond avant le cœur coloré.
    val gradientColors = when {
        lineColors.size >= 2 -> lineColors
        lineColors.size == 1 -> listOf(lineColors.first(), lineColors.first())
        else -> listOf(Color.Transparent, Color.Transparent)
    }
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(colors = gradientColors),
        style = Stroke(
            width = CHRONO_TEMP_LINE_WIDTH.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    values.forEachIndexed { index, value ->
        if (value == null || !value.isFinite()) return@forEachIndexed
        val x = index * stepPx + stepPx / 2f
        val pointY = y(value)
        drawCircle(
            color = pointHaloColor,
            radius = CHRONO_TEMP_POINT_HALO_RADIUS.toPx(),
            center = Offset(x, pointY)
        )
        drawCircle(
            color = temperatureHeatmapColor(value),
            radius = CHRONO_TEMP_POINT_RADIUS.toPx(),
            center = Offset(x, pointY)
        )
    }
}


internal fun chronoTemperatureHeatmapColors(
    values: List<Double?>,
    fallback: Color
): List<Color> = values.map { value ->
    value?.takeIf(Double::isFinite)?.let(::temperatureHeatmapColor) ?: fallback
}

@Composable
private fun ChronoConditionsLane(points: List<SimplifiedTimelinePoint>, mode: DisplayMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_CONDITIONS_HEIGHT)
            .testTag(TAG_TIMELINE_CHRONO_CONDITIONS_LANE)
    ) {
        points.forEach { point ->
            ChronoCell {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.height(if (mode == DisplayMode.DAILY) 48.dp else 34.dp),
                        contentAlignment = Alignment.Center
                    ) {
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
                                size = 28.dp,
                                tint = if (point.isNight == true && point.condition == com.meteocompare.app.domain.model.WeatherCondition.CLEAR)
                                    Color.Unspecified else point.condition?.semanticTint() ?: Color.Unspecified,
                                isNight = point.isNight == true
                            )
                        }
                    }
                    UvIndexCompact(point.uvIndex)
                }
            }
        }
    }
}

/** One shared, measured label for both daily chronology presentations. */
@Composable
private fun ChronoDailyReading(
    value: String,
    point: SimplifiedTimelinePoint,
    metric: DailyRangeMetric,
    units: WeatherUnits,
    showDailyRanges: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
    valueTag: String? = null,
    alignStart: Boolean = false
) {
    val locale = LocalConfiguration.current.locales[0]
    DailyRangeLabel(
        value = value,
        range = if (showDailyRanges) dailyMetricRange(point, metric, units, locale) else null,
        rangeTag = tag,
        valueTag = valueTag,
        modifier = modifier,
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
        valueStyle = valueStyle,
        valueColor = MaterialTheme.colorScheme.onSurface,
        rangeColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ChronoRainLane(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    showDailyRanges: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val accent = precipitationMetricAccent()
    Row(modifier = Modifier.fillMaxWidth().height(chronoRainRowHeight(mode, showDailyRanges))
        .background(accent.copy(alpha = 0.018f))) {
        points.forEach { point ->
            val probability = point.precipitationPercent?.coerceIn(0, 100)
            ChronoCell {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)
                ) {
                    // The fixed title column already contains the rain icon.
                    // Center the reading in both hourly and daily cells.
                    ChronoDailyReading(
                        value = probability?.let { "$it%" } ?: "—", point = point,
                        metric = DailyRangeMetric.RAIN_PROBABILITY, units = units,
                        showDailyRanges = showDailyRanges && mode == DisplayMode.DAILY,
                        tag = "timeline_daily_rain_probability_range",
                        modifier = Modifier.fillMaxWidth(),
                        valueTag = "timeline_daily_rain_probability_value"
                    )
                    Spacer(Modifier.height(if (showDailyRanges && mode == DisplayMode.DAILY) 2.dp else 3.dp))
                    ChronoDailyReading(
                        value = chronoRainAmountCompact(point), point = point,
                        metric = DailyRangeMetric.RAIN_AMOUNT, units = units,
                        showDailyRanges = showDailyRanges && mode == DisplayMode.DAILY,
                        tag = "timeline_daily_rain_range",
                        valueTag = if (mode == DisplayMode.DAILY) "timeline_daily_rain_amount"
                            else "timeline_hourly_rain_amount",
                        modifier = Modifier.fillMaxWidth(),
                        valueStyle = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ChronoCloudLane(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    showDailyRanges: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val accent = MaterialTheme.colorScheme.secondary
    Row(modifier = Modifier.fillMaxWidth().height(chronoCloudRowHeight(mode, showDailyRanges))
        .background(accent.copy(alpha = 0.012f))) {
        points.forEach { point ->
            val cloud = point.cloudCoverPercent?.coerceIn(0, 100)
            ChronoCell {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f))) {
                        if (cloud != null) Box(modifier = Modifier.fillMaxHeight()
                            .fillMaxWidth(cloud / 100f).clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.72f)))
                    }
                    Spacer(Modifier.height(4.dp))
                    ChronoDailyReading(
                        value = cloud?.let { "$it%" } ?: "—", point = point,
                        metric = DailyRangeMetric.CLOUD_COVER, units = units,
                        showDailyRanges = showDailyRanges && mode == DisplayMode.DAILY,
                        tag = "timeline_daily_cloud_range", modifier = Modifier.fillMaxWidth(),
                        valueStyle = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ChronoSunshineLane(
    points: List<SimplifiedTimelinePoint>,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val locale = LocalConfiguration.current.locales[0]
    val formats = localizedSunshineDurationFormats()
    Row(modifier = Modifier.fillMaxWidth().height(CHRONO_SUNSHINE_HEIGHT)) {
        points.forEach { point ->
            ChronoCell {
                Text(
                    text = formatSunshineDuration(point.estimatedSunHours, hourly = point.instant != null, units, locale, formats),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ChronoPressureLane(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    showDailyRanges: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val accent = MaterialTheme.colorScheme.tertiary
    Row(modifier = Modifier.fillMaxWidth().height(chronoPressureRowHeight(mode, showDailyRanges))
        .background(accent.copy(alpha = 0.025f)).testTag("timeline_chrono_pressure_lane")) {
        points.forEach { point ->
            ChronoCell {
                ChronoDailyReading(
                    value = point.pressureHpa?.let {
                        units.format(it, WeatherUnit.PRESSURE, if (units.pressureInInHg) 2 else 0)
                    } ?: "—", point = point,
                    metric = DailyRangeMetric.PRESSURE, units = units,
                    showDailyRanges = showDailyRanges && mode == DisplayMode.DAILY,
                    tag = "timeline_daily_pressure_range",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ChronoMoistureLane(
    points: List<SimplifiedTimelinePoint>,
    humidity: Boolean,
    mode: DisplayMode,
    showDailyRanges: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val tint = if (humidity) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
    Row(modifier = Modifier.fillMaxWidth().height(chronoMoistureRowHeight(mode, showDailyRanges))
        .background(tint.copy(alpha = 0.025f))
        .testTag(if (humidity) "timeline_chrono_humidity_lane" else "timeline_chrono_dew_point_lane")) {
        points.forEach { point ->
            ChronoCell {
                ChronoDailyReading(
                    value = if (humidity) point.humidityPercent?.let { "$it %" } ?: "—"
                        else units.temp(point.dewPointC),
                    point = point,
                    metric = if (humidity) DailyRangeMetric.HUMIDITY else DailyRangeMetric.DEW_POINT,
                    units = units, showDailyRanges = showDailyRanges && mode == DisplayMode.DAILY,
                    tag = if (humidity) "timeline_daily_humidity_range" else "timeline_daily_dew_point_range",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ChronoWindLane(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    showDailyRanges: Boolean,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val accent = windMetricAccent()
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier = Modifier.fillMaxWidth().height(chronoWindRowHeight(mode, showDailyRanges))
        .background(accent.copy(alpha = 0.014f))) {
        points.forEach { point ->
            val maxWind = maxOf(point.windKmh ?: 0.0, point.windGustKmh ?: 0.0)
            val strength = (maxWind / 90.0).coerceIn(0.0, 1.0).toFloat()
            ChronoCell {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp),
                    verticalArrangement = Arrangement.Center) {
                    // The left-hand row title already identifies the wind;
                    // removing its duplicate icon gives both readings full width.
                    ChronoDailyReading(
                        value = point.windKmh?.let {
                            val speed = units.speed(it)
                            windCardinal(point.windDirectionDeg, locale)
                                ?.let { direction -> "$speed · $direction" } ?: speed
                        } ?: "—", point = point,
                        metric = DailyRangeMetric.WIND, units = units,
                        showDailyRanges = showDailyRanges && mode == DisplayMode.DAILY,
                        tag = "timeline_daily_wind_range",
                        modifier = Modifier.fillMaxWidth(),
                        valueTag = "timeline_daily_wind_value"
                    )
                    Spacer(Modifier.height(2.dp))
                    ChronoDailyReading(
                        value = point.windGustKmh?.let {
                            stringResource(R.string.timeline_wind_gust, it)
                        } ?: "—",
                        point = point,
                        metric = DailyRangeMetric.WIND_GUST, units = units,
                        showDailyRanges = showDailyRanges && mode == DisplayMode.DAILY,
                        tag = "timeline_daily_gust_range",
                        modifier = Modifier.fillMaxWidth(),
                        valueStyle = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.70f))) {
                        if (strength > 0f) Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(strength)
                            .clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.72f)))
                    }
                }
            }
        }
    }
}

internal const val TAG_TIMELINE_CHRONO_CONVERGENCE_CELL = "timeline_chrono_convergence_cell"
internal const val TAG_TIMELINE_CHRONO_CONVERGENCE_TITLE = "timeline_chrono_convergence_title"

@Composable
private fun ChronoAgreementLane(
    points: List<SimplifiedTimelinePoint>,
    onConvergenceClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val openInfoLabel = stringResource(R.string.timeline_convergence_open_info)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_AGREEMENT_HEIGHT)
    ) {
        points.forEach { point ->
            val reasons = chronoOrderedDivergenceReasons(point.divergenceReasons)
            val displayLevel = timelineConsensusDisplayLevel(point)
            val colorAnchor = timelineConsensusColorAnchor(displayLevel)
            val tone = if (colorAnchor != null) {
                confidenceColor(colorAnchor)
            } else {
                scheme.onSurfaceVariant
            }
            val clickModifier = if (onConvergenceClick != null) {
                Modifier.clickable(role = Role.Button, onClickLabel = openInfoLabel,
                    onClick = onConvergenceClick)
            } else Modifier
            ChronoCell(modifier = Modifier.testTag(TAG_TIMELINE_CHRONO_CONVERGENCE_CELL)
                .then(clickModifier)) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = point.consensusPercent?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = tone
                        )
                        Icon(
                            imageVector = if (reasons.isEmpty()) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.WarningAmber
                            },
                            contentDescription = null,
                            tint = tone,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(scheme.surfaceContainerHighest.copy(alpha = 0.76f))
                    ) {
                        point.consensusPercent?.coerceIn(0, 100)?.let { percent ->
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(percent / 100f)
                                    .clip(RoundedCornerShape(50))
                                    .background(tone.copy(alpha = 0.88f))
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().height(CHRONO_AGREEMENT_REASONS_HEIGHT),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        reasons.take(3).forEach { reason ->
                            Box(
                                modifier = Modifier
                                    .padding(start = 3.dp)
                                    .size(CHRONO_AGREEMENT_REASON_ICON_BOX_SIZE)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(tone.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = chronoDivergenceIcon(reason),
                                    contentDescription = null,
                                    tint = tone,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ChronoCell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private data class ChronoLabels(
    val timeLabel: String,
    val contextLabel: String?
)

@Composable
private fun chronoLabels(
    point: SimplifiedTimelinePoint,
    index: Int,
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    zone: ZoneId,
    hourFormatter: DateTimeFormatter,
    dayFormatter: DateTimeFormatter,
    today: LocalDate,
    currentHour: Instant
): ChronoLabels {
    if (mode == DisplayMode.DAILY) {
        val date = point.date
        val label = when {
            date == null -> "—"
            date == today -> stringResource(R.string.timeline_today)
            date == today.plusDays(1) -> stringResource(R.string.timeline_tomorrow)
            else -> date.format(dayFormatter).replaceFirstChar { it.uppercase() }
        }
        return ChronoLabels(label, null)
    }

    val zoned = point.instant?.atZone(zone)
    val currentDate = zoned?.toLocalDate()
    val previousDate = points.getOrNull(index - 1)?.instant?.atZone(zone)?.toLocalDate()
    val context = when {
        currentDate == null -> null
        index > 0 && currentDate == previousDate -> null
        currentDate == today -> stringResource(R.string.timeline_today)
        currentDate == today.plusDays(1) -> stringResource(R.string.timeline_tomorrow)
        else -> currentDate.format(dayFormatter).replaceFirstChar { it.uppercase() }
    }
    val time = when {
        point.instant == currentHour -> stringResource(R.string.timeline_now)
        zoned != null -> zoned.format(hourFormatter)
        else -> "—"
    }
    return ChronoLabels(time, context)
}

/** Use both hourly curves for the shared axis, but only min/max for daily. */
internal fun chronoTemperaturePlotBounds(
    temperatures: List<Double?>,
    apparent: List<Double?>,
    minimums: List<Double?>,
    maximums: List<Double?>,
    mode: DisplayMode
): com.meteocompare.app.core.charts.CanonicalChartRange {
    val values = if (mode == DisplayMode.DAILY) minimums + maximums else temperatures + apparent
    return canonicalChartRange(values, minimumSpan = 5.0, minimumPadding = 1.5, paddingFraction = 0.0)
}

/** Prepared once per timeline update; invalid/missing readings break the secondary curve. */
internal fun chronoApparentTemperatureSeries(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode
): List<Double?> = if (mode == DisplayMode.HOURLY) {
    points.map { metricPlotValue(it.apparentTemperatureC) }
} else emptyList()

private fun chronoTemperature(point: SimplifiedTimelinePoint, mode: DisplayMode): Double? = when (mode) {
    DisplayMode.HOURLY -> point.temperatureC
    DisplayMode.DAILY -> when {
        point.tempMinC != null && point.tempMaxC != null -> (point.tempMinC + point.tempMaxC) / 2.0
        point.tempMaxC != null -> point.tempMaxC
        else -> point.tempMinC
    }
}

private fun chronoTemperatureLabel(point: SimplifiedTimelinePoint, mode: DisplayMode, units: WeatherUnits): String = when (mode) {
    DisplayMode.HOURLY -> units.temp(point.temperatureC)
    DisplayMode.DAILY -> {
        val high = point.tempMaxC
        val low = point.tempMinC
        when {
            high != null && low != null -> "${units.temp(high)} / ${units.temp(low)}"
            high != null -> units.temp(high)
            low != null -> units.temp(low)
            else -> "—"
        }
    }
}

private fun chronoTemperatureValueY(
    value: Double, min: Double, max: Double,
    mode: DisplayMode = DisplayMode.HOURLY
): Dp {
    val fraction = ((max - value) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val top = chronoTemperaturePlotTop(mode)
    return top + (chronoTemperaturePlotBottom(mode) - top) * fraction
}

private fun chronoTemperatureLabelOffset(value: Double, min: Double, max: Double): Dp {
    val y = chronoTemperatureValueY(value, min, max)
    return maxOf(3.dp, minOf(y - 23.dp, CHRONO_TEMP_HEIGHT - 28.dp))
}

/** Place le maximum au-dessus de sa courbe en mode journalier. */
internal fun chronoDailyHighTemperatureLabelOffset(
    value: Double, min: Double, max: Double, withRange: Boolean = false
): Dp {
    val y = chronoTemperatureValueY(value, min, max, DisplayMode.DAILY)
    // Two text lines above the curve require more clearance than a single label.
    val offset = if (withRange) 40.dp else 23.dp
    return maxOf(2.dp, minOf(y - offset, chronoTemperatureRowHeight(DisplayMode.DAILY) - 48.dp))
}

/**
 * Place le minimum sous sa courbe en mode journalier.
 * Le plot daily se termine à 98 dp dans un conteneur de 148 dp :
 * les deux lignes (valeur et plage) tiennent sous la courbe sans chevauchement.
 */
internal fun chronoDailyLowTemperatureLabelOffset(value: Double, min: Double, max: Double): Dp {
    val y = chronoTemperatureValueY(value, min, max, DisplayMode.DAILY)
    return maxOf(3.dp, minOf(y + 5.dp, chronoTemperatureRowHeight(DisplayMode.DAILY) - 36.dp))
}

@Composable
private fun chronoRainAmountCompact(point: SimplifiedTimelinePoint): String {
    val amount = point.precipitationConditionalMm
        ?.takeIf { it.isFinite() && it >= 0.05 }
        ?: point.precipitationMm?.takeIf { it.isFinite() && it >= 0.0 }
    return amount?.let { stringResource(R.string.timeline_precip_amount, it) } ?: "—"
}

private fun chronoOrderedDivergenceReasons(reasons: Set<DivergenceReason>): List<DivergenceReason> = listOf(
    DivergenceReason.PRECIPITATION,
    DivergenceReason.WIND,
    DivergenceReason.TEMPERATURE,
    DivergenceReason.CONDITION
).filter { it in reasons }

private fun chronoDivergenceIcon(reason: DivergenceReason): ImageVector = when (reason) {
    DivergenceReason.PRECIPITATION -> Icons.Outlined.WaterDrop
    DivergenceReason.WIND -> Icons.Outlined.Air
    DivergenceReason.TEMPERATURE -> Icons.Outlined.Thermostat
    DivergenceReason.CONDITION -> Icons.Outlined.Cloud
}

internal fun chronoTemperatureRowHeight(mode: DisplayMode): Dp =
    if (mode == DisplayMode.DAILY) 148.dp else CHRONO_TEMP_HEIGHT
private fun chronoTemperaturePlotTop(mode: DisplayMode): Dp =
    if (mode == DisplayMode.DAILY) 40.dp else CHRONO_TEMP_PLOT_TOP
private fun chronoTemperaturePlotBottom(mode: DisplayMode): Dp =
    if (mode == DisplayMode.DAILY) 98.dp else CHRONO_TEMP_PLOT_BOTTOM
private fun chronoRainRowHeight(mode: DisplayMode, showDailyRanges: Boolean): Dp =
    if (mode == DisplayMode.DAILY) { if (showDailyRanges) 64.dp else 58.dp } else CHRONO_RAIN_HEIGHT
private fun chronoCloudRowHeight(mode: DisplayMode, showDailyRanges: Boolean): Dp =
    if (mode == DisplayMode.DAILY) { if (showDailyRanges) 49.dp else 46.dp } else CHRONO_CLOUD_HEIGHT
private fun chronoPressureRowHeight(mode: DisplayMode, showDailyRanges: Boolean): Dp =
    if (mode == DisplayMode.DAILY) { if (showDailyRanges) 50.dp else 48.dp } else CHRONO_PRESSURE_HEIGHT
private fun chronoMoistureRowHeight(mode: DisplayMode, showDailyRanges: Boolean): Dp =
    if (mode == DisplayMode.DAILY) { if (showDailyRanges) 49.dp else 46.dp } else CHRONO_MOISTURE_HEIGHT
private fun chronoWindRowHeight(mode: DisplayMode, showDailyRanges: Boolean): Dp =
    if (mode == DisplayMode.DAILY) { if (showDailyRanges) 80.dp else 75.dp } else CHRONO_WIND_HEIGHT

private val CHRONO_DAILY_APPARENT_HEIGHT = 50.dp

internal fun chronoDailyApparentHeight(showDailyRanges: Boolean, fontScale: Float = 1f): Dp =
    CHRONO_DAILY_APPARENT_HEIGHT * if (showDailyRanges) fontScale.coerceAtLeast(1f) else 1f

internal fun chronoRowHeights(mode: DisplayMode, showDailyRanges: Boolean, fontScale: Float = 1f): List<Dp> = listOf(
    CHRONO_DATE_HEIGHT,
    chronoTemperatureRowHeight(mode)
) + (if (mode == DisplayMode.DAILY) listOf(chronoDailyApparentHeight(showDailyRanges, fontScale)) else emptyList()) + listOf(
    CHRONO_CONDITIONS_HEIGHT,
    chronoRainRowHeight(mode, showDailyRanges),
    chronoCloudRowHeight(mode, showDailyRanges),
    CHRONO_SUNSHINE_HEIGHT,
    chronoPressureRowHeight(mode, showDailyRanges),
    chronoMoistureRowHeight(mode, showDailyRanges),
    chronoMoistureRowHeight(mode, showDailyRanges),
    chronoWindRowHeight(mode, showDailyRanges),
    CHRONO_AGREEMENT_HEIGHT
)

internal fun chronoPointWidth(mode: DisplayMode, showDailyRanges: Boolean = false): Dp = when (mode) {
    DisplayMode.HOURLY -> 96.dp
    DisplayMode.DAILY -> 148.dp
}

internal fun chronoLabelColumnWidth(screenWidthDp: Int, compact: Boolean = false): Dp =
    if (screenWidthDp >= 600) { if (compact) 120.dp else 128.dp }
    else { if (compact) 96.dp else 104.dp }

internal const val TAG_TIMELINE_CHRONO_VIEW = "timeline_chrono_view"
internal const val TAG_TIMELINE_CHRONO_DATE_LANE = "timeline_chrono_date_lane"
internal const val TAG_TIMELINE_CHRONO_APPARENT = "timeline_chrono_apparent"
internal const val TAG_TIMELINE_CHRONO_TEMPERATURE_PLOT = "timeline_chrono_temperature_plot"
internal const val TAG_TIMELINE_CHRONO_CONDITIONS_LANE = "timeline_chrono_conditions_lane"

private val CHRONO_DATE_HEIGHT = 52.dp
private val CHRONO_TEMP_HEIGHT = 118.dp
private val CHRONO_CONDITIONS_HEIGHT = 65.dp
private val CHRONO_RAIN_HEIGHT = 60.dp
private val CHRONO_CLOUD_HEIGHT = 52.dp
private val CHRONO_SUNSHINE_HEIGHT = 45.dp
private val CHRONO_PRESSURE_HEIGHT = 52.dp
private val CHRONO_MOISTURE_HEIGHT = 49.dp
private val CHRONO_WIND_HEIGHT = 62.dp
private val CHRONO_AGREEMENT_HEIGHT = 70.dp
private val CHRONO_AGREEMENT_REASONS_HEIGHT = 18.dp
private val CHRONO_AGREEMENT_REASON_ICON_BOX_SIZE = 18.dp
private val CHRONO_TEMP_PLOT_TOP = 30.dp
private val CHRONO_TEMP_PLOT_BOTTOM = 94.dp
internal val CHRONO_TEMP_LINE_WIDTH = 3.dp
internal val CHRONO_TEMP_POINT_HALO_RADIUS = 5.2.dp
internal val CHRONO_TEMP_POINT_RADIUS = 3.4.dp
