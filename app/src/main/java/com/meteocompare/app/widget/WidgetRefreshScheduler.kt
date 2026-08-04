package com.meteocompare.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.RefreshInterval
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val WIDGET_LOG_TAG = "MeteoCompare/Widget"

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
 * Le worker conserve un tick de sécurité à 15 min (minimum WorkManager),
 * mais ne reconstruit plus systématiquement les RemoteViews. Il compare le
 * bucket courant au dernier rendu : 15 min pour le profil le plus frais,
 * 30 min pour MINUTES_30 et 1 h pour HOUR_1/HOURS_3/HOURS_6/MANUAL.
 *
 * Les rafraîchissements immédiats (configuration, préférences, boot) portent
 * un flag `force` et contournent ce filtre. Lorsqu'un rendu est nécessaire,
 * loadWidgetData conserve le même seuil `maxCacheAgeMs` : la réduction de
 * CPU/IPC n'altère donc ni les fetch réseau attendus ni le mode manuel.
 *
 * ─── Pourquoi pas AlarmManager ? ────────────────────────────────────────
 * Voir docblock historique retenu ci-dessous — les motifs (Doze, App
 * Standby, contraintes système) restent valides.
 *
 * WorkManager :
 *   - Respecte Doze / App Standby / Battery Saver — le système regroupe les
 *     jobs pour minimiser les wake-ups.
 *   - Aucune contrainte batterie explicite : les anciens jobs sont migrés
 *     via la policy UPDATE afin de ne pas rester bloqués en mode économie.
 *   - Pas de contrainte NETWORK CONNECTED : le tick doit tourner même
 *     offline pour faire évoluer les labels d'heure. Le fetch, lui, est
 *     court-circuité en amont par NetworkMonitor.isOnline() dans le repo.
 *   - Persistance à travers les redémarrages device.
 */
internal object WidgetRefreshScheduler {

    /**
     * Nom unique du travail périodique. Un seul job à la fois : les appels
     * ordinaires à [schedule] conservent le travail existant avec KEEP, tandis
     * que [updateAfterAppReplacement] applique explicitement la nouvelle spec.
     */
    private const val WORK_NAME = "meteocompare_widget_refresh"
    private const val IMMEDIATE_WORK_NAME = "meteocompare_widget_refresh_now"
    private const val WORK_TAG = "meteocompare_widget"

    /**
     * Cadence FIXE du tick d'affichage. 15 min = minimum autorisé par
     * WorkManager pour un PeriodicWorkRequest. On veut ce plancher pour que
     * le passage d'heure (14:00 → 15:00) soit reflété rapidement dans les
     * labels "14h 15h 16h 17h" → "15h 16h 17h 18h".
     */
    private const val TICK_MINUTES = 15L

    /**
     * Garantit la présence du worker périodique sans toucher à une
     * planification déjà valide. KEEP évite les annulations/recréations
     * observées à chaque démarrage de process.
     */
    fun schedule(context: Context) {
        enqueue(
            workManager = WorkManager.getInstance(context.applicationContext),
            policy = ExistingPeriodicWorkPolicy.KEEP
        )
    }

    /**
     * Applique la spécification courante après remplacement de l'APK. Cette
     * voie est volontairement séparée du démarrage normal : UPDATE peut
     * migrer une cadence ou des contraintes, mais peut aussi interrompre et
     * replanifier un travail déjà enregistré.
     */
    fun updateAfterAppReplacement(context: Context) {
        enqueue(
            workManager = WorkManager.getInstance(context.applicationContext),
            policy = ExistingPeriodicWorkPolicy.UPDATE
        )
    }

    /** Overload testable du chemin normal KEEP. */
    internal fun schedule(workManager: WorkManager) {
        enqueue(workManager, ExistingPeriodicWorkPolicy.KEEP)
    }

    /** Overload testable du chemin de migration UPDATE. */
    internal fun updateAfterAppReplacement(workManager: WorkManager) {
        enqueue(workManager, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun enqueue(
        workManager: WorkManager,
        policy: ExistingPeriodicWorkPolicy
    ) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            repeatInterval = TICK_MINUTES,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            // Une fenêtre de 5 min permet à Android de regrouper ce tick avec
            // d'autres travaux système, sans perdre la précision utile du
            // widget (les heures sont affichées par pas d'une heure).
            flexTimeInterval = 5,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    // Pas de contrainte réseau : le tick doit tourner offline
                    // pour que les labels d'heure évoluent. Le fetch réseau
                    // est short-circuité par NetworkMonitor.isOnline() en
                    // amont dans ForecastRepositoryImpl.fetchAndCache.
                    //
                    // ─── setRequiresBatteryNotLow SUPPRIMÉ ────────────────
                    // Version précédente avait `.setRequiresBatteryNotLow(true)`.
                    // Trade-off réévalué en défaveur : sur Pixel 9a comme sur
                    // les OEMs agressifs (MIUI, OneUI, EMUI), cette contrainte
                    // fait suspendre le worker DÈS que la batterie touche le
                    // seuil "low" (~15%) OU dès que le mode économie s'active,
                    // ce qui arrive fréquemment en journée. Symptôme visible :
                    // widget qui n'évolue plus (12h/13h toujours affichés à 14h).
                    //
                    // Le coût du tick est négligeable (~10 ms CPU, souvent
                    // cache-only), et le fetch réseau est de toute façon
                    // court-circuité par `maxCacheAgeMs`. Rendre le tick
                    // inconditionnel restaure la fiabilité de l'affichage à
                    // un coût battery marginal — le bon compromis.
                    .build()
            )
            // En cas de panne persistante du launcher ou de Glance, ne pas
            // réveiller le process toutes les quelques dizaines de secondes.
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.MINUTES
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            policy,
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
        triggerImmediateRefresh(WorkManager.getInstance(context.applicationContext))
    }

