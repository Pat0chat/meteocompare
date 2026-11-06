package com.meteocompare.app.ui.localitycompare

import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.ui.components.SunshineDurationFormats
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.usecase.LocalityHourMetrics
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Chart logic cannot request forecasts: it only projects already-computed hourly metrics. */
class LocalityHourlyChartTest {
    private val sunshineFormats = SunshineDurationFormats("%1\$s min", "%1\$s h", "%1\$s h %2\$s min", "min")
    private val hour = LocalityHourMetrics(
        instant = Instant.parse("2026-06-20T14:00:00Z"),
        temperatureC = 20.0,
        precipitationMm = 2.54,
        rainProbabilityPercent = 35,
        cloudCoverPercent = 60,
        windKmh = 16.09344,
        estimatedSunHours = 0.5,
        agreementPercent = 72,
        uvIndex = 5.6,
        modelCount = 3
    )

    @Test fun hourlySunshineIsShownAndPlottedInMinutes() {
        val metric = CompareChartMetric.SUNSHINE
        for (profile in com.meteocompare.app.domain.model.UnitSystem.entries) {
            val units = WeatherUnits(profile)
            assertEquals("30 min", metric.displayValue(hour, units, java.util.Locale.FRANCE, sunshineFormats))
            assertEquals("min", chartAxisUnit(metric, units, sunshineFormats.minuteUnit))
            assertEquals(30.0, metric.plotValue(hour, units)!!, 0.0001)
            assertEquals(30.0, chartPoints(
                listOf(HourChartCity("paris", "Paris", listOf(hour))), metric, units
            ).single()[0]!!, 0.0001)
        }
        assertEquals("—", metric.displayValue(null, WeatherUnits(), java.util.Locale.FRANCE, sunshineFormats))
        assertEquals(null, metric.plotValue(null, WeatherUnits()))
    }

    @Test fun dailyComparisonUsesReadableHoursAndMinutes() {
        val day = com.meteocompare.app.domain.usecase.LocalityDayMetrics(
            date = java.time.LocalDate.of(2026, 6, 20),
            minC = null, maxC = null, precipitationMm = null,
            rainProbabilityPercent = null, cloudCoverPercent = null,
            maxWindKmh = null, estimatedSunHours = 1.2,
            agreementPercent = null, modelCount = 1
        )
        assertEquals("1 h 12 min", displayDayMetric(
            CompareChartMetric.SUNSHINE, day, WeatherUnits(), java.util.Locale.FRANCE, sunshineFormats
        ))
    }

    @Test fun chartRangeIgnoresMissingAndInvalidValues() {
        assertEquals(ChartRange(0.0, 1.0, false), chartRange(listOf(listOf(null, Double.NaN))))
        val range = chartRange(listOf(listOf(10.0, null, 20.0), listOf(30.0)))
        assertEquals(7.6, range.lower, 0.0001)
        assertEquals(32.4, range.upper, 0.0001)
        assertEquals(true, range.hasData)
    }

    @Test fun chartTapMapsToLocalClockHoursAndClampsEdges() {
        assertEquals(0, chartHourFromX(-200f, 20f, 250f))
        assertEquals(0, chartHourFromX(20f, 20f, 250f))
        assertEquals(12, chartHourFromX(140f, 20f, 250f))
        assertEquals(23, chartHourFromX(250f, 20f, 250f))
        assertEquals(23, chartHourFromX(900f, 20f, 250f))
        assertEquals(0, chartHourFromX(50f, 20f, 20f))
    }

    @Test fun missingHoursRemainGapsInsteadOfInventedZeros() {
        val cities = listOf(HourChartCity("paris", "Paris", List(14) { null } + hour))
        val points = chartPoints(cities, CompareChartMetric.TEMPERATURE, WeatherUnits())
        assertEquals(24, points.single().size)
        assertNull(points.single()[13])
        assertEquals(20.0, points.single()[14]!!, 0.00001)
        assertNull(points.single()[15])
    }

    @Test fun chartUnitsMatchTheUserChoiceIncludingMixedUkProfile() {
        val cities = listOf(HourChartCity("uk", "London", listOf(hour)))
        val mixed = WeatherUnits(UnitSystem.UK_MIXED)
        val imperial = WeatherUnits(UnitSystem.IMPERIAL)
        val tempMixed = chartPoints(cities, CompareChartMetric.TEMPERATURE, mixed).single()[0]
        val tempImperial = chartPoints(cities, CompareChartMetric.TEMPERATURE, imperial).single()[0]
        val windMixed = chartPoints(cities, CompareChartMetric.WIND, mixed).single()[0]
        assertEquals(20.0, tempMixed!!, 0.00001)
        assertEquals(68.0, tempImperial!!, 0.00001)
        assertEquals(10.0, windMixed!!, 0.00001)
        assertEquals(35.0, chartPoints(cities, CompareChartMetric.RAIN_RISK, mixed).single()[0]!!, 0.00001)
    }

    @Test fun axisRangesRespectPhysicalBoundsAndDontInventValues() {
        val data = listOf(listOf(8.0, 10.0, null))
        assertEquals(ChartRange(0.0, 100.0, true), metricChartRange(data, CompareChartMetric.CLOUDS))
        assertEquals(ChartRange(0.0, 100.0, true), metricChartRange(data, CompareChartMetric.RAIN_RISK))
        assertEquals(ChartRange(0.0, 100.0, true), metricChartRange(data, CompareChartMetric.AGREEMENT))
        assertEquals(ChartRange(0.0, 60.0, true), metricChartRange(data, CompareChartMetric.SUNSHINE))
        val rain = metricChartRange(data, CompareChartMetric.RAIN)
        assertEquals(0.0, rain.lower, 0.0)
        assertEquals(11.2, rain.upper, 0.00001)
        val uv = metricChartRange(data, CompareChartMetric.UV)
        assertEquals(0.0, uv.lower, 0.0)
        assertEquals(true, uv.upper >= 10.0)
        assertEquals(false, metricChartRange(listOf(listOf(null, Double.NaN)), CompareChartMetric.UV).hasData)
    }

