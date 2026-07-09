package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Tick périodique des widgets d'accueil.
 *
 * ─── Découpage tick d'affichage vs fetch réseau ─────────────────────────
 * Version précédente : la cadence du worker était pilotée par la
 * [com.meteocompare.app.domain.model.RefreshInterval] utilisateur (30 min,
 * 1h, 3h, 6h, ou MANUAL). En pratique ce paramètre servait DEUX buts
 * incompatibles :
 *
 *   1. Faire évoluer l'affichage — labels d'heure ("14h→15h" au passage
 *      d'heure), "Auj." qui devient "Hier" à minuit, etc.
 *   2. Fetcher les données depuis Open-Meteo.
 *
 * Un utilisateur qui choisissait HOUR_1 acceptait implicitement "un fetch
 * par heure au max", mais se retrouvait avec des labels d'heure gelés
 * jusqu'au tick suivant — d'où la plainte "les heures ne changent pas au
 * fur et à mesure du temps".
 *
 * Maintenant le worker tick est à cadence FIXE = 15 min (le minimum
 * WorkManager). La [RefreshInterval] utilisateur est encore lue à chaque
 * loadWidgetData, mais elle sert UNIQUEMENT de seuil `maxCacheAgeMs`
 * dans le repository :
 *
 *   - Tick 15 min → invalidation du LaunchedEffect → loadWidgetData tourne
 *     → labels d'heure recalculés depuis un Instant.now() frais.
 *   - À l'intérieur de loadWidgetData, le repo lit son cache et ne fetche
 *     que si `System.currentTimeMillis() - cachedFetchedAt > maxCacheAgeMs`.
 *     Donc en HOUR_1 : 1 tick sur 4 fait un vrai HTTP, les 3 autres sont
 *     cache-only.
 *
 * Coût du tick cache-only : ~10 ms de CPU (lecture Room + reconstruction
 * WidgetData). Négligeable vs le coût radio d'un HTTP.
 *
 * ─── Pourquoi pas AlarmManager ? ────────────────────────────────────────
 * Voir docblock historique retenu ci-dessous — les motifs (Doze, App
 * Standby, contraintes système) restent valides.
 *
 * WorkManager :
 *   - Respecte Doze / App Standby / Battery Saver — le système regroupe les
 *     jobs pour minimiser les wake-ups.
 *   - Contrainte BATTERY_NOT_LOW : quand la batterie est basse, on suspend
 *     le tick automatique.
 *   - Pas de contrainte NETWORK CONNECTED : le tick doit tourner même
 *     offline pour faire évoluer les labels d'heure. Le fetch, lui, est
 *     court-circuité en amont par NetworkMonitor.isOnline() dans le repo.
 *   - Persistance à travers les redémarrages device.
 */
internal object WidgetRefreshScheduler {

    /**
     * Nom unique du travail périodique. Un seul job à la fois — les appels
     * ultérieurs à [schedule] avec `ExistingPeriodicWorkPolicy.KEEP`
     * réutilisent le job existant sans le recréer.
     */
    private const val WORK_NAME = "meteocompare_widget_refresh"

    /**
     * Cadence FIXE du tick d'affichage. 15 min = minimum autorisé par
     * WorkManager pour un PeriodicWorkRequest. On veut ce plancher pour que
     * le passage d'heure (14:00 → 15:00) soit reflété rapidement dans les
     * labels "14h 15h 16h 17h" → "15h 16h 17h 18h".
     */
    private const val TICK_MINUTES = 15L

    /**
     * Programme (ou garde) le worker périodique. Idempotent via
     * `ExistingPeriodicWorkPolicy.KEEP` : appeler plusieurs fois (au
     * démarrage, à l'ajout d'un nouveau widget, à un changement de settings)
     * ne recrée pas le job. La cadence étant maintenant fixe, il n'y a plus
     * jamais besoin de re-schedule pour un changement d'intervalle
     * utilisateur — seulement [triggerImmediateRefresh] pour propager le
     * changement sans attendre 15 min.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            TICK_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    // Pas de contrainte réseau : le tick doit tourner offline
                    // pour que les labels d'heure évoluent. Le fetch réseau
                    // est short-circuité par NetworkMonitor.isOnline() en
                    // amont dans ForecastRepositoryImpl.fetchAndCache.
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    /**
     * Déclenche un tick unique en plus du périodique. Utilisé quand une
     * préférence qui affecte le widget change dans Settings :
     *
     *   - Modèles activés (impacte les URLs Open-Meteo et donc le cache)
     *   - Intervalle de rafraîchissement (impacte `maxCacheAgeMs`)
     *
     * Sans ce trigger, un toggle de modèle mettrait jusqu'à 15 min à se
     * refléter sur l'écran d'accueil — expérience frustrante quand l'user
     * vient explicitement de faire un choix.
     *
     * Le OneTime job partage le même [WidgetRefreshWorker] class que le
     * périodique : même logique, même filtre ghost-IDs.
     */
    fun triggerImmediateRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    /**
     * Annule tout worker de rafraîchissement. Appelée quand le dernier widget
     * est retiré de l'écran d'accueil ([MeteoWidgetReceiver.onDisabled]) —
     * inutile de continuer à tick pour un widget qui n'existe plus.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(WORK_NAME)
    }
}

/**
 * Worker qui déclenche un rafraîchissement des widgets MeteoCompare posés
 * sur l'écran d'accueil.
 *
 * ─── Ce qu'il fait, ce qu'il ne fait PAS ────────────────────────────────
 * On écrit uniquement un timestamp dans `RefreshTickKey` des prefs Glance de
 * chaque widget vivant. C'est cette écriture — pas de fetch, pas d'update
 * explicit — qui invalide la lecture réactive `currentState<Preferences>()`
 * dans [MeteoWidget.provideGlance], force la recomposition, et re-déclenche
 * le `LaunchedEffect(cityId, forecastMode, refreshTick)` qui contient
 * `loadWidgetData`. Là seulement le repo décide fetch OU cache selon
 * `maxCacheAgeMs`.
 *
 * ─── Suppression du double-update ───────────────────────────────────────
 * Version précédente : après `updateAppWidgetState`, on appelait aussi
 * `MeteoWidget().update(ctx, glanceId)` "au cas où le trigger réactif
 * n'aurait pas fait effet". En pratique ce belt-and-suspenders démarrait
 * une SECONDE session Glance en parallèle, qui re-lançait provideGlance
 * → LaunchedEffect → loadWidgetData → potentiellement second fetch HTTP.
 *
 * Le state write via `updateAppWidgetState` SUFFIT — Glance recompose bien
 * de manière réactive dès que la valeur du pref change. Testé sur widget
 * fraîchement installé (le cas d'edge invoqué à l'époque), la recomposition
 * arrive au premier tick périodique. Pas de flake observé.
 *
 * ─── Filtre ghost glanceIds ─────────────────────────────────────────────
 * Deuxième source de duplication : `GlanceAppWidgetManager.getGlanceIds`
 * retourne des IDs vus par Glance en interne. Certains scénarios laissent
 * ces IDs orphelins (widget removed pendant un crash, ConfigActivity
 * cancelled après que le système a alloué le widgetId, app update pendant
 * que le widget était sur l'écran, etc.). Ces ghosts n'ont pas de widget
 * vivant côté launcher mais recevraient un tick — et donc un fetch — à
 * chaque cycle, expliquant les "3 requêtes pour 1 widget" observées.
 *
 * On croise avec `AppWidgetManager.getAppWidgetIds` (la vérité côté
 * système) et on skip les IDs qui n'y sont pas. On ne peut pas purger la
 * DataStore Glance des ghosts (aucune API publique pour ça), mais le fait
 * de les ignorer suffit à couper l'effet visible : plus de fetch fantôme.
 * Les prefs orphelines occupent quelques octets en trop mais restent
 * neutres.
 */
internal class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val glanceManager = GlanceAppWidgetManager(ctx)
        val glanceIds = glanceManager.getGlanceIds(MeteoWidget::class.java)
        if (glanceIds.isEmpty()) {
            // Le worker a survécu au retrait de tous les widgets — le cancel
            // dans onDisabled a raté, ou race avec un tick déjà en vol.
            // Rien à faire, on laisse le prochain cycle décider.
            return Result.success()
        }

        // Vérité système sur les widgets réellement posés sur l'écran
        // d'accueil. Utilisée juste après pour filtrer les glanceIds qui
        // seraient encore trackés côté Glance mais sans widget vivant.
        val liveWidgetIds = AppWidgetManager.getInstance(ctx)
            .getAppWidgetIds(ComponentName(ctx, MeteoWidgetReceiver::class.java))
            .toSet()

        val now = System.currentTimeMillis()

        glanceIds.forEach { glanceId ->
            val widgetId = runCatching { glanceManager.getAppWidgetId(glanceId) }
                .getOrNull()
            if (widgetId == null || widgetId !in liveWidgetIds) {
                // Ghost : Glance connaît l'ID mais le launcher non. On skip.
                //
                // Note : on ne peut PAS purger la DataStore Glance associée
                // depuis ici — `GlanceAppWidgetManager` n'expose pas d'API
                // publique pour retirer un glanceId. Le prefs orphelin reste,
                // mais l'important est neutralisé : plus de tick → plus de
                // recomposition → plus de fetch pour cet ID. Ces ghosts
                // finiront par disparaître si l'utilisateur re-drop un widget
                // (Glance recycle les IDs) ou à la désinstallation de l'app.
                return@forEach
            }
            runCatching {
                updateAppWidgetState(
                    context = ctx,
                    definition = PreferencesGlanceStateDefinition,
                    glanceId = glanceId
                ) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WidgetPreferences.RefreshTickKey] = now
                    }
                }
                // ⚠ PAS de MeteoWidget().update(ctx, glanceId) ici — voir
                // docblock de classe pour la raison (second session Glance
                // → double fetch).
            }
        }

        // Result.success même en cas d'échec dans une des runCatching : le
        // prochain cycle périodique de 15 min ré-essaiera. Un Result.retry
        // ici serait redondant avec la cadence WorkManager.
        return Result.success()
    }
}
