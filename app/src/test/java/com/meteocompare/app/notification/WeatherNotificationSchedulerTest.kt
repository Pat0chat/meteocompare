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

    // ───────────────────── prochaine occurrence quotidienne ─────────────────────

    @Test
    fun `heure a venir aujourd'hui`() {
        assertEquals(
            now.plusHours(1),
            WeatherNotificationScheduler.nextDailyOccurrence(now, LocalTime.of(7, 0))
        )
        assertEquals(
            Duration.ofHours(1),
            WeatherNotificationScheduler.delayUntilNext(now, LocalTime.of(7, 0))
        )
    }

    @Test
    fun `heure deja atteinte - planifiee le lendemain`() {
        val atTarget = now.withHour(7)
        assertEquals(
            atTarget.plusDays(1),
            WeatherNotificationScheduler.nextDailyOccurrence(atTarget, LocalTime.of(7, 0))
        )
        assertEquals(
            Duration.ofDays(1),
            WeatherNotificationScheduler.delayUntilNext(atTarget, LocalTime.of(7, 0))
        )
        assertEquals(
            Duration.ofHours(24).minusSeconds(10),
            WeatherNotificationScheduler.delayUntilNext(atTarget.plusSeconds(10), LocalTime.of(7, 0))
        )
    }

    @Test
    fun `passage a l'heure d'ete - l'heure locale est conservee`() {
        // Nuit du 28 au 29 mars 2026 : 02:00 CET → 03:00 CEST.
        val evening = ZonedDateTime.of(2026, 3, 28, 22, 0, 0, 0, paris)
        val next = WeatherNotificationScheduler.nextDailyOccurrence(evening, LocalTime.of(7, 0))

        assertEquals(LocalTime.of(7, 0), next.toLocalTime())
        assertEquals(Duration.ofHours(8), Duration.between(evening, next))
    }

    // ───────────────────────────── alertes WorkManager ─────────────────────────────

    @Test
    fun `alertes desactivees - periodique et kickoff sont annules`() {
        WeatherNotificationScheduler.applyAlerts(
            workManager = workManager,
            settings = NotificationSettings(),
            alertsPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            kickImmediately = true
        )

        verify { workManager.cancelUniqueWork(WeatherNotificationScheduler.ALERTS_WORK_NAME) }
        verify { workManager.cancelUniqueWork(WeatherNotificationScheduler.ALERTS_IMMEDIATE_WORK_NAME) }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `sans ville suivie - aucune alerte nest planifiee`() {
        WeatherNotificationScheduler.applyAlerts(
            workManager = workManager,
            settings = NotificationSettings(divergenceAlertsEnabled = true),
            alertsPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            kickImmediately = true
        )

        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `alertes - travail periodique sans contrainte reseau`() {
        val request = slot<PeriodicWorkRequest>()

        WeatherNotificationScheduler.applyAlerts(
            workManager = workManager,
            settings = NotificationSettings(forecastChangeAlertsEnabled = true, cityIds = setOf("paris")),
            alertsPolicy = ExistingPeriodicWorkPolicy.KEEP,
            kickImmediately = false
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
        assertEquals(androidx.work.NetworkType.NOT_REQUIRED, spec.constraints.requiredNetworkType)
        assertEquals(true, spec.constraints.requiresBatteryNotLow())
        assertEquals(
            WeatherNotificationWorker.Kind.ALERTS.name,
            spec.input.getString(WeatherNotificationScheduler.KIND_INPUT_KEY)
        )
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                WeatherNotificationScheduler.ALERTS_IMMEDIATE_WORK_NAME,
                any(),
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `modification reglages - alertes controlees immediatement`() {
        val request = slot<OneTimeWorkRequest>()

        WeatherNotificationScheduler.applyAlerts(
            workManager = workManager,
            settings = NotificationSettings(divergenceAlertsEnabled = true, cityIds = setOf("paris")),
            alertsPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            kickImmediately = true
        )

        verify {
            workManager.enqueueUniqueWork(
                WeatherNotificationScheduler.ALERTS_IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                capture(request)
            )
        }
        assertEquals(
            WeatherNotificationWorker.Kind.ALERTS.name,
            request.captured.workSpec.input.getString(WeatherNotificationScheduler.KIND_INPUT_KEY)
        )
    }

    @Test
    fun `resume declenche par alarme utilise un work expedited sans delai`() {
        val request = WeatherNotificationScheduler.immediateRequest(
            WeatherNotificationWorker.Kind.DAILY_SUMMARY
        )

        assertEquals(0L, request.workSpec.initialDelay)
        assertEquals(
            WeatherNotificationWorker.Kind.DAILY_SUMMARY.name,
            request.workSpec.input.getString(WeatherNotificationScheduler.KIND_INPUT_KEY)
        )
    }
}
