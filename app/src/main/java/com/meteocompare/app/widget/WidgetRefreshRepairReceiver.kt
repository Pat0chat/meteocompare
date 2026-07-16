package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Répare la planification après redémarrage ou remplacement de l'APK.
 *
 * WorkManager restaure normalement ses travaux tout seul, mais ce receiver
 * applique aussi la spécification courante (`ExistingPeriodicWorkPolicy.UPDATE`)
 * et déclenche un rendu immédiat. C'est utile après une mise à jour qui retire
 * une ancienne contrainte batterie, ou sur un firmware qui a nettoyé la base
 * WorkManager tout en conservant les widgets du launcher.
 */
class WidgetRefreshRepairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val appContext = context.applicationContext
        val hasWidgets = runCatching {
            WidgetReceivers.anyAlive(
                appContext,
                AppWidgetManager.getInstance(appContext)
            )
        }.getOrElse { error ->
            Log.w("MeteoCompare/Widget", "Unable to repair widget scheduling", error)
            false
        }

        if (hasWidgets) {
            Log.d("MeteoCompare/Widget", "Repairing widget refresh after ${intent.action}")
            WidgetRefreshScheduler.schedule(appContext)
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
    }
}
