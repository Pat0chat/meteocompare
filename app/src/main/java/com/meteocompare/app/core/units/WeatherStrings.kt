package com.meteocompare.app.core.units

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.meteocompare.app.R

/** Resource arguments enter in metric, just like the pure numeric formatters. */
@Composable
fun weatherStringResource(@StringRes id: Int, vararg args: Any): String =
    weatherString(LocalResources.current, LocalWeatherUnits.current, id, *args)

fun weatherString(resources: Resources, units: WeatherUnits, @StringRes id: Int, vararg args: Any): String {
    val locale = resources.configuration.locales[0]
    val staticArgs: Array<out Any>? = when (id) {
        R.string.temp_legend_above_normal, R.string.temp_legend_below_normal ->
            arrayOf(units.format(2.0, WeatherUnit.TEMPERATURE_COMPACT, if (units.imperial) 1 else 0, locale, delta = true))
        R.string.bias_reliability_method_note -> arrayOf(units.rain(0.5, locale))
        R.string.forecast_insight_detail_temperature_frost -> arrayOf(units.temp(0.0, locale = locale))
        R.string.var_wind_max, R.string.var_wind_gust_max, R.string.a11y_wind_max_label ->
            arrayOf(units.format(10.0, WeatherUnit.HEIGHT, 0, locale))
        R.string.marine_not_coastal -> arrayOf(units.format(50.0, WeatherUnit.DISTANCE, 0, locale))
        R.string.wind_table_legend -> arrayOf(units.format(10.0, WeatherUnit.HEIGHT, 0, locale), units.windUnit)
        else -> null
    }
    if (staticArgs != null) return resources.getString(id, *staticArgs)
    val spec = measureText(id) ?: return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
    val formatted = args.mapIndexed { index, arg ->
        if (index in spec.indices) {
            units.value((arg as? Number)?.toDouble(), spec.unit, spec.digits, locale, spec.delta)
        } else arg
    }
    val label = if (spec.spoken) spokenUnit(resources, units, spec.unit) else units.label(spec.unit)
    return resources.getString(id, *(formatted + label).toTypedArray())
}

internal fun spokenUnit(resources: Resources, units: WeatherUnits, metricUnit: WeatherUnit): String {
    return resources.getString(when (metricUnit) {
        WeatherUnit.TEMPERATURE, WeatherUnit.TEMPERATURE_COMPACT -> if (units.imperial) R.string.unit_degrees_fahrenheit else R.string.unit_degrees_celsius
        WeatherUnit.WIND_SPEED -> if (units.imperial) R.string.unit_miles_per_hour else R.string.a11y_kmh_unit
        WeatherUnit.PRECIPITATION -> if (units.imperial) R.string.unit_inches else R.string.unit_millimetres
        else -> return units.label(metricUnit)
    })
}

private data class MeasureText(val unit: WeatherUnit, val indices: IntArray, val digits: Int,
    val delta: Boolean = false, val spoken: Boolean = false)

private fun measureText(@StringRes id: Int): MeasureText? = when (id) {
    R.string.timeline_precip_amount -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0), 1, false, false)
    R.string.timeline_precip_amount_if_rain -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0), 1, false, false)
    R.string.forecast_insight_metric_precipitation -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0), 1, false, false)
    R.string.metric_summary_max_model_precip -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0), 1, false, false)
    R.string.forecast_insight_metric_precipitation_range -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0, 1), 1, false, false)
    R.string.confidence_precip_all_rain -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0, 1), 0, false, false)
    R.string.forecast_insight_metric_wind -> MeasureText(WeatherUnit.WIND_SPEED, intArrayOf(0), 0, false, false)
    R.string.timeline_wind_gust -> MeasureText(WeatherUnit.WIND_SPEED, intArrayOf(0), 0, false, false)
    R.string.forecast_insight_metric_wind_range -> MeasureText(WeatherUnit.WIND_SPEED, intArrayOf(0, 1), 0, false, false)
    R.string.forecast_insight_metric_temperature_range -> MeasureText(WeatherUnit.TEMPERATURE_COMPACT, intArrayOf(0, 1), 0, false, false)
    R.string.forecast_insight_metric_temperature_scenarios -> MeasureText(WeatherUnit.TEMPERATURE_COMPACT, intArrayOf(0, 1), 0, false, false)
    R.string.home_temperature_trend_rising -> MeasureText(WeatherUnit.TEMPERATURE_COMPACT, intArrayOf(0), 0, false, false)
    R.string.home_temperature_trend_falling -> MeasureText(WeatherUnit.TEMPERATURE_COMPACT, intArrayOf(0), 0, false, false)
    R.string.home_temperature_trend_stable -> MeasureText(WeatherUnit.TEMPERATURE_COMPACT, intArrayOf(0), 0, false, false)
    R.string.marine_distance -> MeasureText(WeatherUnit.DISTANCE, intArrayOf(0), 1, false, false)
    R.string.a11y_now_temp -> MeasureText(WeatherUnit.TEMPERATURE, intArrayOf(0), 0, false, true)
    R.string.a11y_hourly_template -> MeasureText(WeatherUnit.TEMPERATURE, intArrayOf(1, 2), 0, false, true)
    R.string.a11y_rain_no_convergence -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0, 1), 1, false, true)
    R.string.a11y_rain -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0, 1), 1, false, true)
    R.string.a11y_models_divided -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(2, 3), 1, false, true)
    R.string.mini_forecast_a11y_no_rain -> MeasureText(WeatherUnit.TEMPERATURE, intArrayOf(0, 1), 0, spoken = true)
    R.string.mini_forecast_a11y_with_rain -> MeasureText(WeatherUnit.TEMPERATURE, intArrayOf(0, 1), 0, spoken = true)
    R.string.widget_value_temperature_range -> MeasureText(WeatherUnit.TEMPERATURE_COMPACT, intArrayOf(0, 1), 0)
    R.string.widget_value_precip_range -> MeasureText(WeatherUnit.PRECIPITATION, intArrayOf(0, 1), 1)
    R.string.widget_value_wind_range -> MeasureText(WeatherUnit.WIND_SPEED, intArrayOf(0, 1), 0)
    else -> null
}
