package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.TideEvent
import com.meteocompare.app.domain.model.TideEventType
import com.meteocompare.app.domain.util.detectTideEvents
import com.meteocompare.app.domain.util.nearestMarineIndex
import com.meteocompare.app.domain.util.tideRangeNext24h
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
internal fun MarineSection(
    state: MarineUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val loaded = state as? MarineUiState.Loaded
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.marine_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val distance = loaded?.data?.grid?.distanceKm
                    Text(
                        text = if (distance != null) {
                            stringResource(R.string.marine_distance, distance)
                        } else {
                            stringResource(R.string.marine_intro)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onRefresh,
                    enabled = state !is MarineUiState.Loading && loaded?.isRefreshing != true
                ) {
                    Text(
                        if (loaded?.isRefreshing == true || state is MarineUiState.Loading) {
                            stringResource(R.string.marine_loading)
                        } else {
                            stringResource(R.string.action_refresh_marine)
                        }
                    )
                }
            }

            when (state) {
                MarineUiState.Idle, MarineUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is MarineUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
                }

                is MarineUiState.Loaded -> MarineDashboard(state.data)
            }
        }
    }
}

@Composable
private fun MarineDashboard(data: MarineForecast) {
    val now = System.currentTimeMillis()
    val index = remember(data, now / 60_000L) { data.nearestMarineIndex(now) }
    val h = data.hourly
    fun value(values: List<Double?>): Double? = values.getOrNull(index)
    val waveDirection = value(h.waveDirection)

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MarineKpi(stringResource(R.string.marine_wave_height), format(value(h.waveHeight), "m"))
        MarineKpi(stringResource(R.string.marine_wave_period), format(value(h.wavePeriod), "s"))
        MarineKpi(
            stringResource(R.string.marine_wave_direction),
            waveDirection?.let { "${it.toInt()}° ${compass(it)}" } ?: "—"
        )
        MarineKpi(stringResource(R.string.marine_swell_height), format(value(h.swellHeight), "m"))
        MarineKpi(stringResource(R.string.marine_sea_temp), format(value(h.seaSurfaceTemperature), "°C"))
    }

    MarineSurface(
        title = stringResource(R.string.marine_wave_evolution),
        subtitle = stringResource(R.string.marine_next_48h)
    ) {
        MarineLineChart(
            epochs = h.timestampEpochMs,
            values = h.waveHeight,
            nowEpochMs = now,
            hours = 48,
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.marine_daily_outlook),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            data.daily.dates.take(7).forEachIndexed { i, date ->
                MarineDayCard(data, date, i)
            }
        }
    }

    val tideEvents = remember(data, now / 60_000L) { data.detectTideEvents(hours = 72, nowEpochMs = now).take(6) }
    val tideRange = remember(data, now / 60_000L) { data.tideRangeNext24h(now) }
    val currentLevel = value(h.seaLevelHeightMsl)
    val nextLevel = h.seaLevelHeightMsl.getOrNull(index + 1)
    val trend = when {
        currentLevel == null || nextLevel == null -> stringResource(R.string.marine_unavailable)
        nextLevel - currentLevel > 0.015 -> stringResource(R.string.marine_trend_rising)
        currentLevel - nextLevel > 0.015 -> stringResource(R.string.marine_trend_falling)
        else -> stringResource(R.string.marine_trend_steady)
    }

    MarineSurface(
        title = stringResource(R.string.marine_tides),
        subtitle = stringResource(R.string.marine_tides_intro)
    ) {
        MarineLineChart(
            epochs = h.timestampEpochMs,
            values = h.seaLevelHeightMsl,
            nowEpochMs = now,
            hours = 72,
            events = tideEvents,
            modifier = Modifier.fillMaxWidth().height(260.dp)
        )
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(stringResource(R.string.marine_current_level), style = MaterialTheme.typography.labelMedium)
                Text(
                    text = format(currentLevel, "m", digits = 2),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stringResource(R.string.marine_reference_msl)} · $trend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MarineFact(
                label = stringResource(R.string.marine_next_extremum),
                value = tideEvents.firstOrNull()?.let {
                    stringResource(if (it.type == TideEventType.HIGH) R.string.marine_high_water else R.string.marine_low_water)
                } ?: "—",
                detail = tideEvents.firstOrNull()?.let { "${eventTime(it)} · ${format(it.value, "m", 2)}" }
                    ?: stringResource(R.string.marine_tide_unavailable),
                modifier = Modifier.weight(1f)
            )
            MarineFact(
                label = stringResource(R.string.marine_tide_range_short),
                value = tideRange?.let { format(it.range, "m", 2) } ?: "—",
                detail = stringResource(R.string.marine_next_24h),
                modifier = Modifier.weight(1f)
            )
        }
        if (tideEvents.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            tideEvents.take(4).forEach { TideRow(it) }
        }
        Text(
            text = stringResource(R.string.marine_tide_datum_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text(
        text = stringResource(R.string.marine_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    Text(
        text = stringResource(R.string.marine_source),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MarineKpi(label: String, value: String) {
    Surface(
        modifier = Modifier.width(142.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MarineSurface(title: String, subtitle: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MarineDayCard(data: MarineForecast, date: String, index: Int) {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    val dayLabel = runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }.getOrDefault(date)
    Surface(
        modifier = Modifier.width(136.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(dayLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(format(data.daily.waveHeightMax.getOrNull(index), "m"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${format(data.daily.wavePeriodMax.getOrNull(index), "s")} · ${format(data.daily.swellHeightMax.getOrNull(index), "m")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val direction = data.daily.waveDirectionDominant.getOrNull(index)
            Text(direction?.let { "${it.toInt()}° ${compass(it)}" } ?: "—", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MarineFact(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TideRow(event: TideEvent) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(12.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(if (event.type == TideEventType.HIGH) "↥" else "↧", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (event.type == TideEventType.HIGH) R.string.marine_high_water else R.string.marine_low_water),
                style = MaterialTheme.typography.labelLarge
            )
            Text(eventDateTime(event), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(format(event.value, "m", 2), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun eventDateTime(event: TideEvent): String {
    val locale = LocalContext.current.resources.configuration.locales[0] ?: Locale.getDefault()
    return runCatching {
        LocalDateTime.parse(event.timestamp).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    }.getOrDefault(event.timestamp)
}

private fun eventTime(event: TideEvent): String = runCatching {
    LocalDateTime.parse(event.timestamp).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(event.timestamp.takeLast(5))

@Composable
private fun MarineLineChart(
    epochs: List<Long?>,
    values: List<Double?>,
    nowEpochMs: Long,
    hours: Int,
    modifier: Modifier = Modifier,
    events: List<TideEvent> = emptyList()
) {
    val points = remember(epochs, values, nowEpochMs / 60_000L, hours) {
        buildList<Pair<Long, Double>> {
            val end = nowEpochMs + hours * 3_600_000L
            for (i in 0 until minOf(epochs.size, values.size)) {
                val epoch = epochs[i] ?: continue
                val value = values[i] ?: continue
                if (value.isFinite() && epoch >= nowEpochMs - 3_600_000L && epoch <= end) add(epoch to value)
            }
        }
    }
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.marine_chart_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val highColor = MaterialTheme.colorScheme.tertiary
    val lowColor = MaterialTheme.colorScheme.secondary
    val minX = points.first().first.toDouble()
    val maxX = points.last().first.toDouble()
    val rawMinY = points.minOf { it.second }
    val rawMaxY = points.maxOf { it.second }
    val yPadding = max((rawMaxY - rawMinY) * 0.08, 0.05)
    val minY = rawMinY - yPadding
    val maxY = rawMaxY + yPadding

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))) {
        val left = 10.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        repeat(5) { i ->
            val y = top + (bottom - top) * i / 4f
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        repeat(7) { i ->
            val x = left + (right - left) * i / 6f
            drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
        }
        fun x(epoch: Long): Float = if (maxX == minX) left else (left + (epoch - minX) / (maxX - minX) * (right - left)).toFloat()
        fun y(value: Double): Float = if (maxY == minY) (top + bottom) / 2 else (bottom - (value - minY) / (maxY - minY) * (bottom - top)).toFloat()
        val path = Path()
        points.forEachIndexed { i, (epoch, value) ->
            if (i == 0) path.moveTo(x(epoch), y(value)) else path.lineTo(x(epoch), y(value))
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
        events.forEach { event ->
            if (event.epochMs.toDouble() in minX..maxX && event.value in minY..maxY) {
                drawCircle(
                    color = if (event.type == TideEventType.HIGH) highColor else lowColor,
                    radius = 5.dp.toPx(),
                    center = Offset(x(event.epochMs), y(event.value))
                )
            }
        }
    }
}

private fun format(value: Double?, unit: String, digits: Int = 1): String =
    value?.takeIf { it.isFinite() }?.let { "% .${digits}f".format(Locale.getDefault(), it).trim() + " $unit" } ?: "—"

private fun compass(degrees: Double): String {
    val labels = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val normalized = ((degrees % 360) + 360) % 360
    return labels[((normalized + 22.5) / 45.0).toInt() % 8]
}
