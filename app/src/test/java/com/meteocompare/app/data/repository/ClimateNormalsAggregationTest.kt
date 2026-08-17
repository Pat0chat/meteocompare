package com.meteocompare.app.data.repository

import com.meteocompare.app.data.remote.ClimateArchiveApi
import com.meteocompare.app.data.remote.dto.ArchiveDailyDto
import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    // ─── Régression : les overlays pluie/vent journaliers restent désactivés ───

    @Test
    fun `precipitation et vent restent absents des reperes horaires`() {
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-06-15", "2023-06-15", "2024-06-15"),
                tempMax = listOf(25.0, 27.0, 26.0),
                tempMin = listOf(15.0, 16.0, 14.0),
                precipSum = listOf(0.0, 10.0, null),      // moyenne = 5.0 sur 2 obs
                windSpeedMax = listOf(20.0, null, 30.0)   // moyenne = 25.0 sur 2 obs
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response)
        assertEquals(1, result.size)
        val june15 = result.first()
        assertNull(june15.precipMeanNormal)
        assertNull(june15.windMeanNormal)
    }

    @Test
    fun `precipitation et vent - listes null retournent des normales null`() {
        // Cas d'une API qui ne retournerait pas les variables (ex. downgrade
        // temporaire côté serveur). L'agrégation doit rester robuste et laisser
        // les champs à null plutôt que crash.
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-06-15"),
                tempMax = listOf(25.0),
                tempMin = listOf(15.0),
                precipSum = null,
                windSpeedMax = null
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response)
        assertEquals(1, result.size)
        val june15 = result.first()
        // Les températures restent renseignées
        assertEquals(25.0, june15.tempMaxNormal, 0.001)
        // Précip et vent tombent à null (pas de crash, pas de valeur bidon comme 0.0)
        org.junit.Assert.assertNull(june15.precipMeanNormal)
        org.junit.Assert.assertNull(june15.windMeanNormal)
    }
    @Test
    fun `annee thermique manquante est ignoree sans reactiver pluie vent`() {
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-06-15", "2023-06-15", "2024-06-15"),
                tempMax = listOf(25.0, null, 27.0),
                tempMin = listOf(15.0, null, 17.0),
                precipSum = listOf(0.0, 12.0, 6.0),
                windSpeedMax = listOf(20.0, 40.0, 30.0)
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response).single()

        assertEquals(26.0, result.tempMaxNormal, 0.001)
        assertEquals(16.0, result.tempMinNormal, 0.001)
        assertNull(result.precipMeanNormal)
        assertNull(result.windMeanNormal)
    }

    @Test
    fun `valeurs physiques invalides sont ignorees dans les normales`() {
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-06-15", "2023-06-15", "2024-06-15"),
                tempMax = listOf(25.0, Double.NaN, 27.0),
                tempMin = listOf(15.0, 16.0, 17.0),
                precipSum = listOf(2.0, -3.0, 4.0),
                windSpeedMax = listOf(20.0, -10.0, 30.0)
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response).single()

        // La paire thermique NaN est entièrement exclue pour 2023.
        assertEquals(26.0, result.tempMaxNormal, 0.001)
        assertEquals(16.0, result.tempMinNormal, 0.001)
        // Les overlays pluie/vent sont désactivés : ces grandeurs journalières
        // ne sont pas comparables à la bande horaire.
        assertNull(result.precipMeanNormal)
        assertNull(result.windMeanNormal)
    }

    @Test
    fun `source et variables des reperes thermiques restent explicites`() {
        assertEquals("era5", ClimateNormalsRepositoryImpl.NORMALS_REANALYSIS_MODEL)
        assertEquals(
            "temperature_2m_max,temperature_2m_min",
            ClimateArchiveApi.NORMALS_DAILY_VARS
        )
    }

    @Test
    fun `cache ERA5 utilise un namespace distinct du cache legacy`() {
        assertEquals("era5-v1", ClimateNormalsRepositoryImpl.CLIMATE_CACHE_NAMESPACE)
        assertEquals(
            "era5-v1:paris",
            ClimateNormalsRepositoryImpl.cacheCityId("paris")
        )
    }

    @Test
    fun `date malformee est ignoree sans interrompre le lot`() {
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2022-06-15", "date-invalide", "2024-06-15"),
                tempMax = listOf(25.0, 999.0, 27.0),
                tempMin = listOf(15.0, 999.0, 17.0),
                precipSum = listOf(0.0, 999.0, 6.0),
                windSpeedMax = listOf(20.0, 999.0, 30.0)
            )
        )

        val result = ClimateNormalsRepositoryImpl.aggregate(response).single()

        assertEquals(26.0, result.tempMaxNormal, 0.001)
        assertEquals(16.0, result.tempMinNormal, 0.001)
        assertNull(result.precipMeanNormal)
        assertNull(result.windMeanNormal)
    }

    @Test
    fun `payload ERA5 de dix annees quasi complet est accepte`() {
        val start = LocalDate.of(2016, 1, 1)
        val end = LocalDate.of(2025, 12, 31)
        val dates = generateSequence(start) { date ->
            date.plusDays(1).takeIf { !it.isAfter(end) }
        }.toList()
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = dates.map(LocalDate::toString),
                tempMax = List(dates.size) { 20.0 },
                tempMin = List(dates.size) { 10.0 }
            )
        )

        assertTrue(ClimateNormalsRepositoryImpl.isArchivePayloadComplete(response, start, end))
    }

    @Test
    fun `payload ERA5 d une seule annee ne peut pas ecraser le cache dix ans`() {
        val requestedStart = LocalDate.of(2016, 1, 1)
        val requestedEnd = LocalDate.of(2025, 12, 31)
        val oneYearStart = LocalDate.of(2025, 1, 1)
        val dates = generateSequence(oneYearStart) { date ->
            date.plusDays(1).takeIf { it.year == 2025 }
        }.toList()
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = dates.map(LocalDate::toString),
                tempMax = List(dates.size) { 20.0 },
                tempMin = List(dates.size) { 10.0 }
            )
        )

        assertFalse(ClimateNormalsRepositoryImpl.isArchivePayloadComplete(
            response, requestedStart, requestedEnd
        ))
    }

    @Test
    fun `payload ERA5 sans Tmin est refuse avant ecriture Room`() {
        val start = LocalDate.of(2016, 1, 1)
        val end = LocalDate.of(2025, 12, 31)
        val dates = generateSequence(start) { date ->
            date.plusDays(1).takeIf { !it.isAfter(end) }
        }.toList()
        val response = ArchiveResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = dates.map(LocalDate::toString),
                tempMax = List(dates.size) { 20.0 },
                tempMin = null
            )
        )

        assertFalse(ClimateNormalsRepositoryImpl.isArchivePayloadComplete(response, start, end))
    }

}
