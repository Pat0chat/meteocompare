package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.ForecastCacheEntity
import com.meteocompare.app.data.mapper.ForecastMapper
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
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

/**
 * Tests du [ForecastRepositoryImpl] en mode BATCHED (post-optimisation
 * multi-modèles en un seul appel HTTPS).
 *
 * ─── Différence avec la version pré-batching ─────────────────────────────
 * Avant : le repo appelait `api.getForecast(model = X)` N fois en parallèle.
 * Les tests mockaient chaque appel individuellement.
 *
 * Après : un seul appel `api.getForecastBatched(models = "X,Y,Z")` retourne
 * une réponse suffixée que le [BatchedForecastSplitter] décompose en un
 * DTO par modèle. Les tests mockent maintenant le batched call et
 * construisent des réponses JSON qui produisent les mêmes comportements.
 *
 * Helpers factorisés : [batchedResponseWith] construit une réponse batched
 * suffixée par les modèles fournis. C'est le pattern principal utilisé
 * dans tous les tests qui doivent contrôler quels modèles "répondent" ou
 * "échouent".
 */
class ForecastRepositoryImplTest {

    private lateinit var api: OpenMeteoApi
    private lateinit var cacheDao: ForecastCacheDao
    private lateinit var repository: ForecastRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val paris = City(
        id = "1", name = "Paris", country = "France",
        latitude = 48.85, longitude = 2.35
    )

    /**
     * DTO minimal utilisé pour peupler le cache Room dans les tests qui
     * simulent un cache présent. Il n'a pas besoin d'être suffixé — le
     * cache stocke des DTOs unitaires (un par modèle), le splitter
     * n'intervient qu'en amont sur la réponse réseau.
     */
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
        cacheDao = mockk(relaxed = true)
        val networkMonitor: NetworkMonitor = mockk {
            every { isOnline() } returns true
        }
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

    // ─────────────────────── Tests refresh de base ───────────────────────

