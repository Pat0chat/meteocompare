package com.meteocompare.app.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavHostTest {

    @Test
    fun two_pane_layout_starts_at_expanded_width() {
        assertFalse(shouldUseTabletLayout(839.dp))
        assertTrue(shouldUseTabletLayout(840.dp))
        assertTrue(shouldUseTabletLayout(1_200.dp))
    }

    @Test
    fun list_pane_stays_readable_without_taking_over_the_detail() {
        assertEquals(340.dp, tabletListPaneWidth(840.dp))
        assertEquals(408.dp, tabletListPaneWidth(1_200.dp))
        assertEquals(420.dp, tabletListPaneWidth(1_600.dp))
    }

    @Test
    fun selection_is_kept_or_falls_back_when_the_city_list_changes() {
        val cityIds = listOf("paris", "lyon")

        assertEquals("lyon", resolveSelectedCityId("lyon", cityIds))
        assertEquals("paris", resolveSelectedCityId("removed", cityIds))
        assertEquals("paris", resolveSelectedCityId(null, cityIds))
        assertNull(resolveSelectedCityId("paris", emptyList()))
    }
}
