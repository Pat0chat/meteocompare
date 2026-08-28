package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.ForecastCalibrationProfile
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineV3Test {

    private fun profile(
        bias: Double = 2.0,
        score: Int = 80,
        samples: Int = 30,
        stdDev: Double = 1.0,
        mae: Double = 1.0,
        observedWetDays: Int? = null,
        forecastWetDays: Int? = null
    ) = ForecastCalibrationProfile(
        bias = bias,
        score = score,
        standardDeviation = stdDev,
        meanAbsoluteError = mae,
        sampleSize = samples,
        observedWetDays = observedWetDays,
        forecastWetDays = forecastWetDays
    )

    private val closeEntries = listOf(
        ForecastConsensus.Entry(WeatherModel.GFS, 20.0),
        ForecastConsensus.Entry(WeatherModel.ECMWF, 21.0),
        ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, 22.0),
        ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, 23.0)
    )

    @Test
    fun `multi consensus is robust to an isolated outlier`() {
        val result = ForecastEngineV3.continuous(
            closeEntries + ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, 70.0),
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.MULTI_CONSENSUS)
        )

        assertEquals(ForecastEngine.MULTI_CONSENSUS, result.effectiveEngine)
        val central = requireNotNull(result.central)
        val low = requireNotNull(result.interval.low)
        val high = requireNotNull(result.interval.high)
        assertTrue(central < 30.0)
        assertTrue(low <= central)
        assertTrue(high >= central)
    }

    @Test
    fun `calibration requires at least two calibrated families`() {
        val baseline = ForecastEngineV3.continuous(closeEntries)
        val result = ForecastEngineV3.continuous(
            closeEntries,
            ForecastEngineV3.ContinuousOptions(
                engine = ForecastEngine.CALIBRATION,
                calibration = mapOf(WeatherModel.GFS to profile(bias = 8.0))
            )
        )

        assertTrue(result.fallback)
        assertEquals(ForecastEngineV3.FallbackReason.INSUFFICIENT_CALIBRATION, result.fallbackReason)
        assertEquals(ForecastEngine.MULTI_CONSENSUS, result.effectiveEngine)
        assertEquals(baseline.central!!, result.central!!, 1e-9)
    }

    @Test
    fun `calibration grows progressively between 14 and 30 observations`() {
        val calibration14 = closeEntries.associate { it.model to profile(bias = 3.0, samples = 14) }
        val calibration30 = closeEntries.associate { it.model to profile(bias = 3.0, samples = 30) }

        val partial = ForecastEngineV3.continuous(
            closeEntries,
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.CALIBRATION, calibration = calibration14)
        )
        val full = ForecastEngineV3.continuous(
            closeEntries,
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.CALIBRATION, calibration = calibration30)
        )

        assertFalse(partial.fallback)
        assertFalse(full.fallback)
        assertEquals(14.0 / 30.0, partial.calibrationStrength, 1e-9)
        assertEquals(1.0, full.calibrationStrength, 1e-9)
        assertTrue(full.central!! < partial.central!!)
    }

    @Test
    fun `scenario engine falls back explicitly when there is only one scenario`() {
        // Une série 20/21/22/23 est volontairement scindable par V3 : ses
        // intervalles réguliers forment deux groupes de poids comparables selon
        // le seuil robuste. Utiliser ici un nuage réellement compact permet de
        // tester le contrat SINGLE_SCENARIO sans contredire l'algorithme.
        val singleClusterEntries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, 20.0),
            ForecastConsensus.Entry(WeatherModel.ECMWF, 20.2),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, 20.4),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, 20.6)
        )
        val result = ForecastEngineV3.continuous(
            singleClusterEntries,
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.SCENARIOS)
        )

        assertTrue(result.fallback)
        assertEquals(ForecastEngineV3.FallbackReason.SINGLE_SCENARIO, result.fallbackReason)
        assertEquals(ForecastEngine.MULTI_CONSENSUS, result.effectiveEngine)
        assertEquals(1, result.scenarioCount)
    }

    @Test
    fun `scenario engine exposes two distinct clusters when split is meaningful`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, 10.0),
            ForecastConsensus.Entry(WeatherModel.ECMWF, 10.5),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, 11.0),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, 20.0),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, 20.5)
        )
        val result = ForecastEngineV3.continuous(
            entries,
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.SCENARIOS)
        )

        assertFalse(result.fallback)
        assertEquals(ForecastEngine.SCENARIOS, result.effectiveEngine)
        assertEquals(2, result.scenarioCount)
        assertEquals(2, result.scenarios.size)
        assertTrue(result.scenarioGap!! >= 8.0)
        assertTrue(result.dominantShare!! in 0.52..0.82)
    }

    @Test
    fun `adaptive prefers a strong scenario split`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.GFS, 10.0),
            ForecastConsensus.Entry(WeatherModel.ECMWF, 10.4),
            ForecastConsensus.Entry(WeatherModel.ARPEGE_EUROPE, 10.8),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, 20.0),
            ForecastConsensus.Entry(WeatherModel.GEM_GLOBAL, 20.4)
        )
        val result = ForecastEngineV3.continuous(
            entries,
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.ADAPTIVE)
        )

        assertEquals(ForecastEngine.SCENARIOS, result.effectiveEngine)
        assertEquals(ForecastEngineV3.Explanation.ADAPTIVE_SCENARIO, result.explanation)
    }

    @Test
    fun `adaptive calibration blend remains conservative`() {
        val calibration = closeEntries.associate { it.model to profile(bias = 2.0, score = 80, samples = 30) }
        val multi = ForecastEngineV3.continuous(closeEntries)
        val calibrated = ForecastEngineV3.continuous(
            closeEntries,
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.CALIBRATION, calibration = calibration)
        )
        val adaptive = ForecastEngineV3.continuous(
            closeEntries,
            ForecastEngineV3.ContinuousOptions(engine = ForecastEngine.ADAPTIVE, calibration = calibration)
        )

        assertEquals(ForecastEngine.CALIBRATION, adaptive.effectiveEngine)
        assertTrue(requireNotNull(adaptive.adaptiveTrust) in 0.5..0.85)
        val multiCentral = requireNotNull(multi.central)
        val calibratedCentral = requireNotNull(calibrated.central)
        val adaptiveCentral = requireNotNull(adaptive.central)
        val low = minOf(multiCentral, calibratedCentral)
        val high = maxOf(multiCentral, calibratedCentral)
        assertTrue(adaptiveCentral in low..high)
    }

    @Test
    fun `precipitation keeps native probability below one hundred even when deterministic amounts are wet`() {
        val result = ForecastEngineV3.precipitation(
            listOf(
                ForecastConsensus.PrecipitationRow(WeatherModel.GFS, amountMm = 4.0, probabilityPercent = 70),
                ForecastConsensus.PrecipitationRow(WeatherModel.ECMWF, amountMm = 5.0, probabilityPercent = 80),
                ForecastConsensus.PrecipitationRow(WeatherModel.ARPEGE_EUROPE, amountMm = 6.0, probabilityPercent = 90)
            ),
            ForecastEngineV3.PrecipitationOptions(threshold = 0.1)
        )

        assertEquals(80, result.probabilityPercent)
        assertEquals(3, result.wetModelCount)
        assertEquals(ForecastConsensus.PrecipitationSource.PROBABILITY, result.source)
    }

    @Test
    fun `precipitation probability without amount keeps amount unknown`() {
        val result = ForecastEngineV3.precipitation(
            listOf(
                ForecastConsensus.PrecipitationRow(WeatherModel.GFS, probabilityPercent = 80),
                ForecastConsensus.PrecipitationRow(WeatherModel.ECMWF, probabilityPercent = 70)
            )
        )

        assertEquals(75, result.probabilityPercent)
        assertEquals(null, result.centralAmountMm)
        assertEquals(null, result.expectedAmountMm)
    }

    @Test
    fun `precipitation occurrence calibration is guarded by family coverage`() {
        val rows = listOf(
            ForecastConsensus.PrecipitationRow(WeatherModel.GFS, amountMm = 2.0, probabilityPercent = 50),
            ForecastConsensus.PrecipitationRow(WeatherModel.ECMWF, amountMm = 2.0, probabilityPercent = 50)
        )
        val insufficient = ForecastEngineV3.precipitation(
            rows,
            ForecastEngineV3.PrecipitationOptions(
                engine = ForecastEngine.CALIBRATION,
                calibration = mapOf(
                    WeatherModel.GFS to profile(bias = 0.0, observedWetDays = 30, forecastWetDays = 0)
                )
            )
        )
        val covered = ForecastEngineV3.precipitation(
            rows,
            ForecastEngineV3.PrecipitationOptions(
                engine = ForecastEngine.CALIBRATION,
                calibration = mapOf(
                    WeatherModel.GFS to profile(bias = 0.0, observedWetDays = 30, forecastWetDays = 0),
                    WeatherModel.ECMWF to profile(bias = 0.0, observedWetDays = 30, forecastWetDays = 0)
                )
            )
        )

        assertEquals(50, insufficient.probabilityPercent)
        assertEquals(63, covered.probabilityPercent)
        assertTrue(covered.occurrenceCalibrationCoverage >= ForecastEngineV3.MIN_CALIBRATION_COVERAGE)
    }

    @Test
    fun `all engines are invariant to input ordering`() {
        val calibration = closeEntries.associate { it.model to profile(bias = 1.5) }
        ForecastEngine.entries.forEach { engine ->
            val options = ForecastEngineV3.ContinuousOptions(
                engine = engine,
                calibration = calibration
            )
            val forward = ForecastEngineV3.continuous(closeEntries, options)
            val reversed = ForecastEngineV3.continuous(closeEntries.reversed(), options)

            assertEquals(forward.central ?: Double.NaN, reversed.central ?: Double.NaN, 1e-9)
            assertEquals(forward.effectiveEngine, reversed.effectiveEngine)
            assertEquals(forward.scenarioCount, reversed.scenarioCount)
            assertEquals(forward.fallbackReason, reversed.fallbackReason)
            assertEquals(forward.interval.low ?: Double.NaN, reversed.interval.low ?: Double.NaN, 1e-9)
            assertEquals(forward.interval.high ?: Double.NaN, reversed.interval.high ?: Double.NaN, 1e-9)
        }
    }

    @Test
    fun `engine processing never mutates raw model entries`() {
        val raw = closeEntries.toList()
        val snapshot = raw.map { it.copy() }
        ForecastEngine.entries.forEach { engine ->
            ForecastEngineV3.continuous(
                raw,
                ForecastEngineV3.ContinuousOptions(
                    engine = engine,
                    calibration = raw.associate { it.model to profile() }
                )
            )
        }
        assertEquals(snapshot, raw)
    }

    @Test
    fun `unknown persisted engine safely falls back to multi consensus`() {
        assertEquals(ForecastEngine.MULTI_CONSENSUS, ForecastEngine.fromString("UNKNOWN_ENGINE"))
        assertEquals(ForecastEngine.MULTI_CONSENSUS, ForecastEngine.fromString(null))
        assertNotEquals(ForecastEngine.CALIBRATION, ForecastEngine.fromString("calibration"))
    }
}
