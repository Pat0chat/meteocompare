package com.meteocompare.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityFranceEligibilityTest {

    @Test
    fun `code ISO FR est la source de verite`() {
        assertTrue(city(country = "Whatever", countryCode = "fr").isFrenchLocation)
        assertFalse(city(country = "France", countryCode = "GB").isFrenchLocation)
    }

    @Test
    fun `favoris francais legacy restent eligibles dans les langues supportees`() {
        listOf("France", "Frankreich", "Francia").forEach { label ->
            assertTrue(city(country = label, countryCode = null).isFrenchLocation)
        }
    }

    @Test
    fun `ville etrangere legacy ne devient jamais eligible par defaut`() {
        assertFalse(city(country = "Germany", countryCode = null).isFrenchLocation)
        assertFalse(city(country = "United Kingdom", countryCode = null).isFrenchLocation)
    }

    private fun city(country: String, countryCode: String?) = City(
        id = "1",
        name = "Test",
        country = country,
        latitude = 0.0,
        longitude = 0.0,
        countryCode = countryCode
    )
}
