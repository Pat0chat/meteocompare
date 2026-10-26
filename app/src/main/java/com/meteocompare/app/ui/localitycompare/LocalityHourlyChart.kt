package com.meteocompare.app.ui.localitycompare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.util.windCardinal
import com.meteocompare.app.domain.usecase.LocalityHourMetrics
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.max

/** A single local wall-clock hour across cities, not a shared UTC instant. */
internal enum class CompareChartMetric(val labelRes: Int) {
    TEMPERATURE(R.string.compare_hour_temperature),
    APPARENT(R.string.metric_apparent_temperature),
    PRESSURE(R.string.metric_pressure_short),
    HUMIDITY(R.string.metric_humidity),
    DEW_POINT(R.string.metric_dew_point),
    RAIN(R.string.compare_hour_rain),
    RAIN_RISK(R.string.compare_rain_probability),
    CLOUDS(R.string.compare_hour_cloud_cover),
    WIND(R.string.compare_hour_wind),
    SUNSHINE(R.string.compare_sunshine),
    AGREEMENT(R.string.compare_hour_agreement),
    UV(R.string.compare_uv_hour);

    fun rawValue(hour: LocalityHourMetrics?): Double? = when (this) {
        TEMPERATURE -> hour?.temperatureC
        APPARENT -> hour?.apparentTemperatureC
        PRESSURE -> hour?.pressureHpa
        HUMIDITY -> hour?.humidityPercent?.toDouble()
        DEW_POINT -> hour?.dewPointC
        RAIN -> hour?.precipitationMm
        RAIN_RISK -> hour?.rainProbabilityPercent?.toDouble()
        CLOUDS -> hour?.cloudCoverPercent?.toDouble()
        WIND -> hour?.windKmh
        SUNSHINE -> hour?.estimatedSunHours
        AGREEMENT -> hour?.agreementPercent?.toDouble()
        UV -> hour?.uvIndex
    }?.takeIf(Double::isFinite)

    fun displayValue(hour: LocalityHourMetrics?, units: WeatherUnits, locale: Locale): String {
        val value = rawValue(hour) ?: return "—"
        return when (this) {
            TEMPERATURE, APPARENT, DEW_POINT -> units.temp(value, locale = locale)
            PRESSURE -> units.format(value, com.meteocompare.app.core.units.WeatherUnit.PRESSURE, if (units.imperial) 2 else 0, locale)
            RAIN -> units.rain(value, locale)
            WIND -> units.speed(value, locale = locale).let { speed ->
                windCardinal(hour?.windDirectionDeg, locale)?.let { "$speed · $it" } ?: speed
            }
            RAIN_RISK, CLOUDS, HUMIDITY, AGREEMENT -> "${value.roundToInt()} %"
            SUNSHINE -> "${units.number(value, 1, locale)} h"
            UV -> units.number(value, 1, locale)
        }
    }

    /** Convert values before drawing so UK mph / °C and other profiles agree with labels. */
    fun plotValue(hour: LocalityHourMetrics?, units: WeatherUnits): Double? = rawValue(hour)?.let {
        when (this) {
            TEMPERATURE, APPARENT, DEW_POINT -> units.temperature(it)
            PRESSURE -> units.pressure(it)
            RAIN -> units.precipitation(it)
            WIND -> units.wind(it)
            else -> it
        }
    }
}

internal data class ChartRange(val lower: Double, val upper: Double, val hasData: Boolean)

/** Immutable y-axis extent computed only when the plotted metric/data changes. */
internal fun chartRange(points: List<List<Double?>>): ChartRange {
    var lowest = Double.POSITIVE_INFINITY
    var highest = Double.NEGATIVE_INFINITY
    for (city in points) for (value in city) {
        if (value != null && value.isFinite()) {
            if (value < lowest) lowest = value
            if (value > highest) highest = value
        }
    }
    if (!lowest.isFinite()) return ChartRange(0.0, 1.0, false)
    val span = (highest - lowest).takeIf { it > 0.001 } ?: 1.0
    return ChartRange(lowest - span * .12, highest + span * .12, true)
}

