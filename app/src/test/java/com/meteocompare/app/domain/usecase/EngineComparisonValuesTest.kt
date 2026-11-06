package com.meteocompare.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Each chart selector must read its own metric, never a visually similar series. */
class EngineComparisonValuesTest {
    private val sample = EngineComparisonValues(
        tempMax = 26.0,
        tempMin = 7.0,
        precipitationAmountMm = 19.0,
        precipitationProbabilityPercent = 30,
        precipitationExpectedMm = 3.5,
        windKmh = 14.0,
        gustKmh = 38.0,
        cloudPercent = 81.0,
        condition = null
    )

    @Test fun everyChartVariableUsesTheExpectedSeries() {
        val expected = mapOf(
            EngineComparisonMetric.TEMP_MAX to 26.0,
            EngineComparisonMetric.TEMP_MIN to 7.0,
            EngineComparisonMetric.PRECIPITATION to 3.5,
            EngineComparisonMetric.WIND to 14.0,
            EngineComparisonMetric.GUST to 38.0,
            EngineComparisonMetric.CLOUD to 81.0
        )
        assertEquals(EngineComparisonMetric.entries.toSet(), expected.keys)
        expected.forEach { (metric, value) -> assertEquals(value, sample.value(metric) ?: Double.NaN, 0.0) }
    }

    @Test fun absentValuesRemainAbsentForEachMetric() {
        val missing = sample.copy(
            tempMax = null, tempMin = null, precipitationExpectedMm = null,
            windKmh = null, gustKmh = null, cloudPercent = null
        )
        EngineComparisonMetric.entries.forEach { assertNull(missing.value(it)) }
    }
}
