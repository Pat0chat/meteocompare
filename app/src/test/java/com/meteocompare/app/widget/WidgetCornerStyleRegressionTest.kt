package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCornerStyleRegressionTest {
    @Test fun existing_widgets_remain_rounded_by_default() {
        assertEquals(WidgetCornerStyle.ROUNDED, WidgetCornerStyle.fromStored(null))
        assertEquals(WidgetCornerStyle.ROUNDED, WidgetCornerStyle.fromStored("unknown"))
    }

    @Test fun square_selection_survives_deserialization() {
        assertEquals(WidgetCornerStyle.SQUARE, WidgetCornerStyle.fromStored("SQUARE"))
    }

    @Test fun short_widget_keeps_room_for_content_and_avoids_outer_launcher_corners() {
        assertEquals(16 to 6, squarePanelInsetsDp(140f, 72f))
        assertEquals(20 to 6, squarePanelInsetsDp(230f, 120f))
    }

    @Test fun large_square_widget_draws_a_distinct_inset_rectangular_panel() {
        assertEquals(22 to 6, squarePanelInsetsDp(330f, 170f))
    }

    @Test fun square_forecast_preserves_height_for_the_bottom_strip() {
        val panel = widgetPanelGeometryDp(330f, 170f, WidgetCornerStyle.SQUARE)
        assertEquals(286f, panel.widthDp, 0.001f)
        assertEquals(158f, panel.heightDp, 0.001f)
        assertEquals(WidgetLayoutKind.EXTRA_LARGE,
            classifyWidgetLayout(panel.widthDp, panel.heightDp))
        val chartHeight = miniForecastChartHeightDp(
            widgetHeightDp = panel.heightDp,
            headerHeightDp = miniForecastHeaderHeightBudgetDp(compact = true, showExtras = false),
            sectionGapDp = 7f,
            profile = miniForecastProfileForWidth(panel.widthDp)
        )
        assertTrue("bottom heatmap needs real display space", chartHeight >= 55)
    }

    @Test fun compact_tall_square_keeps_hourly_daily_section() {
        val panel = widgetPanelGeometryDp(170f, 150f, WidgetCornerStyle.SQUARE)
        assertEquals(138f, panel.heightDp, 0.001f)
        assertEquals(WidgetLayoutKind.COMPACT_TALL,
            classifyWidgetLayout(panel.widthDp, panel.heightDp))
    }

    @Test fun rounded_widgets_keep_their_original_dimensions() {
        val panel = widgetPanelGeometryDp(330f, 170f, WidgetCornerStyle.ROUNDED)
        assertEquals(0, panel.insetX)
        assertEquals(0, panel.insetY)
        assertEquals(330f, panel.widthDp, 0.001f)
        assertEquals(170f, panel.heightDp, 0.001f)
    }

    @Test fun tiny_square_keeps_minimum_panel_size() {
        val panel = widgetPanelGeometryDp(48f, 48f, WidgetCornerStyle.SQUARE)
        assertTrue(panel.widthDp >= 40f)
        assertTrue(panel.heightDp >= 32f)
    }
}
