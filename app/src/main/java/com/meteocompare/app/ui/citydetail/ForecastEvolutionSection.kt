package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.DayForecastEvolution
import com.meteocompare.app.domain.model.ForecastEvolutionSnapshot
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.VariableForecastEvolution
import com.meteocompare.app.ui.components.CollapsibleSectionHeader
import com.meteocompare.app.ui.components.ModernTextTabs
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

internal const val TAG_FORECAST_EVOLUTION_CARD = "forecast-evolution-card"
internal const val TAG_FORECAST_EVOLUTION_DETAILS = "forecast-evolution-details"
internal const val TAG_FORECAST_EVOLUTION_HEADER = "forecast-evolution-header"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForecastEvolutionSection(
    state: ForecastEvolutionState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        ForecastEvolutionState.Idle,
        ForecastEvolutionState.Unavailable,
        is ForecastEvolutionState.Error -> return
        ForecastEvolutionState.Loading -> EvolutionLoadingCard(expanded, onExpandedChange, modifier)
        is ForecastEvolutionState.BuildingHistory -> EvolutionBuildingHistoryCard(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = modifier
        )
        is ForecastEvolutionState.Loaded -> {
            val report = state.report
            val usableDays = remember(report) {
                report.days.filter { day -> day.variables.values.any { it.revision != null } }
            }
            if (usableDays.isEmpty()) return

            var selectedEpochDay by rememberSaveable(report.fetchedAt) {
                mutableLongStateOf(usableDays.first().date.toEpochDay())
            }
            val selectedDay = usableDays.firstOrNull { it.date.toEpochDay() == selectedEpochDay }
                ?: usableDays.first()
            val availableVariables = selectedDay.variables.values
                .filter { it.revision != null }
                .map { it.variable }
            if (availableVariables.isEmpty()) return

            var selectedVariableName by rememberSaveable(selectedDay.date) {
                mutableStateOf(availableVariables.first().name)
            }
            val selectedVariable = availableVariables.firstOrNull {
                it.name == selectedVariableName
            } ?: availableVariables.first()
            val evolution = selectedDay.variables.getValue(selectedVariable)
            var showModels by remember { mutableStateOf(false) }

            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag(TAG_FORECAST_EVOLUTION_CARD),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 7.dp)) {
                    CollapsibleSectionHeader(
                        text = stringResource(R.string.forecast_evolution_title),
                        subtitle = stringResource(R.string.forecast_evolution_subtitle),
                        expanded = expanded,
                        onToggle = { onExpandedChange(!expanded) },
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag(TAG_FORECAST_EVOLUTION_HEADER)
                    )

                    // Repliée, la carte revient toujours au premier jour utile :
                    // autrement un jour futur sélectionné lorsqu'elle était ouverte
                    // resterait affiché sans aucune date visible dans le résumé.
                    EvolutionCompactSummary(
                        day = if (expanded) selectedDay else usableDays.first(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )

                    if (expanded) {
                        Column(modifier = Modifier.testTag(TAG_FORECAST_EVOLUTION_DETAILS)) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )

                            EvolutionDateSelector(
                                days = usableDays,
                                selected = selectedDay.date,
                                onSelected = { selectedEpochDay = it.toEpochDay() }
                            )

                            ModernTextTabs(
                                options = availableVariables,
                                selected = selectedVariable,
                                onSelected = { selectedVariableName = it.name },
                                label = { variable -> stringResource(variableLabel(variable)) },
                                accent = evolutionAccent(selectedVariable),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 5.dp)
                            )

                            EvolutionAnalysis(evolution = evolution)

                            TextButton(
                                onClick = { showModels = true },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(stringResource(R.string.forecast_evolution_view_models))
                            }
                        }
                    }
                }
            }

            if (showModels) {
                ModelEvolutionSheet(
                    evolution = evolution,
                    onDismiss = { showModels = false }
                )
            }
        }
    }
}

@Composable
private fun EvolutionBuildingHistoryCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TAG_FORECAST_EVOLUTION_CARD),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 7.dp)) {
            CollapsibleSectionHeader(
                text = stringResource(R.string.forecast_evolution_title),
                subtitle = stringResource(R.string.forecast_evolution_history_building),
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) },
                modifier = Modifier.testTag(TAG_FORECAST_EVOLUTION_HEADER)
            )
            if (expanded) {
                Text(
                    text = stringResource(R.string.forecast_evolution_history_building_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .testTag(TAG_FORECAST_EVOLUTION_DETAILS)
                )
            }
        }
    }
}

