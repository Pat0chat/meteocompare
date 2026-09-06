package com.meteocompare.app.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavHostTest {

    @Test
    fun smartphone_widths_use_the_single_pane_layout() {
        assertFalse(shouldUseTabletLayout(320.dp))
        assertFalse(shouldUseTabletLayout(600.dp))
        assertFalse(shouldUseTabletLayout(839.dp))
    }

    @Test
    fun tablet_expanded_widths_use_the_two_pane_layout() {
        assertTrue(shouldUseTabletLayout(840.dp))
        assertTrue(shouldUseTabletLayout(1_200.dp))
    }

    @Test
    fun tablet_list_pane_uses_its_minimum_width_at_the_breakpoint() {
        assertEquals(340.dp, tabletListPaneWidth(840.dp))
    }

    @Test
    fun tablet_list_pane_grows_proportionally_between_its_bounds() {
        assertEquals(408.dp, tabletListPaneWidth(1_200.dp))
    }

    @Test
    fun tablet_list_pane_is_capped_to_preserve_detail_space() {
        assertEquals(420.dp, tabletListPaneWidth(1_600.dp))
    }

    @Test
    fun tablet_keeps_a_selection_that_is_still_available() {
        val cityIds = listOf("paris", "lyon")

        assertEquals("lyon", resolveSelectedCityId("lyon", cityIds))
    }

    @Test
    fun tablet_selects_the_first_city_initially_or_after_removal() {
        val cityIds = listOf("paris", "lyon")

        assertEquals("paris", resolveSelectedCityId("removed", cityIds))
        assertEquals("paris", resolveSelectedCityId(null, cityIds))
    }

    @Test
    fun tablet_clears_the_selection_when_no_city_remains() {
        assertNull(resolveSelectedCityId("paris", emptyList()))
    }
}
