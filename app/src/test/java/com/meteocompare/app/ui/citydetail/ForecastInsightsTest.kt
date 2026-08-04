package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastInsightsTest {
    private val start = Instant.parse("2026-07-23T08:00:00Z")

    @Test
    fun `rain phenomenon and rain uncertainty are fused into one event`() {
        val rain = point(
            hour = 4,
            precipitation = 80,
            condition = WeatherCondition.RAIN,
            reasons = setOf(DivergenceReason.PRECIPITATION),
            metricConsensus = rainConsensus(42, divergent = true)
        )
        val laterSplit = point(
            hour = 5,
            precipitation = 45,
            condition = WeatherCondition.RAIN,
            reasons = setOf(DivergenceReason.PRECIPITATION),
            metricConsensus = rainConsensus(38, divergent = true)
        )

        val events = detectForecastEvents(overview(listOf(rain, laterSplit)))

        assertEquals(1, events.count { it.kind == ForecastEventKind.PRECIPITATION })
        assertFalse(events.any { it.kind == ForecastEventKind.UNCERTAINTY })
        assertTrue(DivergenceReason.PRECIPITATION in events.first().evidence.divergenceReasons)
        assertEquals(42, events.first().evidence.consensus?.percent)
    }

    @Test
    fun `event exposes metric specific evidence`() {
        val rain = point(
            hour = 3,
            precipitation = 75,
            condition = WeatherCondition.RAIN,
            reasons = setOf(DivergenceReason.PRECIPITATION),
            metricConsensus = rainConsensus(44, divergent = true),
            probabilityMin = 35,
            probabilityMax = 90
        )

        val event = detectForecastEvents(overview(listOf(rain))).first()

        assertEquals(ForecastMetric.PRECIPITATION, event.evidence.metric)
        assertEquals(35, event.evidence.probabilityMinimum)
        assertEquals(90, event.evidence.probabilityMaximum)
        assertEquals(44, event.evidence.consensus?.percent)
    }

    @Test
    fun `routine morning warming is not an insight`() {
        val points = listOf(
            point(hour = 0, temperature = 12.0),
            point(hour = 3, temperature = 17.0),
            point(hour = 6, temperature = 22.0)
        )

        val insights = buildForecastInsights(overview(points, timezone = "Europe/Paris"))

        assertFalse(insights.any { it.kind == ForecastInsightKind.TEMPERATURE_CHANGE })
    }

    @Test
    fun `frost threshold creates a useful temperature event`() {
        val points = listOf(
            point(hour = 0, temperature = 4.0),
            point(hour = 3, temperature = -1.0, reasons = setOf(DivergenceReason.TEMPERATURE))
        )

        val insight = buildForecastInsights(overview(points))
            .first { it.kind == ForecastInsightKind.TEMPERATURE_CHANGE }

        assertEquals(-1, insight.targetValue)
        assertNotNull(insight.event)
        assertEquals(ForecastEventKind.TEMPERATURE, insight.event?.kind)
    }

    @Test
    fun `heat threshold creates a useful temperature event`() {
        val points = listOf(
            point(hour = 0, temperature = 24.0),
            point(hour = 3, temperature = 31.0)
        )

        val insights = buildForecastInsights(overview(points, timezone = "UTC"))

        assertTrue(insights.any {
            it.kind == ForecastInsightKind.TEMPERATURE_CHANGE && it.targetValue == 31
        })
    }

    @Test
    fun `unrelated disagreement remains a separate event`() {
        val rain = point(hour = 2, precipitation = 85, condition = WeatherCondition.RAIN)
        val windSplit = point(
            hour = 7,
            wind = 32.0,
            reasons = setOf(DivergenceReason.WIND),
            metricConsensus = mapOf(
                ForecastMetric.WIND to MetricConsensus(
                    ForecastMetric.WIND, 35, 4, ModelConsensusLevel.LOW,
                    minimum = 15.0, maximum = 52.0, isDivergent = true
                )
            )
        )

        val events = detectForecastEvents(overview(listOf(rain, windSplit)))

        assertTrue(events.any { it.kind == ForecastEventKind.PRECIPITATION })
        assertTrue(events.any { it.kind == ForecastEventKind.UNCERTAINTY })
    }

    @Test
    fun `stable state is a fallback only`() {
        val stable = (0..3).map { hour ->
            point(hour = hour, consensus = 88, consensusLevel = ModelConsensusLevel.HIGH)
        }

        val insights = buildForecastInsights(overview(stable))

        assertEquals(1, insights.size)
        assertEquals(ForecastInsightKind.HIGH_AGREEMENT, insights.single().kind)
        assertEquals(ForecastEventKind.STABLE, insights.single().event?.kind)
    }

    @Test
    fun `stable message disappears when any disagreement exists`() {
        val stable = (0..2).map { hour ->
            point(hour = hour, consensus = 88, consensusLevel = ModelConsensusLevel.HIGH)
        }
        val split = point(
            hour = 3,
            reasons = setOf(DivergenceReason.CONDITION),
            consensus = 40,
            consensusLevel = ModelConsensusLevel.LOW
        )

        val insights = buildForecastInsights(overview(stable + split))

        assertFalse(insights.any { it.kind == ForecastInsightKind.HIGH_AGREEMENT })
        assertTrue(insights.any { it.kind == ForecastInsightKind.DISAGREEMENT })
    }

    @Test
    fun `insights remain chronological while impact is stored independently`() {
        val nearWatch = point(hour = 2, wind = 50.0)
        val laterAlert = point(
            hour = 7,
            precipitation = 90,
            condition = WeatherCondition.THUNDERSTORM
        )

        val insights = buildForecastInsights(overview(listOf(nearWatch, laterAlert)))

        assertTrue(insights.first().point!!.instant!! < insights.last().point!!.instant!!)
        assertEquals(ForecastInsightLevel.ALERT, insights.last().level)
    }

    @Test
    fun `at most three insights are exposed`() {
        val points = listOf(
            point(hour = 1, precipitation = 85, condition = WeatherCondition.RAIN),
            point(hour = 3, wind = 55.0),
            point(hour = 5, temperature = -2.0, reasons = setOf(DivergenceReason.TEMPERATURE)),
            point(hour = 7, condition = WeatherCondition.FOG, reasons = setOf(DivergenceReason.CONDITION))
        )

        assertTrue(buildForecastInsights(overview(points)).size <= 3)
    }

    @Test
    fun `weather transition creates an event object`() {
        val clear = point(hour = 0, condition = WeatherCondition.CLEAR)
        val fog = point(hour = 3, condition = WeatherCondition.FOG)
        val fog2 = point(hour = 4, condition = WeatherCondition.FOG)

        val event = detectForecastEvents(overview(listOf(clear, fog, fog2)))
            .first { it.kind == ForecastEventKind.WEATHER_TRANSITION }

        assertEquals(WeatherCondition.FOG, event.condition)
        assertEquals(ForecastMetric.CONDITION, event.evidence.metric)
    }

    private fun overview(
        points: List<SimplifiedTimelinePoint>,
        timezone: String = "UTC"
    ) = OverviewTimeline(DisplayMode.HOURLY, points, timezone)

    private fun point(
        hour: Int,
        temperature: Double? = 20.0,
        precipitation: Int? = null,
        wind: Double? = 12.0,
        condition: WeatherCondition? = WeatherCondition.CLEAR,
        reasons: Set<DivergenceReason> = emptySet(),
        metricConsensus: Map<ForecastMetric, MetricConsensus> = defaultConsensus(),
        probabilityMin: Int? = precipitation,
        probabilityMax: Int? = precipitation,
        consensus: Int = 80,
        consensusLevel: ModelConsensusLevel = ModelConsensusLevel.HIGH
    ) = SimplifiedTimelinePoint(
        instant = start.plusSeconds(hour * 3_600L),
        temperatureC = temperature,
        temperatureMinAcrossModels = temperature?.minus(1),
        temperatureMaxAcrossModels = temperature?.plus(1),
        precipitationPercent = precipitation,
        precipitationSource = precipitation?.let { PrecipitationSignalSource.MODEL_PROBABILITY },
        precipitationModelCount = if (precipitation != null) 4 else 0,
        wetModelCount = if ((precipitation ?: 0) >= 50) 3 else 0,
        precipitationMm = precipitation?.let { 2.0 },
        precipitationMinAcrossModelsMm = precipitation?.let { 0.2 },
        precipitationMaxAcrossModelsMm = precipitation?.let { 5.0 },
        precipitationProbabilityMin = probabilityMin,
        precipitationProbabilityMax = probabilityMax,
        windKmh = wind,
        windMinAcrossModels = wind?.minus(5),
        windMaxAcrossModels = wind?.plus(5),
        condition = condition,
        modelCount = 4,
        temperatureModelCount = if (temperature != null) 4 else 0,
        windModelCount = if (wind != null) 4 else 0,
        conditionModelCount = if (condition != null) 4 else 0,
        hasMultiModelEvidence = true,
        consensusPercent = consensus,
        consensusLevel = consensusLevel,
        metricConsensus = metricConsensus,
        divergenceReasons = reasons
    )

    private fun rainConsensus(percent: Int, divergent: Boolean) = mapOf(
        ForecastMetric.PRECIPITATION to MetricConsensus(
            metric = ForecastMetric.PRECIPITATION,
            percent = percent,
            modelCount = 4,
            level = if (percent >= 50) ModelConsensusLevel.MEDIUM else ModelConsensusLevel.LOW,
            minimum = 20.0,
            maximum = 90.0,
            isDivergent = divergent
        )
    )

    companion object {
        private fun defaultConsensus() = mapOf(
            ForecastMetric.TEMPERATURE to MetricConsensus(
                ForecastMetric.TEMPERATURE, 80, 4, ModelConsensusLevel.HIGH,
                minimum = 19.0, maximum = 21.0
            ),
            ForecastMetric.WIND to MetricConsensus(
                ForecastMetric.WIND, 80, 4, ModelConsensusLevel.HIGH,
                minimum = 8.0, maximum = 16.0
            ),
            ForecastMetric.CONDITION to MetricConsensus(
                ForecastMetric.CONDITION, 80, 4, ModelConsensusLevel.HIGH
            )
        )
    }
}
