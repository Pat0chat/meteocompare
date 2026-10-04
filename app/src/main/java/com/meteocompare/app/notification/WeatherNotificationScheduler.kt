package com.meteocompare.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.domain.model.NotificationSettings
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Planification des notifications météo locales.
 *
 * Deux mécanismes complémentaires :
 *
 * 1. **Résumé quotidien** — AlarmManager porte uniquement l'heure murale
 *    choisie par l'utilisateur. L'alarme est inexacte et utilise
 *    [AlarmManager.setAndAllowWhileIdle] : elle peut donc réveiller
 *    l'application en Doze sans demander l'accès spécial aux alarmes exactes.
 *    À sa réception, un WorkManager immédiat exécute le pipeline météo. Le
 *    worker planifie l'occurrence suivante avant le calcul météo.
 *
 *    WorkManager reste volontairement l'exécuteur du travail long, mais n'est
 *    plus utilisé comme horloge : un `initialDelay` n'est qu'une date
 *    d'éligibilité et peut être fortement retardé par JobScheduler, notamment
 *    lorsque l'application est en arrière-plan.
 *
 * 2. **Alertes** (divergence, révision de prévision) — travail périodique toutes
 *    les [ALERTS_INTERVAL_HOURS] heures, batterie non faible. Lors d'une
 *    modification explicite des réglages, un contrôle immédiat est aussi lancé
 *    afin qu'une nouvelle activation n'attende pas la première fenêtre
 *    périodique.
 *
 * Aucune contrainte réseau n'est imposée : le repository peut exploiter un
 * cache valide hors ligne, notamment en mode MANUAL.
 */
object WeatherNotificationScheduler {

    internal const val DAILY_SUMMARY_WORK_NAME = "meteocompare_notification_daily_summary"
    internal const val ALERTS_WORK_NAME = "meteocompare_notification_alerts"
    internal const val ALERTS_IMMEDIATE_WORK_NAME = "meteocompare_notification_alerts_now"
    internal const val DAILY_SUMMARY_ALARM_ACTION =
        "com.meteocompare.app.action.WEATHER_NOTIFICATION_DAILY_SUMMARY"

    private const val WORK_TAG = "meteocompare_notifications"
    private const val DAILY_ALARM_REQUEST_CODE = 41021

    internal const val KIND_INPUT_KEY = "notification_kind"
    internal const val ALERTS_INTERVAL_HOURS = 3L
    private const val ALERTS_FLEX_HOURS = 1L

    /**
     * Garantit la présence des planifications attendues sans repousser une
     * alarme quotidienne déjà valide. À appeler au démarrage de l'application.
     */
    fun ensureScheduled(context: Context, settings: NotificationSettings) {
        val appContext = context.applicationContext
        ensureDailySummaryAlarm(appContext, settings, ZonedDateTime.now())
        applyAlerts(
            workManager = WorkManager.getInstance(appContext),
            settings = settings,
            alertsPolicy = ExistingPeriodicWorkPolicy.KEEP,
            kickImmediately = false
        )
    }

