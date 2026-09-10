package com.meteocompare.app.ui.citydetail

import androidx.annotation.StringRes
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition

/** Ressources de titre partagées par l'application et ses widgets. */
@StringRes
internal fun likelyPrecipitationTitleRes(insight: ForecastInsight): Int =
    when (insight.targetCondition) {
        WeatherCondition.THUNDERSTORM -> R.string.forecast_insight_title_weather_thunderstorm
        WeatherCondition.FREEZING_RAIN -> R.string.forecast_insight_title_weather_freezing_rain
        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> R.string.forecast_insight_title_weather_snow
        else -> if (insight.isStrengtheningRainSignal) {
            R.string.forecast_insight_title_rain_strengthening
        } else {
            R.string.forecast_insight_title_rain_likely
        }
    }

@StringRes
internal fun disagreementTitleRes(reasons: Set<DivergenceReason>): Int =
    when (primaryDivergenceReason(reasons)) {
        DivergenceReason.PRECIPITATION -> R.string.forecast_insight_title_disagreement_rain
        DivergenceReason.WIND -> R.string.forecast_insight_title_disagreement_wind
        DivergenceReason.TEMPERATURE -> R.string.forecast_insight_title_disagreement_temperature
        DivergenceReason.CONDITION -> R.string.forecast_insight_title_disagreement_condition
        null -> R.string.forecast_insight_title_disagreement
    }

@StringRes
internal fun windEventTitleRes(insight: ForecastInsight): Int {
    val baseline = insight.value
    val target = insight.secondaryValue
    val delta = if (baseline != null && target != null) target - baseline else 0
    return when {
        (target ?: 0) >= 60 -> R.string.forecast_insight_title_wind_very_strong
        delta >= 15 -> R.string.forecast_insight_title_wind_rising
        else -> R.string.forecast_insight_title_wind_strong
    }
}

@StringRes
internal fun temperatureChangeTitleRes(insight: ForecastInsight): Int {
    val target = insight.targetValue
    val delta = insight.value ?: 0
    val uncertain = DivergenceReason.TEMPERATURE in insight.divergenceReasons
    return when {
        target != null && target <= 0 -> R.string.forecast_insight_title_temperature_frost
        target != null && target >= 35 -> R.string.forecast_insight_title_temperature_extreme_heat
        target != null && target >= 30 -> R.string.forecast_insight_title_temperature_heat
        uncertain -> R.string.forecast_insight_title_temperature_uncertain
        delta < 0 -> R.string.forecast_insight_title_temperature_unusual_cooling
        else -> R.string.forecast_insight_title_temperature_unusual_warming
    }
}

@StringRes
internal fun weatherChangeTitleRes(insight: ForecastInsight): Int {
    val reference = insight.referenceCondition
    val target = insight.targetCondition
    val improves = reference != null && target != null && target.severityRank < reference.severityRank
    return when (target) {
        WeatherCondition.FOG -> R.string.forecast_insight_title_weather_fog
        WeatherCondition.THUNDERSTORM -> R.string.forecast_insight_title_weather_thunderstorm
        WeatherCondition.FREEZING_RAIN -> R.string.forecast_insight_title_weather_freezing_rain
        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> R.string.forecast_insight_title_weather_snow
        else -> when {
            improves -> R.string.forecast_insight_title_weather_improving
            insight.level == ForecastInsightLevel.INFO -> R.string.forecast_insight_title_weather_change
            else -> R.string.forecast_insight_title_weather_worsening
        }
    }
}

internal fun primaryDivergenceReason(reasons: Set<DivergenceReason>): DivergenceReason? =
    listOf(
        DivergenceReason.PRECIPITATION,
        DivergenceReason.WIND,
        DivergenceReason.TEMPERATURE,
        DivergenceReason.CONDITION
    ).firstOrNull { it in reasons }
