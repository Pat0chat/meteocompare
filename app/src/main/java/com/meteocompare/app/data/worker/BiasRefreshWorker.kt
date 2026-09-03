package com.meteocompare.app.data.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.BiasVariable
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Rafraîchissement quotidien des données de suivi de biais.
 *
 * Tous les cycles utilisent la même source à échéance fixe :
 *   1. **Prévisions J+1…J+7** — le cycle quotidien recharge un delta de 3 jours
 *      depuis Previous Runs ; le cycle manuel initialise jusqu'à 21 jours.
 *      Les scores ne mélangent donc jamais des horizons dépendant de l'heure
 *      d'ouverture de l'application.
 *   2. **Références historiques** — l'archive Open-Meteo complète les jours
 *      vérifiables manquants pour les trois variables.
 *   3. **Housekeeping** — purge des samples au-delà de 35 jours et du cache
 *      forecast au-delà de 7 jours.
 *
 * Une fois par jour, avec réseau disponible et batterie non faible. Les
 * opérations sont idempotentes et bornées par des timeouts.
 */

object BiasRefreshScheduler {

    private const val WORK_NAME = "meteocompare_bias_refresh"
    private const val KICKOFF_WORK_NAME = "meteocompare_bias_refresh_kickoff"
    private const val MANUAL_WORK_NAME = "meteocompare_bias_refresh_manual"
    private const val WORK_TAG = "meteocompare_bias"

