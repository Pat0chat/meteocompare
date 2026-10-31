package com.meteocompare.app.core.locale

import com.meteocompare.app.domain.model.ClockFormatPreference

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTimeFormatTest {
    @Test
    fun `english uses am and pm independently of region`() {
        for (locale in listOf(Locale.US, Locale.UK, Locale.forLanguageTag("en-AU"))) {
            assertEquals("12 AM", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hour(locale)))
            assertEquals("12 PM", LocalTime.NOON.format(WeatherTimeFormat.hour(locale)))
            assertEquals("12 AM", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hourPadded(locale)))
            assertEquals("1 PM", LocalTime.of(13, 0).format(WeatherTimeFormat.hour(locale)))
            assertEquals("11:45 PM", LocalTime.of(23, 45).format(WeatherTimeFormat.clock(locale)))
            assertEquals("12:00 AM", LocalTime.MIDNIGHT.format(WeatherTimeFormat.clock(locale)))
            assertEquals("12:00 PM", LocalTime.NOON.format(WeatherTimeFormat.clock(locale)))
        }
    }

    @Test
    fun `other supported languages stay on twenty four hour clock`() {
        for (locale in listOf(Locale.FRANCE, Locale.GERMANY, Locale.ITALY, Locale.forLanguageTag("es-ES"))) {
            assertEquals("0h", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hour(locale)))
            assertEquals("13h", LocalTime.of(13, 0).format(WeatherTimeFormat.hour(locale)))
            assertEquals("23:45", LocalTime.of(23, 45).format(WeatherTimeFormat.clock(locale)))
            assertEquals("00h", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hourPadded(locale)))
        }
    }

    @Test
    fun `explicit twelve hour preference works in every supported language`() {
        val locales = listOf(Locale.FRANCE, Locale.US, Locale.GERMANY, Locale.ITALY, Locale.forLanguageTag("es-ES"))
        for (locale in locales) {
            assertEquals("12 AM", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hour(locale, ClockFormatPreference.HOUR_12)))
            assertEquals("12 PM", LocalTime.NOON.format(WeatherTimeFormat.hour(locale, ClockFormatPreference.HOUR_12)))
            assertEquals("3:07 PM", LocalTime.of(15, 7).format(WeatherTimeFormat.clock(locale, ClockFormatPreference.HOUR_12)))
            assertEquals("11 PM", LocalTime.of(23, 0).format(WeatherTimeFormat.hourPadded(locale, ClockFormatPreference.HOUR_12)))
            assertTrue(WeatherTimeFormat.usesTwelveHourClock(locale, ClockFormatPreference.HOUR_12))
        }
    }

    @Test
    fun `explicit twenty four hour preference works in english and other languages`() {
        val locales = listOf(Locale.FRANCE, Locale.US, Locale.UK, Locale.GERMANY, Locale.ITALY, Locale.forLanguageTag("es-ES"))
        for (locale in locales) {
            assertEquals("00h", LocalTime.MIDNIGHT.format(WeatherTimeFormat.hourPadded(locale, ClockFormatPreference.HOUR_24)))
            assertEquals("15h", LocalTime.of(15, 7).format(WeatherTimeFormat.hour(locale, ClockFormatPreference.HOUR_24)))
            assertEquals("15:07", LocalTime.of(15, 7).format(WeatherTimeFormat.clock(locale, ClockFormatPreference.HOUR_24)))
            assertTrue(!WeatherTimeFormat.usesTwelveHourClock(locale, ClockFormatPreference.HOUR_24))
        }
    }

    @Test
    fun `preference parsing is robust to missing or obsolete stored values`() {
        assertEquals(ClockFormatPreference.AUTO, ClockFormatPreference.fromString(null))
        assertEquals(ClockFormatPreference.AUTO, ClockFormatPreference.fromString("bad_value"))
        assertEquals(ClockFormatPreference.HOUR_12, ClockFormatPreference.fromString("HOUR_12"))
        assertEquals(ClockFormatPreference.HOUR_24, ClockFormatPreference.fromString("HOUR_24"))
    }

    @Test
    fun `extended chart labels honor the chosen clock`() {
        val value = LocalTime.of(21, 42)
        assertEquals("9:42 PM", value.format(WeatherTimeFormat.clock(Locale.FRANCE, ClockFormatPreference.HOUR_12)))
        assertEquals("21:42", value.format(WeatherTimeFormat.clock(Locale.US, ClockFormatPreference.HOUR_24)))
    }

    @Test
    fun `switching language does not override chosen clock format`() {
        val time = LocalTime.of(18, 35)
        val languages = listOf(Locale.US, Locale.FRANCE, Locale.GERMANY,
            Locale.ITALY, Locale.forLanguageTag("es-ES"), Locale.UK)
        // DataStore stores the choice independently; only the Locale changes
        // when the activity is recreated after a language selection.
        for (preference in listOf(ClockFormatPreference.HOUR_12, ClockFormatPreference.HOUR_24)) {
            val expected = if (preference == ClockFormatPreference.HOUR_12) "6:35 PM" else "18:35"
            for (locale in languages) {
                assertEquals(expected, time.format(WeatherTimeFormat.clock(locale, preference)))
            }
        }
    }

    @Test
    fun `formatting respects destination city timezone and daylight saving time`() {
        val zone = ZoneId.of("Europe/Paris")
        val beforeJump = Instant.parse("2026-03-29T00:00:00Z").atZone(zone)
        val afterJump = Instant.parse("2026-03-29T01:00:00Z").atZone(zone)
        assertEquals("1 AM", beforeJump.format(WeatherTimeFormat.hour(Locale.US)))
        assertEquals("3 AM", afterJump.format(WeatherTimeFormat.hour(Locale.US)))
        assertEquals("1h", beforeJump.format(WeatherTimeFormat.hour(Locale.FRANCE)))
        assertEquals("3h", afterJump.format(WeatherTimeFormat.hour(Locale.FRANCE)))
    }

    @Test
    fun `meteogram sunrise and sunset display exact minutes in city timezone`() {
        val cityZone = ZoneId.of("Europe/Paris")
        val sunrise = Instant.parse("2026-06-21T03:47:00Z").atZone(cityZone)
        val sunset = Instant.parse("2026-06-21T19:56:00Z").atZone(cityZone)
        assertEquals("05:47", sunrise.format(WeatherTimeFormat.clock(Locale.FRANCE)))
        assertEquals("21:56", sunset.format(WeatherTimeFormat.clock(Locale.FRANCE)))
        assertEquals("5:47 AM", sunrise.format(WeatherTimeFormat.clock(Locale.US)))
        assertEquals("9:56 PM", sunset.format(WeatherTimeFormat.clock(Locale.US)))
        assertEquals("05:47", sunrise.format(WeatherTimeFormat.clock(Locale.US, ClockFormatPreference.HOUR_24)))
    }

    @Test
    fun `longer axis and date labels use same clock convention`() {
        val instant = Instant.parse("2026-10-13T23:30:00Z").atZone(ZoneId.of("UTC"))
        assertEquals("Tue 13 Oct · 11:30 PM", instant.format(WeatherTimeFormat.dateClock(Locale.US)))
        assertEquals("Tue 11 PM", instant.format(WeatherTimeFormat.dayHour(Locale.US)))
        assertEquals("23:30", instant.format(WeatherTimeFormat.clock(Locale.FRANCE)))
        assertEquals("Tue\n11:30 PM", instant.format(WeatherTimeFormat.dayClockLines(Locale.UK)))
        assertEquals("13/10 · 11:30 PM", instant.format(WeatherTimeFormat.shortDateClock(Locale.UK)))
        assertTrue(instant.format(WeatherTimeFormat.dateClock(Locale.FRANCE)).endsWith("23:30"))
    }
}
