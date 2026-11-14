package com.meteocompare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ConditionDecision
import com.meteocompare.app.domain.util.ConditionOrigin
import com.meteocompare.app.domain.util.ConditionReason
import com.meteocompare.app.domain.util.ConditionStatus
import com.meteocompare.app.domain.util.ConditionVisualTreatment
import com.meteocompare.app.domain.util.conditionVisualTreatment
import com.meteocompare.app.domain.util.visibleConditionAlternatives
import kotlin.math.roundToInt

/** All five diagnoses share the same icon language and a tap-to-explain dialog. */
@Composable
internal fun ConditionStatusIcon(
    condition: WeatherCondition?,
    decision: ConditionDecision?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    isNight: Boolean = false,
    tint: Color = Color.Unspecified
) {
    val treatment = conditionVisualTreatment(condition, decision)
    val alternatives = visibleConditionAlternatives(decision)
    val label = decision?.status?.let { stringResource(statusLabelRes(it)) }
        ?: if (condition == null) stringResource(R.string.condition_status_unavailable)
           else stringResource(R.string.condition_status_not_specified)
    val conditionText = condition?.let { stringResource(weatherConditionLabelRes(it)) }
    val talkback = listOfNotNull(conditionText, label).joinToString(" · ")
    var showDetails by remember(condition, decision) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clickable(role = Role.Button, onClick = { showDetails = true })
            .semantics { contentDescription = talkback },
        contentAlignment = Alignment.Center
    ) {
        when (treatment) {
            ConditionVisualTreatment.ALTERNATIVES -> {
                if (alternatives.size >= 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                        alternatives.forEach { alternative ->
                            WeatherIconDecorative(alternative, size = size * 0.50f, isNight = isNight)
                        }
                    }
                } else if (alternatives.size == 1) {
                    WeatherIconDecorative(alternatives[0], size = size * 0.78f, isNight = isNight)
                } else {
                    Text("≠", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
                StatusMarker("≠", Modifier.align(Alignment.BottomEnd))
            }
            ConditionVisualTreatment.NO_DATA -> Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                if (condition != null) {
                    WeatherIconDecorative(condition, size = size, tint = tint, isNight = isNight)
                } else {
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (treatment) {
                    ConditionVisualTreatment.ESTIMATE_MARKER -> StatusMarker("≈", Modifier.align(Alignment.BottomEnd))
                    ConditionVisualTreatment.PARTIAL_MARKER -> StatusMarker("◔", Modifier.align(Alignment.BottomEnd))
                    else -> Unit
                }
            }
        }
    }
    if (showDetails) {
        ConditionExplanationDialog(condition, decision, onDismiss = { showDetails = false })
    }
}

@Composable
private fun StatusMarker(value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(horizontal = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun ConditionExplanationDialog(
    condition: WeatherCondition?,
    decision: ConditionDecision?,
    onDismiss: () -> Unit
) {
    val status = decision?.status
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(status?.let(::statusLabelRes) ?: R.string.condition_status_not_specified)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(status?.let(::statusDescriptionRes)
                    ?: R.string.condition_status_not_specified_desc))
                condition?.let { Text(stringResource(R.string.condition_explanation_forecast,
                    stringResource(weatherConditionLabelRes(it)))) }
                visibleConditionAlternatives(decision).takeIf { it.isNotEmpty() }?.let { alternatives ->
                    Text(stringResource(R.string.condition_explanation_alternatives,
                        alternatives.map { stringResource(weatherConditionLabelRes(it)) }.joinToString(" / ")))
                }
                decision?.let { result ->
                    Text(stringResource(R.string.condition_explanation_origin,
                        stringResource(originLabelRes(result.origin))))
                    if (result.origin != ConditionOrigin.NONE) {
                        val resource = if (result.origin == ConditionOrigin.MODEL_CONSENSUS)
                            R.string.condition_explanation_model_coverage else R.string.condition_explanation_time_coverage
                        Text(stringResource(resource, (result.coverage * 100).roundToInt()))
                    }
                    result.agreement?.let { agreement ->
                        Text(stringResource(R.string.condition_explanation_agreement,
                            (agreement * 100).roundToInt()))
                    }
                    result.reasons.forEach { reason -> Text("• " + stringResource(reasonLabelRes(reason))) }
                    result.notablePhenomenon?.takeIf { it != condition }?.let { notable ->
                        Text(stringResource(R.string.condition_explanation_notable,
                            stringResource(weatherConditionLabelRes(notable))))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.condition_explanation_close)) } }
    )
}

