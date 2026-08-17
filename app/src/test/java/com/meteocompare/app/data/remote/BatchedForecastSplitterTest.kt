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
                "temperature_2m_ncep_gfs_seamless":  [20.0, 21.5],
                "temperature_2m_ecmwf_ifs025":  [19.5, 21.0],
                "precipitation_ncep_gfs_seamless":   [0.0, 0.2],
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
                "temperature_2m_ncep_gfs_seamless":  [20.0, 21.0, 22.0],
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

    @Test
    fun `multi-models - rafales suffixees et heures solaires partagees sont conservees`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 48.85, "longitude": 2.35, "timezone": "Europe/Paris",
              "hourly": {
                "time": ["2026-07-23T12:00"],
                "temperature_2m_ncep_gfs_seamless": [25.0],
                "temperature_2m_ecmwf_ifs025": [24.0],
                "wind_gusts_10m_ncep_gfs_seamless": [52.0],
                "wind_gusts_10m_ecmwf_ifs025": [48.0]
              },
              "daily": {
                "time": ["2026-07-23"],
                "temperature_2m_max_ncep_gfs_seamless": [28.0],
                "temperature_2m_max_ecmwf_ifs025": [27.0],
                "wind_gusts_10m_max_ncep_gfs_seamless": [64.0],
                "wind_gusts_10m_max_ecmwf_ifs025": [59.0],
                "sunrise": ["2026-07-23T06:12"],
                "sunset": ["2026-07-23T21:39"]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.GFS, WeatherModel.ECMWF)
        )

        assertEquals(listOf(52.0), split.getValue(WeatherModel.GFS).hourly?.windGusts10m)
        assertEquals(listOf(48.0), split.getValue(WeatherModel.ECMWF).hourly?.windGusts10m)
        assertEquals(listOf(64.0), split.getValue(WeatherModel.GFS).daily?.windGusts10mMax)
        assertEquals(listOf("2026-07-23T06:12"), split.getValue(WeatherModel.GFS).daily?.sunrise)
        assertEquals(listOf("2026-07-23T21:39"), split.getValue(WeatherModel.ECMWF).daily?.sunset)
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
                "temperature_2m_ncep_gfs_seamless": [20.0]
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
                "temperature_2m_ncep_gfs_seamless":         [20.0, 21.0],
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

    @Test
    fun `AROME HD reconstruit cloud cover depuis les couches quand total absent`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 48.85, "longitude": 2.35, "timezone": "Europe/Paris",
              "hourly": {
                "time": ["2026-08-17T09:00","2026-08-17T10:00","2026-08-17T11:00"],
                "temperature_2m_meteofrance_arome_france_hd": [18.0,19.0,20.0],
                "cloud_cover_low_meteofrance_arome_france_hd": [10,20,30],
                "cloud_cover_mid_meteofrance_arome_france_hd": [40,15,20],
                "cloud_cover_high_meteofrance_arome_france_hd": [25,70,10]
              }
            }"""
        )

        val arome = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.AROME_FRANCE_HD, WeatherModel.GFS)
        ).getValue(WeatherModel.AROME_FRANCE_HD)

        assertEquals(listOf(40, 70, 30), arome.hourly?.cloudCover)
    }

    @Test
    fun `AROME HD conserve cloud cover total quand il est fourni`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 48.85, "longitude": 2.35, "timezone": "Europe/Paris",
              "hourly": {
                "time": ["2026-08-17T09:00"],
                "temperature_2m_meteofrance_arome_france_hd": [18.0],
                "cloud_cover_meteofrance_arome_france_hd": [35],
                "cloud_cover_low_meteofrance_arome_france_hd": [90],
                "cloud_cover_mid_meteofrance_arome_france_hd": [90],
                "cloud_cover_high_meteofrance_arome_france_hd": [90]
              }
            }"""
        )

        val arome = BatchedForecastSplitter.split(
            response, listOf(WeatherModel.AROME_FRANCE_HD, WeatherModel.GFS)
        ).getValue(WeatherModel.AROME_FRANCE_HD)

        assertEquals(listOf(35), arome.hourly?.cloudCover)
    }

    // ─────────────────────── Robustesse ───────────────────

    @Test
    fun `JsonNull dans les tableaux - converti en null cote Kotlin`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00","2026-06-23T01:00","2026-06-23T02:00"],
                "temperature_2m_ncep_gfs_seamless": [20.0, null, 21.0]
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
                "temperature_2m_ncep_gfs_seamless": [20.0, 99.0, 22.0]
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
                "temperature_2m_max_ncep_gfs_seamless": [24.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(response, listOf(WeatherModel.GFS))
        assertEquals(setOf(WeatherModel.GFS), split.keys)
        assertNull(split.getValue(WeatherModel.GFS).hourly)
        assertEquals(listOf(24.0), split.getValue(WeatherModel.GFS).daily?.temperature2mMax)
    }

    @Test
    fun `valeurs sans axe temporel aligne - modele filtre`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": [],
                "temperature_2m_ncep_gfs_seamless": [20.0]
              },
              "daily": {
                "time": [null],
                "temperature_2m_max_ncep_gfs_seamless": [24.0]
              }
            }"""
        )

        val split = BatchedForecastSplitter.split(response, listOf(WeatherModel.GFS))

        assertTrue(split.isEmpty())
    }

    @Test
    fun `variables absentes pour un modele - null dans le DTO reconstruit`() {
        // GFS a tout, AROME HD n'a pas precipitation (variable optionnelle)
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00"],
                "temperature_2m_ncep_gfs_seamless": [20.0],
                "precipitation_ncep_gfs_seamless": [0.0],
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
    @Test
    fun `GEM accepte la cle canonique actuelle et ancien suffixe en compatibilite`() {
        val canonical = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00"],
                "temperature_2m_cmc_gem_gdps": [17.0]
              }
            }"""
        )
        val legacy = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00"],
                "temperature_2m_gem_global": [18.0]
              }
            }"""
        )

        assertEquals(
            listOf(17.0),
            BatchedForecastSplitter.split(canonical, listOf(WeatherModel.GEM_GLOBAL))
                .getValue(WeatherModel.GEM_GLOBAL).hourly?.temperature2m
        )
        assertEquals(
            listOf(18.0),
            BatchedForecastSplitter.split(legacy, listOf(WeatherModel.GEM_GLOBAL))
                .getValue(WeatherModel.GEM_GLOBAL).hourly?.temperature2m
        )
    }

    @Test
    fun `entiers encodes en nombre decimal exact restent lisibles`() {
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 0.0, "longitude": 0.0, "timezone": "UTC",
              "hourly": {
                "time": ["2026-06-23T00:00"],
                "temperature_2m_ncep_gfs_seamless": [20.0],
                "cloud_cover_ncep_gfs_seamless": [55.0],
                "weather_code_ncep_gfs_seamless": [2.0]
              }
            }"""
        )

        val dto = BatchedForecastSplitter.split(response, listOf(WeatherModel.GFS))
            .getValue(WeatherModel.GFS)
        assertEquals(listOf(55), dto.hourly?.cloudCover)
        assertEquals(listOf(2), dto.hourly?.weatherCode)
    }

}
