package com.meteocompare.app.data.remote

import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.domain.model.WeatherModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Contrat de bout en bout Forecast API -> réponse batched -> splitter -> mapper domaine.
 * Les valeurs sentinelles distinctes rendent une permutation silencieuse de variable
 * immédiatement visible dans le test.
 */
class ForecastDataChainContractTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mapper = ForecastMapper()

    @Test
    fun `toutes les variables demandees survivent au splitter et au mapper sans permutation`() {
        val key = WeatherModel.GFS.apiKey
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 48.85,
              "longitude": 2.35,
              "timezone": "Europe/Paris",
              "hourly": {
                "time": ["2026-08-16T12:00"],
                "temperature_2m_${key}": [21.1],
                "precipitation_${key}": [2.2],
                "precipitation_probability_${key}": [33],
                "cloud_cover_${key}": [44],
                "wind_speed_10m_${key}": [55.5],
                "wind_direction_10m_${key}": [166],
                "wind_gusts_10m_${key}": [77.7],
                "weather_code_${key}": [61]
              },
              "daily": {
                "time": ["2026-08-16"],
                "temperature_2m_max_${key}": [28.1],
                "temperature_2m_min_${key}": [14.2],
                "precipitation_sum_${key}": [6.3],
                "precipitation_probability_max_${key}": [74],
                "wind_speed_10m_max_${key}": [35.5],
                "wind_gusts_10m_max_${key}": [86.6],
                "wind_direction_10m_dominant_${key}": [197],
                "weather_code_${key}": [63],
                "sunrise": ["2026-08-16T06:42"],
                "sunset": ["2026-08-16T21:02"]
              }
            }"""
        )

        val dto = BatchedForecastSplitter.split(
            response,
            listOf(WeatherModel.GFS, WeatherModel.ECMWF)
        )[WeatherModel.GFS]
        assertNotNull(dto)

        val series = mapper.toSeries(WeatherModel.GFS, dto!!)
        assertEquals(listOf(21.1), series.hourly.temperature2m)
        assertEquals(listOf(2.2), series.hourly.precipitation)
        assertEquals(listOf(33), series.hourly.precipitationProbability)
        assertEquals(listOf(44), series.hourly.cloudCover)
        assertEquals(listOf(55.5), series.hourly.windSpeed10m)
        assertEquals(listOf(166), series.hourly.windDirection10m)
        assertEquals(listOf(77.7), series.hourly.windGusts10m)
        assertEquals(listOf(61), series.hourly.weatherCode)

        assertEquals(listOf(LocalDate.of(2026, 8, 16)), series.daily.dates)
        assertEquals(listOf(28.1), series.daily.tempMax)
        assertEquals(listOf(14.2), series.daily.tempMin)
        assertEquals(listOf(6.3), series.daily.precipitationSum)
        assertEquals(listOf(74), series.daily.precipitationProbabilityMax)
        assertEquals(listOf(35.5), series.daily.windSpeedMax)
        assertEquals(listOf(86.6), series.daily.windGustsMax)
        assertEquals(listOf(197), series.daily.windDirection10mDominant)
        assertEquals(listOf(63), series.daily.weatherCode)
        assertEquals(
            Instant.parse("2026-08-16T04:42:00Z"),
            series.daily.sunrise.single()
        )
        assertEquals(
            Instant.parse("2026-08-16T19:02:00Z"),
            series.daily.sunset.single()
        )
    }

    @Test
    fun `ECMWF HRES 9 km traverse le pipeline avec les bons suffixes et variables`() {
        val key = WeatherModel.ECMWF.apiKey
        val response = json.decodeFromString<BatchedForecastResponseDto>(
            """{
              "latitude": 48.85,
              "longitude": 2.35,
              "timezone": "Europe/Paris",
              "hourly": {
                "time": ["2026-08-28T06:00"],
                "temperature_2m_${key}": [17.4],
                "precipitation_${key}": [1.2],
                "precipitation_probability_${key}": [64],
                "cloud_cover_${key}": [83],
                "cloud_cover_low_${key}": [72],
                "cloud_cover_mid_${key}": [41],
                "cloud_cover_high_${key}": [18],
                "wind_speed_10m_${key}": [22.5],
                "wind_direction_10m_${key}": [245],
                "wind_gusts_10m_${key}": [38.7],
                "weather_code_${key}": [61]
              },
              "daily": {
                "time": ["2026-08-28"],
                "temperature_2m_max_${key}": [21.8],
                "temperature_2m_min_${key}": [13.6],
                "precipitation_sum_${key}": [4.7],
                "precipitation_probability_max_${key}": [72],
                "wind_speed_10m_max_${key}": [29.4],
                "wind_gusts_10m_max_${key}": [46.2],
                "wind_direction_10m_dominant_${key}": [238],
                "weather_code_${key}": [63],
                "sunrise": ["2026-08-28T07:03"],
                "sunset": ["2026-08-28T20:42"]
              }
            }"""
        )

        val dto = BatchedForecastSplitter.split(
            response,
            listOf(WeatherModel.ECMWF, WeatherModel.GFS)
        ).getValue(WeatherModel.ECMWF)
        val series = mapper.toSeries(WeatherModel.ECMWF, dto)

        assertEquals("ecmwf_ifs", WeatherModel.ECMWF.apiKey)
        assertEquals(listOf(17.4), series.hourly.temperature2m)
        assertEquals(listOf(1.2), series.hourly.precipitation)
        assertEquals(listOf(64), series.hourly.precipitationProbability)
        assertEquals(listOf(83), series.hourly.cloudCover)
        assertEquals(listOf(22.5), series.hourly.windSpeed10m)
        assertEquals(listOf(245), series.hourly.windDirection10m)
        assertEquals(listOf(38.7), series.hourly.windGusts10m)
        assertEquals(listOf(61), series.hourly.weatherCode)
        assertEquals(listOf(21.8), series.daily.tempMax)
        assertEquals(listOf(13.6), series.daily.tempMin)
        assertEquals(listOf(4.7), series.daily.precipitationSum)
        assertEquals(listOf(72), series.daily.precipitationProbabilityMax)
        assertEquals(listOf(29.4), series.daily.windSpeedMax)
        assertEquals(listOf(46.2), series.daily.windGustsMax)
        assertEquals(listOf(238), series.daily.windDirection10mDominant)
        assertEquals(listOf(63), series.daily.weatherCode)
    }

    @Test
    fun `contrat de variables API reste aligne avec les champs du pipeline`() {
        assertEquals(
            setOf(
                "temperature_2m", "precipitation", "precipitation_probability",
                "cloud_cover", "cloud_cover_low", "cloud_cover_mid", "cloud_cover_high",
                "wind_speed_10m", "wind_direction_10m",
                "wind_gusts_10m", "weather_code"
            ),
            OpenMeteoApi.DEFAULT_HOURLY_VARS.split(',').toSet()
        )
        assertEquals(
            setOf(
                "temperature_2m_max", "temperature_2m_min", "precipitation_sum",
                "precipitation_probability_max", "wind_speed_10m_max",
                "wind_gusts_10m_max", "wind_direction_10m_dominant",
                "weather_code", "sunrise", "sunset"
            ),
            OpenMeteoApi.DEFAULT_DAILY_VARS.split(',').toSet()
        )
    }
}
