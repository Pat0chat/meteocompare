package com.meteocompare.app.core.network

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoClockDebugInterceptorTest {

    @Test
    fun `ecart inferieur ou egal a 30 minutes ne produit pas de warning`() {
        val serverDate = "Wed, 09 Sep 2026 08:00:00 GMT"

        assertNull(
            OpenMeteoClockDebugInterceptor.buildClockSkewWarning(
                serverDateHeader = serverDate,
                deviceNowMillis = Instant.parse("2026-09-09T08:30:00Z").toEpochMilli()
            )
        )
    }

    @Test
    fun `appareil en retard produit un ecart negatif lisible`() {
        val warning = OpenMeteoClockDebugInterceptor.buildClockSkewWarning(
            serverDateHeader = "Wed, 09 Sep 2026 08:00:00 GMT",
            deviceNowMillis = Instant.parse("2026-09-08T21:18:00Z").toEpochMilli()
        )

        assertEquals(
            "Device clock differs from server by -10h42m.\n" +
                "Forecast presentation may be incorrect.",
            warning
        )
    }

    @Test
    fun `appareil en avance produit un ecart positif lisible`() {
        val warning = OpenMeteoClockDebugInterceptor.buildClockSkewWarning(
            serverDateHeader = "Wed, 09 Sep 2026 08:00:00 GMT",
            deviceNowMillis = Instant.parse("2026-09-09T09:07:00Z").toEpochMilli()
        )

        assertTrue(warning?.contains("+1h7m") == true)
    }

    @Test
    fun `header Date invalide est ignore`() {
        assertNull(
            OpenMeteoClockDebugInterceptor.buildClockSkewWarning(
                serverDateHeader = "not-a-date",
                deviceNowMillis = 0L
            )
        )
    }

    @Test
    fun `seuls les domaines Open Meteo sont controles`() {
        assertTrue(OpenMeteoClockDebugInterceptor.isOpenMeteoHost("api.open-meteo.com"))
        assertTrue(OpenMeteoClockDebugInterceptor.isOpenMeteoHost("marine-api.open-meteo.com"))
        assertFalse(OpenMeteoClockDebugInterceptor.isOpenMeteoHost("meteocompare.app"))
        assertFalse(OpenMeteoClockDebugInterceptor.isOpenMeteoHost("open-meteo.com.example.org"))
    }
}
