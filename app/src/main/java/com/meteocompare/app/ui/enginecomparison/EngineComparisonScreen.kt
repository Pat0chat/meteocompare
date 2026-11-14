package com.meteocompare.app.ui.enginecomparison

import com.meteocompare.app.core.charts.metricPlotValue
import com.meteocompare.app.core.charts.canonicalChartRange
import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.LocalWeatherUnits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.ui.components.ConditionStatusIcon
import com.meteocompare.app.ui.components.ConditionStatusLegend
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.usecase.EngineComparisonDay
import com.meteocompare.app.domain.usecase.EngineComparisonMetric
import com.meteocompare.app.domain.usecase.EngineComparisonValues
import com.meteocompare.app.domain.usecase.EngineDivergenceLevel
import com.meteocompare.app.ui.components.AppToastEffect
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineComparisonScreen(
    onBack: () -> Unit,
    viewModel: EngineComparisonViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppToastEffect(viewModel.feedback)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIfStale()
    }
    WeatherAccentTheme(condition = state.weatherAccentCondition()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.engine_comparison_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            when (val current = state) {
                EngineComparisonUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                is EngineComparisonUiState.Error -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(current.message)
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }

                is EngineComparisonUiState.Loaded -> EngineComparisonContent(
                    state = current,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

/** La premiere echeance est aujourd'hui dans le fuseau de la ville. */
private fun EngineComparisonUiState.weatherAccentCondition(): WeatherCondition? {
    val loaded = this as? EngineComparisonUiState.Loaded ?: return null
    val firstDay = loaded.days.firstOrNull() ?: return null
    return firstDay.byEngine[loaded.selectedEngine]?.condition
        ?: firstDay.byEngine.values.asSequence().mapNotNull { it.condition }.firstOrNull()
}

@Composable
internal fun EngineComparisonContent(
    state: EngineComparisonUiState.Loaded,
    modifier: Modifier = Modifier
) {
    var metric by remember { mutableStateOf(EngineComparisonMetric.TEMP_MAX) }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(TAG_ENGINE_COMPARISON_LIST),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            EngineComparisonHeader(
                cityName = state.cityName,
                selectedEngine = state.selectedEngine
            )
        }

        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OpenMeteoAttribution(home = false)
            }
        }

        item {
            EngineSectionCard {
                Text(
                    text = stringResource(R.string.engine_comparison_metric),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                // A single, horizontally scrolling row: variables never wrap onto
                // a second line on small devices or with large system font sizes.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag(TAG_ENGINE_METRIC_STRIP),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EngineComparisonMetric.entries.forEach { candidate ->
                        FilterChip(
                            selected = metric == candidate,
                            onClick = { metric = candidate },
                            modifier = Modifier.testTag("$TAG_ENGINE_METRIC_PREFIX${candidate.name}"),
                            label = { Text(metricLabel(candidate), maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    imageVector = engineMetricIcon(candidate),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                EngineLineChart(
                    days = state.days,
                    metric = metric,
                    selectedEngine = state.selectedEngine
                )
                Spacer(Modifier.height(12.dp))
                EngineLegend(state.selectedEngine)
            }
        }

        item {
            EngineSectionCard {
                Text(
                    text = stringResource(R.string.engine_comparison_divergence_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.engine_comparison_divergence_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                DivergenceTimeline(state.days)
            }
        }

        item {
            EngineSectionCard {
                Text(
                    text = stringResource(R.string.engine_comparison_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                EngineDailyComparisonTable(
                    days = state.days,
                    selectedEngine = state.selectedEngine
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.engine_comparison_science_note),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EngineComparisonHeader(
    cityName: String,
    selectedEngine: ForecastEngine
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = cityName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.engine_comparison_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        ForecastEngine.entries.forEachIndexed { index, engine ->
            val color = engineColor(engine)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            append(engineLabel(engine))
                            if (engine == selectedEngine) append("  ✓")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (engine == selectedEngine) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (engine == selectedEngine) color else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = engineDescription(engine),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (index != ForecastEngine.entries.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 21.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun EngineSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            content = content
        )
    }
}

// Stable UI identifiers for accessibility and UI regression tests.
internal const val TAG_ENGINE_COMPARISON_LIST = "engine_comparison_list"
internal const val TAG_ENGINE_METRIC_STRIP = "engine_comparison_metric_strip"
internal const val TAG_ENGINE_METRIC_PREFIX = "engine_comparison_metric_"
internal const val TAG_ENGINE_CHART_SCROLL = "engine_comparison_chart_scroll"
internal const val TAG_ENGINE_CHART_DAY_PREFIX = "engine_comparison_chart_day_"
internal const val TAG_ENGINE_CHART_INSPECTOR = "engine_comparison_chart_inspector"
internal const val TAG_ENGINE_CHART_SELECTED_DAY = "engine_comparison_chart_selected_day"

@Composable
private fun EngineLineChart(
    days: List<EngineComparisonDay>,
    metric: EngineComparisonMetric,
    selectedEngine: ForecastEngine,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    if (days.size < 2 || days.sumOf { day ->
            day.byEngine.values.count { metricPlotValue(it.value(metric)) != null }
        } < 2
    ) {
        Text(
            text = stringResource(R.string.engine_comparison_not_enough_data),
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    val locale = LocalLocale.current.platformLocale
    val values = days.flatMap { day ->
        day.byEngine.values.mapNotNull { metricPlotValue(it.value(metric)) }
    }
    // One Y axis for the whole timeline, to keep the comparison meaningful
    // while scrolling (the scale never changes behind the user's back).
    val bounds = canonicalChartRange(values, minimumSpan = 1.0, paddingFraction = 0.12)
    val minValue = bounds.min
    val range = bounds.span
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val accentColors = mapOf(
        ForecastEngine.MULTI_CONSENSUS to engineColor(ForecastEngine.MULTI_CONSENSUS),
        ForecastEngine.CALIBRATION to engineColor(ForecastEngine.CALIBRATION),
        ForecastEngine.SCENARIOS to engineColor(ForecastEngine.SCENARIOS),
        ForecastEngine.ADAPTIVE to engineColor(ForecastEngine.ADAPTIVE)
    )
    val backgrounds = days.map { day ->
        divergenceLevelColor(day.divergence.level).copy(alpha = 0.075f)
    }
    var selectedDayIndex by remember(days.first().date, days.size) { mutableStateOf(0) }
    val selectedIndex = selectedDayIndex.coerceIn(0, days.lastIndex)
    val scrollState = rememberScrollState()
    val chartHeight = 244.dp
    val plotTop = 16.dp
    val plotBottom = 18.dp

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = metricLabel(metric),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = units.label(metricUnit(metric)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.engine_comparison_chart_scroll_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // The vertical axis stays visible while the dates and curves scroll.
            Column(
                modifier = Modifier
                    .width(53.dp)
                    .height(chartHeight)
                    .padding(top = plotTop, bottom = plotBottom, end = 7.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                (3 downTo 0).forEach { tick ->
                    Text(
                        text = chartAxisLabel(
                            minValue + range * tick / 3.0,
                            range / 3.0,
                            metric,
                            locale,
                            units
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val dayWidth = engineChartDayWidth(maxWidth.value).dp
                val contentWidth = dayWidth * days.size
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .testTag(TAG_ENGINE_CHART_SCROLL)
                ) {
                    Canvas(
                        modifier = Modifier
                            .width(contentWidth)
                            .height(chartHeight)
                            .pointerInput(days, dayWidth) {
                                detectTapGestures { tap ->
                                    engineChartDayIndexAt(
                                        tap.x,
                                        dayWidth.toPx(),
                                        days.size
                                    )?.let { selectedDayIndex = it }
                                }
                            }
                    ) {
                        val top = plotTop.toPx()
                        val bottom = size.height - plotBottom.toPx()
                        val slot = dayWidth.toPx()

                        days.forEachIndexed { index, day ->
                            val startX = slot * index
                            drawRect(
                                color = backgrounds[index],
                                topLeft = Offset(startX, top),
                                size = Size(slot, bottom - top)
                            )
                            if (index == selectedIndex) {
                                drawRect(
                                    color = primaryColor.copy(alpha = 0.07f),
                                    topLeft = Offset(startX, top),
                                    size = Size(slot, bottom - top)
                                )
                            }
                            drawLine(
                                color = gridColor.copy(alpha = 0.42f),
                                start = Offset(startX + slot / 2f, top),
                                end = Offset(startX + slot / 2f, bottom),
                                strokeWidth = 0.7.dp.toPx()
                            )
                        }
                        (0..3).forEach { index ->
                            val y = top + (bottom - top) * index / 3f
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = if (index in 1..2) {
                                    PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
                                } else null
                            )
                        }
                        val selectedX = slot * (selectedIndex + 0.5f)
                        drawLine(
                            color = primaryColor.copy(alpha = 0.65f),
                            start = Offset(selectedX, top),
                            end = Offset(selectedX, bottom),
                            strokeWidth = 1.3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))
                        )

                        // The active engine is always painted last, over other
                        // curves. Never connect through a missing data point.
                        val drawOrder = ForecastEngine.entries.filter { it != selectedEngine } + selectedEngine
                        drawOrder.forEach { engine ->
                            val color = accentColors.getValue(engine)
                            val isActive = engine == selectedEngine
                            val segments = mutableListOf<List<Offset>>()
                            var segment = mutableListOf<Offset>()
                            days.forEachIndexed { index, day ->
                                val value = metricPlotValue(day.byEngine[engine]?.value(metric))
                                if (value == null) {
                                    if (segment.isNotEmpty()) segments.add(segment)
                                    segment = mutableListOf()
                                } else {
                                    segment.add(
                                        Offset(
                                            x = slot * (index + 0.5f),
                                            y = bottom - ((value - minValue) / range).toFloat() * (bottom - top)
                                        )
                                    )
                                }
                            }
                            if (segment.isNotEmpty()) segments.add(segment)

                            segments.forEach { points ->
                                if (points.size > 1) {
                                    val line = Path().apply {
                                        points.forEachIndexed { index, point ->
                                            if (index == 0) moveTo(point.x, point.y)
                                            else lineTo(point.x, point.y)
                                        }
                                    }
                                    if (isActive) {
                                        val area = Path().apply {
                                            moveTo(points.first().x, bottom)
                                            lineTo(points.first().x, points.first().y)
                                            points.drop(1).forEach { lineTo(it.x, it.y) }
                                            lineTo(points.last().x, bottom)
                                            close()
                                        }
                                        drawPath(
                                            path = area,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0.015f)),
                                                startY = top,
                                                endY = bottom
                                            )
                                        )
                                        // Contrast halo helps distinguish overlapping curves.
                                        drawPath(line, color = surfaceColor.copy(alpha = 0.86f),
                                            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                                    }
                                    drawPath(
                                        line,
                                        color = color.copy(alpha = if (isActive) 1f else 0.82f),
                                        style = Stroke(width = if (isActive) 3.dp.toPx() else 2.dp.toPx(),
                                            cap = StrokeCap.Round)
                                    )
                                }
                                points.forEach { point ->
                                    drawCircle(surfaceColor, radius = if (isActive) 5.dp.toPx() else 3.6.dp.toPx(), center = point)
                                    drawCircle(color, radius = if (isActive) 3.4.dp.toPx() else 2.2.dp.toPx(), center = point)
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.width(contentWidth)) {
                        days.forEachIndexed { index, day ->
                            Column(
                                modifier = Modifier
                                    .width(dayWidth)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedDayIndex = index }
                                    .testTag("$TAG_ENGINE_CHART_DAY_PREFIX$index")
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = day.date.format(dayFormatter),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index == selectedIndex) primaryColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                                if (index == selectedIndex) {
                                    Box(
                                        Modifier.padding(top = 2.dp).size(4.dp)
                                            .clip(CircleShape).background(primaryColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        EngineChartInspector(days[selectedIndex], metric, selectedEngine, units)
    }
}

@Composable
private fun EngineChartInspector(
    day: EngineComparisonDay,
    metric: EngineComparisonMetric,
    selectedEngine: ForecastEngine,
    units: WeatherUnits
) {
    val locale = LocalLocale.current.platformLocale
    val values = ForecastEngine.entries.mapNotNull { engine ->
        metricPlotValue(day.byEngine[engine]?.value(metric))
    }
    val spread = if (values.size >= 2) {
        val minimum = values.minOrNull()
        val maximum = values.maxOrNull()
        if (minimum != null && maximum != null) maximum - minimum else null
    } else null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.46f))
            .padding(10.dp)
            .testTag(TAG_ENGINE_CHART_INSPECTOR)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDate(day.date, locale),
                modifier = Modifier.testTag(TAG_ENGINE_CHART_SELECTED_DAY),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            if (spread != null) {
                Text(
                    text = stringResource(
                        R.string.engine_comparison_chart_spread,
                        units.format(spread, metricUnit(metric), 1, locale, delta = true)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ForecastEngine.entries.chunked(2).forEach { engines ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                engines.forEach { engine ->
                    val color = engineColor(engine)
                    Row(
                        modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                        Column {
                            Text(
                                text = engineLabel(engine),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (engine == selectedEngine) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2
                            )
                            Text(
                                text = units.format(
                                    metricPlotValue(day.byEngine[engine]?.value(metric)),
                                    metricUnit(metric), 1, locale
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineLegend(selectedEngine: ForecastEngine) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ForecastEngine.entries.forEach { engine ->
            val color = engineColor(engine)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Canvas(Modifier.width(24.dp).height(10.dp)) {
                    val y = size.height / 2f
                    drawLine(
                        color = color,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = if (engine == selectedEngine) 3.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = color,
                        radius = 3.dp.toPx(),
                        center = Offset(size.width / 2f, y)
                    )
                }
                Text(
                    text = engineLabel(engine),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (engine == selectedEngine) FontWeight.Bold else FontWeight.Medium,
                    color = if (engine == selectedEngine) color else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DivergenceTimeline(days: List<EngineComparisonDay>) {
    val locale = LocalLocale.current.platformLocale
    val dayFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d MMM", locale)
    }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        days.forEach { day ->
            val globalColor = divergenceLevelColor(day.divergence.level)
            val globalLabel = divergenceLevelLabel(day.divergence.level)

            Column(
                modifier = Modifier
                    .width(252.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f))
                    .padding(horizontal = 11.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = day.date.format(dayFormatter).replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(globalColor)
                        )
                        Text(
                            text = globalLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = globalColor
                        )
                    }
                }

                Spacer(Modifier.height(9.dp))
                DivergenceMetricCompactRow(
                    label = stringResource(R.string.engine_divergence_variable_temperature),
                    delta = day.divergence.temperatureDelta,
                    unit = WeatherUnit.TEMPERATURE,
                    scaleMax = 4.0,
                    decimals = 1
                )
                Spacer(Modifier.height(7.dp))
                DivergenceMetricCompactRow(
                    label = stringResource(R.string.engine_divergence_variable_rain),
                    delta = day.divergence.precipitationDelta,
                    unit = WeatherUnit.PRECIPITATION,
                    scaleMax = 8.0,
                    decimals = 1
                )
                Spacer(Modifier.height(7.dp))
                DivergenceMetricCompactRow(
                    label = stringResource(R.string.engine_divergence_variable_wind),
                    delta = day.divergence.windDelta,
                    unit = WeatherUnit.WIND_SPEED,
                    scaleMax = 15.0,
                    decimals = 0
                )
                Spacer(Modifier.height(7.dp))
                DivergenceMetricCompactRow(
                    label = stringResource(R.string.engine_divergence_variable_cloud),
                    delta = day.divergence.cloudDelta,
                    unit = WeatherUnit.PERCENT,
                    scaleMax = 50.0,
                    decimals = 0
                )
            }
        }
    }
}

@Composable
private fun DivergenceMetricCompactRow(
    label: String,
    delta: Double,
    unit: WeatherUnit,
    scaleMax: Double,
    decimals: Int,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val locale = LocalLocale.current.platformLocale
    val lowColor = divergenceLevelColor(EngineDivergenceLevel.LOW)
    val mediumColor = divergenceLevelColor(EngineDivergenceLevel.MEDIUM)
    val highColor = divergenceLevelColor(EngineDivergenceLevel.HIGH)
    val normalized = divergenceMetricProgress(delta, scaleMax)
    val level = divergenceMetricLevel(delta, scaleMax)
    val valueColor = when (level) {
        EngineDivergenceLevel.LOW -> lowColor
        EngineDivergenceLevel.MEDIUM -> mediumColor
        EngineDivergenceLevel.HIGH -> highColor
    }
    val formattedDelta = "Δ " + units.format(delta, unit, decimals, locale, delta = true)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        DivergenceScale(
            progress = normalized,
            lowColor = lowColor,
            mediumColor = mediumColor,
            highColor = highColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formattedDelta,
            modifier = Modifier.width(66.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun DivergenceScale(
    progress: Float,
    lowColor: Color,
    mediumColor: Color,
    highColor: Color,
    modifier: Modifier = Modifier
) {
    val markerColor = MaterialTheme.colorScheme.onSurface
    val markerOutline = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier.height(10.dp)
    ) {
        val barHeight = 5.dp.toPx()
        val radius = barHeight / 2f
        val y = size.height / 2f
        val top = y - barHeight / 2f
        val lowEnd = size.width * 0.35f
        val mediumEnd = size.width * 0.75f

        drawRoundRect(
            color = lowColor.copy(alpha = 0.9f),
            topLeft = Offset(0f, top),
            size = Size(lowEnd, barHeight),
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRect(
            color = mediumColor.copy(alpha = 0.9f),
            topLeft = Offset(lowEnd - radius, top),
            size = Size((mediumEnd - lowEnd) + radius * 2f, barHeight)
        )
        drawRoundRect(
            color = highColor.copy(alpha = 0.9f),
            topLeft = Offset(mediumEnd - radius, top),
            size = Size(size.width - mediumEnd + radius, barHeight),
            cornerRadius = CornerRadius(radius, radius)
        )

        val markerX = (size.width * progress.coerceIn(0f, 1f))
            .coerceIn(4.dp.toPx(), size.width - 4.dp.toPx())
        drawCircle(
            color = markerOutline,
            radius = 4.5.dp.toPx(),
            center = Offset(markerX, y)
        )
        drawCircle(
            color = markerColor,
            radius = 2.7.dp.toPx(),
            center = Offset(markerX, y)
        )
    }
}

@Composable
private fun EngineDailyComparisonTable(
    days: List<EngineComparisonDay>,
    selectedEngine: ForecastEngine,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val locale = LocalLocale.current.platformLocale
    val scrollState = rememberScrollState()
    val tableWidth = 620.dp

    Column(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).width(tableWidth)
    ) {
        ConditionStatusLegend()
        EngineTableHeader(selectedEngine)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        days.forEachIndexed { dayIndex, day ->
            Text(
                text = formatDate(day.date, locale),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )


            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_temp_min),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.tempMin, locale, WeatherUnit.TEMPERATURE_COMPACT, units = units) }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_temp_max),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.tempMax, locale, WeatherUnit.TEMPERATURE_COMPACT, units = units) }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_precipitation),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.precipitationAmountMm, locale, WeatherUnit.PRECIPITATION, units = units) }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_table_probability),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> value?.precipitationProbabilityPercent?.let { "$it%" } ?: "—" }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_table_expected_rain),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.precipitationExpectedMm, locale, WeatherUnit.PRECIPITATION, units = units) }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_wind),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatInteger(value?.windKmh, locale, WeatherUnit.WIND_SPEED, units = units) }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_gust),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatInteger(value?.gustKmh, locale, WeatherUnit.WIND_SPEED, units = units) }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_cloud),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatInteger(value?.cloudPercent, locale, WeatherUnit.PERCENT, units = units) }
            )
            EngineConditionTableRow(day, selectedEngine)

            if (dayIndex != days.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

/** Condition and its provenance are inspectable directly in engine comparison. */
@Composable
private fun EngineConditionTableRow(day: EngineComparisonDay, selectedEngine: ForecastEngine) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(
            text = stringResource(R.string.engine_table_condition),
            width = 120.dp,
            emphasized = true
        )
        ForecastEngine.entries.forEach { engine ->
            val value = day.byEngine[engine]
            val condition = value?.condition
            val color = if (engine == selectedEngine) engineColor(engine) else null
            Row(
                modifier = Modifier.width(125.dp)
                    .background(color?.copy(alpha = 0.08f) ?: Color.Transparent)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConditionStatusIcon(
                    condition = condition,
                    decision = value?.conditionDecision,
                    size = 27.dp
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = condition?.let { stringResource(weatherConditionLabelRes(it)) } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = color ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun EngineTableHeader(selectedEngine: ForecastEngine) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(
            text = stringResource(R.string.engine_table_metric),
            width = 120.dp,
            emphasized = true
        )
        ForecastEngine.entries.forEach { engine ->
            val color = engineColor(engine)
            TableCell(
                text = engineLabel(engine),
                width = 125.dp,
                emphasized = engine == selectedEngine,
                accent = if (engine == selectedEngine) color else null
            )
        }
    }
}

@Composable
private fun ComparisonTableRow(
    label: String,
    selectedEngine: ForecastEngine,
    values: Map<ForecastEngine, String>
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TableCell(
            text = label,
            width = 120.dp,
            emphasized = true
        )
        ForecastEngine.entries.forEach { engine ->
            val color = engineColor(engine)
            TableCell(
                text = values[engine] ?: "—",
                width = 125.dp,
                emphasized = engine == selectedEngine,
                accent = if (engine == selectedEngine) color else null
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    emphasized: Boolean,
    accent: Color? = null
) {
    val background = when {
        accent != null -> accent.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .width(width)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun EngineComparisonDay.valuesForEngines(
    formatter: (EngineComparisonValues?) -> String
): Map<ForecastEngine, String> = ForecastEngine.entries.associateWith { engine ->
    formatter(byEngine[engine])
}

internal fun divergenceMetricProgress(delta: Double, scaleMax: Double): Float =
    if (delta.isFinite() && scaleMax > 0.0) {
        (delta / scaleMax).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }

internal fun divergenceMetricLevel(delta: Double, scaleMax: Double): EngineDivergenceLevel {
    val normalized = divergenceMetricProgress(delta, scaleMax)
    return when {
        normalized >= 0.75f -> EngineDivergenceLevel.HIGH
        normalized >= 0.35f -> EngineDivergenceLevel.MEDIUM
        else -> EngineDivergenceLevel.LOW
    }
}

@Composable
private fun engineColor(engine: ForecastEngine): Color {
    val dark = isSystemInDarkTheme()
    return when (engine) {
        ForecastEngine.MULTI_CONSENSUS -> if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)
        ForecastEngine.CALIBRATION -> if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
        ForecastEngine.SCENARIOS -> if (dark) Color(0xFFCE93D8) else Color(0xFF7B1FA2)
        ForecastEngine.ADAPTIVE -> if (dark) Color(0xFF4DB6AC) else Color(0xFF00897B)
    }
}

@Composable
private fun divergenceLevelColor(level: EngineDivergenceLevel): Color {
    val dark = isSystemInDarkTheme()
    return when (level) {
        EngineDivergenceLevel.LOW -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        EngineDivergenceLevel.MEDIUM -> if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)
        EngineDivergenceLevel.HIGH -> if (dark) Color(0xFFEF9A9A) else Color(0xFFC62828)
    }
}

@Composable
private fun divergenceLevelLabel(level: EngineDivergenceLevel): String = when (level) {
    EngineDivergenceLevel.LOW -> stringResource(R.string.engine_divergence_low)
    EngineDivergenceLevel.MEDIUM -> stringResource(R.string.engine_divergence_medium)
    EngineDivergenceLevel.HIGH -> stringResource(R.string.engine_divergence_high)
}

@Composable
private fun engineLabel(engine: ForecastEngine): String = stringResource(
    when (engine) {
        ForecastEngine.MULTI_CONSENSUS -> R.string.forecast_engine_multi_consensus
        ForecastEngine.CALIBRATION -> R.string.forecast_engine_calibration
        ForecastEngine.SCENARIOS -> R.string.forecast_engine_scenarios
        ForecastEngine.ADAPTIVE -> R.string.forecast_engine_adaptive
    }
)

@Composable
private fun engineDescription(engine: ForecastEngine): String = stringResource(
    when (engine) {
        ForecastEngine.MULTI_CONSENSUS -> R.string.forecast_engine_multi_consensus_desc
        ForecastEngine.CALIBRATION -> R.string.forecast_engine_calibration_desc
        ForecastEngine.SCENARIOS -> R.string.forecast_engine_scenarios_desc
        ForecastEngine.ADAPTIVE -> R.string.forecast_engine_adaptive_desc
    }
)

@Composable
private fun metricLabel(metric: EngineComparisonMetric): String = stringResource(
    when (metric) {
        EngineComparisonMetric.TEMP_MAX -> R.string.engine_metric_temp_max
        EngineComparisonMetric.TEMP_MIN -> R.string.engine_metric_temp_min
        EngineComparisonMetric.PRECIPITATION -> R.string.engine_metric_precipitation
        EngineComparisonMetric.WIND -> R.string.engine_metric_wind
        EngineComparisonMetric.GUST -> R.string.engine_metric_gust
        EngineComparisonMetric.CLOUD -> R.string.engine_metric_cloud
    }
)

private fun engineMetricIcon(metric: EngineComparisonMetric): ImageVector = when (metric) {
    EngineComparisonMetric.TEMP_MAX -> Icons.Filled.Thermostat
    EngineComparisonMetric.TEMP_MIN -> Icons.Filled.AcUnit
    EngineComparisonMetric.PRECIPITATION -> Icons.Filled.WaterDrop
    EngineComparisonMetric.WIND -> Icons.Filled.Air
    EngineComparisonMetric.GUST -> Icons.Filled.Cyclone
    EngineComparisonMetric.CLOUD -> Icons.Filled.Cloud
}

private fun metricUnit(metric: EngineComparisonMetric): WeatherUnit = when (metric) {
    EngineComparisonMetric.TEMP_MAX,
    EngineComparisonMetric.TEMP_MIN -> WeatherUnit.TEMPERATURE
    EngineComparisonMetric.PRECIPITATION -> WeatherUnit.PRECIPITATION
    EngineComparisonMetric.WIND,
    EngineComparisonMetric.GUST -> WeatherUnit.WIND_SPEED
    EngineComparisonMetric.CLOUD -> WeatherUnit.PERCENT
}

private fun chartAxisLabel(value: Double, tickStep: Double, metric: EngineComparisonMetric, locale: Locale, units: WeatherUnits): String =
    units.axisValue(value, metricUnit(metric), tickStep, when (metric) {
        EngineComparisonMetric.TEMP_MAX, EngineComparisonMetric.TEMP_MIN,
        EngineComparisonMetric.PRECIPITATION -> 1
        else -> 0
    }, locale)

private fun formatDecimal(value: Double?, locale: Locale, suffix: WeatherUnit, units: WeatherUnits): String =
    units.format(value, suffix, 1, locale)

private fun formatInteger(value: Double?, locale: Locale, suffix: WeatherUnit, units: WeatherUnits): String =
    units.format(value, suffix, 0, locale)

private fun formatDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
