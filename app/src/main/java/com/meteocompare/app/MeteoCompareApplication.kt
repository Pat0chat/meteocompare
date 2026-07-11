package com.meteocompare.app

import android.app.Application
import com.meteocompare.app.data.worker.BiasRefreshScheduler
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
 *   Idempotent via `ExistingPeriodicWorkPolicy.KEEP` : re-schedule à chaque
 *   process start, no-op si déjà planifié.
 *
 * ## Ce qui n'est PAS planifié ici
 *
 * Le worker widget est planifié à la demande dans `MeteoWidgetReceiver.onEnabled`
 * (uniquement quand un widget est posé), pas au démarrage de l'app. Deux
 * politiques différentes délibérées :
 *   - Widget : dépendant de l'existence d'au moins un widget posé → schedule
 *     par lifecycle receiver.
 *   - Biais : dépendant de l'existence d'au moins une ville favorite, mais
 *     puisque le worker skip proprement une liste vide de favorites, on peut
 *     toujours schedule sans overhead — plus simple à raisonner.
 */
@HiltAndroidApp
class MeteoCompareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BiasRefreshScheduler.schedule(this)
    }
}
