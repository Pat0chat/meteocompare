package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
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
import io.mockk.every
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
        // NetworkMonitor : par défaut on simule "en ligne" pour que les tests
        // existants (qui ne testent PAS l'offline) continuent à passer.
        val networkMonitor: NetworkMonitor = mockk {
            every { isOnline() } returns true
        }
        // Context : on stub getString(any<Int>()) pour que les messages d'erreur
        // localisés ne dépendent pas de la génération de R en test JVM pur.
        val context: android.content.Context = mockk(relaxed = true) {
            every { getString(any<Int>()) } returns "stubbed-error"
            every { getString(any<Int>(), *anyVararg()) } returns "stubbed-error"
        }
        repository = ForecastRepositoryImpl(
            api = api,
            mapper = ForecastMapper(),
            cacheDao = cacheDao,
            json = json,
            networkMonitor = networkMonitor,
            context = context,
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
    fun `refresh returns Error when network fails (no cache fallback)`() = runTest {
        // Cette nouvelle sémantique fixe le faux positif "Prévisions mises à
        // jour" qui apparaissait en mode avion : avant, le repo retombait
        // sur le cache et l'UI affichait un succès trompeur. Maintenant on
        // remonte l'erreur honnêtement et l'UI affiche "Pas de connexion".
        // Le contenu déjà affiché côté UI n'est pas effacé (philosophie
        // tolerant côté CityDetailViewModel).
        coEvery {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IOException("offline")

        // Même avec un cache présent, refresh doit retourner Error
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

        assertTrue("Should NOT fallback to cache, error must surface to UI",
            result is ApiResult.Error)
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

    // ─── Court-circuit maxCacheAgeMs ─────────────────────────────────────
    //
    // Cette suite de tests vérifie la logique cache-frais du repository,
    // qui est LA nouvelle logique d'économie batterie/data. Un bug ici
    // (mauvais comparateur, mauvaise unité, off-by-one) ferait soit fetcher
    // trop peu (données périmées à l'écran) soit trop souvent (batterie).

    @Test
    fun `stream avec maxCacheAgeMs null - refetch même si cache récent (comportement historique)`() =
        runTest {
            // Cache écrit il y a 100 ms — beaucoup plus récent que n'importe
            // quel intervalle raisonnable, mais on ne passe PAS de seuil →
            // le repo doit quand même fetch le réseau (backward-compat).
            val cachedEntity = ForecastCacheEntity(
                cityId = paris.id,
                modelKey = WeatherModel.GFS.apiKey,
                fetchedAtEpochMs = System.currentTimeMillis() - 100L,
                responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
            )
            coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
            coEvery {
                api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns sampleDto

            val emissions = repository.getCityForecastStream(
                city = paris,
                models = listOf(WeatherModel.GFS),
                maxCacheAgeMs = null // ⚠ explicitement null
            ).toList()

            // Cache + fresh, comme historiquement
            assertEquals(2, emissions.size)
            coVerify(exactly = 1) {
                api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `stream avec maxCacheAgeMs court-circuit - émet cache seul quand assez frais`() = runTest {
        // Cache écrit il y a 5 s, seuil = 1 h → cache est frais, PAS de fetch
        // réseau. C'est l'économie batterie/data principale de la feature.
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.GFS.apiKey,
            fetchedAtEpochMs = System.currentTimeMillis() - 5_000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
        coEvery {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns sampleDto

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS),
            maxCacheAgeMs = 60 * 60 * 1000L // 1 heure
        ).toList()

        assertEquals("Seul le cache doit être émis, pas de fresh", 1, emissions.size)
        assertTrue(emissions[0] is ApiResult.Success)
        coVerify(exactly = 0) {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `stream avec maxCacheAgeMs - refetch quand cache trop vieux`() = runTest {
        // Cache d'il y a 2 h, seuil = 1 h → cache trop vieux, on DOIT fetch.
        // Comportement classique cache + fresh.
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.GFS.apiKey,
            fetchedAtEpochMs = System.currentTimeMillis() - 2 * 60 * 60 * 1000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
        coEvery {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns sampleDto

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS),
            maxCacheAgeMs = 60 * 60 * 1000L
        ).toList()

        assertEquals("Cache + fresh doivent être émis", 2, emissions.size)
        coVerify(exactly = 1) {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `stream avec forceRefresh true - ignore maxCacheAgeMs (pull-to-refresh)`() = runTest {
        // Pull-to-refresh doit TOUJOURS fetch, même si cache est fait il y a
        // 1 seconde. Le seuil ne s'applique qu'au chargement automatique.
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.GFS.apiKey,
            fetchedAtEpochMs = System.currentTimeMillis() - 1_000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
        coEvery {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns sampleDto

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS),
            forceRefresh = true,
            maxCacheAgeMs = 60 * 60 * 1000L // seuil confortable, ignoré grâce à forceRefresh
        ).toList()

        // forceRefresh=true saute l'émission cache initiale → seule fresh est émise
        assertEquals(1, emissions.size)
        coVerify(exactly = 1) {
            api.getForecast(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }
}
