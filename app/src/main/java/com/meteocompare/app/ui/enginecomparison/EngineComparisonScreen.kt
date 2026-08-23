package com.meteocompare.app.ui.enginecomparison

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.meteocompare.app.domain.usecase.EngineComparisonValues
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
            EngineComparisonHeader(
                cityName = state.cityName,
                selectedEngine = state.selectedEngine
            )
        }

        item {
            EngineSectionCard {
                Text(
                    text = stringResource(R.string.engine_comparison_metric),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
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

@Composable
private fun EngineLineChart(
    days: List<EngineComparisonDay>,
    metric: EngineComparisonMetric,
    selectedEngine: ForecastEngine
) {
    val values = days
        .flatMap { it.byEngine.values }
        .mapNotNull { it.value(metric) }
        .filter(Double::isFinite)

    if (days.size < 2 || values.size < 2) {
        Text(
            text = stringResource(R.string.engine_comparison_not_enough_data),
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
    val dayFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE", locale)
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val engineColors = mapOf(
        ForecastEngine.MULTI_CONSENSUS to engineColor(ForecastEngine.MULTI_CONSENSUS),
        ForecastEngine.CALIBRATION to engineColor(ForecastEngine.CALIBRATION),
        ForecastEngine.SCENARIOS to engineColor(ForecastEngine.SCENARIOS),
        ForecastEngine.ADAPTIVE to engineColor(ForecastEngine.ADAPTIVE)
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = metricLabel(metric),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = metricUnit(metric),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
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
                        val x = if (days.size == 1) {
                            left
                        } else {
                            left + (right - left) * index / (days.size - 1f)
                        }
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
                    text = day.date.format(dayFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
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
        DateTimeFormatter.ofPattern("EEE d", locale)
    }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            val color = divergenceLevelColor(day.divergence.level)
            val levelLabel = divergenceLevelLabel(day.divergence.level)

            Column(
                modifier = Modifier
                    .width(112.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(7.dp).background(color)
                )
                Column(Modifier.padding(10.dp)) {
                    Text(
                        text = day.date.format(dayFormatter),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = levelLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ΔT ${formatCompact(day.divergence.temperatureDelta, "°")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ΔV ${formatCompact(day.divergence.windDelta, " km/h")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ΔN ${formatCompact(day.divergence.cloudDelta, "%")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineDailyComparisonTable(
    days: List<EngineComparisonDay>,
    selectedEngine: ForecastEngine
) {
    val locale = LocalLocale.current.platformLocale
    val scrollState = rememberScrollState()
    val tableWidth = 620.dp

    Column(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).width(tableWidth)
    ) {
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

            val conditionValues = ForecastEngine.entries.associateWith { engine ->
                day.byEngine[engine]?.condition?.let { condition ->
                    stringResource(weatherConditionLabel(condition))
                } ?: "—"
            }

            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_temp_min),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.tempMin, locale, "°") }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_temp_max),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.tempMax, locale, "°") }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_precipitation),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.precipitationAmountMm, locale, " mm") }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_table_probability),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> value?.precipitationProbabilityPercent?.let { "$it%" } ?: "—" }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_table_expected_rain),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatDecimal(value?.precipitationExpectedMm, locale, " mm") }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_wind),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatInteger(value?.windKmh, locale, " km/h") }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_gust),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatInteger(value?.gustKmh, locale, " km/h") }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_metric_cloud),
                selectedEngine = selectedEngine,
                values = day.valuesForEngines { value -> formatInteger(value?.cloudPercent, locale, "%") }
            )
            ComparisonTableRow(
                label = stringResource(R.string.engine_table_condition),
                selectedEngine = selectedEngine,
                values = conditionValues
            )

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
    width: androidx.compose.ui.unit.Dp,
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

private fun metricUnit(metric: EngineComparisonMetric): String = when (metric) {
    EngineComparisonMetric.TEMP_MAX,
    EngineComparisonMetric.TEMP_MIN -> "°C"
    EngineComparisonMetric.PRECIPITATION -> "mm"
    EngineComparisonMetric.WIND,
    EngineComparisonMetric.GUST -> "km/h"
    EngineComparisonMetric.CLOUD -> "%"
}

private fun chartAxisLabel(
    value: Double,
    metric: EngineComparisonMetric,
    locale: Locale
): String = when (metric) {
    EngineComparisonMetric.TEMP_MAX,
    EngineComparisonMetric.TEMP_MIN,
    EngineComparisonMetric.PRECIPITATION -> String.format(locale, "%.1f", value)
    EngineComparisonMetric.WIND,
    EngineComparisonMetric.GUST,
    EngineComparisonMetric.CLOUD -> String.format(locale, "%.0f", value)
}

private fun formatDecimal(value: Double?, locale: Locale, suffix: String): String =
    value?.takeIf(Double::isFinite)?.let { String.format(locale, "%.1f%s", it, suffix) } ?: "—"

private fun formatInteger(value: Double?, locale: Locale, suffix: String): String =
    value?.takeIf(Double::isFinite)?.let { String.format(locale, "%.0f%s", it, suffix) } ?: "—"

private fun formatCompact(value: Double, suffix: String): String =
    if (value.isFinite()) String.format(Locale.ROOT, "%.1f%s", value, suffix) else "—"

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
