package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver principal du widget MeteoCompare (variante STANDARD).
 * Le système Android appelle ce receiver pour les événements de lifecycle du
 * widget : ajout, resize, suppression, mise à jour périodique.
 *
 * ─── Multi-provider ────────────────────────────────────────────────────
 * Trois autres receivers frères ([MeteoWidgetReceiverTiny],
 * [MeteoWidgetReceiverWide], [MeteoWidgetReceiverLarge]) existent pour
 * exposer différentes tailles cible dans le picker de widgets — voir le
 * docblock du manifest pour la justification (compatibilité Pixel/Samsung
 * launchers, launchers sans resize).
 *
 * Tous les receivers partagent :
 *   - Le MÊME [GlanceAppWidget] ([MeteoWidget]) pour le rendu. Le layout
 *     s'adapte à la taille réelle via SizeMode.Exact — pas besoin de
 *     variantes de composable par taille.
 *   - Le MÊME lifecycle WorkManager tick (voir [WidgetRefreshScheduler]).
 *   - La MÊME activité de configuration.
 *
 * La seule différence est la meta-data XML (tailles cible/min/max) et
 * l'entrée dans le picker.
 *
 * ─── Lifecycle WorkManager ────────────────────────────────────────────
 *   - onEnabled  : PREMIER widget ajouté pour CE receiver → programme le
 *                  worker périodique. Idempotent (KEEP policy) — si un
 *                  autre receiver frère l'a déjà programmé, no-op.
 *   - onDisabled : DERNIER widget de CE receiver retiré → on ne cancel
 *                  PAS le worker (des frères peuvent encore avoir des
 *                  widgets vivants). Le worker s'auto-noop via son
 *                  early-return `glanceIds.isEmpty()` si vraiment plus
 *                  rien n'est vivant. On cancel seulement si TOUS les
 *                  receivers sont vides — vérifié via [isAnyReceiverAlive].
 *   - onDeleted  : override explicite pour visibilité — la default de
 *                  [GlanceAppWidgetReceiver.onDeleted] appelle déjà
 *                  `cleanUp(appWidgetIds)` qui purge les DataStore Glance
 *                  orphelines. On garde le override pour rendre ce chemin
 *                  explicite au lecteur du code.
 */
open class MeteoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = MeteoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Idempotent : `schedule` utilise ExistingPeriodicWorkPolicy.KEEP,
        // donc si un autre receiver frère a déjà programmé le worker, ce
        // second appel est un no-op côté WorkManager.
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // On cancel le worker UNIQUEMENT si aucun autre receiver frère n'a
        // encore de widget vivant. Sinon on laisserait des widgets d'autres
        // variantes sans tick, avec des labels d'heure gelés.
        if (!isAnyReceiverAlive(context)) {
            WidgetRefreshScheduler.cancel(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
    }

    /**
     * Vrai s'il reste au moins un widget vivant côté launcher pour l'un des
     * receivers MeteoCompare. Utilisé par [onDisabled] pour décider si on
     * peut couper le worker.
     */
    private fun isAnyReceiverAlive(context: Context): Boolean {
        val awm = AppWidgetManager.getInstance(context)
        return WidgetReceivers.All.any { clazz ->
            awm.getAppWidgetIds(ComponentName(context, clazz)).isNotEmpty()
        }
    }
}

/**
 * Variante MINI — cible 1×1. Les 3 receivers ci-dessous n'ajoutent aucun
 * comportement propre : ils héritent TOUT de [MeteoWidgetReceiver]. Leur
 * unique raison d'être est d'être une entrée séparée dans le manifest, ce
 * qui crée une entrée séparée dans le picker de widgets Android.
 *
 * ─── Pourquoi vides ? ──────────────────────────────────────────────────
 * Chaque receiver Android est indexé par sa ComponentName (nom de classe
 * qualifié). Le manifest référence ces classes par name, donc elles DOIVENT
 * exister comme classes concrètes distinctes. Mais leur comportement runtime
 * est 100% commun — d'où l'héritage direct sans override.
 *
 * Le rendu ADAPTATIF à la taille (1×1, 2×1, …, 5×2) est géré côté composable
 * dans [MeteoWidget] via [SizeMode.Exact] et `LocalSize.current`. Pas besoin
 * de composables spécialisés par variante.
 */
class MeteoWidgetReceiverTiny : MeteoWidgetReceiver()

/**
 * Variante BANDEAU — cible 5×1. Voir [MeteoWidgetReceiverTiny] pour la
 * justification de la classe vide.
 */
class MeteoWidgetReceiverWide : MeteoWidgetReceiver()

/**
 * Variante GRAND — cible 5×2. Voir [MeteoWidgetReceiverTiny] pour la
 * justification de la classe vide.
 */
class MeteoWidgetReceiverLarge : MeteoWidgetReceiver()

/**
 * Registre central des receivers de widget MeteoCompare.
 *
 * ─── Pourquoi centraliser ? ───────────────────────────────────────────
 * Plusieurs endroits du code doivent itérer sur "tous les receivers" :
 *   - [MeteoWidgetReceiver.isAnyReceiverAlive] : check si on peut cancel
 *     le worker.
 *   - [WidgetRefreshWorker.doWork] : cross-check `getAppWidgetIds` sur
 *     chaque ComponentName pour filtrer les ghost glanceIds.
 *
 * Sans ce registre, chaque callsite duplique la liste — un ajout futur
 * de variante (une 6×3 tablette par ex) obligerait à traquer 3-4 endroits.
 * La liste étant courte et statique, une `List<Class<...>>` en `object`
 * suffit — pas besoin de réflexion sur le manifest ni de configuration
 * externalisée.
 */
internal object WidgetReceivers {
    /**
     * Ordre : le receiver STANDARD en premier (c'est le plus "canonique",
     * on veut qu'il apparaisse comme référence dans les debugs et logs).
     * Les autres dans l'ordre où ils apparaissent dans le picker (mini,
     * bandeau, grand) pour cohérence UX.
     */
    val All: List<Class<out MeteoWidgetReceiver>> = listOf(
        MeteoWidgetReceiver::class.java,
        MeteoWidgetReceiverTiny::class.java,
        MeteoWidgetReceiverWide::class.java,
        MeteoWidgetReceiverLarge::class.java
    )
}