    /** Overload testable : voir [schedule] pour la justification. */
    internal fun triggerImmediateRefresh(workManager: WorkManager) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(workDataOf(FORCE_REFRESH_KEY to true))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.MINUTES
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Annule tout worker de rafraîchissement. Appelée quand le dernier widget
     * est retiré de l'écran d'accueil ([MeteoWidgetReceiver.onDisabled]) —
     * inutile de continuer à tick pour un widget qui n'existe plus.
     */
    fun cancel(context: Context) {
        cancel(WorkManager.getInstance(context.applicationContext))
    }

    /** Overload testable : voir [schedule] pour la justification. */
    internal fun cancel(workManager: WorkManager) {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /**
     * Nom unique du job périodique — exposé `internal` pour que les tests
     * puissent vérifier que [schedule] et [cancel] utilisent bien le même
     * (invariant sinon `cancel` ne trouve rien à annuler).
     */
    internal const val FORCE_REFRESH_KEY: String = "force_widget_refresh"

    internal const val TESTABLE_WORK_NAME: String = WORK_NAME
    internal const val TESTABLE_IMMEDIATE_WORK_NAME: String = IMMEDIATE_WORK_NAME
    internal const val TESTABLE_WORK_TAG: String = WORK_TAG
}

/**
 * Worker qui déclenche un rafraîchissement des widgets MeteoCompare posés
 * sur l'écran d'accueil.
 *
 * ─── Ce qu'il fait, ce qu'il ne fait PAS ────────────────────────────────
 * On écrit un timestamp puis on demande explicitement la mise à jour dans `RefreshTickKey` des prefs Glance de
 * chaque widget vivant. Le timestamp invalide la clé du LaunchedEffect et l'appel explicite à update()
 * recrée les RemoteViews. Ensemble, ils garantissent la lecture réactive `currentState<Preferences>()`
 * dans [MeteoWidget.provideGlance], force la recomposition, et re-déclenche
 * le `LaunchedEffect(cityId, forecastMode, refreshTick)` qui contient
 * `loadWidgetData`. Là seulement le repo décide fetch OU cache selon
 * `maxCacheAgeMs`.
 *
 * ─── Notification explicite du host ─────────────────────────────────────
 * Une écriture de state seule ne notifie pas de manière portable le host du widget.
 * La documentation Glance demande un appel à `update()` afin de recréer et
 * transmettre les RemoteViews. Le worker effectue donc exactement une mise à
 * jour explicite par widget vivant.
 *
 * ─── IDs vivants comme source de vérité ─────────────────────────────────
 * Le worker part des AppWidgetIds renvoyés par le launcher puis résout leur
 * GlanceId. Cette direction évite les IDs Glance orphelins et couvre les
 * widgets restaurés que l'index interne Glance n'aurait pas encore listés.
 */
internal class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = RUN_MUTEX.withLock {
        val ctx = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(ctx)

        // AppWidgetManager est la source de vérité côté launcher. Construire
        // les GlanceId depuis les IDs réellement vivants évite à la fois les
        // DataStore orphelines et le cas inverse où getGlanceIds() n'a pas
        // encore resynchronisé un widget restauré par le système.
        var receiverLookupFailures = 0
        val liveWidgetIds = WidgetReceivers.liveWidgetIdsWith { receiverClass ->
            runCatching {
                appWidgetManager
                    .getAppWidgetIds(ComponentName(ctx, receiverClass))
                    .toList()
            }.getOrElse { error ->
                receiverLookupFailures++
                android.util.Log.w(
                    WIDGET_LOG_TAG,
                    "Unable to query ${receiverClass.simpleName}",
                    error
                )
                emptyList()
            }
        }

        if (liveWidgetIds.isEmpty()) {
            // Si toutes les interrogations launcher ont échoué, ne pas conclure
            // à tort qu'il n'existe aucun widget : demander un retry.
            return@withLock if (receiverLookupFailures == WidgetReceivers.All.size) {
                Result.retry()
            } else {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(WIDGET_LOG_TAG, "No live widgets to refresh")
                }
                Result.success()
            }
        }

