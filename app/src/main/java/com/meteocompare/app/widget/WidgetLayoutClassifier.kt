package com.meteocompare.app.widget

internal const val TINY_MAX_WIDTH_DP = 105
internal const val TINY_MAX_HEIGHT_DP = 105
internal const val SMALL_MAX_WIDTH_DP = 210
internal const val MEDIUM_MAX_WIDTH_DP = 320
internal const val WIDE_MIN_WIDTH_DP = 380
internal const val EXTRA_LARGE_MIN_HEIGHT_DP = 130
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