@Composable
private fun EvolutionLoadingCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TAG_FORECAST_EVOLUTION_CARD),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 7.dp)) {
            CollapsibleSectionHeader(
                text = stringResource(R.string.forecast_evolution_title),
                subtitle = stringResource(R.string.forecast_evolution_loading),
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) },
                modifier = Modifier.testTag(TAG_FORECAST_EVOLUTION_HEADER)
            )
            if (expanded) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .testTag(TAG_FORECAST_EVOLUTION_DETAILS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.forecast_evolution_loading_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EvolutionCompactSummary(
    day: DayForecastEvolution,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ForecastEvolutionVariable.entries.forEach { variable ->
            val evolution = day.variables[variable]
            EvolutionCompactMetric(
                variable = variable,
                trend = evolution?.trend ?: ForecastEvolutionTrend.INSUFFICIENT_DATA,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EvolutionCompactMetric(
    variable: ForecastEvolutionVariable,
    trend: ForecastEvolutionTrend,
    modifier: Modifier = Modifier
) {
    val accent = evolutionAccent(variable)
    val variableName = stringResource(variableLabel(variable))
    val trendName = stringResource(trendLabelResource(variable, trend))
    val a11yDescription = stringResource(
        R.string.forecast_evolution_metric_a11y,
        variableName,
        trendName
    )
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .clearAndSetSemantics { contentDescription = a11yDescription }
            .padding(horizontal = 7.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = variableIcon(variable),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = trendGlyph(trend),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EvolutionDateSelector(
    days: List<DayForecastEvolution>,
    selected: LocalDate,
    onSelected: (LocalDate) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(days, key = { it.date.toEpochDay() }) { day ->
            val isSelected = day.date == selected
            Surface(
                modifier = Modifier.clickable { onSelected(day.date) },
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ) {
                Text(
                    text = shortDate(day.date),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EvolutionAnalysis(evolution: VariableForecastEvolution) {
    val accent = evolutionAccent(evolution.variable)
    val revision = evolution.revision

    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trendTitle(evolution),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (revision != null) {
                    Text(
                        text = if (revision.trend == ForecastEvolutionTrend.VOLATILE) {
                            stringResource(
                                R.string.forecast_evolution_model_direction_volatile,
                                revision.comparedModels,
                                revision.previousAgeHours
                            )
                        } else {
                            stringResource(
                                R.string.forecast_evolution_model_direction,
                                revision.dominantModels,
                                revision.comparedModels,
                                revision.previousAgeHours
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        EvolutionTrendChart(evolution = evolution, accent = accent)
        EvolutionSnapshotValues(evolution = evolution)

        Text(
            text = stringResource(R.string.forecast_evolution_method_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EvolutionTrendChart(
    evolution: VariableForecastEvolution,
    accent: Color
) {
    val snapshots = evolution.allSnapshotsChronological
    if (snapshots.size < 2) return
    val values = snapshots.map(ForecastEvolutionSnapshot::medianValue)
    val min = values.minOrNull() ?: return
    val max = values.maxOrNull() ?: return
    val range = (max - min).takeIf { it > 0.0001 } ?: 1.0
    val lineColor = accent
    val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val pointInnerColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 5.dp.toPx()
        val bottom = size.height - 5.dp.toPx()
        drawLine(guideColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        val points = values.mapIndexed { index, value ->
            val x = if (values.size == 1) size.width / 2f
            else left + (right - left) * index / (values.size - 1).toFloat()
            val normalized = ((value - min) / range).toFloat()
            Offset(x, bottom - normalized * (bottom - top))
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { point ->
            drawCircle(lineColor, radius = 4.dp.toPx(), center = point)
            drawCircle(pointInnerColor, radius = 1.7.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun EvolutionSnapshotValues(evolution: VariableForecastEvolution) {
    val snapshots = evolution.allSnapshotsChronological
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        snapshots.forEach { snapshot ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (snapshot.daysAgo == 0) stringResource(R.string.forecast_evolution_now)
                    else stringResource(
                        R.string.forecast_evolution_day_ago_short,
                        snapshot.ageHours
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formatEvolutionValue(snapshot.medianValue, evolution.variable),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEvolutionSheet(
    evolution: VariableForecastEvolution,
    onDismiss: () -> Unit
) {
    val revision = evolution.revision ?: return
    val previous = evolution.previous.firstOrNull { it.daysAgo == revision.previousDaysAgo } ?: return
    val models = revision.deltasByModel.keys.sortedBy { it.resolutionKm }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.forecast_evolution_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.forecast_evolution_sheet_subtitle,
                    longDate(evolution.targetDate),
                    revision.previousAgeHours
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.forecast_evolution_model), modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall)
                Text(stringResource(R.string.forecast_evolution_previous), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text(stringResource(R.string.forecast_evolution_now), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                Text("Δ", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
            models.forEach { model ->
                val current = evolution.current.valuesByModel[model] ?: return@forEach
                val old = previous.valuesByModel[model] ?: return@forEach
                val delta = revision.deltasByModel[model] ?: return@forEach
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.displayName,
                        modifier = Modifier.weight(1.4f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(formatEvolutionValue(old, evolution.variable), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text(formatEvolutionValue(current, evolution.variable), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                    Text(
                        text = signedEvolutionValue(delta, evolution.variable),
                        modifier = Modifier.weight(0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun trendTitle(evolution: VariableForecastEvolution): String =
    stringResource(trendLabelResource(evolution.variable, evolution.trend))

private fun trendLabelResource(
    variable: ForecastEvolutionVariable,
    trend: ForecastEvolutionTrend
): Int = when (trend) {
    ForecastEvolutionTrend.INCREASING -> when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> R.string.forecast_evolution_temp_up
        ForecastEvolutionVariable.PRECIPITATION -> R.string.forecast_evolution_precip_up
        ForecastEvolutionVariable.WIND -> R.string.forecast_evolution_wind_up
    }
    ForecastEvolutionTrend.DECREASING -> when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> R.string.forecast_evolution_temp_down
        ForecastEvolutionVariable.PRECIPITATION -> R.string.forecast_evolution_precip_down
        ForecastEvolutionVariable.WIND -> R.string.forecast_evolution_wind_down
    }
    ForecastEvolutionTrend.VOLATILE -> R.string.forecast_evolution_volatile
    ForecastEvolutionTrend.STABLE -> R.string.forecast_evolution_stable
    ForecastEvolutionTrend.INSUFFICIENT_DATA -> R.string.forecast_evolution_insufficient
}

private fun trendGlyph(trend: ForecastEvolutionTrend): String = when (trend) {
    ForecastEvolutionTrend.INCREASING -> "↗"
    ForecastEvolutionTrend.DECREASING -> "↘"
    ForecastEvolutionTrend.VOLATILE -> "↕"
    ForecastEvolutionTrend.STABLE -> "→"
    ForecastEvolutionTrend.INSUFFICIENT_DATA -> "—"
}

private fun variableIcon(variable: ForecastEvolutionVariable): ImageVector = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> Icons.Outlined.Thermostat
    ForecastEvolutionVariable.PRECIPITATION -> Icons.Outlined.WaterDrop
    ForecastEvolutionVariable.WIND -> Icons.Outlined.Air
}

@Composable
private fun evolutionAccent(variable: ForecastEvolutionVariable): Color = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> temperatureMetricAccent()
    ForecastEvolutionVariable.PRECIPITATION -> precipitationMetricAccent()
    ForecastEvolutionVariable.WIND -> windMetricAccent()
}

private fun variableLabel(variable: ForecastEvolutionVariable): Int = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> R.string.forecast_evolution_metric_temperature_max
    ForecastEvolutionVariable.PRECIPITATION -> R.string.forecast_evolution_metric_precipitation_sum
    ForecastEvolutionVariable.WIND -> R.string.forecast_evolution_metric_wind_max
}

private fun formatEvolutionValue(value: Double, variable: ForecastEvolutionVariable): String = when (variable) {
    ForecastEvolutionVariable.TEMPERATURE -> "${value.roundToInt()}°"
    ForecastEvolutionVariable.PRECIPITATION -> String.format(Locale.getDefault(), "%.1f mm", value)
    ForecastEvolutionVariable.WIND -> "${value.roundToInt()} km/h"
}

private fun signedEvolutionValue(value: Double, variable: ForecastEvolutionVariable): String {
    val sign = if (value > 0) "+" else ""
    return when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> "$sign${String.format(Locale.getDefault(), "%.1f", value)}°"
        ForecastEvolutionVariable.PRECIPITATION -> "$sign${String.format(Locale.getDefault(), "%.1f", value)} mm"
        ForecastEvolutionVariable.WIND -> "$sign${value.roundToInt()} km/h"
    }
}

private fun shortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d"))

private fun longDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
