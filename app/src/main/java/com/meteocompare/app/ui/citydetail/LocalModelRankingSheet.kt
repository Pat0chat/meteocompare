package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelReliability
import com.meteocompare.app.domain.model.ReliabilityLevel
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.components.ModernStateSelector
import com.meteocompare.app.ui.theme.confidenceColor
import kotlin.math.abs

internal const val TAG_LOCAL_RANKING_CARD = "local-ranking-card"
internal const val TAG_LOCAL_RANKING_SHEET = "local-ranking-sheet"

/**
 * Carte compacte placée dans la fiche ville. Elle expose immédiatement le
 * meilleur modèle local pour température, pluie et vent, sans obliger à ouvrir
 * un tableau détaillé. Chaque ligne ouvre la sheet sur la variable concernée.
 */
@Composable
internal fun LocalModelRankingSummaryCard(
    rankings: LocalModelRankings,
    onOpenRanking: (BiasVariable) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!rankings.hasAnyRanking) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TAG_LOCAL_RANKING_CARD),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            RankingWinnerRow(
                ranking = rankings.temperature,
                accent = MaterialTheme.colorScheme.error,
                onClick = { onOpenRanking(BiasVariable.TEMPERATURE) }
            )
            RankingWinnerRow(
                ranking = rankings.precipitation,
                accent = MaterialTheme.colorScheme.primary,
                onClick = { onOpenRanking(BiasVariable.PRECIPITATION) }
            )
            RankingWinnerRow(
                ranking = rankings.wind,
                accent = MaterialTheme.colorScheme.tertiary,
                onClick = { onOpenRanking(BiasVariable.WIND_SPEED) }
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.local_ranking_summary_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onOpenRanking(rankings.firstAvailableVariable) }) {
                    Text(stringResource(R.string.local_ranking_view_all))
                }
            }
        }
    }
}

@Composable
private fun RankingWinnerRow(
    ranking: LocalVariableRanking,
    accent: Color,
    onClick: () -> Unit
) {
    val winner = ranking.winner
    val enabled = winner != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = variableSymbol(ranking.variable),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(variableLabelResId(ranking.variable)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = winner?.model?.displayName
                    ?: stringResource(R.string.local_ranking_not_available),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (winner != null) {
            RankingScoreBadge(score = winner.reliability.score)
        }
    }
}

