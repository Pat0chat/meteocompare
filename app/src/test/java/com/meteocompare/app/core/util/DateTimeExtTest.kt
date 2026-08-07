package com.meteocompare.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `timeline distingue les deux heures repetees au passage hiver`() {
        val parsed = parseOpenMeteoTimeline(
            listOf(
                "2026-10-25T01:00",
                "2026-10-25T02:00",
                "2026-10-25T02:00",
                "2026-10-25T03:00"
            ),
            "Europe/Paris"
        )

        assertEquals(
            listOf(
                Instant.parse("2026-10-24T23:00:00Z"),
                Instant.parse("2026-10-25T00:00:00Z"),
                Instant.parse("2026-10-25T01:00:00Z"),
                Instant.parse("2026-10-25T02:00:00Z")
            ),
            parsed
        )
    }

    @Test
    fun `timeline rejette une heure locale inexistante au passage ete`() {
        val parsed = parseOpenMeteoTimeline(
            listOf("2026-03-29T01:00", "2026-03-29T02:00", "2026-03-29T03:00"),
            "Europe/Paris"
        )

        assertEquals(Instant.parse("2026-03-29T00:00:00Z"), parsed[0])
        assertNull(parsed[1])
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), parsed[2])
    }

    @Test
    fun `api timezone uses auto for absent or invalid favorite timezone`() {
        assertEquals("auto", apiTimezoneOrAuto(null))
        assertEquals("auto", apiTimezoneOrAuto("not-a-timezone"))
        assertEquals("Europe/Paris", apiTimezoneOrAuto("Europe/Paris"))
    }
}
