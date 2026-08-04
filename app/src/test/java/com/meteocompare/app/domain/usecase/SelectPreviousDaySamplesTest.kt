package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.BiasSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SelectPreviousDaySamplesTest {

    @Test
    fun `keeps latest forecast captured on previous local day`() {
        val target = LocalDate.of(2026, 8, 4)
        val samples = listOf(
            sample(target, "2026-08-02T20:00:00Z", 10.0),
            sample(target, "2026-08-03T08:00:00Z", 11.0),
            sample(target, "2026-08-03T20:00:00Z", 12.0),
            sample(target, "2026-08-04T06:00:00Z", 13.0)
        )

        val selected = selectPreviousDaySamples(samples, "Europe/Paris")

        assertEquals(1, selected.size)
        assertEquals(12.0, selected.single().forecast, 0.0)
    }

    @Test
    fun `excludes historical values learned after target date`() {
        val target = LocalDate.of(2026, 7, 10)
        val selected = selectPreviousDaySamples(
            listOf(sample(target, "2026-08-01T10:00:00Z", 20.0)),
            "Europe/Paris"
        )
        assertTrue(selected.isEmpty())
    }

    @Test
    fun `uses city timezone around midnight`() {
        val target = LocalDate.of(2026, 8, 5)
        val selected = selectPreviousDaySamples(
            listOf(sample(target, "2026-08-03T23:30:00Z", 25.0)),
            "Pacific/Kiritimati"
        )
        assertEquals(1, selected.size)
    }

    private fun sample(target: LocalDate, issuedAt: String, forecast: Double) = BiasSample(
        targetDate = target,
        forecast = forecast,
        observation = 9.0,
        issuedAt = Instant.parse(issuedAt)
    )
}
