package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetChartLabelLayoutTest {
    @Test fun `all 12 hours are plotted but printed labels are spaced on narrow bitmaps`() {
        assertEquals(listOf(0, 6), widgetHeatmapLabelIndices(190))
        assertEquals(listOf(0, 3, 6, 9), widgetHeatmapLabelIndices(270))
        assertEquals(listOf(0, 2, 4, 6, 8, 10), widgetHeatmapLabelIndices(560))
        assertEquals((0..11).toList(), widgetHeatmapLabelIndices(900))
        for (width in 80..1100 step 17) {
            val indices = widgetHeatmapLabelIndices(width)
            assertTrue(indices.all { it in 0..11 })
            assertEquals(indices.distinct(), indices)
            assertEquals(indices.sorted(), indices)
            assertEquals(0, indices.first())
        }
    }

    @Test fun `label font scales down to fit and never grows beyond natural size`() {
        for (natural in listOf(8f, 12f, 18f, 25f)) {
            val fitted = widgetFittedTextSizePx(natural, measuredTextWidthPx = 80f, maxTextWidthPx = 30f)
            assertTrue(fitted < natural)
            assertTrue(fitted >= 0f)
            assertEquals(natural, widgetFittedTextSizePx(natural, 20f, 30f), 0f)
        }
    }

    @Test fun `mini and heatmap have readable fallback for tiny widgets`() {
        assertEquals(WidgetTwelveHourPresentation.NARROW_SUMMARY,
            widgetTwelveHourPresentation(90f, 100f))
        assertEquals(WidgetTwelveHourPresentation.COMPACT_SUMMARY,
            widgetTwelveHourPresentation(175f, 120f))
        assertEquals(WidgetTwelveHourPresentation.COMPACT_SUMMARY,
            widgetTwelveHourPresentation(350f, 45f))
        assertEquals(WidgetTwelveHourPresentation.CHART,
            widgetTwelveHourPresentation(350f, 100f))
        for (width in 40..450 step 5) for (height in 20..300 step 7) {
            val mode = widgetTwelveHourPresentation(width.toFloat(), height.toFloat())
            if (mode == WidgetTwelveHourPresentation.CHART) {
                assertTrue("Bitmap requested where labels cannot fit: $width/$height",
                    width >= 215 && height >= 72)
            }
        }
    }

    @Test fun `first and last labels stay within bitmap bounds`() {
        assertTrue(widgetLabelMaxWidthPx(400, 20f, 100f) <= 38f)
        assertTrue(widgetLabelMaxWidthPx(400, 380f, 100f) <= 38f)
        assertTrue(widgetLabelMaxWidthPx(400, 200f, 100f) >= 85f)
    }
}
