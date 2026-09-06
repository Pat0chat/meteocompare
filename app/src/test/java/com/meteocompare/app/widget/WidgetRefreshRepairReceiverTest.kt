package com.meteocompare.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshRepairReceiverTest {

    @Test
    fun `boot remplacement et changements dhorloge declenchent une reparation`() {
        listOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.DATE_CHANGED"
        ).forEach { action ->
            assertTrue("action ignorée: $action", isWidgetRefreshRepairAction(action))
        }
    }

    @Test
    fun `une action sans rapport est ignoree`() {
        assertFalse(isWidgetRefreshRepairAction(null))
        assertFalse(isWidgetRefreshRepairAction("android.intent.action.AIRPLANE_MODE"))
    }
}
