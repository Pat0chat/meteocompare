package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.WeatherModel
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic, mixed-source stress coverage of all four engine strategies. */
class ForecastEngineInvariantMatrixTest {
    private val models = WeatherModel.entries
    private val extremes = listOf(
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        -1_000.0, -100.0, -20.0, 0.0, 2.5, 45.0, 70.0, 1_000.0
    )

    @Test fun continuousOutputsStayFiniteAndWithinRequestedLimits() {
        repeat(180) { seed ->
            val rng = Random(seed + 75_001)
            val selected = models.shuffled(rng).take(6)
            val entries = selected.map { model ->
                ForecastConsensus.Entry(model, extremes[rng.nextInt(extremes.size)])
            }
            ForecastEngine.entries.forEach { engine ->
                val result = ForecastEngineV3.continuous(entries, ForecastEngineV3.ContinuousOptions(
                    engine = engine, min = -100.0, max = 70.0, tight = 1.0, wide = 5.0
                ))
                for (value in listOf(result.central, result.interval.low, result.interval.high)) {
                    assertTrue("$seed $engine: $value", value == null || (value.isFinite() && value in -100.0..70.0))
                }
                if (result.interval.low != null && result.interval.high != null) {
                    assertTrue("$seed $engine: reversed interval", result.interval.low <= result.interval.high)
                }
                assertTrue(result.familyCount <= result.modelCount)
            }
        }
    }

    @Test fun precipitationRemainsPhysicalWithMissingAndCorruptInputs() {
        repeat(180) { seed ->
            val rng = Random(seed + 93_117)
            val rows = models.shuffled(rng).take(8).map { model ->
                ForecastConsensus.PrecipitationRow(
                    model, extremes[rng.nextInt(extremes.size)],
                    listOf<Int?>(null, -1, 0, 30, 50, 80, 100, 101)[rng.nextInt(8)]
                )
            }
            ForecastEngine.entries.forEach { engine ->
                val result = ForecastEngineV3.precipitation(
                    rows, ForecastEngineV3.PrecipitationOptions(engine = engine)
                )
                assertTrue("$seed $engine", result.probabilityPercent == null || result.probabilityPercent in 0..100)
                for (mm in listOf(result.centralAmountMm, result.conditionalAmountMm, result.expectedAmountMm)) {
                    assertTrue("$seed $engine: $mm", mm == null || (mm.isFinite() && mm in 0.0..5_000.0))
                }
                assertTrue("$seed $engine: model count", result.modelCount <= rows.size)
                assertTrue("$seed $engine: family count", result.familyCount <= result.modelCount)
            }
        }
    }

    @Test fun permutationOfModelsDoesNotChangeDeterministicCentral() {
        repeat(100) { seed ->
            val rng = Random(seed + 10_201)
            val entries = models.shuffled(rng).take(6).map { model ->
                ForecastConsensus.Entry(model, rng.nextDouble(-20.0, 40.0))
            }
            ForecastEngine.entries.forEach { engine ->
                val options = ForecastEngineV3.ContinuousOptions(engine = engine)
                val forward = ForecastEngineV3.continuous(entries, options)
                val backward = ForecastEngineV3.continuous(entries.reversed(), options)
                assertEquals("$seed $engine", forward.central ?: Double.NaN,
                    backward.central ?: Double.NaN, 1e-7)
                assertEquals("$seed $engine", forward.effectiveEngine, backward.effectiveEngine)
            }
        }
    }
}
