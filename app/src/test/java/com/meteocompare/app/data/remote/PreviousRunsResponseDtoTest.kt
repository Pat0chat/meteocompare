package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import com.meteocompare.app.di.NetworkModule
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviousRunsResponseDtoTest {
    @Test
    fun `premier modele hors couverture - latitude et longitude null tolerees`() {
        val dto = NetworkModule.provideJson().decodeFromString<PreviousRunsResponseDto>(
            """{
              "latitude":null,
              "longitude":null,
              "timezone":"Africa/Nairobi",
              "hourly":{
                "time":["2026-09-26T00:00"],
                "temperature_2m_previous_day1_ncep_gfs_seamless":[17.4]
              }
            }"""
        )

        assertNull(dto.latitude)
        assertNull(dto.longitude)
        assertEquals("Africa/Nairobi", dto.timezone)
        assertEquals(1, (dto.hourly?.get("time") as JsonArray).size)
    }
}
