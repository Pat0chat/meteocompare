package com.meteocompare.app.core.charts

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.domain.model.UnitSystem
import org.junit.Assert.*
import org.junit.Test

class CanonicalChartRangeTest {
    @Test fun `constant series gets a real minimum amplitude and ordered ticks`() {
        for (constant in listOf(-40.0, 0.0, 20.0, 100.0)) {
            val range = canonicalChartRange(listOf(constant, constant), minimumSpan = 2.0)
            assertEquals(2.0, range.span, 1e-10)
            assertTrue(range.min < constant && range.max > constant)
            assertEquals(range.min, range.ticks(5).first(), 0.0)
            assertEquals(range.max, range.ticks(5).last(), 0.0)
            assertTrue(range.ticks(5).zipWithNext().all { (a, b) -> a < b })
        }
    }

    @Test fun `invalid empty and extreme inputs cannot create invalid axes`() {
        val range = canonicalChartRange(listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE))
        assertEquals(CanonicalChartRange(0.0, 1.0), range)
        assertNull(metricPlotValue(Double.NEGATIVE_INFINITY))
        assertNull(metricPlotValue(-Double.MAX_VALUE))
        val extremes = canonicalChartRange(listOf(-1e12, 1e12))
        assertTrue(extremes.ticks(5).all { it.isFinite() && it.toFloat().isFinite() })
    }

    @Test fun `rain wind and marine ranges preserve canonical signs and small positive values`() {
        val rain = canonicalChartRange(listOf(-1.0, 0.0, 0.001), minimumSpan = 0.1, zeroFloor = true, includeZero = true)
        assertEquals(0.0, rain.min, 0.0)
        assertTrue(rain.max >= 0.1)
        val tide = canonicalChartRange(listOf(-1.2, 0.4), minimumPadding = 0.03)
        assertTrue(tide.min < -1.2 && tide.max > 0.4)
    }

    @Test fun `imperial labels cannot change canonical geometry or minimum amplitudes`() {
        val values = listOf(-2.0, -1.5, 0.4)
        val expected = canonicalChartRange(values, minimumSpan = 5.0)
        for (system in listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL, UnitSystem.METRIC)) {
            val units = WeatherUnits(system)
            expected.ticks(5).map { units.format(it, WeatherUnit.TEMPERATURE) }
            assertEquals(expected, canonicalChartRange(values, minimumSpan = 5.0))
            assertEquals(units.temperatureDelta(expected.span),
                units.temperature(expected.max) - units.temperature(expected.min), 1e-10)
        }
    }
}
