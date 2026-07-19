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
import com.meteocompare.app.core.util.runSuspendCatching
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Rafraîchissement quotidien des données de suivi de biais.
 *
 * Trois responsabilités par cycle :
 *   1. **Fetch delta des observations** — pour chaque ville favorite, appeler
 *      [com.meteocompare.app.domain.usecase.FetchBiasObservationsUseCase]
 *      qui interroge Open-Meteo archive et remplit `observation_samples`
 *      pour les jours manquants depuis le dernier fetch.
 *   2. **Housekeeping bias** — purger les samples au-delà de 35 jours pour
 *      maintenir la DB dans un budget contrôlé (fenêtre glissante 30j +
 *      5j de marge).
 *   3. **Housekeeping forecast cache** — purger les entrées de `forecast_cache`
 *      au-delà de 7 jours. Ce cache sert de fallback offline "dernière
 *      donnée connue" ; au-delà d'une semaine c'est de la donnée périmée
 *      qui n'a plus d'utilité même pour l'affichage offline (l'utilisateur
 *      qui rouvre l'app après 7 jours attend une prévision fraîche).
 *
 * ## Ce qui n'est PAS fait ici
 *
 * Le snapshot des prévisions courantes n'est PAS géré par ce worker. Il est
 * piggybacké sur les fetch utilisateur (voir
 * [com.meteocompare.app.domain.usecase.SnapshotForecastUseCase]) — chaque fois
 * qu'un utilisateur ouvre une ville ou refresh, le forecast frais est
 * automatiquement snapshotté. Le worker ne s'occupe QUE des observations
 * rétrospectives et des purges.
 *
 * Les `climate_normals_cache` ne sont PAS purgés — c'est un dataset stable
 * de ~366 rows par ville (moyennes 10 ans par jour de l'année), qui ne
 * change qu'aux migrations rares côté Open-Meteo. La croissance est bornée
 * par le nombre de villes favorites, aucun besoin de nettoyage.
 *
 * ## Cadence
 *
 * Une fois par jour, contrainte `NetworkType.CONNECTED`. Ne tourne pas si
 * pas de réseau — WorkManager retentera automatiquement à la prochaine
 * connexion (contrainte satisfaite → job dispatché).
 *
 * `flexTimeInterval = 6h` : WorkManager peut décaler le job de ±6h autour
 * de son slot quotidien pour regrouper avec d'autres jobs système et
 * économiser la batterie. Pour du fetch d'archive météo qui n'a aucune
 * urgence horaire, c'est optimal.
 *
 * ## Politique d'erreurs
 *
 * Une erreur sur UNE ville ne fait pas échouer le worker global — on skip
 * et on continue. La ville sera retentée au cycle suivant. Une erreur
 * catastrophique (repo inaccessible) → `Result.retry` pour laisser
 * WorkManager retenter avec backoff exponentiel.
 *
 * Les deux purges (biais + cache prévisionnel) sont protégées séparément :
 * un échec de l'une ne bloque pas l'autre, et le worker
 * retourne toujours SUCCESS après ces étapes (les fetches ont réussi, le
 * cleanup est du bonus).
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
     * 2. **OneTimeWorkRequest** (kickoff immédiat) — pour que le premier fetch
     *    d'observations n'attende pas la première fenêtre système du periodic
     *    (potentiellement jusqu'à 24h). [ExistingWorkPolicy.KEEP] pour l'idempotence :
     *    à chaque démarrage on essaie d'enqueue, mais si un kickoff est déjà en
     *    cours WorkManager ignore le doublon. Une fois terminé, le worker
     *    applique en plus un garde de fraîcheur de 20 h uniquement aux
     *    kickoffs, afin qu'une ouverture d'app ne relance pas le réseau.
     *    Le periodic quotidien n'est jamais filtré par ce garde.
     *
     * À appeler depuis [com.meteocompare.app.MeteoCompareApplication.onCreate].
     */
    fun schedule(context: Context) {
        enqueue(
            workManager = WorkManager.getInstance(context.applicationContext),
            periodicPolicy = ExistingPeriodicWorkPolicy.KEEP
        )
    }

    /**
     * Met à niveau la spécification du worker après remplacement de l'APK.
     * Cette opération est séparée du démarrage normal pour éviter le bruit et
     * les interruptions WorkManager inutiles.
     */
    fun updateAfterAppReplacement(context: Context) {
        enqueue(
            workManager = WorkManager.getInstance(context.applicationContext),
            periodicPolicy = ExistingPeriodicWorkPolicy.UPDATE
        )
    }

    /**
     * Lance un cycle exceptionnel demandé explicitement depuis les réglages.
     *
     * Le travail garde les mêmes contraintes et le même mutex que les cycles
     * automatiques. Il contourne uniquement la garde temporelle de 20 h afin
     * que l'utilisateur puisse initialiser les biais après avoir ajouté ses
     * premières villes. KEEP déduplique les taps pendant que le travail est
     * en attente ou actif ; le worker applique ensuite un anti-répétition de
     * 30 minutes après un cycle réussi.
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
        enqueue(workManager, ExistingPeriodicWorkPolicy.KEEP)
    }

    /** Overload testable du chemin de migration UPDATE. */
    internal fun updateAfterAppReplacement(workManager: WorkManager) {
        enqueue(workManager, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun enqueue(
        workManager: WorkManager,
        periodicPolicy: ExistingPeriodicWorkPolicy
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Croisière quotidienne.
        val periodic = PeriodicWorkRequestBuilder<BiasRefreshWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
            // Flex 6h → WorkManager peut décaler ±6h autour du slot pour
            // regrouper avec d'autres jobs système. Zéro contrainte de
            // ponctualité pour du fetch météo rétrospectif.
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

        // Kickoff immédiat pour ne pas attendre 24h avant le premier cycle.
        // Le worker applique un garde de 20 h aux kickoffs terminés. Le
        // premier lancement reste immédiat, mais les ouvertures suivantes ne
        // dépassent pas une lecture de préférence locale.
        val kickoff = OneTimeWorkRequestBuilder<BiasRefreshWorker>()
            .setInputData(workDataOf(KICKOFF_INPUT_KEY to true))
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

    /**
     * Purge budget : 35 jours de rétention (30 jours de fenêtre + 5 jours de
     * marge pour couvrir un éventuel décalage de fetch). Extrait `internal`
     * pour être testable et évoluer sans toucher au worker.
     */
    internal const val KICKOFF_INPUT_KEY: String = "bias_refresh_kickoff"
    internal const val MANUAL_INPUT_KEY: String = "bias_refresh_manual"

    internal const val PER_CITY_OPERATION_TIMEOUT_MS: Long = 45_000L

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

        // schedule() tente volontairement de garantir un kickoff à chaque
        // création de process. Une fois ce kickoff terminé, KEEP n'empêche pas
        // un nouveau one-shot lors d'une ouverture ultérieure. Ce garde léger
        // évite alors tout accès Hilt/Room/réseau pendant 20 h.
        val isKickoff = inputData.getBoolean(
            BiasRefreshScheduler.KICKOFF_INPUT_KEY,
            false
        )
        val isManual = inputData.getBoolean(
            BiasRefreshScheduler.MANUAL_INPUT_KEY,
            false
        )

        // Une demande manuelle contourne la garde longue de 20 h, mais pas
        // l'anti-répétition court : après un cycle réussi, relancer le même
        // backfill quelques secondes plus tard ne peut produire aucune donnée
        // supplémentaire et gaspillerait réseau et batterie. KEEP couvre les
        // taps pendant ENQUEUED/RUNNING ; ce garde couvre les taps après SUCCESS.
        val minIntervalMs = when {
            isManual -> BiasRefreshRunGate.MANUAL_MIN_INTERVAL_MS
            isKickoff -> BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS
            else -> {
                // Le periodic reste quotidien. Ce garde court ne sert qu'à éviter
                // un doublon si un kickoff vient de réussir juste avant sa fenêtre.
                BiasRefreshRunGate.PERIODIC_MIN_INTERVAL_MS
            }
        }
        if (!BiasRefreshRunGate.shouldRun(ctx, minIntervalMs)) {
            Log.d(
                LOG_TAG,
                when {
                    isManual -> "Bias manual refresh skipped: a successful cycle is still fresh"
                    isKickoff -> "Bias kickoff skipped: a successful daily cycle is still fresh"
                    else -> "Bias periodic skipped: a kickoff completed recently"
                }
            )
            return@withLock Result.success()
        }

        val entry = EntryPointAccessors.fromApplication(ctx, BiasRefreshEntryPoint::class.java)

        val cityRepo = entry.cityRepository()
        val biasRepo = entry.biasSampleRepository()
        val userPrefs = entry.userPreferencesRepository()
        val fetchObs = entry.fetchBiasObservationsUseCase()
        val backfill = entry.backfillHistoricalForecastUseCase()

        // Snapshot one-shot des favorites + modèles activés — pas besoin
        // d'écouter les Flow (le worker ne vit que le temps d'un cycle).
        val favorites = runSuspendCatching { cityRepo.observeFavorites().first() }
            .getOrElse { return@withLock Result.retry() }

        // Un cycle vide peut tout de même exécuter le housekeeping, mais il ne
        // sera pas marqué comme cycle de collecte réussi. Sinon un kickoff lancé
        // avant l'ajout de la première ville bloquerait les prochains kickoffs
        // pendant 20 h alors qu'aucune donnée de biais n'a été collectée.
        if (favorites.isEmpty()) {
            Log.d(LOG_TAG, "Bias refresh has no favorite city; housekeeping only")
        }

        val enabledModels = runSuspendCatching { userPrefs.observeEnabledModels().first() }
            .getOrDefault(emptyList())

        // Chaque opération réseau ET le cycle global sont bornés.
        // `withTimeoutOrNull` distingue le timeout local d'une vraie annulation
        // du worker : une annulation externe continue de se propager, tandis
        // qu'un serveur lent ne laisse pas un job monopoliser le process.
        val observationSuccesses = withTimeoutOrNull(BiasRefreshScheduler.WORK_BUDGET_MS) {
            for (city in favorites) {
                val result = withTimeoutOrNull(
                    BiasRefreshScheduler.PER_CITY_OPERATION_TIMEOUT_MS
                ) {
                    runSuspendCatching { backfill(city, enabledModels) }
                }
                when {
                    result == null -> Log.w(
                        LOG_TAG,
                        "Historical forecast backfill timed out for city=${city.id}"
                    )
                    result.isFailure -> Log.w(
                        LOG_TAG,
                        "Historical forecast backfill failed for city=${city.id}",
                        result.exceptionOrNull()
                    )
                }
            }

            var successes = 0
            for (city in favorites) {
                val result = withTimeoutOrNull(
                    BiasRefreshScheduler.PER_CITY_OPERATION_TIMEOUT_MS
                ) {
                    runSuspendCatching { fetchObs(city) }
                }
                when {
                    result?.isSuccess == true -> successes++
                    result == null -> Log.w(
                        LOG_TAG,
                        "Observation refresh timed out for city=${city.id}"
                    )
                    else -> Log.w(
                        LOG_TAG,
                        "Observation refresh failed for city=${city.id}",
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
        if (favorites.isNotEmpty() && observationSuccesses == 0) {
            return@withLock Result.retry()
        }

        // Housekeeping bias : purge les samples > RETENTION_DAYS.
        runSuspendCatching {
            biasRepo.purgeOlderThan(
                LocalDate.now().minusDays(BiasRefreshScheduler.RETENTION_DAYS)
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
            val cutoffMs = java.time.Instant.now()
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
