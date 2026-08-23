package com.meteocompare.app.ui.enginecomparison

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.usecase.EngineComparisonDay
import com.meteocompare.app.domain.usecase.EngineComparisonMetric
import com.meteocompare.app.domain.usecase.EngineDivergenceLevel
import com.meteocompare.app.ui.components.ModernStateChip
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.engine_comparison_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back))
                    }
                }
            )
        }
    ) { padding ->
        when (val current = state) {
            EngineComparisonUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is EngineComparisonUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineComparisonContent(
    state: EngineComparisonUiState.Loaded,
    modifier: Modifier = Modifier
) {
    var metric by remember { mutableStateOf(EngineComparisonMetric.TEMP_MAX) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    state.cityName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.engine_comparison_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Σ",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.engine_comparison_selected, engineLabel(state.selectedEngine)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.engine_comparison_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    ForecastEngine.entries.forEachIndexed { index, engine ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(
                                1.dp,
                                if (engine == state.selectedEngine) {
                                    engineColor(engine).copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (engine == state.selectedEngine) {
                                    engineColor(engine).copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    modifier = Modifier.size(26.dp),
                                    shape = CircleShape,
                                    color = engineColor(engine)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = when (engine) {
                                                ForecastEngine.MULTI_CONSENSUS -> "M"
                                                ForecastEngine.CALIBRATION -> "C"
                                                ForecastEngine.SCENARIOS -> "S"
                                                ForecastEngine.ADAPTIVE -> "A"
                                            },
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        engineLabel(engine),
                                        fontWeight = if (engine == state.selectedEngine) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (engine == state.selectedEngine) engineColor(engine) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        engineDescription(engine),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (index != ForecastEngine.entries.lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(R.string.engine_comparison_metric), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EngineComparisonMetric.entries.forEach { candidate ->
                            ModernStateChip(
                                selected = metric == candidate,
                                onClick = { metric = candidate },
                                label = metricLabel(candidate),
                                accent = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    EngineLineChart(state.days, metric, state.selectedEngine)
                    Spacer(Modifier.height(10.dp))
                    EngineLegend(state.selectedEngine)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.engine_comparison_divergence_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.engine_comparison_divergence_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.days) { day ->
            DivergenceTimelineCard(day, Modifier.padding(horizontal = 16.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.engine_comparison_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        items(state.days) { day ->
            EngineDayTable(day, state.selectedEngine, Modifier.padding(horizontal = 16.dp))
        }
        item {
            Text(
                text = stringResource(R.string.engine_comparison_science_note),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EngineLineChart(
    days: List<EngineComparisonDay>,
    metric: EngineComparisonMetric,
    selectedEngine: ForecastEngine
) {
    val values = days.flatMap { it.byEngine.values }.mapNotNull { it.value(metric) }.filter(Double::isFinite)
    if (days.size < 2 || values.size < 2) {
        Text(
            stringResource(R.string.engine_comparison_not_enough_data),
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    val locale = LocalLocale.current.platformLocale
    val minValue = values.minOrNull() ?: return
    val maxValue = values.maxOrNull() ?: return
    val range = (maxValue - minValue).takeIf { it > 1e-9 } ?: 1.0
    val topLabel = maxValue
    val midLabel = minValue + range / 2.0
    val bottomLabel = minValue
    val platformLocale = LocalLocale.current.platformLocale
    val dayFormatter = remember(platformLocale) {
        DateTimeFormatter.ofPattern("EEE", platformLocale)
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val engineColors = ForecastEngine.entries.associateWith { engineColor(it) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                metricLabel(metric),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = metricUnit(metric),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.height(220.dp).padding(end = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(chartAxisLabel(topLabel, metric, locale), style = MaterialTheme.typography.labelSmall)
                Text(chartAxisLabel(midLabel, metric, locale), style = MaterialTheme.typography.labelSmall)
                Text(chartAxisLabel(bottomLabel, metric, locale), style = MaterialTheme.typography.labelSmall)
            }
            Canvas(
                modifier = Modifier.weight(1f).height(220.dp)
            ) {
                val left = 6.dp.toPx()
                val right = size.width - 6.dp.toPx()
                val top = 12.dp.toPx()
                val bottom = size.height - 12.dp.toPx()
                listOf(top, top + (bottom - top) / 2f, bottom).forEach { y ->
                    drawLine(
                        color = gridColor,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                drawLine(
                    color = gridColor,
                    start = Offset(left, top),
                    end = Offset(left, bottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(left, bottom),
                    end = Offset(right, bottom),
                    strokeWidth = 1.dp.toPx()
                )

                ForecastEngine.entries.forEach { engine ->
                    val path = Path()
                    var started = false
                    days.forEachIndexed { index, day ->
                        val value = day.byEngine[engine]?.value(metric) ?: return@forEachIndexed
                        val x = if (days.size == 1) left else left + (right - left) * index / (days.size - 1f)
                        val y = bottom - ((value - minValue) / range).toFloat() * (bottom - top)
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                        drawCircle(
                            color = engineColors.getValue(engine),
                            radius = if (engine == selectedEngine) 4.dp.toPx() else 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                    if (started) {
                        drawPath(
                            path = path,
                            color = engineColors.getValue(engine),
                            style = Stroke(
                                width = if (engine == selectedEngine) 3.dp.toPx() else 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 42.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                Text(
                    day.date.format(dayFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineLegend(selected: ForecastEngine) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ForecastEngine.entries.forEach { engine ->
            val color = engineColor(engine)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (engine == selected) {
                    color.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Canvas(Modifier.width(22.dp).height(10.dp)) {
                        val y = size.height / 2f
                        drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = if (engine == selected) 3.dp.toPx() else 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width / 2f, y))
                    }
                    Text(
                        text = engineLabel(engine),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (engine == selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (engine == selected) color else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DivergenceTimelineCard(day: EngineComparisonDay, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val levelColor = when (day.divergence.level) {
        EngineDivergenceLevel.HIGH -> MaterialTheme.colorScheme.error
        EngineDivergenceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
        EngineDivergenceLevel.LOW -> MaterialTheme.colorScheme.primary
    }
    val label = when (day.divergence.level) {
        EngineDivergenceLevel.HIGH -> stringResource(R.string.engine_divergence_high)
        EngineDivergenceLevel.MEDIUM -> stringResource(R.string.engine_divergence_medium)
        EngineDivergenceLevel.LOW -> stringResource(R.string.engine_divergence_low)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, levelColor.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.width(6.dp).fillMaxHeight().background(levelColor)
            )
            Column(Modifier.weight(1f).padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatDate(day.date, locale), fontWeight = FontWeight.SemiBold)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = levelColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = levelColor,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                DivergenceBarRow("Temp", day.divergence.temperatureDelta, "°C", EngineComparisonMetric.TEMP_MAX)
                Spacer(Modifier.height(6.dp))
                DivergenceBarRow("Pluie", day.divergence.precipitationDelta, "mm", EngineComparisonMetric.PRECIPITATION)
                Spacer(Modifier.height(6.dp))
                DivergenceBarRow("Vent", day.divergence.windDelta, "km/h", EngineComparisonMetric.WIND)
                Spacer(Modifier.height(6.dp))
                DivergenceBarRow("Nuages", day.divergence.cloudDelta, "%", EngineComparisonMetric.CLOUD)
                if (day.divergence.conditionCount > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${day.divergence.conditionCount} conditions distinctes selon les moteurs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DivergenceBarRow(
    label: String,
    delta: Double,
    unit: String,
    metric: EngineComparisonMetric
) {
    val color = divergenceColor(delta, metric)
    val fraction = divergenceFraction(delta, metric)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(58.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier.weight(1f).height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                        .background(color)
                )
            }
        }
        Text(
            text = if (delta.isFinite()) {
                when (metric) {
                    EngineComparisonMetric.TEMP_MAX,
                    EngineComparisonMetric.TEMP_MIN,
                    EngineComparisonMetric.PRECIPITATION -> String.format(Locale.ROOT, "%.1f %s", delta, unit)
                    else -> String.format(Locale.ROOT, "%.0f %s", delta, unit)
                }
            } else {
                "—"
            },
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EngineDayTable(day: EngineComparisonDay, selected: ForecastEngine, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDate(day.date, locale),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = engineLabel(selected),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ForecastEngine.entries.forEachIndexed { index, engine ->
                val value = day.byEngine[engine]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        1.dp,
                        if (engine == selected) engineColor(engine).copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (engine == selected) engineColor(engine).copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(10.dp),
                                    shape = CircleShape,
                                    color = engineColor(engine)
                                ) {}
                                Text(
                                    engineLabel(engine),
                                    fontWeight = if (engine == selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (engine == selected) engineColor(engine) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                value?.condition?.let { stringResource(weatherConditionLabel(it)) } ?: "—",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricBadge("Tmin", value?.tempMin?.let { String.format(locale, "%.1f°", it) } ?: "—")
                            MetricBadge("Tmax", value?.tempMax?.let { String.format(locale, "%.1f°", it) } ?: "—")
                            MetricBadge("Pluie", value?.precipitationAmountMm?.let { String.format(locale, "%.1f mm", it) } ?: "—")
                            MetricBadge("Prob.", value?.precipitationProbabilityPercent?.let { "$it%" } ?: "—")
                            MetricBadge("Attendue", value?.precipitationExpectedMm?.let { String.format(locale, "%.1f mm", it) } ?: "—")
                            MetricBadge("Vent", value?.windKmh?.let { String.format(locale, "%.0f km/h", it) } ?: "—")
                            MetricBadge("Raf.", value?.gustKmh?.let { String.format(locale, "%.0f km/h", it) } ?: "—")
                            MetricBadge("Nuages", value?.cloudPercent?.let { String.format(locale, "%.0f%%", it) } ?: "—")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            engineDescription(engine),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index != ForecastEngine.entries.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun engineColor(engine: ForecastEngine): Color = when (engine) {
    ForecastEngine.MULTI_CONSENSUS -> MaterialTheme.colorScheme.primary
    ForecastEngine.CALIBRATION -> MaterialTheme.colorScheme.secondary
    ForecastEngine.SCENARIOS -> MaterialTheme.colorScheme.tertiary
    ForecastEngine.ADAPTIVE -> MaterialTheme.colorScheme.error
}

private fun metricUnit(metric: EngineComparisonMetric): String = when (metric) {
    EngineComparisonMetric.TEMP_MAX,
    EngineComparisonMetric.TEMP_MIN -> "°C"
    EngineComparisonMetric.PRECIPITATION -> "mm"
    EngineComparisonMetric.WIND,
    EngineComparisonMetric.GUST -> "km/h"
    EngineComparisonMetric.CLOUD -> "%"
}

private fun chartAxisLabel(value: Double, metric: EngineComparisonMetric, locale: Locale): String = when (metric) {
    EngineComparisonMetric.TEMP_MAX,
    EngineComparisonMetric.TEMP_MIN,
    EngineComparisonMetric.PRECIPITATION -> String.format(locale, "%.1f", value)
    EngineComparisonMetric.WIND,
    EngineComparisonMetric.GUST,
    EngineComparisonMetric.CLOUD -> String.format(locale, "%.0f", value)
}

private fun divergenceFraction(delta: Double, metric: EngineComparisonMetric): Float {
    if (!delta.isFinite() || delta <= 0.0) return 0f
    val max = when (metric) {
        EngineComparisonMetric.TEMP_MAX,
        EngineComparisonMetric.TEMP_MIN -> 8.0
        EngineComparisonMetric.PRECIPITATION -> 10.0
        EngineComparisonMetric.WIND,
        EngineComparisonMetric.GUST -> 20.0
        EngineComparisonMetric.CLOUD -> 80.0
    }
    return (delta / max).coerceIn(0.05, 1.0).toFloat()
}

@Composable
private fun divergenceColor(delta: Double, metric: EngineComparisonMetric): Color {
    val ratio = divergenceFraction(delta, metric)
    return when {
        ratio >= 0.75f -> MaterialTheme.colorScheme.error
        ratio >= 0.4f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun engineLabel(engine: ForecastEngine): String = stringResource(when (engine) {
    ForecastEngine.MULTI_CONSENSUS -> R.string.forecast_engine_multi_consensus
    ForecastEngine.CALIBRATION -> R.string.forecast_engine_calibration
    ForecastEngine.SCENARIOS -> R.string.forecast_engine_scenarios
    ForecastEngine.ADAPTIVE -> R.string.forecast_engine_adaptive
})

@Composable
private fun metricLabel(metric: EngineComparisonMetric): String = stringResource(when (metric) {
    EngineComparisonMetric.TEMP_MAX -> R.string.engine_metric_temp_max
    EngineComparisonMetric.TEMP_MIN -> R.string.engine_metric_temp_min
    EngineComparisonMetric.PRECIPITATION -> R.string.engine_metric_precipitation
    EngineComparisonMetric.WIND -> R.string.engine_metric_wind
    EngineComparisonMetric.GUST -> R.string.engine_metric_gust
    EngineComparisonMetric.CLOUD -> R.string.engine_metric_cloud
})

@Composable
private fun engineDescription(engine: ForecastEngine): String = stringResource(when (engine) {
    ForecastEngine.MULTI_CONSENSUS -> R.string.forecast_engine_multi_consensus_desc
    ForecastEngine.CALIBRATION -> R.string.forecast_engine_calibration_desc
    ForecastEngine.SCENARIOS -> R.string.forecast_engine_scenarios_desc
    ForecastEngine.ADAPTIVE -> R.string.forecast_engine_adaptive_desc
})

private fun weatherConditionLabel(condition: WeatherCondition): Int = when (condition) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_overcast
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
    WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN -> R.string.weather_unknown
}

private fun formatDate(date: java.time.LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
