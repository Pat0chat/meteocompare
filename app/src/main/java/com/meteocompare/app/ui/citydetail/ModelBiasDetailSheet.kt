package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.meteocompare.app.domain.model.PrecipitationReliability
import com.meteocompare.app.domain.model.ReliabilityLevel
import com.meteocompare.app.domain.model.ReliabilityTrend
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import kotlin.math.abs
import kotlin.math.roundToInt


internal const val TAG_MODEL_BIAS_DETAIL_SHEET = "model-bias-detail-sheet"
internal const val TAG_MODEL_BIAS_HEADER = "model-bias-header"
internal const val TAG_MODEL_BIAS_SCORE = "model-bias-score"

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
    onDismiss: () -> Unit,
    onOpenRanking: ((WeatherModel, BiasVariable) -> Unit)? = null
) {
    if (selection == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        val variableAccent = biasVariableAccent(selection.bias.variable)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_MODEL_BIAS_DETAIL_SHEET)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            BiasSheetHeader(
                model = selection.model,
                variable = selection.bias.variable,
                windowDays = selection.bias.windowDays,
                accent = variableAccent
            )
            Spacer(Modifier.height(14.dp))
            ReliabilityHero(
                selection = selection,
                onOpenRanking = onOpenRanking,
                variableAccent = variableAccent
            )
            Spacer(Modifier.height(10.dp))
            ReliabilitySummary(selection, variableAccent)

            SectionTitle(
                text = stringResource(R.string.bias_reliability_section_performance),
                accent = variableAccent
            )
            ReliabilityMetricsGrid(selection)
            Spacer(Modifier.height(8.dp))
            RecentTrendCard(selection.reliability)

            selection.multiModelReliability?.let { baseline ->
                Spacer(Modifier.height(8.dp))
                MultiModelComparisonCard(selection.reliability, baseline)
            }

            SectionTitle(
                text = stringResource(R.string.bias_reliability_section_history),
                accent = variableAccent
            )
            BiasSparkline(
                forecast = selection.dailyForecast,
                observation = selection.dailyObservation,
                direction = selection.bias.direction,
                yDomainMin = selection.yDomainMin,
                yDomainMax = selection.yDomainMax
            )

            selection.reliability.precipitation?.let { precipitation ->
                SectionTitle(
                    text = stringResource(R.string.bias_reliability_section_rain),
                    accent = variableAccent
                )
                PrecipitationDiagnosticsCard(precipitation)
            }

            SectionTitle(
                text = stringResource(R.string.bias_reliability_section_bias),
                accent = variableAccent
            )
            BiasReadingCard(selection)

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.bias_reliability_method_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f))
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun BiasSheetHeader(
    model: WeatherModel,
    variable: BiasVariable,
    windowDays: Int,
    accent: Color
) {
    val variableLabel = stringResource(sheetVariableLabelResId(variable))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 34.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.bias_reliability_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.bias_sheet_eyebrow,
                    model.displayName,
                    variableLabel,
                    windowDays
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag(TAG_MODEL_BIAS_HEADER)
            )
        }
    }
}

