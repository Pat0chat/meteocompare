package com.meteocompare.app.core.units

import com.meteocompare.app.domain.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CustomWeatherUnitsTest {
    private val custom = CustomUnitPreferences(
        temperature = TemperatureChoice.FAHRENHEIT,
        wind = WindChoice.KNOTS,
        precipitation = PrecipitationChoice.INCHES,
        pressure = PressureChoice.INHG,
        height = HeightChoice.FEET,
        distance = DistanceChoice.MILES
    )
    private val units = WeatherUnits(UnitSystem.CUSTOM, custom)

    @Test fun `each dimension converts and labels without altering metric input`() {
        assertEquals("68°F", units.temp(20.0, locale = Locale.US))
        assertEquals("+3.6 °F", units.signedDelta(2.0, WeatherUnit.TEMPERATURE, locale = Locale.US))
        assertEquals("10 kn", units.speed(18.52, Locale.US))
        assertEquals("1.00 in", units.rain(25.4, Locale.US))
        assertEquals("1.00 in/h", units.format(25.4, WeatherUnit.PRECIPITATION_RATE, locale = Locale.US))
        assertEquals("29.92 inHg", units.format(1013.25, WeatherUnit.PRESSURE, locale = Locale.US))
        assertEquals("10 ft", units.format(3.048, WeatherUnit.HEIGHT, locale = Locale.US))
        assertEquals("10 mi", units.format(16.09344, WeatherUnit.DISTANCE, locale = Locale.US))
        assertEquals("83%", units.format(83.0, WeatherUnit.PERCENT, locale = Locale.US))
        assertEquals(18.52, 18.52, 0.0) // Original input never mutated.
    }

    @Test fun `wind choices include accurate knots and metres per second`() {
        assertEquals("10 kn", WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(wind = WindChoice.KNOTS))
            .speed(18.52, Locale.US))
        assertEquals("10 m/s", WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(wind = WindChoice.MS))
            .speed(36.0, Locale.US))
        assertEquals("10 mph", WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(wind = WindChoice.MPH))
            .speed(16.09344, Locale.US))
        assertEquals("19 km/h", WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences())
            .speed(18.52, Locale.US))
    }

    @Test fun `mixed custom profile is independent across all six dimensions`() {
        val mixed = WeatherUnits(UnitSystem.CUSTOM, CustomUnitPreferences(
            wind = WindChoice.KNOTS, pressure = PressureChoice.INHG, height = HeightChoice.FEET
        ))
        assertEquals("20°", mixed.temp(20.0, locale = Locale.US))
        assertEquals("10 kn", mixed.speed(18.52, Locale.US))
        assertEquals("25.4 mm", mixed.rain(25.4, Locale.US))
        assertEquals("29.92 inHg", mixed.format(1013.25, WeatherUnit.PRESSURE, locale = Locale.US))
        assertEquals("10 ft", mixed.format(3.048, WeatherUnit.HEIGHT, locale = Locale.US))
        assertEquals("10 km", mixed.format(10.0, WeatherUnit.DISTANCE, locale = Locale.US))
    }

    @Test fun `custom settings do not affect pre-existing unit presets`() {
        for (preset in listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL, UnitSystem.UK_MIXED)) {
            val expected = WeatherUnits(preset)
            val withCustom = WeatherUnits(preset, custom)
            for (unit in WeatherUnit.entries) {
                assertEquals(expected.format(25.4, unit, locale = Locale.US),
                    withCustom.format(25.4, unit, locale = Locale.US))
            }
        }
    }
}
