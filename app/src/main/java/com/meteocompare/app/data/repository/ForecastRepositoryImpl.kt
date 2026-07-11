package com.meteocompare.app.data.repository

import android.content.Context
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.toUserMessage
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastCacheEntity
import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.BatchedForecastSplitter
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.usecase.SnapshotForecastUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository avec cache transparent via Room.
 *
 * Stratégie de cache :
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │  getCityForecastStream(city, forceRefresh=false, maxCacheAgeMs=null) │
 *  │                                                                       │
 *  │   1. Lecture cache (synchrone, ~1 ms par modèle)                     │
 *  │   2. Si cache existe → emit Success(cached) immédiatement            │
 *  │   3. Si maxCacheAgeMs != null ET cache plus récent → RETURN          │
 *  │      (économie batterie/data : le user vient d'ouvrir l'app 2 min    │
 *  │       après un précédent refresh, inutile de re-fetcher)             │
 *  │   4. Fetch réseau BATCHED (1 requête HTTPS pour N modèles)           │
 *  │   5. Si réseau OK → écriture cache + emit Success(fresh)             │
 *  │   6. Si réseau KO :                                                  │
 *  │      - cache existait → ne pas émettre d'erreur (user voit le cache) │
 *  │      - sinon → emit Error                                            │
 *  │                                                                       │
 *  │  Avec forceRefresh=true : skip étapes 1-3, traite comme pull-to-     │
 *  │  refresh — mais si réseau KO on retombe sur cache (fallback).        │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 * ─── Batching multi-modèles ──────────────────────────────────────────────
 * Open-Meteo supporte le multi-modèles en une seule requête HTTPS (variables
 * suffixées). Le fetch de N modèles = 1 seul appel réseau et 1 seul handshake
 * TLS. La réponse est décomposée par [BatchedForecastSplitter] en un DTO par
 * modèle, qui est ensuite mappé et caché indépendamment (format de cache
 * inchangé — chaque modèle a toujours sa propre ligne Room).
 *
 * Le re-parsing du JSON au read est délibérément accepté plutôt que de
 * cacher des ForecastSeries pré-parsés. Raison : le JSON brut est sérialisable
 * sans custom serializer (que des primitifs), et le coût (~1 ms × N modèles)
 * est invisible à l'utilisateur.
 */
