package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DailyTimelineRangesTest {
    @Test fun `daily maximum minimum and rain ranges use their own model extrema`() {
        val units = WeatherUnits()
        assertEquals("(25–34)", dailyTimelineRange(25.0, 34.0, units,
            WeatherUnit.TEMPERATURE_COMPACT, locale = Locale.US))
        assertEquals("(12–19)", dailyTimelineRange(12.0, 19.0, units,
            WeatherUnit.TEMPERATURE_COMPACT, locale = Locale.US))
        assertEquals("(2.0–32.0)", dailyTimelineRange(2.0, 32.0, units,
            WeatherUnit.PRECIPITATION, digits = 1, locale = Locale.US))
    }

    @Test fun `all bounds convert using custom units including small rain amounts`() {
        val units = WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(
            temperature = TemperatureChoice.FAHRENHEIT,
            precipitation = PrecipitationChoice.INCHES
        ))
        assertEquals("(77–93)", dailyTimelineRange(25.0, 34.0, units,
            WeatherUnit.TEMPERATURE_COMPACT, locale = Locale.US))
        assertEquals("(0.08–1.26)", dailyTimelineRange(2.0, 32.0, units,
            WeatherUnit.PRECIPITATION, digits = 1, locale = Locale.US))
    }

    private val date = java.time.LocalDate.of(2026, 7, 23)

    private fun allRanges() = SimplifiedTimelinePoint(
        date = date,
        tempMaxC = 30.0, tempMinC = 15.0,
        temperatureMinAcrossModels = 25.0, temperatureMaxAcrossModels = 34.0,
        temperatureModelCount = 3,
        tempMinMinAcrossModels = 12.0, tempMinMaxAcrossModels = 19.0,
        tempMinModelCount = 3,
        precipitationMinAcrossModelsMm = 2.0, precipitationMaxAcrossModelsMm = 32.0,
        precipitationAmountModelCount = 3,
        precipitationProbabilityMin = 20, precipitationProbabilityMax = 90,
        precipitationProbabilityModelCount = 3,
        cloudCoverMinAcrossModels = 15, cloudCoverMaxAcrossModels = 80, cloudCoverModelCount = 3,
        humidityMinAcrossModels = 40, humidityMaxAcrossModels = 70, humidityModelCount = 3,
        dewPointMinAcrossModels = 9.0, dewPointMaxAcrossModels = 17.0, dewPointModelCount = 3,
        pressureMinAcrossModels = 1000.0, pressureMaxAcrossModels = 1030.0, pressureModelCount = 3,
        windMinAcrossModels = 18.52, windMaxAcrossModels = 37.04, windModelCount = 3,
        windGustMinAcrossModels = 46.3, windGustMaxAcrossModels = 92.6, windGustModelCount = 3
    )

    @Test fun `daily ranges include every available numeric metric and distinguish gusts`() {
        val units = WeatherUnits()
        val point = allRanges()
        val expected = mapOf(
            DailyRangeMetric.HIGH_TEMPERATURE to "(25–34)",
            DailyRangeMetric.LOW_TEMPERATURE to "(12–19)",
            DailyRangeMetric.RAIN_AMOUNT to "(2.0–32.0)",
            DailyRangeMetric.RAIN_PROBABILITY to "(20–90%)",
            DailyRangeMetric.CLOUD_COVER to "(15–80%)",
            DailyRangeMetric.PRESSURE to "(1000–1030)",
            DailyRangeMetric.HUMIDITY to "(40–70%)",
            DailyRangeMetric.DEW_POINT to "(9–17)",
            DailyRangeMetric.WIND to "(19–37)",
            DailyRangeMetric.WIND_GUST to "(46–93)"
        )
        DailyRangeMetric.entries.forEach { metric ->
            assertEquals(metric.name, expected[metric], dailyMetricRange(point, metric, units, Locale.US))
        }
    }

    @Test fun `custom and UK unit systems convert all endpoints exactly once`() {
        val point = allRanges()
        val custom = WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(
            temperature = TemperatureChoice.FAHRENHEIT,
            wind = WindChoice.KNOTS,
            precipitation = PrecipitationChoice.INCHES,
            pressure = PressureChoice.INHG
        ))
        assertEquals("(77–93)", dailyMetricRange(point, DailyRangeMetric.HIGH_TEMPERATURE, custom, Locale.US))
        assertEquals("(54–66)", dailyMetricRange(point, DailyRangeMetric.LOW_TEMPERATURE, custom, Locale.US))
        assertEquals("(48–63)", dailyMetricRange(point, DailyRangeMetric.DEW_POINT, custom, Locale.US))
        assertEquals("(10–20)", dailyMetricRange(point, DailyRangeMetric.WIND, custom, Locale.US))
        assertEquals("(25–50)", dailyMetricRange(point, DailyRangeMetric.WIND_GUST, custom, Locale.US))
        assertEquals("(0.08–1.26)", dailyMetricRange(point, DailyRangeMetric.RAIN_AMOUNT, custom, Locale.US))
        assertEquals("(29.53–30.42)", dailyMetricRange(point, DailyRangeMetric.PRESSURE, custom, Locale.US))
        assertEquals("(20–90%)", dailyMetricRange(point, DailyRangeMetric.RAIN_PROBABILITY, custom, Locale.US))
        val uk = WeatherUnits(UnitSystem.UK_MIXED, custom.custom)
        assertEquals("(25–34)", dailyMetricRange(point, DailyRangeMetric.HIGH_TEMPERATURE, uk, Locale.US))
        assertEquals("(12–23)", dailyMetricRange(point, DailyRangeMetric.WIND, uk, Locale.US))
        assertEquals("(2.0–32.0)", dailyMetricRange(point, DailyRangeMetric.RAIN_AMOUNT, uk, Locale.US))
    }

    @Test fun `missing one model and invalid model values never create fake ranges`() {
        val point = allRanges()
        val metric = WeatherUnits()
        assertNull(dailyMetricRange(point.copy(date = null), DailyRangeMetric.WIND, metric))
        assertNull(dailyMetricRange(point.copy(windModelCount = 1), DailyRangeMetric.WIND, metric))
        assertNull(dailyMetricRange(point.copy(precipitationAmountModelCount = 1), DailyRangeMetric.RAIN_AMOUNT, metric))
        assertNull(dailyMetricRange(point.copy(tempMinModelCount = 1), DailyRangeMetric.LOW_TEMPERATURE, metric))
        assertNull(dailyMetricRange(point.copy(pressureMinAcrossModels = Double.NaN), DailyRangeMetric.PRESSURE, metric))
        assertNull(dailyMetricRange(point.copy(humidityMinAcrossModels = 80, humidityMaxAcrossModels = 40),
            DailyRangeMetric.HUMIDITY, metric))
    }

    @Test fun `changing custom presets cannot alter canonical source values`() {
        val point = allRanges()
        for (system in UnitSystem.entries) {
            val units = WeatherUnits(system, CustomUnitPreferences(wind = WindChoice.MS))
            for (metric in DailyRangeMetric.entries) dailyMetricRange(point, metric, units, Locale.FRANCE)
        }
        assertEquals(37.04, point.windMaxAcrossModels!!, 0.0)
        assertEquals(32.0, point.precipitationMaxAcrossModelsMm!!, 0.0)
        assertEquals(1000.0, point.pressureMinAcrossModels!!, 0.0)
    }

    @Test fun `invalid and absent bounds cannot create misleading ranges`() {
        for (bad in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(dailyTimelineRange(bad, 20.0, WeatherUnits(), WeatherUnit.TEMPERATURE_COMPACT))
            assertNull(dailyTimelineRange(10.0, bad, WeatherUnits(), WeatherUnit.TEMPERATURE_COMPACT))
        }
        assertNull(dailyTimelineRange(20.0, 10.0, WeatherUnits(), WeatherUnit.TEMPERATURE_COMPACT))
    }
}
