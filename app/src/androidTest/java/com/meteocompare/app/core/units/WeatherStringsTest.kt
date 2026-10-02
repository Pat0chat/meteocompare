package com.meteocompare.app.core.units

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.ui.accessibility.A11yFormatter
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WeatherStringsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val imperial = WeatherUnits(UnitSystem.IMPERIAL)

    @Test fun every_unit_template_accepts_metric_inputs_in_both_systems_and_all_locales() {
        val cases: List<Pair<Int, Array<out Any>>> = listOf(
            R.string.timeline_precip_amount to arrayOf<Any>(25.4),
            R.string.timeline_precip_amount_if_rain to arrayOf<Any>(25.4),
            R.string.forecast_insight_metric_precipitation to arrayOf<Any>(25.4),
            R.string.metric_summary_max_model_precip to arrayOf<Any>(25.4),
            R.string.forecast_insight_metric_precipitation_range to arrayOf<Any>(0.05, 25.4),
            R.string.confidence_precip_all_rain to arrayOf<Any>(0.05, 25.4),
            R.string.forecast_insight_metric_wind to arrayOf<Any>(16.09344),
            R.string.timeline_wind_gust to arrayOf<Any>(16.09344),
            R.string.forecast_insight_metric_wind_range to arrayOf<Any>(16.09344, 32.18688),
            R.string.forecast_insight_metric_temperature_range to arrayOf<Any>(-40.0, 0.4),
            R.string.forecast_insight_metric_temperature_scenarios to arrayOf<Any>(-40.0, 0.4),
            R.string.home_temperature_trend_rising to arrayOf<Any>(0.4),
            R.string.home_temperature_trend_falling to arrayOf<Any>(0.4),
            R.string.home_temperature_trend_stable to arrayOf<Any>(0.4),
            R.string.marine_distance to arrayOf<Any>(16.09344),
            R.string.a11y_now_temp to arrayOf<Any>(0.4),
            R.string.a11y_hourly_template to arrayOf<Any>(3, 0.4, 20.0, "stable", 85, 72),
            R.string.a11y_rain_no_convergence to arrayOf<Any>(0.05, 25.4),
            R.string.a11y_rain to arrayOf<Any>(0.05, 25.4, 85),
            R.string.a11y_models_divided to arrayOf<Any>(3, 5, 0.05, 25.4),
            R.string.mini_forecast_a11y_no_rain to arrayOf<Any>(-40.0, 0.4),
            R.string.mini_forecast_a11y_with_rain to arrayOf<Any>(-40.0, 0.4, 4),
            R.string.widget_value_temperature_range to arrayOf<Any>(-40.0, 0.4),
            R.string.widget_value_precip_range to arrayOf<Any>(0.05, 25.4),
            R.string.widget_value_wind_range to arrayOf<Any>(16.09344, 32.18688),
            R.string.temp_legend_above_normal to emptyArray(),
            R.string.temp_legend_below_normal to emptyArray(),
            R.string.bias_reliability_method_note to emptyArray(),
            R.string.forecast_insight_detail_temperature_frost to emptyArray(),
            R.string.var_wind_max to emptyArray(),
            R.string.var_wind_gust_max to emptyArray(),
            R.string.a11y_wind_max_label to emptyArray(),
            R.string.marine_not_coastal to emptyArray(),
            R.string.wind_table_legend to emptyArray()
        )
        for (tag in listOf("fr", "en", "de", "es", "it")) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
            for (system in UnitSystem.entries) {
                for ((id, args) in cases) {
                    val output = weatherString(resources, WeatherUnits(system), id, *args)
                    assertFalse("$tag / $system / $id: $output", Regex("%[0-9]+\\$").containsMatchIn(output))
                    assertFalse(output.contains("NaN"))
                    assertFalse(output.contains("Infinity"))
                    assertFalse(output.contains("—"))
                }
            }
        }
    }

    @Test fun dimensional_resources_render_consistent_values_in_every_supported_language() {
        for (tag in listOf("fr", "en", "de", "es", "it")) {
            val locale = Locale.forLanguageTag(tag)
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(locale)
            }).resources
            fun text(id: Int, vararg args: Any) = weatherString(resources, imperial, id, *args)
            assertTrue(text(R.string.timeline_precip_amount, 25.4).contains(imperial.rain(25.4, locale)))
            assertTrue(text(R.string.forecast_insight_metric_wind, 16.09344).contains("10 mph"))
            val range = text(R.string.forecast_insight_metric_temperature_range, 0.0, 100.0)
            assertTrue(range.contains("32°F"))
            assertTrue(range.contains("212°F"))
            assertTrue(text(R.string.marine_distance, 16.09344).contains(imperial.format(16.09344, WeatherUnit.DISTANCE, 1, locale)))
            assertTrue(text(R.string.temp_legend_above_normal).contains(imperial.format(2.0, WeatherUnit.TEMPERATURE_COMPACT, 1, locale, delta = true)))
            assertTrue(text(R.string.forecast_insight_detail_temperature_frost).contains("32°F"))
            assertTrue(text(R.string.wind_table_legend).contains("33 ft"))
            assertTrue(text(R.string.wind_table_legend).contains("mph"))
            assertTrue(text(R.string.var_wind_max).contains("33 ft"))
            assertTrue(text(R.string.marine_not_coastal).contains("31 mi"))
            val spoken = A11yFormatter.temperatureDescription(resources,
                ConfidenceScore(85, 0.0, 0.0, 0.0, 0.0, 5), imperial)
            assertTrue(spoken.contains("32"))
            assertTrue(spoken.contains("Fahrenheit"))
            assertFalse(spoken.contains("Celsius"))
            assertFalse(range.contains("%1$"))
        }
    }

    @Test fun metric_labels_and_values_still_match_after_an_imperial_render() {
        val resources = context.resources
        weatherString(resources, imperial, R.string.timeline_precip_amount, 25.4)
        val text = weatherString(resources, WeatherUnits(), R.string.timeline_precip_amount, 25.4)
        assertTrue(text.contains("mm"))
        assertFalse(text.contains("in"))
    }
}
