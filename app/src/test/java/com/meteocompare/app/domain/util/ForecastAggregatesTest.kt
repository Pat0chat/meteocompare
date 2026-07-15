package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ForecastAggregatesTest {

    private val now = Instant.parse("2026-07-15T12:00:00Z")

    @Test
    fun `température et pluie sont agrégées sur les mêmes index horaires`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = listOf(10.0, 20.0),
                precipitationProbabilities = listOf(20, 40)
            ),
            WeatherModel.ICON_GLOBAL to hourly(
                temperatures = listOf(20.0, 30.0),
                precipitationProbabilities = listOf(40, 60)
            )
        )

        val result = ForecastAggregates.next12h(forecast, now)

        assertEquals(12, result.temperatures.size)
        assertEquals(12, result.precipitationProbabilities.size)
        assertEquals(15.0, result.temperatures[0] ?: error("temperature manquante"), 0.001)
        assertEquals(25.0, result.temperatures[1] ?: error("temperature manquante"), 0.001)
        assertEquals(30, result.precipitationProbabilities[0])
        assertEquals(50, result.precipitationProbabilities[1])
        assertNull(result.temperatures[2])
        assertNull(result.precipitationProbabilities[2])
    }

    @Test
    fun `une valeur éloignée de plus de trente minutes est ignorée`() {
        val timestamps = listOf(now.plusSeconds(31 * 60L))
        val forecast = forecastOf(
            WeatherModel.GFS to HourlyForecast(
                timestamps = timestamps,
                temperature2m = listOf(18.0),
                precipitation = listOf(null),
                windSpeed10m = listOf(null),
                precipitationProbability = listOf(70)
            )
        )

        val result = ForecastAggregates.next12h(forecast, now)

        assertNull(result.temperatures.first())
        assertNull(result.precipitationProbabilities.first())
    }

    private fun hourly(
        temperatures: List<Double?>,
        precipitationProbabilities: List<Int?>
    ): HourlyForecast = HourlyForecast(
        timestamps = temperatures.indices.map { now.plusSeconds(it * 3_600L) },
        temperature2m = temperatures,
        precipitation = List(temperatures.size) { null },
        windSpeed10m = List(temperatures.size) { null },
        precipitationProbability = precipitationProbabilities
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
