package com.meteocompare.app.ui.enginecomparison

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.EngineComparisonBuilder
import com.meteocompare.app.domain.usecase.EngineComparisonDay
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

sealed interface EngineComparisonUiState {
    data object Loading : EngineComparisonUiState
    data class Loaded(
        val cityName: String,
        val selectedEngine: ForecastEngine,
        val days: List<EngineComparisonDay>
    ) : EngineComparisonUiState
    data class Error(val message: String) : EngineComparisonUiState
}

private sealed interface EngineComparisonForecastState {
    data class Data(
        val cityName: String,
        val days: List<EngineComparisonDay>
    ) : EngineComparisonForecastState

    data class Error(val message: String) : EngineComparisonForecastState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EngineComparisonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val preferences: UserPreferencesRepository,
    private val contextProvider: ForecastEngineContextProvider,
    private val comparisonBuilder: EngineComparisonBuilder,
    private val clock: Clock
) : ViewModel() {
    private val cityId: String = checkNotNull(savedStateHandle[Destinations.CITY_DETAIL_ARG])
    private val _state = MutableStateFlow<EngineComparisonUiState>(EngineComparisonUiState.Loading)
    val state: StateFlow<EngineComparisonUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val city = cityRepository.observeFavorites().first().firstOrNull { it.id == cityId }
            if (city == null) {
                _state.value = EngineComparisonUiState.Error("City not found")
                return@launch
            }
            combine(
                preferences.observeEnabledModels(),
                preferences.observeRefreshInterval()
            ) { models, interval -> models to interval }
                .flatMapLatest { (models, interval) ->
                    val maxAge = if (interval == RefreshInterval.MANUAL) Long.MAX_VALUE else interval.millis
                    // Seuls les paramètres qui modifient réellement la requête météo
                    // rouvrent le stream. Une réponse de l'ancienne sélection de modèles
                    // est annulée par flatMapLatest et ne peut pas réécrire l'écran.
                    forecastRepository.getCityForecastStream(city, models, maxCacheAgeMs = maxAge)
                }
                .map { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            val now = clock.instant()
                            // ADAPTIVE force le chargement du profil complet ; le builder
                            // substitue ensuite chacun des quatre moteurs sur le même contexte.
                            val context = contextProvider.build(result.data, ForecastEngine.ADAPTIVE, now)
                            EngineComparisonForecastState.Data(
                                cityName = result.data.city.name,
                                days = comparisonBuilder.build(result.data, context, now)
                            )
                        }
                        is ApiResult.Error -> EngineComparisonForecastState.Error(result.message)
                    }
                }
                .combine(preferences.observeForecastEngine()) { forecastState, selectedEngine ->
                    forecastState to selectedEngine
                }
                .collect { (forecastState, selectedEngine) ->
                    when (forecastState) {
                        is EngineComparisonForecastState.Data -> {
                            _state.value = EngineComparisonUiState.Loaded(
                                cityName = forecastState.cityName,
                                selectedEngine = selectedEngine,
                                days = forecastState.days
                            )
                        }
                        is EngineComparisonForecastState.Error ->
                            if (_state.value !is EngineComparisonUiState.Loaded) {
                                _state.value = EngineComparisonUiState.Error(forecastState.message)
                            }
                    }
                }
        }
    }
}