/** Display-space axis bounds. Quantities that cannot go below zero retain a zero baseline. */
internal fun metricChartRange(points: List<List<Double?>>, metric: CompareChartMetric): ChartRange {
    val data = chartRange(points)
    if (!data.hasData) return data
    return when (metric) {
        CompareChartMetric.TEMPERATURE, CompareChartMetric.APPARENT, CompareChartMetric.PRESSURE, CompareChartMetric.DEW_POINT -> data
        CompareChartMetric.RAIN_RISK, CompareChartMetric.CLOUDS, CompareChartMetric.HUMIDITY, CompareChartMetric.AGREEMENT ->
            ChartRange(0.0, 100.0, true)
        CompareChartMetric.SUNSHINE -> ChartRange(0.0, 1.0, true)
        else -> {
            val maximum = points.asSequence().flatMap { it.asSequence() }
                .filterNotNull().filter { it.isFinite() }.maxOrNull() ?: 0.0
            ChartRange(0.0, max(1.0, maximum * 1.12), true)
        }
    }
}

internal fun chartAxisUnit(metric: CompareChartMetric, units: WeatherUnits): String = when (metric) {
    CompareChartMetric.TEMPERATURE, CompareChartMetric.APPARENT, CompareChartMetric.DEW_POINT -> units.temperatureUnit
    CompareChartMetric.PRESSURE -> units.label(com.meteocompare.app.core.units.WeatherUnit.PRESSURE)
    CompareChartMetric.RAIN -> units.precipitationUnit
    CompareChartMetric.WIND -> units.windUnit
    CompareChartMetric.RAIN_RISK, CompareChartMetric.CLOUDS, CompareChartMetric.HUMIDITY, CompareChartMetric.AGREEMENT -> "%"
    CompareChartMetric.SUNSHINE -> "h"
    CompareChartMetric.UV -> "UV"
}

/** Values are already converted to the selected display units by chartPoints(). */
internal fun chartAxisLabel(value: Double, step: Double, metric: CompareChartMetric,
                            units: WeatherUnits, locale: Locale): String {
    val digits = when (metric) {
        CompareChartMetric.RAIN -> if (units.imperial) 2 else if (step < 1) 1 else 0
        CompareChartMetric.SUNSHINE, CompareChartMetric.UV -> if (step < 1) 1 else 0
        CompareChartMetric.TEMPERATURE, CompareChartMetric.APPARENT, CompareChartMetric.DEW_POINT, CompareChartMetric.WIND -> if (step < 1) 1 else 0
        CompareChartMetric.PRESSURE -> if (units.imperial) 2 else if (step < 1) 1 else 0
        else -> 0
    }
    return units.number(value, digits, locale)
}

internal data class HourChartCity(
    val id: String,
    val name: String,
    /** Exactly 24 slots; null is a genuine gap (including DST skipped hours). */
    val hours: List<LocalityHourMetrics?>
)

/** Pure chart preparation, independent of Compose and network I/O. */
internal fun chartPoints(
    cities: List<HourChartCity>, metric: CompareChartMetric, units: WeatherUnits
): List<List<Double?>> = cities.map { city ->
    (0..23).map { hour -> metric.plotValue(city.hours.getOrNull(hour), units) }
}

