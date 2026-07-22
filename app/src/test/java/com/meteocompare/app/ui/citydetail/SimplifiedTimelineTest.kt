package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimplifiedTimelineTest {

    @Test
    fun `daily timeline uses medians and flags strong disagreement`() {
        val date = LocalDate.of(2026, 7, 22)
        val forecast = CityForecast(
            city = City(
                id = "paris",
                name = "Paris",
                country = "France",
                latitude = 48.85,
                longitude = 2.35,
                timezone = "Europe/Paris"
            ),
            seriesByModel = linkedMapOf(
                WeatherModel.GFS to series(
                    WeatherModel.GFS, date,
                    min = 10.0, max = 20.0, rain = 0.0,
                    probability = 0, wind = 10.0, weatherCode = 0
                ),
                WeatherModel.ECMWF to series(
                    WeatherModel.ECMWF, date,
                    min = 12.0, max = 22.0, rain = 0.1,
                    probability = 10, wind = 12.0, weatherCode = 1
                ),
                WeatherModel.ICON_GLOBAL to series(
                    WeatherModel.ICON_GLOBAL, date,
                    min = 14.0, max = 24.0, rain = 5.0,
                    probability = 90, wind = 40.0, weatherCode = 61
                )
            )
        )

        val point = buildSimplifiedTimeline(forecast, DisplayMode.DAILY).single()

        assertEquals(12.0, point.tempMinC!!, 0.001)
        assertEquals(22.0, point.tempMaxC!!, 0.001)
        assertEquals(12.0, point.windKmh!!, 0.001)
        assertEquals(10, point.precipitationPercent)
        assertEquals(WeatherCondition.CLEAR, point.condition)
        assertEquals(3, point.modelCount)
        assertTrue(point.isDivergent)
    }

    private fun series(
        model: WeatherModel,
        date: LocalDate,
        min: Double,
        max: Double,
        rain: Double,
        probability: Int,
        wind: Double,
        weatherCode: Int
    ): ForecastSeries = ForecastSeries(
        model = model,
        hourly = HourlyForecast(
            timestamps = emptyList(),
            temperature2m = emptyList(),
            precipitation = emptyList(),
            windSpeed10m = emptyList()
        ),
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
}
