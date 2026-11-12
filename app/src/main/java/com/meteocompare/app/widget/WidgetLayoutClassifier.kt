package com.meteocompare.app.widget

internal const val TINY_MAX_WIDTH_DP = 105
internal const val TINY_MAX_HEIGHT_DP = 105
internal const val SMALL_MAX_WIDTH_DP = 210
internal const val MEDIUM_MAX_WIDTH_DP = 320
internal const val WIDE_MIN_WIDTH_DP = 380
internal const val EXTRA_LARGE_MIN_HEIGHT_DP = 120
internal const val EXTRA_LARGE_MIN_WIDTH_DP = 220
internal const val COMPACT_TALL_MIN_WIDTH_DP = 100

/** Visual layout selected from the exact launcher-provided widget size. */
internal enum class WidgetLayoutKind {
    TINY,
    SMALL,
    MEDIUM,
    LARGE,
    WIDE,
    COMPACT_TALL,
    EXTRA_LARGE
}

/**
 * Classifies widget dimensions without Android or Compose dependencies so the
 * cross-launcher breakpoints can be covered by fast JVM tests.
 */
internal fun classifyWidgetLayout(widthDp: Float, heightDp: Float): WidgetLayoutKind = when {
    widthDp < TINY_MAX_WIDTH_DP && heightDp < TINY_MAX_HEIGHT_DP ->
        WidgetLayoutKind.TINY

    heightDp >= EXTRA_LARGE_MIN_HEIGHT_DP && widthDp >= EXTRA_LARGE_MIN_WIDTH_DP ->
        WidgetLayoutKind.EXTRA_LARGE

    heightDp >= EXTRA_LARGE_MIN_HEIGHT_DP && widthDp >= COMPACT_TALL_MIN_WIDTH_DP ->
        WidgetLayoutKind.COMPACT_TALL

    widthDp >= WIDE_MIN_WIDTH_DP -> WidgetLayoutKind.WIDE
    widthDp >= MEDIUM_MAX_WIDTH_DP -> WidgetLayoutKind.LARGE
    widthDp >= SMALL_MAX_WIDTH_DP -> WidgetLayoutKind.MEDIUM
    else -> WidgetLayoutKind.SMALL
}

/**
 * Number of forecast cards based on available width in expanded widgets.
 */
internal fun extendedForecastItemCount(widthDp: Float): Int =
    if (widthDp >= MEDIUM_MAX_WIDTH_DP) 5 else 4

/**
 * A pill keeps its full colored surface, but its usable rectangle is smaller.
 * Prefer a simpler weather summary over shrinking/cropping five columns or a
 * 12-hour chart into the round ends of a narrow widget.
 */
internal fun classifyWidgetLayoutForShape(
    widthDp: Float,
    heightDp: Float,
    style: WidgetCornerStyle,
    horizontalInsetDp: Float,
    verticalInsetDp: Float
): WidgetLayoutKind {
    val standard = classifyWidgetLayout(widthDp, heightDp)
    if (style != WidgetCornerStyle.PILL) return standard
    val usableWidth = (widthDp - horizontalInsetDp * 2f).coerceAtLeast(0f)
    val usableHeight = (heightDp - verticalInsetDp * 2f).coerceAtLeast(0f)
    return when {
        usableWidth < 118f || usableHeight < 45f -> WidgetLayoutKind.TINY
        heightDp >= EXTRA_LARGE_MIN_HEIGHT_DP && usableWidth < 240f -> WidgetLayoutKind.COMPACT_TALL
        heightDp < EXTRA_LARGE_MIN_HEIGHT_DP && usableWidth < 185f -> WidgetLayoutKind.SMALL
        else -> standard
    }
}

/** Only two-row widgets have a distinct current-weather header to remove.
 * Compact, single-row widgets keep their information instead of becoming empty. */
internal fun shouldDisplayCurrentConditions(
    kind: WidgetLayoutKind,
    showCurrentConditions: Boolean
): Boolean = showCurrentConditions ||
    (kind != WidgetLayoutKind.COMPACT_TALL && kind != WidgetLayoutKind.EXTRA_LARGE)
