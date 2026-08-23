package com.meteocompare.app.ui.citylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.domain.util.ForecastAggregates
import com.meteocompare.app.domain.util.WeatherScenarioBuilder
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Clock
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
    private val _isRefreshing = MutableStateFlow(false)
    private val _isOnline = MutableStateFlow(networkMonitor.isOnline())
    private val marineLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    /** Villes validées comme côtières, indépendamment de l'activation de Mer / côte. */
    private val marineAvailableIds = MutableStateFlow<Set<String>>(emptySet())
    private val marineAvailabilityCheckedIds = mutableSetOf<String>()
    private val marineAvailabilityJobs = mutableMapOf<String, Job>()
    private val _marineFeedback = Channel<MarineFeedback>(capacity = Channel.BUFFERED)
    val marineFeedback = _marineFeedback.receiveAsFlow()

    // Tracking des jobs de stream par cityId. Sert à les canceller proprement
    // quand une ville est retirée des favoris ou quand les modèles sélectionnés
    // changent (auquel cas on relance avec la nouvelle config).
    private val streamJobs = mutableMapOf<String, Job>()

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

    private val marineUiState = combine(
        marineLoadingIds,
        marineAvailableIds
    ) { loading, available -> loading to available }

    val uiState: StateFlow<CityListUiState> = combine(
        cityRepository.observeFavorites(),
        forecastsById,
        _isRefreshing,
        _isOnline,
        marineUiState
    ) { cities, cache, refreshing, online, marineState ->
        val (marineLoading, marineAvailable) = marineState
        CityListUiState(
            items = cities.map { city ->
                CityCardState(
                    city = city,
                    forecast = cache[city.id] ?: ForecastState.Loading,
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
        viewModelScope.launch {
            networkMonitor.observeOnline().collect { online ->
                _isOnline.value = online
                if (online) syncMarineAvailability(favoriteCitiesById.values.toList())
            }
        }

        // Le moteur change uniquement la centrale dérivée. On recalcule donc
        // les cartes depuis les forecasts bruts déjà en mémoire, sans annuler
        // ni relancer les streams cache/réseau.
        viewModelScope.launch {
            userPreferences.observeForecastEngine().distinctUntilChanged().collect { engine ->
                rawForecastsById.value.forEach { (id, forecast) ->
                    val city = favoriteCitiesById[id] ?: return@forEach
                    val mapped = toForecastState(city, ApiResult.Success(forecast), engine)
                    forecastsById.update { it + (id to mapped) }
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
                applyForecastResult(city, ApiResult.Success(forecast))
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
        favoriteCitiesById = cities.associateBy(City::id)
        val currentIds = favoriteCitiesById.keys

        // 1. Cancel les streams pour les villes retirées + purge cache. On le
        //    fait TOUJOURS, indépendamment du path d'optimisation ci-dessous.
        (streamJobs.keys + initializedCityIds).filter { it !in currentIds }.forEach { id ->
            streamJobs.remove(id)?.cancel()
            initializedCityIds.remove(id)
            forecastsById.update { it - id }
            rawForecastsById.update { it - id }
            marineAvailabilityJobs.remove(id)?.cancel()
            marineAvailabilityCheckedIds.remove(id)
            marineAvailableIds.update { it - id }
        }

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
            streamJobs.values.forEach { it.cancel() }
            streamJobs.clear()
            initializedCityIds.clear()
        }

        // 3. Lance les streams manquants (ceux qui n'ont pas de job actif).
        //    Si modelsChanged=true, streamJobs est vide → on lance pour toutes
        //    les villes. Si modelsChanged=false, on ne lance que pour les
        //    nouvelles.
        val maxCacheAgeMs = if (interval == RefreshInterval.MANUAL) Long.MAX_VALUE
        else interval.millis
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
                                applyForecastResult(city, result)
                            }
                        completedNormally = true
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
     * Un cache marin existant est utilisé en priorité ; sinon une seule requête
     * par ville et par session est autorisée. En cas d'échec réseau, la ville
     * reste éligible à un nouveau contrôle au prochain retour en ligne.
     */
    private fun syncMarineAvailability(cities: List<City>) {
        cities.forEach { city ->
            if (city.marineEnabled) {
                marineAvailabilityCheckedIds += city.id
                marineAvailableIds.update { it + city.id }
                return@forEach
            }
            if (city.id in marineAvailabilityCheckedIds || city.id in marineAvailabilityJobs) return@forEach

            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                val ownJob = coroutineContext[Job]
                try {
                    val cached = runCatching { marineRepository.getCached(city.id) }.getOrNull()
                    if (cached != null) {
                        marineAvailabilityCheckedIds += city.id
                        if (cached.coastal) marineAvailableIds.update { it + city.id }
                        else marineAvailableIds.update { it - city.id }
                        return@launch
                    }
                    if (!networkMonitor.isOnline()) return@launch

                    val result = runCatching {
                        marineRepository.getMarine(city, forceRefresh = false)
                    }.getOrNull()
                    when (result) {
                        is ApiResult.Success -> {
                            marineAvailabilityCheckedIds += city.id
                            if (result.data.coastal) {
                                marineAvailableIds.update { it + city.id }
                            } else {
                                marineAvailableIds.update { it - city.id }
                            }
                        }
                        else -> Unit // retry possible au prochain changement réseau/config
                    }
                } finally {
                    if (marineAvailabilityJobs[city.id] === ownJob) {
                        marineAvailabilityJobs.remove(city.id)
                    }
                }
            }
            marineAvailabilityJobs[city.id] = job
            job.start()
        }
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
            marineRepository.clear(cityId)
        }
    }

    /** Active le mode côtier après validation du point marin, ou rafraîchit le cache existant. */
    fun onMarineAction(city: City) {
        if (city.id in marineLoadingIds.value) return
        marineAvailabilityJobs.remove(city.id)?.cancel()
        viewModelScope.launch {
            marineLoadingIds.update { it + city.id }
            try {
                when (val result = marineRepository.getMarine(city, forceRefresh = true)) {
                    is ApiResult.Success -> {
                        marineAvailabilityCheckedIds += city.id
                        if (!result.data.coastal) {
                            marineAvailableIds.update { it - city.id }
                            _marineFeedback.send(MarineFeedback.NotCoastal)
                        } else {
                            marineAvailableIds.update { it + city.id }
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
            } finally {
                marineLoadingIds.update { it - city.id }
            }
        }
    }

    fun onRetry(city: City) {
        viewModelScope.launch {
            forecastsById.update { it + (city.id to ForecastState.Loading) }
            val models = userPreferences.observeEnabledModels().first()
            val result = forecastRepository.refreshCityForecast(city, models = models)
            applyForecastResult(city, result)
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
                                applyForecastResult(city, result)
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

    /**
     * Calcule puis applique un résultat avec une comparaison atomique de
     * fraîcheur. La comparaison finale dans StateFlow est indispensable : un
     * calcul de CityCard plus ancien peut finir après un refresh plus récent.
     */
    private suspend fun applyForecastResult(
        city: City,
        result: ApiResult<CityForecast>
    ) {
        if (result is ApiResult.Success) {
            rawForecastsById.update { current ->
                val previous = current[city.id]
                val previousAt = previous?.fetchedAt
                val incomingAt = result.data.fetchedAt
                if (previousAt != null && (incomingAt == null || incomingAt.isBefore(previousAt))) current
                else current + (city.id to result.data)
            }
        }
        val mapped = toForecastState(city, result)
        forecastsById.update { states ->
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
                    if (isOlder || isSameVersion) {
                        states
                    } else {
                        states + (city.id to mapped)
                    }
                }

                else -> states + (city.id to mapped)
            }
        }
    }

    private suspend fun toForecastState(
        city: City,
        result: ApiResult<CityForecast>,
        engineOverride: ForecastEngine? = null
    ): ForecastState = withContext(computationDispatcher) { when (result) {
        is ApiResult.Success -> {
            val now = clock.instant()
            val engine = engineOverride ?: userPreferences.observeForecastEngine().first()
            val engineContext = engineContextProvider.build(result.data, engine, now)
            // Le repository complète le fuseau depuis `timezone=auto` si un
            // favori legacy ne l'avait pas. Utiliser la ville du forecast évite
            // alors de retomber à tort sur UTC pour la Home.
            val forecastCity = result.data.city
            val zone = resolveZoneOrUtc(forecastCity.timezone)
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

                val miniForecast = ForecastAggregates.next12h(result.data, now, engineContext = engineContext)
                val scenarios = WeatherScenarioBuilder.next12h(result.data, now)
                ForecastState.Loaded(
                    today = confidenceCalculator.dayConfidence(result.data, today, engineContext),
                    currentTemp = confidenceCalculator.currentTemperature(result.data, now, engineContext),
                    currentCondition = confidenceCalculator.currentWeatherCondition(result.data, now),
                    currentCloudCover = confidenceCalculator.currentCloudCover(result.data, now, engineContext),
                    fetchedAt = result.data.fetchedAt,
                    sourceModels = result.data.seriesByModel.keys + result.data.errors.keys,
                    next12hTemps = miniForecast.temperatures,
                    next12hPrecipProb = miniForecast.precipitationProbabilities,
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
                ForecastState.Error(messageRes = R.string.forecast_error_no_today)
            }
        }
        is ApiResult.Error -> ForecastState.Error(result.message)
    } }

    companion object {
        /** Évite de saturer CPU, sockets et quotas lorsqu'il y a beaucoup de favoris. */
        private const val MAX_CONCURRENT_CITY_REFRESHES = 3
    }
}