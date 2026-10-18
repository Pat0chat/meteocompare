package com.meteocompare.app.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPrivacyGridTest {
    @Test fun `close fixes inside same grid cell have identical forecast identity`() {
        val first = LocationPrivacyGrid.cell(48.85341, 2.34880)
        val movedSlightly = LocationPrivacyGrid.cell(48.85001, 2.34700)
        assertEquals(first, movedSlightly)
        assertEquals(first, LocationPrivacyGrid.cell(48.85341, 2.34880))
    }

    @Test fun `crossing cells changes forecast cache identity`() {
        val paris = LocationPrivacyGrid.cell(48.85341, 2.34880)
        val lyon = LocationPrivacyGrid.cell(45.76, 4.83)
        assertNotEquals(LocationPrivacyGrid.key(paris), LocationPrivacyGrid.key(lyon))
        assertTrue(LocationPrivacyGrid.isCellId(LocationPrivacyGrid.key(paris)))
        assertFalse(LocationPrivacyGrid.isCellId("2988507"))
    }

    @Test fun `grid never stores raw gps precision`() {
        val cell = LocationPrivacyGrid.cell(48.85341, 2.34880)
        assertEquals(48.85, cell.first * LocationPrivacyGrid.STEP_DEGREES, 0.00001)
        assertEquals(2.35, cell.second * LocationPrivacyGrid.STEP_DEGREES, 0.00001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reject invalid gps samples`() {
        LocationPrivacyGrid.cell(Double.NaN, 2.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reject out of world coordinates`() {
        LocationPrivacyGrid.cell(95.0, 2.0)
    }
}
