package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ForecastCalibrationProfile
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ModelReliabilityCalculator
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parité entre le moteur V3 de l'app et son portage Python du banc d'essai
 * (scripts/backtest/engines.py, scripts/backtest/reliability.py).
 *
 * Les cas et leurs résultats attendus sont produits par
 * `python3 -m scripts.backtest.parity`. Un échec signifie que l'une des deux
 * implémentations a changé : le banc d'essai ne mesure plus l'app tant que
 * le portage n'est pas réaligné et le corpus régénéré.
 */
class ForecastEngineV3ParityTest {

    private fun records(file: String): List<Map<String, String>> {
        val path = "/engine_parity/v1/$file"
        val stream = requireNotNull(javaClass.getResourceAsStream(path)) { "Missing parity corpus $path" }
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            val iterator = lines.filter { it.isNotBlank() }.iterator()
            val header = iterator.next().split('\t')
            iterator.asSequence().map { line ->
                val columns = line.split('\t')
                require(columns.size == header.size) { "$file: ${columns.size} columns for ${header.size}" }
                header.zip(columns).toMap()
            }.toList()
        }
    }

    private fun String.nullable(): String? = takeUnless { it == "_" }
    private fun String.doubleOrNullValue(): Double? = nullable()?.toDouble()
    private fun String.intOrNullValue(): Int? = nullable()?.toInt()

    private fun assertClose(id: String, field: String, expected: Double?, actual: Double?) {
        if (expected == null) {
            assertNull("$id $field: expected null, got $actual", actual)
            return
        }
        assertTrue("$id $field: expected $expected, got null", actual != null)
        val tolerance = 1e-9 * max(1.0, abs(expected))
        assertEquals("$id $field", expected, actual!!, tolerance)
    }

    private fun profiles(raw: String): Map<WeatherModel, ForecastCalibrationProfile> {
        if (raw == "_") return emptyMap()
        return raw.split(';').associate { item ->
            val p = item.split(':')
            WeatherModel.valueOf(p[0]) to ForecastCalibrationProfile(
                bias = p[1].toDouble(),
                score = p[2].toInt(),
                standardDeviation = p[3].toDouble(),
                meanAbsoluteError = p[4].toDouble(),
                sampleSize = p[5].toInt(),
                observedWetDays = p[6].intOrNullValue(),
                forecastWetDays = p[7].intOrNullValue(),
                leadDay = p[8].toInt(),
                wetHitBias = p[9].doubleOrNullValue(),
                wetHitScore = p[10].intOrNullValue(),
                wetHitStandardDeviation = p[11].doubleOrNullValue(),
                wetHitMeanAbsoluteError = p[12].doubleOrNullValue(),
                wetHitSampleSize = p[13].toInt()
            )
        }
    }

    @Test
    fun `continuous engines match the Python port`() {
        val cases = records("continuous.tsv")
        assertTrue("continuous corpus is empty", cases.size >= 400)
        cases.forEach { row ->
            val id = row.getValue("id")
            val entries = row.getValue("entries").split(';').map { item ->
                val (model, value) = item.split(':')
                ForecastConsensus.Entry(WeatherModel.valueOf(model), value.toDouble())
            }
            val result = ForecastEngineV3.continuous(
                entries,
                ForecastEngineV3.ContinuousOptions(
                    engine = ForecastEngine.valueOf(row.getValue("engine")),
                    calibration = profiles(row.getValue("profiles")),
                    tight = row.getValue("tight").toDouble(),
                    wide = row.getValue("wide").toDouble(),
                    min = row.getValue("min").doubleOrNullValue(),
                    max = row.getValue("max").doubleOrNullValue()
                )
            )
            assertClose(id, "central", row.getValue("central").doubleOrNullValue(), result.central)
            assertClose(id, "low", row.getValue("low").doubleOrNullValue(), result.interval.low)
            assertClose(id, "high", row.getValue("high").doubleOrNullValue(), result.interval.high)
            assertEquals("$id effective", row.getValue("effective"), result.effectiveEngine.name)
            assertEquals("$id fallback", row.getValue("fallback").toBoolean(), result.fallback)
            assertEquals("$id convergence", row.getValue("convergence").intOrNullValue(), result.engineConvergencePercent)
        }
    }

    @Test
    fun `precipitation engine matches the Python port`() {
        val cases = records("precipitation.tsv")
        assertTrue("precipitation corpus is empty", cases.size >= 200)
        cases.forEach { row ->
            val id = row.getValue("id")
            val rows = row.getValue("rows").split(';').map { item ->
                val (model, amount) = item.split(':')
                ForecastConsensus.PrecipitationRow(WeatherModel.valueOf(model), amount.doubleOrNullValue())
            }
            val result = ForecastEngineV3.precipitation(
                rows,
                ForecastEngineV3.PrecipitationOptions(
                    engine = ForecastEngine.valueOf(row.getValue("engine")),
                    threshold = 0.1,
                    calibration = profiles(row.getValue("profiles")),
                    amountTight = 1.0,
                    amountWide = 8.0
                )
            )
            assertEquals("$id probability", row.getValue("probability").intOrNullValue(), result.probabilityPercent)
            assertClose(id, "conditional", row.getValue("conditional").doubleOrNullValue(), result.conditionalAmountMm)
            assertClose(id, "central", row.getValue("central").doubleOrNullValue(), result.centralAmountMm)
            assertClose(id, "expected", row.getValue("expected").doubleOrNullValue(), result.expectedAmountMm)
        }
    }

    @Test
    fun `reliability score matches the Python port`() {
        val cases = records("reliability.tsv")
        assertTrue("reliability corpus is empty", cases.size >= 120)
        cases.forEach { row ->
            val id = row.getValue("id")
            val samples = row.getValue("samples").split(';').map { item ->
                val (day, forecast, observation) = item.split(':')
                BiasSample(LocalDate.parse(day), forecast.toDouble(), observation.toDouble())
            }
            val result = ModelReliabilityCalculator.compute(
                variable = BiasVariable.valueOf(row.getValue("variable")),
                samples = samples,
                windowDays = 30
            )
            val expectedScore = row.getValue("score").intOrNullValue()
            if (expectedScore == null) {
                assertNull("$id: expected no reliability", result)
                return@forEach
            }
            requireNotNull(result) { "$id: missing reliability" }
            assertEquals("$id score", expectedScore, result.score)
            assertClose(id, "bias", row.getValue("bias").doubleOrNullValue(), result.meanBias)
            assertClose(id, "mae", row.getValue("mae").doubleOrNullValue(), result.meanAbsoluteError)
            assertClose(id, "std", row.getValue("std").doubleOrNullValue(), result.standardDeviation)
            assertEquals("$id n", row.getValue("n").intOrNullValue(), result.sampleSize)
            assertEquals("$id observed wet", row.getValue("observed_wet").intOrNullValue(),
                result.precipitation?.observedWetDays)
            assertEquals("$id forecast wet", row.getValue("forecast_wet").intOrNullValue(),
                result.precipitation?.forecastWetDays)
        }
    }
}
