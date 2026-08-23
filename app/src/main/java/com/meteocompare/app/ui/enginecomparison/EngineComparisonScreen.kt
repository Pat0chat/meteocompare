package com.meteocompare.app.ui.enginecomparison

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt

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
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            is EngineComparisonUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.message)
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
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
                Text(state.cityName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.engine_comparison_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.engine_comparison_selected, engineLabel(state.selectedEngine)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ForecastEngine.entries.forEach { engine ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (engine == state.selectedEngine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                engineLabel(engine),
                                fontWeight = if (engine == state.selectedEngine) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Text(
                                engineDescription(engine),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    EngineLineChart(state.days, metric)
                    Spacer(Modifier.height(8.dp))
                    EngineLegend(state.selectedEngine)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.engine_comparison_divergence_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.engine_comparison_divergence_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.days.size) { index ->
            DivergenceCard(state.days[index], Modifier.padding(horizontal = 16.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.engine_comparison_details_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        items(state.days.size) { index ->
            EngineDayTable(state.days[index], state.selectedEngine, Modifier.padding(horizontal = 16.dp))
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
private fun EngineLineChart(days: List<EngineComparisonDay>, metric: EngineComparisonMetric) {
    val values = days.flatMap { it.byEngine.values }.mapNotNull { it.value(metric) }.filter(Double::isFinite)
    if (days.size < 2 || values.size < 2) {
        Text(stringResource(R.string.engine_comparison_not_enough_data), style = MaterialTheme.typography.bodySmall)
        return
    }
    val minValue = values.minOrNull() ?: return
    val maxValue = values.maxOrNull() ?: return
    val range = (maxValue - minValue).takeIf { it > 1e-9 } ?: 1.0
    val colors = mapOf(
        ForecastEngine.MULTI_CONSENSUS to MaterialTheme.colorScheme.primary,
        ForecastEngine.CALIBRATION to MaterialTheme.colorScheme.secondary,
        ForecastEngine.SCENARIOS to MaterialTheme.colorScheme.tertiary,
        ForecastEngine.ADAPTIVE to MaterialTheme.colorScheme.error
    )
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        ForecastEngine.entries.forEach { engine ->
            val path = Path()
            var started = false
            days.forEachIndexed { index, day ->
                val value = day.byEngine[engine]?.value(metric) ?: return@forEachIndexed
                val x = if (days.size == 1) left else left + (right - left) * index / (days.size - 1f)
                val y = bottom - ((value - minValue) / range).toFloat() * (bottom - top)
                if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                drawCircle(colors.getValue(engine), radius = 3.dp.toPx(), center = Offset(x, y))
            }
            if (started) drawPath(path, colors.getValue(engine), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    val platformLocale = LocalLocale.current.platformLocale
    val dayFormatter = remember(platformLocale) {
        DateTimeFormatter.ofPattern("EEE", platformLocale)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Text(day.date.format(dayFormatter), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EngineLegend(selected: ForecastEngine) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ForecastEngine.entries.forEach { engine ->
            Text(
                text = (if (engine == selected) "● " else "○ ") + engineLabel(engine),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (engine == selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun DivergenceCard(day: EngineComparisonDay, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    val label = when (day.divergence.level) {
        EngineDivergenceLevel.HIGH -> stringResource(R.string.engine_divergence_high)
        EngineDivergenceLevel.MEDIUM -> stringResource(R.string.engine_divergence_medium)
        EngineDivergenceLevel.LOW -> stringResource(R.string.engine_divergence_low)
    }
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(day.date, locale), fontWeight = FontWeight.SemiBold)
                Text(label, color = when (day.divergence.level) {
                    EngineDivergenceLevel.HIGH -> MaterialTheme.colorScheme.error
                    EngineDivergenceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                    EngineDivergenceLevel.LOW -> MaterialTheme.colorScheme.primary
                }, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.engine_divergence_values,
                    day.divergence.temperatureDelta,
                    day.divergence.precipitationDelta,
                    day.divergence.windDelta,
                    day.divergence.cloudDelta.roundToInt()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EngineDayTable(day: EngineComparisonDay, selected: ForecastEngine, modifier: Modifier = Modifier) {
    val locale = LocalLocale.current.platformLocale
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(formatDate(day.date, locale), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ForecastEngine.entries.forEachIndexed { index, engine ->
                val value = day.byEngine[engine]
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(
                        (if (engine == selected) "● " else "") + engineLabel(engine),
                        fontWeight = if (engine == selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (engine == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(
                            R.string.engine_comparison_row_values,
                            value?.tempMin?.let { String.format(locale, "%.1f", it) } ?: "—",
                            value?.tempMax?.let { String.format(locale, "%.1f", it) } ?: "—",
                            value?.precipitationAmountMm?.let { String.format(locale, "%.1f", it) } ?: "—",
                            value?.precipitationProbabilityPercent?.toString() ?: "—"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(
                            R.string.engine_comparison_row_values_secondary,
                            value?.precipitationExpectedMm?.let { String.format(locale, "%.1f", it) } ?: "—",
                            value?.windKmh?.let { String.format(locale, "%.0f", it) } ?: "—",
                            value?.gustKmh?.let { String.format(locale, "%.0f", it) } ?: "—",
                            value?.cloudPercent?.let { String.format(locale, "%.0f", it) } ?: "—",
                            value?.condition?.let { stringResource(weatherConditionLabel(it)) } ?: "—"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index != ForecastEngine.entries.lastIndex) HorizontalDivider()
            }
        }
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
