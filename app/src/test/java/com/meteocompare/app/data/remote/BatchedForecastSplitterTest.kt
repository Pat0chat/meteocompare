package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires du splitter batched → per-modèle.
 *
 * Cas couverts :
 *   1. Mode single-modèle : variables NON suffixées → assignées au seul modèle
 *      demandé (fallback historique de l'API Open-Meteo).
 *   2. Mode multi-modèles : variables SUFFIXÉES par apiKey → mapping correct
 *      entre suffixe et modèle.
 *   3. Modèle absent de la réponse : filtré du résultat (typiquement modèle
 *      régional hors zone).
 *   4. Modèle présent mais avec toutes valeurs null : filtré (aucune donnée
 *      exploitable pour l'UI).
 *   5. Alignement temporel : le `time` unifié est partagé entre tous les
 *      DTOs reconstruits (invariant Open-Meteo).
 *   6. Robustesse : JsonNull dans les tableaux, éléments manquants ne
 *      cassent pas la sortie.
 */
class BatchedForecastSplitterTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ─────────────────────── Mode single-modèle ───────────────────────

    @Test
    fun `single-model - variables non-suffixees assignees au seul modele demande`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 48.85,
              "longitude": 2.35,
              "timezone": "Europe/Paris",
              "hourly": {
                "time": ["2026-06-23T00:00","2026-06-23T01:00"],
                "temperature_2m": [20.0, 21.5],
                "precipitation": [0.0, 0.2]
              },
              "daily": {
                "time": ["2026-06-23"],
                "temperature_2m_max": [24.0],
                "temperature_2m_min": [15.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(response, listOf(WeatherModel.GFS))

        assertEquals(setOf(WeatherModel.GFS), split.keys)
        val gfs = split.getValue(WeatherModel.GFS)
        assertEquals(listOf("2026-06-23T00:00", "2026-06-23T01:00"), gfs.hourly?.time)
        assertEquals(listOf(20.0, 21.5), gfs.hourly?.temperature2m)
        assertEquals(listOf(0.0, 0.2), gfs.hourly?.precipitation)
        assertEquals(listOf(24.0), gfs.daily?.temperature2mMax)
    }

    // ─────────────────────── Mode multi-modèles ───────────────────────

    @Test
    fun `multi-models - suffixes mappes au bon modele`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 48.85,
              "longitude": 2.35,
              "timezone": "Europe/Paris",
              "hourly": {
                "time": ["2026-06-23T00:00","2026-06-23T01:00"],
                "temperature_2m_gfs_seamless":  [20.0, 21.5],
                "temperature_2m_ecmwf_ifs025":  [19.5, 21.0],
                "precipitation_gfs_seamless":   [0.0, 0.2],
                "precipitation_ecmwf_ifs025":   [0.0, 0.1]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.GFS, WeatherModel.ECMWF)
        )

        assertEquals(setOf(WeatherModel.GFS, WeatherModel.ECMWF), split.keys)
        assertEquals(listOf(20.0, 21.5), split.getValue(WeatherModel.GFS).hourly?.temperature2m)
        assertEquals(listOf(19.5, 21.0), split.getValue(WeatherModel.ECMWF).hourly?.temperature2m)
        assertEquals(listOf(0.0, 0.2), split.getValue(WeatherModel.GFS).hourly?.precipitation)
        assertEquals(listOf(0.0, 0.1), split.getValue(WeatherModel.ECMWF).hourly?.precipitation)
    }

    @Test
    fun `multi-models - time partage entre tous les DTOs reconstruits`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00","2026-06-23T01:00","2026-06-23T02:00"],
                "temperature_2m_gfs_seamless":  [20.0, 21.0, 22.0],
                "temperature_2m_ecmwf_ifs025":  [19.0, 20.0, 21.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.GFS, WeatherModel.ECMWF)
        )

        val expectedTime = listOf(
            "2026-06-23T00:00", "2026-06-23T01:00", "2026-06-23T02:00"
        )
        assertEquals(expectedTime, split.getValue(WeatherModel.GFS).hourly?.time)
        assertEquals(expectedTime, split.getValue(WeatherModel.ECMWF).hourly?.time)
    }

    // ─────────────────────── Filtrage modèles vides ───────────────────

    @Test
    fun `modele absent de la reponse - filtre du resultat`() {
        // On demande GFS + ECMWF, l'API ne retourne QUE GFS
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00"],
                "temperature_2m_gfs_seamless": [20.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.GFS, WeatherModel.ECMWF)
        )

        // ECMWF n'a pas de temperature_2m → considéré "no usable data" → filtré
        assertEquals(setOf(WeatherModel.GFS), split.keys)
        assertFalse("ECMWF ne doit pas être dans le split", WeatherModel.ECMWF in split)
    }

    @Test
    fun `modele present mais toutes valeurs null - filtre`() {
        // Modèle régional hors zone : Open-Meteo renvoie l'entrée mais toutes null
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00","2026-06-23T01:00"],
                "temperature_2m_gfs_seamless":         [20.0, 21.0],
                "temperature_2m_meteofrance_arome_france_hd": [null, null]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.GFS, WeatherModel.AROME_FRANCE_HD)
        )

        assertTrue(WeatherModel.GFS in split)
        assertFalse(
            "AROME hors zone avec temperature entièrement null doit être filtré",
            WeatherModel.AROME_FRANCE_HD in split
        )
    }

    // ─────────────────────── Robustesse ───────────────────

    @Test
    fun `JsonNull dans les tableaux - converti en null cote Kotlin`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00","2026-06-23T01:00","2026-06-23T02:00"],
                "temperature_2m_gfs_seamless": [20.0, null, 21.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(response, listOf(WeatherModel.GFS))
        assertEquals(
            listOf(20.0, null, 21.0),
            split.getValue(WeatherModel.GFS).hourly?.temperature2m
        )
    }

    @Test
    fun `timestamp malforme conserve sa position pour ne pas decaler les valeurs`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00", null, "2026-06-23T02:00"],
                "temperature_2m_gfs_seamless": [20.0, 99.0, 22.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(response, listOf(WeatherModel.GFS))

        assertEquals(
            listOf("2026-06-23T00:00", "", "2026-06-23T02:00"),
            split.getValue(WeatherModel.GFS).hourly?.time
        )
        assertEquals(
            listOf(20.0, 99.0, 22.0),
            split.getValue(WeatherModel.GFS).hourly?.temperature2m
        )
    }

    @Test
    fun `hourly absent de la reponse - dto reconstruit avec hourly null`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "daily": {
                "time": ["2026-06-23"],
                "temperature_2m_max_gfs_seamless": [24.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(response, listOf(WeatherModel.GFS))
        // Pas d'hourly.temperature_2m → filtré comme "no usable data"
        // (invariant de hasNoUsableData : sans température horaire, l'UI ne peut
        // rien afficher d'utile de toute façon)
        assertTrue("Sans hourly.temperature, le modèle est filtré", split.isEmpty())
    }

    @Test
    fun `variables absentes pour un modele - null dans le DTO reconstruit`() {
        // GFS a tout, AROME HD n'a pas precipitation (variable optionnelle)
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00"],
                "temperature_2m_gfs_seamless": [20.0],
                "precipitation_gfs_seamless": [0.0],
                "temperature_2m_meteofrance_arome_france_hd": [21.5]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.GFS, WeatherModel.AROME_FRANCE_HD)
        )

        val arome = split.getValue(WeatherModel.AROME_FRANCE_HD)
        assertNotNull(arome.hourly?.temperature2m)
        // Precipitation absente pour AROME → null (variable manquante)
        assertNull(arome.hourly?.precipitation)

        val gfs = split.getValue(WeatherModel.GFS)
        assertEquals(listOf(0.0), gfs.hourly?.precipitation)
    }
}
