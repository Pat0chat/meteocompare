package com.meteocompare.app.core.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class CanonicalMetricUnitsInterceptorTest {
    @Test fun `real interceptor chain exposes only canonical query parameters without network access`() {
        var outgoing: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(CanonicalMetricUnitsInterceptor())
            .addInterceptor { chain ->
                outgoing = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("{}".toResponseBody()).build()
            }.build()
        val request = Request.Builder().url("https://api.open-meteo.com/v1/forecast?temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch").build()
        client.newCall(request).execute().use { assertEquals(200, it.code) }
        val sent = requireNotNull(outgoing).url
        assertEquals("celsius", sent.queryParameter("temperature_unit"))
        assertEquals("kmh", sent.queryParameter("wind_speed_unit"))
        assertEquals("mm", sent.queryParameter("precipitation_unit"))
    }

    @Test fun `forecasts previous runs and archives cannot request imperial payloads`() {
        val endpoints = listOf("api.open-meteo.com/v1/forecast",
            "previous-runs-api.open-meteo.com/v1/forecast", "archive-api.open-meteo.com/v1/archive")
        for (endpoint in endpoints) {
            val request = Request.Builder().url("https://$endpoint?latitude=48.2&longitude=2.1" +
                "&models=test&temperature_unit=fahrenheit&temperature_unit=celsius" +
                "&wind_speed_unit=mph&windspeed_unit=ms&precipitation_unit=inch").build()
            val normalized = canonicalMetricRequest(request)
            assertEquals(listOf("celsius"), normalized.url.queryParameterValues("temperature_unit"))
            assertEquals(listOf("kmh"), normalized.url.queryParameterValues("wind_speed_unit"))
            assertEquals(listOf("mm"), normalized.url.queryParameterValues("precipitation_unit"))
            assertNull(normalized.url.queryParameter("windspeed_unit"))
            assertEquals("48.2", normalized.url.queryParameter("latitude"))
            assertEquals("test", normalized.url.queryParameter("models"))
            assertEquals(normalized.url, canonicalMetricRequest(normalized).url)
        }
    }

    @Test fun `marine explicitly requests canonical heights and temperatures`() {
        val request = Request.Builder().url("https://marine-api.open-meteo.com/v1/marine" +
            "?length_unit=imperial&temperature_unit=fahrenheit&cell_selection=sea").build()
        val normalized = canonicalMetricRequest(request)
        assertEquals("metric", normalized.url.queryParameter("length_unit"))
        assertEquals("celsius", normalized.url.queryParameter("temperature_unit"))
        assertEquals("sea", normalized.url.queryParameter("cell_selection"))
    }

    @Test fun `missing unit parameters are explicit and unrelated services are untouched`() {
        val forecast = Request.Builder().url("https://api.open-meteo.com/v1/forecast").build()
        assertEquals("celsius", canonicalMetricRequest(forecast).url.queryParameter("temperature_unit"))
        for (url in listOf("https://geocoding-api.open-meteo.com/v1/search?name=Paris",
            "https://example.com/v1/forecast?temperature_unit=fahrenheit",
            "https://api.open-meteo.com.example.com/v1/forecast")) {
            val request = Request.Builder().url(url).build()
            assertSame(request, canonicalMetricRequest(request))
        }
    }
}
