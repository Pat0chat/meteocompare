package com.meteocompare.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Réveille le pipeline du résumé quotidien à l'heure choisie par l'utilisateur.
 *
 * Le déclenchement temporel appartient à AlarmManager (qui peut réveiller une
 * application mise en cache / en Doze), tandis que le travail potentiellement
 * plus long reste exécuté par WorkManager. Le receiver ne fait donc aucune I/O
 * météo lui-même.
 */
class WeatherNotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WeatherNotificationScheduler.DAILY_SUMMARY_ALARM_ACTION) return
        WeatherNotificationScheduler.onDailySummaryAlarm(context.applicationContext)
    }
}
