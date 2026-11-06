package com.meteocompare.app.ui.components

import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.UnitSystem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SunshineDurationTest {
    private val units = WeatherUnits()
    private val french = SunshineDurationFormats("%1\$s min", "%1\$s h", "%1\$s h %2\$s min", "min")
    private val english = SunshineDurationFormats("%1\$s min", "%1\$s hr", "%1\$s hr %2\$s min", "min")
    private val german = SunshineDurationFormats("%1\$s Min.", "%1\$s Std.", "%1\$s Std. %2\$s Min.", "Min.")

    @Test fun hourlySlotsUseMinutesInsteadOfDecimalHours() {
        assertEquals("18 min", formatSunshineDuration(0.3, true, units, Locale.FRANCE, french))
        assertEquals("30 min", formatSunshineDuration(0.5, true, units, Locale.US, english))
        assertEquals("1 min", formatSunshineDuration(1.0 / 60.0, true, units, Locale.FRANCE, french))
        assertEquals("0 min", formatSunshineDuration(0.0, true, units, Locale.FRANCE, french))
        assertEquals("60 min", formatSunshineDuration(1.0, true, units, Locale.FRANCE, french))
    }

    @Test fun dailyTotalsUseReadableHoursAndMinutesAndRespectUnitProfile() {
        for (profile in UnitSystem.entries) {
            val configured = WeatherUnits(profile)
            assertEquals("1 h 12 min", formatSunshineDuration(1.2, false, configured, Locale.FRANCE, french))
            assertEquals("18 min", formatSunshineDuration(0.3, false, configured, Locale.US, english))
            assertEquals("6 h", formatSunshineDuration(6.0, false, configured, Locale.FRANCE, french))
            assertEquals("18 min", formatSunshineDuration(0.3, true, configured, Locale.FRANCE, french))
        }
    }

    @Test fun durationPatternsAllowLocalizedAbbreviations() {
        assertEquals("18 min", formatSunshineDuration(0.3, true, units, Locale.US, english))
        assertEquals("1 hr 12 min", formatSunshineDuration(1.2, false, units, Locale.US, english))
        assertEquals("6 hr", formatSunshineDuration(6.0, false, units, Locale.US, english))
        assertEquals("18 Min.", formatSunshineDuration(0.3, true, units, Locale.GERMANY, german))
        assertEquals("1 Std. 12 Min.", formatSunshineDuration(1.2, false, units, Locale.GERMANY, german))
        assertEquals("6 Std.", formatSunshineDuration(6.0, false, units, Locale.GERMANY, german))
    }

    @Test fun unavailableOrInvalidValuesNeverBecomeZero() {
        for (hours in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            assertEquals("—", formatSunshineDuration(hours, true, units, Locale.FRANCE, french))
        }
        assertEquals("—", formatSunshineDuration(null, false, units, Locale.FRANCE, french))
    }
}
