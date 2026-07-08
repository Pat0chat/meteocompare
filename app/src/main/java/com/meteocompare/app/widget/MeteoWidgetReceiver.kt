package com.meteocompare.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver déclaré dans le manifest sous le tag `<receiver>`. Le
 * système Android l'appelle pour les événements de lifecycle du widget :
 * ajout, resize, suppression, mise à jour périodique.
 *
 * Rôle du receiver : dire à Glance quel [GlanceAppWidget] rendre, et
 * gérer le cycle de vie du [WidgetRefreshScheduler] (programmation du worker
 * WorkManager) en fonction de la présence de widgets sur l'écran d'accueil.
 *
 * ─── Lifecycle WorkManager ──────────────────────────────────────────────
 *   - onEnabled  : PREMIER widget ajouté → programme le worker périodique.
 *                  Après ça, chaque ajout supplémentaire ne re-schedule pas
 *                  (le nom unique du worker fait qu'on partage un seul job).
 *   - onDisabled : DERNIER widget retiré → annule le worker. Pas de raison
 *                  de continuer à fetcher pour un widget qui n'existe plus.
 *
 * ─── Séparation des responsabilités ─────────────────────────────────────
 * La logique de rendu et de fetch de données vit dans [MeteoWidget]. Le
 * receiver ne fait que :
 *   1. Servir de pont Glance ↔ système (glanceAppWidget).
 *   2. Câbler le worker WorkManager sur l'ajout/retrait de widgets.
 *
 * Cette séparation permet notamment de tester [MeteoWidget] en isolation
 * (Glance offre un GlanceAppWidget-testing runner) sans avoir à instancier
 * un BroadcastReceiver Android.
 */
class MeteoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = MeteoWidget()

    /**
     * Scope éphémère pour la lecture (suspending) des prefs.
     *
     * On ne peut PAS utiliser `runBlocking` : bien qu'on soit dans un
     * BroadcastReceiver (qui a un time budget de 10 s avant ANR), bloquer
     * le main thread pour lire DataStore est mauvais style et peut
     * théoriquement causer un ANR si le DataStore est contended.
     *
     * `SupervisorJob` pour que l'échec d'une child job n'annule pas le scope
     * entier (utile si on ajoute des lookups en parallèle plus tard).
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleFromPreferences(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Plus aucun widget → on annule le worker. Sans ça, on continuerait
        // à consommer batterie/data pour rien tant que l'app n'est pas
        // désinstallée.
        WidgetRefreshScheduler.cancel(context)
    }

    /**
     * Programme le worker en lisant l'intervalle utilisateur choisi.
     *
     * On ne peut pas lire directement les prefs ici (fonction non-suspending
     * du système), donc on lance une coroutine sur [scope]. C'est
     * intentionnellement fire-and-forget : si le worker n'est pas encore
     * programmé au moment où le système fait le premier refresh
     * `updatePeriodMillis`, ce n'est pas grave — soit le widget affichera
     * du cache (99 % des cas), soit le worker se programmera à la coroutine
     * suivante. Aucun cas critique de perte de données.
     */
    private fun scheduleFromPreferences(context: Context) {
        val appCtx = context.applicationContext
        scope.launch {
            val entry = EntryPointAccessors.fromApplication(
                appCtx, WidgetEntryPoint::class.java
            )
            val interval = entry.userPreferencesRepository()
                .observeRefreshInterval().first()
            WidgetRefreshScheduler.schedule(appCtx, interval)
        }
    }
}
