package com.meteocompare.app.data.mapper

import com.meteocompare.app.data.remote.dto.MarineHourlyDto
import com.meteocompare.app.data.remote.dto.MarineResponseDto
import com.meteocompare.app.domain.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineMapperTest {
    private val city = City(
        id = "coast",
        name = "Coast",
        country = "France",
        latitude = 43.30,
        longitude = 5.37,
        timezone = "Europe/Paris"
    )

    @Test
    fun `point marin proche avec six vagues est cotier`() {
        val data = response(lat = 43.31, lon = 5.38, waveCount = 6).toDomain(city, 123L)
        assertTrue(data.coastal)
        assertEquals(6, data.usablePoints)
        assertEquals(123L, data.fetchedAtEpochMs)
    }

    @Test
    fun `moins de six hauteurs de vagues refuse activation`() {
        val data = response(lat = 43.31, lon = 5.38, waveCount = 5).toDomain(city, 123L)
        assertFalse(data.coastal)
    }

    @Test
    fun `point marin trop distant refuse activation`() {
        val data = response(lat = 44.0, lon = 6.0, waveCount = 8).toDomain(city, 123L)
        assertFalse(data.coastal)
    }

    @Test
    fun `agregation journaliere conserve un horizon partiel`() {
        val raw = MarineResponseDto(
            latitude = 43.31,
            longitude = 5.38,
            timezone = "Europe/Paris",
            hourly = MarineHourlyDto(
                time = listOf("2026-08-18T18:00", "2026-08-18T19:00", "2026-08-19T00:00"),
                waveHeight = listOf(1.0, 1.4, 0.8),
                waveDirection = listOf(350.0, 10.0, 90.0),
                wavePeriod = listOf(6.0, 7.0, 5.0),
                swellHeight = listOf(0.4, 0.5, 0.3),
                swellDirection = listOf(340.0, 20.0, 80.0),
                swellPeriod = listOf(8.0, 9.0, 7.0),
                seaSurfaceTemperature = listOf(24.0, 24.1, 23.9),
                seaLevelHeightMsl = listOf(0.1, 0.2, 0.0)
            )
        )
        val data = raw.toDomain(city, 123L)
        assertEquals(listOf("2026-08-18", "2026-08-19"), data.daily.dates)
        assertEquals(1.4, data.daily.waveHeightMax[0] ?: -1.0, 0.0001)
        val dominant = data.daily.waveDirectionDominant[0] ?: -1.0
        assertTrue(dominant < 20.0 || dominant > 340.0)
    }

    @Test
    fun `hauteurs negatives et timestamps invalides ne rendent pas le point cotier`() {
        val raw = MarineResponseDto(
            latitude = 43.31,
            longitude = 5.38,
            timezone = "Europe/Paris",
            hourly = MarineHourlyDto(
                time = List(7) { index -> if (index == 6) "invalide" else "2026-08-18T%02d:00".format(10 + index) },
                waveHeight = listOf(-1.0, -2.0, -0.5, -4.0, -3.0, -2.0, 1.0),
                waveDirection = List(7) { 180.0 },
                wavePeriod = List(7) { 6.0 }
            )
        )

        val data = raw.toDomain(city, 123L)

        assertFalse(data.coastal)
        assertEquals(0, data.usablePoints)
        assertEquals(null, data.daily.waveHeightMax.singleOrNull())
    }

    private fun response(lat: Double, lon: Double, waveCount: Int): MarineResponseDto {
        val count = 8
        return MarineResponseDto(
            latitude = lat,
            longitude = lon,
            timezone = "Europe/Paris",
            hourly = MarineHourlyDto(
                time = (0 until count).map { "2026-08-18T%02d:00".format(10 + it) },
                waveHeight = (0 until count).map { if (it < waveCount) 1.0 else null },
                waveDirection = List(count) { 180.0 },
                wavePeriod = List(count) { 6.0 },
                swellHeight = List(count) { 0.5 },
                swellDirection = List(count) { 170.0 },
                swellPeriod = List(count) { 8.0 },
                seaSurfaceTemperature = List(count) { 22.0 },
                seaLevelHeightMsl = List(count) { 0.0 }
            )
        )
    }
}
