package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.WeatherScenarioKind
import com.meteocompare.app.domain.model.WeatherScenarioTiming
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `une echeance voisine ne remplace pas une heure manquante dans un scenario`() {
        val timestamps = (0L..11L)
            .filterNot { it == 4L }
            .map { now.plusSeconds(it * 3_600L) }
        val hourly = HourlyForecast(
            timestamps = timestamps,
            temperature2m = List(timestamps.size) { 15.0 },
            precipitation = List(timestamps.size) { 1.0 },
            windSpeed10m = List(timestamps.size) { 15.0 },
            weatherCode = List(timestamps.size) { 61 },
            precipitationProbability = List(timestamps.size) { 80 },
            cloudCover = List(timestamps.size) { 90 },
            windGusts10m = List(timestamps.size) { 30.0 }
        )
        val forecast = forecastOf(WeatherModel.GFS to hourly)

        val scenario = WeatherScenarioBuilder.next12h(forecast, now).single()

        // Le cumul 12 h reste inconnu : l'heure 04:00 absente n'est pas
        // remplacée silencieusement par 03:00 ou 05:00.
        assertEquals(null, scenario.precipitationMinMm)
        assertEquals(null, scenario.precipitationMaxMm)
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
    fun `au delà de trois groupes on garde trois vrais scénarios et on compte les variantes masquées`() {
        val forecast = forecastOf(
            WeatherModel.GFS to simpleHourly(code = 0, cloud = 5, precip = 0.0),
            WeatherModel.ECMWF to simpleHourly(code = 3, cloud = 95, precip = 0.0),
            WeatherModel.ICON_GLOBAL to simpleHourly(code = 80, cloud = 80, precip = 0.3),
            WeatherModel.UKMO_GLOBAL to simpleHourly(code = 95, cloud = 90, precip = 0.8)
        )

        val scenarios = WeatherScenarioBuilder.next12h(forecast, now, maxScenarios = 3)

        assertEquals(3, scenarios.size)
        assertTrue(scenarios.none { it.kind == WeatherScenarioKind.OTHER })
        assertTrue(scenarios.all { it.totalModelCount == 4 })
        assertTrue(scenarios.all { it.hiddenVariantCount == 1 })
        assertTrue(scenarios.all { it.hiddenModelCount == 1 })
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

    @Test
    fun `12h rain total excludes preceding hour and includes ending hour`() {
        val samples = (0..12).map { offset ->
            now.plusSeconds(offset * 3600L)
        }
        val source = HourlyForecast(
            timestamps = samples,
            temperature2m = List(13) { 15.0 },
            precipitation = listOf(100.0) + List(11) { 1.0 } + listOf(3.0),
            windSpeed10m = List(13) { 10.0 },
            weatherCode = List(13) { 61 },
            precipitationProbability = List(13) { 90 }
        )
        val scenario = WeatherScenarioBuilder.next12h(
            forecastOf(WeatherModel.GFS to source), now
        ).single()
        // 100 mm ending at H belongs to H-1..H, not to our next-12h total.
        assertEquals(14.0, scenario.precipitationMinMm ?: error("missing total"), 1e-9)
        assertEquals(14.0, scenario.precipitationMaxMm ?: error("missing total"), 1e-9)
    }

    @Test
    fun `12h scenarios reject corrupt extremes rather than publishing NaN and infinity`() {
        val timestamps = (0..12).map { now.plusSeconds(it * 3_600L) }
        val source = HourlyForecast(
            timestamps = timestamps,
            temperature2m = List(13) { if (it == 5) Double.NaN else 16.0 },
            precipitation = List(13) { if (it == 7) Double.POSITIVE_INFINITY else 0.2 },
            windSpeed10m = List(13) { 10.0 },
            weatherCode = List(13) { 0 },
            cloudCover = List(13) { if (it == 8) 200 else 10 },
            windGusts10m = List(13) { if (it == 9) Double.POSITIVE_INFINITY else 30.0 }
        )
        val scenario = WeatherScenarioBuilder.next12h(
            forecastOf(WeatherModel.GFS to source), now
        ).single()
        // A single corrupt precipitation hour invalidates the twelve-hour sum.
        assertEquals(null, scenario.precipitationMinMm)
        assertEquals(null, scenario.precipitationMaxMm)
        assertEquals(16.0, scenario.temperatureMinC ?: error("missing temperature"), 1e-9)
        assertEquals(16.0, scenario.temperatureMaxC ?: error("missing temperature"), 1e-9)
        assertEquals(30.0, scenario.gustMaxKmh ?: error("missing gust"), 1e-9)
        assertTrue(scenario.cloudCoverMaxPercent in 0..100)
    }

    @Test
    fun `12h incomplete future last interval does not invent a total`() {
        val source = HourlyForecast(
            timestamps = (0L..11L).map { now.plusSeconds(it * 3600L) },
            temperature2m = List(12) { 15.0 },
            precipitation = List(12) { 1.0 },
            windSpeed10m = List(12) { 10.0 },
            weatherCode = List(12) { 61 }
        )
        val scenario = WeatherScenarioBuilder.next12h(
            forecastOf(WeatherModel.GFS to source), now
        ).single()
        assertEquals(null, scenario.precipitationMinMm)
        assertEquals(null, scenario.precipitationMaxMm)
    }

    @Test
    fun `scenario without WMO excludes past rain and uses rain of upcoming slot`() {
        fun source(upcomingRain: Double): HourlyForecast = HourlyForecast(
            timestamps = (0..12).map { now.plusSeconds(it * 3_600L) },
            temperature2m = List(13) { 15.0 },
            // H is the previous hour: it must NOT make the first slot wet.
            precipitation = listOf(8.0, upcomingRain) + List(11) { 0.0 },
            precipitationProbability = listOf(100, if (upcomingRain > 0.1) 90 else 0) + List(11) { 0 },
            weatherCode = List(13) { null },
            cloudCover = List(13) { 10 },
            windSpeed10m = List(13) { 8.0 }
        )
        val dry = WeatherScenarioBuilder.next12h(
            forecastOf(WeatherModel.GFS to source(0.0)), now
        ).single()
        assertEquals(WeatherScenarioKind.CLEAR, dry.kind)
        assertEquals(0.0, dry.precipitationMinMm!!, 0.0)

        val wet = WeatherScenarioBuilder.next12h(
            forecastOf(WeatherModel.GFS to source(0.8)), now
        ).single()
        assertEquals(WeatherScenarioKind.SHOWERS, wet.kind)
        assertEquals(WeatherScenarioTiming.EARLY, wet.timing)
        assertEquals(0.8, wet.precipitationMinMm!!, 0.0)
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
