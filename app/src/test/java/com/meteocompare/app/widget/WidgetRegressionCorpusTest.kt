package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.meteocompare.app.regression.CorpusExecutionProof

/** Immutable, deterministic reference cases; maintained by scripts/generate_weather_regression_corpus.py. */
class WidgetRegressionCorpusTest {
    private fun proof(file: String, count: Int, expected: Int, startedAt: Long) =
        CorpusExecutionProof.completed(file, count, expected, startedAt)

    private fun records(file: String): Sequence<Map<String, String>> = sequence {
        val input = requireNotNull(javaClass.getResourceAsStream("/weather_regression/v1/$file")) {
            "Missing regression fixture: $file"
        }
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val headers = requireNotNull(reader.readLine()).split('\t')
            for (line in reader.lineSequence()) {
                if (line.isBlank()) continue
                val values = line.split('\t')
                require(values.size == headers.size) { "$file invalid fixture: $line" }
                yield(headers.zip(values).toMap())
            }
        }
    }

    @Test fun launcherGeometryAndShapeTransitionsMatchGoldenContracts() {
        val startedAt = System.nanoTime()
        var cases = 0
        records("widget_layout.tsv").forEach { row ->
            cases++
            val width = row.getValue("width_dp").toFloat()
            val height = row.getValue("height_dp").toFloat()
            val expected = WidgetLayoutKind.valueOf(row.getValue("expected_layout"))
            val actual = classifyWidgetLayoutForShape(
                width, height, WidgetCornerStyle.valueOf(row.getValue("style")),
                row.getValue("horizontal_inset_dp").toFloat(),
                row.getValue("vertical_inset_dp").toFloat()
            )
            assertEquals(row.getValue("id"), expected, actual)
        }
        assertTrue("Widget layout cases unexpectedly missing", cases >= 12_000)
        proof("widget_layout.tsv", cases, 12870, startedAt)
    }

    @Test fun responsiveMetricRowsHaveExpectedDensityAndOrder() {
        val canonical = WidgetForecastMetric.entries.toList()
        val startedAt = System.nanoTime()
        var cases = 0
        records("widget_metrics.tsv").forEach { row ->
            cases++
            val id = row.getValue("id")
            val mask = row.getValue("bitmask").toInt()
            val profile = ForecastCardHeightProfile.valueOf(row.getValue("profile"))
            val height = row.getValue("height_dp").toFloat()
            val available = canonical.filterIndexed { index, _ -> (mask and (1 shl index)) != 0 }
            val layout = forecastMetricLayout(height, profile, available)
            val expected = row.getValue("visible").takeUnless { it == "_" }
                ?.split(',')?.map(WidgetForecastMetric::valueOf).orEmpty()
            assertEquals("$id visible metric rows", expected, layout.visible)
            assertEquals("$id typography", row.getValue("text_sp").toDouble(), layout.textSp.toDouble(), 1e-4)
            assertEquals("$id icons", row.getValue("icon_sp").toDouble(), layout.iconSp.toDouble(), 1e-4)
            assertEquals("$id fitting profile", row.getValue("fitted_profile"),
                forecastFittingCardProfile(height, profile, available.size).name)
            assertTrue("$id must preserve canonical metric order", layout.visible ==
                canonical.filter { it in layout.visible })
        }
        assertTrue("Missing responsive metric scenarios", cases >= 1_400)
        proof("widget_metrics.tsv", cases, 1408, startedAt)
    }

    @Test fun refreshTimingAndClockRollbackCannotFreezeTheWidget() {
        val startedAt = System.nanoTime()
        var cases = 0
        records("widget_refresh.tsv").forEach { row ->
            cases++
            val id = row.getValue("id")
            val last = row.getValue("last_ms").takeUnless { it == "_" }?.toLong()
            val now = row.getValue("now_ms").toLong()
            val force = row.getValue("force").toBooleanStrict()
            assertEquals("$id dispatch decision", row.getValue("expected_due").toBooleanStrict(),
                isWidgetDispatchDue(last ?: 0L, now, force))
            assertEquals("$id refresh tick", row.getValue("expected_tick").toLong(),
                nextWidgetRefreshTick(last, now))
        }
        assertTrue("Missing clock and refresh scenarios", cases >= 2_600)
        assertEquals(15 * 60 * 1000L, widgetDispatchIntervalMs())
        proof("widget_refresh.tsv", cases, 2664, startedAt)
    }

    @Test fun refreshReasonSelectionSurvivesMissingAndInvalidStoredValues() {
        WidgetRefreshReason.entries.forEach { reason ->
            assertEquals(reason, widgetRefreshReason(reason.name))
        }
        assertEquals(WidgetRefreshReason.CATCH_UP, widgetRefreshReason(null))
        assertEquals(WidgetRefreshReason.PERIODIC, widgetRefreshReason("INVALID", isPeriodic = true))
        assertEquals(WidgetRefreshReason.CATCH_UP, widgetRefreshReason("INVALID", isPeriodic = false))
    }
}
