package com.meteocompare.app.notification

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `receiver reste ouvert tant que enqueue WorkManager nest pas confirme`() = runTest {
        val enqueueGate = CompletableDeferred<Unit>()
        var enqueueStarted = false
        var finishCount = 0

        launchDailySummaryEnqueue(
            context = mockk<Context>(relaxed = true),
            scope = this,
            finish = { finishCount++ },
            enqueueAndAwait = {
                enqueueStarted = true
                enqueueGate.await()
            }
        )

        runCurrent()
        assertTrue("l'enqueue doit avoir démarré", enqueueStarted)
        assertEquals("le PendingResult ne doit pas être libéré trop tôt", 0, finishCount)

        enqueueGate.complete(Unit)
        advanceUntilIdle()

        assertEquals("le PendingResult doit être libéré exactement une fois", 1, finishCount)
    }

    @Test
    fun `receiver borne lattente WorkManager et libere le broadcast au timeout`() = runTest {
        var finishCount = 0

        launchDailySummaryEnqueue(
            context = mockk<Context>(relaxed = true),
            scope = this,
            finish = { finishCount++ },
            enqueueAndAwait = { delay(DAILY_ENQUEUE_TIMEOUT_MS * 2) }
        )

        runCurrent()
        assertEquals(0, finishCount)

        advanceTimeBy(DAILY_ENQUEUE_TIMEOUT_MS)
        advanceUntilIdle()

        assertEquals("le timeout doit toujours libérer le PendingResult", 1, finishCount)
    }

    @Test
    fun `receiver libere aussi le PendingResult si enqueue WorkManager echoue`() = runTest {
        var finishCount = 0

        launchDailySummaryEnqueue(
            context = mockk<Context>(relaxed = true),
            scope = this,
            finish = { finishCount++ },
            enqueueAndAwait = { error("enqueue failed") }
        )

        advanceUntilIdle()

        assertEquals("un échec ne doit jamais laisser le broadcast ouvert", 1, finishCount)
    }
}
