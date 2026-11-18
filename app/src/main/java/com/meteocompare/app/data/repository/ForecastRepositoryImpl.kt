package com.meteocompare.app.data.repository

import android.content.Context
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.network.toUserMessage
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.core.util.validZoneOrNull
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastCacheEntity
import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.BatchedForecastSplitter
import com.meteocompare.app.data.remote.EnsembleApi
import com.meteocompare.app.data.remote.EnsembleMedianReducer
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.Coverage
import com.meteocompare.app.domain.model.ForecastEndpoint
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.util.ForecastSeriesDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Repository avec cache transparent via Room.
 *
 * Stratégie de cache :
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │  getCityForecastStream(city, forceRefresh=false, maxCacheAgeMs=null) │
 *  │                                                                       │
 *  │   1. Lecture des entrées de cache disponibles                     │
 *  │   2. Si cache existe → emit Success(cached) immédiatement            │
 *  │   3. Si maxCacheAgeMs != null ET cache plus récent → RETURN          │
 *  │      (économie batterie/data : le user vient d'ouvrir l'app 2 min    │
 *  │       après un précédent refresh, inutile de re-fetcher)             │
 *  │   4. Fetch réseau BATCHED (1 requête, +1 repli global si besoin)    │
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
 * suffixées). Hors domaine régional, un second lot limité aux modèles globaux
 * sélectionnés peut être tenté. La réponse est décomposée par [BatchedForecastSplitter] en un DTO
 * par modèle, puis chaque série est mappée et cachée indépendamment.
 *
 * Le JSON brut reste en cache pour éviter de coupler le schéma Room aux types
 * métier contenant Instant et LocalDate. Le parsing est déplacé sur le
 * dispatcher de calcul.
 */
