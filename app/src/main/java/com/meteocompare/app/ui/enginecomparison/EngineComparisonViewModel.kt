package com.meteocompare.app.ui.enginecomparison

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.toUserMessage
import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.EngineComparisonBuilder
import com.meteocompare.app.domain.usecase.EngineComparisonDay
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.domain.util.forecastPresentationTicks
import com.meteocompare.app.ui.components.AppToastEvent
import com.meteocompare.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        val days: List<EngineComparisonDay>,
        val forecast: CityForecast,
        val calculatedAt: Instant
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
    private val clock: Clock,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {
    private val cityId: String = checkNotNull(savedStateHandle[Destinations.CITY_DETAIL_ARG])
    private val _state = MutableStateFlow<EngineComparisonUiState>(EngineComparisonUiState.Loading)
    val state: StateFlow<EngineComparisonUiState> = _state.asStateFlow()
    private val _feedback = Channel<AppToastEvent>(capacity = Channel.BUFFERED)
    val feedback = _feedback.receiveAsFlow()
    private var loadJob: Job? = null
    private val presentationMutex = Mutex()
    private var latestForecast: CityForecast? = null
    private var calculatedAt: Instant? = null

    init {
        observePresentationDate()
        load()
    }

    fun retry() = load(notifyResult = true)

    /** Relit Room au retour au premier plan, sans masquer le contenu chargé. */
    fun refreshIfStale() = load(showLoading = false)

    private fun load(
        showLoading: Boolean = true,
        notifyResult: Boolean = false
    ) {
        loadJob?.cancel()
        if (showLoading || _state.value !is EngineComparisonUiState.Loaded) {
            _state.value = EngineComparisonUiState.Loading
        }
        loadJob = viewModelScope.launch {
            var resultNotified = false
            runSuspendCatching {
                val city = cityRepository.observeFavorites().first().firstOrNull { it.id == cityId }
                if (city == null) {
                    val message = appContext.getString(R.string.city_not_found_in_favorites)
                    _state.value = EngineComparisonUiState.Error(message)
                    if (notifyResult) {
                        _feedback.send(AppToastEvent.error(R.string.refresh_error, message))
                    }
                    return@runSuspendCatching
                }
                combine(
                    preferences.observeEnabledModels(),
                    preferences.observeRefreshInterval()
                ) { models, interval -> models to interval }
                    .flatMapLatest { (models, interval) ->
                        val maxAge = interval.maxCacheAgeMs
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
                                val context = contextProvider.build(
                                    result.data,
                                    ForecastEngine.ADAPTIVE,
                                    now
                                )
                                EngineComparisonForecastState.Data(
                                    cityName = result.data.city.name,
                                    days = comparisonBuilder.build(result.data, context, now),
                                    forecast = result.data,
                                    calculatedAt = now
                                )
                            }
                            is ApiResult.Error -> EngineComparisonForecastState.Error(result.message)
                        }
                    }
                    .combine(preferences.observeForecastEngine()) { forecastState, selectedEngine ->
                        forecastState to selectedEngine
                    }
                    .collect { (forecastState, selectedEngine) ->
                        presentationMutex.withLock {
                            when (forecastState) {
                                is EngineComparisonForecastState.Data -> {
                                    latestForecast = forecastState.forecast
                                    calculatedAt = forecastState.calculatedAt
                                    _state.value = EngineComparisonUiState.Loaded(
                                        cityName = forecastState.cityName,
                                        selectedEngine = selectedEngine,
                                        days = forecastState.days
                                    )
                                    if (notifyResult && !resultNotified) {
                                        resultNotified = true
                                        _feedback.send(AppToastEvent.success(R.string.refresh_success))
                                    }
                                }
                                is EngineComparisonForecastState.Error -> {
                                    if (_state.value !is EngineComparisonUiState.Loaded) {
                                        _state.value = EngineComparisonUiState.Error(forecastState.message)
                                    }
                                    if (notifyResult && !resultNotified) {
                                        resultNotified = true
                                        _feedback.send(
                                            AppToastEvent.error(
                                                R.string.refresh_error,
                                                forecastState.message
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
            }.onFailure { error ->
                val message = error.toUserMessage(appContext)
                android.util.Log.w(
                    "MeteoCompare/EngineComparison",
                    "Comparison forecast stream failed for city=$cityId",
                    error
                )
                if (_state.value !is EngineComparisonUiState.Loaded) {
                    _state.value = EngineComparisonUiState.Error(message)
                }
                if (notifyResult && !resultNotified) {
                    _feedback.send(AppToastEvent.error(R.string.refresh_error, message))
                }
            }
        }
    }

    /**
     * La page filtre les jours antérieurs au jour civil de la ville. Elle doit
     * donc avancer à minuit même si le forecast brut reste en cache. Le ticker
     * commun sonde à la minute, mais ce calcul ne repart qu'au changement de
     * date locale et ne déclenche jamais de requête météo.
     */
    private fun observePresentationDate() {
        viewModelScope.launch {
            forecastPresentationTicks(clock).collect { now ->
                runSuspendCatching {
                    presentationMutex.withLock {
                        val forecast = latestForecast ?: return@withLock
                        val previous = calculatedAt ?: return@withLock
                        if (previous.localDateIn(forecast.city.timezone) ==
                            now.localDateIn(forecast.city.timezone)
                        ) return@withLock

                        val context = contextProvider.build(forecast, ForecastEngine.ADAPTIVE, now)
                        val days = comparisonBuilder.build(forecast, context, now)
                        val current = _state.value as? EngineComparisonUiState.Loaded
                            ?: return@withLock
                        calculatedAt = now
                        _state.value = current.copy(
                            cityName = forecast.city.name,
                            days = days
                        )
                    }
                }.onFailure { error ->
                    android.util.Log.w(
                        "MeteoCompare/EngineComparison",
                        "Unable to update comparison presentation",
                        error
                    )
                }
            }
        }
    }
}
