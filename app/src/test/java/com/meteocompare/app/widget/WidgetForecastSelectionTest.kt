package com.meteocompare.app.widget

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetForecastSelectionTest {

    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35,
        timezone = "Europe/Paris"
    )

    @Test
    fun `daily choisit un modele avec cinq valeurs reelles plutot que cinq dates partagees`() {
        val dates = (0L until 5L).map { LocalDate.of(2026, 7, 17).plusDays(it) }
        val shortFineModel = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = dates,
                tempMax = listOf(21.0, 22.0, null, null, null),
                tempMin = listOf(14.0, 15.0, null, null, null),
                precipitationSum = listOf(0.0, 2.0, null, null, null),
                windSpeedMax = listOf(10.0, 11.0, null, null, null),
                weatherCode = listOf(1, 61, null, null, null)
            )
        )
        val completeModel = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = emptyHourly(),
            daily = DailyForecast(
                dates = dates,
                tempMax = listOf(20.0, 21.0, 22.0, 23.0, 24.0),
                tempMin = listOf(13.0, 14.0, 15.0, 16.0, 17.0),
                precipitationSum = listOf(0.0, 0.0, 1.5, 0.0, 3.0),
                windSpeedMax = List(5) { 12.0 },
                weatherCode = listOf(1, 2, 61, 3, 63)
            )
        )

        val items = buildForecasts(
            forecast = CityForecast(
                city = city,
                seriesByModel = linkedMapOf(
                    WeatherModel.AROME_FRANCE_HD to shortFineModel,
                    WeatherModel.GFS to completeModel
                )
            ),
            mode = ForecastMode.DAILY,
            timezone = city.timezone,
            now = Instant.parse("2026-07-17T10:00:00Z")
        )

        assertEquals(5, items.size)
        assertEquals(listOf(20.0, 21.0, 22.0, 23.0, 24.0), items.map { it.temp })
        assertTrue(items.all { it.sourceModelName == "GFS" })
        assertTrue(items.all { it.temp != null || it.condition != null })
    }

    @Test
    fun `hourly ignore un modele fin dont les cinq prochaines valeurs sont nulles`() {
        val now = Instant.parse("2026-07-17T10:15:00Z")
        val timestamps = (1L..5L).map { now.plusSeconds(it * 3600) }
        val emptyFineModel = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(5) { null },
                precipitation = List(5) { null },
                windSpeed10m = List(5) { null },
                weatherCode = List(5) { null }
            ),
            daily = emptyDaily()
        )
        val completeModel = ForecastSeries(
            model = WeatherModel.ICON_EU,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = listOf(18.0, 19.0, 20.0, 21.0, 22.0),
                precipitation = listOf(0.0, 0.0, 0.2, 0.0, 0.0),
                windSpeed10m = List(5) { 10.0 },
                weatherCode = listOf(1, 1, 51, 2, 2)
            ),
            daily = emptyDaily()
        )

        val items = buildForecasts(
            forecast = CityForecast(
                city = city,
                seriesByModel = linkedMapOf(
                    WeatherModel.AROME_FRANCE_HD to emptyFineModel,
                    WeatherModel.ICON_EU to completeModel
                )
            ),
            mode = ForecastMode.HOURLY,
            timezone = city.timezone,
            now = now
        )

        assertEquals(5, items.size)
        assertEquals(listOf(18.0, 19.0, 20.0, 21.0, 22.0), items.map { it.temp })
    }

    @Test
    fun `hourly privilegie les details nuages et pluie entre modeles complets`() {
        val now = Instant.parse("2026-07-17T10:15:00Z")
        val timestamps = (1L..5L).map { now.plusSeconds(it * 3600) }
        val fineWithoutDetails = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(5) { 20.0 },
                precipitation = List(5) { 0.0 },
                windSpeed10m = List(5) { 8.0 },
                weatherCode = List(5) { 1 }
            ),
            daily = emptyDaily()
        )
        val detailedModel = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = listOf(18.0, 19.0, 20.0, 21.0, 22.0),
                precipitation = List(5) { 0.0 },
                windSpeed10m = List(5) { 9.0 },
                weatherCode = List(5) { 2 },
                precipitationProbability = listOf(10, 20, 30, 40, 50),
                cloudCover = listOf(25, 35, 45, 55, 65)
            ),
            daily = emptyDaily()
        )

        val items = buildForecasts(
            forecast = CityForecast(
                city = city,
                seriesByModel = linkedMapOf(
                    WeatherModel.AROME_FRANCE_HD to fineWithoutDetails,
                    WeatherModel.GFS to detailedModel
                )
            ),
            mode = ForecastMode.HOURLY,
            timezone = city.timezone,
            now = now
        )

        assertEquals(listOf(18.0, 19.0, 20.0, 21.0, 22.0), items.map { it.temp })
        assertEquals(listOf(25, 35, 45, 55, 65), items.map { it.cloudCoverPct })
        assertEquals(listOf(10, 20, 30, 40, 50), items.map { it.precipProbabilityPct })
    }

    @Test
    fun `hourly attache la confiance globale a lecheance exacte`() {
        val now = Instant.parse("2026-07-17T10:15:00Z")
        val timestamps = (1L..5L).map { now.plusSeconds(it * 3600) }
        val series = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(5) { 20.0 },
                precipitation = List(5) { 0.5 },
                windSpeed10m = List(5) { 8.0 },
                precipitationProbability = listOf(10, 20, 30, 40, 50)
            ),
            daily = emptyDaily()
        )
        fun bands(startPercent: Int) = timestamps.mapIndexed { index, timestamp ->
            confidenceBand(timestamp, startPercent - index * 5, modelCount = 2)
        }
        val confidence = WidgetForecastConfidence(
            hourlyByTimestamp = hourlyForecastConfidenceByTimestamp(
                temperatureBands = bands(90),
                precipitationBands = bands(60),
                windBands = bands(30),
                totalModelCount = 2
            )
        )

        val items = buildForecasts(
            forecast = CityForecast(
                city = city,
                seriesByModel = linkedMapOf(
                    WeatherModel.GFS to series,
                    WeatherModel.ICON_EU to series.copy(model = WeatherModel.ICON_EU)
                )
            ),
            mode = ForecastMode.HOURLY,
            timezone = city.timezone,
            now = now,
            forecastConfidence = confidence
        )

        assertEquals(listOf(60, 55, 50, 45, 40), items.map { it.forecastConfidencePct })
    }

    @Test
    fun `hourly confiance globale exige au moins deux metriques`() {
        val timestamp = Instant.parse("2026-07-17T12:00:00Z")
        val byTimestamp = hourlyForecastConfidenceByTimestamp(
            temperatureBands = listOf(confidenceBand(timestamp, 90)),
            precipitationBands = emptyList(),
            windBands = emptyList(),
            totalModelCount = 2
        )

        assertTrue(byTimestamp.isEmpty())
    }

    @Test
    fun `hourly confiance globale penalise la disparition de modeles`() {
        val timestamp = Instant.parse("2026-07-17T12:00:00Z")
        val byTimestamp = hourlyForecastConfidenceByTimestamp(
            temperatureBands = listOf(confidenceBand(timestamp, 90, modelCount = 2)),
            precipitationBands = emptyList(),
            windBands = listOf(confidenceBand(timestamp, 90, modelCount = 2)),
            totalModelCount = 7
        )

        assertTrue((byTimestamp[timestamp] ?: 100) < 90)
    }

    @Test
    fun `daily confiance globale combine temperature pluie et vent`() {
        val date = LocalDate.of(2026, 7, 17)
        val byDate = dailyForecastConfidenceByDate(
            days = listOf(
                DayConfidence(
                    date = date,
                    tempMax = confidenceScore(percent = 100),
                    tempMin = confidenceScore(percent = 80),
                    precipitation = PrecipitationConfidence.NoRain(
                        percent = 40,
                        modelCount = 2,
                        maxAmountMm = 0.0
                    ),
                    windMax = confidenceScore(percent = 60)
                )
            ),
            totalModelCount = 2
        )

        assertEquals(70, byDate[date])
    }

    @Test
    fun `daily attache la confiance globale a la date exacte`() {
        val dates = listOf(
            LocalDate.of(2026, 7, 17),
            LocalDate.of(2026, 7, 18)
        )
        val items = buildDailyForecasts(
            daily = DailyForecast(
                dates = dates,
                tempMax = listOf(24.0, 25.0),
                tempMin = listOf(15.0, 16.0),
                precipitationSum = listOf(0.0, 1.0),
                windSpeedMax = listOf(10.0, 12.0),
                precipitationProbabilityMax = listOf(10, 40)
            ),
            hourly = emptyHourly(),
            zone = java.time.ZoneId.of("Europe/Paris"),
            now = Instant.parse("2026-07-17T08:00:00Z"),
            forecastConfidenceByDate = mapOf(
                dates[0] to 82,
                dates[1] to 64
            )
        )

        assertEquals(listOf(82, 64), items.map { it.forecastConfidencePct })
    }

    @Test
    fun `daily confiance globale exige au moins deux composantes`() {
        val date = LocalDate.of(2026, 7, 17)
        val byDate = dailyForecastConfidenceByDate(
            days = listOf(
                DayConfidence(
                    date = date,
                    tempMax = confidenceScore(percent = 90),
                    tempMin = null,
                    precipitation = null,
                    windMax = null
                )
            ),
            totalModelCount = 2
        )

        assertTrue(byDate.isEmpty())
    }

    @Test
    fun `daily calcule la couverture nuageuse sur les heures diurnes`() {
        val date = LocalDate.of(2026, 7, 17)
        val zone = java.time.ZoneId.of("Europe/Paris")
        val timestamps = (0L until 24L).map { hour ->
            date.atTime(hour.toInt(), 0).atZone(zone).toInstant()
        }
        val cloud = (0 until 24).map { hour -> if (hour in 7..19) 60 else 10 }
        val hourly = HourlyForecast(
            timestamps = timestamps,
            temperature2m = List(24) { 20.0 },
            precipitation = List(24) { 0.0 },
            windSpeed10m = List(24) { 8.0 },
            cloudCover = cloud
        )

        assertEquals(60, dailyCloudCoverPct(hourly, date, zone))
    }

    @Test
    fun `confiance prudente penalise une faible couverture modeles`() {
        val fullCoverage = conservativeConfidencePercent(
            percents = listOf(90, 90, 90, 90),
            contributingModels = 7,
            totalModels = 7
        )
        val partialCoverage = conservativeConfidencePercent(
            percents = listOf(90, 90, 90, 90),
            contributingModels = 2,
            totalModels = 7
        )

        assertEquals(90, fullCoverage)
        assertTrue(partialCoverage < fullCoverage)
    }

    @Test
    fun `confiance prudente retient le quartile bas plutot que la moyenne`() {
        val score = conservativeConfidencePercent(
            percents = listOf(20, 90, 90, 90),
            contributingModels = 7,
            totalModels = 7
        )

        assertEquals(20, score)
    }

    @Test
    fun `hourly ne recycle pas les premieres heures d un cache entierement passe`() {
        val now = Instant.parse("2026-07-17T10:15:00Z")
        val hourly = HourlyForecast(
            timestamps = (5L downTo 1L).map { now.minusSeconds(it * 3600) },
            temperature2m = List(5) { 15.0 },
            precipitation = List(5) { 0.0 },
            windSpeed10m = List(5) { 8.0 },
            weatherCode = List(5) { 1 }
        )

        assertTrue(buildHourlyForecasts(hourly, java.time.ZoneId.of("UTC"), now).isEmpty())
    }

    @Test
    fun `daily filtre les jours passes d un cache stale sans decaler les valeurs`() {
        val zone = java.time.ZoneId.of("Europe/Paris")
        val dates = (0L until 5L).map { LocalDate.of(2026, 7, 15).plusDays(it) }
        val items = buildDailyForecasts(
            daily = DailyForecast(
                dates = dates,
                tempMax = listOf(15.0, 16.0, 17.0, 18.0, 19.0),
                tempMin = List(5) { 10.0 },
                precipitationSum = listOf(1.0, 2.0, 3.0, 4.0, 5.0),
                windSpeedMax = List(5) { 10.0 },
                weatherCode = listOf(1, 2, 3, 61, 63)
            ),
            hourly = emptyHourly(),
            zone = zone,
            now = LocalDate.of(2026, 7, 17).atStartOfDay(zone).toInstant()
        )

        assertEquals(listOf(17.0, 18.0, 19.0), items.map { it.temp })
    }

    @Test
    fun `bucket confiance pluie cumule les mm horaires au lieu de les moyenner`() {
        assertEquals(24.0, aggregateConfidenceBucketValue(
            ForecastMode.CONFIDENCE_PRECIPITATION, List(24) { 1.0 }
        ), 0.001)
        assertEquals(10.0, aggregateConfidenceBucketValue(
            ForecastMode.CONFIDENCE_TEMPERATURE, listOf(8.0, 12.0)
        ), 0.001)
    }

    private fun confidenceBand(
        timestamp: Instant,
        percent: Int,
        modelCount: Int = 2
    ) = HourlyConfidenceBand(
        timestamp = timestamp,
        meanValue = 0.5,
        minValue = 0.0,
        maxValue = 1.0,
        stdDev = 0.1,
        percent = percent,
        modelCount = modelCount
    )

    private fun confidenceScore(
        percent: Int,
        modelCount: Int = 2
    ) = ConfidenceScore(
        percent = percent,
        minValue = 0.0,
        maxValue = 1.0,
        meanValue = 0.5,
        stdDev = 0.1,
        modelCount = modelCount
    )

    private fun emptyHourly() = HourlyForecast(
        timestamps = emptyList(),
        temperature2m = emptyList(),
        precipitation = emptyList(),
        windSpeed10m = emptyList()
    )

    private fun emptyDaily() = DailyForecast(
        dates = emptyList(),
        tempMax = emptyList(),
        tempMin = emptyList(),
        precipitationSum = emptyList(),
        windSpeedMax = emptyList()
    )
}
