package com.meteocompare.app

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.StrictMode
import android.util.Log
import com.meteocompare.app.data.worker.BiasRefreshScheduler
import com.meteocompare.app.widget.WidgetReceivers
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.HiltAndroidApp

/**
 * Application Hilt racine.
 *
 * ## Rôle
 *
 * Bootstrap de Hilt via [HiltAndroidApp] + planification des workers
 * périodiques globaux — indépendants d'une UI particulière.
 *
 * ## Workers planifiés ici
 *
 * - [BiasRefreshScheduler] — fetch delta quotidien des observations pour le
 *   feature "suivi de biais" (chip sous les noms de modèle dans CityDetail).
 *   Idempotent via `ExistingPeriodicWorkPolicy.UPDATE` : re-schedule à chaque
 *   process start, no-op si déjà planifié.
 *
 * ## Réparation de la planification widget
 *
 * À chaque démarrage de process, l'application vérifie si au moins une
 * instance de widget est posée. Si oui, elle ré-enregistre le travail unique
 * avec la policy UPDATE. Cela répare les bases WorkManager nettoyées par un
 * constructeur ou les anciennes contraintes conservées après mise à jour.
 */
@HiltAndroidApp
class MeteoCompareApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Détecte en développement les I/O réseau/disque sur Main et les
        // ressources Android qui resteraient enregistrées après leur cycle de
        // vie. Aucun coût ni changement de politique dans les builds release.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }

        BiasRefreshScheduler.schedule(this)

        // Répare la planification après mise à jour de l'app, restauration ou
        // nettoyage de la base WorkManager par un OEM. On ne programme rien
        // quand aucun widget n'est réellement posé.
        val hasWidgets = runCatching {
            WidgetReceivers.anyAlive(this, AppWidgetManager.getInstance(this))
        }.getOrElse { error ->
            // Un launcher constructeur ne doit jamais pouvoir faire échouer le
            // démarrage complet de l'application. Le prochain onUpdate du
            // provider ou la prochaine ouverture réparera la planification.
            Log.w("MeteoCompare/Widget", "Unable to inspect installed widgets", error)
            false
        }
        if (hasWidgets) WidgetRefreshScheduler.schedule(this)
    }
}
