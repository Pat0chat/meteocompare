package com.meteocompare.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

/** Shared visual state for forecast icons; all surfaces open the same explanation. */
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
    val alternativeText = if (treatment == ConditionVisualTreatment.ALTERNATIVES && alternatives.isNotEmpty()) {
        val first = stringResource(weatherConditionLabelRes(alternatives[0]))
        val second = if (alternatives.size >= 2) stringResource(weatherConditionLabelRes(alternatives[1]))
                     else null
        listOfNotNull(first, second).joinToString(" / ")
    } else null
    val talkback = listOfNotNull(conditionText ?: alternativeText, label).joinToString(" · ")
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
                    // Diagonal overlap gives each condition 76% of the full icon
                    // size instead of two illegible 50%-width icons side by side.
                    Box(modifier = Modifier.size(size)) {
                        WeatherIconDecorative(alternatives[0],
                            modifier = Modifier.align(Alignment.TopStart).testTag("condition_alternative_primary"),
                            size = size * 0.76f, isNight = isNight)
                        WeatherIconDecorative(alternatives[1],
                            modifier = Modifier.align(Alignment.BottomEnd).testTag("condition_alternative_secondary"),
                            size = size * 0.76f, isNight = isNight)
                    }
                } else if (alternatives.size == 1) {
                    WeatherIconDecorative(alternatives[0], size = size * 0.78f, isNight = isNight)
                } else {
                    Text("≠", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
                StatusMarker("≠", Modifier.align(Alignment.TopEnd))
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
        ConditionExplanationDialog(condition, decision, isNight, onDismiss = { showDetails = false })
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

internal fun statusSymbol(status: ConditionStatus): String = when (status) {
    ConditionStatus.RELIABLE -> "✓"
    ConditionStatus.ESTIMATED -> "≈"
    ConditionStatus.AMBIGUOUS -> "≠"
    ConditionStatus.INCOMPLETE -> "◔"
    ConditionStatus.UNAVAILABLE -> "—"
}

@Composable
internal fun statusAccent(status: ConditionStatus): Color = when (status) {
    ConditionStatus.RELIABLE -> MaterialTheme.colorScheme.primary
    ConditionStatus.ESTIMATED -> MaterialTheme.colorScheme.secondary
    ConditionStatus.AMBIGUOUS, ConditionStatus.INCOMPLETE -> MaterialTheme.colorScheme.tertiary
    ConditionStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun StatusPill(status: ConditionStatus) {
    val accent = statusAccent(status)
    Surface(shape = CircleShape, color = accent.copy(alpha = 0.12f), contentColor = accent) {
        Text(
            stringResource(statusLabelRes(status)),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
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