    @Test
    fun `refresh - modeles ayant répondu sont mappés, modèles vides deviennent des erreurs`() =
        runTest {
            // ICON_EU répond (données suffixées présentes), GFS n'a rien —
            // simulateur d'une réponse batched où Open-Meteo n'a pas de
            // données pour un modèle (hors zone ou downtime individuel).
            coEvery {
                api.getForecastBatched(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            } returns batchedResponseWith(
                modelsWithData = listOf(WeatherModel.ICON_EU)
            )
            coEvery { cacheDao.getForCity(any()) } returns emptyList()

            val result = repository.refreshCityForecast(
                city = paris,
                models = listOf(WeatherModel.ICON_EU, WeatherModel.GFS)
            )

            assertTrue("Should succeed when at least one model responds",
                result is ApiResult.Success)
            result as ApiResult.Success
            assertEquals(1, result.data.seriesByModel.size)
            assertTrue(WeatherModel.ICON_EU in result.data.seriesByModel)
            assertEquals(1, result.data.errors.size)
            assertTrue(WeatherModel.GFS in result.data.errors)
        }

    @Test
    fun `refresh - ecrit chaque modele reussi dans le cache`() = runTest {
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))
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
    fun `refresh - reseau ko sans cache retourne Error`() = runTest {
        // Cette sémantique fixe le faux positif "Prévisions mises à jour"
        // en mode avion : avant, le repo retombait sur le cache et l'UI
        // affichait un succès trompeur. Maintenant on remonte l'erreur
        // honnêtement et l'UI affiche "Pas de connexion". Contenu déjà
        // affiché côté UI n'est pas effacé (VM tolerant).
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IOException("offline")

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

    // ─────────────────────── Stream : cache + fresh ───────────────────────

    @Test
    fun `stream emet cache puis fresh`() = runTest {
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.GFS.apiKey,
            fetchedAtEpochMs = 1_000_000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)

        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS)
        ).toList()

        assertEquals("Should emit cache + fresh", 2, emissions.size)
        assertTrue(emissions[0] is ApiResult.Success)
        assertTrue(emissions[1] is ApiResult.Success)
    }

    @Test
    fun `stream emet Error quand pas de cache et reseau ko`() = runTest {
        coEvery { cacheDao.getForCity(any()) } returns emptyList()
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws IOException("no network")

        val emission = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS)
        ).first()

        assertTrue(emission is ApiResult.Error)
    }

    // ─────────────────────── Court-circuit maxCacheAgeMs ───────────────────
    //
    // Suite qui vérifie la logique cache-frais du repository. Un bug ici
    // ferait soit fetcher trop peu (données périmées à l'écran) soit trop
    // souvent (batterie).

    @Test
    fun `stream avec maxCacheAgeMs null - refetch meme si cache recent (compat historique)`() =
        runTest {
            val cachedEntity = ForecastCacheEntity(
                cityId = paris.id,
                modelKey = WeatherModel.GFS.apiKey,
                fetchedAtEpochMs = System.currentTimeMillis() - 100L,
                responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
            )
            coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
            coEvery {
                api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))

            val emissions = repository.getCityForecastStream(
                city = paris,
                models = listOf(WeatherModel.GFS),
                maxCacheAgeMs = null
            ).toList()

            assertEquals(2, emissions.size)
            coVerify(exactly = 1) {
                api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `stream avec maxCacheAgeMs court-circuit - emet cache seul quand assez frais`() =
        runTest {
            val cachedEntity = ForecastCacheEntity(
                cityId = paris.id,
                modelKey = WeatherModel.GFS.apiKey,
                fetchedAtEpochMs = System.currentTimeMillis() - 5_000L,
                responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
            )
            coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
            coEvery {
                api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))

            val emissions = repository.getCityForecastStream(
                city = paris,
                models = listOf(WeatherModel.GFS),
                maxCacheAgeMs = 60 * 60 * 1000L
            ).toList()

            assertEquals("Seul le cache doit être émis, pas de fresh", 1, emissions.size)
            assertTrue(emissions[0] is ApiResult.Success)
            coVerify(exactly = 0) {
                api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `stream avec maxCacheAgeMs - refetch quand cache trop vieux`() = runTest {
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.GFS.apiKey,
            fetchedAtEpochMs = System.currentTimeMillis() - 2 * 60 * 60 * 1000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS),
            maxCacheAgeMs = 60 * 60 * 1000L
        ).toList()

        assertEquals("Cache + fresh doivent être émis", 2, emissions.size)
        coVerify(exactly = 1) {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `stream avec forceRefresh true - ignore maxCacheAgeMs (pull-to-refresh)`() = runTest {
        val cachedEntity = ForecastCacheEntity(
            cityId = paris.id,
            modelKey = WeatherModel.GFS.apiKey,
            fetchedAtEpochMs = System.currentTimeMillis() - 1_000L,
            responseJson = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        )
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(cachedEntity)
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS),
            forceRefresh = true,
            maxCacheAgeMs = 60 * 60 * 1000L
        ).toList()

        assertEquals(1, emissions.size)
        coVerify(exactly = 1) {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ─────────────────────── Nouveau : gain "un seul appel API" ───────────

    @Test
    fun `refresh - N modèles = 1 seul appel API (invariant batching)`() = runTest {
        // Cœur de l'optimisation : peu importe combien de modèles sont
        // demandés, on doit avoir un seul call getForecastBatched. Un
        // futur refactor qui casserait ça ferait exploser la latence et
        // la conso batterie.
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(
            modelsWithData = listOf(
                WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_EU
            )
        )
        coEvery { cacheDao.getForCity(any()) } returns emptyList()

        repository.refreshCityForecast(
            city = paris,
            models = listOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_EU)
        )

        coVerify(exactly = 1) {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    // ─────────────────────── Helpers ───────────────────────────────────────

    /**
     * Construit une [BatchedForecastResponseDto] où [modelsWithData] ont des
     * températures horaires valides (donc considérés "usables" par le
     * splitter). Les autres modèles éventuellement passés à la fonction ne
     * sont pas dans la réponse — le splitter les filtrera automatiquement,
     * ce qui produit des erreurs par-modèle côté repo.
     *
     * Réponse minimale : 1 pas de temps + temperature_2m. C'est le contrat
     * du splitter pour "usable data".
     */
    private fun batchedResponseWith(
        modelsWithData: List<WeatherModel>
    ): BatchedForecastResponseDto {
        val hourlyJson = buildString {
            append("""{"time":["2026-06-23T00:00"]""")
            for (model in modelsWithData) {
                append(""","temperature_2m_${model.apiKey}":[20.0]""")
            }
            append("}")
        }
        return json.decodeFromString(
            BatchedForecastResponseDto.serializer(),
            """{
              "latitude": 48.85,
              "longitude": 2.35,
              "timezone": "Europe/Paris",
              "hourly": $hourlyJson
            }"""
        )
    }
}
