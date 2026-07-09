package com.meteocompare.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver déclaré dans le manifest sous le tag `<receiver>`. Le
 * système Android l'appelle pour les événements de lifecycle du widget :
 * ajout, resize, suppression, mise à jour périodique.
 *
 * Rôle du receiver : dire à Glance quel [GlanceAppWidget] rendre, et gérer
 * le cycle de vie du [WidgetRefreshScheduler] (programmation du worker
 * WorkManager) en fonction de la présence de widgets sur l'écran d'accueil.
 *
 * ─── Lifecycle WorkManager ──────────────────────────────────────────────
 *   - onEnabled  : PREMIER widget ajouté → programme le worker périodique.
 *                  Après ça, chaque ajout supplémentaire ne re-schedule pas
 *                  (le nom unique du worker fait qu'on partage un seul job).
 *   - onDisabled : DERNIER widget retiré → annule le worker. Pas de raison
 *                  de continuer à tick pour un widget qui n'existe plus.
 *   - onDeleted  : override explicite pour visibilité — la default de
 *                  [GlanceAppWidgetReceiver.onDeleted] appelle déjà
 *                  `cleanUp(appWidgetIds)` qui purge les DataStore Glance
 *                  orphelines. On garde le override pour rendre ce chemin
 *                  explicite au lecteur du code (et pour pouvoir y greffer
 *                  du log/telemetry plus tard sans avoir à modifier la
 *                  signature).
 *
 * ─── Ce que le receiver ne fait PLUS ────────────────────────────────────
 * Précédemment le receiver lisait la `RefreshInterval` utilisateur pour
 * calibrer la cadence du worker. Depuis le découplage tick/fetch (voir
 * docblock [WidgetRefreshScheduler]), la cadence est fixée à 15 min — plus
 * besoin de lire une préf pour programmer, la fonction devient synchrone
 * et le scope+coroutine associés disparaissent.
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

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Plus aucun widget → on annule le worker. Sans ça, on continuerait
        // à consommer batterie pour un tick qui ne fait plus rien (glanceIds
        // vide → early return dans doWork), mais autant ne pas laisser un
        // job périodique inutile en file d'attente WorkManager.
        WidgetRefreshScheduler.cancel(context)
    }

    /**
     * Override explicite pour visibilité — voir docblock de classe. La
     * super-implémentation Glance appelle déjà `cleanUp(appWidgetIds)`, ce
     * qui purge la DataStore Glance pour ces widgets. C'est ce cleanup qui
     * empêche l'accumulation de ghost glanceIds au fil des ajouts/retraits
     * successifs de widgets.
     */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
    }
}
