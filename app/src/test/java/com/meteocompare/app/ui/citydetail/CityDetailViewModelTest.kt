package com.meteocompare.app.ui.citydetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionHistoryData
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.repository.VigilanceRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.ComputeForecastEvolutionUseCase
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Tests de [CityDetailViewModel].
 *
 * Couvre les 3 flows critiques :
 *   - Chargement initial (cache + stream réseau)
 *   - Refresh manuel (avec RefreshFeedback Success / Error)
 *   - City introuvable (Error state + no crash)
 *
 * Le Context est mocké pour retourner une chaîne stubbée — on n'utilise pas
 * R.string.X dans les assertions, on vérifie juste que le message contient ce
 * que `context.getString` a retourné. Ça évite la dépendance sur la génération
 * de R.kt en test JVM pur.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CityDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testNow = Instant.parse("2026-06-28T12:00:00Z")
    private val testClock = Clock.fixed(testNow, ZoneOffset.UTC)

    private val paris = City("1", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val favoritesFlow = MutableStateFlow(listOf(paris))
    private val modelsFlow = MutableStateFlow(WeatherModel.MVP_SELECTION)
    private val refreshIntervalFlow = MutableStateFlow(RefreshInterval.DEFAULT)
    private val forecastEngineFlow = MutableStateFlow(ForecastEngine.DEFAULT)
    private val collapsedSectionsFlow = MutableStateFlow<Set<CityDetailSection>>(emptySet())
    private val detailViewModeFlow = MutableStateFlow(CityDetailViewMode.DEFAULT)
    private val detailContentTabFlow = MutableStateFlow(CityDetailContentTab.DEFAULT)
    private val forecastUpdates = MutableSharedFlow<CityForecast>(extraBufferCapacity = 4)
    private val onlineFlow = MutableStateFlow(true)

    private val cityRepo: CityRepository = mockk(relaxed = true) {
        coEvery { observeFavorites() } returns favoritesFlow
    }
    private val forecastRepo: ForecastRepository = mockk(relaxed = true)
    private val marineRepo: MarineRepository = mockk(relaxed = true)
    private val vigilanceRepo: VigilanceRepository = mockk(relaxed = true) {
        coEvery { getVigilance(any(), any(), any()) } returns ApiResult.Success(null)
    }
    private val evolutionRepo: ForecastEvolutionRepository = mockk(relaxed = true) {
        coEvery { getPreviousForecasts(any(), any(), any(), any(), any()) } returns
            ApiResult.Success(ForecastEvolutionHistoryData(emptyList(), testNow))
    }
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true) {
        every { isOnline() } answers { onlineFlow.value }
        every { observeOnline() } returns onlineFlow
    }
    private val climateRepo: ClimateNormalsRepository = mockk(relaxed = true) {
        // Par défaut, normales en échec → loadedNormals reste null
        coEvery { getNormalsForCity(any()) } returns
                ApiResult.Error(RuntimeException(), "normals unavailable")
    }
    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { observeEnabledModels() } returns modelsFlow
        // observeRefreshInterval() est appelé par loadInitial() via .first().
        // Sans ce stub explicit, MockK relaxed retourne un flow VIDE et .first()
        // lève NoSuchElementException. Un flow avec DEFAULT permet aux tests
        // de passer sans changer leur comportement (DEFAULT = HOUR_1, non-MANUAL,
        // donc maxCacheAgeMs = 3600000 — les tests fonctionnent avec cette valeur).
        coEvery { observeRefreshInterval() } returns refreshIntervalFlow
        every { observeForecastEngine() } returns forecastEngineFlow
        every { observeCollapsedCityDetailSections(any()) } returns collapsedSectionsFlow
        every { observeCityDetailViewMode(any()) } returns detailViewModeFlow
        every { observeCityDetailContentTab(any()) } returns detailContentTabFlow
    }

    // Context : on stub getString pour retourner des strings prévisibles.
    // any<Int>() évite la dépendance sur la génération de R en test pur JVM.
    private val context: Context = mockk(relaxed = true) {
        every { getString(any<Int>()) } returns "stubbed-message"
    }

    private val calculator = ConfidenceCalculator(EqualWeighting())
    private val engineContextProvider = ForecastEngineContextProvider(mockk(relaxed = true))

    private fun buildViewModel(cityId: String = "1"): CityDetailViewModel {
        val saved = SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to cityId))
        return CityDetailViewModel(
            context = context,
            savedStateHandle = saved,
            cityRepository = cityRepo,
            forecastRepository = forecastRepo,
            marineRepository = marineRepo,
            vigilanceRepository = vigilanceRepo,
            networkMonitor = networkMonitor,
            climateNormalsRepository = climateRepo,
            confidenceCalculator = calculator,
            userPreferences = prefs,
            // Suivi de biais Phase 2d — tests existants agnostiques du feature :
            // relaxed mocks qui renvoient valeurs par défaut (Flow vide, null bias).
            // Le VM combinera un Flow amont qui n'émet jamais avec les prefs mockées ;
            // biasState restera à EMPTY, ce qui ne perturbe pas la logique existante
            // testée ici (loadInitial, refresh, applyResult).
            biasSampleRepository = mockk(relaxed = true),
            computeBias = mockk(relaxed = true),
            forecastEvolutionRepository = evolutionRepo,
            computeForecastEvolution = ComputeForecastEvolutionUseCase(),
            clock = testClock,
            computationDispatcher = dispatcher,
            engineContextProvider = engineContextProvider
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        favoritesFlow.value = listOf(paris)
        modelsFlow.value = WeatherModel.MVP_SELECTION
        refreshIntervalFlow.value = RefreshInterval.DEFAULT
        forecastEngineFlow.value = ForecastEngine.DEFAULT
        collapsedSectionsFlow.value = emptySet()
        detailViewModeFlow.value = CityDetailViewMode.DEFAULT
        detailContentTabFlow.value = CityDetailContentTab.DEFAULT
        onlineFlow.value = true
        // Par défaut, stream forecast ne fait rien (jamais terminé)
        coEvery {
            forecastRepo.getCityForecastStream(any(), any(), any(), any(), any())
        } returns flow { /* hang */ }
        every { forecastRepo.observeForecastUpdates() } returns forecastUpdates
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ville non francaise - aucune verification vigilance et etat idle`() = runTest(dispatcher) {
        val london = City(
            id = "2643743",
            name = "London",
            country = "United Kingdom",
            latitude = 51.5074,
            longitude = -0.1278,
            countryCode = "GB"
        )
        favoritesFlow.value = listOf(london)

        val vm = buildViewModel(cityId = london.id)
        runCurrent()

        assertEquals(VigilanceUiState.Idle, vm.vigilanceState.value)
        coVerify(exactly = 0) { vigilanceRepo.getVigilance(eq(london), any(), any()) }
    }

    @Test
    fun `connectivite - expose les changements reseau`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.isOnline.test {
            assertTrue(awaitItem())
            onlineFlow.value = false
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `sections repliees - expose les preferences persistantes de la ville`() =
        runTest(dispatcher) {
            val vm = buildViewModel()

            vm.collapsedSections.test {
                assertEquals(emptySet<CityDetailSection>(), awaitItem())

                collapsedSectionsFlow.value = setOf(
                    CityDetailSection.CONFIDENCE,
                    CityDetailSection.WIND
                )

                assertEquals(
                    setOf(CityDetailSection.CONFIDENCE, CityDetailSection.WIND),
                    awaitItem()
                )
            }
        }

    @Test
    fun `setSectionExpanded - persiste l'etat inverse sous forme collapsed`() =
        runTest(dispatcher) {
            val vm = buildViewModel()

            vm.setSectionExpanded(CityDetailSection.PRECIPITATION, expanded = false)
            coVerify {
                prefs.setCityDetailSectionCollapsed(
                    cityId = "1",
                    section = CityDetailSection.PRECIPITATION,
                    collapsed = true
                )
            }

            vm.setSectionExpanded(CityDetailSection.PRECIPITATION, expanded = true)
            coVerify {
                prefs.setCityDetailSectionCollapsed(
                    cityId = "1",
                    section = CityDetailSection.PRECIPITATION,
                    collapsed = false
                )
            }
        }


    @Test
    fun `detail preferences - expose et persiste le mode et l onglet`() =
        runTest(dispatcher) {
            val vm = buildViewModel()

            vm.detailViewMode.test {
                assertEquals(CityDetailViewMode.DAILY, awaitItem())
                detailViewModeFlow.value = CityDetailViewMode.HOURLY
                assertEquals(CityDetailViewMode.HOURLY, awaitItem())
            }

            vm.detailContentTab.test {
                assertEquals(CityDetailContentTab.CONDITIONS, awaitItem())
                detailContentTabFlow.value = CityDetailContentTab.WIND
                assertEquals(CityDetailContentTab.WIND, awaitItem())
            }

            vm.setDetailViewMode(CityDetailViewMode.HOURLY)
            coVerify { prefs.setCityDetailViewMode("1", CityDetailViewMode.HOURLY) }

            vm.setDetailContentTab(CityDetailContentTab.PRECIPITATION)
            coVerify {
                prefs.setCityDetailContentTab("1", CityDetailContentTab.PRECIPITATION)
            }
        }

    // ──────────────── Chargement initial ────────────────

    @Test
    fun `loadInitial - état initial est Loading`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.state.test {
            assertEquals(CityDetailUiState.Loading, awaitItem())
        }
    }

    @Test
    fun `loadInitial - normales ne demarrent pas avant un forecast exploitable`() =
        runTest(dispatcher) {
            buildViewModel()
            runCurrent()

            coVerify(exactly = 0) { climateRepo.getNormalsForCity(any()) }
        }

    @Test
    fun `loadInitial - le fuseau resolu par le forecast devient la source pour les donnees secondaires`() =
        runTest(dispatcher) {
            val resolvedCity = paris.copy(timezone = "Europe/Paris")
            val forecast = buildForecast(resolvedCity)
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(forecast))

            buildViewModel()
            runCurrent()

            coVerify(exactly = 1) { climateRepo.getNormalsForCity(eq(resolvedCity)) }
        }

    @Test
    fun `loadInitial - normales demarrent une seule fois apres le premier succes`() =
        runTest(dispatcher) {
            val forecast = buildForecast(paris)
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(
                ApiResult.Success(forecast),
                ApiResult.Success(forecast.copy(fetchedAt = forecast.fetchedAt?.plusSeconds(60)))
            )

            buildViewModel()
            runCurrent()

            coVerify(exactly = 1) { climateRepo.getNormalsForCity(eq(paris)) }
        }

    @Test
    fun `loadInitial - ville inconnue dans les favoris → Error`() = runTest(dispatcher) {
        // Favoris vides → cityId "1" introuvable
        favoritesFlow.value = emptyList()

        val vm = buildViewModel(cityId = "1")
        vm.state.test {
            // L'état peut commencer en Loading puis basculer en Error,
            // ou être directement en Error avec UnconfinedTestDispatcher.
            // On consomme jusqu'à atteindre Error.
            var state = awaitItem()
            while (state !is CityDetailUiState.Error) state = awaitItem()
            assertEquals("stubbed-message", state.message)
        }
    }

    @Test
    fun `loadInitial - forecast en succès → Loaded`() = runTest(dispatcher) {
        val forecast = buildForecast(paris)
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))

        val vm = buildViewModel()
        vm.state.test {
            var state = awaitItem()
            while (state !is CityDetailUiState.Loaded) state = awaitItem()
            assertEquals(paris, state.forecast.city)
            assertNotNull(state.currentTemp)
            assertEquals(testNow, state.calculatedAt)
            // Pas de normales (stub renvoie error)
            assertEquals(null, state.normals)
        }
    }


    @Test
    fun `evolution - first fresh forecast exposes building history without failing city detail`() =
        runTest(dispatcher) {
            val forecast = buildForecast(paris).copy(fetchedAt = testNow)
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(forecast))
            coEvery { evolutionRepo.getPreviousForecasts(any(), any(), any(), any(), any()) } returns
                ApiResult.Success(ForecastEvolutionHistoryData(emptyList(), testNow))

            val vm = buildViewModel()
            runCurrent()

            assertTrue(vm.state.value is CityDetailUiState.Loaded)
            assertTrue(vm.evolutionState.value is ForecastEvolutionState.BuildingHistory)
        }

    @Test
    fun `evolution - local historical samples are wired into a loaded report`() =
        runTest(dispatcher) {
            val gfs = buildForecast(paris, WeatherModel.GFS, 20.0)
            val ecmwf = buildForecast(paris, WeatherModel.ECMWF, 22.0)
            val forecast = gfs.copy(
                seriesByModel = gfs.seriesByModel + ecmwf.seriesByModel,
                fetchedAt = testNow
            )
            val targetDate = LocalDate.of(2026, 6, 28)
            val history = listOf(
                ForecastEvolutionSample(
                    WeatherModel.GFS, ForecastEvolutionVariable.PRECIPITATION, targetDate,
                    daysAgo = 1, value = 4.0, ageHours = 25, capturedAt = testNow.minusSeconds(25 * 3600)
                ),
                ForecastEvolutionSample(
                    WeatherModel.ECMWF, ForecastEvolutionVariable.PRECIPITATION, targetDate,
                    daysAgo = 1, value = 5.0, ageHours = 25, capturedAt = testNow.minusSeconds(25 * 3600)
                )
            )
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(forecast))
            coEvery { evolutionRepo.getPreviousForecasts(any(), any(), any(), any(), any()) } returns
                ApiResult.Success(ForecastEvolutionHistoryData(history, testNow.minusSeconds(25 * 3600)))

            val vm = buildViewModel()
            runCurrent()

            val evolution = vm.evolutionState.value
            assertTrue(evolution is ForecastEvolutionState.Loaded)
            evolution as ForecastEvolutionState.Loaded
            assertTrue(evolution.report.hasUsableData)
            assertEquals(25, evolution.report.days.first().variables
                .getValue(ForecastEvolutionVariable.PRECIPITATION).revision?.previousAgeHours)
        }

    @Test
    fun `evolution - history failure stays secondary and never replaces loaded city detail`() =
        runTest(dispatcher) {
            val forecast = buildForecast(paris).copy(fetchedAt = testNow)
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(forecast))
            coEvery { evolutionRepo.getPreviousForecasts(any(), any(), any(), any(), any()) } returns
                ApiResult.Error(IllegalStateException("history unavailable"), "history unavailable")

            val vm = buildViewModel()
            runCurrent()

            assertTrue(vm.state.value is CityDetailUiState.Loaded)
            assertTrue(vm.evolutionState.value is ForecastEvolutionState.Error)
        }

    @Test
    fun `changement de moteur recalcule Details sans nouvelle requete`() = runTest(dispatcher) {
        val forecast = buildScenarioForecast(paris)
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))

        val vm = buildViewModel()
        vm.state.test {
            var state = awaitItem()
            while (state !is CityDetailUiState.Loaded) state = awaitItem()
            val before = state.currentTemp

            coVerify(exactly = 1) {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            }
            forecastEngineFlow.value = ForecastEngine.SCENARIOS

            var updated = awaitItem()
            while (updated !is CityDetailUiState.Loaded || updated.currentTemp == before) updated = awaitItem()
            assertNotEquals(before, updated.currentTemp)
            assertEquals(ForecastEngine.SCENARIOS, updated.engineContext.engine)
            coVerify(exactly = 1) {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `loadInitial - forecast en erreur sans cache → Error`() = runTest(dispatcher) {
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Error(RuntimeException(), "Pas de connexion"))

        val vm = buildViewModel()
        vm.state.test {
            var state = awaitItem()
            while (state !is CityDetailUiState.Error) state = awaitItem()
            assertEquals("Pas de connexion", state.message)
        }
    }

    @Test
    fun `loadInitial - erreur après Loaded n'écrase pas le contenu (philosophie tolerant)`() =
        runTest(dispatcher) {
            // Stream qui émet d'abord Success(cache), puis Error(réseau)
            val cached = buildForecast(paris)
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(
                ApiResult.Success(cached),
                ApiResult.Error(RuntimeException(), "Pas de connexion")
            )

            val vm = buildViewModel()
            vm.state.test {
                // On atteint Loaded
                var state = awaitItem()
                while (state !is CityDetailUiState.Loaded) state = awaitItem()
                // Une fois en Loaded, l'erreur ne doit PAS le ré-écraser en Error.
                // C'est la philosophie "tolerant" : si on a des données, on les
                // garde même si la requête fraîche échoue.
                // Aucune nouvelle émission n'est attendue (ou alors elle reste Loaded).
                expectNoEvents()
            }
        }

    // ──────────────── Refresh ────────────────


    @Test
    fun `changement de modèles recharge la page et accepte un timestamp identique`() =
        runTest(dispatcher) {
            val fetchedAt = Instant.parse("2026-06-28T10:05:00Z")
            val initial = buildForecast(
                paris,
                model = WeatherModel.AROME_FRANCE_HD,
                temperature = 20.0
            ).copy(fetchedAt = fetchedAt)
            val changed = buildForecast(
                paris,
                model = WeatherModel.GFS,
                temperature = 31.0
            ).copy(fetchedAt = fetchedAt)

            coEvery {
                forecastRepo.getCityForecastStream(
                    eq(paris),
                    eq(WeatherModel.MVP_SELECTION),
                    any(),
                    any(),
                    any()
                )
            } returns flowOf(ApiResult.Success(initial))
            coEvery {
                forecastRepo.getCityForecastStream(
                    eq(paris),
                    eq(listOf(WeatherModel.GFS)),
                    any(),
                    any(),
                    any()
                )
            } returns flowOf(ApiResult.Success(changed))

            val vm = buildViewModel()
            vm.state.test {
                var state = awaitItem()
                while ((state as? CityDetailUiState.Loaded)?.forecast?.seriesByModel?.keys !=
                    setOf(WeatherModel.AROME_FRANCE_HD)
                ) {
                    state = awaitItem()
                }

                modelsFlow.value = listOf(WeatherModel.GFS)

                var updated = awaitItem()
                while ((updated as? CityDetailUiState.Loaded)?.forecast?.seriesByModel?.keys !=
                    setOf(WeatherModel.GFS)
                ) {
                    updated = awaitItem()
                }

                val loaded = updated
                assertEquals(fetchedAt, loaded.fetchedAt)
                assertEquals(31.0, loaded.currentTemp ?: Double.NaN, 0.001)
            }

            coVerify(exactly = 1) {
                forecastRepo.getCityForecastStream(
                    eq(paris),
                    eq(listOf(WeatherModel.GFS)),
                    any(),
                    any(),
                    any()
                )
            }
        }

    @Test
    fun `refresh depuis la Home - met à jour la page Détails sans second fetch`() =
        runTest(dispatcher) {
            val initialAt = Instant.parse("2026-06-28T10:00:00Z")
            val refreshedAt = Instant.parse("2026-06-28T10:05:00Z")
            val initial = buildForecast(paris).copy(fetchedAt = initialAt)
            val refreshed = buildForecast(paris).copy(fetchedAt = refreshedAt)

            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(initial))

            val vm = buildViewModel()
            vm.state.test {
                var state = awaitItem()
                while ((state as? CityDetailUiState.Loaded)?.fetchedAt != initialAt) {
                    state = awaitItem()
                }

                forecastUpdates.emit(refreshed)

                var updated = awaitItem()
                while ((updated as? CityDetailUiState.Loaded)?.fetchedAt != refreshedAt) {
                    updated = awaitItem()
                }
                assertEquals(refreshedAt, updated.fetchedAt)
            }

            coVerify(exactly = 1) {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            }
            coVerify(exactly = 0) {
                forecastRepo.refreshCityForecast(any(), any(), any())
            }
        }

    @Test
    fun `refresh externe - ignore autre ville et valeur plus ancienne`() = runTest(dispatcher) {
        val currentAt = Instant.parse("2026-06-28T10:05:00Z")
        val olderAt = Instant.parse("2026-06-28T10:00:00Z")
        val initial = buildForecast(paris).copy(fetchedAt = currentAt)
        val lyon = City("2", "Lyon", country = "France", latitude = 45.75, longitude = 4.85)

        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(initial))

        val vm = buildViewModel()
        vm.state.test {
            var state = awaitItem()
            while ((state as? CityDetailUiState.Loaded)?.fetchedAt != currentAt) {
                state = awaitItem()
            }

            forecastUpdates.emit(
                buildForecast(lyon).copy(fetchedAt = Instant.parse("2026-06-28T10:10:00Z"))
            )
            forecastUpdates.emit(
                buildForecast(paris, model = WeatherModel.GFS).copy(fetchedAt = olderAt)
            )
            expectNoEvents()
            assertEquals(currentAt, (vm.state.value as CityDetailUiState.Loaded).fetchedAt)
        }
    }

    @Test
    fun `refresh - succès émet RefreshFeedback Success`() = runTest(dispatcher) {
        coEvery {
            forecastRepo.refreshCityForecast(eq(paris), any(), any())
        } returns ApiResult.Success(buildForecast(paris))

        val vm = buildViewModel()
        vm.refreshFeedback.test {
            vm.refresh()
            assertEquals(RefreshFeedback.Success, awaitItem())
        }
    }

    @Test
    fun `refresh - erreur émet RefreshFeedback Error avec le message`() = runTest(dispatcher) {
        coEvery {
            forecastRepo.refreshCityForecast(eq(paris), any(), any())
        } returns ApiResult.Error(RuntimeException(), "Timeout réseau")

        val vm = buildViewModel()
        vm.refreshFeedback.test {
            vm.refresh()
            val feedback = awaitItem()
            assertTrue("expected Error, got $feedback", feedback is RefreshFeedback.Error)
            assertEquals("Timeout réseau", (feedback as RefreshFeedback.Error).message)
        }
    }

    @Test
    fun `refresh - city introuvable émet RefreshFeedback Error (string stubbée)`() =
        runTest(dispatcher) {
            favoritesFlow.value = emptyList()

            val vm = buildViewModel(cityId = "ghost")
            vm.refreshFeedback.test {
                vm.refresh()
                val feedback = awaitItem()
                assertTrue(feedback is RefreshFeedback.Error)
                assertEquals("stubbed-message", (feedback as RefreshFeedback.Error).message)
            }
        }

    @Test
    fun `refresh - isRefreshing termine à false après l'opération`() = runTest(dispatcher) {
        coEvery {
            forecastRepo.refreshCityForecast(eq(paris), any(), any())
        } returns ApiResult.Success(buildForecast(paris))

        val vm = buildViewModel()
        assertEquals(false, vm.isRefreshing.value) // initial

        vm.refresh()

        // ⚠ Sous UnconfinedTestDispatcher + mock immédiat, le toggle
        // `false → true → false` n'est PAS observable : la coroutine refresh()
        // s'exécute synchronement (le mock ne suspend pas), donc StateFlow
        // conflate les deux émissions. On peut juste vérifier l'état final.
        assertEquals(false, vm.isRefreshing.value)
        coVerify { forecastRepo.refreshCityForecast(eq(paris), any(), any()) }
    }

    @Test
    fun `refresh - DROP_OLDEST sur le channel - 2 refresh rapides ne mettent qu'1 feedback en file`() =
        runTest(dispatcher) {
            // Le channel est créé avec capacity=1 + onBufferOverflow=DROP_OLDEST.
            // Si l'utilisateur spam le bouton, on garde seulement le dernier.
            coEvery {
                forecastRepo.refreshCityForecast(eq(paris), any(), any())
            } returns ApiResult.Success(buildForecast(paris))

            val vm = buildViewModel()
            // On déclenche 2 refresh sans consommer le channel
            vm.refresh()
            vm.refresh()

            vm.refreshFeedback.test {
                // Le feedback consommé est Success (le dernier — l'avant-dernier
                // a été dropé). On ne peut pas avoir 2 Success consécutifs
                // grâce à DROP_OLDEST.
                assertEquals(RefreshFeedback.Success, awaitItem())
                expectNoEvents()
            }
        }

    // ──────────────── Helpers ────────────────


    private fun buildScenarioForecast(city: City): CityForecast {
        val today = LocalDate.of(2026, 6, 28)
        val values = linkedMapOf(
            WeatherModel.GFS to 10.0,
            WeatherModel.ECMWF to 10.4,
            WeatherModel.ARPEGE_EUROPE to 10.8,
            WeatherModel.UKMO_GLOBAL to 20.0,
            WeatherModel.GEM_GLOBAL to 20.4
        )
        return CityForecast(
            city = city,
            seriesByModel = values.mapValues { (model, value) ->
                ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(
                        timestamps = listOf(testNow),
                        temperature2m = listOf(value),
                        precipitation = listOf(0.0),
                        windSpeed10m = listOf(10.0)
                    ),
                    daily = DailyForecast(
                        dates = listOf(today),
                        tempMax = listOf(value + 2.0),
                        tempMin = listOf(value - 6.0),
                        precipitationSum = listOf(0.0),
                        windSpeedMax = listOf(10.0)
                    )
                )
            }
        )
    }

    private fun buildForecast(
        city: City,
        model: WeatherModel = WeatherModel.AROME_FRANCE_HD,
        temperature: Double = 20.0
    ): CityForecast {
        val today = LocalDate.of(2026, 6, 28)
        val now = testNow
        val daily = DailyForecast(
            dates = listOf(today),
            tempMax = listOf(temperature + 2.0),
            tempMin = listOf(temperature - 6.0),
            precipitationSum = listOf(0.0),
            windSpeedMax = listOf(10.0)
        )
        val hourly = HourlyForecast(
            timestamps = listOf(now),
            temperature2m = listOf(temperature),
            precipitation = listOf(0.0),
            windSpeed10m = listOf(10.0)
        )
        val series = ForecastSeries(
            model = model,
            hourly = hourly,
            daily = daily
        )
        return CityForecast(
            city = city,
            seriesByModel = mapOf(model to series),
            errors = emptyMap()
        )
    }
}