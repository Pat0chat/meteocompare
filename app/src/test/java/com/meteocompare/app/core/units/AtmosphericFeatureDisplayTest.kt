package com.meteocompare.app.core.units

import com.meteocompare.app.domain.model.UnitSystem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression checks for new feels-like extrema and sea-level pressure across UI preferences. */
class AtmosphericFeatureDisplayTest {
    private val languages = listOf("fr", "en", "de", "es", "it")

    @Test fun newMetricsRespectAllThreeUnitProfilesAndAllFiveLanguages() {
        for (language in languages) {
            val locale = Locale.forLanguageTag(language)
            val decimal = if (language == "en") "." else ","
            for (system in UnitSystem.entries) {
                val units = WeatherUnits(system)
                val imperial = system == UnitSystem.IMPERIAL
                assertEquals(system.name, if (imperial) "°F" else "°C", units.temperatureUnit)
                assertEquals(system.name, if (imperial) "inHg" else "hPa", units.label(WeatherUnit.PRESSURE))
                // Same canonical values are rendered differently, not changed in storage.
                assertEquals(system.name, if (imperial) "50°F" else "10°", units.temp(10.0, locale = locale))
                assertEquals(system.name, if (imperial) "68°F" else "20°", units.temp(20.0, locale = locale))
                assertEquals(system.name, if (imperial) "29${decimal}92 inHg" else "1013 hPa",
                    units.format(1013.25, WeatherUnit.PRESSURE, locale = locale))
                assertEquals(system.name, if (imperial) "29${decimal}92" else "1013",
                    units.axisValue(1013.25, WeatherUnit.PRESSURE, 2.0, locale = locale))
                assertEquals("—", units.format(null, WeatherUnit.PRESSURE, locale = locale))
                assertEquals("—", units.temp(null, locale = locale))
            }
        }
    }

    @Test fun localeChangesDontChangeTheChosenUnitsAndUnitChangesDontChangeCanonicalData() {
        val original = Locale.getDefault()
        try {
            val selectedSystem = WeatherUnits(UnitSystem.IMPERIAL)
            Locale.setDefault(Locale.US)
            assertEquals("29.92 inHg", selectedSystem.format(1013.25, WeatherUnit.PRESSURE))
            assertEquals("68°F", selectedSystem.temp(20.0))
            Locale.setDefault(Locale.FRANCE)
            assertEquals("29,92 inHg", selectedSystem.format(1013.25, WeatherUnit.PRESSURE))
            assertEquals("68°F", selectedSystem.temp(20.0))
            assertEquals(UnitSystem.IMPERIAL, selectedSystem.system)

            val metric = WeatherUnits(UnitSystem.METRIC)
            val uk = WeatherUnits(UnitSystem.UK_MIXED)
            assertEquals("1013 hPa", metric.format(1013.25, WeatherUnit.PRESSURE))
            assertEquals("1013 hPa", uk.format(1013.25, WeatherUnit.PRESSURE))
            assertEquals("20°", metric.temp(20.0))
            assertEquals("20°", uk.temp(20.0))
            assertEquals(1013.25, uk.pressure(1013.25), 0.0)
            assertEquals(20.0, uk.temperature(20.0), 0.0)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun pressureTicksStayDistinctForSmallVariationsAfterConversion() {
        for (system in UnitSystem.entries) {
            val units = WeatherUnits(system)
            val ticks = listOf(1013.2, 1013.3, 1013.4)
            val labels = ticks.map {
                units.axisValue(it, WeatherUnit.PRESSURE, 0.1, locale = Locale.US)
            }
            assertEquals(system.name + labels, ticks.size, labels.distinct().size)
            assertTrue(labels.none { it == "—" })
            assertNotEquals("0", labels.first())
        }
    }
}
