package com.meteocompare.app.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meteocompare.app.domain.model.RefreshInterval
import java.util.concurrent.TimeUnit

/**
 * Rafraîchissement périodique des widgets d'accueil via WorkManager.
 *
 * ─── Pourquoi WorkManager, pas updatePeriodMillis ? ──────────────────────
 * L'ancien mécanisme (attribut `updatePeriodMillis` du provider-info XML)
 * s'appuie sur AlarmManager :
 *   - Plancher de 30 minutes (le système ignore les valeurs plus basses).
 *   - Wakelocks du device pour respecter l'alarme.
 *   - Aucune contrainte système : le rafraîchissement s'exécute même en
 *     mode avion (échec réseau garanti, batterie perdue).
 *   - Aucun respect de Doze / App Standby Buckets.
 *
 * WorkManager :
 *   - Respecte Doze / App Standby / Battery Saver — le système regroupe les
 *     jobs pour minimiser les wake-ups.
 *   - Contrainte NETWORK CONNECTED : on n'essaie même pas de fetch si le
 *     device est hors-ligne. Reprise automatique quand la connectivité revient.
 *   - Contrainte BATTERY_NOT_LOW : quand la batterie est basse, on suspend
 *     les rafraîchissements automatiques (l'utilisateur peut toujours
 *     rafraîchir manuellement via l'app).
 *   - Persistance des jobs à travers les redémarrages device.
 *
 * ─── Interaction avec [MeteoWidget.provideGlance] ────────────────────────
 * Le worker écrit une clé "refresh tick" (timestamp) dans les prefs Glance
 * de CHAQUE widget. Cette clé est lue via `currentState<Preferences>()`
 * dans `provideGlance` — un changement invalide la lecture réactive,
 * Glance recompose, et le `LaunchedEffect(cityId, forecastMode, refreshTick)`
 * re-déclenche `loadWidgetData` qui va re-fetcher (via le stream
 * cache-first → réseau si assez vieux).
 *
 * Sans ce tick, un simple `MeteoWidget().update(context, glanceId)` ne
 * suffit pas : la lecture réactive du state ne détecte aucun changement,
 * donc le composable interne ne se ré-exécute pas, et le LaunchedEffect
 * ne repart pas.
 */
internal object WidgetRefreshScheduler {

    /**
     * Nom unique du travail périodique. Un seul job à la fois — les appels
     * ultérieurs à [schedule] avec `ExistingPeriodicWorkPolicy.UPDATE`
     * remplacent la config sans doublon.
     */
    private const val WORK_NAME = "meteocompare_widget_refresh"

    /**
     * Programme (ou re-programme) le worker de rafraîchissement en fonction
     * de l'intervalle utilisateur.
     *
     * - [RefreshInterval.MANUAL] : annule tout worker existant. Le widget ne
     *   se rafraîchit alors plus automatiquement — comportement voulu par
     *   l'utilisateur qui a explicitement demandé "manuel".
     * - Sinon : programme un [androidx.work.PeriodicWorkRequest] avec la
     *   cadence demandée. Le minimum WorkManager est 15 min ; la
     *   [RefreshInterval] la plus basse est calquée dessus.
     *
     * `ExistingPeriodicWorkPolicy.UPDATE` : si un worker tourne déjà avec
     * une autre cadence (parce que l'utilisateur vient de changer le
     * réglage), on l'update sans perdre le state — la prochaine exécution
     * respectera la nouvelle cadence.
     */
    fun schedule(context: Context, interval: RefreshInterval) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (interval == RefreshInterval.MANUAL) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            interval.duration.toMillis(), TimeUnit.MILLISECONDS
        )
            .setConstraints(
                Constraints.Builder()
                    // Pas de tentative de fetch en offline — WorkManager
                    // reportera automatiquement le job quand la connectivité
                    // revient. Économie majeure de wake-ups en avion/tunnel.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    // Quand la batterie est basse, on suspend l'actualisation
                    // auto. Le user voit le dernier snapshot cache jusqu'à ce
                    // que la batterie remonte ou qu'il ouvre l'app manuellement.
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Annule tout worker de rafraîchissement. Appelée quand le dernier widget
     * est retiré de l'écran d'accueil (`onDisabled` du receiver) — inutile
     * de continuer à fetcher pour un widget qui n'existe plus.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(WORK_NAME)
    }
}

/**
 * Worker qui déclenche un rafraîchissement de tous les widgets MeteoCompare
 * posés sur l'écran d'accueil.
 *
 * Stratégie : on ne fait PAS le fetch réseau ici — on invalide juste l'état
 * Glance en incrémentant `RefreshTickKey`. Cela déclenche la recomposition
 * du widget qui, dans son `LaunchedEffect`, appelle `loadWidgetData` avec
 * la clé de fraîcheur cache appropriée. Résultat :
 *
 *   - Si le cache est plus jeune que l'intervalle utilisateur → pas de
 *     requête réseau, on réutilise juste le cache.
 *   - Sinon → un unique fetch réseau (partagé via le cache pour l'app).
 *
 * Cette séparation évite de dupliquer la logique de fetch entre le worker
 * et l'app — la vérité vit dans [ForecastRepositoryImpl.getCityForecastStream].
 */
internal class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val manager = GlanceAppWidgetManager(ctx)
        val glanceIds = manager.getGlanceIds(MeteoWidget::class.java)
        if (glanceIds.isEmpty()) {
            // Cas edge : le worker a survécu au retrait de tous les widgets
            // (le cancel dans onDisabled a raté ou race). Rien à faire.
            return Result.success()
        }

        val now = System.currentTimeMillis()
        glanceIds.forEach { glanceId ->
            runCatching {
                // Écriture réactive : la modification de RefreshTickKey
                // invalide la lecture `currentState<Preferences>()` dans le
                // composable → Glance recompose → LaunchedEffect(refreshTick)
                // re-déclenche loadWidgetData.
                updateAppWidgetState(
                    context = ctx,
                    definition = PreferencesGlanceStateDefinition,
                    glanceId = glanceId
                ) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[REFRESH_TICK_KEY] = now
                    }
                }
                // Update explicite : force Glance à rerender même dans le cas
                // rare où le trigger réactif n'aurait pas fait effet (widget
                // fraîchement installé, provider non encore attaché, etc.).
                MeteoWidget().update(ctx, glanceId)
            }
        }
        // On retourne Result.success() même en cas d'échec de fetch réseau :
        // WorkManager reprogrammera la prochaine exécution normalement. Un
        // Result.retry() ici serait redondant avec la contrainte NETWORK
        // CONNECTED — si on est arrivés jusqu'ici, on a du réseau ; si le
        // fetch a échoué, c'est un serveur down, pas quelque chose que retry
        // dans les 30 secondes va résoudre.
        return Result.success()
    }

    companion object {
        /**
         * Clé "refresh tick" dans les prefs Glance. Timestamp du dernier
         * déclenchement worker. Utilisée comme dépendance de [LaunchedEffect]
         * dans MeteoWidget pour re-déclencher le fetch.
         *
         * Note : c'est la même clé que celle exposée par [WidgetPreferences]
         * — dupliquée ici pour l'autonomie du fichier. Voir le commentaire
         * dans WidgetPreferences.
         */
        internal val REFRESH_TICK_KEY = longPreferencesKey("widget_refresh_tick")
    }
}
