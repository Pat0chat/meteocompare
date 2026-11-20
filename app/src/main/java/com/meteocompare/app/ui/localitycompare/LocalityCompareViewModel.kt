package com.meteocompare.app.ui.localitycompare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.CustomUnitPreferences
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.UvIndexRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

internal sealed interface CompareForecastState {
    data object Loading : CompareForecastState
    data class Ready(val forecast: CityForecast) : CompareForecastState
    data class Failed(val message: String) : CompareForecastState
}

internal data class LocalityCompareState(
    val favorites: List<City> = emptyList(),
    val selectedIds: List<String> = emptyList(),
    val forecasts: Map<String, CompareForecastState> = emptyMap(),
    val uvForecasts: Map<String, UvForecast> = emptyMap(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val customUnits: CustomUnitPreferences = CustomUnitPreferences()
) {
    val selectedCities: List<City> get() = selectedIds.mapNotNull { id -> favorites.firstOrNull { it.id == id } }
}

/**
 * Observes favorites and uses the same cache-aware model requests as Home.
 * Each city is loaded independently; an unavailable destination must not hide
 * the others. Removing a favorite cancels its flow before another can display it.
 */
@HiltViewModel
internal class LocalityCompareViewModel @Inject constructor(
    private val cities: CityRepository,
    private val forecasts: ForecastRepository,
    private val uvRepository: UvIndexRepository,
    private val preferences: UserPreferencesRepository,
    val calculator: ConfidenceCalculator
) : ViewModel() {
    private val favorites = cities.observeFavorites().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )
    private val selectedIds = MutableStateFlow<List<String>>(emptyList())
    private val loaded = MutableStateFlow<Map<String, CompareForecastState>>(emptyMap())
    private val loadedUv = MutableStateFlow<Map<String, UvForecast>>(emptyMap())
    private val units = preferences.observeUnitSystem().stateIn(
        viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC
    )
    private val manualRefresh = MutableStateFlow(0)
    private val activeCityJobs = mutableMapOf<String, Job>()
    private val activeRequestKeys = mutableMapOf<String, CityRequestKey>()
    private var handledRefresh = 0

    val uiState: StateFlow<LocalityCompareState> = combine(
        favorites, selectedIds, loaded, loadedUv, units
    ) { all, selected, data, uv, unit ->
        LocalityCompareState(all, selected, data, uv, unit)
    }.combine(preferences.observeCustomUnits()) { state, custom ->
        state.copy(customUnits = custom)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalityCompareState())

    init {
        viewModelScope.launch {
            favorites.collect { available ->
                val restored = restoreComparedCities(
                    available.map(City::id), selectedIds.value
                )
                if (restored != selectedIds.value) selectedIds.value = restored.take(3)
            }
        }
        viewModelScope.launch {
            combine(
                favorites,
                selectedIds,
                preferences.observeEnabledModels(),
                preferences.observeRefreshInterval(),
                manualRefresh
            ) { all, selected, models, interval, refresh ->
                Request(
                    cities = selected.mapNotNull { id -> all.firstOrNull { it.id == id } },
                    models = models,
                    interval = interval,
                    refresh = refresh
                )
            }.collect { request ->
                // Diff the selected city requests instead of cancelling and reloading
                // every city when a chip is toggled or the favorites list reorders.
                val currentIds = request.cities.mapTo(hashSetOf()) { it.id }
                for (id in activeRequestKeys.keys.toList()) {
                    if (id !in currentIds) {
                        activeCityJobs.remove(id)?.cancel()
                        activeRequestKeys.remove(id)
                    }
                }
                loaded.update { current -> current.filterKeys { it in currentIds } }
                loadedUv.update { current -> current.filterKeys { it in currentIds } }
                val forceNetwork = request.refresh > handledRefresh
                handledRefresh = maxOf(handledRefresh, request.refresh)

                request.cities.forEach { city ->
                    val key = CityRequestKey(city, request.models, request.interval, request.refresh)
                    if (activeRequestKeys[city.id] == key) return@forEach
                    activeCityJobs.remove(city.id)?.cancel()
                    activeRequestKeys[city.id] = key
                    loaded.update { current -> current + (city.id to CompareForecastState.Loading) }
                    if (request.models.isEmpty()) {
                        loaded.update { current -> current +
                            (city.id to CompareForecastState.Failed("No weather models enabled")) }
                        return@forEach
                    }
                    // Job stays active until its cache/network stream completes.
                    // Completed keys are kept so unrelated emissions cannot reload it.
                    activeCityJobs[city.id] = viewModelScope.launch {
                        launch {
                            // UV is shared across Home/Detail/Chart View; do not request
                            // it again when the manual weather-model refresh is pressed.
                            if (city.id !in loadedUv.value) {
                                try {
                                    val uv = uvRepository.get(city)
                                    if (uv != null && activeRequestKeys[city.id] == key) {
                                        loadedUv.update { it + (city.id to uv) }
                                    }
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    // UV is optional and must not fail model forecasts.
                                }
                            }
                        }
                        launch {
                            try {
                                forecasts.getCityForecastStream(
                                    city = city,
                                    models = request.models,
                                    forceRefresh = forceNetwork,
                                    maxCacheAgeMs = request.interval.maxCacheAgeMs
                                ).collect { result ->
                                    val state = when (result) {
                                        is ApiResult.Success -> CompareForecastState.Ready(result.data)
                                        is ApiResult.Error -> CompareForecastState.Failed(result.message)
                                    }
                                    if (activeRequestKeys[city.id] == key) {
                                        loaded.update { it + (city.id to state) }
                                    }
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (error: Exception) {
                                if (activeRequestKeys[city.id] == key) {
                                    loaded.update { it + (city.id to
                                        CompareForecastState.Failed(error.message.orEmpty())) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun toggleCity(id: String) {
        if (favorites.value.none { it.id == id }) return
        selectedIds.update { current -> toggleComparedCity(current, id) }
    }

    fun refresh() { manualRefresh.update { it + 1 } }

    private data class CityRequestKey(
        val city: City,
        val models: List<WeatherModel>,
        val interval: RefreshInterval,
        val refresh: Int
    )

    private data class Request(
        val cities: List<City>,
        val models: List<WeatherModel>,
        val interval: RefreshInterval,
        val refresh: Int
    )
}
