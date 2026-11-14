package com.meteocompare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ConditionDecision
import com.meteocompare.app.domain.util.ConditionOrigin
import com.meteocompare.app.domain.util.ConditionStatus
import com.meteocompare.app.domain.util.visibleConditionAlternatives
import kotlin.math.roundToInt

/** One themed, scrollable explanation shared by Home, chronologies, tables and charts. */
@Composable
internal fun ConditionExplanationDialog(
    condition: WeatherCondition?,
    decision: ConditionDecision?,
    isNight: Boolean,
    onDismiss: () -> Unit
) {
    val status = decision?.status
    val scheme = MaterialTheme.colorScheme
    ConditionInfoDialog(
        title = stringResource(R.string.condition_detail_title),
        testTag = "condition_explanation_dialog",
        closeButtonTag = "condition_dialog_close",
        onDismiss = onDismiss
    ) {
        ConditionHero(condition, status, isNight)
        Text(
            stringResource(status?.let(::statusDescriptionRes)
                ?: R.string.condition_status_not_specified_desc),
            modifier = Modifier.padding(horizontal = 4.dp),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        val alternatives = visibleConditionAlternatives(decision)
        if (alternatives.isNotEmpty()) {
            DiagnosticSectionTitle(stringResource(R.string.condition_explanation_alternatives_title))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                alternatives.forEach { alternative ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WeatherIconDecorative(alternative, size = 38.dp, isNight = isNight)
                            Text(
                                stringResource(weatherConditionLabelRes(alternative)),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
        if (decision != null) {
            DiagnosticSectionTitle(stringResource(R.string.condition_explanation_data_title))
            Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerLow) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailField(
                        stringResource(R.string.condition_explanation_source_title),
                        stringResource(originLabelRes(decision.origin))
                    )
                    if (decision.origin != ConditionOrigin.NONE) {
                        val resource = if (decision.origin == ConditionOrigin.MODEL_CONSENSUS)
                            R.string.condition_explanation_model_coverage else R.string.condition_explanation_time_coverage
                        DiagnosticProgress(
                            label = stringResource(resource, (decision.coverage * 100).roundToInt()),
                            progress = decision.coverage.toFloat(),
                            tag = "condition_coverage"
                        )
                    }
                    decision.agreement?.let { agreement ->
                        DiagnosticProgress(
                            label = stringResource(R.string.condition_explanation_agreement,
                                (agreement * 100).roundToInt()),
                            progress = agreement.toFloat(),
                            tag = "condition_agreement"
                        )
                    }
                }
            }
            if (decision.reasons.isNotEmpty()) {
                DiagnosticSectionTitle(stringResource(R.string.condition_explanation_reasons_title))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    decision.reasons.sortedBy { it.ordinal }.forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(Modifier.padding(top = 7.dp).size(6.dp)
                                .background(scheme.tertiary, CircleShape))
                            Text(
                                stringResource(reasonLabelRes(reason)),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            decision.notablePhenomenon?.takeIf { it != condition }?.let { notable ->
                Surface(shape = RoundedCornerShape(14.dp), color = scheme.tertiaryContainer.copy(alpha = 0.56f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WeatherIconDecorative(notable, size = 30.dp, isNight = isNight)
                        Text(
                            stringResource(R.string.condition_explanation_notable,
                                stringResource(weatherConditionLabelRes(notable))),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ConditionHero(
    condition: WeatherCondition?,
    status: ConditionStatus?,
    isNight: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth().background(
            Brush.linearGradient(
                listOf(scheme.primaryContainer.copy(alpha = 0.70f), scheme.secondaryContainer.copy(alpha = 0.55f))
            ),
            RoundedCornerShape(20.dp)
        ).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = scheme.surface.copy(alpha = 0.78f)) {
                Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                    if (condition != null) {
                        WeatherIconDecorative(condition, size = 60.dp, isNight = isNight)
                    } else {
                        Text(
                            status?.let(::statusSymbol) ?: "—",
                            style = MaterialTheme.typography.headlineLarge,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    condition?.let { stringResource(weatherConditionLabelRes(it)) }
                        ?: stringResource(R.string.condition_explanation_no_condition),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 3
                )
                if (status != null) StatusPill(status)
                else Text(
                    stringResource(R.string.condition_status_not_specified),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DiagnosticProgress(label: String, progress: Float, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).testTag(tag),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}
