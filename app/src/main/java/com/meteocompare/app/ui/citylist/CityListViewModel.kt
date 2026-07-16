package com.meteocompare.app.ui.citylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.util.ForecastAggregates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CityListViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val confidenceCalculator: ConfidenceCalculator,
    private val userPreferences: UserPreferencesRepository,
    @param:DefaultDispatcher private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val forecastsById = MutableStateFlow<Map<String, ForecastState>>(emptyMap())
    private val _isRefreshing = MutableStateFlow(false)

    // Tracking des jobs de stream par cityId. Sert à les canceller proprement
    // quand une ville est retirée des favoris ou quand les modèles sélectionnés
    // changent (auquel cas on relance avec la nouvelle config).
    private val streamJobs = mutableMapOf<String, Job>()

    // Snapshot de la dernière configuration de stream. L'intervalle fait
    // partie de la clé : il détermine la fraîcheur acceptable du cache au
    // moment de la souscription.
    private var lastStreamConfig: Pair<List<WeatherModel>, RefreshInterval>? = null

    val uiState: StateFlow<CityListUiState> = combine(
        cityRepository.observeFavorites(),
        forecastsById,
        _isRefreshing
    ) { cities, cache, refreshing ->
        CityListUiState(
            items = cities.map { city ->
                CityCardState(
                    city = city,
                    forecast = cache[city.id] ?: ForecastState.Loading
                )
            },
            isRefreshing = refreshing
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
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.length < 2) {
                _isSearching.value = false
                flowOf(emptyList())
            } else {
                flow {
                    _isSearching.value = true
                    _searchError.value = null
                    when (val result = cityRepository.searchCities(query)) {
                        is ApiResult.Success -> emit(result.data)
                        is ApiResult.Error -> {
                            _searchError.value = result.message
                            emit(emptyList())
                        }
                    }
                    _isSearching.value = false
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
        val currentIds = cities.map { it.id }.toSet()

        // 1. Cancel les streams pour les villes retirées + purge cache. On le
        //    fait TOUJOURS, indépendamment du path d'optimisation ci-dessous.
        streamJobs.keys.filter { it !in currentIds }.forEach { id ->
            streamJobs.remove(id)?.cancel()
            forecastsById.update { it - id }
        }

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
            streamJobs.values.forEach { it.cancel() }
            streamJobs.clear()
        }

        // 3. Lance les streams manquants (ceux qui n'ont pas de job actif).
        //    Si modelsChanged=true, streamJobs est vide → on lance pour toutes
        //    les villes. Si modelsChanged=false, on ne lance que pour les
        //    nouvelles.
        val maxCacheAgeMs = if (interval == RefreshInterval.MANUAL) Long.MAX_VALUE
        else interval.millis
        cities.forEach { city ->
            val existing = streamJobs[city.id]
            if (existing?.isActive != true) {
                val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                    val ownJob = coroutineContext[Job]
                    try {
                        forecastRepository
                            .getCityForecastStream(
                                city = city,
                                models = models,
                                maxCacheAgeMs = maxCacheAgeMs
                            )
                            .collect { result ->
                                val mapped = toForecastState(city, result)
                                forecastsById.update { it + (city.id to mapped) }
                            }
                    } finally {
                        // Ne jamais laisser de Job terminé dans la registry.
                        if (streamJobs[city.id] === ownJob) streamJobs.remove(city.id)
                    }
                }
                streamJobs[city.id] = job
                job.start()
            }
        }

        lastStreamConfig = config
    }

    // ─── Actions utilisateur ────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onAddCity(city: City) {
        viewModelScope.launch {
            cityRepository.addFavorite(city)
            _searchQuery.value = ""
        }
    }

    fun onRemoveCity(cityId: String) {
        viewModelScope.launch {
            cityRepository.removeFavorite(cityId)
            // Nettoyage explicite après la suppression utilisateur. Une émission
            // DataStore vide transitoire ne doit jamais effacer le cache.
            forecastRepository.clearCacheForCity(cityId)
        }
    }

    fun onRetry(city: City) {
        viewModelScope.launch {
            forecastsById.update { it + (city.id to ForecastState.Loading) }
            val models = userPreferences.observeEnabledModels().first()
            val result = forecastRepository.refreshCityForecast(city, models = models)
            val mapped = toForecastState(city, result)
            forecastsById.update { it + (city.id to mapped) }
        }
    }

    /** Pull-to-refresh : force le réseau pour toutes les villes en parallèle. */
    fun onRefreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val cities = uiState.value.items.map { it.city }
                val models = userPreferences.observeEnabledModels().first()
                val limiter = Semaphore(MAX_CONCURRENT_CITY_REFRESHES)
                coroutineScope {
                    cities.map { city ->
                        async {
                            limiter.withPermit {
                                val result = forecastRepository.refreshCityForecast(city, models)
                                val mapped = toForecastState(city, result)
                                forecastsById.update { it + (city.id to mapped) }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private suspend fun toForecastState(
        city: City,
        result: ApiResult<CityForecast>
    ): ForecastState = withContext(computationDispatcher) { when (result) {
        is ApiResult.Success -> {
            val today = result.data.seriesByModel.values
                .firstOrNull()?.daily?.dates?.firstOrNull()
            if (today != null) {
                // ─── Sunrise/sunset via calcul local NOAA ─────────────────
                // Résout automatiquement le fuseau : le champ `city.timezone`
                // peut être null si l'utilisateur a ajouté la ville avant que
                // le geocoder ne renvoie le fuseau — on retombe alors sur UTC
                // (safe fallback : l'heure sera à ±quelques h de la réalité,
                // mais mieux que crasher).
                val zone = runCatching {
                    java.time.ZoneId.of(city.timezone ?: "UTC")
                }.getOrDefault(java.time.ZoneId.of("UTC"))
                val sun = com.meteocompare.app.domain.util.SolarTimes.compute(
                    latitude = city.latitude,
                    longitude = city.longitude,
                    date = today,
                    zone = zone
                )

                val now = java.time.Instant.now()
                val miniForecast = ForecastAggregates.next12h(result.data, now)
                ForecastState.Loaded(
                    today = confidenceCalculator.dayConfidence(result.data, today),
                    currentTemp = confidenceCalculator.currentTemperature(result.data),
                    currentCondition = confidenceCalculator.currentWeatherCondition(result.data),
                    currentCloudCover = confidenceCalculator.currentCloudCover(result.data),
                    fetchedAt = result.data.fetchedAt,
                    next12hTemps = miniForecast.temperatures,
                    next12hPrecipProb = miniForecast.precipitationProbabilities,
                    // Même instant de référence que les agrégats ci-dessus :
                    // les valeurs et les labels horaires restent alignés.
                    hourlyStartTime = now
                        .atZone(zone)
                        .toLocalDateTime()
                        .truncatedTo(java.time.temporal.ChronoUnit.HOURS),
                    sunrise = sun.sunrise,
                    sunset = sun.sunset
                )
            } else {
                ForecastState.Error("Aucune donnée journalière reçue")
            }
        }
        is ApiResult.Error -> ForecastState.Error(result.message)
    } }

    companion object {
        /** Évite de saturer CPU, sockets et quotas lorsqu'il y a beaucoup de favoris. */
        private const val MAX_CONCURRENT_CITY_REFRESHES = 3
    }
}