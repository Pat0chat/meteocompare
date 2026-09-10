package com.meteocompare.app.core.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class MeteoCompareClientHeaderInterceptorTest {

    @Test
    fun `ajoute l'identite Android sans modifier les autres entetes`() {
        val request = Request.Builder()
            .url("https://meteocompare.app/_mcx/vigilance")
            .header("Accept", "application/json")
            .build()

        val identified = request.withMeteoCompareClientHeader()

        assertEquals("android", identified.header("X-MeteoCompare-Client"))
        assertEquals("application/json", identified.header("Accept"))
    }

    @Test
    fun `remplace toute identite precedente par une valeur Android unique`() {
        val request = Request.Builder()
            .url("https://meteocompare.app/_mcx/vigilance")
            .addHeader(METEOCOMPARE_CLIENT_HEADER_NAME, "web")
            .addHeader(METEOCOMPARE_CLIENT_HEADER_NAME, "legacy")
            .build()

        val identified = request.withMeteoCompareClientHeader()

        assertEquals(
            listOf(METEOCOMPARE_CLIENT_HEADER_VALUE),
            identified.headers.values(METEOCOMPARE_CLIENT_HEADER_NAME)
        )
    }
}
