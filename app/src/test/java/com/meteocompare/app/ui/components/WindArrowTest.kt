package com.meteocompare.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WindArrowTest {
    @Test
    fun rotation_points_downwind_for_cardinal_directions() {
        assertEquals(180f, windArrowRotation(0), 0f)
        assertEquals(270f, windArrowRotation(90), 0f)
        assertEquals(0f, windArrowRotation(180), 0f)
        assertEquals(90f, windArrowRotation(270), 0f)
    }

    @Test
    fun rotation_normalizes_angles_before_applying_downwind_offset() {
        assertEquals(180f, windArrowRotation(360), 0f)
        assertEquals(90f, windArrowRotation(-90), 0f)
        assertEquals(0f, windArrowRotation(540), 0f)
    }
}
