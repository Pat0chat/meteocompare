package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Récepteur commun aux deux widgets Android : météo adaptative et « À retenir ».
 * Partage la planification des rafraîchissements et le cycle de vie Glance.
 * Le worker n'est arrêté que lorsque tous les widgets ont été retirés.
 */
open class MeteoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = MeteoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // KEEP est idempotent : un autre receiver peut rappeler schedule
        // sans créer ni remplacer le worker périodique existant.
        scheduleRefreshSafely(context, immediate = false)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Callback initial/configuration/resize ET filet de sécurité système
        // toutes les 30 min. Cette seconde voie est importante sur les OEMs
        // qui retardent fortement WorkManager (notamment certaines versions
        // MIUI). Le job one-shot forcé incrémente RefreshTickKey et pousse
        // explicitement les RemoteViews ; REPLACE déduplique une éventuelle
        // rafale de callbacks de plusieurs providers.
        scheduleRefreshSafely(context, immediate = false)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleRefreshSafely(context, immediate = true)
    }

    override fun onRestored(
        context: Context,
        oldWidgetIds: IntArray,
        newWidgetIds: IntArray
    ) {
        // Après restauration sur un nouveau téléphone, la base WorkManager
        // n'est pas forcément restaurée avec les AppWidgetIds. Replanifier ici
        // évite un widget figé jusqu'au prochain lancement de l'application.
        scheduleRefreshSafely(context, immediate = false)
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        scheduleRefreshSafely(context, immediate = true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Conserver le worker tant qu'au moins un des deux widgets reste actif.
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

}

/** Les callbacks BroadcastReceiver ne doivent pas crasher sur une panne WorkManager. */
private fun scheduleRefreshSafely(context: Context, immediate: Boolean) {
    runCatching {
        if (immediate) WidgetRefreshScheduler.triggerImmediateRefresh(context)
        else WidgetRefreshScheduler.schedule(context)
    }.onFailure { error ->
        android.util.Log.w(
            "MeteoCompare/Widget",
            "Unable to ${if (immediate) "trigger" else "schedule"} widget refresh",
            error
        )
    }
}

/** Entrée Météo du sélecteur de widgets, adaptative et redimensionnable. */
class MeteoWeatherWidgetReceiver : MeteoWidgetReceiver()

/** Widget éditorial centré sur le signal principal « À retenir ». */
class MeteoInsightWidgetReceiver : MeteoWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeteoInsightWidget()
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
    else -> MeteoWidget()
}

internal fun isInsightWidgetProvider(providerClassName: String?): Boolean =
    providerClassName == MeteoInsightWidgetReceiver::class.java.name

internal fun isRegisteredWidgetProviderClassName(providerClassName: String?): Boolean =
    providerClassName != null && WidgetReceivers.All.any { it.name == providerClassName }

internal fun isOwnedWidgetProvider(
    appPackageName: String,
    providerPackageName: String?,
    providerClassName: String?
): Boolean =
    providerPackageName == appPackageName &&
        isRegisteredWidgetProviderClassName(providerClassName)

/**
 * Liste des deux receivers déclarés dans le manifeste, utilisée pour
 * vérifier la présence de widgets actifs et rafraîchir les bons AppWidgetIds.
 * Garder cette liste synchronisée avec le manifeste et ses tests.
 */
internal object WidgetReceivers {
    val All: List<Class<out MeteoWidgetReceiver>> = listOf(
        MeteoWeatherWidgetReceiver::class.java,
        MeteoInsightWidgetReceiver::class.java
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
