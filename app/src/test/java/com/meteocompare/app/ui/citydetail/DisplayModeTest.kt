package com.meteocompare.app.ui.citydetail

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Contrats temporels partagés par la chronologie et les tableaux détaillés. */
class DisplayModeTest {

    @Test
    fun `resolveCityZone falls back to UTC for invalid timezone`() {
        assertEquals("UTC", resolveCityZone("not/a-timezone").id)
        assertEquals("UTC", resolveCityZone(null).id)
        assertEquals("Europe/Paris", resolveCityZone("Europe/Paris").id)
    }

    @Test
    fun `cityLocalDate uses the city timezone and not the device timezone`() {
        val now = Instant.parse("2026-01-01T11:30:00Z")

        assertEquals(LocalDate.parse("2026-01-02"), cityLocalDate("Pacific/Auckland", now))
        assertEquals(LocalDate.parse("2026-01-01"), cityLocalDate("Europe/Paris", now))
    }

    @Test
    fun `cityLocalDate uses UTC when timezone is invalid`() {
        val now = Instant.parse("2026-01-01T23:30:00Z")
        assertEquals(LocalDate.parse("2026-01-01"), cityLocalDate("invalid", now))
    }

    @Test
    fun `hourly horizon starts at the current city hour and spans 24 hours`() {
        val now = Instant.parse("2026-07-24T18:37:20Z")
        val (start, endExclusive) = computeHourlyHorizon("Europe/Paris", now)

        assertEquals(Instant.parse("2026-07-24T18:00:00Z"), start)
        assertEquals(Instant.parse("2026-07-25T18:00:00Z"), endExclusive)
    }
}
