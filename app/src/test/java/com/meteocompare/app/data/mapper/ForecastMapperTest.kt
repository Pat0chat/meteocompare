package com.meteocompare.app.data.mapper

import com.meteocompare.app.data.remote.dto.DailyDto
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.HourlyDto
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForecastMapperTest {

    private lateinit var mapper: ForecastMapper

    @Before
    fun setUp() {
        mapper = ForecastMapper()
    }

    @Test
    fun `maps hourly temperatures preserving order`() {
        val dto = ForecastResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            hourly = HourlyDto(
                time = listOf("2026-06-23T00:00", "2026-06-23T01:00", "2026-06-23T02:00"),
                temperature2m = listOf(18.0, 17.5, 17.0),
                precipitation = listOf(0.0, 0.1, 0.0),
                windSpeed10m = listOf(10.0, 12.0, 11.0)
            )
        )

        val series = mapper.toSeries(WeatherModel.GFS, dto)

        assertEquals(3, series.hourly.timestamps.size)
        assertEquals(listOf(18.0, 17.5, 17.0), series.hourly.temperature2m)
        assertEquals(WeatherModel.GFS, series.model)
    }

    @Test
    fun `handles missing variables by filling nulls of correct length`() {
        val dto = ForecastResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            hourly = HourlyDto(
                time = listOf("2026-06-23T00:00", "2026-06-23T01:00"),
                temperature2m = listOf(18.0, 17.5),
                precipitation = null, // modèle qui ne fournit pas la pluie
                windSpeed10m = listOf(10.0, 12.0)
            )
        )

        val series = mapper.toSeries(WeatherModel.GFS, dto)

        assertEquals(2, series.hourly.precipitation.size)
        assertTrue(series.hourly.precipitation.all { it == null })
    }

    @Test
    fun `inline null values keep their timestamp position`() {
        val dto = ForecastResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            hourly = HourlyDto(
                time = listOf(
                    "2026-06-23T00:00",
                    "2026-06-23T01:00",
                    "2026-06-23T02:00"
                ),
                temperature2m = listOf(18.0, null, 17.0),
                weatherCode = listOf(1, null, 3)
            )
        )

        val series = mapper.toSeries(WeatherModel.GFS, dto)

        assertEquals(3, series.hourly.timestamps.size)
        assertEquals(listOf(18.0, null, 17.0), series.hourly.temperature2m)
        assertEquals(listOf(1, null, 3), series.hourly.weatherCode)
    }

    @Test
    fun `invalid timestamp removes the same position from every variable`() {
        val dto = ForecastResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            hourly = HourlyDto(
                time = listOf(
                    "2026-06-23T00:00",
                    "invalid",
                    "2026-06-23T02:00"
                ),
                temperature2m = listOf(18.0, 99.0, null),
                precipitation = listOf(0.0, 42.0, 1.5)
            )
        )

        val series = mapper.toSeries(WeatherModel.GFS, dto)

        assertEquals(2, series.hourly.timestamps.size)
        assertEquals(listOf(18.0, null), series.hourly.temperature2m)
        assertEquals(listOf(0.0, 1.5), series.hourly.precipitation)
    }

    @Test
    fun `returns empty forecast when hourly is absent`() {
        val dto = ForecastResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            hourly = null,
            daily = DailyDto(
                time = listOf("2026-06-23"),
                temperature2mMax = listOf(25.0),
                temperature2mMin = listOf(15.0)
            )
        )

        val series = mapper.toSeries(WeatherModel.AROME_FRANCE_HD, dto)

        assertEquals(0, series.hourly.size)
        assertEquals(1, series.daily.size)
        assertEquals(listOf(25.0), series.daily.tempMax)
    }

    @Test
    fun `maps wind gusts and astronomical sun times`() {
        val dto = ForecastResponseDto(
            latitude = 48.85,
            longitude = 2.35,
            timezone = "Europe/Paris",
            hourly = HourlyDto(
                time = listOf("2026-07-23T12:00"),
                temperature2m = listOf(25.0),
                windGusts10m = listOf(48.0)
            ),
            daily = DailyDto(
                time = listOf("2026-07-23"),
                temperature2mMax = listOf(28.0),
                windGusts10mMax = listOf(61.0),
                sunrise = listOf("2026-07-23T06:12"),
                sunset = listOf("2026-07-23T21:39")
            )
        )

        val series = mapper.toSeries(WeatherModel.GFS, dto)

        assertEquals(listOf(48.0), series.hourly.windGusts10m)
        assertEquals(listOf(61.0), series.daily.windGustsMax)
        assertEquals(Instant.parse("2026-07-23T04:12:00Z"), series.daily.sunrise.single())
        assertEquals(Instant.parse("2026-07-23T19:39:00Z"), series.daily.sunset.single())
    }
}
