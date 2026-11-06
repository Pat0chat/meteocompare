package com.meteocompare.app.ui.enginecomparison

/**
 * A comparison chart is a horizontally scrollable timeline, not a miniature
 * rendering of the entire forecast horizon. Keep roughly three days in view
 * whatever the screen size (while preserving a useful minimum touch target).
 * Pure functions to make the geometry testable without Compose or Android.
 */
internal const val ENGINE_CHART_VISIBLE_DAYS = 3.25f

internal fun engineChartDayWidth(viewportWidthDp: Float): Float =
    if (viewportWidthDp.isFinite() && viewportWidthDp > 0f) {
        maxOf(84f, viewportWidthDp / ENGINE_CHART_VISIBLE_DAYS)
    } else {
        84f
    }

/** The tap coordinate is relative to the *scrolling content*, not the viewport. */
internal fun engineChartDayIndexAt(x: Float, dayWidth: Float, dayCount: Int): Int? {
    if (dayCount <= 0 || !x.isFinite() || !dayWidth.isFinite() || dayWidth <= 0f) return null
    return (x / dayWidth).toInt().coerceIn(0, dayCount - 1)
}
