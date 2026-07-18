package com.meteocompare.app.ui.citydetail.confidence

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ConfidenceExplanationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val date = LocalDate.of(2026, 7, 15)
    private val paris = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris"
    )
    private val favorites = MutableStateFlow(listOf(paris))
    private val enabledModels = MutableStateFlow(listOf(WeatherModel.GFS, WeatherModel.ICON_EU))
    private val refreshInterval = MutableStateFlow(RefreshInterval.DEFAULT)
    private val forecastResults = MutableStateFlow<ApiResult<CityForecast>>(ApiResult.Success(forecast()))
    private val forecastUpdates = MutableSharedFlow<CityForecast>(extraBufferCapacity = 1)

    private val context: Context = mockk(relaxed = true) {
        every { getString(any<Int>()) } returns "localized-error"
    }
    private val cityRepository: CityRepository = mockk(relaxed = true) {
        every { observeFavorites() } returns favorites
    }
    private val forecastRepository: ForecastRepository = mockk(relaxed = true) {
        every { getCityForecastStream(any(), any(), any(), any(), any()) } returns forecastResults
        every { observeForecastUpdates() } returns forecastUpdates
    }
    private val preferences: UserPreferencesRepository = mockk(relaxed = true) {
        every { observeEnabledModels() } returns enabledModels
        every { observeRefreshInterval() } returns refreshInterval
    }
    private val calculator = ConfidenceCalculator(EqualWeighting())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        favorites.value = listOf(paris)
        enabledModels.value = listOf(WeatherModel.GFS, WeatherModel.ICON_EU)
        refreshInterval.value = RefreshInterval.DEFAULT
        forecastResults.value = ApiResult.Success(forecast())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid route date returns localized error without loading forecast`() = runTest(dispatcher) {
        val viewModel = viewModel(dateArg = "not-a-date")

        assertEquals(
            ConfidenceExplanationUiState.Error("localized-error"),
            viewModel.state.value
        )
        verify(exactly = 0) {
            forecastRepository.getCityForecastStream(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `missing favorite returns localized error`() = runTest(dispatcher) {
        favorites.value = emptyList()
        val viewModel = viewModel()

        assertEquals(
            ConfidenceExplanationUiState.Error("localized-error"),
            viewModel.state.value
        )
    }

    @Test
    fun `success exposes all available variables and contributing models`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val loaded = viewModel.state.value as ConfidenceExplanationUiState.Loaded

        assertEquals(paris, loaded.city)
        assertEquals(date, loaded.date)
        assertEquals(
            listOf(
                VariableKind.TEMP_MAX,
                VariableKind.TEMP_MIN,
                VariableKind.PRECIPITATION,
                VariableKind.WIND_MAX
            ),
            loaded.variableBreakdowns.map(VariableBreakdown::kind)
        )
        assertEquals(
            listOf(WeatherModel.ICON_EU, WeatherModel.GFS),
            loaded.contributingModels
        )
        assertTrue(loaded.dayConfidence.tempMax != null)
    }

    @Test
    fun `uses the user refresh interval to avoid a redundant network fetch`() = runTest(dispatcher) {
        refreshInterval.value = RefreshInterval.HOURS_3

        viewModel()

        verify(exactly = 1) {
            forecastRepository.getCityForecastStream(
                eq(paris),
                any(),
                eq(7),
                eq(false),
                eq(RefreshInterval.HOURS_3.millis)
            )
        }
    }


    @Test
    fun `changing enabled models reloads an open explanation with equal timestamp`() =
        runTest(dispatcher) {
            val fetchedAt = Instant.parse("2026-07-15T12:00:00Z")
            val initial = forecast(
                models = listOf(WeatherModel.GFS, WeatherModel.ICON_EU),
                baseTemp = 25.0,
                fetchedAt = fetchedAt
            )
            val changed = forecast(
                models = listOf(WeatherModel.GFS),
                baseTemp = 40.0,
                fetchedAt = fetchedAt
            )

            every {
                forecastRepository.getCityForecastStream(
                    eq(paris),
                    eq(listOf(WeatherModel.GFS, WeatherModel.ICON_EU)),
                    any(),
                    any(),
                    any()
                )
            } returns flowOf(ApiResult.Success(initial))
            every {
                forecastRepository.getCityForecastStream(
                    eq(paris),
                    eq(listOf(WeatherModel.GFS)),
                    any(),
                    any(),
                    any()
                )
            } returns flowOf(ApiResult.Success(changed))

            val viewModel = viewModel()
            assertEquals(
                listOf(WeatherModel.ICON_EU, WeatherModel.GFS),
                (viewModel.state.value as ConfidenceExplanationUiState.Loaded).contributingModels
            )

            enabledModels.value = listOf(WeatherModel.GFS)

            val loaded = viewModel.state.value as ConfidenceExplanationUiState.Loaded
            assertEquals(listOf(WeatherModel.GFS), loaded.contributingModels)
            assertEquals(
                listOf(40.0),
                loaded.variableBreakdowns
                    .first { it.kind == VariableKind.TEMP_MAX }
                    .perModel
                    .map(ModelValue::value)
            )

            verify(exactly = 1) {
                forecastRepository.getCityForecastStream(
                    eq(paris),
                    eq(listOf(WeatherModel.GFS)),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `manual refresh from another screen updates an already open explanation`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val base = forecast()
            val refreshed = base.copy(
                seriesByModel = base.seriesByModel.mapValues { (_, series) ->
                    series.copy(daily = series.daily.copy(tempMax = listOf(42.0)))
                },
                fetchedAt = Instant.parse("2026-07-15T12:10:00Z")
            )

            forecastUpdates.emit(refreshed)

            val loaded = viewModel.state.value as ConfidenceExplanationUiState.Loaded
            val maxTemps = loaded.variableBreakdowns
                .first { it.kind == VariableKind.TEMP_MAX }
                .perModel
                .map(ModelValue::value)
            assertEquals(listOf(42.0, 42.0), maxTemps)
        }

    @Test
    fun `network error before data is displayed as error`() = runTest(dispatcher) {
        forecastResults.value = ApiResult.Error(IllegalStateException("network"), "network")
        val viewModel = viewModel()

        assertEquals(ConfidenceExplanationUiState.Error("network"), viewModel.state.value)
    }

    @Test
    fun `network error after cached success keeps loaded state`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value is ConfidenceExplanationUiState.Loaded)

        forecastResults.value = ApiResult.Error(IllegalStateException("network"), "network")

        assertTrue(viewModel.state.value is ConfidenceExplanationUiState.Loaded)
    }

    private fun viewModel(
        cityId: String = paris.id,
        dateArg: String = date.toString()
    ): ConfidenceExplanationViewModel = ConfidenceExplanationViewModel(
        context = context,
        savedStateHandle = SavedStateHandle(
            mapOf(
                Destinations.CITY_DETAIL_ARG to cityId,
                Destinations.CONFIDENCE_DATE_ARG to dateArg
            )
        ),
        cityRepository = cityRepository,
        forecastRepository = forecastRepository,
        userPreferences = preferences,
        confidenceCalculator = calculator,
        computationDispatcher = dispatcher
    )

    private fun forecast(
        models: List<WeatherModel> = listOf(WeatherModel.GFS, WeatherModel.ICON_EU),
        baseTemp: Double = 25.0,
        fetchedAt: Instant? = null
    ): CityForecast {
        val series = models.mapIndexed { index, model ->
            model to ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = listOf(Instant.parse("2026-07-15T12:00:00Z")),
                    temperature2m = listOf(20.0 + index),
                    precipitation = listOf(index.toDouble()),
                    windSpeed10m = listOf(15.0 + index)
                ),
                daily = DailyForecast(
                    dates = listOf(date),
                    tempMax = listOf(baseTemp + index),
                    tempMin = listOf(baseTemp - 10.0 + index),
                    precipitationSum = listOf(index.toDouble()),
                    windSpeedMax = listOf(20.0 + index)
                )
            )
        }.toMap()
        return CityForecast(paris, series, fetchedAt = fetchedAt)
    }
}
