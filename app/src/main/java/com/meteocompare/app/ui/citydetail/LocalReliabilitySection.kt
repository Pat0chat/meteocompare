package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.ui.components.CollapsibleSectionHeader
import com.meteocompare.app.ui.components.ModernTextTabs
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent

internal const val TAG_LOCAL_RELIABILITY_CARD = "local-reliability-card"
internal const val TAG_LOCAL_RELIABILITY_HEADER = "local-reliability-header"
internal const val TAG_LOCAL_RELIABILITY_DETAILS = "local-reliability-details"

/**
 * Bloc unique réunissant accord inter-modèles et fiabilité historique locale.
 * Le résumé des gagnants reste visible replié ; le graphique n'apparaît qu'en
 * état déplié pour préserver une lecture compacte de la fiche.
 */
@Composable
internal fun LocalReliabilitySection(
    overallConfidencePercent: Int?,
    modelCount: Int,
    rankings: LocalModelRankings,
    tempBands: List<HourlyConfidenceBand>,
    precipBands: List<HourlyConfidenceBand>,
    windBands: List<HourlyConfidenceBand>,
    timezone: String?,
    normals: Map<Int, DayNormals>?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenRanking: (BiasVariable) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableMetrics = remember(tempBands, precipBands, windBands, rankings) {
        availableReliabilityMetrics(rankings, tempBands, precipBands, windBands)
    }
    if (availableMetrics.isEmpty()) return

    var savedMetric by rememberSaveable(stateSaver = ConfidenceMetric.Saver) {
        mutableStateOf(availableMetrics.first())
    }
    val metric = savedMetric.takeIf { it in availableMetrics } ?: availableMetrics.first()
    val activeVariable = metric.toBiasVariable()
    val activeRanking = rankings.forVariable(activeVariable)
    val activeAccent = metricAccent(metric)
    val bands = when (metric) {
        ConfidenceMetric.TEMPERATURE -> tempBands
        ConfidenceMetric.PRECIPITATION -> precipBands
        ConfidenceMetric.WIND -> windBands
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(TAG_LOCAL_RELIABILITY_CARD),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 7.dp)) {
            CollapsibleSectionHeader(
                text = stringResource(R.string.local_reliability_title),
                subtitle = reliabilitySubtitle(overallConfidencePercent, modelCount),
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .testTag(TAG_LOCAL_RELIABILITY_HEADER),
                trailingContent = {
                    if (rankings.hasAnyRanking) {
                        TextButton(onClick = { onOpenRanking(rankingVariableFor(activeVariable, rankings)) }) {
                            Text(stringResource(R.string.local_ranking_view_all))
                        }
                    }
                }
            )

            if (rankings.hasAnyRanking) {
                ReliabilityWinnersSummary(
                    rankings = rankings,
                    onOpenRanking = onOpenRanking,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            if (expanded) {
                Column(modifier = Modifier.testTag(TAG_LOCAL_RELIABILITY_DETAILS)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )

                    ReliabilityMetricTabs(
                        options = availableMetrics,
                        selected = metric,
                        onSelected = { savedMetric = it },
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    if (bands.size >= 2) {
                        HourlyConfidenceChart(
                            bands = bands,
                            metric = metric,
                            timezone = timezone,
                            normals = normals
                        )
                    }

                    activeRanking.winner?.let { winner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRanking(activeVariable) }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(activeAccent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = metricIcon(metric),
                                    contentDescription = null,
                                    tint = activeAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.local_reliability_best_model),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = winner.model.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            ReliabilityScore(score = winner.reliability.score)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReliabilityWinnersSummary(
    rankings: LocalModelRankings,
    onOpenRanking: (BiasVariable) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReliabilityWinnerCompact(
            icon = Icons.Outlined.Thermostat,
            accent = temperatureMetricAccent(),
            winner = rankings.temperature.winner,
            modifier = Modifier.weight(1f),
            onClick = { onOpenRanking(BiasVariable.TEMPERATURE) }
        )
        ReliabilityWinnerCompact(
            icon = Icons.Outlined.WaterDrop,
            accent = precipitationMetricAccent(),
            winner = rankings.precipitation.winner,
            modifier = Modifier.weight(1f),
            onClick = { onOpenRanking(BiasVariable.PRECIPITATION) }
        )
        ReliabilityWinnerCompact(
            icon = Icons.Outlined.Air,
            accent = windMetricAccent(),
            winner = rankings.wind.winner,
            modifier = Modifier.weight(1f),
            onClick = { onOpenRanking(BiasVariable.WIND_SPEED) }
        )
    }
}

@Composable
private fun ReliabilityWinnerCompact(
    icon: ImageVector,
    accent: Color,
    winner: LocalModelRankingEntry?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.07f))
            .clickable(enabled = winner != null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = winner?.model?.displayName ?: "—",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReliabilityMetricTabs(
    options: List<ConfidenceMetric>,
    selected: ConfidenceMetric,
    onSelected: (ConfidenceMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    ModernTextTabs(
        options = options,
        selected = selected,
        onSelected = onSelected,
        label = { metric ->
            stringResource(
                when (metric) {
                    ConfidenceMetric.TEMPERATURE -> R.string.metric_temperature
                    ConfidenceMetric.PRECIPITATION -> R.string.metric_precipitation
                    ConfidenceMetric.WIND -> R.string.metric_wind
                }
            )
        },
        accent = metricAccent(selected),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ReliabilityScore(score: Int) {
    val accent = confidenceColor(score)
    Box(
        modifier = Modifier
            .background(accent.copy(alpha = 0.13f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun reliabilitySubtitle(percent: Int?, modelCount: Int): String =
    if (percent != null) {
        stringResource(R.string.local_reliability_subtitle_with_confidence, percent, modelCount)
    } else {
        stringResource(R.string.local_reliability_subtitle_models, modelCount)
    }

private fun ConfidenceMetric.toBiasVariable(): BiasVariable = when (this) {
    ConfidenceMetric.TEMPERATURE -> BiasVariable.TEMPERATURE
    ConfidenceMetric.PRECIPITATION -> BiasVariable.PRECIPITATION
    ConfidenceMetric.WIND -> BiasVariable.WIND_SPEED
}

@Composable
private fun metricAccent(metric: ConfidenceMetric): Color = when (metric) {
    ConfidenceMetric.TEMPERATURE -> temperatureMetricAccent()
    ConfidenceMetric.PRECIPITATION -> precipitationMetricAccent()
    ConfidenceMetric.WIND -> windMetricAccent()
}

private fun metricIcon(metric: ConfidenceMetric): ImageVector = when (metric) {
    ConfidenceMetric.TEMPERATURE -> Icons.Outlined.Thermostat
    ConfidenceMetric.PRECIPITATION -> Icons.Outlined.WaterDrop
    ConfidenceMetric.WIND -> Icons.Outlined.Air
}
