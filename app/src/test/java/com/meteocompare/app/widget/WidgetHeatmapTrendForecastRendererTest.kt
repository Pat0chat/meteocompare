package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHeatmapTrendForecastRendererTest {

    @Test
    fun `modern heatmap mode is detected`() {
        assertTrue(ForecastMode.HEATMAP_TREND_12H.isModernHeatmapChartForecast())
        assertFalse(ForecastMode.HEATMAP_CHART_12H.isModernHeatmapChartForecast())
    }

    @Test
    fun `trend heatmap shares width-based text thinning with heatmap`() {
        assertEquals(listOf(0, 6), widgetHeatmapLabelIndices(190))
        assertEquals((0..11).toList(), widgetHeatmapLabelIndices(900))
    }
}
