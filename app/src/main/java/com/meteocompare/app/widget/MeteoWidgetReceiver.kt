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
        if (!WidgetReceivers.anyAlive(context, AppWidgetManager.getInstance(context))) {
            WidgetRefreshScheduler.cancel(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
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
 * Variante 3×1. Voir [MeteoWidgetReceiverTiny] pour la justification de la
 * classe vide.
 */
class MeteoWidgetReceiver3x1 : MeteoWidgetReceiver()

/**
 * Variante 4×1. Voir [MeteoWidgetReceiverTiny].
 */
class MeteoWidgetReceiver4x1 : MeteoWidgetReceiver()

/**
 * Variante BANDEAU 5×1. Voir [MeteoWidgetReceiverTiny].
 */
class MeteoWidgetReceiverWide : MeteoWidgetReceiver()

/**
 * Variante 2×2. Voir [MeteoWidgetReceiverTiny].
 */
class MeteoWidgetReceiver2x2 : MeteoWidgetReceiver()

/**
 * Variante 3×2. Voir [MeteoWidgetReceiverTiny].
 */
class MeteoWidgetReceiver3x2 : MeteoWidgetReceiver()

/**
 * Variante 4×2. Voir [MeteoWidgetReceiverTiny].
 */
class MeteoWidgetReceiver4x2 : MeteoWidgetReceiver()

/**
 * Variante GRAND 5×2. Voir [MeteoWidgetReceiverTiny].
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
 *
 * ─── Ordre ─────────────────────────────────────────────────────────────
 * Trié par taille croissante (petit → grand) : d'abord les single-row
 * du 1×1 au 5×1, puis les double-row du 2×2 au 5×2. Cet ordre est
 * cohérent avec l'ordre d'apparition dans AndroidManifest.xml — certains
 * launchers respectent cet ordre dans leur picker de widgets.
 *
 * L'invariant "cette liste = ce qui est déclaré au manifest" est
 * verrouillé par [WidgetReceiversRegistryTest].
 */
internal object WidgetReceivers {
    val All: List<Class<out MeteoWidgetReceiver>> = listOf(
        MeteoWidgetReceiverTiny::class.java,       // 1×1
        MeteoWidgetReceiver::class.java,           // 2×1 (default)
        MeteoWidgetReceiver3x1::class.java,        // 3×1
        MeteoWidgetReceiver4x1::class.java,        // 4×1
        MeteoWidgetReceiverWide::class.java,       // 5×1
        MeteoWidgetReceiver2x2::class.java,        // 2×2
        MeteoWidgetReceiver3x2::class.java,        // 3×2
        MeteoWidgetReceiver4x2::class.java,        // 4×2
        MeteoWidgetReceiverLarge::class.java       // 5×2
    )

    /**
     * Vrai s'il reste au moins un widget vivant côté launcher pour l'un des
     * receivers du registre. Utilisé par [MeteoWidgetReceiver.onDisabled]
     * pour décider si on peut couper le worker WorkManager.
     *
     * ─── Pourquoi prendre AppWidgetManager en paramètre ? ────────────
     * Testabilité. Le paramètre `awm` permet aux tests unitaires de passer
     * un mock sans dépendance sur l'Android SDK (dont les classes sont
     * des stubs qui throw "Stub!" dans la JVM de test).
     *
     * Le vrai callsite ([MeteoWidgetReceiver.onDisabled]) obtient
     * l'instance via `AppWidgetManager.getInstance(context)` juste avant
     * l'appel. Pas d'injection Hilt : `AppWidgetManager` a un lifecycle
     * process-scoped stable, une simple factory suffit.
     */
    fun anyAlive(context: Context, awm: AppWidgetManager): Boolean =
        anyAliveWith { clazz ->
            awm.getAppWidgetIds(ComponentName(context, clazz)).isNotEmpty()
        }

    /**
     * Cœur testable de [anyAlive]. Reçoit une fonction de lookup qui répond
     * "ce receiver a-t-il des widgets vivants ?" — sans dépendre d'aucune
     * classe Android SDK (ComponentName, AppWidgetManager).
     *
     * ─── Pourquoi cette indirection ? ────────────────────────────────────
     * Le test unitaire de [anyAlive] ne peut pas passer par
     * `ComponentName(context, clazz)` : ComponentName est une classe Android
     * SDK stubbée dans le classpath des unit tests, son constructeur throw
     * "Stub!" à l'exécution. Idem pour mock AppWidgetManager sans
     * `mockk-agent-jvm` en dépendance de test.
     *
     * Ce helper contient TOUTE la logique métier (l'itération sur `All`) et
     * délègue à l'appelant la partie qui nécessite des classes Android. Les
     * tests fournissent un lookup pur (Map ou lambda), aucune classe SDK
     * n'est chargée sur le chemin de test.
     *
     * Marqué `internal` : accessible depuis les tests du même module,
     * invisible pour les consumers hors module.
     */
    internal fun anyAliveWith(hasWidgetsFor: (Class<out MeteoWidgetReceiver>) -> Boolean): Boolean =
        All.any(hasWidgetsFor)
}
