package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetHeatmapForecastRendererTest {

    @Test
    fun `anchor indices stay readable on compact profiles`() {
        assertEquals(listOf(0, 3, 6, 9, 11), WidgetHeatmapForecastRenderer.anchorIndices(MiniForecastSizeProfile.COMPACT_2X2))
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 11), WidgetHeatmapForecastRenderer.anchorIndices(MiniForecastSizeProfile.MEDIUM_3X2))
        assertEquals((0..11).toList(), WidgetHeatmapForecastRenderer.anchorIndices(MiniForecastSizeProfile.EXPANDED_4X2))
    }

    @Test
    fun `temperature range gets padded to avoid flat chart`() {
        val padded = WidgetHeatmapForecastRenderer.paddedTemperatureRange(18.0, 18.0)
        assertEquals(16.0, padded.first, 0.001)
        assertEquals(20.0, padded.second, 0.001)
    }

    @Test
    fun `normalized temperature maps warm values above cold values`() {
        val coldY = WidgetHeatmapForecastRenderer.normalizedTemperatureY(10.0, 5.0, 25.0, 0f, 100f)
        val warmY = WidgetHeatmapForecastRenderer.normalizedTemperatureY(20.0, 5.0, 25.0, 0f, 100f)
        assertTrue("Une température plus chaude doit monter dans le graphique", warmY < coldY)
    }
    @Test
    fun `temperature curve reserves top space for condition icons`() {
        val y = WidgetHeatmapForecastRenderer.normalizedTemperatureY(
            temperature = 25.0,
            minTemp = 5.0,
            maxTemp = 25.0,
            top = 0f,
            bottom = 100f,
            usableTopRatio = WidgetHeatmapForecastRenderer.temperatureCurveTopRatio(MiniForecastSizeProfile.EXPANDED_4X2),
            usableBottomRatio = 0.30f
        )
        assertTrue("La courbe doit commencer sous la zone réservée aux icônes", y >= 30f)
    }

    @Test
    fun `temperature curve reserves lower space for temperature values`() {
        val bandHeight = 100f
        val baseline = 90f
        val ascent = -12f
        val pointRadius = 5.4f
        val topRatio = WidgetHeatmapForecastRenderer.temperatureCurveTopRatio(
            MiniForecastSizeProfile.EXPANDED_4X2
        )
        val bottomRatio = WidgetHeatmapForecastRenderer.temperatureCurveBottomRatio(
            bandHeightPx = bandHeight,
            labelBaselineRelativePx = baseline,
            labelAscentPx = ascent,
            maxPointRadiusPx = pointRadius,
            usableTopRatio = topRatio
        )
        val curveLowestY = bandHeight * (1f - bottomRatio)
        val labelTopY = baseline + ascent

        assertTrue(
            "La courbe et ses points doivent rester au-dessus du texte",
            curveLowestY + pointRadius < labelTopY
        )
    }

    @Test
    fun `temperature labels are pushed toward the lower edge`() {
        MiniForecastSizeProfile.entries.forEach { profile ->
            assertTrue(
                WidgetHeatmapForecastRenderer.temperatureLabelBottomInsetRatio(profile) <= 0.10f
            )
        }
    }

}
