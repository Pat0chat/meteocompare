package com.meteocompare.app.ui.localitycompare

import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UvIndexRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Ensure changing UI selection never restarts weather streams of unchanged cities. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalityCompareViewModelReuseTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val paris = City("p", "Paris", null, "France", 48.0, 2.0, "UTC")
    private val lyon = City("l", "Lyon", null, "France", 45.0, 4.0, "UTC")
    private val lille = City("x", "Lille", null, "France", 50.0, 3.0, "UTC")
    private val favorites = MutableStateFlow(listOf(paris, lyon, lille))
    private val models = MutableStateFlow(listOf(WeatherModel.GFS))
    private val interval = MutableStateFlow(RefreshInterval.DEFAULT)
    private val units = MutableStateFlow(UnitSystem.METRIC)
    private val cityRepository = mockk<CityRepository> {
        every { observeFavorites() } returns favorites
    }
    private val forecastRepository = mockk<ForecastRepository> {
        every { getCityForecastStream(any(), any(), any(), any(), any()) } answers {
            val city = firstArg<City>()
            flowOf(ApiResult.Success(CityForecast(city, emptyMap())))
        }
    }
    private val uvRepository = mockk<UvIndexRepository>()
    private val preferences = mockk<UserPreferencesRepository> {
        every { observeEnabledModels() } returns models
        every { observeRefreshInterval() } returns interval
        every { observeUnitSystem() } returns units
        every { observeCustomUnits() } returns flowOf(com.meteocompare.app.domain.model.CustomUnitPreferences())
    }
    private lateinit var viewModel: LocalityCompareViewModel

    @Before fun setup() {
        coEvery { uvRepository.get(any<City>()) } returns null
        Dispatchers.setMain(dispatcher)
        viewModel = LocalityCompareViewModel(
            cityRepository, forecastRepository, uvRepository, preferences,
            ConfidenceCalculator(EqualWeighting())
        )
    }
    @After fun teardown() { viewModel.viewModelScope.cancel(); Dispatchers.resetMain() }

    @Test fun selectingThirdCityOrReorderingFavoritesOnlyLoadsNewCity() = runTest(dispatcher) {
        verify(exactly = 1) { forecastRepository.getCityForecastStream(eq(paris), any(), any(), any(), any()) }
        verify(exactly = 1) { forecastRepository.getCityForecastStream(eq(lyon), any(), any(), any(), any()) }
        viewModel.toggleCity(lille.id)
        verify(exactly = 1) { forecastRepository.getCityForecastStream(eq(lille), any(), any(), any(), any()) }
        favorites.value = listOf(lille, lyon, paris)
        verify(exactly = 1) { forecastRepository.getCityForecastStream(eq(paris), any(), any(), any(), any()) }
        verify(exactly = 1) { forecastRepository.getCityForecastStream(eq(lyon), any(), any(), any(), any()) }
        verify(exactly = 1) { forecastRepository.getCityForecastStream(eq(lille), any(), any(), any(), any()) }
    }

    @Test fun changingUnitsNeverReloadsWeatherOrUv() = runTest(dispatcher) {
        units.value = UnitSystem.IMPERIAL
        verify(exactly = 2) { forecastRepository.getCityForecastStream(any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { uvRepository.get(any()) }
    }

    @Test fun manualRefreshReloadsEachSelectedCityExactlyOnce() = runTest(dispatcher) {
        viewModel.refresh()
        verify(exactly = 2) { forecastRepository.getCityForecastStream(eq(paris), any(), any(), any(), any()) }
        verify(exactly = 2) { forecastRepository.getCityForecastStream(eq(lyon), any(), any(), any(), any()) }
    }
}
