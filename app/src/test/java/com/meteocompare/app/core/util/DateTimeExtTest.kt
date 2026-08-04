package com.meteocompare.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateTimeExtTest {

    @Test
    fun `local date follows the city timezone around UTC midnight`() {
        val instant = Instant.parse("2026-07-23T23:30:00Z")

        assertEquals(LocalDate.of(2026, 7, 24), instant.localDateIn("Pacific/Kiritimati"))
        assertEquals(LocalDate.of(2026, 7, 23), instant.localDateIn("America/Los_Angeles"))
    }

    @Test
    fun `invalid or absent timezone falls back to UTC`() {
        assertEquals(ZoneId.of("UTC"), resolveZoneOrUtc(null))
        assertEquals(ZoneId.of("UTC"), resolveZoneOrUtc("not-a-timezone"))
    }
}
