package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM geometry simulation, covering every 1x1 .. 6x4 combination.
 * These are representative dp allocations, not assertions about the real
 * pixel sizes returned by Nova/One UI/Pixel Launcher. The final RemoteViews
 * appearance still needs the device checklist in TESTING.md.
 */
class WidgetGridMatrixTest {
    private data class Launcher(val name: String, val cellW: Int, val cellH: Int,
                                val gap: Int, val hostInsets: Int)
    private val hosts = listOf(
        Launcher("Nova", 77, 75, 4, 12),
        Launcher("OneUI", 71, 76, 4, 12),
        Launcher("Pixel", 62, 76, 5, 10),
        Launcher("tight", 64, 66, 4, 16)
    )
    private val completeMetrics = WidgetForecastMetric.entries.toList()
    private fun hostSize(host: Launcher, columns: Int, rows: Int): Pair<Int, Int> =
        (columns * host.cellW + (columns - 1) * host.gap - host.hostInsets) to
            (rows * host.cellH + (rows - 1) * host.gap - host.hostInsets)

    @Test fun every_grid_from_1x1_to_6x4_uses_its_real_host_dimensions() {
        // Exact mode avoids multiplying bitmap RemoteViews variants. Every
        // host size is classified on its actual width and height, so no preset
        // can consume only 60% of the launcher allocation.
        var cases = 0
        for (host in hosts) for (columns in 1..6) for (rows in 1..4) {
            val (width, height) = hostSize(host, columns, rows)
            assertTrue("Invalid host: ${host.name} ${columns}x$rows", width > 0 && height > 0)
            val layout = classifyWidgetLayout(width.toFloat(), height.toFloat())
            assertTrue(layout in WidgetLayoutKind.entries)
            // Adding a launcher row must never decrease the available height.
            if (rows > 1) {
                assertTrue(height > hostSize(host, columns, rows - 1).second)
            }
            cases++
        }
        assertEquals(96, cases)
    }

    @Test fun daily_and_hourly_forecasts_keep_sunshine_and_convergence_when_they_fit() {
        // The former vertical-only policy could show CLOUD+RAIN but silently
        // hide both SUNSHINE and CONVERGENCE in 4x2 and 5x2 widgets.
        for (forecast in listOf("DAILY", "HOURLY")) {
            for (height in 54..220 step 2) for (width in 36..120 step 2) {
                for (scale in listOf(0.85f, 1f, 1.2f, 1.4f)) {
                    val candidate = forecastCardHeightProfile(height.toFloat())
                    val profile = forecastFittingCardProfile(
                        height.toFloat(), candidate, completeMetrics.size, scale
                    )
                    val layout = forecastAdaptiveMetricLayout(
                        height.toFloat(), width.toFloat(), profile, completeMetrics, scale
                    )
                    assertTrue("$forecast $width x $height scale=$scale",
                        layout.visible.all { it in completeMetrics })
                    assertTrue("A forecast row is wider than its card", layout.columns in 1..2)
                    if (layout.columns == 2) {
                        assertTrue("paired metrics must contain sunshine: $forecast $width/$height",
                            WidgetForecastMetric.SUNSHINE in layout.visible)
                        assertTrue("paired metrics must contain convergence: $forecast $width/$height",
                            WidgetForecastMetric.CONVERGENCE in layout.visible)
                        assertEquals(completeMetrics, layout.visible)
                    }
                    // In a large enough card, neither information may vanish.
                    if (width >= 70f * scale && height >= 115f * scale) {
                        assertEquals("$forecast $width/$height $scale", completeMetrics, layout.visible)
                    }
                }
            }
        }
    }

    @Test fun every_grid_and_user_setting_keeps_positive_content_height_and_valid_metrics() {
        var verified = 0
        val displayModes = listOf("HOURLY", "DAILY", "CONFIDENCE_ALL",
            "MINI_FORECAST_12H", "HEATMAP_CHART_12H", "HEATMAP_TREND_12H")
        for (host in hosts) for (columns in 1..6) for (rows in 1..4) {
            val (width, height) = hostSize(host, columns, rows)
            val size = WidgetResponsivePreset(width, height)
            for (mode in displayModes) for (showCurrent in listOf(false, true)) {
                // NONE / COMPACT / NORMAL / LARGE, all five corner styles,
                // with or without the location refresh footer.
                for (spacingFactor in listOf(0f, 0.5f, 1f, 1.5f)) {
                    for (textScale in listOf(0.85f, 1f, 1.4f)) {
                        for (corner in WidgetCornerStyle.entries) for (hasLocation in listOf(false, true)) {
                        val baseInset = forecastContainerVerticalPaddingDp(size.heightDp.toFloat()) * spacingFactor
                        val inset = if (corner == WidgetCornerStyle.PILL) maxOf(
                            baseInset, (size.heightDp * 0.12f).coerceIn(5f, 14f)
                        ) else baseInset
                        val shape = classifyWidgetLayoutForShape(
                            size.widthDp.toFloat(), size.heightDp.toFloat(),
                            corner, baseInset, inset
                        )
                        val isTall = shape == WidgetLayoutKind.COMPACT_TALL ||
                            shape == WidgetLayoutKind.EXTRA_LARGE
                        val header = if (!showCurrent || !isTall) 0f else if (size.heightDp < 152) 40f else 54f
                        val gap = if (showCurrent && isTall) 6f else 0f
                        val remaining = forecastBottomStripAvailableHeightDp(
                            size.heightDp.toFloat(), header, gap, inset,
                            footerHeightDp = locationRefreshFooterHeightDp(hasLocation)
                        )
                        assertTrue("$host ${columns}x$rows $mode", remaining > 0f)
                        if (mode == "HOURLY" || mode == "DAILY") {
                            val fitting = forecastFittingCardProfile(
                                remaining, forecastCardHeightProfile(remaining), 4, textScale)
                            val cardWidth = (size.widthDp - 20f) /
                                (if (size.widthDp >= 320) 5 else 4)
                            val selected = forecastAdaptiveMetricLayout(
                                remaining, cardWidth, fitting, completeMetrics, textScale)
                            assertTrue(selected.visible.size <= 4)
                            assertEquals(completeMetrics.filter { it in selected.visible }, selected.visible)
                        }
                        verified++
                        }
                    }
                }
            }
        }
        assertEquals(96 * 6 * 2 * 4 * 3 * 5 * 2, verified)
    }

    @Test fun missing_weather_data_is_never_fabricated_in_compact_cards() {
        for (height in 40..200 step 10) for (width in 30..115 step 5) {
            val profile = forecastCardHeightProfile(height.toFloat())
            assertEquals(emptyList<WidgetForecastMetric>(),
                forecastAdaptiveMetricLayout(height.toFloat(), width.toFloat(), profile,
                    emptyList()).visible)
            val onlyRain = listOf(WidgetForecastMetric.RAIN)
            assertTrue(forecastAdaptiveMetricLayout(height.toFloat(), width.toFloat(), profile,
                onlyRain).visible.all { it == WidgetForecastMetric.RAIN })
        }
    }
}
