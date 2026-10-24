package com.meteocompare.app.ui.citydetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispersionBoundsLayoutTest {
    @Test fun close_values_keep_at_least_eight_pixels_between_labels() {
        val placement = dispersionBoundsPlacement(
            width = 280,
            minLabelWidth = 52,
            maxLabelWidth = 54,
            minCenter = 139f,
            maxCenter = 141f,
            gap = 8
        )
        assertFalse(placement.maxOnSecondLine)
        assertTrue(placement.minX >= 0)
        assertTrue(placement.maxX + 54 <= 280)
        assertTrue(placement.maxX >= placement.minX + 52 + 8)
    }

    @Test fun widely_separated_values_stay_aligned_to_their_points() {
        val placement = dispersionBoundsPlacement(
            width = 280,
            minLabelWidth = 48,
            maxLabelWidth = 48,
            minCenter = 45f,
            maxCenter = 234f,
            gap = 8
        )
        assertEquals(21, placement.minX)
        assertEquals(210, placement.maxX)
        assertFalse(placement.maxOnSecondLine)
    }

    @Test fun close_values_at_right_edge_remain_inside_screen() {
        val placement = dispersionBoundsPlacement(
            width = 200,
            minLabelWidth = 68,
            maxLabelWidth = 70,
            minCenter = 190f,
            maxCenter = 194f,
            gap = 8
        )
        assertFalse(placement.maxOnSecondLine)
        assertTrue(placement.maxX >= placement.minX + 68 + 8)
        assertTrue(placement.maxX + 70 <= 200)
    }

    @Test fun felt_value_stays_centered_directly_below_its_point() {
        assertEquals(110, centeredDispersionLabelStart(width = 300, labelWidth = 80, center = 150f))
    }

    @Test fun felt_value_at_both_edges_remains_inside_graph() {
        assertEquals(0, centeredDispersionLabelStart(width = 220, labelWidth = 96, center = 4f))
        assertEquals(124, centeredDispersionLabelStart(width = 220, labelWidth = 96, center = 219f))
    }

    @Test fun large_type_on_narrow_screen_never_produces_negative_positions() {
        assertEquals(0, centeredDispersionLabelStart(width = 48, labelWidth = 80, center = 30f))
    }

    @Test fun narrow_screen_or_large_font_uses_two_lines() {
        val placement = dispersionBoundsPlacement(
            width = 88,
            minLabelWidth = 54,
            maxLabelWidth = 55,
            minCenter = 43f,
            maxCenter = 45f,
            gap = 8
        )
        assertTrue(placement.maxOnSecondLine)
        assertTrue(placement.minX + 54 <= 88)
        assertTrue(placement.maxX + 55 <= 88)
    }
}
