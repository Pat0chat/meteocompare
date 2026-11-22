package com.meteocompare.app.widget

/**
 * All twelve hourly samples remain in the curve and heatmap. Only text labels
 * are thinned when a physical bitmap does not provide enough pixels for a
 * time, a temperature and a probability in every cell.
 */
internal fun widgetHeatmapLabelIndices(bitmapWidthPx: Int): List<Int> = when {
    bitmapWidthPx >= 840 -> (0..11).toList()
    bitmapWidthPx >= 360 -> (0..10 step 2).toList()
    bitmapWidthPx >= 220 -> (0..9 step 3).toList()
    else -> listOf(0, 6)
}

/** Text-size fit without exceeding the caller-provided maximum label width. */
internal fun widgetFittedTextSizePx(
    currentTextSizePx: Float,
    measuredTextWidthPx: Float,
    maxTextWidthPx: Float
): Float {
    if (currentTextSizePx <= 0f || maxTextWidthPx <= 0f) return 0f
    if (measuredTextWidthPx <= maxTextWidthPx || measuredTextWidthPx <= 0f) return currentTextSizePx
    return (currentTextSizePx * maxTextWidthPx / measuredTextWidthPx).coerceIn(0f, currentTextSizePx)
}

/** Keep labels on the bitmap even at index zero (the current hour). */
internal fun widgetLabelMaxWidthPx(
    bitmapWidthPx: Int,
    centerX: Float,
    effectiveSlotWidthPx: Float
): Float = minOf(
    effectiveSlotWidthPx * 0.90f,
    centerX.coerceAtLeast(0f) * 2f - 2f,
    (bitmapWidthPx - centerX).coerceAtLeast(0f) * 2f - 2f
).coerceAtLeast(0f)

internal enum class WidgetTwelveHourPresentation { NARROW_SUMMARY, COMPACT_SUMMARY, CHART }

/** Do not draw twelve tiny labels into a 1-column or low-height widget. */
internal fun widgetTwelveHourPresentation(
    availableWidthDp: Float,
    chartHeightDp: Float
): WidgetTwelveHourPresentation = when {
    availableWidthDp < 120f -> WidgetTwelveHourPresentation.NARROW_SUMMARY
    availableWidthDp < 215f || chartHeightDp < 72f -> WidgetTwelveHourPresentation.COMPACT_SUMMARY
    else -> WidgetTwelveHourPresentation.CHART
}
