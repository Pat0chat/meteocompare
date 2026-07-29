package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
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
        )
        val target = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T14:00:00Z"),
            temperatureC = 25.1,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
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
        )
        val target = SimplifiedTimelinePoint(
            date = LocalDate.of(2026, 7, 24),
            tempMaxC = 19.9,
            modelCount = 3,
            temperatureModelCount = 3,
            hasMultiModelEvidence = true,
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
            analysisPoints = listOf(start, hiddenWindPeak, displayedEnd)
        )

        val insight = buildForecastInsights(overview)
            .first { it.kind == ForecastInsightKind.WIND_EVENT }

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
    fun `likely rain does not hide a separate nearby rain disagreement`() {
        val likelyRain = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T16:00:00Z"),
            precipitationPercent = 85,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            wetModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val rainDisagreement = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T17:00:00Z"),
            precipitationPercent = 20,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true,
            divergenceReasons = setOf(DivergenceReason.PRECIPITATION)
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(likelyRain, rainDisagreement))
        )

        assertTrue(insights.any { it.kind == ForecastInsightKind.RAIN_LIKELY })
        assertTrue(insights.any { it.kind == ForecastInsightKind.DISAGREEMENT })
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

    @Test
    fun `positive weather change never evicts three actionable signals`() {
        val points = listOf(
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z"),
                temperatureC = 20.0,
                temperatureModelCount = 3,
                precipitationPercent = 80,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                windKmh = 10.0,
                windModelCount = 3,
                condition = WeatherCondition.RAIN,
                conditionModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T11:00:00Z"),
                temperatureC = 21.0,
                temperatureModelCount = 3,
                precipitationPercent = 5,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                windKmh = 12.0,
                windModelCount = 3,
                condition = WeatherCondition.CLEAR,
                conditionModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T13:00:00Z"),
                temperatureC = 22.0,
                temperatureModelCount = 3,
                precipitationPercent = 5,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                windKmh = 35.0,
                windModelCount = 3,
                condition = WeatherCondition.CLEAR,
                conditionModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T17:00:00Z"),
                temperatureC = 28.0,
                temperatureModelCount = 3,
                precipitationPercent = 5,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                windKmh = 20.0,
                windModelCount = 3,
                condition = WeatherCondition.CLEAR,
                conditionModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            )
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, points)
        )

        assertEquals(3, insights.size)
        assertTrue(insights.any { it.kind == ForecastInsightKind.RAIN_LIKELY })
        assertTrue(insights.any { it.kind == ForecastInsightKind.WIND_EVENT })
        assertTrue(insights.any { it.kind == ForecastInsightKind.TEMPERATURE_CHANGE })
        assertFalse(insights.any { it.kind == ForecastInsightKind.WEATHER_CHANGE })
    }

    @Test
    fun `meaningful weather change is surfaced with its baseline and target`() {
        val clear = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            condition = WeatherCondition.CLEAR,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val fog = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T13:00:00Z"),
            condition = WeatherCondition.FOG,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val persistentFog = fog.copy(
            instant = Instant.parse("2026-07-23T14:00:00Z")
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(clear, fog, persistentFog))
        ).first { it.kind == ForecastInsightKind.WEATHER_CHANGE }

        assertEquals(clear, insight.referencePoint)
        assertEquals(fog, insight.point)
        assertEquals(WeatherCondition.CLEAR, insight.referenceCondition)
        assertEquals(WeatherCondition.FOG, insight.targetCondition)
        assertEquals(ForecastInsightLevel.WATCH, insight.level)
    }

    @Test
    fun `explicit rain signal absorbs a nearby weather change to rain`() {
        val clear = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            condition = WeatherCondition.CLEAR,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val rain = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T12:00:00Z"),
            condition = WeatherCondition.RAIN,
            conditionModelCount = 3,
            precipitationPercent = 80,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(clear, rain))
        )

        assertTrue(insights.any { it.kind == ForecastInsightKind.RAIN_LIKELY })
        assertFalse(insights.any { it.kind == ForecastInsightKind.WEATHER_CHANGE })
    }

    @Test
    fun `isolated moderate rain signal is ignored`() {
        val points = listOf(
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z"),
                precipitationPercent = 10,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T11:00:00Z"),
                precipitationPercent = 72,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T12:00:00Z"),
                precipitationPercent = 10,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            )
        )

        val insights = buildForecastInsights(OverviewTimeline(DisplayMode.HOURLY, points))

        assertFalse(insights.any {
            it.kind == ForecastInsightKind.RAIN_LIKELY ||
                it.kind == ForecastInsightKind.RAIN_UNCERTAIN
        })
    }

    @Test
    fun `strengthening rain episode produces one concise rain insight`() {
        val points = listOf(40, 55, 80, 85).mapIndexed { index, probability ->
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z").plusSeconds(index * 3600L),
                precipitationPercent = probability,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            )
        }

        val rainInsights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, points)
        ).filter {
            it.kind == ForecastInsightKind.RAIN_LIKELY ||
                it.kind == ForecastInsightKind.RAIN_UNCERTAIN
        }

        assertEquals(1, rainInsights.size)
        val insight = rainInsights.single()
        assertEquals(ForecastInsightKind.RAIN_LIKELY, insight.kind)
        assertEquals(40, insight.referenceValue)
        assertEquals(85, insight.targetValue)
        assertTrue(insight.isStrengtheningRainSignal)
        assertTrue(insight.isPersistent)
    }

    @Test
    fun `transient hourly condition change is ignored`() {
        val clear = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            condition = WeatherCondition.CLEAR,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val briefFog = clear.copy(
            instant = Instant.parse("2026-07-23T11:00:00Z"),
            condition = WeatherCondition.FOG
        )
        val clearAgain = clear.copy(
            instant = Instant.parse("2026-07-23T12:00:00Z")
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(clear, briefFog, clearAgain))
        )

        assertFalse(insights.any { it.kind == ForecastInsightKind.WEATHER_CHANGE })
    }

    @Test
    fun `temperature insight selects the sharper later transition`() {
        val points = listOf(
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z"),
                temperatureC = 18.0,
                temperatureModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T14:00:00Z"),
                temperatureC = 26.0,
                temperatureModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T17:00:00Z"),
                temperatureC = 19.0,
                temperatureModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            )
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, points)
        ).first { it.kind == ForecastInsightKind.TEMPERATURE_CHANGE }

        assertEquals(points[1], insight.referencePoint)
        assertEquals(points[2], insight.point)
        assertEquals(-7, insight.value)
    }

    @Test
    fun `already strong wind is surfaced without requiring a large rise`() {
        val points = listOf(48.0, 50.0, 47.0).mapIndexed { index, wind ->
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z").plusSeconds(index * 3600L),
                windKmh = wind,
                windModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            )
        }

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, points)
        ).first { it.kind == ForecastInsightKind.WIND_EVENT }

        assertEquals(48, insight.value)
        assertEquals(50, insight.secondaryValue)
        assertEquals(ForecastInsightLevel.WATCH, insight.level)
    }

    @Test
    fun `later alert is retained ahead of lower value information`() {
        val points = listOf(
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z"),
                temperatureC = 18.0,
                temperatureModelCount = 3,
                windKmh = 10.0,
                windModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T11:00:00Z"),
                temperatureC = 20.0,
                temperatureModelCount = 3,
                windKmh = 12.0,
                windModelCount = 3,
                consensusPercent = 45,
                consensusLevel = ModelConsensusLevel.LOW,
                divergenceReasons = setOf(DivergenceReason.CONDITION),
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T13:00:00Z"),
                temperatureC = 26.0,
                temperatureModelCount = 3,
                windKmh = 32.0,
                windModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T18:00:00Z"),
                temperatureC = 22.0,
                temperatureModelCount = 3,
                precipitationPercent = 90,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                condition = WeatherCondition.THUNDERSTORM,
                conditionModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            )
        )

        val insights = buildForecastInsights(OverviewTimeline(DisplayMode.HOURLY, points))

        assertTrue(insights.any {
            it.kind == ForecastInsightKind.RAIN_LIKELY &&
                it.level == ForecastInsightLevel.ALERT
        })
        assertTrue(insights.size <= 3)
    }

    @Test
    fun `later severe rain episode wins over earlier weak uncertainty`() {
        val points = listOf(
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z"),
                precipitationPercent = 40,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T11:00:00Z"),
                precipitationPercent = 45,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            ),
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T18:00:00Z"),
                precipitationPercent = 90,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                condition = WeatherCondition.THUNDERSTORM,
                conditionModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true
            )
        )

        val rainInsight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, points)
        ).first {
            it.kind == ForecastInsightKind.RAIN_LIKELY ||
                it.kind == ForecastInsightKind.RAIN_UNCERTAIN
        }

        assertEquals(ForecastInsightKind.RAIN_LIKELY, rainInsight.kind)
        assertEquals(points.last(), rainInsight.point)
        assertEquals(ForecastInsightLevel.ALERT, rainInsight.level)
    }

    @Test
    fun `generic agreement is only used as a fallback`() {
        val points = listOf(48.0, 50.0, 47.0).mapIndexed { index, wind ->
            SimplifiedTimelinePoint(
                instant = Instant.parse("2026-07-23T10:00:00Z").plusSeconds(index * 3600L),
                windKmh = wind,
                windModelCount = 3,
                modelCount = 3,
                hasMultiModelEvidence = true,
                consensusPercent = 85,
                consensusLevel = ModelConsensusLevel.HIGH
            )
        }

        val insights = buildForecastInsights(OverviewTimeline(DisplayMode.HOURLY, points))

        assertTrue(insights.any { it.kind == ForecastInsightKind.WIND_EVENT })
        assertFalse(insights.any { it.kind == ForecastInsightKind.HIGH_AGREEMENT })
    }

    @Test
    fun `condition persistence does not bridge a large hourly gap`() {
        val clear = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            condition = WeatherCondition.CLEAR,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val fog = clear.copy(
            instant = Instant.parse("2026-07-23T13:00:00Z"),
            condition = WeatherCondition.FOG
        )
        val distantFog = fog.copy(
            instant = Instant.parse("2026-07-23T16:00:00Z")
        )

        val insights = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(clear, fog, distantFog))
        )

        assertFalse(insights.any { it.kind == ForecastInsightKind.WEATHER_CHANGE })
    }

    @Test
    fun `wind persistence does not bridge missing hours`() {
        val first = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            windKmh = 48.0,
            windModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val distantPeak = first.copy(
            instant = Instant.parse("2026-07-23T14:00:00Z"),
            windKmh = 50.0
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(first, distantPeak))
        ).first { it.kind == ForecastInsightKind.WIND_EVENT }

        assertFalse(insight.isPersistent)
    }

    @Test
    fun `strongest disagreement episode is selected`() {
        val weak = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            modelCount = 3,
            hasMultiModelEvidence = true,
            consensusPercent = 45,
            consensusLevel = ModelConsensusLevel.LOW,
            divergenceReasons = setOf(DivergenceReason.TEMPERATURE)
        )
        val stable = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T11:00:00Z"),
            modelCount = 3,
            hasMultiModelEvidence = true,
            consensusPercent = 80,
            consensusLevel = ModelConsensusLevel.HIGH
        )
        val strong = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T16:00:00Z"),
            modelCount = 3,
            hasMultiModelEvidence = true,
            consensusPercent = 20,
            consensusLevel = ModelConsensusLevel.LOW,
            divergenceReasons = setOf(
                DivergenceReason.PRECIPITATION,
                DivergenceReason.WIND
            )
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(weak, stable, strong))
        ).first { it.kind == ForecastInsightKind.DISAGREEMENT }

        assertEquals(strong, insight.point)
        assertEquals(ForecastInsightLevel.ALERT, insight.level)
    }

    @Test
    fun `specialized precipitation insight points to the thunderstorm hour`() {
        val rainStart = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T16:00:00Z"),
            precipitationPercent = 95,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            condition = WeatherCondition.RAIN,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val storm = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T18:00:00Z"),
            precipitationPercent = 80,
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
            precipitationModelCount = 3,
            condition = WeatherCondition.THUNDERSTORM,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(rainStart, storm))
        ).first { it.kind == ForecastInsightKind.RAIN_LIKELY }

        assertEquals(storm, insight.point)
        assertEquals(WeatherCondition.THUNDERSTORM, insight.targetCondition)
        assertEquals(80, insight.targetValue)
        assertEquals(ForecastInsightLevel.ALERT, insight.level)
    }

    @Test
    fun `weather transition uses the immediately preceding condition`() {
        val overcast = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            condition = WeatherCondition.OVERCAST,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val clear = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T11:00:00Z"),
            condition = WeatherCondition.CLEAR,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val fog = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T12:00:00Z"),
            condition = WeatherCondition.FOG,
            conditionModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val persistentFog = fog.copy(instant = Instant.parse("2026-07-23T13:00:00Z"))

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(overcast, clear, fog, persistentFog))
        ).first { it.kind == ForecastInsightKind.WEATHER_CHANGE }

        assertEquals(clear, insight.referencePoint)
        assertEquals(WeatherCondition.CLEAR, insight.referenceCondition)
        assertEquals(WeatherCondition.FOG, insight.targetCondition)
    }

    @Test
    fun `wind rise uses the recent local minimum as its baseline`() {
        val initiallyStrong = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T10:00:00Z"),
            windKmh = 40.0,
            windModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val lull = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T12:00:00Z"),
            windKmh = 12.0,
            windModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )
        val peak = SimplifiedTimelinePoint(
            instant = Instant.parse("2026-07-23T14:00:00Z"),
            windKmh = 46.0,
            windModelCount = 3,
            modelCount = 3,
            hasMultiModelEvidence = true
        )

        val insight = buildForecastInsights(
            OverviewTimeline(DisplayMode.HOURLY, listOf(initiallyStrong, lull, peak))
        ).first { it.kind == ForecastInsightKind.WIND_EVENT }

        assertEquals(lull, insight.referencePoint)
        assertEquals(12, insight.value)
        assertEquals(46, insight.secondaryValue)
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
