package com.meteocompare.app.data.repository

import com.meteocompare.app.data.remote.dto.ArchiveDailyDto
import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la logique d'agrégation des normales.
 *
 * `aggregate` est exposée en `internal` dans le companion object — testable
 * sans instancier le repository (qui demanderait des mocks d'API et de DAO),
 * et accessible depuis ce module sans réflexion.
 */
class ClimateNormalsAggregationTest {

    @Test
    fun `moyenne sur 3 ans pour le 15 juin - max convergent`() {
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-06-15", "2023-06-15", "2024-06-15"),
                tempMax = listOf(25.0, 27.0, 26.0),  // moyenne = 26.0
                tempMin = listOf(15.0, 16.0, 14.0)   // moyenne = 15.0
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response)
        assertEquals(1, result.size)
        val june15 = result.first()
        assertEquals(6, june15.month)
        assertEquals(15, june15.day)
        assertEquals(26.0, june15.tempMaxNormal, 0.001)
        assertEquals(15.0, june15.tempMinNormal, 0.001)
    }

    @Test
    fun `jours avec donnees null sont ignores dans la moyenne`() {
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-01-01", "2023-01-01", "2024-01-01"),
                tempMax = listOf(5.0, null, 7.0),    // moyenne = 6.0 sur 2 obs
                tempMin = listOf(0.0, null, 2.0)     // moyenne = 1.0 sur 2 obs
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response)
        assertEquals(1, result.size)
        assertEquals(6.0, result.first().tempMaxNormal, 0.001)
        assertEquals(1.0, result.first().tempMinNormal, 0.001)
    }

    @Test
    fun `resultats tries par month puis day`() {
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-12-31", "2022-01-01", "2022-06-15"),
                tempMax = listOf(2.0, 3.0, 25.0),
                tempMin = listOf(-2.0, -1.0, 15.0)
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response)
        assertEquals(3, result.size)
        assertEquals(1 to 1, result[0].month to result[0].day)
        assertEquals(6 to 15, result[1].month to result[1].day)
        assertEquals(12 to 31, result[2].month to result[2].day)
    }
}
