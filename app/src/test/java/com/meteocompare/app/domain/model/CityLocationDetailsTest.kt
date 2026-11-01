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

    @Test
    fun `home keeps country and administrative details on at most two lines`() {
        val city = City(
            id = "saint-martin", name = "Saint-Martin", country = "France",
            admin1 = "Normandie", departmentName = "Manche", admin3 = "Avranches",
            admin4 = "Saint-Martin", postalCode = "50300",
            latitude = 48.0, longitude = -1.0
        )
        assertEquals(
            listOf("France", "Normandie · Manche · Avranches · 50300"),
            city.homeLocationLines
        )
        // No information disappears from the full detail view.
        assertEquals(5, city.locationDetails.size)
    }

    @Test
    fun `home supports city with country only or no usable location`() {
        val city = City(
            id = "1", name = "Paris", country = "France", latitude = 0.0, longitude = 0.0
        )
        assertEquals(listOf("France"), city.homeLocationLines)
        assertEquals(emptyList<String>(), city.copy(country = " ").homeLocationLines)
    }

    @Test
    fun `home does not repeat the city name or duplicate administrative levels`() {
        val city = City(
            id = "2", name = "Berlin", country = "Deutschland", admin1 = " berlin ",
            departmentName = "Deutschland", admin3 = "Mitte", admin4 = "MITTE",
            latitude = 0.0, longitude = 0.0
        )
        assertEquals(listOf("Deutschland", "Mitte"), city.homeLocationLines)
    }

}
