package com.meteocompare.app.ui.graphicview

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.repository.VigilanceRepository
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GraphicForecastViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-09-16T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val city = City(
        id = "graphic-city",
        name = "Graphic City",
        country = "United Kingdom",
        latitude = 51.5,
        longitude = -0.1,
        timezone = "UTC",
        countryCode = "GB"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun graphic_view_requests_eight_days_and_exposes_a_full_168_hour_timeline() = runTest(dispatcher) {
        val forecast = sevenDayForecast()
        val cityRepository = mockk<CityRepository> {
            every { observeFavorites() } returns flowOf(listOf(city))
        }
        val forecastRepository = mockk<ForecastRepository> {
            every {
                getCityForecastStream(
                    city = any(),
                    models = any(),
                    forecastDays = any(),
                    forceRefresh = any(),
                    maxCacheAgeMs = null
                )
            } returns flowOf(ApiResult.Success(forecast))
        }
        val preferences = mockk<UserPreferencesRepository> {
            every { observeEnabledModels() } returns flowOf(listOf(WeatherModel.GFS))
            every { observeForecastEngine() } returns flowOf(ForecastEngine.MULTI_CONSENSUS)
        }
        val contextProvider = ForecastEngineContextProvider(mockk(relaxed = true))
        val viewModel = GraphicForecastViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to city.id)),
            cityRepository = cityRepository,
            forecastRepository = forecastRepository,
            vigilanceRepository = mockk<VigilanceRepository>(relaxed = true),
            preferences = preferences,
            contextProvider = contextProvider,
            clock = clock,
            appContext = mockk<Context>(relaxed = true),
            computationDispatcher = dispatcher
        )

        try {
            runCurrent()
            val state = viewModel.state.value as GraphicForecastUiState.Loaded

            assertEquals(168, state.points.size)
            assertEquals(now, state.points.first().instant)
            assertEquals(now.plusSeconds(167 * 3_600L), state.points.last().instant)
            assertEquals(168, state.modelValuesByInstant.size)
            assertTrue(state.points.all { it.condition != null })

            verify(exactly = 1) {
                forecastRepository.getCityForecastStream(
                    city = city,
                    models = listOf(WeatherModel.GFS),
                    forecastDays = 8,
                    forceRefresh = false,
                    maxCacheAgeMs = null
                )
            }
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun sevenDayForecast(): CityForecast {
        val timestamps = List(168) { index -> now.plusSeconds(index * 3_600L) }
        val hourly = HourlyForecast(
            timestamps = timestamps,
            temperature2m = List(168) { 12.0 + (it % 24) * 0.5 },
            precipitation = List(168) { if (it % 12 == 0) 1.5 else 0.0 },
            precipitationProbability = List(168) { if (it % 12 == 0) 75 else 10 },
            windSpeed10m = List(168) { 10.0 + it % 8 },
            windGusts10m = List(168) { 20.0 + it % 10 },
            windDirection10m = List(168) { (it * 15) % 360 },
            cloudCover = List(168) { (it * 5) % 100 },
            weatherCode = List(168) { if (it % 12 == 0) 61 else 1 }
        )
        return CityForecast(
            city = city,
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(
                    model = WeatherModel.GFS,
                    hourly = hourly,
                    daily = DailyForecast(
                        dates = emptyList(),
                        tempMax = emptyList(),
                        tempMin = emptyList(),
                        precipitationSum = emptyList(),
                        windSpeedMax = emptyList()
                    )
                )
            )
        )
    }
}
