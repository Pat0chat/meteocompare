package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTextSizingPolicyTest {
    @Test fun `auto enlarges only when widget has sufficient room`() {
        assertEquals(1f, widgetTextScale(160f, 70f, true, 140), 0.001f)
        assertEquals(1.12f, widgetTextScale(260f, 150f, true, 100), 0.001f)
        assertEquals(1.20f, widgetTextScale(390f, 220f, true, 85), 0.001f)
    }

    @Test fun `manual choice and caps protect narrow widgets`() {
        assertEquals(0.85f, widgetTextScale(400f, 200f, false, 85), 0.001f)
        assertEquals(1.40f, widgetTextScale(400f, 200f, false, 140), 0.001f)
        assertEquals(1.10f, widgetTextScale(100f, 120f, false, 140), 0.001f)
        assertEquals(1.18f, widgetTextScale(240f, 80f, false, 140), 0.001f)
        assertEquals(1.25f, widgetTextScale(260f, 140f, false, 140), 0.001f)
    }

    @Test fun `scale never decreases as available width and height increase`() {
        for (h in 50..260 step 5) for (w in 80..500 step 5) {
            val base = widgetTextScale(w.toFloat(), h.toFloat(), true, 100)
            assertTrue(widgetTextScale((w + 10).toFloat(), (h + 10).toFloat(), true, 100) >= base)
        }
    }
}