@Singleton
class ForecastRepositoryImpl @Inject constructor(
    private val api: OpenMeteoApi,
    private val mapper: ForecastMapper,
    private val cacheDao: ForecastCacheDao,
    private val json: Json,
    private val networkMonitor: NetworkMonitor,
    private val snapshotForecast: SnapshotForecastUseCase,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ForecastRepository {

    // ── Coalescing des fetch réseau concurrents ───────────────────────────
    //
    // Problème observé : au cold start, plusieurs souscripteurs indépendants
    // du même flow pour une même ville coexistent (CityListVM + CityDetailVM
    // + ConfidenceExplanationVM + widget composition Glance). Chaque flow
    // étant cold, chaque subscriber déclenchait son propre `fetchAndCache`
    // → N requêtes HTTPS pour la même donnée en < 500ms.
    //
    // Fix : registre des fetches en vol par clé (city, models, forecastDays).
    // Un subscriber qui arrive alors qu'une fetch est déjà en cours pour la
    // même clé attend son résultat au lieu d'en lancer une nouvelle. Le
    // travail réel tourne sur [repoScope] (SupervisorJob dédié) — un
    // subscriber qui cancel n'interrompt pas la fetch pour les autres, et
    // le cache est mis à jour même si tous les subscribers d'origine ont
    // disparu (donnée disponible au prochain démarrage).
    //
    // Impact HTTP réel : N subscribers concurrents pour la même clé →
    // 1 seul HTTPS. N subscribers pour des clés différentes → toujours N
    // (le coalescing est per-key, il ne sérialise pas).
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val inflightMutex = Mutex()
    private val inflightFetches = mutableMapOf<String, Deferred<ApiResult<CityForecast>>>()

    /**
     * Clé de coalescing. Ordonnée sur les noms de modèles pour être invariante
     * au tri de la liste passée par le caller (deux appelants qui demandent
     * les mêmes modèles dans un ordre différent doivent partager la fetch).
     */
    private fun cacheKey(city: City, models: List<WeatherModel>, forecastDays: Int): String =
        "${city.id}|${models.map { it.name }.sorted().joinToString(",")}|$forecastDays"

    /**
     * Version coalescée de [fetchAndCache]. Voir le KDoc du registre pour le
     * pourquoi. Sémantique identique côté retour : renvoie l'`ApiResult` que
     * `fetchAndCache` aurait renvoyé pour cette clé.
     */
    private suspend fun coalescedFetchAndCache(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int
    ): ApiResult<CityForecast> {
        val key = cacheKey(city, models, forecastDays)
        val deferred = inflightMutex.withLock {
            // Réutilise le Deferred existant s'il est encore actif. Un
            // Deferred terminé mais pas encore retiré (race entre `finally`
            // du produit et cet accès) fonctionne aussi — `await()` renvoie
            // immédiatement la valeur déjà calculée.
            inflightFetches[key]?.takeIf { !it.isCompleted } ?: repoScope.async {
                try {
                    fetchAndCache(city, models, forecastDays)
                } finally {
                    // Retrait sous mutex pour éviter la race avec un nouveau
                    // caller qui arriverait juste après la fin de fetch.
                    inflightMutex.withLock { inflightFetches.remove(key) }
                }
            }.also { inflightFetches[key] = it }
        }
        return deferred.await()
    }

    override fun getCityForecastStream(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int,
        forceRefresh: Boolean,
        maxCacheAgeMs: Long?
    ): Flow<ApiResult<CityForecast>> = flow {
        var hasCached = false
        var cachedFetchedAtMs: Long? = null

        // ── Étape 1 : émission immédiate depuis le cache (si non forcé) ──
        if (!forceRefresh) {
            val cached = readCache(city, models)
            if (cached != null) {
                hasCached = true
                cachedFetchedAtMs = cached.fetchedAt?.toEpochMilli()
                emit(ApiResult.Success(cached))
            }
        }

        // ── Étape 2 : court-circuit si le cache est "assez frais" ──
        //
        // Économie batterie/data quand l'utilisateur ouvre l'app plusieurs
        // fois dans une courte fenêtre. Sans ce garde, chaque cold start
        // déclenche 5 requêtes réseau parallèles vers Open-Meteo — inutile
        // si on vient de rafraîchir il y a 3 minutes.
        //
        // On garde la sécurité "cache pré-feature sans fetchedAt" : si
        // cachedFetchedAtMs est null (donnée cache antérieure à l'ajout du
        // champ fetchedAt), on refetch quand même, pour ne pas laisser le
        // user coincé sur du cache très vieux.
        if (!forceRefresh && maxCacheAgeMs != null && hasCached &&
            cachedFetchedAtMs != null) {
            val ageMs = System.currentTimeMillis() - cachedFetchedAtMs
            if (ageMs in 0..maxCacheAgeMs) {
                // Cache assez récent, on n'appelle même pas fetchAndCache.
                return@flow
            }
        }

        // ── Étape 3 : fetch réseau + écriture cache ──
        // Passe par [coalescedFetchAndCache] pour dédupliquer les fetches
        // concurrents sur la même clé (voir le KDoc du registre).
        val networkResult = coalescedFetchAndCache(city, models, forecastDays)

        when (networkResult) {
            is ApiResult.Success -> emit(networkResult)
            is ApiResult.Error -> {
                if (!hasCached) {
                    // Pas de cache pour adoucir l'échec → on remonte l'erreur.
                    // Mais on essaie une dernière fois de lire le cache, au cas
                    // où on avait forceRefresh=true et il existe quand même.
                    val fallback = readCache(city, models)
                    if (fallback != null) emit(ApiResult.Success(fallback))
                    else emit(networkResult)
                }
                // Si on a déjà émis du cache, on n'émet PAS l'erreur — l'UI
                // garde les données qu'elle a, pas de message d'erreur intrusif.
            }
        }
    }

    override suspend fun refreshCityForecast(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int
    ): ApiResult<CityForecast> = withContext(ioDispatcher) {
        // Fix faux positif "Prévisions mises à jour" en mode avion :
        //   AVANT : si réseau KO mais cache existe → on retournait Success(cached)
        //           → l'UI affichait "Prévisions mises à jour" alors qu'aucune
        //              donnée fraîche n'avait été obtenue. Mensonger.
        //   APRÈS : on retourne directement le résultat de fetchAndCache.
        //           - Réseau OK → Success(fresh)
        //           - Réseau KO → Error("Pas de connexion") → snackbar honnête.
        //
        // Les données déjà affichées dans l'UI ne sont pas effacées : la VM
        // garde son state Loaded (philosophie tolerant côté CityDetailViewModel).
        //
        // Passe par [coalescedFetchAndCache] : un pull-to-refresh qui arrive
        // pendant qu'une fetch est déjà en vol (autre subscriber, widget)
        // attend son résultat au lieu d'en lancer une seconde HTTP identique.
        coalescedFetchAndCache(city, models, forecastDays)
    }

    override suspend fun clearCacheForCity(cityId: String) = withContext(ioDispatcher) {
        cacheDao.deleteForCity(cityId)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Récupère TOUS les modèles depuis le cache pour cette ville et les
     * reconstruit en un CityForecast. Renvoie null si le cache ne contient
     * AUCUN modèle parmi ceux demandés.
     *
     * Note : on ne filtre PAS par fraîcheur ici — un cache vieux de 6 h est
     * meilleur qu'un écran blanc. L'utilisateur peut toujours rafraîchir
     * manuellement, et le réseau écrasera de toute façon.
     */
    private suspend fun readCache(
        city: City,
        models: List<WeatherModel>
    ): CityForecast? = withContext(ioDispatcher) {
        val entries = cacheDao.getForCity(city.id)
            .filter { entry -> models.any { it.apiKey == entry.modelKey } }
        if (entries.isEmpty()) return@withContext null

        val series = entries.mapNotNull { entry ->
            val model = models.firstOrNull { it.apiKey == entry.modelKey } ?: return@mapNotNull null
            runCatching {
                val dto = json.decodeFromString<ForecastResponseDto>(entry.responseJson)
                mapper.toSeries(model, dto)
            }.getOrNull()
        }
        if (series.isEmpty()) return@withContext null

        // Fraîcheur du CityForecast = plus récent des fetchedAt de tous les
        // modèles présents en cache. En pratique tous les modèles d'une même
        // ville sont écrits ensemble (même `now` dans fetchAndCache), donc les
        // valeurs sont très proches ; on prend le max pour être robuste au cas
        // où un modèle isolé aurait été rafraîchi séparément par une future
        // évolution du code.
        val newestFetchedAtMs = entries.maxOf { it.fetchedAtEpochMs }

        CityForecast(
            city = city,
            seriesByModel = series.associateBy { it.model },
            errors = emptyMap(), // on n'a pas mémorisé les erreurs en cache
            fetchedAt = Instant.ofEpochMilli(newestFetchedAtMs)
        )
    }

    /**
     * Fetch batched multi-modèles (1 requête HTTPS) + écriture cache.
     *
     * ─── Historique ─────────────────────────────────────────────────────
     * Version antérieure : N appels HTTPS parallèles via `coroutineScope +
     * async` (voir [OpenMeteoApi.getForecast]). Fonctionnel mais coûteux :
     * N handshakes TLS + N wakeups radio + retry par modèle. Remplacé par
     * l'appel batched — voir [OpenMeteoApi.getForecastBatched] pour la
     * justification.
     *
     * ─── Fraîcheur d'horodatage ──────────────────────────────────────────
     * Tous les modèles reçoivent le même `now` en cache — c'est LA valeur
     * de vérité pour "cette ville a été rafraîchie à telle heure" côté UI.
     *
     * ─── Erreurs par modèle ──────────────────────────────────────────────
     * Le splitter filtre déjà les modèles pour lesquels Open-Meteo n'a
     * renvoyé aucune donnée exploitable (typiquement hors de leur zone de
     * couverture). Ces modèles apparaissent dans [CityForecast.errors] avec
     * un message localisé "hors zone" — l'UI peut choisir de les grisage
     * plutôt que masquer.
     */
    private suspend fun fetchAndCache(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int
    ): ApiResult<CityForecast> = withContext(ioDispatcher) {
        require(models.isNotEmpty()) { "models must not be empty" }

        // Court-circuit hors-ligne : évite un timeout de 15s pour rien.
        if (!networkMonitor.isOnline()) {
            return@withContext ApiResult.Error(
                IOException("No network"),
                context.getString(R.string.error_no_network)
            )
        }

        val now = System.currentTimeMillis()

        // ── Requête batched ────────────────────────────────────────────
        // Une seule ligne = un seul appel HTTPS. `forecast_days` prend la
        // valeur max sur les modèles demandés — les modèles à horizon plus
        // court retournent null au-delà, ce que le mapper gère (aligne les
        // listes de valeurs sur les timestamps, pad avec null si absent).
        val effectiveForecastDays = models.maxOf { it.maxForecastDays }
            .coerceAtMost(forecastDays.coerceAtLeast(1))

        // Log explicite pour vérifier en debug/production que le batching
        // fonctionne comme prévu. Filtrable par `adb logcat -s MeteoCompare/Net`,
        // le tag court permet un grep visuel rapide. Un futur regression qui
        // ferait éclater ce log en N lignes séparées (une par modèle) serait
        // une régression très visible.
        //
        // Niveau INFO plutôt que DEBUG : on veut voir ce log même sur les
        // logcat filtrés par défaut du Play Store (release keeps INFO+).
        android.util.Log.d(
            LOG_TAG,
            "Batched fetch: ${models.size} models in 1 HTTPS request " +
                    "→ ${models.joinToString(",") { it.apiKey }}"
        )

        val batched = try {
            api.getForecastBatched(
                latitude = city.latitude,
                longitude = city.longitude,
                models = models.joinToString(",") { it.apiKey },
                forecastDays = effectiveForecastDays
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            return@withContext ApiResult.Error(e, e.toUserMessage(context))
        }

        // ── Décomposition batched → per-modèle ─────────────────────────
        val perModelDtos: Map<WeatherModel, ForecastResponseDto> =
            BatchedForecastSplitter.split(batched, models)

        val successes = mutableMapOf<WeatherModel, ForecastSeries>()
        val errors = mutableMapOf<WeatherModel, String>()

        // Modèles qui ont répondu : on cache + on mappe
        for ((model, dto) in perModelDtos) {
            runCatching {
                cacheDao.upsert(
                    ForecastCacheEntity(
                        cityId = city.id,
                        modelKey = model.apiKey,
                        fetchedAtEpochMs = now,
                        responseJson = json.encodeToString(
                            ForecastResponseDto.serializer(),
                            dto
                        )
                    )
                )
            }
            // Une erreur d'écriture cache ne bloque PAS l'affichage — la
            // donnée fraîche va quand même être renvoyée au caller.
            successes[model] = mapper.toSeries(model, dto)
        }

        // Modèles demandés mais absents du split → filtrés par le splitter
        // (données inexploitables). Reportés au caller pour affichage
        // discret ("modèle hors zone").
        val missingModels = models - perModelDtos.keys
        for (model in missingModels) {
            errors[model] = context.getString(R.string.error_model_out_of_range)
        }

        if (successes.isEmpty()) {
            // TOUS les modèles demandés ont retourné vide — probablement
            // hors de leur zone de couverture. On surface une seule erreur
            // au lieu d'un CityForecast vide avec N erreurs individuelles.
            ApiResult.Error(
                IllegalStateException("No usable model in batched response"),
                context.getString(R.string.error_no_model_available)
            )
        } else {
            val fresh = CityForecast(
                city = city,
                seriesByModel = successes,
                errors = errors,
                fetchedAt = Instant.ofEpochMilli(now)
            )
            // Piggyback : chaque fetch réseau réussi alimente aussi l'historique
            // de suivi de biais. runCatching pour être défensif — un bug dans
            // le snapshot use case ne doit JAMAIS faire échouer le refresh
            // utilisateur (dégradation gracieuse : l'user voit son forecast
            // frais, on perd juste un point d'historique de biais).
            runCatching { snapshotForecast(fresh) }
            ApiResult.Success(fresh)
        }
    }

    companion object {
        /**
         * Tag court pour `adb logcat -s MeteoCompare/Net` — permet de vérifier
         * visuellement (dev/QA) que le batching fonctionne comme prévu :
         *   1 refresh utilisateur → 1 ligne "Batched fetch: N models…"
         * Si plusieurs lignes apparaissent en séquence rapide, c'est le signe
         * d'une régression (parallélisation non voulue) ou d'un refresh
         * multiple (widget + app en même temps).
         */
        private const val LOG_TAG = "MeteoCompare/Net"
    }
}