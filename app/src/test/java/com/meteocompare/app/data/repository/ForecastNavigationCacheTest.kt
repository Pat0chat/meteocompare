package com.meteocompare.app.data.repository

import android.content.Context
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastCacheEntity
import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.data.remote.dto.DailyDto
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.HourlyDto
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastDisplayHorizon
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.ForecastRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Counts HTTP calls through the real repository and a cache that retains writes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastNavigationCacheTest {
    @Test fun city_then_detail_then_graph_downloads_eleven_days_once() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler))
        val city = fixture.loadDefault()
        val detail = fixture.load(ForecastDisplayHorizon.DETAIL_REQUEST_DAYS)
        val graph = fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)

        assertEquals(listOf(11), fixture.requestedDays)
        assertEquals(11, city.seriesByModel.getValue(WeatherModel.GFS).daily.size)
        assertEquals(city, detail)
        assertEquals(city, graph)
    }

    @Test fun overlapping_city_detail_and_graph_loads_share_one_download() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler))
        fixture.gate = CompletableDeferred()
        val city = async { fixture.loadDefault() }
        val detail = async { fixture.load(ForecastDisplayHorizon.DETAIL_REQUEST_DAYS) }
        val graph = async { fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS) }
        runCurrent()
        assertEquals(listOf(11), fixture.requestedDays)

        fixture.gate!!.complete(Unit)
        assertEquals(city.await(), detail.await())
        assertEquals(city.await(), graph.await())
        assertEquals(listOf(11), fixture.requestedDays)
    }

    @Test fun manual_refresh_from_list_or_detail_preserves_the_graph_horizon() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler))
        fixture.loadDefault()
        assertTrue(fixture.repository.refreshCityForecast(fixture.city, fixture.models) is ApiResult.Success)
        fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)
        assertEquals(listOf(11, 11), fixture.requestedDays)

        assertTrue(fixture.repository.refreshCityForecast(
            fixture.city, fixture.models, ForecastDisplayHorizon.DETAIL_REQUEST_DAYS
        ) is ApiResult.Success)
        fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)
        assertEquals(listOf(11, 11, 11), fixture.requestedDays)
    }

    @Test fun legacy_seven_day_cache_is_extended_by_the_first_default_load() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler), cachedDays = 7)
        fixture.loadDefault()
        fixture.load(ForecastDisplayHorizon.DETAIL_REQUEST_DAYS)
        fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)
        assertEquals(listOf(11), fixture.requestedDays)
    }

    @Test fun legacy_ten_day_cache_is_extended_by_the_first_default_load() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler), cachedDays = 10)
        fixture.loadDefault()
        assertEquals(listOf(11), fixture.requestedDays)
        fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)
        assertEquals(listOf(11), fixture.requestedDays)
    }

    @Test fun fresh_eleven_day_cache_serves_all_three_loads_without_network() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler), cachedDays = 11)
        fixture.loadDefault()
        fixture.load(ForecastDisplayHorizon.DETAIL_REQUEST_DAYS)
        fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)
        assertTrue(fixture.requestedDays.isEmpty())
    }

    @Test fun short_range_models_keep_their_available_horizon_and_reuse_cache() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler), model = WeatherModel.AROME_FRANCE_HD)
        fixture.loadDefault()
        fixture.load(ForecastDisplayHorizon.DETAIL_REQUEST_DAYS)
        fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)
        assertEquals(listOf(3), fixture.requestedDays)
    }

    @Test fun unavailable_long_range_model_does_not_repeat_the_download_on_navigation() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler),
            model = WeatherModel.AROME_FRANCE_HD, unavailableModel = WeatherModel.GFS)
        val forecast = fixture.loadDefault()
        assertTrue(WeatherModel.GFS in forecast.errors)
        assertEquals(setOf(WeatherModel.AROME_FRANCE_HD), forecast.seriesByModel.keys)
        fixture.load(ForecastDisplayHorizon.DETAIL_REQUEST_DAYS)
        fixture.load(ForecastDisplayHorizon.GRAPHIC_REQUEST_DAYS)
        assertEquals(listOf(11), fixture.requestedDays)
    }

    @Test fun expired_unavailability_marker_allows_a_new_download() = runTest {
        val fixture = Fixture(UnconfinedTestDispatcher(testScheduler),
            model = WeatherModel.AROME_FRANCE_HD, unavailableModel = WeatherModel.GFS)
        fixture.loadDefault()
        fixture.expireModel(WeatherModel.GFS)
        fixture.loadDefault()
        assertEquals(listOf(11, 11), fixture.requestedDays)
    }

    private class Fixture(
        dispatcher: CoroutineDispatcher,
        cachedDays: Int = 0,
        model: WeatherModel = WeatherModel.GFS,
        unavailableModel: WeatherModel? = null
    ) {
        val city = City("paris", "Paris", country = "France", latitude = 48.85,
            longitude = 2.35, timezone = "UTC", countryCode = "FR")
        val models = listOfNotNull(model, unavailableModel)
        val requestedDays = mutableListOf<Int>()
        var gate: CompletableDeferred<Unit>? = null
        private val clock = Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
        private val json = Json { ignoreUnknownKeys = true }
        private var rows = if (cachedDays == 0) emptyList() else listOf(ForecastCacheEntity(
            city.id, model.apiKey, clock.millis(),
            json.encodeToString(ForecastResponseDto.serializer(), forecast(cachedDays))
        ))
        private val cache = mockk<ForecastCacheDao>(relaxed = true).also { dao ->
            coEvery { dao.getForCity(city.id) } coAnswers { rows }
            coEvery { dao.replaceRequestedModels(any(), any(), any(), any()) } coAnswers {
                rows = thirdArg<List<ForecastCacheEntity>>()
            }
        }
        private val api = mockk<OpenMeteoApi>().also { api ->
            coEvery { api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } coAnswers {
                val days = arg<Int>(6)
                requestedDays += days
                gate?.await()
                // Honour both the requested horizon and the model's availability.
                // Suffix values so the response cannot fabricate data for a missing model.
                val dto = forecast(minOf(days, model.maxForecastDays))
                val fields = json.encodeToJsonElement(ForecastResponseDto.serializer(), dto)
                    .jsonObject.mapValues { (key, value) ->
                        if ((key == "hourly" || key == "daily") && value is JsonObject) {
                            JsonObject(value.mapKeys { (variable, _) ->
                                if (variable == "time") variable else "${variable}_${model.apiKey}"
                            })
                        } else value
                    }
                json.decodeFromString(BatchedForecastResponseDto.serializer(), JsonObject(fields).toString())
            }
        }
        val repository: ForecastRepository = ForecastRepositoryImpl(
            api, ForecastMapper(), cache, json,
            mockk<NetworkMonitor> { every { isOnline() } returns true }, clock,
            mockk(relaxed = true), mockk<Context>(relaxed = true), dispatcher, dispatcher
        )

        fun expireModel(model: WeatherModel) {
            rows = rows.map { row ->
                if (row.modelKey == model.apiKey) row.copy(fetchedAtEpochMs = clock.millis() - 3_600_001L)
                else row
            }
        }

        suspend fun loadDefault(): CityForecast = success(repository.getCityForecastStream(
            city, models, maxCacheAgeMs = 3_600_000L
        ).toList())

        suspend fun load(days: Int): CityForecast = success(repository.getCityForecastStream(
            city, models, forecastDays = days, maxCacheAgeMs = 3_600_000L
        ).toList())

        private fun success(results: List<ApiResult<CityForecast>>): CityForecast {
            assertTrue(results.last() is ApiResult.Success)
            return (results.last() as ApiResult.Success).data
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
    }
}
