package com.meteocompare.app

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.StrictMode
import android.util.Log
import com.meteocompare.app.core.locale.initializePersistedLocaleCache
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
 *   Les démarrages ordinaires utilisent `ExistingPeriodicWorkPolicy.KEEP` :
 *   le travail existant est conservé sans annulation/replanification.
 *
 * ## Réparation de la planification widget
 *
 * À chaque démarrage de process, l'application vérifie si au moins une
 * instance de widget est posée. Si oui, elle garantit la présence du travail
 * unique avec la policy KEEP. La migration de sa spécification via UPDATE est
 * réservée au broadcast MY_PACKAGE_REPLACED.
 */
@HiltAndroidApp
class MeteoCompareApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Lecture disque unique, volontairement effectuée avant StrictMode.
        // MainActivity.attachBaseContext et les widgets utilisent ensuite le
        // cache mémoire sans relire SharedPreferences sur le thread principal.
        initializePersistedLocaleCache(this)

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

        // Garantit la présence du travail sans remplacer une planification
        // déjà valide. La policy UPDATE est réservée à MY_PACKAGE_REPLACED.
        // On ne programme rien quand aucun widget n'est réellement posé.
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
