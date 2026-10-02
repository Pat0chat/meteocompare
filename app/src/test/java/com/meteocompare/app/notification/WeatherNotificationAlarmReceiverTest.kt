package com.meteocompare.app.notification

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WeatherNotificationAlarmReceiverTest {

    @Test
    fun `action quotidienne est stable et reservee a lapplication`() {
        assertEquals(
            "com.meteocompare.app.action.WEATHER_NOTIFICATION_DAILY_SUMMARY",
            WeatherNotificationScheduler.DAILY_SUMMARY_ALARM_ACTION
        )
    }

    @Test
    fun `action quotidienne ne collisionne pas avec les broadcasts systeme`() {
        val action = WeatherNotificationScheduler.DAILY_SUMMARY_ALARM_ACTION
        assertNotEquals(Intent.ACTION_BOOT_COMPLETED, action)
        assertNotEquals(Intent.ACTION_TIME_CHANGED, action)
        assertNotEquals(Intent.ACTION_TIMEZONE_CHANGED, action)
    }
}
