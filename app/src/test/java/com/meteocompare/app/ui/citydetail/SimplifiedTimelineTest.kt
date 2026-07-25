package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimplifiedTimelineTest {

    private val now = Instant.parse("2026-07-23T10:00:00Z")
    private val today = LocalDate.of(2026, 7, 23)

    @Test
    fun `daily timeline uses medians and flags strong disagreement`() {
        val forecast = CityForecast(
            city = paris,
            seriesByModel = linkedMapOf(
                WeatherModel.GFS to series(
                    WeatherModel.GFS, today,
                    min = 10.0, max = 20.0, rain = 0.0,
                    probability = 0, wind = 10.0, weatherCode = 0
                ),
                WeatherModel.ECMWF to series(
                    WeatherModel.ECMWF, today,
                    min = 12.0, max = 22.0, rain = 0.1,
                    probability = 10, wind = 12.0, weatherCode = 1
                ),
                WeatherModel.ICON_GLOBAL to series(
                    WeatherModel.ICON_GLOBAL, today,
                    min = 14.0, max = 24.0, rain = 5.0,
                    probability = 90, wind = 40.0, weatherCode = 61
                )
            )
        )

        val point = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now).single()

        assertEquals(12.0, point.tempMinC!!, 0.001)
        assertEquals(22.0, point.tempMaxC!!, 0.001)
        assertEquals(12.0, point.windKmh!!, 0.001)
        assertEquals(10, point.precipitationPercent)
        assertEquals(PrecipitationSignalSource.MODEL_PROBABILITY, point.precipitationSource)
        assertEquals(3, point.precipitationModelCount)
        // Égalité parfaite entre clair, principalement clair et pluie : le
        // départage conservateur choisit la condition la plus significative.
        assertEquals(WeatherCondition.RAIN, point.condition)
        assertEquals(3, point.modelCount)
        assertTrue(point.isDivergent)
    }

    @Test
    fun `one isolated probability falls back to deterministic model agreement`() {
        val forecast = CityForecast(
            city = paris,
            seriesByModel = linkedMapOf(
                WeatherModel.GFS to series(
                    WeatherModel.GFS, today,
                    min = 10.0, max = 20.0, rain = 0.0,
                    probability = 90, wind = 10.0, weatherCode = 0
                ),
                WeatherModel.ECMWF to series(
                    WeatherModel.ECMWF, today,
                    min = 11.0, max = 21.0, rain = 0.0,
                    probability = null, wind = 11.0, weatherCode = 0
                ),
                WeatherModel.ICON_GLOBAL to series(
                    WeatherModel.ICON_GLOBAL, today,
                    min = 12.0, max = 22.0, rain = 0.0,
                    probability = null, wind = 12.0, weatherCode = 1
                )
            )
        )

        val point = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now).single()

        assertEquals(PrecipitationSignalSource.MODEL_AGREEMENT, point.precipitationSource)
        assertEquals(0, point.precipitationPercent)
        assertEquals(0, point.wetModelCount)
        assertEquals(3, point.precipitationModelCount)
    }

    @Test
    fun `daily timeline filters dates older than the local day`() {
        val yesterday = today.minusDays(1)
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.GFS to series(
                    WeatherModel.GFS, yesterday,
                    min = 10.0, max = 20.0, rain = 0.0,
                    probability = 0, wind = 10.0, weatherCode = 0
                )
            )
        )

        assertTrue(buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now).isEmpty())
    }

    @Test
    fun `timeline skips snapshots without any usable value`() {
        val emptySeries = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = listOf(today),
                tempMax = listOf(null),
                tempMin = listOf(null),
                precipitationSum = listOf(null),
                windSpeedMax = listOf(null),
                weatherCode = listOf(null),
                precipitationProbabilityMax = listOf(null)
            )
        )
        val forecast = CityForecast(paris, mapOf(WeatherModel.GFS to emptySeries))

        assertTrue(buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now).isEmpty())
    }

    private val paris = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35,
        timezone = "Europe/Paris"
    )

    private fun series(
        model: WeatherModel,
        date: LocalDate,
        min: Double,
        max: Double,
        rain: Double,
        probability: Int?,
        wind: Double,
        weatherCode: Int
    ): ForecastSeries = ForecastSeries(
        model = model,
        hourly = emptyHourly(),
        daily = DailyForecast(
            dates = listOf(date),
            tempMax = listOf(max),
            tempMin = listOf(min),
            precipitationSum = listOf(rain),
            windSpeedMax = listOf(wind),
            weatherCode = listOf(weatherCode),
            precipitationProbabilityMax = listOf(probability)
        )
    )

    private fun emptyHourly() = HourlyForecast(
        timestamps = emptyList(),
        temperature2m = emptyList(),
        precipitation = emptyList(),
        windSpeed10m = emptyList()
    )
}
