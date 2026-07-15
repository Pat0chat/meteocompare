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
import com.meteocompare.app.core.util.runSuspendCatching
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
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
        val favorites = runSuspendCatching { cityRepo.observeFavorites().first() }
            .getOrElse { return Result.retry() }
        val enabledModels = runSuspendCatching { userPrefs.observeEnabledModels().first() }
            .getOrDefault(emptyList())

        // Backfill historical-forecast d'abord — le use case est idempotent
        // (skip si des rows passées existent déjà) donc l'appeler à chaque
        // cycle est safe. Une ville en échec (timeout, 5xx) est skippée sans
        // faire échouer le cycle global. Coût no-op = 1 SELECT COUNT local.
        for (city in favorites) {
            runSuspendCatching { backfill(city, enabledModels) }.getOrNull()
        }

        // Fetch delta observations pour chaque ville, tolérant aux erreurs
        // individuelles (idem : une ville qui rate ne bloque pas les autres).
        for (city in favorites) {
            runSuspendCatching { fetchObs(city) }.getOrNull()
        }

        // Housekeeping bias : purge les samples > RETENTION_DAYS.
        runSuspendCatching {
            biasRepo.purgeOlderThan(
                LocalDate.now().minusDays(BiasRefreshScheduler.RETENTION_DAYS)
            )
        }

        // Housekeeping forecast cache : purge les entrées > FORECAST_CACHE_RETENTION_DAYS.
        // Protégé séparément pour rester indépendant de la purge du biais :
        // un échec de l'un ne doit pas empêcher l'autre. Le DAO utilise un
        // cutoff en epoch millis alors que biasRepo utilise LocalDate, d'où
        // la conversion inline (Instant.now().minus(N days).toEpochMilli()).
        //
        // Coût : 1 DELETE indexé sur `fetchedAtEpochMs`. Sur une DB de
        // ~100 rows (17 modèles × 5 villes × ~1 entry par jour × 1 semaine),
        // s'exécute en < 5 ms. Négligeable même sur bas de gamme.
        runSuspendCatching {
            val cutoffMs = java.time.Instant.now()
                .minus(BiasRefreshScheduler.FORECAST_CACHE_RETENTION_DAYS, java.time.temporal.ChronoUnit.DAYS)
                .toEpochMilli()
            entry.forecastCacheDao().deleteOlderThan(cutoffMs)
        }

        return Result.success()
    }
}
