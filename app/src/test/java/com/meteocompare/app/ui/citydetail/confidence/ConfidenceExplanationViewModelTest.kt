package com.meteocompare.app.ui.citydetail.confidence

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val forecastResults = MutableStateFlow<ApiResult<CityForecast>>(ApiResult.Success(forecast()))

    private val context: Context = mockk(relaxed = true) {
        every { getString(any<Int>()) } returns "localized-error"
    }
    private val cityRepository: CityRepository = mockk(relaxed = true) {
        every { observeFavorites() } returns favorites
    }
    private val forecastRepository: ForecastRepository = mockk(relaxed = true) {
        every { getCityForecastStream(any(), any(), any(), any(), any()) } returns forecastResults
    }
    private val preferences: UserPreferencesRepository = mockk(relaxed = true) {
        every { observeEnabledModels() } returns enabledModels
    }
    private val calculator = ConfidenceCalculator(EqualWeighting())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        favorites.value = listOf(paris)
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
        confidenceCalculator = calculator
    )

    private fun forecast(): CityForecast {
        val models = listOf(WeatherModel.GFS, WeatherModel.ICON_EU)
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
                    tempMax = listOf(25.0 + index),
                    tempMin = listOf(15.0 + index),
                    precipitationSum = listOf(index.toDouble()),
                    windSpeedMax = listOf(20.0 + index)
                )
            )
        }.toMap()
        return CityForecast(paris, series)
    }
}
