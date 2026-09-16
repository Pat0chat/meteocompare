package com.meteocompare.app.ui.graphicview

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.toUserMessage
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.repository.VigilanceRepository
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.domain.util.SolarTimes
import com.meteocompare.app.ui.citydetail.DisplayMode
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.buildSimplifiedTimeline
import com.meteocompare.app.ui.citydetail.resolveCityZone
import com.meteocompare.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class GraphicSolarWindow(
    val sunrise: Instant?,
    val sunset: Instant?
)

internal data class GraphicModelValue(
    val modelName: String,
    val temperatureC: Double?,
    val precipitationMm: Double?,
    val precipitationProbabilityPercent: Int?,
    val windKmh: Double?,
    val windGustKmh: Double?,
    val windDirectionDeg: Int?
)

internal sealed interface GraphicForecastUiState {
    data object Loading : GraphicForecastUiState

    data class Loaded(
        val city: City,
        val points: List<SimplifiedTimelinePoint>,
        val solarByDate: Map<LocalDate, GraphicSolarWindow>,
        val modelValuesByInstant: Map<Instant, List<GraphicModelValue>>,
        val vigilance: VigilanceForecast? = null,
        val calculatedAt: Instant
    ) : GraphicForecastUiState

    data class Error(val message: String) : GraphicForecastUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GraphicForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val vigilanceRepository: VigilanceRepository,
    private val preferences: UserPreferencesRepository,
    private val contextProvider: ForecastEngineContextProvider,
    private val clock: Clock,
    @param:ApplicationContext private val appContext: Context,
    @param:DefaultDispatcher private val computationDispatcher: CoroutineDispatcher
) : ViewModel() {
    private val cityId: String = checkNotNull(savedStateHandle[Destinations.CITY_DETAIL_ARG])
    private val _state = MutableStateFlow<GraphicForecastUiState>(GraphicForecastUiState.Loading)
    internal val state: StateFlow<GraphicForecastUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var vigilanceJob: Job? = null
    private var latestVigilance: VigilanceForecast? = null

    init {
        load()
    }

    fun retry() = load(showLoading = true)

    /** Relit d'abord le cache au retour au premier plan et ne masque pas le contenu courant. */
    fun refreshIfStale() = load(showLoading = false)

    private fun load(showLoading: Boolean = true) {
        loadJob?.cancel()
        vigilanceJob?.cancel()
        if (showLoading || _state.value !is GraphicForecastUiState.Loaded) {
            _state.value = GraphicForecastUiState.Loading
        }

        loadJob = viewModelScope.launch {
            try {
                val city = cityRepository.observeFavorites().first().firstOrNull { it.id == cityId }
                if (city == null) {
                    _state.value = GraphicForecastUiState.Error(
                        appContext.getString(R.string.city_not_found_in_favorites)
                    )
                    return@launch
                }

                launchVigilance(city)

                preferences.observeEnabledModels()
                    .flatMapLatest { models ->
                        // Huit jours civils garantissent une fenêtre glissante de
                        // 168 h depuis l'heure courante pour les modèles qui ont
                        // cet horizon. maxCacheAgeMs=null conserve l'émission du
                        // cache, puis déclenche un fetch afin de compléter la fin
                        // de la timeline même si la page détail vient d'être lue.
                        forecastRepository.getCityForecastStream(
                            city = city,
                            models = models,
                            forecastDays = GRAPHIC_REQUEST_DAYS,
                            maxCacheAgeMs = null
                        )
                    }
                    .combine(preferences.observeForecastEngine()) { result, engine -> result to engine }
                    .collect { (result, engine) ->
                        when (result) {
                            is ApiResult.Success -> {
                                val now = clock.instant()
                                val loaded = withContext(computationDispatcher) {
                                    val engineContext = contextProvider.build(result.data, engine, now)
                                    buildLoadedState(
                                        forecast = result.data,
                                        now = now,
                                        engineContext = engineContext,
                                        vigilance = latestVigilance
                                    )
                                }
                                _state.value = loaded
                            }

                            is ApiResult.Error -> if (_state.value !is GraphicForecastUiState.Loaded) {
                                _state.value = GraphicForecastUiState.Error(result.message)
                            }
                        }
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                android.util.Log.w(
                    "MeteoCompare/GraphicForecast",
                    "Graphic forecast stream failed for city=$cityId",
                    error
                )
                if (_state.value !is GraphicForecastUiState.Loaded) {
                    _state.value = GraphicForecastUiState.Error(error.toUserMessage(appContext))
                }
            }
        }
    }

    private fun launchVigilance(city: City) {
        latestVigilance = null
        if (!city.isFrenchLocation) return
        vigilanceJob = viewModelScope.launch {
            runSuspendCatching {
                vigilanceRepository.getVigilance(
                    city = city,
                    includeCoast = city.marineEnabled,
                    forceRefresh = false
                )
            }.onSuccess { result ->
                if (result is ApiResult.Success) {
                    latestVigilance = result.data
                    _state.update { current ->
                        if (current is GraphicForecastUiState.Loaded) {
                            current.copy(vigilance = result.data)
                        } else current
                    }
                }
            }.onFailure { error ->
                android.util.Log.w(
                    "MeteoCompare/GraphicForecast",
                    "Unable to load vigilance for city=$cityId",
                    error
                )
            }
        }
    }

    private fun buildLoadedState(
        forecast: CityForecast,
        now: Instant,
        engineContext: com.meteocompare.app.domain.model.ForecastEngineContext,
        vigilance: VigilanceForecast?
    ): GraphicForecastUiState.Loaded {
        val points = buildSimplifiedTimeline(
            forecast = forecast,
            mode = DisplayMode.HOURLY,
            now = now,
            engineContext = engineContext,
            hourlyHorizonHours = GRAPHIC_HORIZON_HOURS
        ).take(GRAPHIC_HORIZON_HOURS)

        return GraphicForecastUiState.Loaded(
            city = forecast.city,
            points = points,
            solarByDate = buildSolarWindows(forecast, points),
            modelValuesByInstant = buildModelValues(forecast, points),
            vigilance = vigilance,
            calculatedAt = now
        )
    }

    private fun buildSolarWindows(
        forecast: CityForecast,
        points: List<SimplifiedTimelinePoint>
    ): Map<LocalDate, GraphicSolarWindow> {
        val zone = resolveCityZone(forecast.city.timezone)
        val dates = points.mapNotNull { it.instant?.atZone(zone)?.toLocalDate() }.distinct()
        return dates.associateWith { date ->
            val apiWindow = forecast.seriesByModel.values.firstNotNullOfOrNull { series ->
                val index = series.daily.dates.indexOf(date)
                if (index < 0) return@firstNotNullOfOrNull null
                val sunrise = series.daily.sunrise.getOrNull(index)
                val sunset = series.daily.sunset.getOrNull(index)
                if (sunrise != null && sunset != null && sunset.isAfter(sunrise)) {
                    GraphicSolarWindow(sunrise, sunset)
                } else null
            }
            apiWindow ?: run {
                val fallback = SolarTimes.compute(
                    latitude = forecast.city.latitude,
                    longitude = forecast.city.longitude,
                    date = date,
                    zone = zone
                )
                GraphicSolarWindow(
                    sunrise = fallback.sunrise?.let { date.atTime(it).atZone(zone).toInstant() },
                    sunset = fallback.sunset?.let { date.atTime(it).atZone(zone).toInstant() }
                )
            }
        }
    }

    private fun buildModelValues(
        forecast: CityForecast,
        points: List<SimplifiedTimelinePoint>
    ): Map<Instant, List<GraphicModelValue>> {
        val indexes = forecast.seriesByModel.mapValues { (_, series) ->
            series.hourly.timestamps.withIndex().associate { (index, timestamp) -> timestamp to index }
        }
        return points.mapNotNull pointLoop@ { point ->
            val instant = point.instant ?: return@pointLoop null
            val rows = forecast.seriesByModel.mapNotNull modelLoop@ { (model, series) ->
                val index = indexes[model]?.get(instant) ?: return@modelLoop null
                val temperature = series.hourly.temperature2m.getOrNull(index)
                val precipitation = series.hourly.precipitation.getOrNull(index)
                val probability = series.hourly.precipitationProbability.getOrNull(index)
                val wind = series.hourly.windSpeed10m.getOrNull(index)
                val gust = series.hourly.windGusts10m.getOrNull(index)
                val direction = series.hourly.windDirection10m.getOrNull(index)
                if (temperature == null && precipitation == null && probability == null &&
                    wind == null && gust == null && direction == null
                ) return@modelLoop null
                GraphicModelValue(
                    modelName = model.displayName,
                    temperatureC = temperature,
                    precipitationMm = precipitation,
                    precipitationProbabilityPercent = probability,
                    windKmh = wind,
                    windGustKmh = gust,
                    windDirectionDeg = direction
                )
            }.sortedBy { it.modelName }
            instant to rows
        }.toMap()
    }

    private companion object {
        const val GRAPHIC_HORIZON_HOURS = 24 * 7
        const val GRAPHIC_REQUEST_DAYS = 8
    }
}
