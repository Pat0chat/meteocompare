package com.meteocompare.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.await
import com.meteocompare.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Réveille le pipeline du résumé quotidien à l'heure choisie par l'utilisateur.
 *
 * Le déclenchement temporel appartient à AlarmManager (qui peut réveiller une
 * application mise en cache / en Doze), tandis que le travail potentiellement
 * plus long reste exécuté par WorkManager. Le receiver ne fait donc aucune I/O
 * météo lui-même.
 *
 * L'enqueue WorkManager est asynchrone. On conserve la fenêtre de vie du
 * broadcast avec [goAsync] jusqu'à ce que WorkManager confirme que la demande
 * est enregistrée/planifiée. Sans cela, un process réveillé uniquement pour
 * l'alarme peut redevenir tuable dès le retour de [onReceive], ce qui peut
 * laisser le travail en attente jusqu'au prochain démarrage de l'application.
 */
class WeatherNotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WeatherNotificationScheduler.DAILY_SUMMARY_ALARM_ACTION) return

        val appContext = context.applicationContext
        // goAsync() est normalement non nul lorsqu'Android délivre réellement
        // le broadcast. Les tests qui appellent onReceive() directement peuvent
        // ne pas disposer d'un PendingResult système : le nullable les garde
        // valides sans changer le comportement en production.
        val pendingResult: BroadcastReceiver.PendingResult? = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WeatherNotificationScheduler.onDailySummaryAlarm(appContext).await()
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "Daily summary work enqueue committed")
                }
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "Unable to enqueue daily summary work", error)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private companion object {
        const val LOG_TAG = "MeteoCompare/Notif"
    }
}
