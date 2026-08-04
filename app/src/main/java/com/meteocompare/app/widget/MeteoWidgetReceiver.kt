package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver principal du widget MeteoCompare (variante STANDARD 2×1).
 * Le système Android appelle ce receiver pour les événements de lifecycle du
 * widget : ajout, resize, suppression, mise à jour périodique.
 *
 * ─── Multi-provider (11 receivers au total) ─────────────────────────────
 * Dix autres receivers frères existent pour exposer différentes tailles
 * cible dans le picker de widgets — voir le docblock du manifest pour la
 * justification (compatibilité Pixel/Samsung launchers, launchers sans
 * resize). Liste complète dans [WidgetReceivers.All].
 *
 * Tous les receivers partagent :
 *   - Le MÊME [GlanceAppWidget] ([MeteoWidget]) pour le rendu. Le layout
 *     s'adapte à la taille réelle via SizeMode.Exact — pas besoin de
 *     variantes de composable par taille.
 *   - Le MÊME lifecycle WorkManager tick (voir [WidgetRefreshScheduler]).
 *   - La MÊME activité de configuration.
 *   - La MÊME plage de resize (min 1×1, max 5×2). Voir docblock d'un XML
 *     provider (par ex. `meteocompare_widget_info.xml`) pour la
 *     justification des dimensions homogènes.
 *
 * La seule différence est la target size dans la meta-data XML et
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
 *                  receivers sont vides — vérifié via [WidgetReceivers.anyAlive].
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
        // KEEP est idempotent : un autre receiver peut rappeler schedule
        // sans créer ni remplacer le worker périodique existant.
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Callback initial/configuration/resize du launcher. La cadence
        // périodique XML est désactivée (updatePeriodMillis=0) pour éviter un
        // second réveil toutes les 30 min en parallèle de WorkManager. Cet
        // événement reste une occasion de réparer la planification unique ;
        // `super` déclenche le rendu Glance demandé par le launcher.
        WidgetRefreshScheduler.schedule(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray
    ) {
        // Après restauration sur un nouveau téléphone, la base WorkManager
        // n'est pas forcément restaurée avec les AppWidgetIds. Replanifier ici
        // évite un widget figé jusqu'au prochain lancement de l'application.
        WidgetRefreshScheduler.schedule(context)
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // On cancel le worker UNIQUEMENT si aucun autre receiver frère n'a
        // encore de widget vivant. Sinon on laisserait des widgets d'autres
        // variantes sans tick, avec des labels d'heure gelés.
        val anyWidgetStillAlive = runCatching {
            WidgetReceivers.anyAlive(context, AppWidgetManager.getInstance(context))
        }.getOrElse { error ->
            // Fail-open : en cas de bug launcher temporaire, conserver un
            // worker inutile est préférable à figer les widgets restants.
            android.util.Log.w(
                "MeteoCompare/Widget",
                "Unable to inspect widgets during onDisabled",
                error
            )
            true
        }
        if (!anyWidgetStillAlive) WidgetRefreshScheduler.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
    }
}

/**
 * Variante MINI — cible 1×1. Les 8 receivers ci-dessous n'ajoutent aucun
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
class MeteoWidgetReceiver1x1 : MeteoWidgetReceiver()

/**
 * Variante 2×1.
 */
class MeteoWidgetReceiver2x1 : MeteoWidgetReceiver()

/**
 * Variante 3×1.
 */
class MeteoWidgetReceiver3x1 : MeteoWidgetReceiver()

/**
 * Variante 4×1.
 */
class MeteoWidgetReceiver4x1 : MeteoWidgetReceiver()

/**
 * Variante 5×1.
 */
class MeteoWidgetReceiver5x1 : MeteoWidgetReceiver()

/**
 * Variante 2×2.
 */
class MeteoWidgetReceiver2x2 : MeteoWidgetReceiver()

/**
 * Variante 3×2.
 */
class MeteoWidgetReceiver3x2 : MeteoWidgetReceiver()

/**
 * Variante 4×2.
 */
class MeteoWidgetReceiver4x2 : MeteoWidgetReceiver()

/**
 * Variante 5×2.
 */
class MeteoWidgetReceiver5x2 : MeteoWidgetReceiver()

/** Widget éditorial centré sur le signal principal « À retenir ». */
class MeteoInsightWidgetReceiver : MeteoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeteoInsightWidget()
}

/** Widget comparatif centré sur le consensus propre à chaque variable. */
class MeteoConsensusWidgetReceiver : MeteoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeteoConsensusWidget()
}

/**
 * Résout le bon GlanceAppWidget depuis le provider Android réel. Utilisé par
 * la configuration et le worker pour ne jamais pousser un RemoteViews d'une
 * autre famille de widget sur le même AppWidgetId.
 */
internal fun glanceWidgetForProviderClassName(providerClassName: String?): GlanceAppWidget = when (
    providerClassName
) {
    MeteoInsightWidgetReceiver::class.java.name -> MeteoInsightWidget()
    MeteoConsensusWidgetReceiver::class.java.name -> MeteoConsensusWidget()
    else -> MeteoWidget()
}

internal fun isEditorialWidgetProvider(providerClassName: String?): Boolean =
    providerClassName == MeteoInsightWidgetReceiver::class.java.name ||
        providerClassName == MeteoConsensusWidgetReceiver::class.java.name

/**
 * Registre central des receivers de widget MeteoCompare.
 *
 * ─── Pourquoi centraliser ? ───────────────────────────────────────────
 * Plusieurs endroits du code doivent itérer sur "tous les receivers" :
 *   - [WidgetReceivers.anyAlive] : check si on peut cancel
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
        MeteoWidgetReceiver1x1::class.java,       // 1×1
        MeteoWidgetReceiver2x1::class.java,       // 2×1 (default)
        MeteoWidgetReceiver3x1::class.java,       // 3×1
        MeteoWidgetReceiver4x1::class.java,       // 4×1
        MeteoWidgetReceiver5x1::class.java,       // 5×1
        MeteoWidgetReceiver2x2::class.java,       // 2×2
        MeteoWidgetReceiver3x2::class.java,       // 3×2
        MeteoWidgetReceiver4x2::class.java,       // 4×2
        MeteoWidgetReceiver5x2::class.java,       // 5×2
        MeteoInsightWidgetReceiver::class.java,   // 4×2 éditorial
        MeteoConsensusWidgetReceiver::class.java  // 4×2 consensus
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

    internal fun liveWidgetIdsWith(
        idsFor: (Class<out MeteoWidgetReceiver>) -> List<Int>
    ): List<Int> = All.flatMap(idsFor).distinct()

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
