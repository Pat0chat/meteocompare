package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasDirection
import com.meteocompare.app.domain.model.BiasSignificance
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.ModelReliability
import com.meteocompare.app.domain.model.ModelReliabilityCalculator
import com.meteocompare.app.domain.model.PrecipitationReliability
import com.meteocompare.app.domain.model.ReliabilityLevel
import com.meteocompare.app.domain.model.ReliabilityRank
import com.meteocompare.app.domain.model.ReliabilityTrend
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.confidenceColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tableau de fiabilité local d'un modèle.
 *
 * La sheet ne se limite plus au biais signé : elle distingue désormais le sens
 * de l'erreur (biais), son amplitude réellement ressentie (MAE/RMSE), sa
 * régularité, la part de journées proches, l'évolution récente, le rang local
 * et la comparaison à la moyenne multi-modèles. Pour la pluie, elle ajoute les
 * détections, fausses alertes et événements manqués.
 *
 * Le contenu est scrollable afin de rester utilisable en paysage et avec une
 * grande taille de police.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelBiasDetailSheet(
    selection: BiasSelection?,
    onDismiss: () -> Unit
) {
    if (selection == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            SheetEyebrow(selection.model, selection.bias.variable, selection.bias.windowDays)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.bias_reliability_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            ReliabilityHero(selection)
            Spacer(Modifier.height(10.dp))
            ReliabilitySummary(selection)

            SectionTitle(stringResource(R.string.bias_reliability_section_performance))
            ReliabilityMetricsGrid(selection)
            Spacer(Modifier.height(10.dp))
            RecentTrendCard(selection.reliability)

            selection.multiModelReliability?.let { baseline ->
                Spacer(Modifier.height(10.dp))
                MultiModelComparisonCard(selection.reliability, baseline)
            }

            SectionTitle(stringResource(R.string.bias_reliability_section_history))
            BiasSparkline(
                forecast = selection.dailyForecast,
                observation = selection.dailyObservation,
                direction = selection.bias.direction,
                yDomainMin = selection.yDomainMin,
                yDomainMax = selection.yDomainMax
            )

            selection.reliability.precipitation?.let { precipitation ->
                SectionTitle(stringResource(R.string.bias_reliability_section_rain))
                PrecipitationDiagnosticsCard(precipitation)
            }

            SectionTitle(stringResource(R.string.bias_reliability_section_bias))
            BiasReadingCard(selection)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.bias_reliability_method_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Données complètes nécessaires au tableau de fiabilité. */
@Immutable
internal data class BiasSelection(
    val model: WeatherModel,
    val bias: ModelBias,
    val reliability: ModelReliability,
    val localRank: ReliabilityRank?,
    val multiModelReliability: ModelReliability?,
    val dailyForecast: List<Double>,
    val dailyObservation: List<Double>,
    val yDomainMin: Double,
    val yDomainMax: Double
)

/** Construit de manière pure les données de la sheet pour un modèle donné. */
internal fun buildBiasSelection(
    model: WeatherModel,
    variable: BiasVariable,
    state: VariableBiasState
): BiasSelection? {
    val bias = state.biasByModel[model] ?: return null
    val samples = state.historyByModel[model] ?: return null
    val yMin = state.yDomainMin ?: return null
    val yMax = state.yDomainMax ?: return null
    val perDay = samples.distinctBy { it.targetDate }

    val reliabilityByModel = state.historyByModel.mapNotNull { (candidate, history) ->
        ModelReliabilityCalculator.compute(
            variable = variable,
            samples = history,
            windowDays = bias.windowDays
        )?.let { candidate to it }
    }.toMap()
    val reliability = reliabilityByModel[model] ?: return null

    return BiasSelection(
        model = model,
        bias = bias,
        reliability = reliability,
        localRank = ModelReliabilityCalculator.rank(model, reliabilityByModel),
        multiModelReliability = ModelReliabilityCalculator.computeMultiModelBaseline(
            variable = variable,
            historyByModel = state.historyByModel,
            windowDays = bias.windowDays
        ),
        dailyForecast = perDay.map { it.forecast },
        dailyObservation = perDay.map { it.observation },
        yDomainMin = yMin,
        yDomainMax = yMax
    )
}

@Composable
private fun SheetEyebrow(model: WeatherModel, variable: BiasVariable, windowDays: Int) {
    val variableLabel = stringResource(sheetVariableLabelResId(variable))
    Text(
        text = stringResource(
            R.string.bias_sheet_eyebrow,
            model.displayName,
            variableLabel,
            windowDays
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ReliabilityHero(selection: BiasSelection) {
    val reliability = selection.reliability
    val accent = when (reliability.level) {
        ReliabilityLevel.EXCELLENT, ReliabilityLevel.GOOD -> confidenceColor(100)
        ReliabilityLevel.FAIR -> confidenceColor(60)
        ReliabilityLevel.LIMITED -> confidenceColor(20)
    }
    val container = accent.copy(alpha = 0.11f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bias_reliability_local_index),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = reliability.score.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            text = stringResource(R.string.bias_reliability_score_suffix),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp, start = 3.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(reliabilityLevelResId(reliability.level)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    selection.localRank?.let { rank ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.bias_reliability_rank,
                                rank.rank,
                                rank.modelCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ScoreBar(progress = reliability.score / 100f, accent = accent)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.bias_reliability_score_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreBar(progress: Float, accent: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
    }
}

@Composable
private fun ReliabilitySummary(selection: BiasSelection) {
    val error = formatMeasure(selection.reliability.meanAbsoluteError, selection.bias.variable)
    val tailRes = when {
        selection.bias.significance == BiasSignificance.NOT_SIGNIFICANT ->
            R.string.bias_reliability_summary_calibrated
        selection.bias.direction == BiasDirection.WARM ->
            R.string.bias_reliability_summary_over
        selection.bias.direction == BiasDirection.COLD ->
            R.string.bias_reliability_summary_under
        else -> R.string.bias_reliability_summary_calibrated
    }
    Text(
        text = stringResource(
            R.string.bias_reliability_summary,
            selection.model.displayName,
            error,
            stringResource(tailRes)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ReliabilityMetricsGrid(selection: BiasSelection) {
    val reliability = selection.reliability
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_mae),
                value = formatMeasure(reliability.meanAbsoluteError, reliability.variable),
                supporting = stringResource(
                    R.string.bias_reliability_rmse_support,
                    formatMeasure(reliability.rootMeanSquareError, reliability.variable)
                ),
                modifier = Modifier.weight(1f),
                emphasized = true
            )
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_mean_bias),
                value = formatBiasLabel(selection.bias),
                supporting = stringResource(biasDirectionSupportResId(selection.bias)),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_close_days),
                value = "${(reliability.withinToleranceRate * 100).roundToInt()} %",
                supporting = stringResource(
                    R.string.bias_reliability_close_days_support,
                    formatMeasure(reliability.closeTolerance, reliability.variable)
                ),
                modifier = Modifier.weight(1f)
            )
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_variability),
                value = formatMeasure(reliability.standardDeviation, reliability.variable),
                supporting = stringResource(R.string.bias_reliability_variability_support),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReliabilityMetricCard(
    label: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentTrendCard(reliability: ModelReliability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bias_reliability_recent_trend),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(reliabilityTrendResId(reliability.trend)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val recentError = reliability.recentMeanAbsoluteError
            val previousError = reliability.previousMeanAbsoluteError
            if (recentError != null && previousError != null) {
                Text(
                    text = stringResource(
                        R.string.bias_reliability_trend_values,
                        formatMeasure(previousError, reliability.variable),
                        formatMeasure(recentError, reliability.variable)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun MultiModelComparisonCard(
    selected: ModelReliability,
    baseline: ModelReliability
) {
    val selectedMae = selected.meanAbsoluteError
    val baselineMae = baseline.meanAbsoluteError
    val deltaPercent = if (baselineMae > 0.0) {
        ((selectedMae - baselineMae) / baselineMae * 100.0)
    } else {
        0.0
    }
    val comparisonText = when {
        abs(deltaPercent) < 5.0 -> stringResource(R.string.bias_reliability_baseline_similar)
        deltaPercent < 0.0 -> stringResource(
            R.string.bias_reliability_baseline_better,
            abs(deltaPercent).roundToInt()
        )
        else -> stringResource(
            R.string.bias_reliability_baseline_worse,
            abs(deltaPercent).roundToInt()
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.bias_reliability_baseline_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = comparisonText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(
                    R.string.bias_reliability_baseline_values,
                    formatMeasure(selectedMae, selected.variable),
                    formatMeasure(baselineMae, baseline.variable)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrecipitationDiagnosticsCard(stats: PrecipitationReliability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RainMetricRow(
                label = stringResource(R.string.bias_reliability_rain_detection),
                value = stats.hitRate
            )
            RainMetricRow(
                label = stringResource(R.string.bias_reliability_rain_false_alarms),
                value = stats.falseAlarmRate
            )
            RainMetricRow(
                label = stringResource(R.string.bias_reliability_rain_misses),
                value = stats.missedEventRate
            )
            Text(
                text = stringResource(
                    R.string.bias_reliability_rain_samples,
                    stats.observedWetDays,
                    stats.forecastWetDays
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RainMetricRow(label: String, value: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value?.let { "${(it * 100).roundToInt()} %" }
                ?: stringResource(R.string.bias_reliability_not_available),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BiasReadingCard(selection: BiasSelection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SheetBiasTitle(selection.bias)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.bias_reliability_direction_balance,
                    (selection.reliability.overestimateRate * 100).roundToInt(),
                    (selection.reliability.underestimateRate * 100).roundToInt()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            BiasExplainer(selection)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.bias_reliability_sample_coverage,
                    selection.reliability.sampleSize,
                    selection.reliability.windowDays
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Titre chiffré du biais, désormais replacé dans une section explicative. */
@Composable
private fun SheetBiasTitle(bias: ModelBias) {
    val isCalibrated = bias.significance == BiasSignificance.NOT_SIGNIFICANT
    val palette = if (isCalibrated) {
        biasChipPalette(BiasDirection.NEUTRAL, pending = false)
    } else {
        biasChipPalette(bias.direction, pending = false)
    }
    val prefix = stringResource(R.string.bias_sheet_title_prefix)
    val suffix = stringResource(R.string.bias_sheet_title_suffix)
    val annotated = buildAnnotatedString {
        append(prefix)
        append(" ")
        withStyle(
            SpanStyle(
                color = palette.foreground,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        ) { append(formatBiasLabel(bias)) }
        append(" ")
        append(suffix)
    }
    Text(text = annotated, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun BiasExplainer(selection: BiasSelection) {
    val name = selection.model.displayName
    if (selection.bias.significance == BiasSignificance.NOT_SIGNIFICANT) {
        Text(
            text = stringResource(
                R.string.bias_explainer_calibrated,
                name,
                stringResource(sheetExplainerVariableResId(selection.bias.variable)),
                selection.bias.windowDays
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    val opener = stringResource(
        R.string.bias_explainer_opener,
        name,
        stringResource(explainerVerbResId(selection.bias.direction)),
        stringResource(sheetExplainerVariableResId(selection.bias.variable)),
        selection.bias.windowDays
    )
    Text(
        text = "$opener ${stringResource(explainerStabilityResId(selection.bias.significance))}",
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun formatMeasure(value: Double, variable: BiasVariable): String =
    "%.1f".format(value) + unitFor(variable)

private fun unitFor(variable: BiasVariable): String = when (variable) {
    BiasVariable.TEMPERATURE -> "°"
    BiasVariable.PRECIPITATION -> " mm"
    BiasVariable.WIND_SPEED -> " km/h"
}

private fun sheetVariableLabelResId(variable: BiasVariable): Int = when (variable) {
    BiasVariable.TEMPERATURE -> R.string.bias_sheet_variable_temperature
    BiasVariable.PRECIPITATION -> R.string.bias_sheet_variable_precipitation
    BiasVariable.WIND_SPEED -> R.string.bias_sheet_variable_wind_speed
}

private fun sheetExplainerVariableResId(variable: BiasVariable): Int = when (variable) {
    BiasVariable.TEMPERATURE -> R.string.bias_variable_temperature
    BiasVariable.PRECIPITATION -> R.string.bias_variable_precipitation
    BiasVariable.WIND_SPEED -> R.string.bias_variable_wind_speed
}

private fun explainerVerbResId(direction: BiasDirection): Int = when (direction) {
    BiasDirection.WARM -> R.string.bias_verb_overestimates
    BiasDirection.COLD -> R.string.bias_verb_underestimates
    BiasDirection.NEUTRAL -> R.string.bias_verb_neutral
}

private fun explainerStabilityResId(significance: BiasSignificance): Int = when (significance) {
    BiasSignificance.HIGH -> R.string.bias_stability_high
    BiasSignificance.MODERATE -> R.string.bias_stability_moderate
    BiasSignificance.NOT_SIGNIFICANT -> R.string.bias_stability_not_significant
}

private fun reliabilityLevelResId(level: ReliabilityLevel): Int = when (level) {
    ReliabilityLevel.EXCELLENT -> R.string.bias_reliability_level_excellent
    ReliabilityLevel.GOOD -> R.string.bias_reliability_level_good
    ReliabilityLevel.FAIR -> R.string.bias_reliability_level_fair
    ReliabilityLevel.LIMITED -> R.string.bias_reliability_level_limited
}

private fun reliabilityTrendResId(trend: ReliabilityTrend): Int = when (trend) {
    ReliabilityTrend.IMPROVING -> R.string.bias_reliability_trend_improving
    ReliabilityTrend.STABLE -> R.string.bias_reliability_trend_stable
    ReliabilityTrend.DECLINING -> R.string.bias_reliability_trend_declining
    ReliabilityTrend.INSUFFICIENT_DATA -> R.string.bias_reliability_trend_insufficient
}

private fun biasDirectionSupportResId(bias: ModelBias): Int = when {
    bias.significance == BiasSignificance.NOT_SIGNIFICANT ->
        R.string.bias_reliability_direction_calibrated
    bias.direction == BiasDirection.WARM -> R.string.bias_reliability_direction_over
    bias.direction == BiasDirection.COLD -> R.string.bias_reliability_direction_under
    else -> R.string.bias_reliability_direction_calibrated
}
