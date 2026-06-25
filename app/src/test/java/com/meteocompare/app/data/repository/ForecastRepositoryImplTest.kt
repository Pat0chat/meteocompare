package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastCacheEntity
import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import com.meteocompare.app.data.remote.dto.HourlyDto
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.WeatherModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ForecastRepositoryImplTest {

    private lateinit var api: OpenMeteoApi
    private lateinit var cacheDao: ForecastCacheDao
    private lateinit var repository: ForecastRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true }

    private val paris = City(
        id = "1", name = "Paris", country = "France",
        latitude = 48.85, longitude = 2.35
    )

    private val sampleDto = ForecastResponseDto(
        latitude = 48.85,
        longitude = 2.35,
        timezone = "Europe/Paris",
        hourly = HourlyDto(
            time = listOf("2026-06-23T00:00"),
            temperature2m = listOf(20.0)
        )
    )

    @Before
    fun setUp() {
        api = mockk()
        cacheDao = mockk(relaxed = true) // relaxed → no-op pour les méthodes void/suspend
        repository = ForecastRepositoryImpl(
            api = api,
            mapper = ForecastMapper(),
            cacheDao = cacheDao,
            json = json,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    @Test
    fun `refresh aggregates successful models and reports failures`() = runTest {
        coEvery {
            api.getForecast(any(), any(), WeatherModel.ICON_EU.apiKey, any(), any(), any(), any(), any(), any(), any())
        } returns sampleDto
        coEvery {
            api.getForecast(any(), any(), WeatherModel.GFS.apiKey, any(), any(), any(), any(), any(), any(), any())
        } throws IOException("network down")
        coEvery { cacheDao.getForCity(any()) } returns emptyList()

        val result = repository.refreshCityForecast(
            city = paris,
            models = listOf(WeatherModel.ICON_EU, WeatherModel.GFS)
        )

        assertTrue("Should succeed when at least one model responds", result is ApiResult.Success)
        result as ApiResult.Success
        assertEquals(1, result.data.seriesByModel.size)
        assertTrue(WeatherModel.ICON_EU in result.data.seriesByModel)
        assertEquals(1, result.data.errors.size)
    }

    @Test
    fun `refresh writes successful results to cache`() = runTest {
        coEvery { api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns sampleDto
        coEvery { cacheDao.getForCity(any()) } returns emptyList()

        val slot = slot<ForecastCacheEntity>()
        coEvery { cacheDao.upsert(capture(slot)) } returns Unit

        repository.refreshCityForecast(
            city = paris,
            models = listOf(WeatherModel.GFS)
        )

        coVerify { cacheDao.upsert(any()) }
        assertEquals("1", slot.captured.cityId)
        assertEquals(WeatherModel.GFS.apiKey, slot.captured.modelKey)
    }

    @Test
    fun `refresh falls back to cache when network completely fails`() = runTest {
        coEvery {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IOException("offline")

        // Le cache contient un résultat pour ICON_EU
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.ICON_EU.apiKey,
            fetchedAtEpochMs = 1_000_000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)

        val result = repository.refreshCityForecast(
            city = paris,
            models = listOf(WeatherModel.ICON_EU)
        )

        assertTrue("Should fallback to cache", result is ApiResult.Success)
        result as ApiResult.Success
        assertTrue(WeatherModel.ICON_EU in result.data.seriesByModel)
    }

    @Test
    fun `stream emits cached value first then fresh value`() = runTest {
        // Cache présent
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.GFS.apiKey,
            fetchedAtEpochMs = 1_000_000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)

        // Réseau OK aussi
        coEvery {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns sampleDto

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS)
        ).toList()

        assertEquals("Should emit cache + fresh", 2, emissions.size)
        assertTrue(emissions[0] is ApiResult.Success)
        assertTrue(emissions[1] is ApiResult.Success)
    }

    @Test
    fun `stream emits error when no cache and network fails`() = runTest {
        coEvery { cacheDao.getForCity(any()) } returns emptyList()
        coEvery {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IOException("no network")

        val emission = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS)
        ).first()

        assertTrue(emission is ApiResult.Error)
    }
}
