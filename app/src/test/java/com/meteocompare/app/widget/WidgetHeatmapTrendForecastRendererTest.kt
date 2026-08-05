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
    fun `shared heatmap helpers keep 12 anchors on expanded profile`() {
        assertEquals(12, WidgetHeatmapForecastRenderer.anchorIndices(MiniForecastSizeProfile.EXPANDED_4X2).size)
    }
}
