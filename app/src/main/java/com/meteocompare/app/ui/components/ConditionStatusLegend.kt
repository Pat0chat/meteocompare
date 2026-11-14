package com.meteocompare.app.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.util.ConditionStatus

/** One unobtrusive, contextual help entry near chronology and comparison controls. */
@Composable
internal fun ConditionStatusLegend(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().testTag("condition_status_legend"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.condition_legend_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.condition_legend_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(R.string.condition_legend_help),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    if (open) ConditionLegendDialog(onDismiss = { open = false })
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
