package com.meteocompare.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.domain.util.ConditionStatus

/** One unobtrusive, contextual help entry near chronology and comparison controls. */
@Composable
internal fun ConditionStatusLegend(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val accessibleSummary = stringResource(R.string.condition_legend_summary)
    // This help is already inside the host timeline/table surface: no nested
    // cards or tinted legend pills. Keep one accessible tap target and two
    // deliberately grouped rows so the explanation remains easy to scan.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .testTag("condition_status_legend")
            .clickable(role = Role.Button, onClick = { open = true })
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                stringResource(R.string.condition_legend_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Text(
                stringResource(R.string.condition_legend_help),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = accessibleSummary },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("condition_legend_first_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegendChip(ConditionStatus.ESTIMATED, R.string.condition_legend_estimated_short,
                    Modifier.weight(1f))
                LegendChip(ConditionStatus.AMBIGUOUS, R.string.condition_legend_alternatives_short,
                    Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().testTag("condition_legend_second_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegendChip(ConditionStatus.INCOMPLETE, R.string.condition_legend_partial_short,
                    Modifier.weight(1f))
                LegendChip(ConditionStatus.UNAVAILABLE, R.string.condition_legend_unavailable_short,
                    Modifier.weight(1f))
            }
        }
    }
    if (open) ConditionLegendDialog(onDismiss = { open = false })
}

/** Flat labels: the accent stays on the symbol rather than on a nested card. */
@Composable
private fun LegendChip(status: ConditionStatus, labelRes: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(statusSymbol(status), color = statusAccent(status), fontSize = 21.sp,
            lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
private fun ConditionLegendDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ConditionInfoDialog(
        title = stringResource(R.string.condition_legend_title),
        testTag = "condition_legend_dialog",
        onDismiss = onDismiss
    ) {
        ConditionStatus.entries.forEach { status ->
            Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerLow) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = statusAccent(status).copy(alpha = 0.11f)) {
                        Box(Modifier.size(39.dp), contentAlignment = Alignment.Center) {
                            Text(statusSymbol(status), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = statusAccent(status))
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(statusLabelRes(status)), style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text(stringResource(statusDescriptionRes(status)), style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            stringResource(R.string.condition_legend_not_probability),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}