    /**
     * Planifie le worker. Deux requests enqueue'd :
     *
     * 1. **PeriodicWorkRequest** (cadence 24h, flex 6h) — le rythme de croisière.
     *    Le démarrage normal utilise [ExistingPeriodicWorkPolicy.KEEP] pour ne
     *    pas interrompre/replanifier un travail déjà valide. UPDATE est réservé
     *    au remplacement de l'APK via [updateAfterAppReplacement].
     *
     * 2. **OneTimeWorkRequest** (kickoff conditionnel) — pour que la première
     *    référence devenue vérifiable n'attende pas la fenêtre du periodic
     *    (potentiellement jusqu'à 24h). Un bref délai initial laisse le premier
     *    rendu de l'application terminer avant le housekeeping. [ExistingWorkPolicy.KEEP]
     *    assure l'idempotence :
     *    Le scheduler consulte d'abord un garde local de fraîcheur de 20 h ;
     *    il n'enqueue donc rien pendant cette période. KEEP déduplique aussi
     *    un éventuel kickoff déjà en attente ou actif.
     *    Le periodic quotidien n'est jamais filtré par ce garde.
     *
     * À appeler depuis [com.meteocompare.app.MeteoCompareApplication.onCreate].
     */
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        enqueue(
            workManager = WorkManager.getInstance(appContext),
            periodicPolicy = ExistingPeriodicWorkPolicy.KEEP,
            enqueueKickoff = BiasRefreshRunGate.shouldRun(
                appContext,
                BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS
            )
        )
    }

    /**
     * Met à niveau la spécification du worker après remplacement de l'APK.
     * Cette opération est séparée du démarrage normal pour éviter le bruit et
     * les interruptions WorkManager inutiles.
     */
    fun updateAfterAppReplacement(context: Context) {
        val appContext = context.applicationContext
        enqueue(
            workManager = WorkManager.getInstance(appContext),
            periodicPolicy = ExistingPeriodicWorkPolicy.UPDATE,
            enqueueKickoff = BiasRefreshRunGate.shouldRun(
                appContext,
                BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS
            )
        )
    }

    /**
     * Lance un cycle exceptionnel demandé explicitement depuis les réglages.
     *
     * Le travail garde les mêmes contraintes et le même mutex que les cycles
     * automatiques. Il contourne uniquement la garde temporelle de 20 h afin
     * que l'utilisateur puisse initialiser les biais après avoir ajouté ses
     * premières villes. KEEP déduplique les taps pendant que le travail est
     * en attente ou actif. Une nouvelle action explicite reste possible après
     * la fin du cycle, même si un cycle automatique récent a réussi.
     */
    fun triggerManualRefresh(context: Context) {
        triggerManualRefresh(
            WorkManager.getInstance(context.applicationContext)
        )
    }

    /** Overload testable du déclenchement manuel. */
    internal fun triggerManualRefresh(workManager: WorkManager) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val manual = OneTimeWorkRequestBuilder<BiasRefreshWorker>()
            .setInputData(workDataOf(MANUAL_INPUT_KEY to true))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.HOURS
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            manual
        )
    }

    /** Overload testable du chemin normal KEEP. */
    internal fun schedule(workManager: WorkManager) {
        enqueue(workManager, ExistingPeriodicWorkPolicy.KEEP, enqueueKickoff = true)
    }

    /** Overload testable du chemin de migration UPDATE. */
    internal fun updateAfterAppReplacement(workManager: WorkManager) {
        enqueue(workManager, ExistingPeriodicWorkPolicy.UPDATE, enqueueKickoff = true)
    }

    private fun enqueue(
        workManager: WorkManager,
        periodicPolicy: ExistingPeriodicWorkPolicy,
        enqueueKickoff: Boolean
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Croisière quotidienne.
        val periodic = PeriodicWorkRequestBuilder<BiasRefreshWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
            // Fenêtre flexible de 6 h : WorkManager choisit l'exécution dans
            // cette fenêtre selon les contraintes et les opportunités de
            // regroupement. Le traitement rétrospectif n'est pas urgent.
            flexTimeInterval = 6, flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.HOURS
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            periodicPolicy,
            periodic
        )

        if (enqueueKickoff) {
            // Kickoff différé brièvement pour ne pas concurrencer le premier
            // rendu Compose, tout en restant très loin de l'attente potentielle
            // de 24 h du periodic. Le worker applique ensuite le garde de 20 h.
            val kickoff = OneTimeWorkRequestBuilder<BiasRefreshWorker>()
                .setInputData(workDataOf(KICKOFF_INPUT_KEY to true))
                .setInitialDelay(KICKOFF_INITIAL_DELAY_MS, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.HOURS
                )
                .addTag(WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                KICKOFF_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                kickoff
            )
        }
    }

    /**
     * Purge budget : 35 jours de rétention (30 jours de fenêtre + 5 jours de
     * marge pour couvrir un éventuel décalage de fetch). Extrait `internal`
     * pour être testable et évoluer sans toucher au worker.
     */
    internal const val KICKOFF_INPUT_KEY: String = "bias_refresh_kickoff"
    internal const val MANUAL_INPUT_KEY: String = "bias_refresh_manual"

    internal const val KICKOFF_INITIAL_DELAY_MS: Long = 60_000L
    internal const val MANUAL_LOOKBACK_DAYS: Int = 21
    internal const val AUTOMATIC_LOOKBACK_DAYS: Int = 3

    internal fun historyLookbackDays(isManual: Boolean): Int =
        if (isManual) MANUAL_LOOKBACK_DAYS else AUTOMATIC_LOOKBACK_DAYS

    internal const val PER_CITY_OPERATION_TIMEOUT_MS: Long = 75_000L

    /**
     * Budget global inférieur à la fenêtre d'exécution habituelle d'un worker.
     * Les opérations sont idempotentes : si le budget expire, WorkManager
     * reprend le cycle avec backoff sans laisser un job monopoliser le process.
     */
    internal const val WORK_BUDGET_MS: Long = 8 * 60_000L

    internal const val RETENTION_DAYS: Long = 35

    /**
     * Purge budget du cache forecast : 7 jours.
     *
     * Rationale du chiffre : au-delà de 7 jours d'absence d'ouverture d'app,
     * l'utilisateur qui revient attend une prévision fraîche, pas la
     * "dernière connue". Le cache ne sert plus qu'à masquer la latence du
     * fetch en cours OU à couvrir un cas offline immédiat (~24h max en
     * pratique). 7 jours est le plafond où l'utilité de garder chaque
     * ligne devient franchement négative — plus longtemps ne rend PAS
     * l'app plus utile, mais grossit la DB.
     *
     * Aligné sur la doc historique du DAO
     * ([com.meteocompare.app.data.local.ForecastCacheDao.deleteOlderThan]).
     */
    internal const val FORECAST_CACHE_RETENTION_DAYS: Long = 7
}

/**
 * Worker qui exécute le cycle de rafraîchissement biais. Utilise Hilt via
 * [BiasRefreshEntryPoint] plutôt qu'`@HiltWorker` — voir le KDoc de l'entry
 * point pour le rationnel.
 */