@Composable
internal fun LocalityHourlyChart(
    cities: List<HourChartCity>,
    units: WeatherUnits,
    locale: Locale,
    hourFormatter: DateTimeFormatter,
    selectedHour: Int,
    onHourSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    metric: CompareChartMetric = CompareChartMetric.TEMPERATURE,
    onMetricSelected: (CompareChartMetric) -> Unit = {}
) {
    // Extra metrics can extend beyond the viewport. Always reveal the metric
    // selected from the daily overview, not just those composed by LazyRow.
    val metricListState = rememberLazyListState()
    LaunchedEffect(metric) {
        metricListState.scrollToItem(metric.ordinal)
    }
    val palette = MaterialTheme.colorScheme
    val dark = palette.surface.luminance() < .5f
    // The metric owns the accent; the city keeps a distinct variation within that family.
    val accent = when (metric) {
        CompareChartMetric.TEMPERATURE, CompareChartMetric.APPARENT -> temperatureMetricAccent()
        CompareChartMetric.PRESSURE, CompareChartMetric.HUMIDITY -> if (dark) Color(0xFF9DD6D4) else Color(0xFF27817F)
        CompareChartMetric.DEW_POINT -> temperatureMetricAccent()
        CompareChartMetric.RAIN, CompareChartMetric.RAIN_RISK -> precipitationMetricAccent()
        CompareChartMetric.CLOUDS -> if (dark) Color(0xFFB2BFF5) else Color(0xFF596BAD)
        CompareChartMetric.WIND -> windMetricAccent()
        CompareChartMetric.SUNSHINE -> if (dark) Color(0xFFFFD36A) else Color(0xFFAC700D)
        CompareChartMetric.AGREEMENT -> if (dark) Color(0xFF69D9AE) else Color(0xFF13845C)
        CompareChartMetric.UV -> if (dark) Color(0xFFCBA7FF) else Color(0xFF8744BB)
    }
    val lineColors = listOf(
        accent,
        lerp(accent, palette.onSurface, if (dark) .45f else .38f),
        lerp(accent, palette.tertiary, .65f)
    )
    val points = remember(cities, units, metric) { chartPoints(cities, metric, units) }
    val range = remember(points, metric) { metricChartRange(points, metric) }
    val axisUnit = chartAxisUnit(metric, units)
    val textMeasurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = palette.onSurfaceVariant, fontSize = 10.sp)
    val axisValues = remember(range) { (0..4).map { index -> range.upper - (range.upper - range.lower) * index / 4 } }
    val yTicks = remember(axisValues, metric, units, locale, textMeasurer, tickStyle) {
        axisValues.map { value ->
            textMeasurer.measure(
                chartAxisLabel(value, (range.upper - range.lower) / 4, metric, units, locale), tickStyle
            )
        }
    }
    val xTicks = remember(hourFormatter, textMeasurer, tickStyle) {
        listOf(0, 6, 12, 18, 23).map { hour ->
            hour to textMeasurer.measure(LocalTime.of(hour, 0).format(hourFormatter), tickStyle)
        }
    }
    val yAxisTitle = stringResource(metric.labelRes)
    val axisTitleStyle = MaterialTheme.typography.labelMedium
    val gridColor = palette.outlineVariant
    val chartBackground = if (dark) palette.surfaceContainer else palette.surface

    Surface(modifier = modifier.fillMaxWidth().testTag("compare_hourly_graph"),
        color = palette.surfaceContainerLow, shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(
                state = metricListState,
                modifier = Modifier.testTag("compare_chart_metric_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(CompareChartMetric.entries) { option ->
                    FilterChip(
                        selected = metric == option,
                        onClick = { onMetricSelected(option) },
                        label = { Text(stringResource(option.labelRes)) },
                        modifier = Modifier.testTag("compare_chart_metric_${option.name.lowercase()}"),
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = if (dark) .30f else .14f),
                            selectedLabelColor = palette.onSurface
                        ),
                        border = if (metric == option) BorderStroke(1.4.dp, accent) else null
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(width = 4.dp, height = 23.dp)
                        .background(accent, RoundedCornerShape(4.dp)))
                    Text("$yAxisTitle · $axisUnit", style = axisTitleStyle,
                        color = palette.onSurface, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).testTag("compare_chart_axis_unit"))
                }
                Text(LocalTime.of(selectedHour.coerceIn(0, 23), 0).format(hourFormatter),
                    color = accent, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("compare_selected_hour"))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cities.forEachIndexed { index, city ->
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(width = 15.dp, height = 4.dp)
                            .background(lineColors[index % lineColors.size], RoundedCornerShape(2.dp)))
                        Text(city.name, style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                    }
                }
            }
            if (!range.hasData) {
                Text(stringResource(R.string.compare_no_data_for_hour),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = palette.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            val baseModifier = Modifier.fillMaxWidth().height(248.dp)
                .testTag("compare_hour_chart")
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(selectedHour.toFloat(), 0f..23f, 22)
                    setProgress { newValue ->
                        onHourSelected(newValue.roundToInt().coerceIn(0, 23))
                        true
                    }
                }
                .pointerInput(cities, metric) {
                    detectTapGestures { offset ->
                        val startX = 60.dp.toPx()
                        val rightX = size.width - 20.dp.toPx()
                        if (rightX > startX) onHourSelected(chartHourFromX(offset.x, startX, rightX))
                    }
                }
                .pointerInput(cities, metric) {
                    detectHorizontalDragGestures { change, _ ->
                        val startX = 60.dp.toPx()
                        val rightX = size.width - 20.dp.toPx()
                        if (rightX > startX) onHourSelected(chartHourFromX(change.position.x, startX, rightX))
                        change.consume()
                    }
                }
            Canvas(baseModifier) {
                val left = 60.dp.toPx()
                val right = size.width - 20.dp.toPx()
                val top = 12.dp.toPx()
                val bottom = size.height - 35.dp.toPx()
                if (right <= left || bottom <= top) return@Canvas
                val width = right - left
                val height = bottom - top
                fun x(hour: Int) = left + width * hour / 23f
                fun y(value: Double): Float = bottom - ((value - range.lower) /
                    (range.upper - range.lower)).toFloat() * height

                drawRoundRect(chartBackground, topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(width, height),
                    cornerRadius = CornerRadius(10.dp.toPx()))
                // One subtle selected-hour band, drawn beneath the measurements.
                drawRoundRect(accent.copy(alpha = if (dark) .14f else .09f),
                    topLeft = Offset(x(selectedHour) - 7.dp.toPx(), top),
                    size = androidx.compose.ui.geometry.Size(14.dp.toPx(), height),
                    cornerRadius = CornerRadius(7.dp.toPx()))
                for (index in 0..4) {
                    val gy = top + height * index / 4
                    drawLine(gridColor, Offset(left, gy), Offset(right, gy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())))
                    val measured = yTicks[index]
                    drawText(measured, topLeft = Offset(left - measured.size.width - 9.dp.toPx(),
                        gy - measured.size.height / 2f))
                }
                // Tick marks and labels share exactly the same plot coordinates.
                xTicks.forEach { (hour, measured) ->
                    val gx = x(hour)
                    drawLine(gridColor.copy(alpha = .7f), Offset(gx, top), Offset(gx, bottom),
                        strokeWidth = .8.dp.toPx())
                    drawText(measured, topLeft = Offset(
                        (gx - measured.size.width / 2f).coerceIn(1.dp.toPx(),
                            size.width - measured.size.width - 1.dp.toPx()),
                        bottom + 7.dp.toPx()
                    ))
                }
                // Separate filled polygons for contiguous data: gaps are NEVER interpolated.
                points.forEachIndexed { index, values ->
                    val tone = lineColors[index % lineColors.size]
                    var segment = Path()
                    var fill = Path()
                    var first: Float? = null
                    var last = 0f
                    var started = false
                    fun flush() {
                        if (started) {
                            if (first != null && last > first!!) {
                                fill.lineTo(last, bottom)
                                fill.lineTo(first!!, bottom)
                                fill.close()
                                drawPath(fill, brush = Brush.verticalGradient(
                                    colors = listOf(tone.copy(alpha = if (dark) .23f else .16f),
                                        tone.copy(alpha = .005f)),
                                    startY = top, endY = bottom
                                ))
                            }
                            drawPath(segment, tone, style = Stroke(
                                width = 2.7.dp.toPx(), cap = StrokeCap.Round,
                                pathEffect = if (index == 2) PathEffect.dashPathEffect(
                                    floatArrayOf(9.dp.toPx(), 4.dp.toPx())) else null
                            ))
                        }
                        segment = Path()
                        fill = Path()
                        first = null
                        started = false
                    }
                    for (hour in 0..23) {
                        val value = values.getOrNull(hour)
                        if (value == null || !value.isFinite()) { flush(); continue }
                        val px = x(hour)
                        val py = y(value)
                        if (!started) {
                            segment.moveTo(px, py)
                            fill.moveTo(px, py)
                            first = px
                            started = true
                        } else {
                            segment.lineTo(px, py)
                            fill.lineTo(px, py)
                        }
                        last = px
                    }
                    flush()
                    // Points remain visible even if the model has only isolated hours.
                    if (values.count { it != null } <= 2) {
                        values.forEachIndexed { hour, value ->
                            if (value != null && value.isFinite())
                                drawCircle(tone, 3.dp.toPx(), Offset(x(hour), y(value)))
                        }
                    }
                }
                val cursorX = x(selectedHour)
                drawLine(accent.copy(alpha = .95f), Offset(cursorX, top), Offset(cursorX, bottom),
                    strokeWidth = 1.7.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())))
                points.forEachIndexed { index, values ->
                    values.getOrNull(selectedHour)?.takeIf { it.isFinite() }?.let { value ->
                        val location = Offset(cursorX, y(value))
                        drawCircle(chartBackground, radius = 6.3.dp.toPx(), center = location)
                        drawCircle(lineColors[index % lineColors.size], radius = 4.7.dp.toPx(), center = location)
                        drawCircle(palette.surface, radius = 1.6.dp.toPx(), center = location)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.compare_chart_x_axis),
                    style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
                Text(stringResource(R.string.compare_chart_touch_hint),
                    style = MaterialTheme.typography.labelSmall, color = palette.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cities.forEachIndexed { index, city ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(city.name, style = MaterialTheme.typography.labelSmall,
                            color = lineColors[index % lineColors.size], maxLines = 1)
                        Text(metric.displayValue(city.hours.getOrNull(selectedHour), units, locale),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = palette.onSurface,
                            modifier = Modifier.testTag("compare_chart_value_${city.id}"))
                    }
                }
            }
        }
    }
}

