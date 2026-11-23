package com.meteocompare.app.widget

import kotlin.math.sqrt

/**
 * The weather widget uses SizeMode.Exact. Unlike 44 Responsive variants, it
 * normally sends one portrait and one landscape layout per update. Leave
 * memory headroom for icons and RemoteViews metadata while keeping the 12h
 * chart sharp enough to read (especially on high-density launchers).
 */
internal const val MAX_RESPONSIVE_FORECAST_BITMAP_PIXELS = 90_000
internal const val MAX_RESPONSIVE_FORECAST_TOTAL_BITMAP_BYTES = 1_100_000
internal const val MAX_WIDGET_HOST_ORIENTATIONS = 3

internal fun widgetResponsiveForecastVariantPixelBudget(): Int = minOf(
    MAX_RESPONSIVE_FORECAST_BITMAP_PIXELS,
    MAX_RESPONSIVE_FORECAST_TOTAL_BITMAP_BYTES / 4 / MAX_WIDGET_HOST_ORIENTATIONS
)

internal fun widgetResponsiveForecastBitmapDensity(
    widthDp: Float,
    heightDp: Float,
    deviceDensity: Float,
    maxPixels: Int = widgetResponsiveForecastVariantPixelBudget()
): Float {
    val area = widthDp.coerceAtLeast(1f) * heightDp.coerceAtLeast(1f)
    val maxDensity = sqrt(maxPixels.coerceAtLeast(1).toFloat() / area)
    return minOf(deviceDensity.coerceAtLeast(0.5f), 2f, maxDensity)
        .coerceAtLeast(0.1f)
}