internal class BiasRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = RUN_MUTEX.withLock {
        val ctx = applicationContext

        // Défense en profondeur : le scheduler filtre déjà les kickoffs récents,
        // mais un travail resté en file ou restauré par WorkManager peut encore
        // démarrer. Ce garde évite alors tout accès Hilt/Room/réseau pendant 20 h.
        val isKickoff = inputData.getBoolean(
            BiasRefreshScheduler.KICKOFF_INPUT_KEY,
            false
        )
        val isManual = inputData.getBoolean(
            BiasRefreshScheduler.MANUAL_INPUT_KEY,
            false
        )

        // Une action explicite de l'utilisateur ne doit jamais être bloquée par
        // le timestamp d'un cycle automatique : le cycle précédent a pu ne faire
        // que du housekeeping, alors que le bouton doit initialiser l'historique
        // J+1…J+7 via Previous Runs. ExistingWorkPolicy.KEEP déduplique déjà les taps
        // tant que le travail manuel est en attente ou actif.
        if (!isManual) {
            val minIntervalMs = if (isKickoff) {
                BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS
            } else {
                // Le periodic reste quotidien. Ce garde court ne sert qu'à éviter
                // un doublon si un kickoff vient de réussir juste avant sa fenêtre.
                BiasRefreshRunGate.PERIODIC_MIN_INTERVAL_MS
            }
            if (!BiasRefreshRunGate.shouldRun(ctx, minIntervalMs)) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        LOG_TAG,
                        if (isKickoff) {
                            "Bias kickoff skipped: a successful daily cycle is still fresh"
                        } else {
                            "Bias periodic skipped: a kickoff completed recently"
                        }
                    )
                }
                return@withLock Result.success()
            }
        }

        val entry = EntryPointAccessors.fromApplication(ctx, BiasRefreshEntryPoint::class.java)

        val cityRepo = entry.cityRepository()
        val biasRepo = entry.biasSampleRepository()
        val fetchObs = entry.fetchBiasObservationsUseCase()
        val bootstrapHistory = entry.bootstrapBiasHistoryUseCase()
        val clock = entry.clock()

        // Snapshot one-shot des favorites + modèles activés — pas besoin
        // d'écouter les Flow (le worker ne vit que le temps d'un cycle).
        val favorites = runSuspendCatching { cityRepo.observeFavorites().first() }
            .getOrElse { return@withLock Result.retry() }

        // Un cycle vide peut tout de même exécuter le housekeeping, mais il ne
        // sera pas marqué comme cycle de collecte réussi. Sinon un kickoff lancé
        // avant l'ajout de la première ville bloquerait les prochains kickoffs
        // pendant 20 h alors qu'aucune donnée de biais n'a été collectée.
        if (favorites.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "Bias refresh has no favorite city; housekeeping only")
            }
            if (isManual) return@withLock Result.failure()
        }

        val enabledModels = if (favorites.isEmpty()) {
            emptyList()
        } else {
            runSuspendCatching {
                entry.userPreferencesRepository().observeEnabledModels().first()
            }.getOrElse { error ->
                Log.w(LOG_TAG, "Cannot read enabled models for bias refresh", error)
                return@withLock Result.retry()
            }
        }
        if (isManual && enabledModels.isEmpty()) {
            Log.w(LOG_TAG, "Manual bias bootstrap aborted: no enabled model")
            return@withLock Result.failure()
        }

        // Tous les cycles rechargent des prévisions Previous Runs J+1…J+7 avant
        // leurs références. Le manuel prend 21 jours ; le quotidien se limite
        // à 3 jours pour rester idempotent et peu coûteux.
        val citySuccesses = withTimeoutOrNull(BiasRefreshScheduler.WORK_BUDGET_MS) {
            var successes = 0
            for (city in favorites) {
                val result = withTimeoutOrNull(
                    BiasRefreshScheduler.PER_CITY_OPERATION_TIMEOUT_MS
                ) {
                    runSuspendCatching {
                        val bootstrap = if (enabledModels.isEmpty()) {
                            null
                        } else {
                            bootstrapHistory(
                                city = city,
                                models = enabledModels,
                                requestedDays = BiasRefreshScheduler.historyLookbackDays(isManual)
                            )
                        }
                        if (bootstrap != null && !bootstrap.hasUsableData) {
                            error("Previous Runs returned no usable J+1…J+7 sample for city=${city.id}")
                        }
                        val referenceDays = fetchObs(city)
                        if (BuildConfig.DEBUG) {
                            if (bootstrap != null) {
                                Log.d(
                                    LOG_TAG,
                                    (if (isManual) "Manual bias bootstrap" else "Daily fixed-lead refresh") +
                                        " city=${city.id}: " +
                                        "models=${bootstrap.coveredModels}/${enabledModels.size}, " +
                                        "days=${bootstrap.coveredDays}/${bootstrap.requestedDays}, " +
                                        "forecastRecords=${bootstrap.forecastRecords}, " +
                                        "referenceDays=$referenceDays" +
                                        if (isManual) {
                                            ", forecastReady=" +
                                                "T${bootstrap.forecastReadyModels(BiasVariable.TEMPERATURE)}/" +
                                                "P${bootstrap.forecastReadyModels(BiasVariable.PRECIPITATION)}/" +
                                                "W${bootstrap.forecastReadyModels(BiasVariable.WIND_SPEED)}, " +
                                                "coverage=" + bootstrap.coverageByModel.entries
                                                    .sortedBy { it.key.name }
                                                    .joinToString(";") { (model, counts) ->
                                                        "${model.name}:" +
                                                            "T${counts[BiasVariable.TEMPERATURE] ?: 0}/" +
                                                            "P${counts[BiasVariable.PRECIPITATION] ?: 0}/" +
                                                            "W${counts[BiasVariable.WIND_SPEED] ?: 0}"
                                                    }
                                        } else {
                                            ""
                                        }
                                )
                            } else {
                                Log.d(
                                    LOG_TAG,
                                    "Bias reference refresh city=${city.id}: referenceDays=$referenceDays"
                                )
                            }
                        }
                        bootstrap to referenceDays
                    }
                }
                when {
                    result?.isSuccess == true -> successes++
                    result == null -> Log.w(
                        LOG_TAG,
                        "Bias refresh timed out for city=${city.id}"
                    )
                    else -> Log.w(
                        LOG_TAG,
                        "Bias refresh failed for city=${city.id}",
                        result.exceptionOrNull()
                    )
                }
            }
            successes
        } ?: run {
            Log.w(LOG_TAG, "Bias refresh exceeded global work budget")
            return@withLock Result.retry()
        }

        // Si toutes les villes ont échoué, signaler retry plutôt qu'un faux
        // SUCCESS. WorkManager appliquera son backoff sans boucle active.
        if (favorites.isNotEmpty() && citySuccesses == 0) {
            return@withLock Result.retry()
        }

        // Housekeeping bias : purge les samples > RETENTION_DAYS.
        runSuspendCatching {
            biasRepo.purgeOlderThan(
                clock.instant().atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    .minusDays(BiasRefreshScheduler.RETENTION_DAYS)
            )
        }.onFailure { Log.w(LOG_TAG, "Bias sample cleanup failed", it) }

        // Housekeeping forecast cache : purge les entrées > FORECAST_CACHE_RETENTION_DAYS.
        // Protégé séparément pour rester indépendant de la purge du biais :
        // un échec de l'un ne doit pas empêcher l'autre. Le DAO utilise un
        // cutoff en epoch millis alors que biasRepo utilise LocalDate, d'où
        // la conversion inline (Instant.now().minus(N days).toEpochMilli()).
        //
        // Coût : une seule requête DELETE sur une table naturellement bornée
        // à une ligne par couple (ville, modèle). Le volume reste faible ; un
        // index supplémentaire ne justifierait pas une migration destructive.
        runSuspendCatching {
            val cutoffMs = clock.instant()
                .minus(BiasRefreshScheduler.FORECAST_CACHE_RETENTION_DAYS, java.time.temporal.ChronoUnit.DAYS)
                .toEpochMilli()
            entry.forecastCacheDao().deleteOlderThan(cutoffMs)
        }.onFailure { Log.w(LOG_TAG, "Forecast cache cleanup failed", it) }

        if (favorites.isNotEmpty()) {
            BiasRefreshRunGate.markSuccess(ctx)
        }
        Result.success()
    }

    companion object {
        private const val LOG_TAG = "MeteoCompare/BiasWorker"
        private val RUN_MUTEX = Mutex()
    }
}
