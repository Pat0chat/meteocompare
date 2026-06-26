package com.meteocompare.app.ui.citylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

@HiltViewModel
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CityListViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val confidenceCalculator: ConfidenceCalculator,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val forecastsById = MutableStateFlow<Map<String, ForecastState>>(emptyMap())
    private val _isRefreshing = MutableStateFlow(false)

    // Tracking des jobs de stream par cityId. Sert à les canceller proprement
    // quand une ville est retirée des favoris ou quand les modèles sélectionnés
    // changent (auquel cas on relance avec la nouvelle config).
    private val streamJobs = mutableMapOf<String, Job>()

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
        // Le cœur : on combine favoris + modèles sélectionnés.
        // Quand l'une des deux sources change, on réajuste les streams.
        viewModelScope.launch {
            combine(
                cityRepository.observeFavorites(),
                userPreferences.observeEnabledModels()
            ) { cities, models -> cities to models }
                .collect { (cities, models) ->
                    syncStreams(cities, models)
                }
        }
    }

    /**
     * Synchronise les streams en cours avec la liste actuelle (favoris × modèles).
     *
     * Quand on entre dans cette fonction, les streams peuvent être désync :
     *   - Ville X retirée des favoris → on cancel son job et on purge son cache.
     *   - Ville Y ajoutée aux favoris → on lance un nouveau stream pour elle.
     *   - Les modèles sélectionnés ont changé → on relance TOUS les streams
     *     avec la nouvelle config.
     *
     * Pour simplifier : à chaque appel, on cancel tout et on relance avec la
     * config actuelle. Coût accepté : quand le user change un modèle dans les
     * settings, tous les fetches en cours sont annulés et relancés. Pour un
     * MVP c'est acceptable — les vrais use cases (ajouter une ville) ne touchent
     * pas à la sélection de modèles.
     */
    private fun syncStreams(cities: List<City>, models: List<WeatherModel>) {
        val currentIds = cities.map { it.id }.toSet()

        // 1. Cancel les streams pour les villes retirées + purge cache
        streamJobs.keys.filter { it !in currentIds }.forEach { id ->
            streamJobs.remove(id)?.cancel()
            forecastsById.update { it - id }
            viewModelScope.launch { forecastRepository.clearCacheForCity(id) }
        }

        // 2. Cancel tous les streams restants (modèles ont peut-être changé)
        streamJobs.values.forEach { it.cancel() }
        streamJobs.clear()

        // 3. Relance pour chaque ville
        cities.forEach { city ->
            streamJobs[city.id] = viewModelScope.launch {
                forecastRepository
                    .getCityForecastStream(city, models = models)
                    .collect { result ->
                        forecastsById.update { it + (city.id to toForecastState(result)) }
                    }
            }
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
        viewModelScope.launch { cityRepository.removeFavorite(cityId) }
    }

    fun onRetry(city: City) {
        viewModelScope.launch {
            forecastsById.update { it + (city.id to ForecastState.Loading) }
            val models = userPreferences.observeEnabledModels().first()
            val result = forecastRepository.refreshCityForecast(city, models = models)
            forecastsById.update { it + (city.id to toForecastState(result)) }
        }
    }

    /** Pull-to-refresh : force le réseau pour toutes les villes en parallèle. */
    fun onRefreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val cities = uiState.value.items.map { it.city }
                val models = userPreferences.observeEnabledModels().first()
                coroutineScope {
                    cities.map { city ->
                        async {
                            val result = forecastRepository.refreshCityForecast(city, models)
                            forecastsById.update { it + (city.id to toForecastState(result)) }
                        }
                    }.awaitAll()
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun toForecastState(result: ApiResult<CityForecast>): ForecastState = when (result) {
        is ApiResult.Success -> {
            val today = result.data.seriesByModel.values
                .firstOrNull()?.daily?.dates?.firstOrNull()
            if (today != null) {
                ForecastState.Loaded(
                    today = confidenceCalculator.dayConfidence(result.data, today),
                    currentTemp = confidenceCalculator.currentTemperature(result.data)
                )
            } else {
                ForecastState.Error("Aucune donnée journalière reçue")
            }
        }
        is ApiResult.Error -> ForecastState.Error(result.message)
    }
}
