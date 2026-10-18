package com.meteocompare.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLocationRefreshVisibilityTest {
    @Test fun `button appears only for explicit current-location widget configuration`() {
        assertTrue(shouldShowWidgetLocationAction("current"))
        assertFalse(shouldShowWidgetLocationAction(null))
        assertFalse(shouldShowWidgetLocationAction("2988507"))
        assertFalse(shouldShowWidgetLocationAction("current:977:48"))
    }
}
