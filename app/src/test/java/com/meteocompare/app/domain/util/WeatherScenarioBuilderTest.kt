package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.WeatherScenarioKind
import com.meteocompare.app.domain.model.WeatherScenarioTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WeatherScenarioBuilderTest {

    private val now = Instant.parse("2026-08-07T10:00:00Z")

    @Test
    fun `regroupe deux modèles secs et isole un scénario de pluie tardive`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = List(12) { 20.0 + it / 10.0 },
                precipitations = List(12) { 0.0 },
                weatherCodes = List(12) { 1 },
                clouds = List(12) { 20 },
                gusts = List(12) { 28.0 }
            ),
            WeatherModel.ECMWF to hourly(
                temperatures = List(12) { 19.0 + it / 10.0 },
                precipitations = List(12) { 0.0 },
                weatherCodes = List(12) { 0 },
                clouds = List(12) { 15 },
                gusts = List(12) { 32.0 }
            ),
            WeatherModel.ICON_GLOBAL to hourly(
                temperatures = List(12) { 18.0 },
                precipitations = List(8) { 0.0 } + listOf(0.4, 0.8, 0.5, 0.0),
                weatherCodes = List(8) { 2 } + listOf(80, 80, 61, 2),
                clouds = List(12) { 82 },
                gusts = List(12) { 45.0 }
            )
        )

        val scenarios = WeatherScenarioBuilder.next12h(forecast, now)

        assertEquals(2, scenarios.size)
        assertEquals(WeatherScenarioKind.CLEAR, scenarios[0].kind)
        assertEquals(2, scenarios[0].modelCount)
        assertEquals(3, scenarios[0].totalModelCount)
        assertEquals(WeatherScenarioKind.RAIN, scenarios[1].kind)
        assertEquals(WeatherScenarioTiming.LATE, scenarios[1].timing)
        assertEquals(1, scenarios[1].modelCount)
        assertEquals(45.0, scenarios[1].gustMaxKmh ?: error("gust missing"), 0.001)
    }

    @Test
    fun `un épisode pluvieux réparti sur presque toute la fenêtre est persistant`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = List(12) { 12.0 },
                precipitations = List(12) { if (it in 1..10) 0.4 else 0.0 },
                weatherCodes = List(12) { if (it in 1..10) 61 else 3 },
                clouds = List(12) { 95 },
                gusts = List(12) { 50.0 }
            )
        )

        val scenario = WeatherScenarioBuilder.next12h(forecast, now).single()

        assertEquals(WeatherScenarioKind.RAIN, scenario.kind)
        assertEquals(WeatherScenarioTiming.THROUGHOUT, scenario.timing)
    }

    @Test
    fun `au delà de trois groupes les variantes minoritaires sont regroupées explicitement`() {
        val forecast = forecastOf(
            WeatherModel.GFS to simpleHourly(code = 0, cloud = 5, precip = 0.0),
            WeatherModel.ECMWF to simpleHourly(code = 3, cloud = 95, precip = 0.0),
            WeatherModel.ICON_GLOBAL to simpleHourly(code = 80, cloud = 80, precip = 0.3),
            WeatherModel.UKMO_GLOBAL to simpleHourly(code = 95, cloud = 90, precip = 0.8)
        )

        val scenarios = WeatherScenarioBuilder.next12h(forecast, now, maxScenarios = 3)

        assertEquals(3, scenarios.size)
        assertEquals(WeatherScenarioKind.OTHER, scenarios.last().kind)
        assertEquals(2, scenarios.last().modelCount)
        assertTrue(scenarios.all { it.totalModelCount == 4 })
    }


    @Test
    fun `un ciel sec a 82 pourcent reste variable et non couvert`() {
        val forecast = forecastOf(
            WeatherModel.GFS to simpleHourly(code = 3, cloud = 82, precip = 0.0)
        )

        val scenario = WeatherScenarioBuilder.next12h(forecast, now).single()

        assertEquals(WeatherScenarioKind.VARIABLE_SKY, scenario.kind)
    }

    @Test
    fun `cumul pluie 12h reste absent si une heure de quantite manque`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = List(12) { 15.0 },
                precipitations = List(11) { 1.0 } + listOf(null),
                weatherCodes = List(12) { 61 },
                clouds = List(12) { 90 },
                gusts = List(12) { 30.0 }
            )
        )

        val scenario = WeatherScenarioBuilder.next12h(forecast, now).single()

        assertEquals(WeatherScenarioKind.RAIN, scenario.kind)
        assertEquals(null, scenario.precipitationMinMm)
        assertEquals(null, scenario.precipitationMaxMm)
    }

    private fun simpleHourly(code: Int, cloud: Int, precip: Double): HourlyForecast = hourly(
        temperatures = List(12) { 15.0 },
        precipitations = List(12) { precip },
        weatherCodes = List(12) { code },
        clouds = List(12) { cloud },
        gusts = List(12) { 30.0 }
    )

    private fun hourly(
        temperatures: List<Double?>,
        precipitations: List<Double?>,
        weatherCodes: List<Int?>,
        clouds: List<Int?>,
        gusts: List<Double?>
    ): HourlyForecast = HourlyForecast(
        timestamps = temperatures.indices.map { now.plusSeconds(it * 3_600L) },
        temperature2m = temperatures,
        precipitation = precipitations,
        windSpeed10m = List(temperatures.size) { 15.0 },
        weatherCode = weatherCodes,
        precipitationProbability = precipitations.map { if ((it ?: 0.0) > 0.0) 70 else 10 },
        cloudCover = clouds,
        windGusts10m = gusts
    )

    private fun forecastOf(
        vararg series: Pair<WeatherModel, HourlyForecast>
    ): CityForecast = CityForecast(
        city = City(
            id = "1",
            name = "Paris",
            country = "France",
            latitude = 48.85,
            longitude = 2.35
        ),
        seriesByModel = series.associate { (model, hourly) ->
            model to ForecastSeries(
                model = model,
                hourly = hourly,
                daily = DailyForecast(
                    dates = emptyList(),
                    tempMax = emptyList(),
                    tempMin = emptyList(),
                    precipitationSum = emptyList(),
                    windSpeedMax = emptyList()
                )
            )
        }
    )
}
