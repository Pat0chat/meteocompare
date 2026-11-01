package com.meteocompare.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CityLocationDetailsTest {
    @Test
    fun `location details separate country region district municipality and postcode`() {
        val city = City(
            id = "1", name = "Saint-Martin", country = "France",
            latitude = 0.0, longitude = 0.0,
            admin1 = "Normandie", departmentName = "Manche",
            admin3 = "Avranches", admin4 = "Saint-Martin", postalCode = "50300"
        )
        assertEquals(listOf("France", "Normandie", "Manche", "Avranches", "50300"), city.locationDetails)
    }

    @Test
    fun `missing and duplicated administrative details are not displayed`() {
        val city = City(
            id = "2", name = "Berlin", country = "Deutschland",
            latitude = 0.0, longitude = 0.0,
            admin1 = " berlin ", departmentName = "Deutschland", admin3 = ""
        )
        assertEquals(listOf("Deutschland"), city.locationDetails)
    }

    @Test
    fun `old city json stays compatible with new optional fields`() {
        val city = Json.decodeFromString<City>("""{"id":"1","name":"Paris","country":"France","latitude":48.85,"longitude":2.35}""")
        assertEquals(listOf("France"), city.locationDetails)
        assertEquals(null, city.admin3)
        assertEquals(null, city.postalCode)
    }
}
