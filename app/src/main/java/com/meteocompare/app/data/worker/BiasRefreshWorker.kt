package com.meteocompare.app.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Rafraîchissement quotidien des données de suivi de biais.
 *
 * Deux responsabilités par cycle :
 *   1. **Fetch delta des observations** — pour chaque ville favorite, appeler
 *      [com.meteocompare.app.domain.usecase.FetchBiasObservationsUseCase]
 *      qui interroge Open-Meteo archive et remplit `observation_samples`
 *      pour les jours manquants depuis le dernier fetch.
 *   2. **Housekeeping** — purger les samples au-delà de 35 jours pour maintenir
 *      la DB dans un budget contrôlé.
 *
 * ## Ce qui n'est PAS fait ici
 *
 * Le snapshot des prévisions courantes n'est PAS géré par ce worker. Il est
 * piggybacké sur les fetch utilisateur (voir
 * [com.meteocompare.app.domain.usecase.SnapshotForecastUseCase]) — chaque fois
 * qu'un utilisateur ouvre une ville ou refresh, le forecast frais est
 * automatiquement snapshotté. Le worker ne s'occupe QUE des observations
 * rétrospectives et du purge.
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
 */
object BiasRefreshScheduler {

    private const val WORK_NAME = "meteocompare_bias_refresh"
    private const val KICKOFF_WORK_NAME = "meteocompare_bias_refresh_kickoff"

    /**
     * Planifie le worker. Deux requests enqueue'd :
     *
     * 1. **PeriodicWorkRequest** (cadence 24h, flex 6h) — le rythme de croisière.
     *    [ExistingPeriodicWorkPolicy.KEEP] : re-schedule à chaque démarrage
     *    n'écrase pas un job déjà planifié, seul le premier appel "compte".
     *
     * 2. **OneTimeWorkRequest** (kickoff immédiat) — pour que le premier fetch
     *    d'observations n'attende pas la première fenêtre système du periodic
     *    (potentiellement jusqu'à 24h). [ExistingWorkPolicy.KEEP] pour l'idempotence :
     *    à chaque démarrage on essaie d'enqueue, mais si un kickoff est déjà en
     *    cours ou terminé récemment WorkManager ignore le doublon. Utile à
     *    l'installation ET aux redémarrages qui suivent une longue période
     *    d'inactivité.
     *
     * À appeler depuis [com.meteocompare.app.MeteoCompareApplication.onCreate].
     */
    fun schedule(context: Context) {
        schedule(WorkManager.getInstance(context.applicationContext))
    }

    /** Overload testable — permet d'injecter un WorkManager mocké. */
    internal fun schedule(workManager: WorkManager) {
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
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        // Kickoff immédiat pour ne pas attendre 24h avant le premier cycle.
        // Le worker est idempotent (backfill vérifie d'abord si des données
        // existent déjà, delta observation skip si latest = yesterday) donc
        // relancer plus souvent que nécessaire ne coûte rien de plus qu'un
        // check DB local.
        val kickoff = OneTimeWorkRequestBuilder<BiasRefreshWorker>()
            .setConstraints(constraints)
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
    internal const val RETENTION_DAYS: Long = 35
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

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val entry = EntryPointAccessors.fromApplication(ctx, BiasRefreshEntryPoint::class.java)

        val cityRepo = entry.cityRepository()
        val biasRepo = entry.biasSampleRepository()
        val userPrefs = entry.userPreferencesRepository()
        val fetchObs = entry.fetchBiasObservationsUseCase()
        val backfill = entry.backfillHistoricalForecastUseCase()

        // Snapshot one-shot des favorites + modèles activés — pas besoin
        // d'écouter les Flow (le worker ne vit que le temps d'un cycle).
        val favorites = runCatching { cityRepo.observeFavorites().first() }
            .getOrElse { return Result.retry() }
        val enabledModels = runCatching { userPrefs.observeEnabledModels().first() }
            .getOrDefault(emptyList())

        // Backfill historical-forecast d'abord — le use case est idempotent
        // (skip si des rows passées existent déjà) donc l'appeler à chaque
        // cycle est safe. Une ville en échec (timeout, 5xx) est skippée sans
        // faire échouer le cycle global. Coût no-op = 1 SELECT COUNT local.
        for (city in favorites) {
            runCatching { backfill(city, enabledModels) }.getOrNull()
        }

        // Fetch delta observations pour chaque ville, tolérant aux erreurs
        // individuelles (idem : une ville qui rate ne bloque pas les autres).
        for (city in favorites) {
            runCatching { fetchObs(city) }.getOrNull()
        }

        // Housekeeping : purge les samples > RETENTION_DAYS.
        runCatching {
            biasRepo.purgeOlderThan(
                LocalDate.now().minusDays(BiasRefreshScheduler.RETENTION_DAYS)
            )
        }

        return Result.success()
    }
}
