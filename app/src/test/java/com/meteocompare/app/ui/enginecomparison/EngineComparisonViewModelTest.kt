package com.meteocompare.app.ui.enginecomparison

import android.content.Context
import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EngineComparisonBuilder
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class EngineComparisonViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-08-23T05:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris"
    )
    private val modelsFlow = MutableStateFlow(WeatherModel.MVP_SELECTION)
    private val intervalFlow = MutableStateFlow(RefreshInterval.DEFAULT)
    private val engineFlow = MutableStateFlow(ForecastEngine.MULTI_CONSENSUS)
    private val forecast = buildForecast()

    private val cityRepository: CityRepository = mockk(relaxed = true) {
        every { observeFavorites() } returns flowOf(listOf(city))
    }
    private val forecastRepository: ForecastRepository = mockk(relaxed = true) {
        every { getCityForecastStream(city, any(), any(), any(), any()) } returns
            flowOf(ApiResult.Success(forecast))
    }
    private val preferences: UserPreferencesRepository = mockk(relaxed = true) {
        every { observeEnabledModels() } returns modelsFlow
        every { observeRefreshInterval() } returns intervalFlow
        every { observeForecastEngine() } returns engineFlow
    }
    private val contextProvider: ForecastEngineContextProvider = mockk(relaxed = true) {
        coEvery { build(forecast, ForecastEngine.ADAPTIVE, now) } returns
            ForecastEngineContext(engine = ForecastEngine.ADAPTIVE)
    }
    private val appContext: Context = mockk(relaxed = true)
    private val builder = EngineComparisonBuilder(ConfidenceCalculator(EqualWeighting()))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        modelsFlow.value = WeatherModel.MVP_SELECTION
        intervalFlow.value = RefreshInterval.DEFAULT
        engineFlow.value = ForecastEngine.MULTI_CONSENSUS
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `missing favorite exposes localized error without opening forecast stream`() = runTest(dispatcher) {
        every { cityRepository.observeFavorites() } returns flowOf(emptyList())
        every { appContext.getString(com.meteocompare.app.R.string.city_not_found_in_favorites) } returns "City missing"

        val viewModel = EngineComparisonViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to city.id)),
            cityRepository = cityRepository,
            forecastRepository = forecastRepository,
            preferences = preferences,
            contextProvider = contextProvider,
            comparisonBuilder = builder,
            clock = clock,
            appContext = appContext
        )

        viewModel.state.test {
            var state = awaitItem()
            while (state is EngineComparisonUiState.Loading) state = awaitItem()
            assertEquals(EngineComparisonUiState.Error("City missing"), state)
            verify(exactly = 0) {
                forecastRepository.getCityForecastStream(any(), any(), any(), any(), any())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing selected engine only updates highlight without reopening forecast stream`() =
        runTest(dispatcher) {
            val viewModel = EngineComparisonViewModel(
                savedStateHandle = SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to city.id)),
                cityRepository = cityRepository,
                forecastRepository = forecastRepository,
                preferences = preferences,
                contextProvider = contextProvider,
                comparisonBuilder = builder,
                clock = clock,
                appContext = appContext
            )

            viewModel.state.test {
                var loaded = awaitItem()
                while (loaded !is EngineComparisonUiState.Loaded) loaded = awaitItem()
                assertEquals(ForecastEngine.MULTI_CONSENSUS, loaded.selectedEngine)
                assertEquals(7, loaded.days.size)
                verify(exactly = 1) {
                    forecastRepository.getCityForecastStream(city, any(), any(), any(), any())
                }

                engineFlow.value = ForecastEngine.CALIBRATION
                var updated = awaitItem()
                while (updated !is EngineComparisonUiState.Loaded ||
                    updated.selectedEngine != ForecastEngine.CALIBRATION
                ) {
                    updated = awaitItem()
                }

                assertEquals(ForecastEngine.CALIBRATION, updated.selectedEngine)
                assertEquals(loaded.days, updated.days)
                verify(exactly = 1) {
                    forecastRepository.getCityForecastStream(city, any(), any(), any(), any())
                }
            }
        }

    private fun buildForecast(): CityForecast {
        val dates = (23..29).map { LocalDate.of(2026, 8, it) }
        val values = linkedMapOf(
            WeatherModel.GFS to 20.0,
            WeatherModel.ECMWF to 21.0,
            WeatherModel.ARPEGE_EUROPE to 22.0,
            WeatherModel.UKMO_GLOBAL to 23.0
        )
        return CityForecast(
            city = city,
            seriesByModel = values.mapValues { (model, base) ->
                ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList()),
                    daily = DailyForecast(
                        dates = dates,
                        tempMax = dates.map { base },
                        tempMin = dates.map { base - 8.0 },
                        precipitationSum = dates.map { 2.0 },
                        windSpeedMax = dates.map { 20.0 },
                        precipitationProbabilityMax = dates.map { 60 },
                        windGustsMax = dates.map { 35.0 }
                    )
                )
            }
        )
    }
}
