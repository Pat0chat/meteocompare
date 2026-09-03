package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Consensus v2 n'affiche pas une moyenne diluée : sous 50 % de risque,
        // la quantité centrale déterministe est 0 mm.
        assertEquals(0.0, result.precipitationAmountsMm[0] ?: error("pluie manquante"), 0.001)
        assertEquals(1.5, result.precipitationAmountsMm[1] ?: error("pluie manquante"), 0.001)
        // Sans WMO ni nébulosité, une P(pluie) centrale < 50 % ne fabrique plus
        // une condition par vote de fallbacks individuels.
        assertNull(result.conditions[0])
        assertEquals(WeatherCondition.RAIN_SHOWERS, result.conditions[1])
        assertNull(result.temperatures[2])
        assertNull(result.precipitationProbabilities[2])
        assertNull(result.precipitationAmountsMm[2])
        assertNull(result.conditions[2])
    }

    @Test
    fun `une heure voisine est ignoree meme si elle est tres proche`() {
        val timestamps = listOf(now.plusSeconds(60L))
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
    fun `un modele sans echeance exacte est exclu sans biaiser le consensus`() {
        val exact = HourlyForecast(
            timestamps = listOf(now),
            temperature2m = listOf(20.0),
            precipitation = listOf(0.0),
            windSpeed10m = listOf(null),
            precipitationProbability = listOf(20)
        )
        val neighborOnly = HourlyForecast(
            timestamps = listOf(now.plusSeconds(3_600L)),
            temperature2m = listOf(40.0),
            precipitation = listOf(10.0),
            windSpeed10m = listOf(null),
            precipitationProbability = listOf(100)
        )
        val forecast = forecastOf(
            WeatherModel.GFS to exact,
            WeatherModel.ECMWF to neighborOnly
        )

        val result = ForecastAggregates.next12h(forecast, now)

        assertEquals(20.0, result.temperatures.first() ?: error("temperature manquante"), 0.001)
        assertEquals(20, result.precipitationProbabilities.first())
        assertEquals(0.0, result.precipitationAmountsMm.first() ?: error("pluie manquante"), 0.001)
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
    fun `ponderations locales sont appliquees a la temperature et a la pluie`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = listOf(0.0),
                precipitationProbabilities = listOf(0),
                precipitationAmounts = listOf(0.0)
            ),
            WeatherModel.ECMWF to hourly(
                temperatures = listOf(10.0),
                precipitationProbabilities = listOf(100),
                precipitationAmounts = listOf(2.0)
            )
        )
        val baseline = ForecastAggregates.next12h(forecast, now)
        val weighted = ForecastAggregates.next12h(
            forecast,
            now,
            engineContext = ForecastEngineContext(
                localWeightsByVariable = mapOf(
                    ForecastEngineVariable.TEMPERATURE to mapOf(
                        WeatherModel.GFS to 0.5,
                        WeatherModel.ECMWF to 1.5
                    ),
                    ForecastEngineVariable.PRECIPITATION to mapOf(
                        WeatherModel.GFS to 0.5,
                        WeatherModel.ECMWF to 1.5
                    )
                )
            )
        )

        assertTrue(weighted.temperatures.first()!! > baseline.temperatures.first()!!)
        assertTrue(
            weighted.precipitationProbabilities.first()!! >
                baseline.precipitationProbabilities.first()!!
        )
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
    fun `avec plusieurs familles WMO le ciel est affine par la nebulosite robuste`() {
        val forecast = forecastOf(
            WeatherModel.AROME_FRANCE_HD to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(3), listOf(64)),
            WeatherModel.ARPEGE_EUROPE to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(3), listOf(65)),
            WeatherModel.GFS to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(3), listOf(66)),
            WeatherModel.ECMWF to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(2), listOf(61)),
            WeatherModel.UKMO_GLOBAL to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(2), listOf(62)),
            WeatherModel.GEM_GLOBAL to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(1), listOf(58)),
            WeatherModel.METNO_NORDIC to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(1), listOf(60)),
            WeatherModel.KNMI_HARMONIE_EU to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(0), listOf(56)),
            WeatherModel.BOM_ACCESS to hourly(listOf(18.0), listOf(10), listOf(0.0), listOf(0), listOf(59))
        )

        val result = ForecastAggregates.next12h(forecast, now, includeConditions = true)

        assertEquals(WeatherCondition.PARTLY_CLOUDY, result.conditions.first())
    }

    @Test
    fun `des fallbacks de modeles incomplets ne renversent pas deux familles WMO natives`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(listOf(18.0), listOf(80), listOf(2.0), listOf(61), listOf(95)),
            WeatherModel.ECMWF to hourly(listOf(18.0), listOf(80), listOf(2.0), listOf(61), listOf(95)),
            WeatherModel.ICON_GLOBAL to hourly(listOf(18.0), listOf(0), listOf(0.0), listOf(null), listOf(10)),
            WeatherModel.UKMO_GLOBAL to hourly(listOf(18.0), listOf(0), listOf(0.0), listOf(null), listOf(10)),
            WeatherModel.GEM_GLOBAL to hourly(listOf(18.0), listOf(0), listOf(0.0), listOf(null), listOf(10))
        )

        val result = ForecastAggregates.next12h(forecast, now, includeConditions = true)

        assertEquals(WeatherCondition.RAIN, result.conditions.first())
    }

    @Test
    fun `widget 12h peut inferer le ciel depuis cloud cover sans weather code`() {
        val forecast = forecastOf(
            WeatherModel.GFS to hourly(
                temperatures = listOf(18.0),
                precipitationProbabilities = listOf(null),
                precipitationAmounts = listOf(0.0),
                weatherCodes = listOf(null),
                cloudCover = listOf(82)
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
    fun `helper sans modeles utilise aussi la hierarchie pour les precipitations liquides`() {
        assertEquals(
            WeatherCondition.RAIN,
            ForecastAggregates.conditionConsensus(
                listOf(
                    WeatherCondition.CLEAR,
                    WeatherCondition.MAINLY_CLEAR,
                    WeatherCondition.DRIZZLE,
                    WeatherCondition.RAIN_SHOWERS,
                    WeatherCondition.RAIN
                )
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
        weatherCodes: List<Int?> = List(temperatures.size) { null },
        cloudCover: List<Int?> = List(temperatures.size) { null }
    ): HourlyForecast = HourlyForecast(
        timestamps = temperatures.indices.map { now.plusSeconds(it * 3_600L) },
        temperature2m = temperatures,
        precipitation = precipitationAmounts,
        windSpeed10m = List(temperatures.size) { null },
        weatherCode = weatherCodes,
        precipitationProbability = precipitationProbabilities,
        cloudCover = cloudCover
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
