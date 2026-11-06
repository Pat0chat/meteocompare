package com.meteocompare.app.ui.enginecomparison

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineChartGeometryTest {
    @Test fun tenDaysAreNeverCompressedIntoOneViewport() {
        listOf(280f, 360f, 600f, 1024f).forEach { width ->
            val dayWidth = engineChartDayWidth(width)
            assertTrue("Timeline must scroll on $width dp viewport", dayWidth * 10 > width)
            assertTrue("Should display about three days", width / dayWidth <= ENGINE_CHART_VISIBLE_DAYS + 0.01f)
        }
    }

    @Test fun smallScreensKeepReadableDayColumns() {
        assertEquals(84f, engineChartDayWidth(180f), 0.001f)
        assertEquals(84f, engineChartDayWidth(0f), 0.001f)
        assertEquals(84f, engineChartDayWidth(Float.NaN), 0.001f)
    }

    @Test fun tappingChartResolvesDayRelativeToScrolledContent() {
        assertEquals(0, engineChartDayIndexAt(0f, 90f, 10))
        assertEquals(1, engineChartDayIndexAt(130f, 90f, 10))
        assertEquals(9, engineChartDayIndexAt(899f, 90f, 10))
        assertEquals(9, engineChartDayIndexAt(99999f, 90f, 10))
    }

    @Test fun invalidGeometryDoesNotPickAnUnrelatedDay() {
        assertNull(engineChartDayIndexAt(12f, 90f, 0))
        assertNull(engineChartDayIndexAt(Float.NaN, 90f, 10))
        assertNull(engineChartDayIndexAt(12f, 0f, 10))
        assertNull(engineChartDayIndexAt(12f, Float.POSITIVE_INFINITY, 10))
    }
}
