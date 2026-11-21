package com.meteocompare.app.ui.citydetail

/**
 * Decide whether two complete labels fit on one line. All dimensions are pixels
 * measured using the current locale, text style, and font scale.
 * Long arithmetic also covers unrealistic accessibility/font input safely.
 */
internal fun dailyRangeFitsInline(
    valueWidthPx: Int,
    rangeWidthPx: Int,
    availableWidthPx: Int,
    gapPx: Int
): Boolean = valueWidthPx >= 0 && rangeWidthPx >= 0 && gapPx >= 0 &&
    availableWidthPx >= 0 &&
    valueWidthPx.toLong() + rangeWidthPx.toLong() + gapPx.toLong() <= availableWidthPx.toLong()
