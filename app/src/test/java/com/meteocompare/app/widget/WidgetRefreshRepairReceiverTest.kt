package com.meteocompare.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import android.content.Intent
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
    fun `changements d heure et remplacement remplacent la planification notifications`() {
        listOf(
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED
        ).forEach { action ->
            assertTrue("action non replanifiée: $action", shouldReplaceWeatherNotificationSchedule(action))
        }
        assertFalse(shouldReplaceWeatherNotificationSchedule(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(shouldReplaceWeatherNotificationSchedule(null))
    }

    @Test
    fun `une action sans rapport est ignoree`() {
        assertFalse(isWidgetRefreshRepairAction(null))
        assertFalse(isWidgetRefreshRepairAction("android.intent.action.AIRPLANE_MODE"))
    }
}
