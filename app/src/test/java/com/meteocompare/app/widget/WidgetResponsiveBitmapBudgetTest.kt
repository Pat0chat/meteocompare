package com.meteocompare.app.widget

import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetResponsiveBitmapBudgetTest {
    @Test fun `three orientation layouts remain under bitmap budget`() {
        val bounds = listOf(130f to 80f, 300f to 120f, 430f to 250f)
        val perVariant = widgetResponsiveForecastVariantPixelBudget()
        var sumPixels = 0
        for ((width, height) in bounds) {
            val density = widgetResponsiveForecastBitmapDensity(width, height, 3.5f, perVariant)
            val w = (width * density).toInt().coerceAtLeast(1)
            val h = (height * density).toInt().coerceAtLeast(1)
            assertTrue("Oversized widget bitmap: ${width}x$height", w * h <= perVariant)
            sumPixels += w * h
        }
        assertTrue(sumPixels * 4 <= MAX_RESPONSIVE_FORECAST_TOTAL_BITMAP_BYTES)
    }

    @Test fun `a single chart is no longer downsampled across 44 unrelated layouts`() {
        val normal = widgetResponsiveForecastBitmapDensity(350f, 110f, 3f)
        assertTrue("Bitmap was made illegible by variant budget: density=$normal", normal >= 1.4f)
        assertTrue(normal <= 2f)
        assertTrue(widgetResponsiveForecastBitmapDensity(100f, 55f, 3f) >= 1.9f)
    }
}
