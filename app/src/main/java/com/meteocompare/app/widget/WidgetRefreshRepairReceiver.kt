package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.data.worker.BiasRefreshScheduler
import com.meteocompare.app.notification.WeatherNotificationEntryPoint
import com.meteocompare.app.notification.WeatherNotificationScheduler
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Répare la planification après redémarrage ou remplacement de l'APK, et
 * invalide immédiatement le rendu après un changement d'heure/date/fuseau.
 *
 * WorkManager restaure normalement ses travaux tout seul. Après un reboot,
 * ce receiver utilise KEEP et déclenche seulement un rendu widget immédiat.
 * Après remplacement de l'APK, il utilise UPDATE pour migrer explicitement les
 * spécifications des workers de biais et de widget.
 */
class WidgetRefreshRepairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isWidgetRefreshRepairAction(intent.action)) return

        val appContext = context.applicationContext
        val action = intent.action
        val pendingResult = goAsync()

        // BroadcastReceiver.onReceive s'exécute sur Main. WorkManager et le
        // garde SharedPreferences peuvent effectuer des I/O : on termine la
        // réparation dans la fenêtre goAsync plutôt que de bloquer le receiver.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val isAppReplacement = action == Intent.ACTION_MY_PACKAGE_REPLACED

                runCatching {
                    if (isAppReplacement) {
                        BiasRefreshScheduler.updateAfterAppReplacement(appContext)
                    }
                }.onFailure { error ->
                    Log.w("MeteoCompare/BiasWorker", "Unable to repair bias scheduling", error)
                }

                // Le résumé quotidien est un one-shot calculé à partir de
                // l'heure murale. Un changement d'heure/fuseau/date invalide
                // donc son initialDelay : on le remplace immédiatement.
                runCatching {
                    val settings = EntryPointAccessors
                        .fromApplication(appContext, WeatherNotificationEntryPoint::class.java)
                        .userPreferencesRepository()
                        .observeNotificationSettings()
                        .first()
                    if (shouldReplaceWeatherNotificationSchedule(action)) {
                        WeatherNotificationScheduler.reschedule(appContext, settings)
                    } else {
                        WeatherNotificationScheduler.ensureScheduled(appContext, settings)
                    }
                }.onFailure { error ->
                    Log.w("MeteoCompare/Notif", "Unable to repair notification scheduling", error)
                }

                runCatching {
                    val hasWidgets = WidgetReceivers.anyAlive(
                        appContext,
                        AppWidgetManager.getInstance(appContext)
                    )

                    if (hasWidgets) {
                        if (BuildConfig.DEBUG) {
                            Log.d("MeteoCompare/Widget", "Repairing widget refresh after $action")
                        }
                        if (isAppReplacement) {
                            WidgetRefreshScheduler.updateAfterAppReplacement(appContext)
                        } else {
                            WidgetRefreshScheduler.schedule(appContext)
                        }
                        WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
                    }
                }.onFailure { error ->
                    Log.w("MeteoCompare/Widget", "Unable to repair widget scheduling", error)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Actions système qui invalident l'heure ou la planification d'un widget. */
private val widgetRefreshRepairActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_DATE_CHANGED
)

internal fun isWidgetRefreshRepairAction(action: String?): Boolean =
    action in widgetRefreshRepairActions

/**
 * Les événements qui changent l'heure murale doivent remplacer le one-shot du
 * résumé quotidien. Au boot, KEEP suffit puisque WorkManager restaure ses jobs.
 */
internal fun shouldReplaceWeatherNotificationSchedule(action: String?): Boolean = action in setOf(
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_DATE_CHANGED
)
