package com.meteocompare.app.domain.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VigilanceForecastTest {

    @Test
    fun `past red interval does not keep current alert red`() {
        val fetchedAt = Instant.parse("2026-08-29T12:00:00Z")
        val alert = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.THUNDERSTORMS,
            maxColor = VigilanceColor.RED,
            intervals = listOf(
                VigilanceInterval(
                    begin = Instant.parse("2026-08-29T06:00:00Z"),
                    end = Instant.parse("2026-08-29T10:00:00Z"),
                    color = VigilanceColor.RED,
                    scope = VigilanceScope.DEPARTMENT
                ),
                VigilanceInterval(
                    begin = Instant.parse("2026-08-29T10:00:00Z"),
                    end = Instant.parse("2026-08-29T18:00:00Z"),
                    color = VigilanceColor.YELLOW,
                    scope = VigilanceScope.DEPARTMENT
                )
            )
        )

        val forecast = forecast(fetchedAt, alert)

        assertEquals(VigilanceColor.YELLOW, forecast.maxAlertColor)
        assertEquals(1, forecast.activeAlerts.single().intervals.size)
        assertEquals(VigilanceColor.YELLOW, forecast.activeAlerts.single().intervals.single().color)
    }

    @Test
    fun `fully expired alert is not displayed`() {
        val fetchedAt = Instant.parse("2026-08-29T12:00:00Z")
        val alert = VigilancePhenomenonAlert(
            phenomenon = VigilancePhenomenon.WIND,
            maxColor = VigilanceColor.ORANGE,
            intervals = listOf(
                VigilanceInterval(
                    begin = Instant.parse("2026-08-29T06:00:00Z"),
                    end = Instant.parse("2026-08-29T11:00:00Z"),
                    color = VigilanceColor.ORANGE,
                    scope = VigilanceScope.DEPARTMENT
                )
            )
        )

        assertTrue(forecast(fetchedAt, alert).activeAlerts.isEmpty())
    }

    private fun forecast(
        fetchedAt: Instant,
        alert: VigilancePhenomenonAlert
    ) = VigilanceForecast(
        source = "Météo-France",
        department = "91",
        includeCoast = false,
        updateTime = fetchedAt,
        productDatetime = fetchedAt,
        generationTimestamp = fetchedAt,
        periods = listOf(
            VigilancePeriod(
                term = "J",
                begin = fetchedAt.minusSeconds(21_600),
                end = fetchedAt.plusSeconds(43_200),
                maxColor = alert.maxColor,
                departmentMaxColor = alert.maxColor,
                coastMaxColor = null,
                phenomena = listOf(alert)
            )
        ),
        fetchedAt = fetchedAt
    )
}
