package com.meteocompare.app.core.units

import com.meteocompare.app.domain.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden reference vectors are generated independently of WeatherUnits.
 * Do not derive expected values by calling production conversion functions.
 * The CI report counts these as verified only after the exact number of
 * assertions has passed in both Debug and Release JUnit executions.
 *
 * Generation: scripts/generate_unit_conversion_corpus.py
 * Contract: docs/unit-conversion-corpus.md
 */
class UnitConversionCorpusTest {
    private val root = "/unit_conversion/v1"
    private val expectedCounts = mapOf(
        "profiles.tsv" to 131,
        "conversions.tsv" to 198_072,
        "display.tsv" to 452_736,
        "missing.tsv" to 5_240
    )

    private fun records(name: String): Sequence<Map<String, String>> = sequence {
        val input = requireNotNull(javaClass.getResourceAsStream("$root/$name")) {
            "Missing golden unit corpus $root/$name"
        }
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val columns = requireNotNull(reader.readLine()).split('\t')
            require(columns.first() == "id") { "No scenario ID in $name" }
            var lineNumber = 1
            while (true) {
                val line = reader.readLine() ?: break
                lineNumber++
                if (line.isBlank()) continue
                val values = line.split('\t')
                require(values.size == columns.size) {
                    "$name:$lineNumber expected ${columns.size} columns, got ${values.size}"
                }
                yield(columns.zip(values).toMap())
            }
        }
    }

    private fun profiles(): Map<String, WeatherUnits> = records("profiles.tsv").associate { row ->
        row.getValue("id") to WeatherUnits(
            system = UnitSystem.valueOf(row.getValue("system")),
            custom = CustomUnitPreferences(
                temperature = TemperatureChoice.valueOf(row.getValue("temperature")),
                wind = WindChoice.valueOf(row.getValue("wind")),
                precipitation = PrecipitationChoice.valueOf(row.getValue("precipitation")),
                pressure = PressureChoice.valueOf(row.getValue("pressure")),
                height = HeightChoice.valueOf(row.getValue("height")),
                distance = DistanceChoice.valueOf(row.getValue("distance"))
            )
        )
    }

    private fun proof(name: String, count: Int, start: Long) {
        assertEquals("Corpus $name was not fully executed", expectedCounts.getValue(name), count)
        val durationMs = (System.nanoTime() - start).coerceAtLeast(0L) / 1_000_000
        println("METEOCOMPARE_UNIT_CORPUS_V1 file=$name cases=$count elapsed_ms=$durationMs")
    }

    @Test fun customProfilesCoverEveryCombinationAndLegacyPresets() {
        val start = System.nanoTime()
        val all = profiles()
        assertEquals(131, all.size)
        assertEquals(128, all.values.count { it.system == UnitSystem.CUSTOM })
        assertEquals(3, all.values.count { it.system != UnitSystem.CUSTOM })
        assertEquals(128, all.values.filter { it.system == UnitSystem.CUSTOM }.map { it.custom }.toSet().size)
        assertEquals(setOf(UnitSystem.METRIC, UnitSystem.IMPERIAL, UnitSystem.UK_MIXED),
            all.values.filter { it.system != UnitSystem.CUSTOM }.map { it.system }.toSet())
        proof("profiles.tsv", all.size, start)
    }

    @Test fun allNumericConversionsMatchIndependentRationalReference() {
        val start = System.nanoTime()
        val unitsById = profiles()
        var count = 0
        records("conversions.tsv").forEach { row ->
            count++
            val id = row.getValue("id")
            val units = unitsById.getValue(row.getValue("profile"))
            val kind = WeatherUnit.valueOf(row.getValue("unit"))
            val metric = row.getValue("metric").toDouble()
            val expected = row.getValue("expected").toDouble()
            val delta = row.getValue("delta").toBooleanStrict()
            val actual = units.convert(metric, kind, delta)
            // IEEE-754 roundoff only: strict relative 1e-12, 16 ULPs.
            val tolerance = max(1e-12, max(abs(expected) * 1e-12, Math.ulp(expected) * 16))
            assertTrue("$id conversion is non-finite: $actual", actual.isFinite())
            assertEquals("$id converted numeric value", expected, actual, tolerance)
            assertEquals("$id symbol", row.getValue("symbol"), units.label(kind))
        }
        proof("conversions.tsv", count, start)
    }

    @Test fun allFormatsMatchGoldenLocalePrecisionAndUnitSuffix() {
        val start = System.nanoTime()
        val unitsById = profiles()
        var count = 0
        records("display.tsv").forEach { row ->
            count++
            val id = row.getValue("id")
            val units = unitsById.getValue(row.getValue("profile"))
            val kind = WeatherUnit.valueOf(row.getValue("unit"))
            val locale = Locale.forLanguageTag(row.getValue("locale").replace('_', '-'))
            val metric = row.getValue("metric").toDouble()
            val digits = row.getValue("digits").toInt()
            val delta = row.getValue("delta").toBooleanStrict()
            assertEquals("$id numeric text", row.getValue("expected_value"),
                units.value(metric, kind, digits, locale, delta))
            assertEquals("$id value and unit", row.getValue("expected_format"),
                units.format(metric, kind, digits, locale, delta))
        }
        proof("display.tsv", count, start)
    }

    @Test fun missingAndNonFiniteValuesAlwaysStayUnavailable() {
        val start = System.nanoTime()
        val unitsById = profiles()
        var count = 0
        records("missing.tsv").forEach { row ->
            count++
            val id = row.getValue("id")
            val units = unitsById.getValue(row.getValue("profile"))
            val kind = WeatherUnit.valueOf(row.getValue("unit"))
            val value: Double? = when (row.getValue("metric")) {
                "null" -> null
                "nan" -> Double.NaN
                "+infinity" -> Double.POSITIVE_INFINITY
                "-infinity" -> Double.NEGATIVE_INFINITY
                else -> error("Unexpected sentinel for $id")
            }
            for (locale in arrayOf(Locale.US, Locale.FRANCE, Locale.GERMANY, Locale.forLanguageTag("tr-TR"))) {
                assertEquals("$id value $locale", "—", units.value(value, kind, locale = locale))
                assertEquals("$id full $locale", "—", units.format(value, kind, locale = locale))
            }
        }
        proof("missing.tsv", count, start)
    }

    @Test fun conversionsAreDisplayOnlyAndPreserveOrderingDeltasAndAxisTicks() {
        // Additional metamorphic checks beyond the independently generated vectors.
        for ((id, units) in profiles()) {
            for (kind in WeatherUnit.entries) {
                val low = -3.25
                val middle = 2.5
                val high = 19.75
                val a = units.convert(low, kind)
                val b = units.convert(middle, kind)
                val c = units.convert(high, kind)
                assertTrue("$id/$kind preserves order", a < b && b < c)
                assertEquals("$id/$kind offset must cancel for ranges",
                    units.convert(high - low, kind, delta = true), c - a, 1e-10)
                val distinct = listOf(low, middle, high).map { it }
                assertEquals("$id/$kind canonical inputs are never rewritten", listOf(low, middle, high), distinct)
                val labels = listOf(-3.0, 0.0, 3.0)
                    .map { units.axisValue(it, kind, 3.0, locale = Locale.US) }
                assertEquals("$id/$kind axis labels must be distinct", 3, labels.toSet().size)
            }
        }
        assertEquals("32°F", WeatherUnits(UnitSystem.IMPERIAL).temp(0.0, locale = Locale.US))
        assertEquals("10 kn", WeatherUnits(UnitSystem.CUSTOM,
            CustomUnitPreferences(wind = WindChoice.KNOTS)).speed(18.52, Locale.US))
    }
}
