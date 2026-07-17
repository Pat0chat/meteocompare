package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meteocompare.app.data.worker.BiasRefreshScheduler

/**
 * Répare la planification après redémarrage ou remplacement de l'APK.
 *
 * WorkManager restaure normalement ses travaux tout seul. Après un reboot,
 * ce receiver utilise KEEP et déclenche seulement un rendu widget immédiat.
 * Après remplacement de l'APK, il utilise UPDATE pour migrer explicitement les
 * spécifications des workers de biais et de widget.
 */
class WidgetRefreshRepairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val appContext = context.applicationContext
        val isAppReplacement = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (isAppReplacement) {
            BiasRefreshScheduler.updateAfterAppReplacement(appContext)
        }

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
            if (isAppReplacement) {
                WidgetRefreshScheduler.updateAfterAppReplacement(appContext)
            } else {
                WidgetRefreshScheduler.schedule(appContext)
            }
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
    }
}
