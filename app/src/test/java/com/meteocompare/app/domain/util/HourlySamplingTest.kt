package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HourlySamplingTest {

    @Test
    fun `arrondit vers echeance suivante apres la demi heure locale`() {
        val forecast = forecast(timezone = "Europe/Paris")
        val now = Instant.parse("2026-07-15T12:56:00Z") // 14:56 à Paris

        assertEquals(
            Instant.parse("2026-07-15T13:00:00Z"), // 15:00 à Paris
            HourlySampling.anchor(forecast, now)
        )
    }

    @Test
    fun `respecte les fuseaux a demi heure au lieu darrondir en UTC`() {
        val forecast = forecast(timezone = "Asia/Kolkata")
        val now = Instant.parse("2026-07-15T12:10:00Z") // 17:40 en Inde

        assertEquals(
            Instant.parse("2026-07-15T12:30:00Z"), // 18:00 locale
            HourlySampling.anchor(forecast, now)
        )
    }


    @Test
    fun `conserve loffset de la seconde heure lors du passage a lheure dhiver`() {
        val forecast = forecast(timezone = "Europe/Paris")
        // 25/10/2026 02:20 CET = seconde occurrence de 02h après le recul
        // de l'horloge. L'ancrage correct est 02:00 CET (01:00Z), pas la
        // première occurrence 02:00 CEST (00:00Z).
        val now = Instant.parse("2026-10-25T01:20:00Z")

        assertEquals(
            Instant.parse("2026-10-25T01:00:00Z"),
            HourlySampling.anchor(forecast, now)
        )
    }

    @Test
    fun `egalite exacte a trente minutes choisit echeance precedente`() {
        val forecast = forecast(timezone = "UTC")
        val now = Instant.parse("2026-07-15T12:30:00Z")

        assertEquals(Instant.parse("2026-07-15T12:00:00Z"), HourlySampling.anchor(forecast, now))
    }

    private fun forecast(timezone: String) = CityForecast(
        city = City(
            id = "1",
            name = "Test",
            country = "Test",
            latitude = 0.0,
            longitude = 0.0,
            timezone = timezone
        ),
        seriesByModel = emptyMap()
    )
}
