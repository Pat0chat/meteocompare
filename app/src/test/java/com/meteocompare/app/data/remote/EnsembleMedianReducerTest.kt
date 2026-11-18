package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EnsembleMedianReducerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun response(hourly: String, daily: String? = null) =
        json.decodeFromString(
            BatchedForecastResponseDto.serializer(),
            """{
              "latitude": 48.75, "longitude": 2.25, "timezone": "Europe/Paris",
              "hourly": $hourly
              ${daily?.let { ""","daily": $it""" } ?: ""}
            }"""
        )

    @Test
    fun `mediane des membres - nombre impair et pair`() {
        val reduced = EnsembleMedianReducer.reduce(
            response(
                """{
                  "time": ["t0", "t1"],
                  "temperature_2m": [10.0, 1.0],
                  "temperature_2m_member01": [30.0, 2.0],
                  "temperature_2m_member02": [12.0, 3.0],
                  "temperature_2m_member03": [11.0, 4.0]
                }"""
            )
        )

        val hourly = requireNotNull(reduced.hourly)
        // [10, 30, 12, 11] -> 11.5 ; [1, 2, 3, 4] -> 2.5
        assertEquals("[11.5,2.5]", hourly.getValue("temperature_2m").toString())
        assertFalse(hourly.keys.any { "_member" in it })
        assertEquals("[\"t0\",\"t1\"]", hourly.getValue("time").toString())
    }

    @Test
    fun `multi-modeles - membres regroupes par modele`() {
        val wn = WeatherModel.GOOGLE_WEATHERNEXT2.apiKey
        val reduced = EnsembleMedianReducer.reduce(
            response(
                """{
                  "time": ["t0"],
                  "temperature_2m_$wn": [10.0],
                  "temperature_2m_member01_$wn": [20.0],
                  "temperature_2m_member02_$wn": [30.0],
                  "temperature_2m_other_ensemble": [0.0],
                  "temperature_2m_member01_other_ensemble": [-10.0],
                  "temperature_2m_member02_other_ensemble": [-20.0]
                }"""
            )
        )

        val hourly = requireNotNull(reduced.hourly)
        assertEquals("[20.0]", hourly.getValue("temperature_2m_$wn").toString())
        assertEquals("[-10.0]", hourly.getValue("temperature_2m_other_ensemble").toString())
        assertEquals(setOf("time", "temperature_2m_$wn", "temperature_2m_other_ensemble"), hourly.keys)
    }

    @Test
    fun `direction du vent - moyenne circulaire autour du nord`() {
        val reduced = EnsembleMedianReducer.reduce(
            response(
                """{
                  "time": ["t0"],
                  "wind_direction_10m": [350],
                  "wind_direction_10m_member01": [10],
                  "wind_direction_10m_member02": [0]
                }"""
            )
        )

        // Une médiane arithmétique donnerait 10° ; une moyenne arithmétique 120°.
        assertEquals("[0]", requireNotNull(reduced.hourly).getValue("wind_direction_10m").toString())
    }

    @Test
    fun `code meteo - valeur la plus frequente puis code le plus faible`() {
        val reduced = EnsembleMedianReducer.reduce(
            response(
                """{
                  "time": ["t0", "t1"],
                  "weather_code": [61, 3],
                  "weather_code_member01": [3, 61],
                  "weather_code_member02": [61, 3],
                  "weather_code_member03": [2, 61]
                }"""
            )
        )

        assertEquals("[61,3]", requireNotNull(reduced.hourly).getValue("weather_code").toString())
    }

    @Test
    fun `pourcentages - mediane arrondie a l'entier`() {
        val reduced = EnsembleMedianReducer.reduce(
            response(
                """{
                  "time": ["t0"],
                  "cloud_cover": [40],
                  "cloud_cover_member01": [45],
                  "cloud_cover_member02": [100],
                  "cloud_cover_member03": [0]
                }"""
            )
        )

        // [0, 40, 45, 100] -> 42.5 -> 43, lisible par le splitter (entier).
        assertEquals("[43]", requireNotNull(reduced.hourly).getValue("cloud_cover").toString())
    }

    @Test
    fun `majorite de membres sans valeur - echeance laissee a null`() {
        val reduced = EnsembleMedianReducer.reduce(
            response(
                """{
                  "time": ["t0", "t1"],
                  "temperature_2m": [10.0, null],
                  "temperature_2m_member01": [null, null],
                  "temperature_2m_member02": [12.0, 5.0]
                }"""
            )
        )

        val values = requireNotNull(reduced.hourly).getValue("temperature_2m").jsonArray
        assertEquals("11.0", values[0].toString())
        assertEquals(JsonNull, values[1])
    }

    @Test
    fun `variables sans membres et reponse sans ensemble - inchangees`() {
        val original = response(
            hourly = """{ "time": ["t0"], "temperature_2m": [10.0] }""",
            daily = """{
              "time": ["2026-11-18"],
              "sunrise": ["2026-11-18T08:04"],
              "temperature_2m_max": [12.0],
              "temperature_2m_max_member01": [14.0]
            }"""
        )

        val reduced = EnsembleMedianReducer.reduce(original)

        assertEquals(original.hourly, reduced.hourly)
        val daily = requireNotNull(reduced.daily)
        assertEquals("[\"2026-11-18T08:04\"]", daily.getValue("sunrise").toString())
        assertEquals("[13.0]", daily.getValue("temperature_2m_max").toString())
    }

    @Test
    fun `sortie compatible avec le splitter`() {
        val reduced = EnsembleMedianReducer.reduce(
            response(
                """{
                  "time": ["2026-11-18T00:00"],
                  "temperature_2m": [10.0],
                  "temperature_2m_member01": [14.0],
                  "temperature_2m_member02": [12.0],
                  "weather_code": [3],
                  "weather_code_member01": [61],
                  "weather_code_member02": [61]
                }"""
            )
        )

        val dto = BatchedForecastSplitter.split(reduced, listOf(WeatherModel.GOOGLE_WEATHERNEXT2))
            .getValue(WeatherModel.GOOGLE_WEATHERNEXT2)
        assertEquals(listOf(12.0), dto.hourly?.temperature2m)
        assertEquals(listOf(61), dto.hourly?.weatherCode)
    }

    @Test
    fun `moyenne circulaire - vecteurs opposes sans direction`() {
        assertNull(EnsembleMedianReducer.circularMean(listOf(90.0, 270.0)))
    }
}
