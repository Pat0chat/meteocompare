package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineComparisonConditionRegressionTest {
    @Test
    fun `a daily rain total without representative conditions does not become a rainy icon`() {
        val date = LocalDate.of(2026, 7, 23)
        val city = City(
            id = "test", name = "Paris", country = "France",
            latitude = 48.85, longitude = 2.35, timezone = "Europe/Paris"
        )
        fun series(model: WeatherModel) = ForecastSeries(
            model = model,
            hourly = HourlyForecast(
                timestamps = listOf(Instant.parse("2026-07-23T10:00:00Z")),
                temperature2m = listOf(19.0), precipitation = listOf(null),
                windSpeed10m = listOf(10.0)
            ),
            daily = DailyForecast(
                dates = listOf(date), tempMax = listOf(23.0), tempMin = listOf(13.0),
                precipitationSum = listOf(9.0), windSpeedMax = listOf(15.0)
            )
        )
        val forecast = CityForecast(
            city,
            mapOf(WeatherModel.GFS to series(WeatherModel.GFS),
                WeatherModel.ECMWF to series(WeatherModel.ECMWF))
        )
        val days = EngineComparisonBuilder(ConfidenceCalculator(EqualWeighting())).build(
            forecast, ForecastEngineContext.DEFAULT, Instant.parse("2026-07-23T10:00:00Z")
        )
        assertTrue(days.size == 1)
        assertTrue(days.single().byEngine.values.all { it.condition == null })
    }

    @Test
    fun `each comparison engine uses hourly dominant sky instead of isolated daily rain`() {
        val date = LocalDate.of(2026, 7, 23)
        val hours = (0 until 24).map {
            Instant.parse("2026-07-22T22:00:00Z").plusSeconds(it * 3600L)
        }
        fun series(model: WeatherModel, native: Boolean) = ForecastSeries(
            model = model,
            hourly = HourlyForecast(
                timestamps = hours,
                temperature2m = List(24) { 18.0 },
                precipitation = List(24) { 0.0 },
                windSpeed10m = List(24) { 10.0 },
                weatherCode = List(24) { if (native) 0 else null },
                cloudCover = List(24) { 10 }
            ),
            daily = DailyForecast(
                dates = listOf(date), tempMax = listOf(23.0), tempMin = listOf(13.0),
                precipitationSum = listOf(8.0), windSpeedMax = listOf(15.0),
                weatherCode = listOf(61)
            )
        )
        val city = City(
            id = "test", name = "Paris", country = "France",
            latitude = 48.85, longitude = 2.35, timezone = "Europe/Paris"
        )
        val forecast = CityForecast(
            city,
            mapOf(
                WeatherModel.GFS to series(WeatherModel.GFS, true),
                WeatherModel.ECMWF to series(WeatherModel.ECMWF, false)
            )
        )
        val days = EngineComparisonBuilder(ConfidenceCalculator(EqualWeighting())).build(
            forecast, ForecastEngineContext.DEFAULT, Instant.parse("2026-07-23T10:00:00Z")
        )
        assertTrue(days.size == 1)
        assertTrue(days.single().byEngine.values.all { it.condition?.isSky == true })
    }
}
