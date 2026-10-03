package com.meteocompare.app.ui.graphicview

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastCacheEntity
import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.data.remote.dto.DailyDto
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.HourlyDto
import com.meteocompare.app.data.repository.ForecastRepositoryImpl
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exercises the real repository/cache policy, counting HTTP calls rather than flow subscriptions. */
@OptIn(ExperimentalCoroutinesApi::class)
class GraphicForecastRefreshTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = MutableClock(Instant.parse("2026-09-16T08:00:00Z"))
    private val city = City("graphic-city", "London", country = "UK", latitude = 51.5,
        longitude = -0.1, timezone = "UTC", countryCode = "GB")
    private val json = Json { ignoreUnknownKeys = true }
    private val api = mockk<OpenMeteoApi>()
    private val interval = MutableStateFlow(RefreshInterval.DEFAULT)
    private val viewModels = mutableListOf<GraphicForecastViewModel>()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test fun ten_day_cache_is_extended_once_and_reused_on_resume() = runTest(dispatcher) {
        val vm = viewModel(cachedDays = 10)
        runCurrent()
        assertDownloads(1)
        assertEquals(240, (vm.state.value as GraphicForecastUiState.Loaded).points.size)
        repeat(2) { vm.refreshIfStale(); runCurrent() }
        assertDownloads(1)
    }

    @Test fun initial_load_without_cache_then_resume_downloads_once() = runTest(dispatcher) {
        val vm = viewModel(cachedDays = 0)
        vm.refreshIfStale()
        runCurrent()
        assertDownloads(1)
        assertTrue(vm.state.value is GraphicForecastUiState.Loaded)
    }

    @Test fun resume_during_inflight_download_shares_the_request() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(cachedDays = 0, gate = gate)
        vm.refreshIfStale()
        runCurrent()
        assertDownloads(1)
        gate.complete(Unit)
        runCurrent()
        vm.refreshIfStale()
        runCurrent()
        assertDownloads(1)
        assertTrue(vm.state.value is GraphicForecastUiState.Loaded)
    }

    @Test fun fresh_complete_cache_avoids_network_but_retry_forces_it() = runTest(dispatcher) {
        val vm = viewModel(cachedDays = 11)
        vm.refreshIfStale()
        runCurrent()
        assertDownloads(0)
        vm.retry()
        runCurrent()
        assertDownloads(1)
    }

    @Test fun retry_does_not_force_later_automatic_preference_changes() = runTest(dispatcher) {
        val vm = viewModel(cachedDays = 11)
        vm.retry()
        runCurrent()
        assertDownloads(1)
        interval.value = RefreshInterval.HOURS_6
        runCurrent()
        assertDownloads(1)
    }

    @Test fun expired_cache_refreshes_on_resume() = runTest(dispatcher) {
        val vm = viewModel(cachedDays = 11)
        assertDownloads(0)
        clock.current = clock.current.plusSeconds(3_601)
        vm.refreshIfStale()
        runCurrent()
        assertDownloads(1)
    }

    @Test fun changing_refresh_interval_rechecks_cache_age() = runTest(dispatcher) {
        viewModel(cachedDays = 11, ageMs = 30 * 60_000L)
        assertDownloads(0)
        interval.value = RefreshInterval.MINUTES_15
        runCurrent()
        assertDownloads(1)
    }

    @Test fun manual_mode_keeps_old_cache_until_explicit_retry() = runTest(dispatcher) {
        interval.value = RefreshInterval.MANUAL
        val vm = viewModel(cachedDays = 11, ageMs = 24 * 3_600_000L)
        vm.refreshIfStale()
        runCurrent()
        assertDownloads(0)
        vm.retry()
        runCurrent()
        assertDownloads(1)
    }

    private fun viewModel(cachedDays: Int, ageMs: Long = 0, gate: CompletableDeferred<Unit>? = null): GraphicForecastViewModel {
        var rows = if (cachedDays == 0) emptyList() else listOf(ForecastCacheEntity(
            city.id, WeatherModel.GFS.apiKey, clock.millis() - ageMs,
            json.encodeToString(ForecastResponseDto.serializer(), forecast(cachedDays))
        ))
        val cache = mockk<ForecastCacheDao>(relaxed = true)
        coEvery { cache.getForCity(city.id) } coAnswers { rows }
        coEvery { cache.replaceRequestedModels(any(), any(), any(), any()) } coAnswers {
            rows = thirdArg<List<ForecastCacheEntity>>()
        }
        val response = json.decodeFromString(BatchedForecastResponseDto.serializer(),
            json.encodeToString(ForecastResponseDto.serializer(), forecast(11)))
        coEvery { api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            gate?.await()
            response
        }
        val context = mockk<Context>(relaxed = true)
        val repository = ForecastRepositoryImpl(
            api, ForecastMapper(), cache, json,
            mockk<NetworkMonitor> { every { isOnline() } returns true }, clock,
            mockk(relaxed = true), context, dispatcher, dispatcher
        )
        return GraphicForecastViewModel(
            SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to city.id)),
            mockk<CityRepository> { every { observeFavorites() } returns flowOf(listOf(city)) },
            repository, mockk(relaxed = true),
            mockk<UserPreferencesRepository> {
                every { observeEnabledModels() } returns flowOf(listOf(WeatherModel.GFS))
                every { observeRefreshInterval() } returns interval
                every { observeForecastEngine() } returns flowOf(ForecastEngine.MULTI_CONSENSUS)
            },
            ForecastEngineContextProvider(mockk(relaxed = true)), clock, context, dispatcher
        ).also(viewModels::add)
    }

    private fun assertDownloads(count: Int) {
        coVerify(exactly = count) {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), 11, any(), any(), any())
        }
    }

    private fun forecast(days: Int): ForecastResponseDto {
        val date = LocalDate.of(2026, 9, 16)
        return ForecastResponseDto(
            timezone = "UTC",
            hourly = HourlyDto(
                time = List(days * 24) { date.atStartOfDay().plusHours(it.toLong()).toString() },
                temperature2m = List(days * 24) { 15.0 }
            ),
            daily = DailyDto(
                time = List(days) { date.plusDays(it.toLong()).toString() },
                temperature2mMax = List(days) { 20.0 },
                temperature2mMin = List(days) { 10.0 }
            )
        )
    }

    private class MutableClock(var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }
}
