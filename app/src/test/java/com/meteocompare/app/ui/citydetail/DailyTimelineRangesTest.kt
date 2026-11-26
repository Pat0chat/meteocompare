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
        apparentMinC = 8.0, apparentMaxC = 33.0,
        apparentMinMinAcrossModels = -5.0, apparentMinMaxAcrossModels = 12.0, apparentMinModelCount = 3,
        apparentMaxMinAcrossModels = 29.0, apparentMaxMaxAcrossModels = 40.0, apparentMaxModelCount = 3,
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
            TimelineRangeMetric.HIGH_TEMPERATURE to "(25–34)",
            TimelineRangeMetric.LOW_TEMPERATURE to "(12–19)",
            TimelineRangeMetric.LOW_APPARENT_TEMPERATURE to "(-5–12)",
            TimelineRangeMetric.HIGH_APPARENT_TEMPERATURE to "(29–40)",
            TimelineRangeMetric.RAIN_AMOUNT to "(2.0–32.0)",
            TimelineRangeMetric.RAIN_PROBABILITY to "(20–90%)",
            TimelineRangeMetric.CLOUD_COVER to "(15–80%)",
            TimelineRangeMetric.PRESSURE to "(1000–1030)",
            TimelineRangeMetric.HUMIDITY to "(40–70%)",
            TimelineRangeMetric.DEW_POINT to "(9–17)",
            TimelineRangeMetric.WIND to "(19–37)",
            TimelineRangeMetric.WIND_GUST to "(46–93)"
        )
        TimelineRangeMetric.entries.forEach { metric ->
            assertEquals(metric.name, expected[metric], timelineMetricRange(point, metric, units, Locale.US))
        }
    }

    @Test fun `hourly range formatting covers all shared metrics and independent apparent temperature`() {
        val point = allRanges().copy(date = null, instant = java.time.Instant.parse("2026-07-23T10:00:00Z"),
            apparentTemperatureC = 27.0, apparentTemperatureMinAcrossModels = -5.0,
            apparentTemperatureMaxAcrossModels = 12.0, apparentTemperatureModelCount = 2)
        val expected = mapOf(
            TimelineRangeMetric.HOURLY_TEMPERATURE to "(25–34)",
            TimelineRangeMetric.HOURLY_APPARENT_TEMPERATURE to "(-5–12)",
            TimelineRangeMetric.RAIN_AMOUNT to "(2.0–32.0)",
            TimelineRangeMetric.RAIN_PROBABILITY to "(20–90%)",
            TimelineRangeMetric.CLOUD_COVER to "(15–80%)",
            TimelineRangeMetric.PRESSURE to "(1000–1030)",
            TimelineRangeMetric.HUMIDITY to "(40–70%)",
            TimelineRangeMetric.DEW_POINT to "(9–17)",
            TimelineRangeMetric.WIND to "(19–37)",
            TimelineRangeMetric.WIND_GUST to "(46–93)"
        )
        for (metric in TimelineRangeMetric.entries) {
            assertEquals(metric.name, expected[metric], timelineMetricRange(point, metric, WeatherUnits(), Locale.US))
        }
        val custom = WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(
            temperature = TemperatureChoice.FAHRENHEIT, wind = WindChoice.KNOTS,
            precipitation = PrecipitationChoice.INCHES, pressure = PressureChoice.INHG))
        assertEquals("(77–93)", timelineMetricRange(point, TimelineRangeMetric.HOURLY_TEMPERATURE, custom, Locale.US))
        assertEquals("(23–54)", timelineMetricRange(point, TimelineRangeMetric.HOURLY_APPARENT_TEMPERATURE, custom, Locale.US))
        assertEquals("(10–20)", timelineMetricRange(point, TimelineRangeMetric.WIND, custom, Locale.US))
        assertEquals("(0,08–1,26)", timelineMetricRange(point, TimelineRangeMetric.RAIN_AMOUNT, custom, Locale.FRANCE))
        assertEquals("(29.53–30.42)", timelineMetricRange(point, TimelineRangeMetric.PRESSURE, custom, Locale.US))
        assertNull(timelineMetricRange(point.copy(apparentTemperatureModelCount = 1), TimelineRangeMetric.HOURLY_APPARENT_TEMPERATURE, custom))
        assertNull(timelineMetricRange(point.copy(apparentTemperatureMaxAcrossModels = Double.NaN), TimelineRangeMetric.HOURLY_APPARENT_TEMPERATURE, custom))
        assertNull(timelineMetricRange(point.copy(date = date), TimelineRangeMetric.HOURLY_TEMPERATURE, custom))
    }

    @Test fun `custom and UK unit systems convert all endpoints exactly once`() {
        val point = allRanges()
        val custom = WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(
            temperature = TemperatureChoice.FAHRENHEIT,
            wind = WindChoice.KNOTS,
            precipitation = PrecipitationChoice.INCHES,
            pressure = PressureChoice.INHG
        ))
        assertEquals("(77–93)", timelineMetricRange(point, TimelineRangeMetric.HIGH_TEMPERATURE, custom, Locale.US))
        assertEquals("(54–66)", timelineMetricRange(point, TimelineRangeMetric.LOW_TEMPERATURE, custom, Locale.US))
        assertEquals("(23–54)", timelineMetricRange(point, TimelineRangeMetric.LOW_APPARENT_TEMPERATURE, custom, Locale.US))
        assertEquals("(84–104)", timelineMetricRange(point, TimelineRangeMetric.HIGH_APPARENT_TEMPERATURE, custom, Locale.US))
        assertEquals("(48–63)", timelineMetricRange(point, TimelineRangeMetric.DEW_POINT, custom, Locale.US))
        assertEquals("(10–20)", timelineMetricRange(point, TimelineRangeMetric.WIND, custom, Locale.US))
        assertEquals("(25–50)", timelineMetricRange(point, TimelineRangeMetric.WIND_GUST, custom, Locale.US))
        assertEquals("(0.08–1.26)", timelineMetricRange(point, TimelineRangeMetric.RAIN_AMOUNT, custom, Locale.US))
        assertEquals("(29.53–30.42)", timelineMetricRange(point, TimelineRangeMetric.PRESSURE, custom, Locale.US))
        assertEquals("(20–90%)", timelineMetricRange(point, TimelineRangeMetric.RAIN_PROBABILITY, custom, Locale.US))
        val uk = WeatherUnits(UnitSystem.UK_MIXED, custom.custom)
        assertEquals("(25–34)", timelineMetricRange(point, TimelineRangeMetric.HIGH_TEMPERATURE, uk, Locale.US))
        assertEquals("(12–23)", timelineMetricRange(point, TimelineRangeMetric.WIND, uk, Locale.US))
        assertEquals("(2.0–32.0)", timelineMetricRange(point, TimelineRangeMetric.RAIN_AMOUNT, uk, Locale.US))
    }

    @Test fun `missing one model and invalid model values never create fake ranges`() {
        val point = allRanges()
        val metric = WeatherUnits()
        assertNull(timelineMetricRange(point.copy(date = null), TimelineRangeMetric.WIND, metric))
        assertNull(timelineMetricRange(point.copy(date = null), TimelineRangeMetric.LOW_APPARENT_TEMPERATURE, metric))
        assertNull(timelineMetricRange(point.copy(apparentMinModelCount = 1), TimelineRangeMetric.LOW_APPARENT_TEMPERATURE, metric))
        assertNull(timelineMetricRange(point.copy(apparentMaxMaxAcrossModels = Double.NaN), TimelineRangeMetric.HIGH_APPARENT_TEMPERATURE, metric))
        assertEquals("(29–40)", timelineMetricRange(point.copy(apparentMinModelCount = 1), TimelineRangeMetric.HIGH_APPARENT_TEMPERATURE, metric, Locale.US))
        assertNull(timelineMetricRange(point.copy(windModelCount = 1), TimelineRangeMetric.WIND, metric))
        assertNull(timelineMetricRange(point.copy(precipitationAmountModelCount = 1), TimelineRangeMetric.RAIN_AMOUNT, metric))
        assertNull(timelineMetricRange(point.copy(tempMinModelCount = 1), TimelineRangeMetric.LOW_TEMPERATURE, metric))
        assertNull(timelineMetricRange(point.copy(pressureMinAcrossModels = Double.NaN), TimelineRangeMetric.PRESSURE, metric))
        assertNull(timelineMetricRange(point.copy(humidityMinAcrossModels = 80, humidityMaxAcrossModels = 40),
            TimelineRangeMetric.HUMIDITY, metric))
    }

    @Test fun `changing custom presets cannot alter canonical source values`() {
        val point = allRanges()
        for (system in UnitSystem.entries) {
            val units = WeatherUnits(system, CustomUnitPreferences(wind = WindChoice.MS))
            for (metric in TimelineRangeMetric.entries) timelineMetricRange(point, metric, units, Locale.FRANCE)
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