/** Grand sheet de classement local, avec un onglet par variable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalModelRankingSheet(
    rankings: LocalModelRankings,
    cityLabel: String,
    initialVariable: BiasVariable,
    highlightedModel: WeatherModel?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedVariableName by rememberSaveable(initialVariable.name) {
        mutableStateOf(initialVariable.name)
    }
    val selectedVariable = enumValues<BiasVariable>()
        .firstOrNull { it.name == selectedVariableName }
        ?: initialVariable
    val ranking = rankings.forVariable(selectedVariable)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_LOCAL_RANKING_SHEET)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.local_ranking_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = cityLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                RankingVariableTabs(
                    selected = selectedVariable,
                    onSelected = { selectedVariableName = it.name }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.local_ranking_sheet_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
            }

            if (ranking.entries.isEmpty()) {
                RankingEmptyState()
            } else {
                ranking.entries.forEach { entry ->
                    RankingModelRow(
                        entry = entry,
                        variable = selectedVariable,
                        highlighted = selectedVariable == initialVariable && entry.model == highlightedModel
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.local_ranking_method_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun RankingVariableTabs(
    selected: BiasVariable,
    onSelected: (BiasVariable) -> Unit
) {
    val variables = remember {
        listOf(
            BiasVariable.TEMPERATURE,
            BiasVariable.PRECIPITATION,
            BiasVariable.WIND_SPEED
        )
    }
    ModernStateSelector(
        options = variables,
        selected = selected,
        onSelected = onSelected,
        label = { variable -> stringResource(variableShortLabelResId(variable)) },
        accent = rankingVariableAccent(selected),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RankingModelRow(
    entry: LocalModelRankingEntry,
    variable: BiasVariable,
    highlighted: Boolean
) {
    val accent = confidenceColor(entry.reliability.score)
    val container = accent.copy(alpha = if (highlighted) 0.16f else 0.07f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    val borderColor = accent.copy(alpha = if (highlighted) 0.62f else 0.20f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankingPositionBadge(rank = entry.rank, accent = accent)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (highlighted) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = stringResource(R.string.local_ranking_selected_model),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rankingSupportText(entry.reliability, variable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                RankingScoreBadge(score = entry.reliability.score)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(reliabilityLevelResId(entry.reliability.level)),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun RankingPositionBadge(rank: Int, accent: Color) {
    val isPodium = rank <= 3
    Box(
        modifier = Modifier
            .size(if (isPodium) 40.dp else 38.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(accent.copy(alpha = if (isPodium) 0.20f else 0.12f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = if (isPodium) 0.42f else 0.18f),
                shape = RoundedCornerShape(13.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RankingScoreBadge(score: Int) {
    val accent = confidenceColor(score)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RankingEmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.local_ranking_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(R.string.local_ranking_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun rankingSupportText(
    reliability: ModelReliability,
    variable: BiasVariable
): String {
    return if (variable == BiasVariable.PRECIPITATION) {
        stringResource(
            R.string.local_ranking_rain_support,
            formatRankingMeasure(reliability.meanAbsoluteError, variable),
            reliability.precipitation?.falseAlarmCount ?: 0
        )
    } else {
        stringResource(
            R.string.local_ranking_standard_support,
            formatRankingMeasure(reliability.meanAbsoluteError, variable),
            formatRankingSignedMeasure(reliability.meanBias, variable)
        )
    }
}

private fun formatRankingMeasure(value: Double, variable: BiasVariable): String =
    "%.1f".format(value) + rankingUnit(variable)

private fun formatRankingSignedMeasure(value: Double, variable: BiasVariable): String {
    val sign = when {
        value > 0.0 -> "+"
        value < 0.0 -> "−"
        else -> "±"
    }
    return sign + "%.1f".format(abs(value)) + rankingUnit(variable)
}

private fun rankingUnit(variable: BiasVariable): String = when (variable) {
    BiasVariable.TEMPERATURE -> "°"
    BiasVariable.PRECIPITATION -> " mm"
    BiasVariable.WIND_SPEED -> " km/h"
}

@Composable
private fun rankingVariableAccent(variable: BiasVariable): Color = when (variable) {
    BiasVariable.TEMPERATURE -> MaterialTheme.colorScheme.error
    BiasVariable.PRECIPITATION -> MaterialTheme.colorScheme.primary
    BiasVariable.WIND_SPEED -> MaterialTheme.colorScheme.tertiary
}

private fun variableSymbol(variable: BiasVariable): String = when (variable) {
    BiasVariable.TEMPERATURE -> "T"
    BiasVariable.PRECIPITATION -> "P"
    BiasVariable.WIND_SPEED -> "V"
}

private fun variableLabelResId(variable: BiasVariable): Int = when (variable) {
    BiasVariable.TEMPERATURE -> R.string.local_ranking_temperature
    BiasVariable.PRECIPITATION -> R.string.local_ranking_precipitation
    BiasVariable.WIND_SPEED -> R.string.local_ranking_wind
}

private fun variableShortLabelResId(variable: BiasVariable): Int = when (variable) {
    BiasVariable.TEMPERATURE -> R.string.local_ranking_temperature_short
    BiasVariable.PRECIPITATION -> R.string.local_ranking_precipitation_short
    BiasVariable.WIND_SPEED -> R.string.local_ranking_wind_short
}

private fun reliabilityLevelResId(level: ReliabilityLevel): Int = when (level) {
    ReliabilityLevel.EXCELLENT -> R.string.bias_reliability_level_excellent
    ReliabilityLevel.GOOD -> R.string.bias_reliability_level_good
    ReliabilityLevel.FAIR -> R.string.bias_reliability_level_fair
    ReliabilityLevel.LIMITED -> R.string.bias_reliability_level_limited
}
