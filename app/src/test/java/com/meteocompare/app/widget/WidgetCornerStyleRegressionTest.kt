package com.meteocompare.app.widget

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.sqrt
import org.junit.Test

class WidgetCornerStyleRegressionTest {
    @Test fun saved_legacy_styles_remain_readable() {
        assertEquals(WidgetCornerStyle.ROUNDED, WidgetCornerStyle.fromStored(null))
        assertEquals(WidgetCornerStyle.ROUNDED, WidgetCornerStyle.fromStored("invalid"))
        assertEquals(WidgetCornerStyle.SQUARE, WidgetCornerStyle.fromStored("SQUARE"))
        assertEquals(WidgetCornerStyle.CUSTOM, WidgetCornerStyle.fromStored("CUSTOM"))
        assertEquals(WidgetContentSpacing.NORMAL, WidgetContentSpacing.fromStored(null))
        assertEquals(WidgetContentSpacing.NONE, WidgetContentSpacing.fromStored("NONE"))
        assertEquals(WidgetContentSpacing.LARGE, WidgetContentSpacing.fromStored("LARGE"))
    }

    @Test fun all_shapes_are_full_bleed_and_use_a_bounded_corner_radius() {
        assertEquals(0f, widgetCornerRadiusDp(WidgetCornerStyle.SQUARE, 24, 300f, 72f), 0.001f)
        assertEquals(12f, widgetCornerRadiusDp(WidgetCornerStyle.SOFT, 24, 300f, 72f), 0.001f)
        assertEquals(36f, widgetCornerRadiusDp(WidgetCornerStyle.PILL, 24, 300f, 72f), 0.001f)
        assertEquals(28f, widgetCornerRadiusDp(WidgetCornerStyle.ROUNDED, 24, 300f, 72f), 0.001f)
        assertEquals(18f, widgetCornerRadiusDp(WidgetCornerStyle.CUSTOM, 18, 300f, 72f), 0.001f)
    }

    @Test fun pill_and_custom_radii_respect_tiny_widget_sizes() {
        assertEquals(24f, widgetCornerRadiusDp(WidgetCornerStyle.PILL, 200, 48f, 80f), 0.001f)
        assertEquals(24f, widgetCornerRadiusDp(WidgetCornerStyle.CUSTOM, 99, 48f, 80f), 0.001f)
        assertEquals(0f, widgetCornerRadiusDp(WidgetCornerStyle.CUSTOM, -3, 48f, 80f), 0.001f)
        assertEquals(0f, widgetCornerRadiusDp(WidgetCornerStyle.PILL, 24, 0f, 80f), 0.001f)
    }

    @Test fun spacing_changes_only_internal_padding_never_outer_margin() {
        assertEquals(24.dp to 18.dp, widgetContentPadding(16.dp, 12.dp, WidgetContentSpacing.LARGE))
        assertEquals(16.dp to 12.dp, widgetContentPadding(16.dp, 12.dp, WidgetContentSpacing.NORMAL))
        assertEquals(8.dp to 6.dp, widgetContentPadding(16.dp, 12.dp, WidgetContentSpacing.COMPACT))
        assertEquals(0.dp to 0.dp, widgetContentPadding(16.dp, 12.dp, WidgetContentSpacing.NONE))
    }

    @Test fun pill_inscribes_text_inside_the_capsule_without_outer_margins() {
        for ((width, height) in listOf(
            320f to 72f, 170f to 150f, 320f to 170f, 105f to 72f, 48f to 80f
        )) {
            val (x, y) = widgetShapeSafeContentPaddingDp(
                WidgetCornerStyle.PILL, width, height, 0f, 0f
            )
            val r = minOf(width, height) / 2f
            val requiredX = r - sqrt(r * r - (r - y).coerceAtLeast(0f).let { it * it })
            assertTrue("Inside capsule ($width × $height)", x + 0.001f >= requiredX)
            assertTrue("Keep a center area", x * 2f <= width)
            assertTrue("Keep a center area", y * 2f < height)
        }
    }

    @Test fun pill_safety_applies_even_when_spacing_is_none() {
        val pill = widgetShapeSafeContentPaddingDp(
            WidgetCornerStyle.PILL, 260f, 80f, 0f, 0f
        )
        assertTrue(pill.first > 8f)
        assertTrue(pill.second >= 5f)
        assertEquals(
            0f to 0f,
            widgetShapeSafeContentPaddingDp(WidgetCornerStyle.SQUARE, 260f, 80f, 0f, 0f)
        )
        assertEquals(
            10f to 8f,
            widgetShapeSafeContentPaddingDp(WidgetCornerStyle.SOFT, 260f, 80f, 10f, 8f)
        )
    }

    @Test fun without_insets_square_layout_preserves_full_size_for_forecast() {
        assertEquals(WidgetLayoutKind.EXTRA_LARGE, classifyWidgetLayout(330f, 170f))
        assertEquals(WidgetLayoutKind.COMPACT_TALL, classifyWidgetLayout(170f, 150f))
        val chartHeight = miniForecastChartHeightDp(
            widgetHeightDp = 170f,
            headerHeightDp = miniForecastHeaderHeightBudgetDp(compact = true, showExtras = false),
            sectionGapDp = 7f,
            profile = miniForecastProfileForWidth(330f)
        )
        org.junit.Assert.assertTrue(chartHeight >= 55)
    }
}
