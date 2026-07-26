package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastInsightsTest {

    private val now = Instant.parse("2026-07-23T10:00:00Z")

    @Test
    fun `stable dry forecast produces a high agreement insight`() {
        val forecast = forecast(
            probabilityByModel = listOf(5, 10, 0),
            windByModel = listOf(12.0, 13.0, 11.0),
            maxByModel = listOf(22.0, 23.0, 22.5),
            dayCount = 2
        )

        val insights = buildForecastInsights(buildOverviewTimeline(forecast, now))

        assertTrue(insights.any { it.kind == ForecastInsightKind.HIGH_AGREEMENT })
        assertTrue(insights.size <= 3)
    }

    @Test
    fun `split rain signal is surfaced without a duplicate generic message`() {
        val forecast = forecast(
            probabilityByModel = listOf(10, 55, 90),
            windByModel = listOf(10.0, 12.0, 40.0),
            maxByModel = listOf(20.0, 22.0, 28.0)
        )

        val insights = buildForecastInsights(buildOverviewTimeline(forecast, now))

        assertEquals(ForecastInsightKind.RAIN_UNCERTAIN, insights.first().kind)
        assertEquals(PrecipitationSignalSource.MODEL_PROBABILITY, insights.first().precipitationSource)
        assertEquals(1, insights.count { it.point == insights.first().point })
    }


    @Test
    fun `temperature insight exposes its comparison baseline and target`() {
        val start = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            temperatureC = 18.2,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
            isDivergent = false
        )
        val target = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T16:00:00Z"),
            temperatureC = 25.1,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
            isDivergent = false
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(start, target))
        ).first { it.kind == ForecastInsightKind.TEMPERATURE_CHANGE }

        assertEquals(start, insight.referencePoint)
        assertEquals(target, insight.point)
        assertEquals(18, insight.referenceValue)
        assertEquals(25, insight.targetValue)
        assertEquals(7, insight.value)
    }


    @Test
    fun `falling temperature insight keeps the signed delta and both values`() {
        val start = SimplifiedTimelinePoint(
            date = LocalDate.of(2026, 7, 23),
            tempMaxC = 27.8,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
            isDivergent = false
        )
        val target = SimplifiedTimelinePoint(
            date = LocalDate.of(2026, 7, 24),
            tempMaxC = 19.9,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
            isDivergent = false
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.DAILY, listOf(start, target))
        ).first { it.kind == ForecastInsightKind.TEMPERATURE_CHANGE }

        assertEquals(28, insight.referenceValue)
        assertEquals(20, insight.targetValue)
        assertEquals(-8, insight.value)
    }

    @Test
    fun `insights use all analysis points even when the display list omits the event`() {
        val start = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            windKmh = 10.0,
            windModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val hiddenWindPeak = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T11:00:00Z"),
            windKmh = 48.0,
            windModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val displayedEnd = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T12:00:00Z"),
            windKmh = 12.0,
            windModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val overview = OverviewTimeline(
            mode = DisplayMode.HOURLY,
            analysisPoints = listOf(start, hiddenWindPeak, displayedEnd),
            displayPoints = listOf(start, displayedEnd)
        )

        val insight = buildForecastInsights(overview)
            .first { it.kind == ForecastInsightKind.WIND_RISING }

        assertEquals(hiddenWindPeak, insight.point)
    }

    @Test
    fun `specific rain insight absorbs nearby generic rain disagreement`() {
        val rainPoint = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            precipitationPercent = 55,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true,
            divergenceReasons = setOf(DivergenceReason.PRECIPITATION)
        )
        val nearbyDisagreement = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T11:00:00Z"),
            precipitationPercent = 20,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true,
            divergenceReasons = setOf(DivergenceReason.PRECIPITATION)
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(rainPoint, nearbyDisagreement))
        )

        assertTrue(insights.any { it.kind == ForecastInsightKind.RAIN_UNCERTAIN })
        assertFalse(insights.any { it.kind == ForecastInsightKind.DISAGREEMENT })
    }

    @Test
    fun `insights are ordered from nearest to farthest even when the later event is more severe`() {
        val start = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            temperatureC = 18.0,
            temperatureModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val warm = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T14:00:00Z"),
            temperatureC = 25.0,
            temperatureModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val storm = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T17:00:00Z"),
            temperatureC = 22.0,
            temperatureModelCount = 3,
            precipitationPercent = 90,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            condition = com.meteocompare.app.domain.model.WeatherCondition.THUNDERSTORM,
            modelCount = 3,
            hasMultiModelEvidence = true
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(start, warm, storm))
        )

        assertEquals(ForecastInsightKind.TEMPERATURE_CHANGE, insights.first().kind)
        assertEquals(ForecastInsightKind.RAIN_LIKELY, insights.last().kind)
        assertEquals(ForecastInsightLevel.ALERT, insights.last().level)
    }

    @Test
    fun `agreement insight is suppressed when a later point contains disagreement`() {
        val stablePoints = (0..2).map { index ->
            SimplifiedTimelinePoint(
                instant = now.plusSeconds(index * 3600L),
                temperatureC = 20.0 + index,
                modelCount = 3,
                temperatureModelCount = 3,
                hasMultiModelEvidence = true,
                consensusPercent = 85,
                consensusLevel = ModelConsensusLevel.HIGH
            )
        }
        val laterDisagreement = SimplifiedTimelinePoint(
            instant = now.plusSeconds(3 * 3600L),
            temperatureC = 24.0,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
            consensusPercent = 35,
            consensusLevel = ModelConsensusLevel.LOW,
            divergenceReasons = setOf(DivergenceReason.TEMPERATURE)
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, stablePoints + laterDisagreement)
        )

        assertTrue(insights.any { it.kind == ForecastInsightKind.DISAGREEMENT })
        assertFalse(insights.any { it.kind == ForecastInsightKind.HIGH_AGREEMENT })
    }

    @Test
    fun `a single model never produces a high agreement claim`() {
        val forecast = forecast(
            probabilityByModel = listOf(5),
            windByModel = listOf(12.0),
            maxByModel = listOf(22.0),
            dayCount = 2
        )

        val insights = buildForecastInsights(buildOverviewTimeline(forecast, now))

        assertFalse(insights.any { it.kind == ForecastInsightKind.HIGH_AGREEMENT })
        assertFalse(insights.any { it.kind == ForecastInsightKind.DISAGREEMENT })
    }

    private fun forecast(
        probabilityByModel: List<Int>,
        windByModel: List<Double>,
        maxByModel: List<Double>,
        dayCount: Int = 1
    ): CityForecast {
        val firstDate = LocalDate.of(2026, 7, 23)
        val dates = List(dayCount) { firstDate.plusDays(it.toLong()) }
        val models = listOf(
            WeatherModel.GFS,
            WeatherModel.ECMWF,
            WeatherModel.ICON_GLOBAL
        ).take(probabilityByModel.size)
        return CityForecast(
            city = City(
                id = "paris",
                name = "Paris",
                country = "France",
                latitude = 48.85,
                longitude = 2.35,
                timezone = "Europe/Paris"
            ),
            seriesByModel = models.mapIndexed { index, model ->
                val probability = probabilityByModel[index]
                model to ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(
                        timestamps = emptyList(),
                        temperature2m = emptyList(),
                        precipitation = emptyList(),
                        windSpeed10m = emptyList()
                    ),
                    daily = DailyForecast(
                        dates = dates,
                        tempMax = List(dayCount) { maxByModel[index] },
                        tempMin = List(dayCount) { 12.0 + index },
                        precipitationSum = List(dayCount) {
                            if (probability >= 50) 2.0 else 0.0
                        },
                        windSpeedMax = List(dayCount) { windByModel[index] },
                        weatherCode = List(dayCount) { if (probability >= 50) 61 else 1 },
                        precipitationProbabilityMax = List(dayCount) { probability }
                    )
                )
            }.toMap()
        )
    }
}