@Composable
internal fun ConditionStatusLegend(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.condition_legend_summary),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.condition_legend_help)) }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.condition_legend_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ConditionStatus.entries.forEach { status ->
                        Column {
                            Text(stringResource(statusLabelRes(status)), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(statusDescriptionRes(status)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(stringResource(R.string.condition_legend_not_probability), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.condition_explanation_close)) } }
        )
    }
}

internal fun statusLabelRes(status: ConditionStatus): Int = when (status) {
    ConditionStatus.RELIABLE -> R.string.condition_status_reliable
    ConditionStatus.ESTIMATED -> R.string.condition_status_estimated
    ConditionStatus.AMBIGUOUS -> R.string.condition_status_ambiguous
    ConditionStatus.INCOMPLETE -> R.string.condition_status_incomplete
    ConditionStatus.UNAVAILABLE -> R.string.condition_status_unavailable
}
internal fun statusDescriptionRes(status: ConditionStatus): Int = when (status) {
    ConditionStatus.RELIABLE -> R.string.condition_status_reliable_desc
    ConditionStatus.ESTIMATED -> R.string.condition_status_estimated_desc
    ConditionStatus.AMBIGUOUS -> R.string.condition_status_ambiguous_desc
    ConditionStatus.INCOMPLETE -> R.string.condition_status_incomplete_desc
    ConditionStatus.UNAVAILABLE -> R.string.condition_status_unavailable_desc
}
internal fun originLabelRes(origin: ConditionOrigin): Int = when (origin) {
    ConditionOrigin.HOURLY_WMO -> R.string.condition_origin_wmo
    ConditionOrigin.SAME_MODEL_VARIABLES -> R.string.condition_origin_variables
    ConditionOrigin.HOURLY_DOMINANT -> R.string.condition_origin_hourly
    ConditionOrigin.DAILY_WMO_MAXIMUM -> R.string.condition_origin_daily_max
    ConditionOrigin.MODEL_CONSENSUS -> R.string.condition_origin_consensus
    ConditionOrigin.NONE -> R.string.condition_origin_none
}
internal fun reasonLabelRes(reason: ConditionReason): Int = when (reason) {
    ConditionReason.MISSING_DATA -> R.string.condition_reason_missing
    ConditionReason.INVALID_WMO -> R.string.condition_reason_invalid
    ConditionReason.INVALID_PHYSICAL_VALUE -> R.string.condition_reason_invalid
    ConditionReason.PARTIAL_CLOUD_LAYERS -> R.string.condition_reason_clouds
    ConditionReason.PRECEDING_HOUR_PRECIPITATION -> R.string.condition_reason_precip
    ConditionReason.PRECIPITATION_PHASE_UNCERTAIN -> R.string.condition_reason_phase
    ConditionReason.PARTIAL_PERIOD -> R.string.condition_reason_partial
    ConditionReason.IRREVERSIBLE_MAJORITY -> R.string.condition_reason_majority
    ConditionReason.REPRESENTATIVE_SAMPLES -> R.string.condition_reason_representative
    ConditionReason.NO_DOMINANT_CONDITION -> R.string.condition_reason_no_dominant
    ConditionReason.MISSING_SOLAR_WINDOW -> R.string.condition_reason_sun
    ConditionReason.MISSING_FOLLOWING_SUNRISE -> R.string.condition_reason_sun
    ConditionReason.MODEL_DISAGREEMENT -> R.string.condition_reason_disagreement
    ConditionReason.DAILY_WMO_IS_MAXIMUM -> R.string.condition_reason_daily_max
}
