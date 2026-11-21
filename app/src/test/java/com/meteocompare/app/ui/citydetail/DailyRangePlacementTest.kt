package com.meteocompare.app.ui.citydetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyRangePlacementTest {
    @Test fun `fits only when both complete labels and their gap fit`() {
        assertTrue(dailyRangeFitsInline(34, 72, 112, 6))
        assertTrue(dailyRangeFitsInline(34, 72, 113, 6))
        assertFalse(dailyRangeFitsInline(34, 72, 111, 6))
    }

    @Test fun `long localized units fall back below instead of clipping the reading`() {
        assertFalse(dailyRangeFitsInline(85, 92, 150, 7))
        assertTrue(dailyRangeFitsInline(85, 92, 184, 7))
        assertFalse(dailyRangeFitsInline(85, 92, 183, 7))
    }

    @Test fun `invalid dimensions and integer overflow never force inline rendering`() {
        assertFalse(dailyRangeFitsInline(-1, 8, 200, 6))
        assertFalse(dailyRangeFitsInline(30, -1, 200, 6))
        assertFalse(dailyRangeFitsInline(30, 8, -1, 6))
        assertFalse(dailyRangeFitsInline(30, 8, 200, -1))
        assertFalse(dailyRangeFitsInline(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 6))
    }

    @Test fun `zero width and exact boundary are supported`() {
        assertTrue(dailyRangeFitsInline(0, 0, 0, 0))
        assertFalse(dailyRangeFitsInline(1, 0, 0, 0))
    }
}
