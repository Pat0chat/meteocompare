package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
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
                precipitationProbabilities = listOf(20, 40),
                precipitationAmounts = listOf(0.2, 1.0)
            ),
            WeatherModel.ICON_GLOBAL to hourly(
                temperatures = listOf(20.0, 30.0),
                precipitationProbabilities = listOf(40, 60),
                precipitationAmounts = listOf(0.4, 2.0)
            )
        )

        val result = ForecastAggregates.next12h(forecast, now, includeConditions = true)

        assertEquals(12, result.temperatures.size)
        assertEquals(12, result.precipitationProbabilities.size)
        assertEquals(12, result.precipitationAmountsMm.size)
        assertEquals(12, result.conditions.size)
        assertEquals(15.0, result.temperatures[0] ?: error("temperature manquante"), 0.001)
        assertEquals(25.0, result.temperatures[1] ?: error("temperature manquante"), 0.001)
        assertEquals(30, result.precipitationProbabilities[0])
        assertEquals(50, result.precipitationProbabilities[1])
        assertEquals(0.3, result.precipitationAmountsMm[0] ?: error("pluie manquante"), 0.001)
        assertEquals(1.5, result.precipitationAmountsMm[1] ?: error("pluie manquante"), 0.001)
        assertEquals(WeatherCondition.DRIZZLE, result.conditions[0])
        assertEquals(WeatherCondition.RAIN_SHOWERS, result.conditions[1])
        assertNull(result.temperatures[2])
        assertNull(result.precipitationProbabilities[2])
        assertNull(result.precipitationAmountsMm[2])
        assertNull(result.conditions[2])
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

        val result = ForecastAggregates.next12h(forecast, now, includeConditions = true)

        assertNull(result.temperatures.first())
        assertNull(result.precipitationProbabilities.first())
        assertNull(result.precipitationAmountsMm.first())
        assertNull(result.conditions.first())
    }


    @Test
    fun `calcul des conditions reste désactivé pour les consommateurs qui nen ont pas besoin`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = listOf(18.0),
                precipitationProbabilities = listOf(0),
                precipitationAmounts = listOf(0.0),
                weatherCodes = listOf(0)
            )
        )

        val result = ForecastAggregates.next12h(forecast, now)

        assertEquals(emptyList<WeatherCondition?>(), result.conditions)
    }

    @Test
    fun `condition horaire utilise le vote majoritaire des familles météo`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = listOf(18.0),
                precipitationProbabilities = listOf(20),
                precipitationAmounts = listOf(0.0),
                weatherCodes = listOf(2)
            ),
            WeatherModel.ICON_GLOBAL to hourly(
                temperatures = listOf(18.0),
                precipitationProbabilities = listOf(20),
                precipitationAmounts = listOf(0.0),
                weatherCodes = listOf(2)
            ),
            WeatherModel.ECMWF to hourly(
                temperatures = listOf(18.0),
                precipitationProbabilities = listOf(70),
                precipitationAmounts = listOf(1.0),
                weatherCodes = listOf(61)
            )
        )

        val result = ForecastAggregates.next12h(forecast, now, includeConditions = true)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, result.conditions.first())
    }

    @Test
    fun `égalité de conditions privilégie le signal météo le plus prudent`() {
        assertEquals(
            WeatherCondition.RAIN,
            ForecastAggregates.conditionConsensus(
                listOf(WeatherCondition.CLEAR, WeatherCondition.RAIN)
            )
        )
    }

    @Test
    fun `modèle sec sans weather code ne fabrique pas une icône de ciel clair`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = listOf(18.0),
                precipitationProbabilities = listOf(null),
                precipitationAmounts = listOf(0.0),
                weatherCodes = listOf(null)
            )
        )

        val result = ForecastAggregates.next12h(forecast, now, includeConditions = true)

        assertNull(result.conditions.first())
    }

    @Test
    fun `startInstant correspond au premier slot reellement echantillonne`() {
        val lateNow = Instant.parse("2026-07-15T12:56:00Z")
        val forecast = CityForecast(
            city = City(
                id = "1", name = "Paris", country = "France",
                latitude = 48.85, longitude = 2.35, timezone = "Europe/Paris"
            ),
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(
                    model = WeatherModel.GFS,
                    hourly = HourlyForecast(
                        timestamps = listOf(Instant.parse("2026-07-15T13:00:00Z")),
                        temperature2m = listOf(19.0),
                        precipitation = listOf(0.0),
                        windSpeed10m = listOf(5.0)
                    ),
                    daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                )
            )
        )

        val result = ForecastAggregates.next12h(forecast, lateNow)

        assertEquals(Instant.parse("2026-07-15T13:00:00Z"), result.startInstant)
        assertEquals(19.0, result.temperatures.first() ?: error("temperature manquante"), 0.001)
    }

    private fun hourly(
        temperatures: List<Double?>,
        precipitationProbabilities: List<Int?>,
        precipitationAmounts: List<Double?>,
        weatherCodes: List<Int?> = List(temperatures.size) { null }
    ): HourlyForecast = HourlyForecast(
        timestamps = temperatures.indices.map { now.plusSeconds(it * 3_600L) },
        temperature2m = temperatures,
        precipitation = precipitationAmounts,
        windSpeed10m = List(temperatures.size) { null },
        weatherCode = weatherCodes,
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
