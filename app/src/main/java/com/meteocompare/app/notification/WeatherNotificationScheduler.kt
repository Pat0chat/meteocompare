package com.meteocompare.app.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.meteocompare.app.domain.model.NotificationSettings
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Planification des notifications météo locales.
 *
 * Deux travaux uniques et indépendants :
 *
 * 1. **Résumé quotidien** — un `OneTimeWorkRequest` différé jusqu'à la
 *    prochaine occurrence de l'heure choisie (fuseau de l'appareil). Chaque
 *    exécution planifie la suivante en fin de cycle
 *    ([scheduleNextDailySummary]) : contrairement à un travail périodique de
 *    24 h, l'heure ne dérive pas au fil des jours. Aucune contrainte réseau :
 *    à l'heure dite, le résumé part du cache si le réseau manque.
 *
 * 2. **Alertes** (divergence, changement de prévision) — un travail
 *    périodique toutes les [ALERTS_INTERVAL_HOURS] heures, avec batterie non
 *    faible. Aucune contrainte réseau n'est imposée : le repository peut ainsi
 *    exploiter un cache valide hors ligne, notamment en mode MANUAL. La fraîcheur
 *    réseau suit l'intervalle de
 *    rafraîchissement choisi par l'utilisateur (MANUAL = cache seulement).
 *
 * Aucune alarme exacte (permission `SCHEDULE_EXACT_ALARM`) : WorkManager peut
 * décaler légèrement l'exécution selon Doze, ce qui reste acceptable pour une
 * notification météo.
 */
object WeatherNotificationScheduler {

    internal const val DAILY_SUMMARY_WORK_NAME = "meteocompare_notification_daily_summary"
    internal const val ALERTS_WORK_NAME = "meteocompare_notification_alerts"
    private const val WORK_TAG = "meteocompare_notifications"

    internal const val KIND_INPUT_KEY = "notification_kind"
    internal const val ALERTS_INTERVAL_HOURS = 3L
    private const val ALERTS_FLEX_HOURS = 1L

    /**
     * Garantit la présence des travaux attendus sans remplacer une
     * planification valide. À appeler au démarrage de l'application.
     */
    fun ensureScheduled(context: Context, settings: NotificationSettings) {
        apply(
            workManager = WorkManager.getInstance(context.applicationContext),
            settings = settings,
            now = ZonedDateTime.now(),
            dailyPolicy = ExistingWorkPolicy.KEEP,
            alertsPolicy = ExistingPeriodicWorkPolicy.KEEP
        )
    }

    /**
     * Applique des réglages modifiés : replanifie le résumé à la nouvelle
     * heure, active ou annule les travaux selon les types choisis.
     */
    fun reschedule(context: Context, settings: NotificationSettings) {
        apply(
            workManager = WorkManager.getInstance(context.applicationContext),
            settings = settings,
            now = ZonedDateTime.now(),
            dailyPolicy = ExistingWorkPolicy.REPLACE,
            alertsPolicy = ExistingPeriodicWorkPolicy.UPDATE
        )
    }

    /**
     * Planifie le résumé suivant depuis le worker en cours d'exécution.
     * APPEND_OR_REPLACE ajoute le travail à la suite du travail actif au lieu
     * de l'annuler (ce que ferait REPLACE sur un travail en cours). Si le
     * résumé a été désactivé entre-temps, rien n'est replanifié : l'écran
     * Réglages a déjà annulé le travail.
     */
    internal fun scheduleNextDailySummary(context: Context, settings: NotificationSettings) {
        if (!settings.dailySummaryEnabled || settings.cityIds.isEmpty()) return
        enqueueDailySummary(
            workManager = WorkManager.getInstance(context.applicationContext),
            time = settings.dailySummaryTime,
            now = ZonedDateTime.now(),
            policy = ExistingWorkPolicy.APPEND_OR_REPLACE
        )
    }

    /** Overload testable. */
    internal fun apply(
        workManager: WorkManager,
        settings: NotificationSettings,
        now: ZonedDateTime,
        dailyPolicy: ExistingWorkPolicy,
        alertsPolicy: ExistingPeriodicWorkPolicy
    ) {
        val hasCities = settings.cityIds.isNotEmpty()

        if (settings.dailySummaryEnabled && hasCities) {
            enqueueDailySummary(workManager, settings.dailySummaryTime, now, dailyPolicy)
        } else {
            workManager.cancelUniqueWork(DAILY_SUMMARY_WORK_NAME)
        }

        if (settings.alertsEnabled && hasCities) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val alerts = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(
                ALERTS_INTERVAL_HOURS, TimeUnit.HOURS,
                ALERTS_FLEX_HOURS, TimeUnit.HOURS
            )
                .setInputData(workDataOf(KIND_INPUT_KEY to WeatherNotificationWorker.Kind.ALERTS.name))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(WORK_TAG)
                .build()
            workManager.enqueueUniquePeriodicWork(ALERTS_WORK_NAME, alertsPolicy, alerts)
        } else {
            workManager.cancelUniqueWork(ALERTS_WORK_NAME)
        }
    }

    private fun enqueueDailySummary(
        workManager: WorkManager,
        time: LocalTime,
        now: ZonedDateTime,
        policy: ExistingWorkPolicy
    ) {
        val request = OneTimeWorkRequestBuilder<WeatherNotificationWorker>()
            .setInputData(workDataOf(KIND_INPUT_KEY to WeatherNotificationWorker.Kind.DAILY_SUMMARY.name))
            .setInitialDelay(delayUntilNext(now, time).toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(DAILY_SUMMARY_WORK_NAME, policy, request)
    }

    /**
     * Délai jusqu'à la prochaine occurrence de [time] strictement après [now].
     * Un worker qui termine après l'heure cible se replanifie donc pour le
     * lendemain ; un éventuel doublon (horloge modifiée) reste filtré par
     * [NotificationDedupLedger], la clé du résumé portant sur la date.
     */
    internal fun delayUntilNext(now: ZonedDateTime, time: LocalTime): Duration {
        var next = now.with(time).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
