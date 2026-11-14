package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.WeatherCondition

/**
 * One display contract across the hourly table, daily timeline and graphic view.
 * This is a visual treatment, NOT a forecast probability or an extra forecast.
 */
internal enum class ConditionVisualTreatment {
    NORMAL, ESTIMATE_MARKER, ALTERNATIVES, PARTIAL_MARKER, NO_DATA
}

internal fun conditionVisualTreatment(
    condition: WeatherCondition?,
    decision: ConditionDecision?
): ConditionVisualTreatment = when (decision?.status) {
    ConditionStatus.RELIABLE -> if (condition == null) ConditionVisualTreatment.NO_DATA else ConditionVisualTreatment.NORMAL
    ConditionStatus.ESTIMATED -> if (condition == null) ConditionVisualTreatment.NO_DATA else ConditionVisualTreatment.ESTIMATE_MARKER
    ConditionStatus.INCOMPLETE -> ConditionVisualTreatment.PARTIAL_MARKER
    ConditionStatus.AMBIGUOUS -> ConditionVisualTreatment.ALTERNATIVES
    ConditionStatus.UNAVAILABLE -> ConditionVisualTreatment.NO_DATA
    null -> if (condition == null) ConditionVisualTreatment.NO_DATA else ConditionVisualTreatment.NORMAL
}

/** Use distinct, genuine alternatives; never repeat the same icon twice. */
internal fun visibleConditionAlternatives(decision: ConditionDecision?): List<WeatherCondition> =
    decision?.alternatives.orEmpty().filter { it != WeatherCondition.UNKNOWN }.distinct().take(2)
