package com.meteocompare.app.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelReliabilityCalculatorTest {

    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun `serie parfaite obtient le score maximal`() {
        val samples = samples(30) { index ->
            val observed = 10.0 + index / 10.0
            observed to observed
        }

        val reliability = ModelReliabilityCalculator.compute(
            BiasVariable.TEMPERATURE,
            samples
        )!!

        assertEquals(100, reliability.score)
        assertEquals(ReliabilityLevel.EXCELLENT, reliability.level)
        assertEquals(0.0, reliability.meanAbsoluteError, 1e-9)
        assertEquals(1.0, reliability.withinToleranceRate, 1e-9)
    }

    @Test
    fun `biais proche de zero ne masque pas une erreur absolue elevee`() {
        val samples = samples(30) { index ->
            val observed = 12.0
            val forecast = if (index % 2 == 0) 14.0 else 10.0
            forecast to observed
        }

        val reliability = ModelReliabilityCalculator.compute(
            BiasVariable.TEMPERATURE,
            samples
        )!!

        assertEquals(0.0, reliability.meanBias, 1e-9)
        assertEquals(2.0, reliability.meanAbsoluteError, 1e-9)
        assertTrue(reliability.score < 85)
    }

    @Test
    fun `jours proches utilisent la tolerance propre a la variable`() {
        val samples = samples(20) { index ->
            val observed = 10.0
            val forecast = if (index < 15) 11.0 else 13.0
            forecast to observed
        }

        val reliability = ModelReliabilityCalculator.compute(
            BiasVariable.TEMPERATURE,
            samples
        )!!

        assertEquals(1.5, reliability.closeTolerance, 1e-9)
        assertEquals(0.75, reliability.withinToleranceRate, 1e-9)
    }

    @Test
    fun `tendance recente detecte une amelioration`() {
        val samples = samples(21) { index ->
            val observed = 10.0
            val error = if (index < 14) 3.0 else 0.3
            observed + error to observed
        }

        val reliability = ModelReliabilityCalculator.compute(
            BiasVariable.TEMPERATURE,
            samples
        )!!

        assertEquals(ReliabilityTrend.IMPROVING, reliability.trend)
        assertTrue(reliability.recentMeanAbsoluteError!! < reliability.previousMeanAbsoluteError!!)
    }

    @Test
    fun `diagnostic pluie distingue detection fausse alerte et episode manque`() {
        val patterns = listOf(
            3.0 to 2.0,  // hit
            1.0 to 0.0,  // false alarm
            0.0 to 4.0,  // miss
            0.0 to 0.0   // correct dry
        )
        val samples = samples(16) { index -> patterns[index % patterns.size] }

        val reliability = ModelReliabilityCalculator.compute(
            BiasVariable.PRECIPITATION,
            samples
        )!!
        val rain = reliability.precipitation!!

        assertEquals(0.5, rain.hitRate!!, 1e-9)
        assertEquals(0.5, rain.falseAlarmRate!!, 1e-9)
        assertEquals(0.5, rain.missedEventRate!!, 1e-9)
        assertEquals(4, rain.hitCount)
        assertEquals(4, rain.falseAlarmCount)
        assertEquals(4, rain.missedEventCount)
        assertEquals(8, rain.observedWetDays)
        assertEquals(8, rain.forecastWetDays)
    }


    @Test
    fun `diagnostic pluie renvoie indisponible sans evenement de reference`() {
        val reliability = ModelReliabilityCalculator.compute(
            BiasVariable.PRECIPITATION,
            samples(20) { 0.0 to 0.0 }
        )!!
        val rain = reliability.precipitation!!

        assertEquals(null, rain.hitRate)
        assertEquals(null, rain.missedEventRate)
        assertEquals(null, rain.falseAlarmRate)
    }

    @Test
    fun `rang local ordonne le score puis la mae`() {
        val perfect = ModelReliabilityCalculator.compute(
            BiasVariable.TEMPERATURE,
            samples(30) { 10.0 to 10.0 }
        )!!
        val medium = ModelReliabilityCalculator.compute(
            BiasVariable.TEMPERATURE,
            samples(30) { 11.0 to 10.0 }
        )!!
        val poor = ModelReliabilityCalculator.compute(
            BiasVariable.TEMPERATURE,
            samples(30) { 14.0 to 10.0 }
        )!!

        val rank = ModelReliabilityCalculator.rank(
            WeatherModel.GFS,
            mapOf(
                WeatherModel.ECMWF to perfect,
                WeatherModel.GFS to medium,
                WeatherModel.ICON_GLOBAL to poor
            )
        )!!

        assertEquals(2, rank.rank)
        assertEquals(3, rank.modelCount)
    }

    @Test
    fun `reference multi modeles moyenne les previsions par date`() {
        val history = mapOf(
            WeatherModel.GFS to samples(30) { 12.0 to 10.0 },
            WeatherModel.ECMWF to samples(30) { 8.0 to 10.0 }
        )

        val baseline = ModelReliabilityCalculator.computeMultiModelBaseline(
            BiasVariable.TEMPERATURE,
            history
        )

        assertNotNull(baseline)
        assertEquals(0.0, baseline!!.meanAbsoluteError, 1e-9)
        assertEquals(100, baseline.score)
    }

    private fun samples(
        count: Int,
        values: (Int) -> Pair<Double, Double>
    ): List<BiasSample> = List(count) { index ->
        val (forecast, observation) = values(index)
        BiasSample(
            targetDate = start.plusDays(index.toLong()),
            forecast = forecast,
            observation = observation
        )
    }
}