    /**
     * Applique des réglages modifiés : remplace l'alarme quotidienne et met à
     * jour les alertes périodiques. Les alertes activées sont également
     * évaluées une fois immédiatement.
     */
    fun reschedule(
        context: Context,
        settings: NotificationSettings,
        kickAlertsImmediately: Boolean = true
    ) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        // Nettoie aussi un ancien résumé différé (migration depuis la version
        // WorkManager-only) et annule un cycle devenu obsolète après changement
        // explicite de l'heure ou des villes suivies.
        workManager.cancelUniqueWork(DAILY_SUMMARY_WORK_NAME)
        replaceDailySummaryAlarm(appContext, settings, ZonedDateTime.now())
        applyAlerts(
            workManager = workManager,
            settings = settings,
            alertsPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            kickImmediately = kickAlertsImmediately
        )
    }

    /**
     * Appelé au début d'un résumé quotidien pour préparer le suivant.
     * Si le résumé a été désactivé entre-temps, l'alarme est supprimée.
     */
    internal fun scheduleNextDailySummary(context: Context, settings: NotificationSettings) {
        replaceDailySummaryAlarm(context.applicationContext, settings, ZonedDateTime.now())
    }

    /**
     * Point d'entrée du BroadcastReceiver AlarmManager. L'alarme one-shot est
     * considérée consommée puis le worker réel est lancé immédiatement.
     */
    internal fun onDailySummaryAlarm(context: Context): Operation {
        DailyAlarmStateStore(context).clear()
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Daily summary alarm fired at ${ZonedDateTime.now()}")
        }
        return enqueueImmediateWorker(
            workManager = WorkManager.getInstance(context.applicationContext),
            name = DAILY_SUMMARY_WORK_NAME,
            kind = WeatherNotificationWorker.Kind.DAILY_SUMMARY,
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    /** Testable : ne contient que la partie WorkManager des alertes. */
    internal fun applyAlerts(
        workManager: WorkManager,
        settings: NotificationSettings,
        alertsPolicy: ExistingPeriodicWorkPolicy,
        kickImmediately: Boolean
    ) {
        val hasCities = settings.cityIds.isNotEmpty()
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

            if (kickImmediately) {
                enqueueImmediateWorker(
                    workManager = workManager,
                    name = ALERTS_IMMEDIATE_WORK_NAME,
                    kind = WeatherNotificationWorker.Kind.ALERTS,
                    policy = ExistingWorkPolicy.REPLACE
                )
            }
        } else {
            workManager.cancelUniqueWork(ALERTS_WORK_NAME)
            workManager.cancelUniqueWork(ALERTS_IMMEDIATE_WORK_NAME)
        }
    }

    private fun ensureDailySummaryAlarm(
        context: Context,
        settings: NotificationSettings,
        now: ZonedDateTime
    ) {
        if (!settings.dailySummaryEnabled || settings.cityIds.isEmpty()) {
            cancelDailySummaryAlarm(context)
            return
        }

        val existing = dailyAlarmPendingIntent(context, PendingIntent.FLAG_NO_CREATE)
        val stored = DailyAlarmStateStore(context).read()
        val expectedTime = settings.dailySummaryTime.toString()
        val expectedZone = now.zone.id
        val stillMatches = existing != null &&
            stored != null &&
            stored.localTime == expectedTime &&
            stored.zoneId == expectedZone

        if (stillMatches) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "Keeping daily alarm for ${stored.triggerAtMillis}")
            }
            return
        }
        scheduleDailySummaryAlarm(context, settings.dailySummaryTime, now)
    }

    private fun replaceDailySummaryAlarm(
        context: Context,
        settings: NotificationSettings,
        now: ZonedDateTime
    ) {
        if (!settings.dailySummaryEnabled || settings.cityIds.isEmpty()) {
            cancelDailySummaryAlarm(context)
            return
        }
        scheduleDailySummaryAlarm(context, settings.dailySummaryTime, now)
    }

    private fun scheduleDailySummaryAlarm(
        context: Context,
        time: LocalTime,
        now: ZonedDateTime
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val trigger = nextDailyOccurrence(now, time)
        val triggerAtMillis = trigger.toInstant().toEpochMilli()
        val operation = dailyAlarmPendingIntent(
            context,
            PendingIntent.FLAG_UPDATE_CURRENT
        ) ?: return

        // Même PendingIntent => AlarmManager remplace l'occurrence précédente.
        // setAndAllowWhileIdle reste inexact : aucune permission d'alarme exacte
        // n'est nécessaire, mais l'application peut être réveillée en Doze.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation
        )
        DailyAlarmStateStore(context).write(
            localTime = time.toString(),
            zoneId = trigger.zone.id,
            triggerAtMillis = triggerAtMillis
        )
        if (BuildConfig.DEBUG) {
            val delay = Duration.between(now, trigger)
            Log.d(
                LOG_TAG,
                "Daily alarm scheduled for $trigger (in ${delay.toMinutes()} min)"
            )
        }
    }

    private fun cancelDailySummaryAlarm(context: Context) {
        val operation = dailyAlarmPendingIntent(context, PendingIntent.FLAG_NO_CREATE)
        if (operation != null) {
            context.getSystemService(AlarmManager::class.java).cancel(operation)
            operation.cancel()
        }
        DailyAlarmStateStore(context).clear()
        // Nettoie aussi un ancien work différé créé par les versions précédentes.
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(DAILY_SUMMARY_WORK_NAME)
    }

    private fun dailyAlarmPendingIntent(context: Context, lookupFlag: Int): PendingIntent? {
        val flags = lookupFlag or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        val intent = Intent(context, WeatherNotificationAlarmReceiver::class.java)
            .setAction(DAILY_SUMMARY_ALARM_ACTION)
        return PendingIntent.getBroadcast(context, DAILY_ALARM_REQUEST_CODE, intent, flags)
    }

    private fun enqueueImmediateWorker(
        workManager: WorkManager,
        name: String,
        kind: WeatherNotificationWorker.Kind,
        policy: ExistingWorkPolicy
    ): Operation {
        val request = immediateRequest(kind)
        return workManager.enqueueUniqueWork(name, policy, request)
    }

    internal fun immediateRequest(
        kind: WeatherNotificationWorker.Kind,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<WeatherNotificationWorker>()
            .setInputData(workDataOf(KIND_INPUT_KEY to kind.name))
            .apply {
                // Avant Android 12, expedited exigerait un service de premier
                // plan et un ForegroundInfo. Une requête ordinaire suffit ici.
                if (sdkInt >= Build.VERSION_CODES.S) {
                    setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                }
            }
            .addTag(WORK_TAG)
            .build()

    /**
     * Prochaine occurrence de [time], strictement après [now]. Une heure
     * inexistante au printemps est décalée du saut ; en automne on choisit
     * la première occurrence, sans répéter le résumé dans l'heure doublée.
     */
    internal fun nextDailyOccurrence(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val date = now.toLocalDate()
        val minute = time.withSecond(0).withNano(0)
        val today = date.atTime(minute).atZone(now.zone)
        return if (today.isAfter(now)) today
        else date.plusDays(1).atTime(minute).atZone(now.zone)
    }

    internal fun delayUntilNext(now: ZonedDateTime, time: LocalTime): Duration =
        Duration.between(now, nextDailyOccurrence(now, time))

    private const val LOG_TAG = "MeteoCompare/Notif"
}

/**
 * Petit garde persistant pour distinguer une vraie alarme quotidienne existante
 * d'un PendingIntent obsolète, et réparer un changement de réglage interrompu.
 */
private class DailyAlarmStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class State(
        val localTime: String,
        val zoneId: String,
        val triggerAtMillis: Long
    )

    fun read(): State? {
        val localTime = prefs.getString(KEY_LOCAL_TIME, null) ?: return null
        val zoneId = prefs.getString(KEY_ZONE_ID, null) ?: return null
        val trigger = prefs.getLong(KEY_TRIGGER_AT, Long.MIN_VALUE)
        if (trigger == Long.MIN_VALUE) return null
        return State(localTime, zoneId, trigger)
    }

    fun write(localTime: String, zoneId: String, triggerAtMillis: Long) {
        prefs.edit()
            .putString(KEY_LOCAL_TIME, localTime)
            .putString(KEY_ZONE_ID, zoneId)
            .putLong(KEY_TRIGGER_AT, triggerAtMillis)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "meteocompare_notification_daily_alarm"
        const val KEY_LOCAL_TIME = "local_time"
        const val KEY_ZONE_ID = "zone_id"
        const val KEY_TRIGGER_AT = "trigger_at"
    }
}
