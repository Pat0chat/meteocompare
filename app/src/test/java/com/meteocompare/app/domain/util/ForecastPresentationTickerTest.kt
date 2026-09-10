package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastPresentationTickerTest {

    private val forecast = CityForecast(
        city = City(
            id = "paris",
            name = "Paris",
            country = "France",
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris"
        ),
        seriesByModel = emptyMap()
    )

    @Test
    fun `ticker saligne exactement sur la prochaine minute`() {
        assertEquals(
            60_000L,
            nextMinuteBoundaryDelayMs(Instant.parse("2026-06-28T12:00:00Z"))
        )
        assertEquals(
            29_750L,
            nextMinuteBoundaryDelayMs(Instant.parse("2026-06-28T12:00:30.250Z"))
        )
    }

    @Test
    fun `deux instants dans la meme echeance ne demandent aucun recalcul`() {
        assertFalse(
            hasForecastPresentationChanged(
                forecast = forecast,
                previouslyCalculatedAt = Instant.parse("2026-06-28T12:05:00Z"),
                now = Instant.parse("2026-06-28T12:25:00Z")
            )
        )
    }

    @Test
    fun `franchir le milieu de lheure avance le slot affiche`() {
        assertTrue(
            hasForecastPresentationChanged(
                forecast = forecast,
                previouslyCalculatedAt = Instant.parse("2026-06-28T12:30:00Z"),
                now = Instant.parse("2026-06-28T12:31:00Z")
            )
        )
    }

    @Test
    fun `heure dhiver repetee reste distinguee par son instant absolu`() {
        val firstLocalTwoTwenty = Instant.parse("2026-10-25T00:20:00Z")
        val secondLocalTwoTwenty = Instant.parse("2026-10-25T01:20:00Z")

        assertEquals(
            forecastPresentationKey(forecast, firstLocalTwoTwenty).localDate,
            forecastPresentationKey(forecast, secondLocalTwoTwenty).localDate
        )
        assertTrue(
            hasForecastPresentationChanged(
                forecast,
                firstLocalTwoTwenty,
                secondLocalTwoTwenty
            )
        )
    }
}
