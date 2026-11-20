package com.meteocompare.app.ui.components

import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.locale.formatConditionTimeWindow
import com.meteocompare.app.domain.model.ClockFormatPreference
import com.meteocompare.app.domain.util.ConditionTimeWindow
import com.meteocompare.app.domain.util.ConditionWindowKind
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/** Human readable time windows in the destination city, never the phone's zone. */
class ConditionTimeWindowFormattingTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val frClock = WeatherTimeFormat.clock(Locale.FRENCH, ClockFormatPreference.HOUR_24)

    @Test fun `hourly forecast uses city local time and 24 hour clock preference`() {
        val start = Instant.parse("2026-11-13T14:00:00Z")
        val text = formatConditionTimeWindow(ConditionTimeWindow(
            start, start.plusSeconds(3600), paris, ConditionWindowKind.HOURLY
        ), Locale.FRENCH, frClock)
        assertTrue(text, text.contains("15:00"))
        assertTrue(text, !text.contains("16:00"))
        assertTrue(text, text.contains("13 nov"))
    }

    @Test fun `preceding precipitation is shown from fourteen to fifteen local`() {
        val hour = Instant.parse("2026-11-13T14:00:00Z")
        val text = formatConditionTimeWindow(ConditionTimeWindow(
            hour.minusSeconds(3600), hour, paris, ConditionWindowKind.HOURLY_PRECEDING
        ), Locale.FRENCH, frClock)
        assertTrue(text, text.contains("14:00 – 15:00"))
    }

    @Test fun `mixed forecast shows only instantaneous timestamp in main line`() {
        val hour = Instant.parse("2026-11-13T14:00:00Z")
        val text = formatConditionTimeWindow(ConditionTimeWindow(
            hour, hour.plusSeconds(3600), paris, ConditionWindowKind.HOURLY_MIXED
        ), Locale.FRENCH, frClock)
        assertTrue(text, text.contains("15:00"))
        assertTrue(text, !text.contains("16:00"))
    }

    @Test fun `night interval shows both dates and precise sunrise minutes`() {
        val start = Instant.parse("2026-11-13T16:42:00Z")
        val end = Instant.parse("2026-11-14T06:57:00Z")
        val text = formatConditionTimeWindow(ConditionTimeWindow(start, end, paris), Locale.FRENCH, frClock)
        assertTrue(text, text.contains("17:42"))
        assertTrue(text, text.contains("07:57"))
        assertTrue(text, text.contains("13 nov"))
        assertTrue(text, text.contains("14 nov"))
    }

    @Test fun `autumn repeated local hour exposes differing UTC offsets`() {
        val start = Instant.parse("2026-10-25T00:30:00Z")
        val end = Instant.parse("2026-10-25T01:30:00Z")
        val text = formatConditionTimeWindow(ConditionTimeWindow(start, end, paris,
            ConditionWindowKind.HOURLY_PRECEDING), Locale.FRENCH, frClock)
        assertTrue(text, text.contains("UTC+02:00 → UTC+01:00"))
    }

    @Test fun `instantaneous code in repeated autumn hour shows its own offset`() {
        val hour = Instant.parse("2026-10-25T01:00:00Z")
        val text = formatConditionTimeWindow(ConditionTimeWindow(hour, hour.plusSeconds(3600), paris,
            ConditionWindowKind.HOURLY), Locale.FRENCH, frClock)
        assertTrue(text, text.contains("02:00"))
        assertTrue(text, !text.contains("UTC+02:00 → UTC+01:00"))
    }

    @Test fun `missing city timezone falls back to explicitly labeled UTC`() {
        val start = Instant.parse("2026-11-13T12:00:00Z")
        val text = formatConditionTimeWindow(ConditionTimeWindow(start, start.plusSeconds(3600)),
            Locale.FRENCH, frClock)
        assertTrue(text, text.contains("12:00 – 13:00"))
        assertTrue(text, text.endsWith("· UTC"))
    }

    @Test fun `explicit twelve hour preference is respected`() {
        val start = Instant.parse("2026-11-13T14:00:00Z")
        val enClock = WeatherTimeFormat.clock(Locale.ENGLISH, ClockFormatPreference.HOUR_12)
        val text = formatConditionTimeWindow(ConditionTimeWindow(start, start.plusSeconds(3600), paris),
            Locale.ENGLISH, enClock)
        assertTrue(text, text.contains("3:00 PM – 4:00 PM"))
    }
}