        val glanceManager = GlanceAppWidgetManager(ctx)
        val now = System.currentTimeMillis()
        val forceRefresh = inputData.getBoolean(
            WidgetRefreshScheduler.FORCE_REFRESH_KEY,
            false
        )
        val refreshInterval = runSuspendCatching {
            EntryPointAccessors.fromApplication(ctx, WidgetEntryPoint::class.java)
                .userPreferencesRepository()
                .observeRefreshInterval()
                .first()
        }.getOrDefault(RefreshInterval.DEFAULT)

        var updatedCount = 0
        var skippedCount = 0
        var failedCount = 0

        val completedWithinBudget = withTimeoutOrNull(WORK_BUDGET_MS) {
            for (appWidgetId in liveWidgetIds) {
                val updateResult = withTimeoutOrNull(PER_WIDGET_TIMEOUT_MS) {
                    runSuspendCatching {
                        val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                        val prefs = getAppWidgetState(
                            context = ctx,
                            definition = PreferencesGlanceStateDefinition,
                            glanceId = glanceId
                        )
                        val lastDispatch = prefs[WidgetPreferences.LastDispatchAtKey] ?: 0L

                        if (!isWidgetDispatchDue(
                                lastDispatchAtMs = lastDispatch,
                                nowMs = now,
                                interval = refreshInterval,
                                force = forceRefresh
                            )
                        ) {
                            return@runSuspendCatching false
                        }

                        updateAppWidgetState(
                            context = ctx,
                            definition = PreferencesGlanceStateDefinition,
                            glanceId = glanceId
                        ) { current ->
                            current.toMutablePreferences().apply {
                                this[WidgetPreferences.RefreshTickKey] = now
                                this[WidgetPreferences.LastDispatchAtKey] = now
                            }
                        }

                        // Une écriture DataStore ne suffit pas à prévenir le host du
                        // widget. Glance doit recréer puis renvoyer les RemoteViews.
                        // Sans cet appel explicite certains launchers restent figés.
                        try {
                            val providerClassName = appWidgetManager
                                .getAppWidgetInfo(appWidgetId)
                                ?.provider
                                ?.className
                            glanceWidgetForProviderClassName(providerClassName)
                                .update(ctx, glanceId)
                        } catch (error: Throwable) {
                            // Le tick a déjà été écrit pour déclencher la composition.
                            // Si l'envoi RemoteViews échoue, restaurer uniquement le
                            // marqueur de cadence afin que le retry ne soit pas filtré.
                            updateAppWidgetState(
                                context = ctx,
                                definition = PreferencesGlanceStateDefinition,
                                glanceId = glanceId
                            ) { current ->
                                current.toMutablePreferences().apply {
                                    this[WidgetPreferences.LastDispatchAtKey] = lastDispatch
                                }
                            }
                            throw error
                        }
                        true
                    }
                }

                when {
                    updateResult?.isSuccess == true && updateResult.getOrNull() == true -> {
                        updatedCount++
                    }
                    updateResult?.isSuccess == true -> {
                        skippedCount++
                    }
                    else -> {
                        failedCount++
                        android.util.Log.w(
                            WIDGET_LOG_TAG,
                            "Widget refresh failed for appWidgetId=$appWidgetId",
                            updateResult?.exceptionOrNull()
                                ?: TimeoutException("Widget update exceeded ${PER_WIDGET_TIMEOUT_MS}ms")
                        )
                    }
                }
            }
            true
        } ?: false

        if (!completedWithinBudget) {
            android.util.Log.w(
                WIDGET_LOG_TAG,
                "Widget refresh exceeded global ${WORK_BUDGET_MS}ms budget"
            )
            return@withLock Result.retry()
        }

        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                WIDGET_LOG_TAG,
                "Widget refresh completed: live=${liveWidgetIds.size}, " +
                    "updated=$updatedCount, skipped=$skippedCount, failed=$failedCount"
            )
        }

        // Une panne isolée ne doit pas refaire tout le lot. En revanche, si
        // aucun widget vivant n'a pu être actualisé, demander un retry avec
        // le backoff WorkManager améliore la récupération après un problème
        // transitoire du launcher, de Glance ou du stockage.
        if (updatedCount == 0 && failedCount > 0) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        // Le periodic et un refresh immédiat ont des noms WorkManager distincts
        // et peuvent donc se chevaucher. Un seul worker process-wide évite deux
        // écritures Glance/RemoteViews concurrentes ; le cache réseau reste de
        // toute façon coalescé par ForecastRepositoryImpl.
        private val RUN_MUTEX = Mutex()

        internal const val PER_WIDGET_TIMEOUT_MS: Long = 45_000L
        internal const val WORK_BUDGET_MS: Long = 8 * 60_000L
    }
}
