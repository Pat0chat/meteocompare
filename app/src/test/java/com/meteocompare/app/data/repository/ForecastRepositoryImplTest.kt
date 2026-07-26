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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock

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
            // Le repo appelle snapshotForecast(fresh) via runSuspendCatching à chaque
            // fetch réussi. Un relaxed mock renvoie Unit pour toute méthode
            // suspendue sans stub explicite — l'aspect "biais tracking" n'est
            // pas testé ici, on veut juste que le constructeur soit satisfait
            // et que le fetch principal ne soit pas perturbé.
            snapshotForecast = mockk(relaxed = true),
            clock = Clock.systemUTC(),
            context = context,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            computationDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    // ─────────────────────── Tests refresh de base ───────────────────────

    @Test
    fun `refresh réussi - publie la prévision fraîche pour les autres écrans`() = runTest {
        coEvery {
            api.getForecastBatched(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.AROME_FRANCE_HD))

        val update = async { repository.observeForecastUpdates().first() }
        yield() // abonne le collecteur avant le refresh

        val result = repository.refreshCityForecast(
            city = paris,
            models = listOf(WeatherModel.AROME_FRANCE_HD)
        )

        assertTrue(result is ApiResult.Success)
        assertEquals(paris.id, update.await().city.id)
    }

    @Test
    fun `fetch automatique de stream ne publie pas de signal UI`() = runTest {
        coEvery {
            api.getForecastBatched(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.AROME_FRANCE_HD))

        val unexpectedUpdate = async {
            withTimeoutOrNull(1L) { repository.observeForecastUpdates().first() }
        }
        yield()

        val results = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.AROME_FRANCE_HD)
        ).toList()

        assertTrue(results.last() is ApiResult.Success)
        assertEquals(null, unexpectedUpdate.await())
    }

    @Test
    fun `publication des refreshes ne bloque pas et conserve la mise a jour la plus recente`() = runTest {
        coEvery {
            api.getForecastBatched(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.AROME_FRANCE_HD))

        // Le collecteur bloque après la première émission. Les suivantes
        // saturent le buffer : DROP_OLDEST doit préserver la prévision la plus
        // récente, tout en laissant chaque refresh terminer sans suspension.
        val releaseCollector = CompletableDeferred<Unit>()
        val receivedCityIds = mutableListOf<String>()
        val slowCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeForecastUpdates().collect { forecast ->
                receivedCityIds += forecast.city.id
                if (receivedCityIds.size == 1) releaseCollector.await()
            }
        }

        repeat(12) { index ->
            val result = withTimeout(1_000L) {
                repository.refreshCityForecast(
                    city = paris.copy(id = index.toString()),
                    models = listOf(WeatherModel.AROME_FRANCE_HD)
                )
            }
            assertTrue(result is ApiResult.Success)
        }

        releaseCollector.complete(Unit)
        repeat(20) { yield() }
        assertEquals("11", receivedCityIds.last())
        slowCollector.cancel()
    }

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
    fun `refresh - ecrit les modeles reussis dans le cache en un lot`() = runTest {
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))
        coEvery { cacheDao.getForCity(any()) } returns emptyList()

        val requestedKeys = slot<List<String>>()
        val entries = slot<List<ForecastCacheEntity>>()
        val fetchedAt = slot<Long>()
        coEvery {
            cacheDao.replaceRequestedModels(
                eq(paris.id),
                capture(requestedKeys),
                capture(entries),
                capture(fetchedAt)
            )
        } returns Unit

        repository.refreshCityForecast(
            city = paris,
            models = listOf(WeatherModel.GFS)
        )

        coVerify(exactly = 1) {
            cacheDao.replaceRequestedModels(eq(paris.id), any(), any(), any())
        }
        assertEquals(listOf(WeatherModel.GFS.apiKey), requestedKeys.captured)
        assertEquals(1, entries.captured.size)
        assertEquals("1", entries.captured.single().cityId)
        assertEquals(WeatherModel.GFS.apiKey, entries.captured.single().modelKey)
        assertEquals(fetchedAt.captured, entries.captured.single().fetchedAtEpochMs)
    }

    @Test
    fun `refresh partiel - remplace tous les modeles demandes pour supprimer les anciennes lignes`() =
        runTest {
            coEvery {
                api.getForecastBatched(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            } returns batchedResponseWith(modelsWithData = listOf(WeatherModel.ICON_EU))

            val requestedKeys = slot<List<String>>()
            val entries = slot<List<ForecastCacheEntity>>()
            coEvery {
                cacheDao.replaceRequestedModels(
                    eq(paris.id),
                    capture(requestedKeys),
                    capture(entries),
                    any()
                )
            } returns Unit

            val result = repository.refreshCityForecast(
                city = paris,
                models = listOf(WeatherModel.ICON_EU, WeatherModel.GFS)
            )

            assertTrue(result is ApiResult.Success)
            assertEquals(
                listOf(WeatherModel.ICON_EU.apiKey, WeatherModel.GFS.apiKey),
                requestedKeys.captured
            )
            assertEquals(
                setOf(WeatherModel.ICON_EU.apiKey, WeatherModel.GFS.apiKey),
                entries.captured.map(ForecastCacheEntity::modelKey).toSet()
            )
            val missingMarker = entries.captured.single {
                it.modelKey == WeatherModel.GFS.apiKey
            }
            assertTrue(missingMarker.responseJson.contains("MODEL_UNAVAILABLE"))

            // Le marqueur négatif rend le cache complet pendant l'intervalle :
            // rouvrir l'écran ou exécuter un tick widget ne doit pas rappeler
            // l'API immédiatement pour le même modèle hors zone.
            coEvery { cacheDao.getForCity(paris.id) } returns entries.captured
            val cached = repository.getCityForecastStream(
                city = paris,
                models = listOf(WeatherModel.ICON_EU, WeatherModel.GFS),
                maxCacheAgeMs = 60 * 60 * 1000L
            ).toList()

            assertEquals(1, cached.size)
            val cachedForecast = (cached.single() as ApiResult.Success).data
            assertTrue(WeatherModel.ICON_EU in cachedForecast.seriesByModel)
            assertTrue(WeatherModel.GFS in cachedForecast.errors)
            coVerify(exactly = 1) {
                api.getForecastBatched(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
                )
            }
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
    fun `stream avec cache recent mais incomplet - refetch les modeles manquants`() = runTest {
        val recent = System.currentTimeMillis() - 5_000L
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(
            ForecastCacheEntity(
                cityId = paris.id,
                modelKey = WeatherModel.GFS.apiKey,
                fetchedAtEpochMs = recent,
                responseJson = json.encodeToString(
                    ForecastResponseDto.serializer(), sampleDto
                )
            )
        )
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(
            modelsWithData = listOf(WeatherModel.GFS, WeatherModel.ICON_EU)
        )

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS, WeatherModel.ICON_EU),
            maxCacheAgeMs = 60 * 60 * 1000L
        ).toList()

        assertEquals("Cache partiel puis données complètes", 2, emissions.size)
        coVerify(exactly = 1) {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `stream utilise la plus ancienne entree pour juger la fraicheur du lot`() = runTest {
        val now = System.currentTimeMillis()
        val encoded = json.encodeToString(ForecastResponseDto.serializer(), sampleDto)
        coEvery { cacheDao.getForCity(paris.id) } returns listOf(
            ForecastCacheEntity(
                cityId = paris.id,
                modelKey = WeatherModel.GFS.apiKey,
                fetchedAtEpochMs = now - 5_000L,
                responseJson = encoded
            ),
            ForecastCacheEntity(
                cityId = paris.id,
                modelKey = WeatherModel.ICON_EU.apiKey,
                fetchedAtEpochMs = now - 2 * 60 * 60 * 1000L,
                responseJson = encoded
            )
        )
        coEvery {
            api.getForecastBatched(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns batchedResponseWith(
            modelsWithData = listOf(WeatherModel.GFS, WeatherModel.ICON_EU)
        )

        val emissions = repository.getCityForecastStream(
            city = paris,
            models = listOf(WeatherModel.GFS, WeatherModel.ICON_EU),
            maxCacheAgeMs = 60 * 60 * 1000L
        ).toList()

        assertEquals("Le modèle ancien force un refresh du lot", 2, emissions.size)
        coVerify(exactly = 1) {
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

    @Test
    fun `refresh - URL batched contient tous les modèles séparés par virgule`() = runTest {
        // Verrouille le format exact du query param `models=`. Si un jour la
        // construction devient ` models.map{it.apiKey}.joinToString(";")` ou
        // qu'un espace se glisse, Open-Meteo répond en erreur — le test
        // remonterait la régression avant la mise en prod.
        val modelsParam = slot<String>()
        coEvery {
            api.getForecastBatched(
                any(), any(), capture(modelsParam),
                any(), any(), any(), any(), any(), any(), any()
            )
        } returns batchedResponseWith(
            modelsWithData = listOf(
                WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_EU
            )
        )
        coEvery { cacheDao.getForCity(any()) } returns emptyList()

        repository.refreshCityForecast(
            city = paris,
            // Ordre spécifique — on vérifie que l'ordre est préservé et que
            // le format joinToString(",") est respecté à la virgule près.
            models = listOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_EU)
        )

        assertEquals(
            "gfs_seamless,ecmwf_ifs025,icon_eu",
            modelsParam.captured
        )
    }

    @Test
    fun `refresh - forecast_days pris au max des modèles bornée par forecastDays voulu`() =
        runTest {
            // AROME_FRANCE_HD.maxForecastDays = 2 (limite), GFS = 16, forecastDays voulu = 5.
            // Attendu : effectiveForecastDays = min(max(2, 16), 5) = 5.
            // Ce test verrouille l'algo : envoyer forecast_days=16 gaspillerait
            // du réseau (GFS a 16 j de data mais UI n'affiche que 5), et
            // envoyer forecast_days=2 tronquerait GFS et ECMWF prématurément.
            val forecastDaysSlot = slot<Int>()
            coEvery {
                api.getForecastBatched(
                    any(), any(), any(), any(), any(), any(),
                    capture(forecastDaysSlot),
                    any(), any(), any()
                )
            } returns batchedResponseWith(
                modelsWithData = listOf(WeatherModel.AROME_FRANCE_HD, WeatherModel.GFS)
            )
            coEvery { cacheDao.getForCity(any()) } returns emptyList()

            repository.refreshCityForecast(
                city = paris,
                models = listOf(WeatherModel.AROME_FRANCE_HD, WeatherModel.GFS),
                forecastDays = 5
            )

            assertEquals(5, forecastDaysSlot.captured)
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

    // ────────────────────────── Coalescing tests ──────────────────────────
    //
    // Le repository dédoublonne les fetches concurrents pour la même clé
    // (city, models, forecastDays) via [inflightFetches]. Ces tests vérifient :
    //   1. N appels concurrents pour la même clé → 1 seul HTTPS
    //   2. Appels concurrents pour des clés distinctes → N HTTPS
    //   3. Le registre se nettoie après complétion → un appel séquentiel
    //      APRÈS le premier déclenche bien un nouveau HTTPS
    //   4. Un ordre différent des modèles pour la même liste effective
    //      coalesce quand-même (clé triée)
    //
    // ─── Pourquoi un fake API manuel plutôt que mockk ? ──────────────────
    // Pour observer le coalescing, il faut que la PREMIÈRE call soit encore
    // en vol quand la SECONDE arrive. On utilise donc un fake gate-based
    // ([GatedForecastApi]) qui suspend chaque appel jusqu'à `gate.complete(Unit)`.
    // Cette suspension déterministe est plus fiable que jouer avec `delay` +
    // TestDispatcher (interactions Unconfined/StandardTestDispatcher
    // subtiles) et évite la dépendance à `coAnswers` (pas dans toutes les
    // versions de mockk).

    /** Fake OpenMeteoApi qui bloque sur un gate pour reproduire du concurrent. */
    private class GatedForecastApi(
        private val response: BatchedForecastResponseDto
    ) : OpenMeteoApi {
        val gate = CompletableDeferred<Unit>()
        val callCount = java.util.concurrent.atomic.AtomicInteger(0)
        val perModelCallCount = java.util.concurrent.ConcurrentHashMap<String, Int>()

        override suspend fun getForecastBatched(
            latitude: Double, longitude: Double, models: String,
            hourly: String, daily: String, timezone: String,
            forecastDays: Int, windSpeedUnit: String,
            temperatureUnit: String, precipitationUnit: String
        ): BatchedForecastResponseDto {
            callCount.incrementAndGet()
            perModelCallCount.merge(models, 1) { a, b -> a + b }
            gate.await()
            return response
        }

        fun release() { gate.complete(Unit) }
    }

    /** Reconstruit un repository sur un fake API, pour ces tests uniquement. */
    private fun repositoryWith(fakeApi: OpenMeteoApi): ForecastRepositoryImpl {
        val networkMonitor: NetworkMonitor = mockk { every { isOnline() } returns true }
        val context: android.content.Context = mockk(relaxed = true) {
            every { getString(any<Int>()) } returns "stubbed-error"
            every { getString(any<Int>(), *anyVararg()) } returns "stubbed-error"
        }
        return ForecastRepositoryImpl(
            api = fakeApi,
            mapper = ForecastMapper(),
            cacheDao = mockk(relaxed = true),
            json = json,
            networkMonitor = networkMonitor,
            clock = Clock.systemUTC(),
            context = context,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            computationDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            snapshotForecast = mockk(relaxed = true)
        )
    }

    @Test
    fun `coalescing - deux appels concurrents pour même clé = un seul HTTPS`() = runTest {
        val fakeApi = GatedForecastApi(
            batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))
        )
        val repo = repositoryWith(fakeApi)
        val models = listOf(WeatherModel.GFS)

        coroutineScope {
            val a = async { repo.refreshCityForecast(paris, models) }
            val b = async { repo.refreshCityForecast(paris, models) }

            // Yields laissent les deux async progresser jusqu'au gate.
            // La première atteint la mock, incrémente callCount, suspend.
            // La seconde trouve le Deferred inflight, attend le même gate.
            yield(); yield()

            fakeApi.release()
            a.await()
            b.await()
        }

        // Une seule requête HTTPS a dû partir.
        assertEquals(1, fakeApi.callCount.get())
    }

    @Test
    fun `coalescing - villes différentes = HTTPS distincts`() = runTest {
        val fakeApi = GatedForecastApi(
            batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))
        )
        val repo = repositoryWith(fakeApi)
        val edinburgh = City(id = "2", name = "Edinburgh", country = "UK",
            latitude = 55.95, longitude = -3.19)
        val models = listOf(WeatherModel.GFS)

        coroutineScope {
            val a = async { repo.refreshCityForecast(paris, models) }
            val b = async { repo.refreshCityForecast(edinburgh, models) }

            yield(); yield()

            fakeApi.release()
            a.await()
            b.await()
        }

        // Deux clés distinctes → deux fetches
        assertEquals(2, fakeApi.callCount.get())
    }

    @Test
    fun `coalescing - appel séquentiel après complétion redéclenche HTTPS`() = runTest {
        // Cette fois on pré-release le gate pour que chaque call complète
        // immédiatement — on veut mesurer la ré-entrée séquentielle, pas la
        // concurrence.
        val fakeApi = GatedForecastApi(
            batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))
        )
        fakeApi.release()
        val repo = repositoryWith(fakeApi)
        val models = listOf(WeatherModel.GFS)

        // Premier appel, attend sa complétion.
        repo.refreshCityForecast(paris, models)
        // Deuxième appel séquentiel — l'inflight du premier a été retiré
        // du registre par le finally, celui-ci doit donc redéclencher un HTTPS.
        repo.refreshCityForecast(paris, models)

        assertEquals(2, fakeApi.callCount.get())
    }


    @Test
    fun `coalescing - horizons demandes equivalents partagent le meme fetch`() = runTest {
        val fakeApi = GatedForecastApi(
            batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS))
        )
        val repo = repositoryWith(fakeApi)
        val models = listOf(WeatherModel.GFS)

        coroutineScope {
            val a = async { repo.refreshCityForecast(paris, models, forecastDays = 16) }
            val b = async { repo.refreshCityForecast(paris, models, forecastDays = 30) }

            yield(); yield()

            fakeApi.release()
            a.await()
            b.await()
        }

        assertEquals(1, fakeApi.callCount.get())
    }

    @Test
    fun `coalescing - ordres de modèles différents pour même ensemble = coalescent`() = runTest {
        val fakeApi = GatedForecastApi(
            batchedResponseWith(modelsWithData = listOf(WeatherModel.GFS, WeatherModel.ICON_EU))
        )
        val repo = repositoryWith(fakeApi)

        coroutineScope {
            // Deux appels concurrents avec l'ordre inversé — la clé de coalescing
            // triant sur `model.name`, ces deux appels doivent partager le même
            // Deferred inflight.
            val a = async {
                repo.refreshCityForecast(paris, listOf(WeatherModel.GFS, WeatherModel.ICON_EU))
            }
            val b = async {
                repo.refreshCityForecast(paris, listOf(WeatherModel.ICON_EU, WeatherModel.GFS))
            }

            yield(); yield()

            fakeApi.release()
            a.await()
            b.await()
        }

        assertEquals(1, fakeApi.callCount.get())
    }
}