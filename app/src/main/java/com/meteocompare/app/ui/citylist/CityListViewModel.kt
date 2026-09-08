package com.meteocompare.app.ui.citylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.repository.VigilanceRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.domain.util.ForecastAggregates
import com.meteocompare.app.domain.util.forecastPresentationTicks
import com.meteocompare.app.domain.util.hasForecastPresentationChanged
import com.meteocompare.app.domain.util.WeatherScenarioBuilder
import com.meteocompare.app.ui.components.AppToastEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject


sealed interface MarineFeedback {
    data object Enabled : MarineFeedback
    data object Refreshed : MarineFeedback
    data object NotCoastal : MarineFeedback
    data class Error(val message: String) : MarineFeedback
}

@HiltViewModel
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CityListViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val marineRepository: MarineRepository,
    private val vigilanceRepository: VigilanceRepository,
    private val networkMonitor: NetworkMonitor,
    private val confidenceCalculator: ConfidenceCalculator,
    private val userPreferences: UserPreferencesRepository,
    private val clock: Clock,
    @param:DefaultDispatcher private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val engineContextProvider: ForecastEngineContextProvider
) : ViewModel() {

    private val forecastsById = MutableStateFlow<Map<String, ForecastState>>(emptyMap())
    /** Dernières données brutes : changer de moteur recalcule sans refetch réseau. */
    private val rawForecastsById = MutableStateFlow<Map<String, CityForecast>>(emptyMap())
    private val vigilanceById = MutableStateFlow<Map<String, VigilanceForecast>>(emptyMap())
    private val vigilanceJobs = mutableMapOf<String, Job>()
    private val vigilanceCoastModeById = mutableMapOf<String, Boolean>()
    private val _isRefreshing = MutableStateFlow(false)
    private val _isOnline = MutableStateFlow(networkMonitor.isOnline())
    private val marineLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    /** Villes validées comme côtières, indépendamment de l'activation de Mer / côte. */
    private val marineAvailableIds = MutableStateFlow<Set<String>>(emptySet())
    private val marineAvailabilityCheckedAt = mutableMapOf<String, Long>()
    private val marineAvailabilityJobs = mutableMapOf<String, Job>()
    private val _marineFeedback = Channel<MarineFeedback>(capacity = Channel.BUFFERED)
    val marineFeedback = _marineFeedback.receiveAsFlow()
    private val _actionFeedback = Channel<AppToastEvent>(capacity = Channel.BUFFERED)
    val actionFeedback = _actionFeedback.receiveAsFlow()

    // Tracking des jobs de stream par cityId. Sert à les canceller proprement
    // quand une ville est retirée des favoris ou quand les modèles sélectionnés
    // changent (auquel cas on relance avec la nouvelle config).
    private val streamJobs = mutableMapOf<String, Job>()
    private val retryJobs = mutableMapOf<String, Job>()
    /** Relance cache-aware déclenchée au retour au premier plan/réseau. */
    private var policyRefreshJob: Job? = null
    private var manualRefreshJob: Job? = null

    // Les streams cache+réseau sont finis. Une ville reste donc marquée comme
    // initialisée après la fin normale de son stream, sinon l'ajout d'un autre
    // favori relancerait tous les anciens streams (et potentiellement le réseau).
    private val initializedCityIds = mutableSetOf<String>()

    // Index courant des favoris, maintenu sur le Main dispatcher par
    // [syncStreams]. Il permet d'ignorer une mise à jour tardive reçue juste
    // après la suppression d'une ville.
    private var favoriteCitiesById: Map<String, City> = emptyMap()

    // Snapshot de la dernière configuration de stream. L'intervalle fait
    // partie de la clé : il détermine la fraîcheur acceptable du cache au
    // moment de la souscription.
    private var lastStreamConfig: Pair<List<WeatherModel>, RefreshInterval>? = null
    private var streamConfigGeneration: Long = 0L
    private val appliedConfigGenerationByCity = mutableMapOf<String, Long>()
    private val appliedRequestedModelsByCity = mutableMapOf<String, Set<WeatherModel>>()

    private val marineUiState = combine(
        marineLoadingIds,
        marineAvailableIds
    ) { loading, available -> loading to available }

    private val auxiliaryUiState = combine(marineUiState, vigilanceById) { marine, vigilance ->
        marine to vigilance
    }

    val uiState: StateFlow<CityListUiState> = combine(
        cityRepository.observeFavorites(),
        forecastsById,
        _isRefreshing,
        _isOnline,
        auxiliaryUiState
    ) { cities, cache, refreshing, online, auxiliary ->
        val (marineState, vigilance) = auxiliary
        val (marineLoading, marineAvailable) = marineState
        CityListUiState(
            items = cities.map { city ->
                CityCardState(
                    city = city,
                    forecast = cache[city.id] ?: ForecastState.Loading,
                    vigilance = vigilance[city.id]?.takeIf { city.isFrenchLocation },
                    isMarineAvailable = city.marineEnabled || city.id in marineAvailable,
                    isMarineLoading = city.id in marineLoading
                )
            },
            isRefreshing = refreshing,
            isOnline = online
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CityListUiState()
    )

    // ─── Add city sheet state (inchangé) ────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _searchError = MutableStateFlow<String?>(null)

    private val searchResults: StateFlow<List<City>> = _searchQuery
        .debounce(700)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.length < 3) {
                _isSearching.value = false
                flowOf(emptyList())
            } else {
                flow {
                    _isSearching.value = true
                    _searchError.value = null
                    try {
                        val result = runSuspendCatching {
                            cityRepository.searchCities(query)
                        }.getOrElse {
                            _actionFeedback.send(AppToastEvent.error(R.string.toast_action_error))
                            emit(emptyList())
                            return@flow
                        }
                        when (result) {
                            is ApiResult.Success -> emit(result.data)
                            is ApiResult.Error -> {
                                _searchError.value = result.message
                                emit(emptyList())
                            }
                        }
                    } finally {
                        // Reste vrai sinon lorsqu'un repository inattendu lève
                        // pendant la recherche ou quand flatMapLatest annule la
                        // requête parce que l'utilisateur continue à taper.
                        _isSearching.value = false
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val addCityState: StateFlow<AddCityUiState> = combine(
        _searchQuery, searchResults, _isSearching, _searchError
    ) { query, results, searching, error ->
        AddCityUiState(query, results, searching, error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddCityUiState()
    )

    init {
        observePresentationTime()

        viewModelScope.launch {
            networkMonitor.observeOnline().collect { online ->
                val wasOnline = _isOnline.value
                _isOnline.value = online
                if (online && !wasOnline) {
                    // Ferme la course où un check Vigilance a observé l'état
                    // hors ligne mais n'a pas encore quitté sa coroutine : un
                    // simple appel cache-aware le verrait encore actif et le
                    // sauterait. Le repository limite toujours le réseau via TTL.
                    vigilanceJobs.values.forEach { it.cancel() }
                    vigilanceJobs.clear()
                    // Une seule porte d'entrée remet à jour prévisions, Mer / côte
                    // et Vigilance. Les repositories conservent leurs propres TTL :
                    // ce rappel ne force donc aucune requête superflue.
                    refreshIfStale()
                }
            }
        }

        // Le moteur change uniquement la centrale dérivée. On recalcule donc
        // les cartes depuis les forecasts bruts déjà en mémoire, sans annuler
        // ni relancer les streams cache/réseau.
        viewModelScope.launch {
            userPreferences.observeForecastEngine().distinctUntilChanged().collectLatest { engine ->
                runSuspendCatching {
                    rawForecastsById.value.forEach { (id, forecast) ->
                        if (id !in favoriteCitiesById) return@forEach
                        val mapped = toForecastState(
                            result = ApiResult.Success(forecast),
                            engineOverride = engine
                        )
                        // Le calcul quitte Main. Entre-temps un refresh peut avoir
                        // remplacé le forecast brut ou la ville peut avoir été
                        // supprimée. Ne jamais réappliquer alors l'ancien snapshot.
                        forecastsById.update { states ->
                            if (id !in favoriteCitiesById ||
                                rawForecastsById.value[id] !== forecast
                            ) {
                                states
                            } else {
                                states + (id to mapped)
                            }
                        }
                    }
                }.onFailure { error ->
                    android.util.Log.w(
                        "MeteoCompare/CityList",
                        "Unable to apply forecast engine change",
                        error
                    )
                }
            }
        }

        // Le cœur : on combine favoris + modèles sélectionnés + intervalle.
        // Quand l'une de ces sources change, on réajuste les streams.
        //
        // ─── distinctUntilChanged {} pour éviter les cancel-relaunch inutiles ──
        // Les Flows amont sont maintenant distinctUntilChanged côté repository
        // (voir UserPreferencesRepositoryImpl), mais le combine amalgame trois
        // sources — chaque tick d'une source déclenche une combine emission,
        // même si les autres sources n'ont pas changé. Le distinctUntilChanged
        // ici compare le tuple entier — si (villes, modèles, intervalle) est
        // identique, on ne fait pas de sync.
        //
        // Pourquoi ça matter : sans ce garde, un toggle dark/light (via
        // ThemePreference) ne devrait rien changer aux streams, mais l'ancien
        // code aurait tout de même cancel+relaunch tous les streams parce que
        // la subscription DataStore réémettait — c'était le pic de CPU/network
        // que la question cible.
        viewModelScope.launch {
            combine(
                cityRepository.observeFavorites(),
                userPreferences.observeEnabledModels(),
                userPreferences.observeRefreshInterval()
            ) { cities, models, interval ->
                Triple(cities, models, interval)
            }
                .distinctUntilChanged()
                .collect { (cities, models, interval) ->
                    syncStreams(cities, models, interval)
                }
        }

        // Le stream cache+réseau d'une CityCard est volontairement fini. Sans
        // ce canal, un refresh forcé depuis CityDetail écrit bien Room mais la
        // Home déjà présente dans la back stack conserve son ancien timestamp.
        // On applique ici le résultat frais déjà téléchargé : zéro second fetch.
        viewModelScope.launch {
            forecastRepository.observeForecastUpdates().collect { forecast ->
                val city = favoriteCitiesById[forecast.city.id] ?: return@collect
                runSuspendCatching {
                    applyForecastResult(city, ApiResult.Success(forecast))
                }.onFailure { error ->
                    android.util.Log.w(
                        "MeteoCompare/CityList",
                        "Unable to apply external forecast for city=${city.id}",
                        error
                    )
                }
            }
        }
    }

    /**
     * Fait avancer les cartes avec l'horloge sans télécharger à nouveau la
     * météo. Le ticker sonde à la minute, puis la clé de présentation limite
     * le recalcul aux changements d'échéance horaire ou de jour local.
     */
    private fun observePresentationTime() {
        viewModelScope.launch {
            forecastPresentationTicks(clock).collect { now ->
                runSuspendCatching { recalculatePresentation(now) }
                    .onFailure { error ->
                        android.util.Log.w(
                            "MeteoCompare/CityList",
                            "Unable to update forecast presentation",
                            error
                        )
                    }
            }
        }
    }

    private suspend fun recalculatePresentation(now: Instant) {
        val snapshots = rawForecastsById.value
        if (snapshots.isEmpty()) return

        val engine = userPreferences.observeForecastEngine().first()
        val recalculated = snapshots.mapNotNull { (cityId, forecast) ->
            val previous = forecastsById.value[cityId] as? ForecastState.Loaded
                ?: return@mapNotNull null
            val previouslyCalculatedAt = previous.calculatedAt
                ?: return@mapNotNull null
            if (!hasForecastPresentationChanged(
                    forecast = forecast,
                    previouslyCalculatedAt = previouslyCalculatedAt,
                    now = now
                )
            ) return@mapNotNull null

            Triple(
                cityId,
                forecast,
                toForecastState(
                    result = ApiResult.Success(forecast),
                    engineOverride = engine,
                    calculationNow = now
                )
            )
        }
        if (recalculated.isEmpty()) return

        // Un changement de moteur peut avoir lancé son propre calcul pendant
        // le passage sur le dispatcher de calcul. Dans ce cas son résultat est
        // prioritaire et le prochain tick repartira de ce nouvel état.
        if (userPreferences.observeForecastEngine().first() != engine) return

        forecastsById.update { states ->
            recalculated.fold(states) { current, (cityId, forecast, mapped) ->
                when {
                    cityId !in favoriteCitiesById -> current
                    rawForecastsById.value[cityId] !== forecast -> current
                    else -> current + (cityId to mapped)
                }
            }
        }
    }

    /**
     * Relit Room puis laisse la politique utilisateur décider d'un éventuel
     * fetch. Cette voie silencieuse est appelée au retour au premier plan et
     * au retour réseau : elle récupère notamment une prévision plus récente
     * écrite par un widget pendant que l'écran était en arrière-plan.
     *
     * Contrairement au pull-to-refresh, elle ne force jamais le réseau, ne
     * montre pas de spinner et respecte MANUAL. Le coalescing du repository
     * protège aussi le démarrage initial si ON_RESUME arrive en même temps.
     */
    fun refreshIfStale() {
        // Toujours remettre la présentation à l'heure, y compris hors ligne.
        // C'est le rattrapage immédiat après une longue veille du process.
        viewModelScope.launch { recalculatePresentation(clock.instant()) }
        val cities = favoriteCitiesById.values.toList()
        // Les données auxiliaires ont des TTL indépendantes du forecast. Elles
        // doivent elles aussi être revalidées à ON_RESUME, même si une lecture
        // forecast est déjà en cours ou si l'intervalle est MANUAL.
        syncMarineAvailability(cities)
        syncVigilance(cities)
        // Ne pas court-circuiter hors ligne : Room peut avoir été actualisée
        // par le widget avant la perte réseau. Le repository émet ce cache et
        // son NetworkMonitor empêche ensuite toute requête HTTP.
        if (policyRefreshJob?.isActive == true) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            if (cities.isEmpty()) return@launch
            val models = userPreferences.observeEnabledModels().first()
            val interval = userPreferences.observeRefreshInterval().first()
            val expectedConfig = models to interval
            val expectedGeneration = streamConfigGeneration
            if (lastStreamConfig != expectedConfig) return@launch
            val maxCacheAgeMs = interval.maxCacheAgeMs
            val limiter = Semaphore(MAX_CONCURRENT_CITY_REFRESHES)
            coroutineScope {
                cities.map { city ->
                    async {
                        limiter.withPermit {
                            runSuspendCatching {
                                forecastRepository.getCityForecastStream(
                                    city = city,
                                    models = models,
                                    maxCacheAgeMs = maxCacheAgeMs
                                ).collect { result ->
                                    applyForecastResult(
                                        city = city,
                                        result = result,
                                        expectedConfigGeneration = expectedGeneration,
                                        expectedModels = models.toSet()
                                    )
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        policyRefreshJob = job
        job.start()
    }

    /**
     * Synchronise les streams en cours avec la liste actuelle (favoris × modèles
     * × intervalle).
     *
     * Quand on entre dans cette fonction, les streams peuvent être désync :
     *   - Ville X retirée des favoris → on cancel son job et on purge son cache.
     *   - Ville Y ajoutée aux favoris → on lance un nouveau stream pour elle.
     *   - Les modèles ou l'intervalle ont changé → on relance TOUS les streams
     *     avec la nouvelle config.
     *
     * ─── Optimisation vs version précédente ────────────────────────────────
     * L'ancien code faisait un cancel+relaunch de TOUS les streams à chaque
     * appel, même si les modèles n'avaient pas bougé. Concrètement : ajouter
     * une nouvelle ville à la liste des favoris relançait la fetch des N-1
     * autres villes qui étaient déjà en cours de streaming — coût inutile
     * en CPU/network.
     *
     * Maintenant :
     *   - Si les modèles ET l'intervalle N'ONT PAS CHANGÉ depuis le dernier
     *     appel, on ne cancel QUE les streams des villes retirées et on
     *     lance UNIQUEMENT des streams pour les villes ajoutées. Les autres
     *     continuent leur vie.
     *   - Sinon, on refait le cancel-all comme avant : la nouvelle config
     *     s'applique à tous les streams.
     */
    private fun syncStreams(
        cities: List<City>,
        models: List<WeatherModel>,
        interval: RefreshInterval
    ) {
        favoriteCitiesById = cities.associateBy(City::id)
        val currentIds = favoriteCitiesById.keys

        // 1. Cancel les streams pour les villes retirées + purge cache. On le
        //    fait TOUJOURS, indépendamment du path d'optimisation ci-dessous.
        (streamJobs.keys + initializedCityIds).filter { it !in currentIds }.forEach { id ->
            streamJobs.remove(id)?.cancel()
            retryJobs.remove(id)?.cancel()
            initializedCityIds.remove(id)
            forecastsById.update { it - id }
            rawForecastsById.update { it - id }
            appliedConfigGenerationByCity.remove(id)
            appliedRequestedModelsByCity.remove(id)
            marineAvailabilityJobs.remove(id)?.cancel()
            marineAvailabilityCheckedAt.remove(id)
            marineAvailableIds.update { it - id }
            clearVigilanceTracking(id)
        }

        syncVigilance(cities)

        // La disponibilité Mer / côte est distincte de son activation : on la
        // détecte discrètement pour pouvoir signaler l'option dans le menu.
        syncMarineAvailability(cities)

        val config = models to interval
        val configChanged = lastStreamConfig?.let { it != config } ?: true

        // 2. Si les modèles OU l'intervalle ont changé (ou premier appel), on
        //    cancel TOUS les streams restants pour tout relancer avec la
        //    nouvelle config. Sinon on garde les streams existants et on ne
        //    lance que ceux des villes nouvellement ajoutées.
        //
        //    L'intervalle affecte `maxCacheAgeMs` au démarrage du stream : on
        //    relance donc immédiatement pour appliquer le nouveau seuil.
        if (configChanged) {
            streamConfigGeneration += 1L
            policyRefreshJob?.cancel()
            manualRefreshJob?.cancel()
            retryJobs.values.forEach { it.cancel() }
            retryJobs.clear()
            streamJobs.values.forEach { it.cancel() }
            streamJobs.clear()
            initializedCityIds.clear()
        }

        // 3. Lance les streams manquants (ceux qui n'ont pas de job actif).
        //    Si modelsChanged=true, streamJobs est vide → on lance pour toutes
        //    les villes. Si modelsChanged=false, on ne lance que pour les
        //    nouvelles.
        val maxCacheAgeMs = interval.maxCacheAgeMs
        val expectedGeneration = streamConfigGeneration
        val expectedModels = models.toSet()
        cities.forEach { city ->
            if (city.id !in initializedCityIds) {
                initializedCityIds += city.id
                val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                    val ownJob = coroutineContext[Job]
                    var completedNormally = false
                    try {
                        forecastRepository
                            .getCityForecastStream(
                                city = city,
                                models = models,
                                maxCacheAgeMs = maxCacheAgeMs
                            )
                            .collect { result ->
                                applyForecastResult(
                                    city = city,
                                    result = result,
                                    expectedConfigGeneration = expectedGeneration,
                                    expectedModels = expectedModels
                                )
                            }
                        completedNormally = true
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        // Une dépendance locale ou un calcul inattendu ne doit pas
                        // laisser cette carte indéfiniment en Loading. Une carte
                        // déjà chargée reste affichée, comme pour une erreur réseau.
                        android.util.Log.w(
                            "MeteoCompare/CityList",
                            "Forecast stream failed for city=${city.id}",
                            error
                        )
                        forecastsById.update { states ->
                            when {
                                expectedGeneration != streamConfigGeneration -> states
                                city.id !in favoriteCitiesById -> states
                                states[city.id] is ForecastState.Loaded -> states
                                else -> states + (
                                    city.id to ForecastState.Error(
                                        messageRes = R.string.error_unknown
                                    )
                                )
                            }
                        }
                    } finally {
                        // La fin normale est mémorisée : ce stream fini ne doit
                        // pas être relancé lors d'un simple ajout de favori.
                        // Une exception inattendue autorise en revanche un retry
                        // à la prochaine émission de configuration.
                        if (streamJobs[city.id] === ownJob) {
                            if (!completedNormally) initializedCityIds.remove(city.id)
                            streamJobs.remove(city.id)
                        }
                    }
                }
                streamJobs[city.id] = job
                job.start()
            }
        }

        lastStreamConfig = config
    }

    /**
     * Valide en arrière-plan la disponibilité du mode côtier pour les favoris.
     * Un cache marin encore frais est utilisé en priorité. Une décision de
     * disponibilité expire après la même fenêtre de 6 h que le cache ; elle
     * peut alors être revalidée. En cas d'échec réseau, la ville reste éligible
     * à un nouveau contrôle au prochain retour en ligne.
     */
    private fun syncMarineAvailability(cities: List<City>) {
        cities.forEach { city ->
            if (city.marineEnabled) {
                marineAvailabilityCheckedAt[city.id] = clock.millis()
                marineAvailableIds.update { it + city.id }
                return@forEach
            }
            if (isMarineAvailabilityDecisionFresh(city.id) || city.id in marineAvailabilityJobs) return@forEach

            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                val ownJob = coroutineContext[Job]
                var checkedWhileOffline = false
                try {
                    val cached = runSuspendCatching {
                        marineRepository.getFreshCached(city.id)
                    }.getOrNull()
                    if (cached != null) {
                        marineAvailabilityCheckedAt[city.id] = cached.fetchedAtEpochMs
                        if (cached.coastal) {
                            marineAvailableIds.update { it + city.id }
                            syncVigilance(listOf(city))
                        } else marineAvailableIds.update { it - city.id }
                        return@launch
                    }
                    if (!networkMonitor.isOnline()) {
                        checkedWhileOffline = true
                        // Hors ligne, une ancienne décision reste utile pour
                        // l'indication visuelle. On conserve son timestamp
                        // d'origine afin qu'elle soit revalidée dès le retour
                        // réseau au lieu de prolonger artificiellement sa TTL.
                        val stale = runSuspendCatching {
                            marineRepository.getCached(city.id)
                        }.getOrNull()
                        if (stale != null) {
                            marineAvailabilityCheckedAt[city.id] = stale.fetchedAtEpochMs
                            if (stale.coastal) {
                                marineAvailableIds.update { it + city.id }
                                syncVigilance(listOf(city))
                            } else marineAvailableIds.update { it - city.id }
                        }
                        return@launch
                    }

                    val result = runSuspendCatching {
                        marineRepository.getMarine(city, forceRefresh = false)
                    }.getOrNull()
                    when (result) {
                        is ApiResult.Success -> {
                            marineAvailabilityCheckedAt[city.id] = result.data.fetchedAtEpochMs
                            if (result.data.coastal) {
                                marineAvailableIds.update { it + city.id }
                                syncVigilance(listOf(city))
                            } else {
                                marineAvailableIds.update { it - city.id }
                            }
                        }
                        else -> Unit // retry possible au prochain changement réseau/config
                    }
                } finally {
                    if (marineAvailabilityJobs[city.id] === ownJob) {
                        marineAvailabilityJobs.remove(city.id)

                        // Une réponse de cache hors ligne peut mettre à jour la UI
                        // juste avant que ce job ne soit retiré. Si le réseau revient
                        // exactement dans cette fenêtre, le collector réseau voit
                        // encore un job actif et ne lance pas la revalidation. Une fois
                        // ce job terminé, aucun nouvel événement réseau n'arriverait :
                        // on ferme donc explicitement cette race en relançant le check
                        // si (et seulement si) ce job a réellement suivi le chemin
                        // hors ligne et que sa décision reste expirée.
                        if (
                            checkedWhileOffline &&
                            networkMonitor.isOnline() &&
                            !city.marineEnabled &&
                            !isMarineAvailabilityDecisionFresh(city.id)
                        ) {
                            syncMarineAvailability(listOf(city))
                        }
                    }
                }
            }
            marineAvailabilityJobs[city.id] = job
            job.start()
        }
    }

    private fun isMarineAvailabilityDecisionFresh(cityId: String): Boolean {
        val checkedAt = marineAvailabilityCheckedAt[cityId] ?: return false
        // Une correction NTP peut faire reculer l'horloge après le contrôle.
        // Traiter alors la décision comme toute fraîche évite un appel réseau
        // à chaque reprise jusqu'à ce que l'horloge rattrape le timestamp.
        val ageMs = (clock.millis() - checkedAt).coerceAtLeast(0L)
        return ageMs < MarineRepository.AVAILABILITY_CACHE_TTL_MS
    }

    // ─── Actions utilisateur ────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onAddCity(city: City) {
        viewModelScope.launch {
            val added = runSuspendCatching { cityRepository.addFavorite(city) }
            if (added.isFailure) {
                _actionFeedback.send(AppToastEvent.error(R.string.toast_city_add_error, city.name))
                return@launch
            }

            // Vérification immédiate à l'ajout : ne pas attendre la prochaine émission
            // DataStore/synchronisation des cards. Aucun appel n'est lancé hors France.
            if (city.isFrenchLocation) {
                launchVigilanceCheck(city, forceRefresh = true)
            } else {
                clearVigilanceTracking(city.id)
            }
            _searchQuery.value = ""
            _actionFeedback.send(AppToastEvent.success(R.string.toast_city_added, city.name))
        }
    }

    fun onRemoveCity(cityId: String) {
        viewModelScope.launch {
            val removedCity = favoriteCitiesById[cityId]
                ?: runSuspendCatching {
                    cityRepository.observeFavorites().first().firstOrNull { it.id == cityId }
                }.getOrNull()
            val vigilanceDepartment = (removedCity?.departmentCode
                ?: vigilanceById.value[cityId]?.department)
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotEmpty() }

            val removed = runSuspendCatching { cityRepository.removeFavorite(cityId) }
            if (removed.isFailure) {
                _actionFeedback.send(AppToastEvent.error(R.string.toast_city_remove_error))
                return@launch
            }
            // Nettoyage explicite après la suppression utilisateur. Une émission
            // DataStore vide transitoire ne doit jamais effacer le cache météo.
            runSuspendCatching { forecastRepository.clearCacheForCity(cityId) }
                .onFailure { error ->
                    android.util.Log.w(
                        "MeteoCompare/CityList",
                        "Unable to clear forecast cache for city=$cityId",
                        error
                    )
                }
            runSuspendCatching { marineRepository.clear(cityId) }
                .onFailure { error ->
                    android.util.Log.w(
                        "MeteoCompare/CityList",
                        "Unable to clear marine cache for city=$cityId",
                        error
                    )
                }
            clearVigilanceTracking(cityId)
            // Le repository Vigilance vérifie les favoris restants avant toute purge :
            // le cache départemental partagé est conservé tant qu'une autre ville du
            // même département (ou un favori FR legacy non encore résolu) subsiste.
            if (vigilanceDepartment != null) {
                runSuspendCatching {
                    vigilanceRepository.clearCacheForDepartment(vigilanceDepartment)
                }.onFailure { error ->
                    android.util.Log.w(
                        "MeteoCompare/CityList",
                        "Unable to clear vigilance cache for department=$vigilanceDepartment",
                        error
                    )
                }
            }
            _actionFeedback.send(
                removedCity?.let { AppToastEvent.success(R.string.toast_city_removed, it.name) }
                    ?: AppToastEvent.success(R.string.toast_city_removed_generic)
            )
        }
    }

    /** Active le mode côtier après validation du point marin, ou rafraîchit le cache existant. */
    fun onMarineAction(city: City) {
        if (city.id in marineLoadingIds.value) return
        marineAvailabilityJobs.remove(city.id)?.cancel()
        viewModelScope.launch {
            marineLoadingIds.update { it + city.id }
            try {
                runSuspendCatching {
                    when (val result = marineRepository.getMarine(city, forceRefresh = true)) {
                        is ApiResult.Success -> {
                            marineAvailabilityCheckedAt[city.id] = result.data.fetchedAtEpochMs
                            if (!result.data.coastal) {
                                marineAvailableIds.update { it - city.id }
                                _marineFeedback.send(MarineFeedback.NotCoastal)
                            } else {
                                marineAvailableIds.update { it + city.id }
                                syncVigilance(listOf(city))
                                if (!city.marineEnabled) {
                                    cityRepository.setMarineEnabled(city.id, true)
                                    _marineFeedback.send(MarineFeedback.Enabled)
                                } else {
                                    _marineFeedback.send(MarineFeedback.Refreshed)
                                }
                            }
                        }
                        is ApiResult.Error -> _marineFeedback.send(MarineFeedback.Error(result.message))
                    }
                }.onFailure {
                    _actionFeedback.send(AppToastEvent.error(R.string.toast_action_error))
                }
            } finally {
                marineLoadingIds.update { it - city.id }
            }
        }
    }

    fun onRetry(city: City) {
        if (retryJobs[city.id]?.isActive == true) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val ownJob = coroutineContext[Job]
            val previous = forecastsById.value[city.id]
            try {
                runSuspendCatching {
                    forecastsById.update { it + (city.id to ForecastState.Loading) }
                    val models = userPreferences.observeEnabledModels().first()
                    val expectedGeneration = streamConfigGeneration
                    val result = forecastRepository.refreshCityForecast(city, models = models)
                    val appliedState = applyForecastResult(
                        city = city,
                        result = result,
                        expectedConfigGeneration = expectedGeneration,
                        expectedModels = models.toSet()
                    )
                    // La Vigilance est un enrichissement secondaire : une panne
                    // de ce chemin ne doit jamais annuler un forecast réussi ni
                    // transformer son toast de succès en erreur générique.
                    runSuspendCatching {
                        refreshVigilance(city, forceRefresh = true)
                    }.onFailure { error ->
                        android.util.Log.w(
                            "MeteoCompare/CityList",
                            "Vigilance retry failed for city=${city.id}",
                            error
                        )
                    }
                    when {
                        result is ApiResult.Error -> _actionFeedback.send(
                            AppToastEvent.error(R.string.refresh_error, result.message)
                        )
                        appliedState is ForecastState.Loaded -> _actionFeedback.send(
                            AppToastEvent.success(R.string.toast_city_refresh_success, city.name)
                        )
                        appliedState is ForecastState.Error -> _actionFeedback.send(
                            AppToastEvent.error(
                                appliedState.messageRes ?: R.string.toast_action_error
                            )
                        )
                    }
                }.onFailure {
                    forecastsById.update { current ->
                        if (previous == null) current - city.id
                        else current + (city.id to previous)
                    }
                    _actionFeedback.send(AppToastEvent.error(R.string.toast_action_error))
                }
            } finally {
                if (retryJobs[city.id] === ownJob) retryJobs.remove(city.id)
            }
        }
        retryJobs[city.id] = job
        job.start()
    }

    /** Pull-to-refresh : force le réseau pour toutes les villes en parallèle. */
    fun onRefreshAll() {
        if (manualRefreshJob?.isActive == true) return
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _isRefreshing.value = true
            try {
                // L'index maintenu par syncStreams est la source de vérité.
                // uiState est WhileSubscribed et peut être momentanément en
                // retard quand un favori vient d'être ajouté ou supprimé.
                val cities = favoriteCitiesById.values.toList()
                val models = userPreferences.observeEnabledModels().first()
                val expectedGeneration = streamConfigGeneration
                val limiter = Semaphore(MAX_CONCURRENT_CITY_REFRESHES)
                val results = supervisorScope {
                    cities.map { city ->
                        async {
                            limiter.withPermit {
                                runSuspendCatching {
                                    val result = forecastRepository.refreshCityForecast(city, models)
                                    applyForecastResult(
                                        city = city,
                                        result = result,
                                        expectedConfigGeneration = expectedGeneration,
                                        expectedModels = models.toSet()
                                    )
                                    runSuspendCatching {
                                        refreshVigilance(city, forceRefresh = true)
                                    }.onFailure { error ->
                                        android.util.Log.w(
                                            "MeteoCompare/CityList",
                                            "Vigilance refresh failed for city=${city.id}",
                                            error
                                        )
                                    }
                                    result
                                }.getOrNull()
                            }
                        }
                    }.awaitAll()
                }
                val errors = results.filterIsInstance<ApiResult.Error>()
                val failedCount = errors.size + results.count { it == null }
                when {
                    results.isEmpty() -> Unit
                    failedCount == 0 -> _actionFeedback.send(
                        AppToastEvent.success(R.string.toast_refresh_all_success)
                    )
                    failedCount == results.size -> {
                        val firstMessage = errors.firstOrNull()?.message
                        _actionFeedback.send(
                            if (firstMessage != null) {
                                AppToastEvent.error(R.string.toast_refresh_all_error, firstMessage)
                            } else {
                                AppToastEvent.error(R.string.toast_action_error)
                            }
                        )
                    }
                    else -> _actionFeedback.send(
                        AppToastEvent.warning(R.string.toast_refresh_all_partial)
                    )
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                android.util.Log.w(
                    "MeteoCompare/CityList",
                    "Global forecast refresh failed",
                    error
                )
                _actionFeedback.send(AppToastEvent.error(R.string.toast_action_error))
            } finally {
                _isRefreshing.value = false
            }
        }
        manualRefreshJob = job
        job.start()
    }

    private fun syncVigilance(cities: List<City>) {
        cities.forEach { city ->
            if (!city.isFrenchLocation) {
                clearVigilanceTracking(city.id)
                return@forEach
            }
            launchVigilanceCheck(city, forceRefresh = false)
        }
    }

    private fun launchVigilanceCheck(city: City, forceRefresh: Boolean) {
        if (!city.isFrenchLocation) {
            clearVigilanceTracking(city.id)
            return
        }
        val includeCoast = includeCoastForVigilance(city)
        val previousMode = vigilanceCoastModeById[city.id]
        val existingJob = vigilanceJobs[city.id]
        if (!forceRefresh && previousMode == includeCoast && existingJob?.isActive == true) {
            return
        }

        vigilanceJobs.remove(city.id)?.cancel()
        vigilanceCoastModeById[city.id] = includeCoast
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val ownJob = coroutineContext[Job]
            try {
                refreshVigilance(city, forceRefresh = forceRefresh)
            } finally {
                if (vigilanceJobs[city.id] === ownJob) {
                    vigilanceJobs.remove(city.id)
                }
            }
        }
        vigilanceJobs[city.id] = job
        job.start()
    }

    private fun clearVigilanceTracking(cityId: String) {
        vigilanceJobs.remove(cityId)?.cancel()
        vigilanceCoastModeById.remove(cityId)
        vigilanceById.update { it - cityId }
    }

    private fun includeCoastForVigilance(city: City): Boolean =
        city.marineEnabled || city.id in marineAvailableIds.value

    private suspend fun refreshVigilance(city: City, forceRefresh: Boolean) {
        if (!city.isFrenchLocation) {
            clearVigilanceTracking(city.id)
            return
        }
        val includeCoast = includeCoastForVigilance(city)
        when (val result = vigilanceRepository.getVigilance(city, includeCoast, forceRefresh)) {
            is ApiResult.Success -> {
                val data = result.data
                vigilanceById.update { current ->
                    if (data != null && data.activeAlerts.isNotEmpty()) current + (city.id to data)
                    else current - city.id
                }
            }
            is ApiResult.Error -> Unit // La vigilance enrichit la météo sans bloquer la card principale.
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /**
     * Calcule puis applique un résultat avec une comparaison atomique de
     * fraîcheur. La comparaison finale dans StateFlow est indispensable : un
     * calcul de CityCard plus ancien peut finir après un refresh plus récent.
     */
    private suspend fun applyForecastResult(
        city: City,
        result: ApiResult<CityForecast>,
        expectedConfigGeneration: Long? = null,
        expectedModels: Set<WeatherModel>? = null
    ): ForecastState? {
        if (expectedConfigGeneration != null &&
            expectedConfigGeneration != streamConfigGeneration
        ) return null

        val mapped = toForecastState(result)
        // Le calcul quitte Main. Une préférence peut donc changer pendant ce
        // temps et annuler le stream qui a fourni [result]. Son résultat déjà
        // en cours ne doit pas revenir polluer la nouvelle configuration.
        if (expectedConfigGeneration != null &&
            expectedConfigGeneration != streamConfigGeneration
        ) return null

        val currentBefore = forecastsById.value[city.id] as? ForecastState.Loaded
        val isFirstForConfiguration = expectedConfigGeneration != null &&
            appliedConfigGenerationByCity[city.id] != expectedConfigGeneration
        val isModelSelectionTransition = isFirstForConfiguration &&
            expectedModels != null &&
            (appliedRequestedModelsByCity[city.id]?.let { it != expectedModels }
                ?: (currentBefore != null && currentBefore.sourceModels != expectedModels))

        if (result is ApiResult.Success) {
            rawForecastsById.update { current ->
                if (expectedConfigGeneration != null &&
                    expectedConfigGeneration != streamConfigGeneration
                ) return@update current
                if (city.id !in favoriteCitiesById) return@update current
                val previous = current[city.id]
                val previousAt = previous?.fetchedAt
                val incomingAt = result.data.fetchedAt
                val isOlder = previousAt != null &&
                    (incomingAt == null || incomingAt.isBefore(previousAt))
                if (isOlder && !isModelSelectionTransition) current
                else current + (city.id to result.data)
            }
        }
        forecastsById.update { states ->
            if (expectedConfigGeneration != null &&
                expectedConfigGeneration != streamConfigGeneration
            ) return@update states
            if (city.id !in favoriteCitiesById) return@update states

            val current = states[city.id]
            when {
                // Une erreur de refresh ne détruit jamais une carte déjà chargée.
                mapped is ForecastState.Error && current is ForecastState.Loaded -> states

                mapped is ForecastState.Loaded && current is ForecastState.Loaded -> {
                    val incomingAt = mapped.fetchedAt
                    val currentAt = current.fetchedAt
                    val isOlder = currentAt != null &&
                        (incomingAt == null || incomingAt.isBefore(currentAt))
                    val isSameVersion = currentAt != null &&
                        incomingAt == currentAt &&
                        current.sourceModels == mapped.sourceModels
                    if ((isOlder && !isModelSelectionTransition) ||
                        (isSameVersion && !isModelSelectionTransition)
                    ) {
                        states
                    } else {
                        states + (city.id to mapped)
                    }
                }

                else -> states + (city.id to mapped)
            }
        }
        if (result is ApiResult.Success &&
            expectedConfigGeneration != null &&
            expectedConfigGeneration == streamConfigGeneration &&
            city.id in favoriteCitiesById
        ) {
            appliedConfigGenerationByCity[city.id] = expectedConfigGeneration
            if (expectedModels != null) {
                appliedRequestedModelsByCity[city.id] = expectedModels
            }
        }
        return mapped
    }

    /**
     * Aligne l'instant de présentation sur la journée exposée par une réponse
     * Open-Meteo fraîche lorsque l'horloge Android est manifestement hors de la
     * fenêtre reçue (cas typique d'un émulateur restauré depuis un snapshot).
     *
     * Open-Meteo construit la fenêtre Forecast à partir de 00:00 « today » dans
     * le fuseau demandé. Si le device affirme être plusieurs jours avant/après,
     * utiliser son LocalDate brut transforme une réponse réseau valide en
     * `forecast_error_no_today`. On conserve l'heure locale du device et on ne
     * corrige que la composante date, uniquement pour une donnée très récente.
     * Un vieux cache reste donc refusé comme prévision du jour.
     */
    private fun alignPresentationNowToFreshForecast(
        forecast: CityForecast,
        rawNow: Instant,
        zone: java.time.ZoneId
    ): Instant {
        val availableDates = forecast.seriesByModel.values
            .asSequence()
            .flatMap { it.daily.dates.asSequence() }
            .distinct()
            .sorted()
            .toList()
        if (availableDates.isEmpty()) return rawNow

        val deviceDate = rawNow.atZone(zone).toLocalDate()
        if (deviceDate in availableDates) return rawNow

        val fetchedAt = forecast.fetchedAt ?: return rawNow
        val ageMs = kotlin.math.abs(rawNow.toEpochMilli() - fetchedAt.toEpochMilli())
        if (ageMs > MAX_FRESH_FORECAST_CLOCK_ALIGNMENT_AGE_MS) return rawNow

        // La première date de la fenêtre Forecast est le « today » de l'API.
        val apiToday = availableDates.first()
        val dayShift = apiToday.toEpochDay() - deviceDate.toEpochDay()
        if (dayShift == 0L) return rawNow

        val aligned = rawNow.atZone(zone).plusDays(dayShift).toInstant()
        if (BuildConfig.DEBUG) {
            android.util.Log.w(
                "MeteoCompare/CityList",
                "Device/API date mismatch for city=${forecast.city.id}: " +
                    "deviceDate=$deviceDate apiToday=$apiToday " +
                    "range=${availableDates.first()}..${availableDates.last()} " +
                    "timezone=${forecast.city.timezone}; aligning presentation clock"
            )
        }
        return aligned
    }

    private suspend fun toForecastState(
        result: ApiResult<CityForecast>,
        engineOverride: ForecastEngine? = null,
        calculationNow: Instant = clock.instant()
    ): ForecastState = withContext(computationDispatcher) { when (result) {
        is ApiResult.Success -> {
            // Le repository complète le fuseau depuis `timezone=auto` si un
            // favori legacy ne l'avait pas. Utiliser la ville du forecast évite
            // alors de retomber à tort sur UTC pour la Home.
            val forecastCity = result.data.city
            val zone = resolveZoneOrUtc(forecastCity.timezone)
            val now = alignPresentationNowToFreshForecast(
                forecast = result.data,
                rawNow = calculationNow,
                zone = zone
            )
            val engine = engineOverride ?: userPreferences.observeForecastEngine().first()
            val engineContext = engineContextProvider.build(result.data, engine, now)
            val today = now.atZone(zone).toLocalDate()
            val hasToday = result.data.seriesByModel.values.any { today in it.daily.dates }
            if (hasToday) {
                // ─── Sunrise/sunset : API Open-Meteo en priorité ───────────
                // Les heures astronomiques sont demandées dans le même appel
                // forecast. Le calcul NOAA local reste uniquement un secours
                // pour un ancien cache ou une réponse partielle.

                val sunriseFromApi = result.data.seriesByModel.values
                    .asSequence()
                    .mapNotNull { series ->
                        val index = series.daily.dates.indexOf(today)
                        if (index < 0) null else series.daily.sunrise.getOrNull(index)
                    }
                    .firstOrNull()
                val sunsetFromApi = result.data.seriesByModel.values
                    .asSequence()
                    .mapNotNull { series ->
                        val index = series.daily.dates.indexOf(today)
                        if (index < 0) null else series.daily.sunset.getOrNull(index)
                    }
                    .firstOrNull()
                val fallbackSun = if (sunriseFromApi == null || sunsetFromApi == null) {
                    com.meteocompare.app.domain.util.SolarTimes.compute(
                        latitude = forecastCity.latitude,
                        longitude = forecastCity.longitude,
                        date = today,
                        zone = zone
                    )
                } else {
                    null
                }
                val sunrise = sunriseFromApi?.atZone(zone)?.toLocalTime() ?: fallbackSun?.sunrise
                val sunset = sunsetFromApi?.atZone(zone)?.toLocalTime() ?: fallbackSun?.sunset

                val miniForecast = ForecastAggregates.next12h(
                    forecast = result.data,
                    now = now,
                    includeConditions = true,
                    engineContext = engineContext
                )
                val scenarios = WeatherScenarioBuilder.next12h(result.data, now)
                ForecastState.Loaded(
                    today = confidenceCalculator.dayConfidence(result.data, today, engineContext),
                    currentTemp = confidenceCalculator.currentTemperature(result.data, now, engineContext),
                    currentCondition = confidenceCalculator.currentWeatherCondition(result.data, now, engineContext),
                    currentCloudCover = confidenceCalculator.currentCloudCover(result.data, now, engineContext),
                    fetchedAt = result.data.fetchedAt,
                    calculatedAt = now,
                    sourceModels = result.data.seriesByModel.keys + result.data.errors.keys,
                    next12hTemps = miniForecast.temperatures,
                    next12hPrecipProb = miniForecast.precipitationProbabilities,
                    next12hPrecipMm = miniForecast.precipitationAmountsMm,
                    next12hConditions = miniForecast.conditions,
                    next12hScenarios = scenarios,
                    // L'agrégateur expose l'échéance réellement échantillonnée
                    // (ex. 13:00 à 12:56), donc le label ne peut plus dériver.
                    hourlyStartTime = miniForecast.startInstant
                        .atZone(zone)
                        .toLocalDateTime(),
                    sunrise = sunrise,
                    sunset = sunset
                )
            } else {
                if (BuildConfig.DEBUG) {
                    val ranges = result.data.seriesByModel.entries.joinToString { (model, series) ->
                        val dates = series.daily.dates
                        if (dates.isEmpty()) "${model.apiKey}=empty"
                        else "${model.apiKey}=${dates.first()}..${dates.last()}"
                    }
                    android.util.Log.w(
                        "MeteoCompare/CityList",
                        "No daily forecast for presentationDate=$today " +
                            "city=${forecastCity.id} timezone=${forecastCity.timezone} " +
                            "fetchedAt=${result.data.fetchedAt} ranges=[$ranges]"
                    )
                }
                ForecastState.Error(messageRes = R.string.forecast_error_no_today)
            }
        }
        is ApiResult.Error -> ForecastState.Error(result.message)
    } }

    companion object {
        /** Évite de saturer CPU, sockets et quotas lorsqu'il y a beaucoup de favoris. */
        private const val MAX_CONCURRENT_CITY_REFRESHES = 3

        /**
         * Une correction de date n'est permise que sur une réponse/cache fraîche.
         * Au-delà, l'absence de la date locale signifie réellement que le cache
         * est périmé et doit rester présenté comme tel.
         */
        private const val MAX_FRESH_FORECAST_CLOCK_ALIGNMENT_AGE_MS = 6L * 60L * 60L * 1000L
    }
}