@Composable
private fun ReliabilityHero(
    selection: BiasSelection,
    onOpenRanking: ((WeatherModel, BiasVariable) -> Unit)?,
    variableAccent: Color
) {
    val reliability = selection.reliability
    val scoreAccent = reliabilityLevelAccent(reliability.level)
    val container = variableAccent.copy(alpha = 0.055f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    val openRankingDescription = stringResource(
        R.string.local_ranking_open_content_description
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(
            1.dp,
            variableAccent.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.bias_reliability_local_index),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = reliability.score.toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag(TAG_MODEL_BIAS_SCORE),
                            color = scoreAccent
                        )
                        Text(
                            text = stringResource(R.string.bias_reliability_score_suffix),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp, start = 4.dp)
                        )
                    }
                }
                AccentBadge(
                    text = stringResource(reliabilityLevelResId(reliability.level)),
                    accent = scoreAccent
                )
            }
            ScoreBar(progress = reliability.score / 100f, accent = scoreAccent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentBadge(
                    text = stringResource(sheetVariableLabelResId(selection.bias.variable)),
                    accent = variableAccent
                )
                selection.localRank?.let { rank ->
                    AccentBadge(
                        text = stringResource(
                            R.string.bias_reliability_rank,
                            rank.rank,
                            rank.modelCount
                        ),
                        accent = scoreAccent,
                        modifier = if (onOpenRanking != null) {
                            Modifier
                                .semantics {
                                    contentDescription = openRankingDescription
                                }
                                .clickable {
                                    onOpenRanking(selection.model, selection.bias.variable)
                                }
                        } else {
                            Modifier
                        }
                    )
                }
            }
            Text(
                text = stringResource(R.string.bias_reliability_score_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreBar(progress: Float, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
    }
}

@Composable
private fun AccentBadge(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReliabilitySummary(selection: BiasSelection, accent: Color) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
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
}

@Composable
private fun SectionTitle(text: String, accent: Color) {
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(accent.copy(alpha = 0.88f))
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ReliabilityMetricsGrid(selection: BiasSelection) {
    val reliability = selection.reliability
    val biasPalette = if (selection.bias.significance == BiasSignificance.NOT_SIGNIFICANT) {
        biasChipPalette(BiasDirection.NEUTRAL, pending = false)
    } else {
        biasChipPalette(selection.bias.direction, pending = false)
    }
    val maeAccent = metricAccentForError(
        value = reliability.meanAbsoluteError,
        scale = reliability.closeTolerance * 1.6
    )
    val closeDaysAccent = confidenceColor((reliability.withinToleranceRate * 100).roundToInt())
    val variabilityAccent = metricAccentForError(
        value = reliability.standardDeviation,
        scale = reliability.closeTolerance * 1.8
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_mae),
                value = formatMeasure(reliability.meanAbsoluteError, reliability.variable),
                supporting = stringResource(
                    R.string.bias_reliability_rmse_support,
                    formatMeasure(reliability.rootMeanSquareError, reliability.variable)
                ),
                accent = maeAccent,
                modifier = Modifier.weight(1f),
                emphasized = true
            )
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_mean_bias),
                value = formatBiasLabel(selection.bias),
                supporting = stringResource(biasDirectionSupportResId(selection.bias)),
                accent = biasPalette.foreground,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_close_days),
                value = "${(reliability.withinToleranceRate * 100).roundToInt()} %",
                supporting = stringResource(
                    R.string.bias_reliability_close_days_support,
                    formatMeasure(reliability.closeTolerance, reliability.variable)
                ),
                accent = closeDaysAccent,
                modifier = Modifier.weight(1f)
            )
            ReliabilityMetricCard(
                label = stringResource(R.string.bias_reliability_variability),
                value = formatMeasure(reliability.standardDeviation, reliability.variable),
                supporting = stringResource(R.string.bias_reliability_variability_support),
                accent = variabilityAccent,
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
    accent: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    val container = accent.copy(alpha = if (emphasized) 0.075f else 0.045f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(accent)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
                color = accent
            )
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
    val accent = trendAccent(reliability.trend)
    val container = accent.copy(alpha = 0.045f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bias_reliability_recent_trend),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(reliabilityTrendResId(reliability.trend)),
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
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
    val accent = when {
        abs(deltaPercent) < 5.0 -> MaterialTheme.colorScheme.secondary
        deltaPercent < 0.0 -> confidenceColor(90)
        else -> MaterialTheme.colorScheme.error
    }
    val container = accent.copy(alpha = 0.045f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bias_reliability_baseline_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = comparisonText,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
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
}

@Composable
private fun PrecipitationDiagnosticsCard(stats: PrecipitationReliability) {
    val detectionAccent = confidenceColor(((stats.hitRate ?: 0.0) * 100).roundToInt())
    val missAccent = confidenceColor(((1.0 - (stats.missedEventRate ?: 1.0)) * 100).roundToInt())
    val falseAlarmAccent = when {
        stats.falseAlarmCount == 0 -> confidenceColor(90)
        stats.falseAlarmCount <= 2 -> confidenceColor(60)
        else -> confidenceColor(20)
    }
    val wetDaysAccent = MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RainMetricCard(
                    label = stringResource(R.string.bias_reliability_rain_detection),
                    value = stats.hitRate?.let { "${(it * 100).roundToInt()} %" }
                        ?: stringResource(R.string.bias_reliability_not_available),
                    supporting = stringResource(
                        R.string.bias_reliability_rain_detection_support,
                        stats.hitCount,
                        stats.observedWetDays
                    ),
                    accent = detectionAccent,
                    modifier = Modifier.weight(1f)
                )
                RainMetricCard(
                    label = stringResource(R.string.bias_reliability_rain_false_alarms),
                    value = stats.falseAlarmCount.toString(),
                    supporting = stringResource(
                        R.string.bias_reliability_rain_false_alarms_support,
                        stats.falseAlarmCount,
                        stats.forecastWetDays
                    ),
                    accent = falseAlarmAccent,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RainMetricCard(
                    label = stringResource(R.string.bias_reliability_rain_misses),
                    value = stats.missedEventRate?.let { "${(it * 100).roundToInt()} %" }
                        ?: stringResource(R.string.bias_reliability_not_available),
                    supporting = stringResource(
                        R.string.bias_reliability_rain_misses_support,
                        stats.missedEventCount,
                        stats.observedWetDays
                    ),
                    accent = missAccent,
                    modifier = Modifier.weight(1f)
                )
                RainMetricCard(
                    label = stringResource(R.string.bias_reliability_rain_wet_days),
                    value = stats.observedWetDays.toString(),
                    supporting = stringResource(
                        R.string.bias_reliability_rain_volume_support,
                        stats.observedWetDays,
                        stats.forecastWetDays
                    ),
                    accent = wetDaysAccent,
                    modifier = Modifier.weight(1f)
                )
            }
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
private fun RainMetricCard(
    label: String,
    value: String,
    supporting: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val container = accent.copy(alpha = 0.045f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.13f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(accent)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BiasReadingCard(selection: BiasSelection) {
    val overAccent = MaterialTheme.colorScheme.error
    val underAccent = MaterialTheme.colorScheme.primary
    val closeAccent = confidenceColor((selection.reliability.closeRate * 100).roundToInt())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            SheetBiasTitle(selection.bias)
            Spacer(Modifier.height(10.dp))
            DirectionBalanceBar(
                underRate = selection.reliability.underToleranceUnderestimateRate.toFloat(),
                closeRate = selection.reliability.closeRate.toFloat(),
                overRate = selection.reliability.overToleranceOverestimateRate.toFloat(),
                underAccent = underAccent,
                closeAccent = closeAccent,
                overAccent = overAccent
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentBadge(
                    text = stringResource(
                        R.string.bias_reliability_balance_under_badge,
                        (selection.reliability.underToleranceUnderestimateRate * 100).roundToInt()
                    ),
                    accent = underAccent
                )
                AccentBadge(
                    text = stringResource(
                        R.string.bias_reliability_balance_close_badge,
                        (selection.reliability.withinToleranceRate * 100).roundToInt()
                    ),
                    accent = closeAccent
                )
                AccentBadge(
                    text = stringResource(
                        R.string.bias_reliability_balance_over_badge,
                        (selection.reliability.overToleranceOverestimateRate * 100).roundToInt()
                    ),
                    accent = overAccent
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.bias_reliability_direction_balance,
                    (selection.reliability.overToleranceOverestimateRate * 100).roundToInt(),
                    (selection.reliability.closeRate * 100).roundToInt(),
                    (selection.reliability.underToleranceUnderestimateRate * 100).roundToInt()
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

@Composable
private fun DirectionBalanceBar(
    underRate: Float,
    closeRate: Float,
    overRate: Float,
    underAccent: Color,
    closeAccent: Color,
    overAccent: Color
) {
    val total = (underRate + closeRate + overRate).coerceAtLeast(0.0001f)
    val underWeight = (underRate / total).coerceIn(0f, 1f)
    val closeWeight = (closeRate / total).coerceIn(0f, 1f)
    val overWeight = (overRate / total).coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (underWeight > 0f) {
            Box(
                modifier = Modifier
                    .weight(underWeight)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(underAccent)
            )
        }
        if (closeWeight > 0f) {
            Box(
                modifier = Modifier
                    .weight(closeWeight)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(closeAccent)
            )
        }
        if (overWeight > 0f) {
            Box(
                modifier = Modifier
                    .weight(overWeight)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(overAccent)
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

@Composable
private fun biasVariableAccent(variable: BiasVariable): Color = when (variable) {
    BiasVariable.TEMPERATURE -> temperatureMetricAccent()
    BiasVariable.PRECIPITATION -> precipitationMetricAccent()
    BiasVariable.WIND_SPEED -> windMetricAccent()
}

@Composable
private fun reliabilityLevelAccent(level: ReliabilityLevel): Color = when (level) {
    ReliabilityLevel.EXCELLENT -> confidenceColor(95)
    ReliabilityLevel.GOOD -> confidenceColor(82)
    ReliabilityLevel.FAIR -> confidenceColor(60)
    ReliabilityLevel.LIMITED -> confidenceColor(20)
}

@Composable
private fun trendAccent(trend: ReliabilityTrend): Color = when (trend) {
    ReliabilityTrend.IMPROVING -> confidenceColor(90)
    ReliabilityTrend.STABLE -> MaterialTheme.colorScheme.secondary
    ReliabilityTrend.DECLINING -> MaterialTheme.colorScheme.error
    ReliabilityTrend.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun metricAccentForError(value: Double, scale: Double): Color {
    val normalized = (1.0 - (value / scale).coerceIn(0.0, 1.0)) * 100.0
    return confidenceColor(normalized.roundToInt())
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