    @Test fun axisUnitsFollowMetricImperialAndUkChoice() {
        val metric = WeatherUnits()
        val imperial = WeatherUnits(UnitSystem.IMPERIAL)
        val uk = WeatherUnits(UnitSystem.UK_MIXED)
        assertEquals("°C", chartAxisUnit(CompareChartMetric.TEMPERATURE, metric, sunshineFormats.minuteUnit))
        assertEquals("°F", chartAxisUnit(CompareChartMetric.TEMPERATURE, imperial, sunshineFormats.minuteUnit))
        assertEquals("°C", chartAxisUnit(CompareChartMetric.TEMPERATURE, uk, sunshineFormats.minuteUnit))
        assertEquals("km/h", chartAxisUnit(CompareChartMetric.WIND, metric, sunshineFormats.minuteUnit))
        assertEquals("mph", chartAxisUnit(CompareChartMetric.WIND, uk, sunshineFormats.minuteUnit))
        assertEquals("in", chartAxisUnit(CompareChartMetric.RAIN, imperial, sunshineFormats.minuteUnit))
        assertEquals("mm", chartAxisUnit(CompareChartMetric.RAIN, uk, sunshineFormats.minuteUnit))
        assertEquals("%", chartAxisUnit(CompareChartMetric.CLOUDS, metric, sunshineFormats.minuteUnit))
        assertEquals("UV", chartAxisUnit(CompareChartMetric.UV, metric, sunshineFormats.minuteUnit))
        assertEquals("18", chartAxisLabel(18.0, 5.0, CompareChartMetric.SUNSHINE, metric, java.util.Locale.FRANCE))
        assertEquals("0.02", chartAxisLabel(0.02, 0.01, CompareChartMetric.RAIN, imperial, java.util.Locale.US))
    }

    @Test
    fun atmosphericComparisonFollowsLocaleAndUnitChanges() {
        val day = com.meteocompare.app.domain.usecase.LocalityDayMetrics(
            date = java.time.LocalDate.of(2026, 6, 20),
            minC = null, maxC = null, precipitationMm = null,
            rainProbabilityPercent = null, cloudCoverPercent = null,
            maxWindKmh = null, estimatedSunHours = null, agreementPercent = null, modelCount = 2,
            apparentMinC = 10.0, apparentMaxC = 20.0, pressureHpa = 1013.25
        )
        val sample = hour.copy(apparentTemperatureC = 10.0, pressureHpa = 1013.25)
        val cities = listOf(HourChartCity("a", "A", listOf(sample)))
        for (language in listOf("fr", "en", "de", "es", "it")) {
            val locale = java.util.Locale.forLanguageTag(language)
            val decimal = if (language == "en") "." else ","
            for (system in com.meteocompare.app.domain.model.UnitSystem.entries) {
                val units = WeatherUnits(system)
                val imperial = system == com.meteocompare.app.domain.model.UnitSystem.IMPERIAL
                assertEquals(if (imperial) "50°F / 68°F" else "10° / 20°",
                    displayDayMetric(CompareChartMetric.APPARENT, day, units, locale, sunshineFormats))
                assertEquals(if (imperial) "29${decimal}92 inHg" else "1013 hPa",
                    displayDayMetric(CompareChartMetric.PRESSURE, day, units, locale, sunshineFormats))
                val pressures = chartPoints(cities, CompareChartMetric.PRESSURE, units).single()
                assertEquals(if (imperial) 1013.25 / 33.86389 else 1013.25, pressures[0]!!, 0.0001)
                assertEquals(if (imperial) "50°F" else "10°",
                    CompareChartMetric.APPARENT.displayValue(sample, units, locale, sunshineFormats))
                assertEquals(if (imperial) "29${decimal}92 inHg" else "1013 hPa",
                    CompareChartMetric.PRESSURE.displayValue(sample, units, locale, sunshineFormats))
            }
        }
    }

    @Test
    fun atmosphericMetrics_areSelectableAndPressureHasUnitConvertedYAxis() {
        val units = WeatherUnits()
        val imperial = WeatherUnits(com.meteocompare.app.domain.model.UnitSystem.IMPERIAL)
        val hour = com.meteocompare.app.domain.usecase.LocalityHourMetrics(
            instant = java.time.Instant.parse("2026-06-20T12:00:00Z"),
            temperatureC = 20.0, precipitationMm = null, rainProbabilityPercent = null,
            cloudCoverPercent = null, windKmh = null, estimatedSunHours = null,
            agreementPercent = null, uvIndex = null, modelCount = 2,
            apparentTemperatureC = 17.0, pressureHpa = 1013.25
        )
        assertEquals(17.0, CompareChartMetric.APPARENT.rawValue(hour)!!, 0.001)
        assertEquals(1013.25, CompareChartMetric.PRESSURE.rawValue(hour)!!, 0.001)
        assertEquals("hPa", chartAxisUnit(CompareChartMetric.PRESSURE, units, sunshineFormats.minuteUnit))
        assertEquals("inHg", chartAxisUnit(CompareChartMetric.PRESSURE, imperial, sunshineFormats.minuteUnit))
        val displayPressure = CompareChartMetric.PRESSURE.plotValue(hour, imperial)!!
        assertEquals(29.92, displayPressure, 0.02)
    }

}
