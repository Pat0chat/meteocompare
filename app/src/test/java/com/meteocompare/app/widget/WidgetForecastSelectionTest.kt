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
import com.meteocompare.app.domain.model.WeatherCondition
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
    fun `daily utilise le consensus puis prolonge naturellement avec le modele encore disponible`() {
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
        assertEquals(listOf(20.5, 21.5, 22.0, 23.0, 24.0), items.map { it.temp })
        assertTrue(items.all { it.sourceModelName == null })
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
    fun `nouveau rendu widget avance au slot horaire sans nouvelles donnees`() {
        val model = WeatherModel.GFS
        val forecast = CityForecast(
            city = city,
            seriesByModel = mapOf(
                model to ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(
                        timestamps = listOf(
                            Instant.parse("2026-07-17T12:00:00Z"),
                            Instant.parse("2026-07-17T13:00:00Z"),
                            Instant.parse("2026-07-17T14:00:00Z")
                        ),
                        temperature2m = listOf(10.0, 20.0, 30.0),
                        precipitation = listOf(0.0, 0.0, 0.0),
                        windSpeed10m = listOf(5.0, 5.0, 5.0)
                    ),
                    daily = emptyDaily()
                )
            )
        )

        val beforeBoundary = buildForecasts(
            forecast = forecast,
            mode = ForecastMode.HOURLY,
            timezone = city.timezone,
            now = Instant.parse("2026-07-17T12:29:00Z")
        )
        val afterBoundary = buildForecasts(
            forecast = forecast,
            mode = ForecastMode.HOURLY,
            timezone = city.timezone,
            now = Instant.parse("2026-07-17T12:31:00Z")
        )

        assertEquals(10.0, beforeBoundary.first().temp ?: Double.NaN, 0.001)
        assertEquals(20.0, afterBoundary.first().temp ?: Double.NaN, 0.001)
    }

    @Test
    fun `hourly fusionne les modeles disponibles avec le consensus v2`() {
        val now = Instant.parse("2026-07-17T10:15:00Z")
        // Five display slots (11h–16h) require six endpoint timestamps.
        // The sixth timestamp supplies the rain probability of 15h–16h.
        val timestamps = (1L..6L).map { Instant.parse("2026-07-17T10:00:00Z").plusSeconds(it * 3600) }
        val fineWithoutDetails = ForecastSeries(
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = List(6) { 20.0 },
                precipitation = List(6) { 0.0 },
                windSpeed10m = List(6) { 8.0 },
                weatherCode = List(6) { 1 }
            ),
            daily = emptyDaily()
        )
        val detailedModel = ForecastSeries(
            model = WeatherModel.GFS,
            hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = listOf(18.0, 19.0, 20.0, 21.0, 22.0, 23.0),
                precipitation = List(6) { 0.0 },
                windSpeed10m = List(6) { 9.0 },
                weatherCode = List(6) { 2 },
                precipitationProbability = listOf(0, 10, 20, 30, 40, 50),
                cloudCover = listOf(25, 35, 45, 55, 65, 75)
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

        assertEquals(listOf(19.0, 19.5, 20.0, 20.5, 21.0), items.map { it.temp })
        assertEquals(listOf(25, 35, 45, 55, 65), items.map { it.cloudCoverPct })
        // At H, GFS probability is for the preceding hour. The slot H–H+1
        // instead takes the GFS value stamped H+1, halved by the dry AROME vote.
        assertEquals(listOf(5, 10, 15, 20, 25), items.map { it.precipProbabilityPct })
        // Estimation solaire de la timeline réelle : ciel dégagé équivalent,
        // disponible seulement quand le soleil et les nuages sont connus.
        assertTrue(items.all { item -> item.estimatedSunHours?.let { it in 0.0..1.0 } == true })
    }

    @Test
    fun `widget five hours do not reuse preceding probability without sixth endpoint`() {
        val firstHour = Instant.parse("2026-07-17T11:00:00Z")
        val timestamps = (0L..4L).map { firstHour.plusSeconds(it * 3600) }
        val hourly = HourlyForecast(
            timestamps = timestamps,
            temperature2m = List(5) { 20.0 },
            precipitation = List(5) { 0.0 },
            precipitationProbability = listOf(100, 10, 20, 30, 40),
            windSpeed10m = List(5) { 10.0 },
            weatherCode = List(5) { 0 },
            cloudCover = List(5) { 0 }
        )
        val forecast = CityForecast(city, mapOf(
            WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, emptyDaily())
        ))
        val items = buildForecasts(
            forecast, ForecastMode.HOURLY, city.timezone,
            now = firstHour.minusSeconds(60)
        )
        assertEquals(5, items.size)
        // The first 100% is for the past. Only the following four endpoints
        // belong to displayed intervals, and the fifth is genuinely missing.
        assertEquals(listOf(10, 20, 30, 40, null), items.map { it.precipProbabilityPct })
    }

    @Test
    fun `near total cloud cover does not invent widget sunshine without solar evidence`() {
        val start = Instant.parse("2026-07-17T12:00:00Z")
        val timestamps = listOf(start, start.plusSeconds(3600))
        fun forecast(nextHourSunshineSeconds: Double?): CityForecast {
            val hourly = HourlyForecast(
                timestamps = timestamps,
                temperature2m = listOf(20.0, 20.0),
                precipitation = listOf(0.0, 0.0),
                windSpeed10m = listOf(8.0, 8.0),
                weatherCode = listOf(3, 3),
                cloudCover = listOf(95, 95),
                cloudCoverLow = listOf(5, 5),
                cloudCoverMid = listOf(8, 8),
                cloudCoverHigh = listOf(93, 93),
                sunshineDuration = listOf(null, nextHourSunshineSeconds)
            )
            return CityForecast(city, mapOf(
                WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, emptyDaily())
            ))
        }
        fun firstItem(sunSeconds: Double?) = buildForecasts(
            forecast = forecast(sunSeconds), mode = ForecastMode.HOURLY,
            timezone = city.timezone, now = start.plusSeconds(120)
        ).first()
        val unknownSun = firstItem(null)
        assertEquals(95, unknownSun.cloudCoverPct)
        assertEquals(WeatherCondition.OVERCAST, unknownSun.condition)
        assertEquals(null, unknownSun.estimatedSunHours)

        // Only the same model's following-timestamp sunshine observation
        // supports both the veil and the stated 44-minute duration.
        val realSun = firstItem(2640.0)
        assertEquals(95, realSun.cloudCoverPct)
        assertEquals(WeatherCondition.VEILED, realSun.condition)
        assertEquals(44.0 / 60, realSun.estimatedSunHours ?: Double.NaN, 0.001)
    }

    @Test
    fun `hourly utilise la convergence consensus avant lancienne confiance injectee`() {
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

        // Depuis la hiérarchie WMO native -> variables centrales, une condition
        // dérivée de température/pluie/nébulosité ne fabrique plus un faux
        // pourcentage de consensus catégoriel. La convergence globale reste donc
        // calculée ici sur les trois métriques réellement mesurables :
        // température, précipitations et vent.
        // Les deux familles fournissent exactement les mêmes valeurs à chaque
        // échéance : la convergence inter-familles réelle est donc de 100 %.
        assertEquals(listOf(100, 100, 100, 100, 100), items.map { it.forecastConfidencePct })
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
    fun `widget hourly consensus reads probability and inferred rain from H plus one`() {
        val h = Instant.parse("2026-07-17T12:00:00Z")
        val hourly = HourlyForecast(
            timestamps = listOf(h, h.plusSeconds(3600)),
            temperature2m = listOf(18.0, 18.0),
            precipitation = listOf(0.0, 2.0),
            precipitationProbability = listOf(0, 90),
            windSpeed10m = listOf(10.0, 10.0),
            cloudCover = listOf(10, 10)
        )
        val unified = buildForecasts(
            CityForecast(city.copy(timezone = "UTC"), mapOf(
                WeatherModel.GFS to ForecastSeries(WeatherModel.GFS, hourly, emptyDaily())
            )), ForecastMode.HOURLY, "UTC", h
        ).first()
        assertEquals(90, unified.precipProbabilityPct)
        assertEquals(WeatherCondition.RAIN_SHOWERS, unified.condition)
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
