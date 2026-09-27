package com.meteocompare.app.notification

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.meteocompare.app.domain.model.NotificationSettings
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WeatherNotificationSchedulerTest {

    private lateinit var workManager: WorkManager
    private val paris = ZoneId.of("Europe/Paris")
    private val now = ZonedDateTime.of(2026, 9, 28, 6, 0, 0, 0, paris)

    @Before
    fun setUp() {
        workManager = mockk(relaxed = true)
    }

    // ───────────────────────── delayUntilNext ─────────────────────────

    @Test
    fun `heure a venir aujourd'hui`() {
        assertEquals(
            Duration.ofHours(1),
            WeatherNotificationScheduler.delayUntilNext(now, LocalTime.of(7, 0))
        )
    }

    @Test
    fun `heure deja atteinte - planifiee le lendemain`() {
        val atTarget = now.withHour(7)
        assertEquals(
            Duration.ofDays(1),
            WeatherNotificationScheduler.delayUntilNext(atTarget, LocalTime.of(7, 0))
        )
        // Un worker qui termine quelques secondes après l'heure cible ne se
        // replanifie jamais pour la même journée.
        assertEquals(
            Duration.ofHours(24).minusSeconds(10),
            WeatherNotificationScheduler.delayUntilNext(atTarget.plusSeconds(10), LocalTime.of(7, 0))
        )
    }

    @Test
    fun `passage a l'heure d'ete - l'heure locale est conservee`() {
        // Nuit du 28 au 29 mars 2026 : 02:00 CET → 03:00 CEST.
        val evening = ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, paris)
        assertEquals(
            Duration.ofHours(8),
            WeatherNotificationScheduler.delayUntilNext(evening, LocalTime.of(7, 0))
        )
    }

    // ───────────────────────────── apply ─────────────────────────────

    @Test
    fun `tout desactive - les deux travaux sont annules`() {
        WeatherNotificationScheduler.apply(
            workManager,
            NotificationSettings(),
            now,
            ExistingWorkPolicy.REPLACE,
            ExistingPeriodicWorkPolicy.UPDATE
        )

        verify { workManager.cancelUniqueWork(WeatherNotificationScheduler.DAILY_SUMMARY_WORK_NAME) }
        verify { workManager.cancelUniqueWork(WeatherNotificationScheduler.ALERTS_WORK_NAME) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `sans ville suivie - rien n'est planifie meme si un type est actif`() {
        WeatherNotificationScheduler.apply(
            workManager,
            NotificationSettings(dailySummaryEnabled = true, divergenceAlertsEnabled = true),
            now,
            ExistingWorkPolicy.REPLACE,
            ExistingPeriodicWorkPolicy.UPDATE
        )

        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `resume quotidien - travail unique differe jusqu'a l'heure choisie`() {
        val request = slot<OneTimeWorkRequest>()

        WeatherNotificationScheduler.apply(
            workManager,
            NotificationSettings(
                dailySummaryEnabled = true,
                dailySummaryTime = LocalTime.of(7, 30),
                cityIds = setOf("paris")
            ),
            now,
            ExistingWorkPolicy.REPLACE,
            ExistingPeriodicWorkPolicy.UPDATE
        )

        verify {
            workManager.enqueueUniqueWork(
                WeatherNotificationScheduler.DAILY_SUMMARY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                capture(request)
            )
        }
        assertEquals(Duration.ofMinutes(90).toMillis(), request.captured.workSpec.initialDelay)
        assertEquals(
            WeatherNotificationWorker.Kind.DAILY_SUMMARY.name,
            request.captured.workSpec.input.getString(WeatherNotificationScheduler.KIND_INPUT_KEY)
        )
        // Les alertes restent désactivées.
        verify { workManager.cancelUniqueWork(WeatherNotificationScheduler.ALERTS_WORK_NAME) }
    }

    @Test
    fun `alertes - travail periodique avec reseau requis`() {
        val request = slot<PeriodicWorkRequest>()

        WeatherNotificationScheduler.apply(
            workManager,
            NotificationSettings(forecastChangeAlertsEnabled = true, cityIds = setOf("paris")),
            now,
            ExistingWorkPolicy.KEEP,
            ExistingPeriodicWorkPolicy.KEEP
        )

        verify {
            workManager.enqueueUniquePeriodicWork(
                WeatherNotificationScheduler.ALERTS_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                capture(request)
            )
        }
        val spec = request.captured.workSpec
        assertEquals(
            TimeUnit.HOURS.toMillis(WeatherNotificationScheduler.ALERTS_INTERVAL_HOURS),
            spec.intervalDuration
        )
        assertEquals(androidx.work.NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(
            WeatherNotificationWorker.Kind.ALERTS.name,
            spec.input.getString(WeatherNotificationScheduler.KIND_INPUT_KEY)
        )
        verify { workManager.cancelUniqueWork(WeatherNotificationScheduler.DAILY_SUMMARY_WORK_NAME) }
    }
}
