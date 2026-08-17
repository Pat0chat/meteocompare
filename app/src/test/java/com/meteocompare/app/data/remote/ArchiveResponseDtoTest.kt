package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveResponseDtoTest {
    @Test
    fun `reponse biais sans Tmin reste deserialisable`() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<ArchiveResponseDto>(
            """{
              "latitude":48.85,
              "longitude":2.35,
              "timezone":"Europe/Paris",
              "daily":{
                "time":["2026-08-15"],
                "temperature_2m_max":[25.0],
                "precipitation_sum":[3.5],
                "wind_speed_10m_max":[22.0]
              }
            }"""
        )

        assertEquals(listOf(25.0), dto.daily.tempMax)
        assertNull(dto.daily.tempMin)
        assertEquals(listOf(3.5), dto.daily.precipSum)
        assertEquals(listOf(22.0), dto.daily.windSpeedMax)
    }
}
