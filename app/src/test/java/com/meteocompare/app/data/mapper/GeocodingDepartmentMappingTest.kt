package com.meteocompare.app.data.mapper

import com.meteocompare.app.data.remote.dto.GeocodingResultDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeocodingDepartmentMappingTest {

    @Test
    fun `french geocoding result stores department code for vigilance`() {
        val city = GeocodingResultDto(
            id = 3012683,
            name = "Gif-sur-Yvette",
            latitude = 48.70,
            longitude = 2.13,
            country = "France",
            countryCode = "FR",
            admin1 = "Île-de-France",
            admin2 = "Essonne",
            postcodes = listOf("91190"),
            timezone = "Europe/Paris"
        ).toDomain()

        assertEquals("FR", city.countryCode)
        assertEquals("Essonne", city.departmentName)
        assertEquals("91", city.departmentCode)
    }

    @Test
    fun `non french result never receives a french department code`() {
        val city = GeocodingResultDto(
            id = 2950159,
            name = "Berlin",
            latitude = 52.52,
            longitude = 13.41,
            country = "Deutschland",
            countryCode = "DE",
            admin1 = "Berlin",
            postcodes = listOf("10967"),
            timezone = "Europe/Berlin"
        ).toDomain()

        assertNull(city.departmentCode)
    }
}
