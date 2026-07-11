package com.meteocompare.app.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

    /**
     * Planifie le worker. Idempotent : si un job est déjà en cours de
     * planification pour ce nom unique, on ne fait rien
     * ([ExistingPeriodicWorkPolicy.KEEP]).
     *
     * À appeler depuis [com.meteocompare.app.MeteoCompareApplication.onCreate].
     */
    fun schedule(context: Context) {
        schedule(WorkManager.getInstance(context.applicationContext))
    }

    /** Overload testable — permet d'injecter un WorkManager mocké. */
    internal fun schedule(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<BiasRefreshWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
            // Flex 6h → WorkManager peut décaler ±6h autour du slot pour
            // regrouper avec d'autres jobs système. Zéro contrainte de
            // ponctualité pour du fetch météo rétrospectif.
            flexTimeInterval = 6, flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
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
        val fetchObs = entry.fetchBiasObservationsUseCase()

        // Snapshot one-shot des favorites — pas besoin d'écouter le Flow
        // (le worker ne vit que le temps d'un cycle).
        val favorites = runCatching { cityRepo.observeFavorites().first() }
            .getOrElse { return Result.retry() }

        // Fetch delta pour chaque ville, tolérant aux erreurs individuelles.
        // Une ville en échec → on skip, on continue les autres. Elle sera
        // retentée au prochain cycle.
        for (city in favorites) {
            runCatching { fetchObs(city) }
                // On absorbe volontairement l'erreur : loggeable via Timber
                // ailleurs si nécessaire, mais on ne veut pas qu'une ville
                // avec un souci réseau bloque le fetch des autres.
                .getOrNull()
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