@Singleton
class ForecastRepositoryImpl @Inject constructor(
    private val api: OpenMeteoApi,
    private val ensembleApi: EnsembleApi,
    private val mapper: ForecastMapper,
    private val cacheDao: ForecastCacheDao,
    private val json: Json,
    private val networkMonitor: NetworkMonitor,
    private val clock: Clock,
    private val evolutionRecorder: ForecastEvolutionRecorder,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:DefaultDispatcher private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val currentLocationStore: CurrentLocationStore = CurrentLocationStore(context)
) : ForecastRepository {

    // ── Coalescing des fetch réseau concurrents ───────────────────────────
    //
    // Plusieurs souscripteurs indépendants du même flow peuvent coexister
    // pour une ville (liste, détail, explication, widget). Comme le flow est
    // cold, ils déclencheraient chacun `fetchAndCache` sans coalescing.
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
    // 1 seul lot partagé (éventuellement + 1 retry global hors couverture).
    // N subscribers pour des clés différentes → toujours N lots
    // (le coalescing est per-key, il ne sérialise pas).
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val inflightMutex = Mutex()
    private val inflightFetches = mutableMapOf<String, Deferred<ApiResult<CityForecast>>>()
    private val transientBackoff = TransientForecastBackoff()
    internal val fetchDiagnostics = ForecastFetchDiagnostics()

    private fun logFetchDiagnostics() {
        if (BuildConfig.DEBUG) android.util.Log.d("MeteoCompare/ForecastCache", fetchDiagnostics.snapshot().toString())
    }

    // Signal applicatif léger : un refresh réussi peut être lancé depuis la
    // page Détails, la Home ou un autre composant. Les écrans déjà vivants
    // reçoivent directement le CityForecast frais, sans relire Room et surtout
    // sans déclencher une seconde requête HTTP. Le buffer couvre les refreshes
    // parallèles de plusieurs favoris ; l'émission reste ordonnée.
    private val _forecastUpdates = MutableSharedFlow<CityForecast>(
        replay = 0,
        extraBufferCapacity = FORECAST_UPDATE_BUFFER,
        // La synchronisation UI est best-effort et ne doit jamais suspendre un
        // refresh. En cas de rafale, conserver les événements les plus récents
        // est plus sûr que de perdre précisément la dernière prévision.
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun observeForecastUpdates(): Flow<CityForecast> =
        _forecastUpdates.asSharedFlow()

    /**
     * Clé de coalescing. Ordonnée sur les noms de modèles pour être invariante
     * au tri de la liste passée par le caller (deux appelants qui demandent
     * les mêmes modèles dans un ordre différent doivent partager la fetch).
     */
    private fun cacheKey(city: City, models: List<WeatherModel>, forecastDays: Int): String =
        "${city.id}|${models.map { it.name }.sorted().joinToString(",")}|" +
            effectiveForecastDays(models, forecastDays)

    private fun effectiveForecastDays(
        models: List<WeatherModel>,
        requestedForecastDays: Int
    ): Int {
        val requested = requestedForecastDays.coerceAtLeast(1)
        return models.maxOfOrNull(WeatherModel::maxForecastDays)
            ?.coerceAtMost(requested)
            ?: requested
    }

    /**
     * Partage un téléchargement en cours pour une même clé. Avec une limite
     * d'âge, partage également la revalidation du cache : un collecteur lent
     * peut réutiliser le résultat déjà téléchargé par un autre écran.
     * Sans limite d'âge, conserve la sémantique du refresh explicite.
     */
    private suspend fun coalescedFetchAndCache(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int,
        maxCacheAgeMs: Long? = null
    ): ApiResult<CityForecast> {
        val forecastKey = cacheKey(city, models, forecastDays)
        // Le contrôle automatique partage son propre Deferred, puis rejoint
        // le fetch réseau commun si nécessaire. Un refresh explicite ne peut
        // ainsi jamais rejoindre un simple contrôle de fraîcheur du cache.
        val key = if (maxCacheAgeMs == null) forecastKey else "$forecastKey|automatic:$maxCacheAgeMs"
        val deferred = inflightMutex.withLock {
            inflightFetches[key]?.takeIf { !it.isCompleted }?.also {
                fetchDiagnostics.fetchCoalesced()
                logFetchDiagnostics()
            } ?: run {
                // Démarrage lazy : le Deferred est enregistré avant que le
                // travail puisse finir, même avec un dispatcher immédiat.
                val created = repoScope.async(start = CoroutineStart.LAZY) {
                    if (maxCacheAgeMs == null) {
                        fetchAndCache(city, models, forecastDays)
                    } else {
                        // emit(cache) peut suspendre un collecteur pendant qu'un
                        // autre charge déjà les données. Revalider dans le travail
                        // partagé évite un second fetch après sa complétion.
                        val latest = readCacheSafely(city, models)
                        fetchDiagnostics.cacheRead(latest != null)
                        logFetchDiagnostics()
                        if (latest != null && latest.isComplete &&
                            latest.coversForecastHorizon(effectiveForecastDays(models, forecastDays)) &&
                            (clock.millis() - latest.oldestFetchedAtMs).coerceAtLeast(0L) <= maxCacheAgeMs
                        ) {
                            ApiResult.Success(latest.forecast)
                        } else {
                            coalescedFetchAndCache(city, models, forecastDays)
                        }
                    }
                }
                inflightFetches[key] = created
                created.invokeOnCompletion {
                    // Le nettoyage compare l'identité du Deferred. Sans ce
                    // garde, la fin d'un ancien fetch pourrait retirer du
                    // registre un nouveau fetch créé entre-temps pour la même clé.
                    repoScope.launch {
                        inflightMutex.withLock {
                            if (inflightFetches[key] === created) {
                                inflightFetches.remove(key)
                            }
                        }
                    }
                }
                created.start()
                created
            }
        }
        return runSuspendCatching { deferred.await() }
            .getOrElse { error ->
                // [fetchAndCache] convertit déjà les erreurs réseau et de
                // parsing attendues. Ce dernier garde couvre les dépendances
                // inattendues (horloge, monitor, Room...) afin que tous les
                // consommateurs reçoivent un état terminal plutôt qu'un Flow
                // interrompu alors que leur UI affiche encore Loading.
                android.util.Log.w(LOG_TAG, "Unexpected forecast refresh failure", error)
                ApiResult.Error(error, error.toUserMessage(context))
            }
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
        var cacheComplete = false
        var cacheCoversRequestedHorizon = false

        // ── Étape 1 : émission immédiate depuis le cache (si non forcé) ──
        if (!forceRefresh) {
            val cached = readCacheSafely(city, models)
            fetchDiagnostics.cacheRead(cached != null)
            logFetchDiagnostics()
            if (cached != null) {
                hasCached = true
                cachedFetchedAtMs = cached.oldestFetchedAtMs
                cacheComplete = cached.isComplete
                cacheCoversRequestedHorizon = cached.coversForecastHorizon(
                    effectiveForecastDays(models, forecastDays)
                )
                emit(ApiResult.Success(cached.forecast))
            }
        }

        // ── Étape 2 : court-circuit si le cache est "assez frais" ──
        //
        // Économie batterie/data quand l'utilisateur ouvre l'app plusieurs
        // fois dans une courte fenêtre : un cache complet et assez récent
        // évite un nouveau fetch.
        //
        // On garde la sécurité "cache pré-feature sans fetchedAt" : si
        // cachedFetchedAtMs est null (donnée cache antérieure à l'ajout du
        // champ fetchedAt), on refetch quand même, pour ne pas laisser le
        // user coincé sur du cache très vieux.
        if (!forceRefresh && maxCacheAgeMs != null && hasCached && cacheComplete &&
            cacheCoversRequestedHorizon && cachedFetchedAtMs != null) {
            // Une correction NTP ou un changement manuel peut faire reculer
            // l'horloge après l'écriture Room. Le cache paraît alors venir du
            // futur. Le considérer périmé provoquerait un fetch à chaque tick,
            // tandis que l'écriture plus ancienne pourrait être rejetée par le
            // garde de fraîcheur du DAO. Comme les libellés d'âge, on borne ce
            // delta à zéro : cette donnée est traitée comme venant d'être lue.
            val ageMs = (clock.millis() - cachedFetchedAtMs).coerceAtLeast(0L)
            if (ageMs <= maxCacheAgeMs) {
                // Cache assez récent, on n'appelle même pas fetchAndCache.
                return@flow
            }
        }

        // ── Étape 3 : fetch réseau + écriture cache ──
        // Passe par [coalescedFetchAndCache] pour dédupliquer les fetches
        // concurrents sur la même clé (voir le KDoc du registre).
        val networkResult = coalescedFetchAndCache(
            city, models, forecastDays,
            maxCacheAgeMs = if (forceRefresh) null else maxCacheAgeMs
        )

        when (networkResult) {
            is ApiResult.Success -> emit(networkResult)
            is ApiResult.Error -> {
                if (!hasCached) {
                    // Pas de cache pour adoucir l'échec → on remonte l'erreur.
                    // Mais on essaie une dernière fois de lire le cache, au cas
                    // où on avait forceRefresh=true et il existe quand même.
                    val fallback = readCacheSafely(city, models)
                    if (fallback != null) emit(ApiResult.Success(fallback.forecast))
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
        val result = coalescedFetchAndCache(city, models, forecastDays)

        // Seuls les refreshs EXPLICITES de l'application publient ce signal.
        // Les streams automatiques — notamment ceux des widgets/workers —
        // mettent bien Room à jour mais ne réveillent pas les ViewModels en
        // arrière-plan. tryEmit garantit en plus qu'aucune UI lente ne peut
        // suspendre le caller.
        if (result is ApiResult.Success) {
            _forecastUpdates.tryEmit(result.data)
        }
        result
    }

    override suspend fun clearCacheForCity(cityId: String) = withContext(ioDispatcher) {
        cacheDao.deleteForCity(cityId)
        evolutionRecorder.clearCity(cityId)
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Internals
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Une indisponibilité ponctuelle de Room ne doit ni tuer le Flow ni laisser
     * un écran ou un widget bloqué en chargement. Le chemin normal tentera
     * ensuite le réseau et produira un [ApiResult] explicite.
     */
    private suspend fun readCacheSafely(
        city: City,
        models: List<WeatherModel>
    ): CachedForecast? = runSuspendCatching {
        readCache(city, models)
    }.onFailure { error ->
        android.util.Log.w(LOG_TAG, "Forecast cache read failed", error)
    }.getOrNull()

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
    ): CachedForecast? = withContext(ioDispatcher) {
        val entries = cacheDao.getForCity(city.id)
        val modelByApiKey = buildMap {
            models.forEach { model ->
                model.compatibleApiKeys.forEach { key -> put(key, model) }
            }
        }
        val cachedModels = withContext(computationDispatcher) {
            entries.mapNotNull { entry ->
                val model = modelByApiKey[entry.modelKey] ?: return@mapNotNull null
                // Depuis les migrations de source (notamment ECMWF 25 km → HRES 9 km),
                // l'identité logique ne suffit plus : si une ligne porte une
                // source explicite différente, elle ne doit jamais être présentée
                // sous le modèle actif. Les lignes anciennes sans métadonnée
                // restent acceptées uniquement lorsque leur modelKey est déjà
                // une clé compatible de la source courante.
                if (entry.sourceApiKey != null && !model.matchesApiKey(entry.sourceApiKey)) {
                    return@mapNotNull null
                }
                if (entry.responseJson == MISSING_MODEL_CACHE_SENTINEL) {
                    CachedModelEntry(
                        fetchedAtMs = entry.fetchedAtEpochMs,
                        model = model,
                        series = null,
                        knownUnavailable = true,
                        timezone = null
                    )
                } else {
                    runCatching {
                        val dto = json.decodeFromString<ForecastResponseDto>(entry.responseJson)
                        val series = mapper.toSeries(model, dto).also { mapped ->
                            logSeriesDiagnostics(mapped, source = "cache")
                        }
                        // Un JSON valide peut néanmoins ne contenir que des
                        // valeurs rejetées par les garde-fous physiques. Il ne
                        // doit ni être affiché ni rendre le cache « complet ».
                        if (!series.hasUsableTemperatureData()) return@runCatching null
                        CachedModelEntry(
                            fetchedAtMs = entry.fetchedAtEpochMs,
                            model = model,
                            series = series,
                            knownUnavailable = false,
                            timezone = dto.timezone
                        )
                    }.getOrNull()
                }
            }
                // Une clé API peut changer au fil du temps (ex. GEM). Si une
                // ancienne et une nouvelle clé coexistent dans Room, elles
                // désignent le même modèle métier : la ligne la plus récente
                // doit être l'unique source de vérité, indépendamment de
                // l'ordre de retour SQL.
                .groupBy(CachedModelEntry::model)
                .map { (_, candidates) -> candidates.maxBy(CachedModelEntry::fetchedAtMs) }
        }
        if (cachedModels.isEmpty()) return@withContext null

        val seriesByModel = cachedModels
            .mapNotNull(CachedModelEntry::series)
            .associateBy(ForecastSeries::model)
        // Un cache composé uniquement de marqueurs d'indisponibilité ne peut
        // pas alimenter l'UI. Il ne doit donc pas masquer un nouvel essai réseau.
        if (seriesByModel.isEmpty()) return@withContext null

        val unavailableModels = cachedModels
            .asSequence()
            .filter(CachedModelEntry::knownUnavailable)
            .map(CachedModelEntry::model)
            .toSet()

        // Une entrée corrompue est ignorée et ne doit pas influencer la date
        // affichée. Les marqueurs d'indisponibilité valides participent en
        // revanche à la fraîcheur : ils évitent de re-questionner toutes les
        // 15 minutes un modèle momentanément ou structurellement indisponible.
        // La fraîcheur du lot est celle de son entrée LA PLUS ANCIENNE, pas
        // de la plus récente. Sinon l'ajout d'un nouveau modèle pouvait rendre
        // le cache "frais" grâce à un autre modèle récent et empêcher le fetch
        // de la série manquante pendant tout l'intervalle utilisateur.
        val oldestFetchedAtMs = cachedModels.minOf(CachedModelEntry::fetchedAtMs)
        val isComplete = models.all { it in seriesByModel || it in unavailableModels }

        CachedForecast(
            forecast = CityForecast(
                city = city.withApiTimezoneFallback(
                    cachedModels.asSequence().mapNotNull(CachedModelEntry::timezone).firstOrNull()
                ),
                seriesByModel = seriesByModel,
                errors = unavailableModels.associateWith {
                    context.getString(R.string.error_model_out_of_range)
                },
                fetchedAt = Instant.ofEpochMilli(oldestFetchedAtMs)
            ),
            isComplete = isComplete,
            oldestFetchedAtMs = oldestFetchedAtMs
        )
    }

    /**
     * Fetch batched multi-modèles (1 requête normalement, avec un unique
     * retry global possible hors couverture) + écriture cache.
     *
     * Le fetch batched partage un axe temporel et une réponse réseau pour tous
     * les modèles demandés. Voir [OpenMeteoApi.getForecastBatched].
     *
     * ─── Fraîcheur d'horodatage ──────────────────────────────────────────
     * Tous les modèles reçoivent le même `now` en cache — c'est LA valeur
     * de vérité pour "cette ville a été rafraîchie à telle heure" côté UI.
     *
     * ─── Erreurs par modèle ──────────────────────────────────────────────
     * Le splitter filtre déjà les modèles pour lesquels Open-Meteo n'a
     * renvoyé aucune donnée exploitable. La cause n'est pas déduite ici :
     * hors couverture, horizon non fourni ou indisponibilité temporaire sont
     * indistinguables dans une réponse partielle. [CityForecast.errors] reste
     * donc volontairement neutre et l'UI peut griser le modèle plutôt que
     * l'interpréter comme une conclusion géographique.
     *
     * ─── Plusieurs endpoints ─────────────────────────────────────────────
     * Les modèles publiés uniquement en ensemble ([ForecastEndpoint.ENSEMBLE])
     * sont servis par un autre hôte : on émet alors une requête batched par
     * endpoint, en parallèle et avec le même `forecast_days` (même axe
     * temporel local). L'échec d'un seul endpoint n'invalide pas les modèles
     * de l'autre : ses modèles portent l'erreur réseau et leurs anciennes
     * lignes de cache sont conservées telles quelles.
     */
    private suspend fun fetchAndCache(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int
    ): ApiResult<CityForecast> = withContext(ioDispatcher) {
        if (models.isEmpty()) {
            val error = IllegalArgumentException("models must not be empty")
            return@withContext ApiResult.Error(
                error,
                context.getString(R.string.error_no_model_available)
            )
        }

        // Court-circuit hors-ligne : évite un timeout de 15s pour rien.
        if (!networkMonitor.isOnline()) {
            return@withContext ApiResult.Error(
                IOException("No network"),
                context.getString(R.string.error_no_network)
            )
        }

        val now = clock.millis()
        val retryKey = cacheKey(city, models, forecastDays)
        // Plusieurs écrans/widgets peuvent demander successivement la même
        // prévision après une panne serveur. Ne pas marteler Open-Meteo tant
        // que le délai exponentiel (30 s à 15 min) n'a pas expiré.
        transientBackoff.blockedStatus(retryKey, now)?.let { status ->
            return@withContext ApiResult.Error(
                IOException("Open-Meteo HTTP $status: retry postponed"),
                context.getString(R.string.error_server, status)
            )
        }

        // ── Une requête batched par endpoint ─────────────────────────
        //
        // Les modèles publiés uniquement en ensemble sont servis par un autre
        // hôte : chaque endpoint reçoit sa propre requête, en parallèle et
        // avec le même `forecast_days` calculé sur TOUTE la sélection, afin
        // que les deux réponses partagent le même axe temporel local. Sans
        // modèle d'ensemble, une seule requête Forecast API est émise.
        val effectiveDays = effectiveForecastDays(models, forecastDays)
        val modelsByEndpoint = models
            .groupBy(WeatherModel::endpoint)
            .toSortedMap(compareBy(ForecastEndpoint::ordinal))
        val outcomes = coroutineScope {
            modelsByEndpoint.map { (endpoint, endpointModels) ->
                async {
                    when (endpoint) {
                        ForecastEndpoint.FORECAST ->
                            fetchForecastEndpoint(city, endpointModels, effectiveDays, now)
                        ForecastEndpoint.ENSEMBLE ->
                            fetchEnsembleEndpoint(city, endpointModels, effectiveDays, now)
                    }
                }
            }.awaitAll()
        }

        // Un 429/5xx sur l'un des endpoints suffit à différer le prochain
        // essai : les modèles de l'autre endpoint restent affichés, mais on
        // ne relance pas immédiatement une requête vers un service saturé.
        val transientStatus = outcomes.firstNotNullOfOrNull(EndpointOutcome::transientStatus)
        val succeeded = outcomes.filter { it.attempt != null }
        when {
            transientStatus != null -> transientBackoff.failed(retryKey, transientStatus, now)
            succeeded.isNotEmpty() -> transientBackoff.succeeded(retryKey)
        }
        if (succeeded.isEmpty()) {
            // Aucun endpoint n'a répondu : l'erreur de la Forecast API prime
            // (ordre des endpoints), comme avant le découpage par endpoint.
            val failure = outcomes.first()
            return@withContext ApiResult.Error(
                failure.failure ?: IllegalStateException("No usable model in batched response"),
                failure.failureMessage ?: context.getString(R.string.error_no_model_available)
            )
        }
        val failedOutcomes = outcomes.filter { it.attempt == null }

        val perModelDtos = LinkedHashMap<WeatherModel, ForecastResponseDto>()
        val successes = LinkedHashMap<WeatherModel, ForecastSeries>()
        val cacheEntries = mutableListOf<ForecastCacheEntity>()
        succeeded.forEach { outcome ->
            val processed = requireNotNull(outcome.attempt).processed
            perModelDtos += processed.dtos
            successes += processed.series
            cacheEntries += processed.cacheEntries
        }
        val errors = mutableMapOf<WeatherModel, String>()

        // Modèles d'un endpoint en échec : l'erreur réseau est connue et
        // n'est PAS une absence de données. Ils ne reçoivent donc pas de
        // marqueur d'indisponibilité et leurs anciennes lignes restent en cache.
        for (outcome in failedOutcomes) {
            val message = outcome.failureMessage ?: context.getString(R.string.error_unknown)
            outcome.models.forEach { model -> errors[model] = message }
        }

        // Modèles demandés mais absents du split → données inexploitables.
        // La réponse ne permet pas de distinguer hors couverture, horizon
        // absent et incident ponctuel : le message utilisateur reste neutre.
        val answeredModels = succeeded.flatMap(EndpointOutcome::models)
        val missingModels = answeredModels - perModelDtos.keys
        for (model in missingModels) {
            errors[model] = context.getString(R.string.error_model_out_of_range)
        }

        // Remplacement atomique du sous-ensemble demandé. Une réponse partielle
        // doit retirer du cache les anciennes lignes des modèles désormais
        // absents, sans toucher aux modèles non demandés par ce caller.
        // Les absences connues sont conservées sous forme de marqueurs légers :
        // le cache reste complet pendant l'intervalle utilisateur et les widgets
        // ne refont pas une requête toutes les 15 minutes pour une absence inchangée.
        // Si aucun modèle n'est exploitable, on conserve le cache précédent :
        // le refresh est un échec complet et ne doit pas détruire le fallback.
        val currentZoneStillActive = !CurrentLocationStore.isCurrentCity(city.id) ||
            currentLocationStore.snapshot().asCity(context)?.id == city.id
        if (cacheEntries.isNotEmpty() && currentZoneStillActive) {
            val persistedEntries = cacheEntries + missingModels.map { model ->
                ForecastCacheEntity(
                    cityId = city.id,
                    modelKey = model.apiKey,
                    fetchedAtEpochMs = now,
                    responseJson = MISSING_MODEL_CACHE_SENTINEL,
                    sourceApiKey = model.apiKey,
                    resolutionKm = model.resolutionKm
                )
            }
            runSuspendCatching {
                cacheDao.replaceRequestedModels(
                    cityId = city.id,
                    // Inclut les anciennes clés reconnues afin qu'un
                    // refresh migre naturellement le cache vers la clé
                    // canonique et ne laisse pas deux lignes pour un même
                    // modèle logique. Seuls les modèles des endpoints qui
                    // ont répondu sont remplacés.
                    requestedModelKeys = answeredModels
                        .flatMap { it.compatibleApiKeys }
                        .distinct(),
                    entities = persistedEntries,
                    incomingFetchedAtEpochMs = now
                )
            }.onFailure { error ->
                android.util.Log.w(
                    LOG_TAG,
                    "Forecast cache write failed",
                    error
                )
            }
        }

        if (successes.isEmpty()) {
            // TOUS les modèles demandés ont retourné vide. La réponse batched ne
            // permet pas d’attribuer une cause certaine ; on surface une seule erreur
            // au lieu d'un CityForecast vide avec N erreurs individuelles.
            ApiResult.Error(
                IllegalStateException("No usable model in batched response"),
                context.getString(R.string.error_no_model_available)
            )
        } else {
            val fresh = CityForecast(
                city = city.withApiTimezoneFallback(succeeded.first().attempt?.response?.timezone),
                seriesByModel = successes,
                errors = errors,
                fetchedAt = Instant.ofEpochMilli(now)
            )

            // Snapshot local de la prévision fraîchement récupérée par MeteoCompare.
            // Cette écriture ne déclenche aucun réseau et ne doit jamais faire
            // échouer le forecast principal si Room rencontre un incident.
            runSuspendCatching { evolutionRecorder.record(fresh) }
                .onFailure { error ->
                    android.util.Log.w(LOG_TAG, "Forecast evolution snapshot write failed", error)
                }

            ApiResult.Success(fresh)
        }
    }

    private fun Int.isTransientForecastStatus(): Boolean = this == 429 || this in 500..599

    /**
     * Open-Meteo renvoie HTTP 400 lorsqu'un modèle régional ne couvre pas la
     * localisation demandée. Ce cas est métier (couverture du modèle), pas une
     * erreur serveur à exposer telle quelle à l'utilisateur.
     *
     * On reste volontairement strict sur le corps de réponse : un autre HTTP
     * 400 (paramètre invalide, régression de requête, etc.) doit continuer à
     * remonter comme erreur technique afin de ne pas masquer un bug client.
     */
    private fun Throwable.isUnsupportedLocationResponse(): Boolean {
        val http = this as? HttpException ?: return false
        if (http.code() != 400) return false

        val body = runCatching {
            http.response()?.errorBody()?.string().orEmpty()
        }.getOrDefault("")
        val normalized = body.lowercase()

        return normalized.contains("no data is available for this location") ||
            (normalized.contains("no data") &&
                normalized.contains("available") &&
                normalized.contains("location"))
    }

    private fun unsupportedLocationMessage(models: List<WeatherModel>): String =
        context.getString(
            if (models.size == 1) {
                R.string.error_location_not_supported_by_model
            } else {
                R.string.error_location_not_supported_by_models
            }
        )

    /**
     * Forecast API : tente EXACTEMENT la sélection de l'utilisateur puis,
     * uniquement en cas d'échec complet lié à la couverture, les modèles
     * globaux déjà présents dans cette sélection.
     *
     * Cette stratégie ne modifie jamais silencieusement les choix
     * utilisateur : elle retire seulement, pour ce retry, les modèles
     * régionaux qui empêchent potentiellement une ville hors zone de
     * recevoir les prévisions globales pourtant sélectionnées. Les modèles
     * régionaux écartés restent comptés comme « répondus » : ils reçoivent
     * un marqueur d'indisponibilité comme dans une réponse partielle.
     */
    private suspend fun fetchForecastEndpoint(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int,
        fetchedAtEpochMs: Long
    ): EndpointOutcome {
        val primaryAttempt = requestBatchAttempt(
            city = city,
            endpoint = ForecastEndpoint.FORECAST,
            models = models,
            forecastDays = forecastDays,
            fetchedAtEpochMs = fetchedAtEpochMs
        )
        val primary = primaryAttempt.getOrNull()
        val primaryError = primaryAttempt.exceptionOrNull()
        // Un 503/429/5xx indique une indisponibilité temporaire du service,
        // PAS une absence de couverture d'un modèle régional. Retenter
        // immédiatement six modèles reproduisait le même 503 et doublait
        // inutilement la charge réseau (voir incident Open-Meteo d'oct. 2026).
        if (primaryError is HttpException && primaryError.code().isTransientForecastStatus()) {
            return EndpointOutcome.failed(
                models = models,
                failure = primaryError,
                message = primaryError.toUserMessage(context),
                transientStatus = primaryError.code()
            )
        }

        val globalFallbackModels = models.filter { it.coverage == Coverage.GLOBAL }
        // Le repli est pertinent uniquement pour une réponse vide ou un 400
        // identifiant explicitement une localité hors couverture. Les erreurs
        // IO, auth, parsing et les autres 400 doivent rester des erreurs.
        val canRetryGlobalOnly =
            globalFallbackModels.isNotEmpty() && globalFallbackModels.size < models.size &&
                (primary?.processed?.series?.isEmpty() == true ||
                    primaryError?.isUnsupportedLocationResponse() == true)

        if (primary?.processed?.series?.isNotEmpty() == true) {
            return EndpointOutcome.succeeded(models, primary)
        }
        if (!canRetryGlobalOnly) {
            val error = primaryError
                ?: IllegalStateException("No usable model in batched response")
            return EndpointOutcome.failed(
                models = models,
                failure = error,
                message = if (primaryAttempt.isFailure) {
                    if (error.isUnsupportedLocationResponse()) {
                        unsupportedLocationMessage(models)
                    } else {
                        error.toUserMessage(context)
                    }
                } else {
                    context.getString(R.string.error_no_model_available)
                }
            )
        }

        if (BuildConfig.DEBUG) {
            android.util.Log.w(
                LOG_TAG,
                "Regional coverage unavailable for ${city.id}; retrying " +
                    "${globalFallbackModels.size} selected global model(s)",
                primaryError
            )
        }
        return requestBatchAttempt(
            city = city,
            endpoint = ForecastEndpoint.FORECAST,
            models = globalFallbackModels,
            forecastDays = forecastDays,
            fetchedAtEpochMs = fetchedAtEpochMs
        ).fold(
            onSuccess = { attempt -> EndpointOutcome.succeeded(models, attempt) },
            onFailure = { fallbackError ->
                EndpointOutcome.failed(
                    models = models,
                    failure = fallbackError,
                    message = if (fallbackError.isUnsupportedLocationResponse()) {
                        unsupportedLocationMessage(models)
                    } else {
                        fallbackError.toUserMessage(context)
                    },
                    transientStatus = (fallbackError as? HttpException)
                        ?.code()
                        ?.takeIf { it.isTransientForecastStatus() }
                )
            }
        )
    }

    /**
     * Ensemble API : modèles globaux publiés uniquement en ensemble, donc
     * sans repli régional. Chaque modèle est réduit à la médiane de ses
     * membres par [EnsembleMedianReducer].
     */
    private suspend fun fetchEnsembleEndpoint(
        city: City,
        models: List<WeatherModel>,
        forecastDays: Int,
        fetchedAtEpochMs: Long
    ): EndpointOutcome = requestBatchAttempt(
        city = city,
        endpoint = ForecastEndpoint.ENSEMBLE,
        models = models,
        forecastDays = forecastDays,
        fetchedAtEpochMs = fetchedAtEpochMs
    ).fold(
        onSuccess = { attempt ->
            if (attempt.processed.series.isNotEmpty()) {
                EndpointOutcome.succeeded(models, attempt)
            } else {
                EndpointOutcome.failed(
                    models = models,
                    failure = IllegalStateException("No usable model in ensemble response"),
                    message = context.getString(R.string.error_no_model_available)
                )
            }
        },
        onFailure = { error ->
            EndpointOutcome.failed(
                models = models,
                failure = error,
                message = error.toUserMessage(context),
                transientStatus = (error as? HttpException)
                    ?.code()
                    ?.takeIf { it.isTransientForecastStatus() }
            )
        }
    )

    /**
     * Exécute un lot Open-Meteo sur un endpoint et le transforme immédiatement
     * en séries métier. Le type [Result] permet aux appelants de déclencher un
     * retry global sans capturer les [kotlinx.coroutines.CancellationException].
     *
     * [forecastDays] est déjà borné par [effectiveForecastDays] sur toute la
     * sélection : il est transmis tel quel pour aligner les endpoints.
     */
    private suspend fun requestBatchAttempt(
        city: City,
        endpoint: ForecastEndpoint,
        models: List<WeatherModel>,
        forecastDays: Int,
        fetchedAtEpochMs: Long
    ): Result<BatchAttempt> = runSuspendCatching {
        val apiModels = models.joinToString(",") { it.apiKey }

        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                LOG_TAG,
                "Batched fetch ($endpoint): ${models.size} models in 1 HTTPS request " +
                    "→ $apiModels"
            )
        }

        // Compter uniquement les vraies tentatives HTTP, pas un cache stale
        // consulté hors ligne ni une tentative écartée par le backoff.
        fetchDiagnostics.fetchStarted()
        logFetchDiagnostics()
        val batched = when (endpoint) {
            ForecastEndpoint.FORECAST -> api.getForecastBatched(
                latitude = city.latitude,
                longitude = city.longitude,
                models = apiModels,
                forecastDays = forecastDays
            )
            ForecastEndpoint.ENSEMBLE -> ensembleApi.getEnsembleBatched(
                latitude = city.latitude,
                longitude = city.longitude,
                models = apiModels,
                forecastDays = forecastDays
            )
        }

        val processed = withContext(computationDispatcher) {
            // Ensemble : une seule série par modèle, médiane de ses membres,
            // avant le split qui attend le format de la Forecast API.
            val reduced = when (endpoint) {
                ForecastEndpoint.FORECAST -> batched
                ForecastEndpoint.ENSEMBLE -> EnsembleMedianReducer.reduce(batched)
            }
            // Split par réponse : le mode single-modèle (clés non suffixées)
            // dépend du nombre de modèles de CETTE requête.
            val perModelDtos = BatchedForecastSplitter.split(reduced, models)
            val successes = perModelDtos.mapValues { (model, dto) ->
                mapper.toSeries(model, dto).also { series ->
                    logSeriesDiagnostics(series, source = "network")
                }
            }
            val cacheEntries = perModelDtos.map { (model, dto) ->
                ForecastCacheEntity(
                    cityId = city.id,
                    modelKey = model.apiKey,
                    fetchedAtEpochMs = fetchedAtEpochMs,
                    responseJson = json.encodeToString(
                        ForecastResponseDto.serializer(),
                        dto
                    ),
                    sourceApiKey = model.apiKey,
                    resolutionKm = model.resolutionKm
                )
            }
            ProcessedForecast(perModelDtos, successes, cacheEntries)
        }

        BatchAttempt(
            response = batched,
            processed = processed
        )
    }

    private data class BatchAttempt(
        val response: BatchedForecastResponseDto,
        val processed: ProcessedForecast
    )

    /**
     * Résultat d'un endpoint : soit un lot exploitable ([attempt]), soit
     * l'erreur à attribuer à ses modèles. [models] reste la sélection
     * demandée à cet endpoint, même après un repli global.
     */
    private class EndpointOutcome private constructor(
        val models: List<WeatherModel>,
        val attempt: BatchAttempt?,
        val failure: Throwable?,
        val failureMessage: String?,
        val transientStatus: Int?
    ) {
        companion object {
            fun succeeded(models: List<WeatherModel>, attempt: BatchAttempt) =
                EndpointOutcome(models, attempt, null, null, null)

            fun failed(
                models: List<WeatherModel>,
                failure: Throwable,
                message: String,
                transientStatus: Int? = null
            ) = EndpointOutcome(models, null, failure, message, transientStatus)
        }
    }

    private data class CachedForecast(
        val forecast: CityForecast,
        val isComplete: Boolean,
        val oldestFetchedAtMs: Long
    ) {
        /**
         * Les anciennes versions de l'app ne demandaient que 7 jours. Une entrée
         * Room peut donc être parfaitement fraîche et complète côté modèles tout
         * en étant trop courte pour un horizon étendu. Sans ce garde, le stream
         * court-circuitait le réseau et les vues longues restaient bloquées à 7 jours.
         *
         * Pour les horizons historiques (<= 7 jours), on conserve le comportement
         * précédent afin de ne pas invalider inutilement les caches existants. Pour
         * un horizon étendu, au moins un modèle capable de l'atteindre doit contenir
         * des valeurs journalières exploitables jusqu'à cette profondeur.
         */
        fun coversForecastHorizon(requestedDays: Int): Boolean {
            // Un modèle absent avec un marqueur d’indisponibilité ne peut pas
            // fournir son horizon théorique. Le contrôle isComplete et l’âge
            // du lot vérifient séparément la présence et la validité du marqueur.
            val availableDays = forecast.seriesByModel.keys
                .maxOfOrNull(WeatherModel::maxForecastDays) ?: return false
            val requiredDays = minOf(requestedDays, availableDays)
            if (requiredDays <= LEGACY_FORECAST_HORIZON_DAYS) return true

            return forecast.seriesByModel.values.any { series ->
                if (series.model.maxForecastDays < requiredDays) return@any false

                val daily = series.daily
                val usableDays = daily.dates.indices.count { index ->
                    daily.tempMax.getOrNull(index) != null ||
                        daily.tempMin.getOrNull(index) != null ||
                        daily.precipitationSum.getOrNull(index) != null ||
                        daily.windSpeedMax.getOrNull(index) != null ||
                        daily.weatherCode.getOrNull(index) != null
                }
                usableDays >= requiredDays
            }
        }
    }

    private data class CachedModelEntry(
        val fetchedAtMs: Long,
        val model: WeatherModel,
        val series: ForecastSeries?,
        val knownUnavailable: Boolean,
        val timezone: String?
    )

    private fun logSeriesDiagnostics(series: ForecastSeries, source: String) {
        if (!BuildConfig.DEBUG) return
        val diagnostic = ForecastSeriesDiagnostics.analyze(series)
        if (!diagnostic.hasLongInternalMissingSequence) return
        val timestampGaps = diagnostic.timestampGaps
            .filter { it.missingHours >= ForecastSeriesDiagnostics.LONG_INTERNAL_MISSING_RUN_HOURS }
            .joinToString { gap -> "${gap.before}->${gap.after}(${gap.missingHours}h)" }
        val variableRuns = diagnostic.internalMissingRuns
            .filter { it.lengthHours >= ForecastSeriesDiagnostics.LONG_INTERNAL_MISSING_RUN_HOURS }
            .joinToString { run -> "${run.variable}:${run.lengthHours}h@${run.startInstant}" }
        android.util.Log.w(
            LOG_TAG,
            "Series gaps source=$source model=${series.model.apiKey} " +
                "longest=${diagnostic.longestInternalMissingSequenceHours}h " +
                "timestampGaps=[$timestampGaps] variableRuns=[$variableRuns]"
        )
    }

    /** Même invariant que le splitter, après mapping et filtrage physique. */
    private fun ForecastSeries.hasUsableTemperatureData(): Boolean =
        hourly.temperature2m.any { it != null } ||
            daily.tempMax.any { it != null } ||
            daily.tempMin.any { it != null }

    private data class ProcessedForecast(
        val dtos: Map<WeatherModel, ForecastResponseDto>,
        val series: Map<WeatherModel, ForecastSeries>,
        val cacheEntries: List<ForecastCacheEntity>
    )

    /**
     * Le geocoder fournit normalement un fuseau IANA, mais les favoris anciens
     * peuvent ne pas l'avoir. La réponse Forecast API (`timezone=auto`) devient
     * alors la source de secours afin que les heures locales ne retombent pas
     * silencieusement sur UTC. Un fuseau explicite valide du favori reste prioritaire.
     */
    private fun City.withApiTimezoneFallback(apiTimezone: String?): City {
        if (validZoneOrNull(timezone) != null) return this
        val fallback = validZoneOrNull(apiTimezone)?.id ?: return this
        return copy(timezone = fallback)
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
        private const val LEGACY_FORECAST_HORIZON_DAYS = 7
        private const val FORECAST_UPDATE_BUFFER = 8
        private const val MISSING_MODEL_CACHE_SENTINEL =
            "__METEOCOMPARE_MODEL_UNAVAILABLE__"
    }
}
