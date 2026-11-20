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

    @Test fun `invalid and absent bounds cannot create misleading ranges`() {
        for (bad in listOf(null, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(dailyTimelineRange(bad, 20.0, WeatherUnits(), WeatherUnit.TEMPERATURE_COMPACT))
            assertNull(dailyTimelineRange(10.0, bad, WeatherUnits(), WeatherUnit.TEMPERATURE_COMPACT))
        }
        assertNull(dailyTimelineRange(20.0, 10.0, WeatherUnits(), WeatherUnit.TEMPERATURE_COMPACT))
    }
}
