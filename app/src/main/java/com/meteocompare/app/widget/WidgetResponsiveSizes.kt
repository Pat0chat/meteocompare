package com.meteocompare.app.widget

/**
 * Breakpoints retained only for the small editorial/insight widget, which has
 * no bitmap charts. The image-heavy weather widget uses SizeMode.Exact to
 * avoid sending dozens of parallel RemoteViews and to fill the host height.
 */
internal data class WidgetResponsivePreset(val widthDp: Int, val heightDp: Int) {
    init {
        require(widthDp > 0 && heightDp > 0)
    }
}

// The image-heavy weather widget deliberately does not use Responsive presets.
// SizeMode.Exact uses LocalSize and a bounded family of layout breakpoints.
// Only the lightweight editorial widget uses this small responsive catalog.

internal val insightWidgetResponsivePresets = listOf(
    WidgetResponsivePreset(100, 75),
    WidgetResponsivePreset(180, 100),
    WidgetResponsivePreset(275, 110),
    WidgetResponsivePreset(360, 115),
    WidgetResponsivePreset(275, 145),
    WidgetResponsivePreset(380, 170)
)

internal fun widgetSizeModePresetsAreValid(presets: List<WidgetResponsivePreset>): Boolean =
    presets.isNotEmpty() && presets.size <= 48 && presets.distinct().size == presets.size &&
        presets.all { it.widthDp > 0 && it.heightDp > 0 }
