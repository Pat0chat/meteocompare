package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    highlightedKey: String? = null,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val zone = remember(timezone) { resolveCityZone(timezone) }
    val today = remember(timezone, now) { cityLocalDate(timezone, now) }
    val currentHour = remember(timezone, now) { computeHourlyHorizon(timezone, now).first }
    val hourFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val pointWidth = chronoPointWidth(mode)
    val contentWidth = pointWidth * points.size.toFloat()
    val labelWidth = if (configuration.screenWidthDp >= 600) 138.dp else 116.dp
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
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.30f)),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ChronoLabelsColumn(
                width = labelWidth,
                mode = mode
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Box(modifier = Modifier.width(contentWidth)) {
                    ChronoGridBackdrop(
                        pointCount = points.size,
                        highlightedIndex = highlightedIndex,
                        modifier = Modifier.matchParentSize()
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        ChronoTemperaturePlot(
                            points = points,
                            mode = mode
                        )
                        ChronoConditionsLane(
                            points = points,
                            mode = mode,
                            zone = zone,
                            hourFormatter = hourFormatter,
                            dayFormatter = dayFormatter,
                            today = today,
                            currentHour = currentHour
                        )
                        ChronoRainLane(points = points)
                        ChronoCloudLane(points = points)
                        ChronoWindLane(points = points)
                        ChronoAgreementLane(points = points)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronoGridBackdrop(
    pointCount: Int,
    highlightedIndex: Int?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val columnDivider = scheme.outlineVariant.copy(alpha = 0.15f)
    val rowDivider = scheme.outlineVariant.copy(alpha = 0.24f)
    val highlight = scheme.primaryContainer.copy(alpha = 0.16f)
    val rowHeights = chronoRowHeights()

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

        for (index in 1 until pointCount) {
            val x = step * index
            drawLine(
                color = columnDivider,
                start = Offset(x, 10.dp.toPx()),
                end = Offset(x, size.height - 10.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }

        var y = 0f
        rowHeights.dropLast(1).forEach { height ->
            y += height.toPx()
            drawLine(
                color = rowDivider,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun ChronoLabelsColumn(
    width: Dp,
    mode: DisplayMode
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .width(width)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ChronoRowLabel(
                icon = Icons.Outlined.Thermostat,
                title = stringResource(R.string.metric_temperature),
                support = "°C",
                height = CHRONO_TEMP_HEIGHT,
                tint = temperatureMetricAccent()
            )
            ChronoRowLabel(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.detail_tab_conditions),
                support = if (mode == DisplayMode.HOURLY) "24 h" else stringResource(R.string.display_mode_daily),
                height = CHRONO_CONDITIONS_HEIGHT,
                tint = scheme.onSurfaceVariant
            )
            ChronoRowLabel(
                icon = Icons.Outlined.WaterDrop,
                title = stringResource(R.string.metric_precipitation),
                support = "% · mm",
                height = CHRONO_RAIN_HEIGHT,
                tint = precipitationMetricAccent()
            )
            ChronoRowLabel(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.engine_metric_cloud),
                support = "%",
                height = CHRONO_CLOUD_HEIGHT,
                tint = scheme.secondary
            )
            ChronoRowLabel(
                icon = Icons.Outlined.Air,
                title = stringResource(R.string.metric_wind),
                support = "km/h",
                height = CHRONO_WIND_HEIGHT,
                tint = windMetricAccent()
            )
            ChronoRowLabel(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.home_agreement_label),
                support = "%",
                height = CHRONO_AGREEMENT_HEIGHT,
                tint = scheme.primary
            )
        }

        ChronoLabelGrid(modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun ChronoLabelGrid(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val divider = scheme.outlineVariant.copy(alpha = 0.24f)
    val rowHeights = chronoRowHeights()

    Canvas(modifier = modifier) {
        var y = 0f
        rowHeights.dropLast(1).forEach { height ->
            y += height.toPx()
            drawLine(
                color = divider,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        drawLine(
            color = scheme.outlineVariant.copy(alpha = 0.42f),
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
    tint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
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
private fun ChronoTemperaturePlot(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode
) {
    val values = points.map { chronoTemperature(it, mode) }
    val finiteValues = values.filterNotNull().filter(Double::isFinite)
    val rawMin = finiteValues.minOrNull() ?: 0.0
    val rawMax = finiteValues.maxOrNull() ?: 1.0
    val center = (rawMin + rawMax) / 2.0
    val span = maxOf(5.0, rawMax - rawMin + 3.0)
    val min = center - span / 2.0
    val max = center + span / 2.0
    val scheme = MaterialTheme.colorScheme
    val lineColors = values.map { value ->
        value?.takeIf(Double::isFinite)?.let(::temperatureHeatmapColor)
            ?: scheme.onSurfaceVariant.copy(alpha = 0.34f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_TEMP_HEIGHT)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (points.isEmpty()) return@Canvas
            val stepPx = size.width / points.size
            val top = CHRONO_TEMP_PLOT_TOP.toPx()
            val bottom = CHRONO_TEMP_PLOT_BOTTOM.toPx()
            val plotHeight = (bottom - top).coerceAtLeast(1f)

            fun y(value: Double): Float =
                (top + ((max - value) / (max - min) * plotHeight)).toFloat()

            values.forEachIndexed { index, value ->
                if (value != null && value.isFinite()) {
                    val inset = 4.dp.toPx()
                    drawRoundRect(
                        color = temperatureHeatmapColor(value).copy(alpha = 0.055f),
                        topLeft = Offset(index * stepPx + inset, 6.dp.toPx()),
                        size = Size(
                            (stepPx - inset * 2).coerceAtLeast(0f),
                            size.height - 12.dp.toPx()
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                    )
                }
            }

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

            if (started) {
                val brush = Brush.horizontalGradient(
                    colors = if (lineColors.size >= 2) lineColors else lineColors + lineColors
                )
                drawPath(
                    path = path,
                    brush = brush,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                values.forEachIndexed { index, value ->
                    if (value == null || !value.isFinite()) return@forEachIndexed
                    val x = index * stepPx + stepPx / 2f
                    val pointY = y(value)
                    drawCircle(
                        color = scheme.surfaceContainerLowest,
                        radius = 5.2.dp.toPx(),
                        center = Offset(x, pointY)
                    )
                    drawCircle(
                        color = temperatureHeatmapColor(value),
                        radius = 3.4.dp.toPx(),
                        center = Offset(x, pointY)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            points.forEachIndexed { index, point ->
                val value = values[index]
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (value != null && value.isFinite()) {
                        val labelY = chronoTemperatureLabelOffset(value, min, max)
                        Box(
                            modifier = Modifier
                                .offset(y = labelY)
                                .clip(RoundedCornerShape(7.dp))
                                .background(scheme.surface.copy(alpha = 0.90f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = chronoTemperatureLabel(point, mode),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronoConditionsLane(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    zone: ZoneId,
    hourFormatter: DateTimeFormatter,
    dayFormatter: DateTimeFormatter,
    today: java.time.LocalDate,
    currentHour: Instant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_CONDITIONS_HEIGHT)
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
            ChronoCell {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier.height(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        point.condition?.let { condition ->
                            WeatherIconDecorative(
                                condition = condition,
                                size = 28.dp,
                                tint = condition.semanticTint()
                            )
                        } ?: Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = labels.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (index == 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                    labels.contextLabel?.let { context ->
                        Text(
                            text = context,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronoRainLane(points: List<SimplifiedTimelinePoint>) {
    val accent = precipitationMetricAccent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_RAIN_HEIGHT)
            .background(accent.copy(alpha = 0.018f))
    ) {
        points.forEach { point ->
            val probability = point.precipitationPercent?.coerceIn(0, 100)
            ChronoCell {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = probability?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = chronoRainAmountCompact(point),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun ChronoCloudLane(points: List<SimplifiedTimelinePoint>) {
    val accent = MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_CLOUD_HEIGHT)
            .background(accent.copy(alpha = 0.012f))
    ) {
        points.forEach { point ->
            val cloud = point.cloudCoverPercent?.coerceIn(0, 100)
            ChronoCell {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f))
                    ) {
                        if (cloud != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(cloud / 100f)
                                    .clip(RoundedCornerShape(50))
                                    .background(accent.copy(alpha = 0.72f))
                            )
                        }
                    }
                    Text(
                        text = cloud?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ChronoWindLane(points: List<SimplifiedTimelinePoint>) {
    val accent = windMetricAccent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_WIND_HEIGHT)
            .background(accent.copy(alpha = 0.014f))
    ) {
        points.forEach { point ->
            val maxWind = maxOf(point.windKmh ?: 0.0, point.windGustKmh ?: 0.0)
            val strength = (maxWind / 90.0).coerceIn(0.0, 1.0).toFloat()
            ChronoCell {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Air,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = point.windKmh?.let { "${it.roundToInt()}" } ?: "—",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = point.windGustKmh?.let {
                                    "${stringResource(R.string.wind_gust_abbreviation)} ${it.roundToInt()}"
                                } ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.70f))
                    ) {
                        if (strength > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(strength)
                                    .clip(RoundedCornerShape(50))
                                    .background(accent.copy(alpha = 0.72f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronoAgreementLane(points: List<SimplifiedTimelinePoint>) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHRONO_AGREEMENT_HEIGHT)
            .background(scheme.primary.copy(alpha = 0.010f))
    ) {
        points.forEach { point ->
            val reasons = chronoOrderedDivergenceReasons(point.divergenceReasons)
            val displayLevel = when {
                reasons.isNotEmpty() && point.consensusLevel == ModelConsensusLevel.HIGH ->
                    ModelConsensusLevel.MEDIUM
                else -> point.consensusLevel
            }
            val tone = when (displayLevel) {
                ModelConsensusLevel.HIGH -> scheme.primary
                ModelConsensusLevel.MEDIUM -> scheme.tertiary
                ModelConsensusLevel.LOW -> scheme.error
                null -> scheme.onSurfaceVariant
            }
            ChronoCell {
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
                                    .background(tone.copy(alpha = 0.08f)),
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
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
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
    today: java.time.LocalDate,
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
        currentDate == null || currentDate == previousDate || currentDate == today -> null
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

private fun chronoTemperature(point: SimplifiedTimelinePoint, mode: DisplayMode): Double? = when (mode) {
    DisplayMode.HOURLY -> point.temperatureC
    DisplayMode.DAILY -> when {
        point.tempMinC != null && point.tempMaxC != null -> (point.tempMinC + point.tempMaxC) / 2.0
        point.tempMaxC != null -> point.tempMaxC
        else -> point.tempMinC
    }
}

private fun chronoTemperatureLabel(point: SimplifiedTimelinePoint, mode: DisplayMode): String = when (mode) {
    DisplayMode.HOURLY -> point.temperatureC?.roundToInt()?.let { "$it°" } ?: "—"
    DisplayMode.DAILY -> {
        val high = point.tempMaxC?.roundToInt()
        val low = point.tempMinC?.roundToInt()
        when {
            high != null && low != null -> "$high° / $low°"
            high != null -> "$high°"
            low != null -> "$low°"
            else -> "—"
        }
    }
}

private fun chronoTemperatureLabelOffset(value: Double, min: Double, max: Double): Dp {
    val fraction = ((max - value) / (max - min)).coerceIn(0.0, 1.0).toFloat()
    val y = CHRONO_TEMP_PLOT_TOP + (CHRONO_TEMP_PLOT_BOTTOM - CHRONO_TEMP_PLOT_TOP) * fraction
    return maxOf(3.dp, minOf(y - 23.dp, CHRONO_TEMP_HEIGHT - 28.dp))
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

private fun chronoRowHeights(): List<Dp> = listOf(
    CHRONO_TEMP_HEIGHT,
    CHRONO_CONDITIONS_HEIGHT,
    CHRONO_RAIN_HEIGHT,
    CHRONO_CLOUD_HEIGHT,
    CHRONO_WIND_HEIGHT,
    CHRONO_AGREEMENT_HEIGHT
)

internal fun chronoPointWidth(mode: DisplayMode): Dp = when (mode) {
    DisplayMode.HOURLY -> 96.dp
    DisplayMode.DAILY -> 132.dp
}

internal const val TAG_TIMELINE_CHRONO_VIEW = "timeline_chrono_view"

private val CHRONO_TEMP_HEIGHT = 124.dp
private val CHRONO_CONDITIONS_HEIGHT = 88.dp
private val CHRONO_RAIN_HEIGHT = 64.dp
private val CHRONO_CLOUD_HEIGHT = 56.dp
private val CHRONO_WIND_HEIGHT = 66.dp
private val CHRONO_AGREEMENT_HEIGHT = 74.dp
private val CHRONO_AGREEMENT_REASONS_HEIGHT = 18.dp
private val CHRONO_AGREEMENT_REASON_ICON_BOX_SIZE = 18.dp
private val CHRONO_TEMP_PLOT_TOP = 34.dp
private val CHRONO_TEMP_PLOT_BOTTOM = 104.dp
