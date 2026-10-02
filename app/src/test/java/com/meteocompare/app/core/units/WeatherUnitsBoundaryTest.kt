package com.meteocompare.app.core.units

import com.meteocompare.app.domain.model.UnitSystem
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class WeatherUnitsBoundaryTest {
    @Test fun `render contracts distinguish full and compact temperatures and preserve rain precision`() {
        val metric = WeatherUnits()
        val imperial = WeatherUnits(UnitSystem.IMPERIAL)
        val english = listOf(
            "0.0 °C", "32.0 °F", "20.0°", "68.0°F",
            "1.5", "3.0", "0.06", "0.12", "0.1 mm", "<0.01 in"
        )
        for (tag in listOf("en", "fr", "de", "es", "it")) {
            val locale = Locale.forLanguageTag(tag)
            val expected = if (tag == "en") english else english.map { it.replace('.', ',') }
            assertEquals(tag, expected, listOf(
                metric.format(0.0, WeatherUnit.TEMPERATURE, 1, locale),
                imperial.format(0.0, WeatherUnit.TEMPERATURE, 1, locale),
                metric.temp(20.0, 1, locale),
                imperial.temp(20.0, 1, locale),
                // Even a request for zero digits must retain useful rain precision.
                metric.value(1.5, WeatherUnit.PRECIPITATION, 0, locale),
                metric.value(3.0, WeatherUnit.PRECIPITATION, 0, locale),
                imperial.value(1.5, WeatherUnit.PRECIPITATION, 0, locale),
                imperial.value(3.0, WeatherUnit.PRECIPITATION, 0, locale),
                metric.rain(0.05, locale),
                imperial.rain(0.05, locale)
            ))
        }
    }

    @Test fun `existing formatter follows locale changes without changing the selected units`() {
        val original = Locale.getDefault()
        try {
            val units = WeatherUnits(UnitSystem.IMPERIAL)
            Locale.setDefault(Locale.US)
            assertEquals("1.00 in", units.rain(25.4))
            Locale.setDefault(Locale.FRANCE)
            assertEquals("1,00 in", units.rain(25.4))
            assertEquals("29,92 inHg", units.format(1013.25, WeatherUnit.PRESSURE))
            Locale.setDefault(Locale.US)
            assertEquals("1.00 in", units.rain(25.4))
            assertEquals(UnitSystem.IMPERIAL, units.system)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun `axis ticks remain distinct for minimum chart amplitudes in both systems`() {
        for (system in UnitSystem.entries) {
            val units = WeatherUnits(system)
            for ((unit, ticks) in listOf(
                WeatherUnit.TEMPERATURE to listOf(-0.5, 0.0, 0.5),
                WeatherUnit.WIND_SPEED to listOf(19.5, 20.0, 20.5),
                WeatherUnit.PRECIPITATION_RATE to listOf(0.0, 0.25, 0.5, 0.75, 1.0),
                WeatherUnit.HEIGHT to listOf(-0.05, 0.0, 0.05))) {
                val labels = ticks.map { units.axisValue(it, unit, ticks[1] - ticks[0], locale = Locale.US) }
                assertEquals(labels.toString(), ticks.size, labels.distinct().size)
                assertTrue(labels.toString(), labels.none { it.contains("<") || it == "—" })
            }
        }
        val units = WeatherUnits(UnitSystem.IMPERIAL)
        assertEquals("32.0", units.axisValue(0.0, WeatherUnit.TEMPERATURE, 0.5, locale = Locale.US))
    }

    @Test fun `every quantity and locale has finite bounded output for missing and extreme inputs`() {
        for (system in UnitSystem.entries) for (unit in WeatherUnit.entries) {
            val units = WeatherUnits(system)
            for (tag in listOf("fr", "en", "de", "es", "it")) for (value in listOf(
                null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -Double.MAX_VALUE, -100.0, -0.0, 0.0, Double.MIN_VALUE, 0.001, 25.4, 1e12, Double.MAX_VALUE)) {
                val text = units.format(value, unit, locale = Locale.forLanguageTag(tag))
                assertFalse(text, text.contains("NaN") || text.contains("Infinity"))
                assertTrue(text, text.length < 40)
                if (value == null || !value.isFinite()) assertEquals("—", text)
            }
        }
    }

    @Test fun `pressure uses canonical hectopascals and two imperial decimal places`() {
        val units = WeatherUnits(UnitSystem.IMPERIAL)
        assertEquals(1.0, units.pressure(33.86389), 1e-12)
        assertEquals("29.92 inHg", units.format(1013.25, WeatherUnit.PRESSURE, locale = Locale.US))
        assertEquals("1013 hPa", WeatherUnits().format(1013.25, WeatherUnit.PRESSURE, locale = Locale.US))
    }

    @Test fun `signed deltas preserve direction without false Fahrenheit offset or negative zero`() {
        val units = WeatherUnits(UnitSystem.IMPERIAL)
        assertEquals("−3.6 °F", units.signedDelta(-2.0, WeatherUnit.TEMPERATURE, locale = Locale.US))
        assertEquals("+3.6 °F", units.signedDelta(2.0, WeatherUnit.TEMPERATURE, locale = Locale.US))
        assertEquals("0.0 °F", units.signedDelta(-0.001, WeatherUnit.TEMPERATURE, locale = Locale.US))
        assertEquals("−1.00 in", units.signedDelta(-25.4, WeatherUnit.PRECIPITATION, locale = Locale.US))
        assertEquals("—", units.signedDelta(Double.NaN, WeatherUnit.TEMPERATURE))
    }

    @Test fun `small rainfall cannot turn wet values into a displayed zero in either system`() {
        for (system in UnitSystem.entries) {
            val units = WeatherUnits(system)
            val dry = units.rain(0.0, Locale.US)
            for (wet in listOf(Double.MIN_VALUE, 0.000001, 0.001, 0.049, 0.05, 0.1, 0.126, 0.127, 0.2, 0.5)) {
                assertNotEquals(dry, units.rain(wet, Locale.US))
            }
            assertEquals(units.rain(0.05, Locale.US), units.format(0.05, WeatherUnit.PRECIPITATION, -100, Locale.US))
            assertFalse(units.format(0.05, WeatherUnit.PRECIPITATION, 100, Locale.US).contains("NaN"))
        }
    }

    @Test fun `range collapsing compares rendered values after conversion`() {
        val units = WeatherUnits(UnitSystem.IMPERIAL)
        assertFalse(units.sameDisplayedValue(0.1, 0.4, WeatherUnit.TEMPERATURE_COMPACT))
        assertTrue(WeatherUnits().sameDisplayedValue(0.1, 0.4, WeatherUnit.TEMPERATURE_COMPACT))
        assertFalse(units.sameDisplayedValue(null, null, WeatherUnit.TEMPERATURE_COMPACT))
        assertFalse(units.sameDisplayedValue(Double.NaN, Double.NaN, WeatherUnit.WIND_SPEED))
        assertFalse(units.sameDisplayedValue(Double.MAX_VALUE, Double.MAX_VALUE, WeatherUnit.TEMPERATURE))
        assertFalse(units.sameDisplayedValue(0.0, 0.05, WeatherUnit.PRECIPITATION))
    }

    @Test fun `unit labels and precisions agree for absolute values differences and rates`() {
        val units = WeatherUnits(UnitSystem.IMPERIAL)
        assertEquals("32 °F", units.format(0.0, WeatherUnit.TEMPERATURE, locale = Locale.US))
        assertEquals("32°F", units.temp(0.0, locale = Locale.US))
        assertEquals("0 °F", units.format(0.0, WeatherUnit.TEMPERATURE, locale = Locale.US, delta = true))
        assertEquals("1.00 in/h", units.format(25.4, WeatherUnit.PRECIPITATION_RATE, locale = Locale.US))
        assertEquals("10 mph", units.speed(16.09344, Locale.US))
        assertEquals("-10.0 ft", units.format(-3.048, WeatherUnit.HEIGHT, 1, Locale.US))
        assertEquals("1.0 mi", units.format(1.609344, WeatherUnit.DISTANCE, 1, Locale.US))
    }
}
