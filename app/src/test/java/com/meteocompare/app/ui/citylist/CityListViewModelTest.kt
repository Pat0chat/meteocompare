package com.meteocompare.app.ui.citylist

import app.cash.turbine.test
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Tests de [CityListViewModel].
 *
 * Deux pièges majeurs à gérer dans ces tests :
 *
 *   1. **StateFlow conflation sous UnconfinedTestDispatcher** : tout s'exécute
 *      synchronement, donc quand on `set favoritesFlow.value = [paris]`, la
 *      VM lance immédiatement `getCityForecastStream`, qui émet
 *      immédiatement le résultat, qui update `forecastsById` immédiatement.
 *      Au moment où le subscriber observe `uiState`, il a déjà l'état final.
 *      Les states intermédiaires (`Loading`) sont conflatés.
 *      → On utilise une boucle "await jusqu'à atteindre l'état recherché"
 *        plutôt qu'un `awaitItem()` strict par étape.
 *
 *   2. **`stateIn(WhileSubscribed)` + `.value`** : sans subscriber actif,
 *      `uiState.value` retourne `initialValue` (CityListUiState vide), pas
 *      le state réel. Donc `onRefreshAll()` qui lit `uiState.value.items`
 *      voit une liste vide → aucun refresh lancé.
 *      → On maintient une souscription via `backgroundScope.launch` avant
 *        d'appeler les actions qui lisent `.value`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CityListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testNow = Instant.parse("2026-06-28T12:00:00Z")
    private val testClock = Clock.fixed(testNow, ZoneOffset.UTC)

    private val paris = City("1", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val lyon = City("2", "Lyon", country = "France", latitude = 45.75, longitude = 4.85)

    private val favoritesFlow = MutableStateFlow<List<City>>(emptyList())
    private val modelsFlow = MutableStateFlow(WeatherModel.MVP_SELECTION)
    private val refreshIntervalFlow = MutableStateFlow(RefreshInterval.DEFAULT)
    private val forecastEngineFlow = MutableStateFlow(ForecastEngine.DEFAULT)
    private val forecastUpdates = MutableSharedFlow<CityForecast>(extraBufferCapacity = 4)
    private val onlineFlow = MutableStateFlow(true)

    private val cityRepo: CityRepository = mockk(relaxed = true) {
        coEvery { observeFavorites() } returns favoritesFlow
    }
    private val forecastRepo: ForecastRepository = mockk(relaxed = true)
    private val marineRepo: MarineRepository = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true) {
        every { isOnline() } answers { onlineFlow.value }
        every { observeOnline() } returns onlineFlow
    }
    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { observeEnabledModels() } returns modelsFlow
        // observeRefreshInterval() est utilisé par le combine dans l'init du
        // ViewModel. Sans ce stub, MockK relaxed retourne un flow VIDE et le
        // combine à 3 sources ne s'active jamais → aucun stream forecast n'est
        // lancé et les tests Turbine timeout à 3s. Un flow avec DEFAULT (HOUR_1)
        // reproduit le comportement historique — les tests attendent que le
        // stream soit lancé et émette, ce qui suppose que le combine amont ait
        // reçu une valeur pour chaque source.
        coEvery { observeRefreshInterval() } returns refreshIntervalFlow
        every { observeForecastEngine() } returns forecastEngineFlow
    }
    private val calculator = ConfidenceCalculator(EqualWeighting())
    private val engineContextProvider = ForecastEngineContextProvider(mockk(relaxed = true))

    private lateinit var viewModel: CityListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        favoritesFlow.value = emptyList()
        modelsFlow.value = WeatherModel.MVP_SELECTION
        refreshIntervalFlow.value = RefreshInterval.DEFAULT
        forecastEngineFlow.value = ForecastEngine.DEFAULT
        onlineFlow.value = true
        // Par défaut, getCityForecastStream renvoie un flow qui reste en cours.
        // Les tests qui veulent un résultat spécifique l'overrident AVANT
        // d'instancier la VM (sinon l'init de syncStreams capture l'ancien stub).
        //
        // NB : la signature a maintenant 5 paramètres (city, models, forecastDays,
        // forceRefresh, maxCacheAgeMs). MockK match sur le nombre exact d'args,
        // donc les 5 any() sont nécessaires — 4 renverrait "no answer found".
        coEvery {
            forecastRepo.getCityForecastStream(any(), any(), any(), any(), any())
        } returns flow {
            /* ne rien émettre, ne pas terminer */
        }
        every { forecastRepo.observeForecastUpdates() } returns forecastUpdates
        viewModel = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ──────────────── uiState ────────────────

    @Test
    fun `uiState - vide initialement quand pas de favoris`() = runTest(dispatcher) {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty)
            assertEquals(false, initial.isRefreshing)
        }
    }

    @Test
    fun `uiState - expose le mode hors connexion en temps reel`() = runTest(dispatcher) {
        viewModel.uiState.test {
            assertTrue(awaitItem().isOnline)
            onlineFlow.value = false
            var state = awaitItem()
            while (state.isOnline) state = awaitItem()
            assertEquals(false, state.isOnline)
        }
    }

    @Test
    fun `uiState - liste les favoris en Loading tant que pas de forecast`() = runTest(dispatcher) {
        viewModel.uiState.test {
            awaitItem() // état initial vide
            favoritesFlow.value = listOf(paris, lyon)

            // Avec le mock par défaut (stream qui ne termine pas), on doit voir
            // les villes en Loading. Loop pour atteindre l'état avec 2 items.
            var state = awaitItem()
            while (state.items.size < 2) state = awaitItem()
            assertEquals(2, state.items.size)
            assertTrue(state.items.all { it.forecast is ForecastState.Loading })
        }
    }

    @Test
    fun `uiState - quand le forecast arrive en succès, passe en Loaded`() = runTest(dispatcher) {
        // ⚠ Stub AVANT de réinstancier la VM : sinon l'init de syncStreams
        // capture l'ancien stub (hanging flow) au moment du combine initial.
        val forecast = buildForecast(paris, dailyMaxTemp = 22.0)
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))

        // Nouvelle VM qui capturera le bon stub
        val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)

        vm.uiState.test {
            awaitItem() // initial vide
            favoritesFlow.value = listOf(paris)

            // Loading intermédiaire conflaté → on loop jusqu'à Loaded.
            var state = awaitItem()
            while (state.items.firstOrNull()?.forecast !is ForecastState.Loaded) {
                state = awaitItem()
            }
            val card = state.items.first()
            assertEquals(paris.id, card.city.id)
        }
    }

    @Test
    fun `changement de moteur recalcule la CityCard sans refetch`() = runTest(dispatcher) {
        val forecast = buildScenarioForecast(paris)
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))

        // Réutiliser la VM créée dans setUp : en instancier une seconde laisserait
        // deux collecteurs actifs sur favoritesFlow et doublerait artificiellement
        // l'appel au repository, ce qui invaliderait précisément le coVerify
        // « sans refetch » que ce test cherche à protéger.
        viewModel.uiState.test {
            awaitItem()
            favoritesFlow.value = listOf(paris)
            var loadedState = awaitItem()
            while (loadedState.items.firstOrNull()?.forecast !is ForecastState.Loaded) loadedState = awaitItem()
            val before = (loadedState.items.first().forecast as ForecastState.Loaded).currentTemp

            coVerify(exactly = 1) {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            }
            forecastEngineFlow.value = ForecastEngine.SCENARIOS

            var updated = awaitItem()
            while ((updated.items.firstOrNull()?.forecast as? ForecastState.Loaded)?.currentTemp == before) {
                updated = awaitItem()
            }
            val after = (updated.items.first().forecast as ForecastState.Loaded).currentTemp
            assertNotEquals(before, after)
            coVerify(exactly = 1) {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `uiState - error path conserve la ville dans la liste avec ForecastState Error`() =
        runTest(dispatcher) {
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Error(RuntimeException("net"), "Pas de connexion"))

            val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)

            vm.uiState.test {
                awaitItem()
                favoritesFlow.value = listOf(paris)
                var state = awaitItem()
                while (state.items.firstOrNull()?.forecast !is ForecastState.Error) {
                    state = awaitItem()
                }
                assertEquals("Pas de connexion",
                    (state.items.first().forecast as ForecastState.Error).message)
            }
        }

    // ──────────────── Refresh ────────────────

    @Test
    fun `refresh externe - met à jour le timestamp de la CityCard sans nouveau fetch`() =
        runTest(dispatcher) {
            val initialAt = Instant.parse("2026-06-28T10:00:00Z")
            val refreshedAt = Instant.parse("2026-06-28T10:05:00Z")
            val initial = buildForecast(paris, dailyMaxTemp = 22.0).copy(fetchedAt = initialAt)
            val refreshed = buildForecast(paris, dailyMaxTemp = 24.0).copy(fetchedAt = refreshedAt)

            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(initial))

            val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)

            vm.uiState.test {
                awaitItem()
                favoritesFlow.value = listOf(paris)

                var state = awaitItem()
                while ((state.items.firstOrNull()?.forecast as? ForecastState.Loaded)?.fetchedAt != initialAt) {
                    state = awaitItem()
                }

                // Ignore les appels d'initialisation des deux ViewModels présents
                // dans cette classe. À partir d'ici, on vérifie uniquement que
                // l'événement partagé ne relance aucun stream réseau.
                clearMocks(forecastRepo, answers = false, recordedCalls = true)

                forecastUpdates.emit(refreshed)

                var updated = awaitItem()
                while ((updated.items.firstOrNull()?.forecast as? ForecastState.Loaded)?.fetchedAt != refreshedAt) {
                    updated = awaitItem()
                }
                val loaded = updated.items.first().forecast as ForecastState.Loaded
                assertEquals(refreshedAt, loaded.fetchedAt)
                assertEquals(22.0, loaded.currentTemp ?: Double.NaN, 0.001)
            }

            coVerify(exactly = 0) {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            }
        }


    @Test
    fun `refresh externe - accepte un jeu de modèles différent avec le même timestamp`() =
        runTest(dispatcher) {
            val fetchedAt = Instant.parse("2026-06-28T10:05:00Z")
            val initial = buildForecast(
                paris,
                dailyMaxTemp = 22.0,
                model = WeatherModel.AROME_FRANCE_HD
            ).copy(fetchedAt = fetchedAt)
            val refreshed = buildForecast(
                paris,
                dailyMaxTemp = 30.0,
                model = WeatherModel.GFS
            ).copy(fetchedAt = fetchedAt)

            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(initial))

            val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)

            vm.uiState.test {
                awaitItem()
                favoritesFlow.value = listOf(paris)

                var state = awaitItem()
                while ((state.items.firstOrNull()?.forecast as? ForecastState.Loaded)?.sourceModels !=
                    setOf(WeatherModel.AROME_FRANCE_HD)
                ) {
                    state = awaitItem()
                }

                clearMocks(forecastRepo, answers = false, recordedCalls = true)
                forecastUpdates.emit(refreshed)

                var updated = awaitItem()
                while ((updated.items.firstOrNull()?.forecast as? ForecastState.Loaded)?.sourceModels !=
                    setOf(WeatherModel.GFS)
                ) {
                    updated = awaitItem()
                }

                val loaded = updated.items.first().forecast as ForecastState.Loaded
                assertEquals(fetchedAt, loaded.fetchedAt)
                assertEquals(28.0, loaded.currentTemp ?: Double.NaN, 0.001)
            }

            coVerify(exactly = 0) {
                forecastRepo.getCityForecastStream(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `ajouter un favori ne relance pas le stream fini des villes deja initialisees`() =
        runTest(dispatcher) {
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(buildForecast(paris, dailyMaxTemp = 22.0)))
            coEvery {
                forecastRepo.getCityForecastStream(eq(lyon), any(), any(), any(), any())
            } returns flowOf(ApiResult.Success(buildForecast(lyon, dailyMaxTemp = 20.0)))

            // Utilise l'instance créée par setUp. Créer une seconde VM ici
            // abonnerait deux collecteurs au même favoritesFlow et lancerait
            // légitimement deux streams pour la nouvelle ville.
            val vm = viewModel
            backgroundScope.launch { vm.uiState.collect {} }
            favoritesFlow.value = listOf(paris)
            vm.uiState.first { it.items.firstOrNull()?.forecast is ForecastState.Loaded }

            clearMocks(forecastRepo, answers = false, recordedCalls = true)
            favoritesFlow.value = listOf(paris, lyon)
            vm.uiState.first { state ->
                state.items.size == 2 && state.items.all { it.forecast is ForecastState.Loaded }
            }

            coVerify(exactly = 0) {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
            }
            coVerify(exactly = 1) {
                forecastRepo.getCityForecastStream(eq(lyon), any(), any(), any(), any())
            }
        }

    @Test
    fun `refresh global en erreur conserve la derniere carte chargee`() = runTest(dispatcher) {
        val initialAt = Instant.parse("2026-06-28T10:00:00Z")
        val initial = buildForecast(paris, dailyMaxTemp = 22.0).copy(fetchedAt = initialAt)
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(initial))
        coEvery {
            forecastRepo.refreshCityForecast(eq(paris), any(), any())
        } returns ApiResult.Error(RuntimeException("offline"), "Pas de connexion")

        val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)
        backgroundScope.launch { vm.uiState.collect {} }
        favoritesFlow.value = listOf(paris)
        vm.uiState.first {
            (it.items.firstOrNull()?.forecast as? ForecastState.Loaded)?.fetchedAt == initialAt
        }

        vm.onRefreshAll()

        val after = vm.uiState.value.items.single().forecast
        assertTrue(after is ForecastState.Loaded)
        assertEquals(initialAt, (after as ForecastState.Loaded).fetchedAt)
    }

    @Test
    fun `onRefreshAll - termine avec isRefreshing à false et appelle refresh pour chaque favori`() =
        runTest(dispatcher) {
            // Subscribe BEFORE setting favorites pour que uiState.value soit fiable.
            backgroundScope.launch { viewModel.uiState.collect {} }
            favoritesFlow.value = listOf(paris, lyon)
            // Attend que uiState reflète bien les 2 villes (combine émet)
            viewModel.uiState.first { it.items.size == 2 }

            coEvery { forecastRepo.refreshCityForecast(any(), any(), any()) } returns
                ApiResult.Success(buildForecast(paris, dailyMaxTemp = 20.0))

            viewModel.onRefreshAll()

            // isRefreshing : on ne peut PAS observer le transient `true` car
            // le mock refreshCityForecast retourne sans suspendre — toute la
            // coroutine s'exécute synchronement et StateFlow conflate. On vérifie
            // donc juste l'état final (le bloc finally remet à false).
            assertEquals(false, viewModel.uiState.value.isRefreshing)

            // Et chaque favori a bien été refreshé
            coVerify { forecastRepo.refreshCityForecast(eq(paris), any(), any()) }
            coVerify { forecastRepo.refreshCityForecast(eq(lyon), any(), any()) }
        }

    // ──────────────── Retry ────────────────

    @Test
    fun `onRetry - applique le résultat du refresh sur la ville`() = runTest(dispatcher) {
        // Initial : Error pour paris
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Error(RuntimeException(), "boom"))
        // Retry : refreshCityForecast renvoie succès
        val freshForecast = buildForecast(paris, dailyMaxTemp = 25.0)
        coEvery {
            forecastRepo.refreshCityForecast(eq(paris), any(), any())
        } returns ApiResult.Success(freshForecast)

        val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)

        vm.uiState.test {
            awaitItem() // initial vide
            favoritesFlow.value = listOf(paris)

            // Atteindre l'état Error
            var state = awaitItem()
            while (state.items.firstOrNull()?.forecast !is ForecastState.Error) state = awaitItem()

            vm.onRetry(paris)

            // Atteindre l'état Loaded (transition Loading intermédiaire conflatée)
            var final = awaitItem()
            while (final.items.firstOrNull()?.forecast !is ForecastState.Loaded) {
                final = awaitItem()
            }
            assertTrue(final.items.first().forecast is ForecastState.Loaded)
        }
    }


    @Test
    fun `changing refresh interval restarts streams with the new cache policy`() = runTest(dispatcher) {
        favoritesFlow.value = listOf(paris)

        coVerify(atLeast = 1) {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), eq(RefreshInterval.DEFAULT.millis))
        }

        refreshIntervalFlow.value = RefreshInterval.HOURS_3

        coVerify(atLeast = 1) {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), eq(RefreshInterval.HOURS_3.millis))
        }
    }

    // ──────────────── Add city / search ────────────────

    @Test
    fun `onAddCity - persiste dans le repo et reset le query`() = runTest(dispatcher) {
        viewModel.onSearchQueryChanged("Par")
        viewModel.onAddCity(paris)

        coVerify { cityRepo.addFavorite(paris) }

        viewModel.addCityState.test {
            assertEquals("", awaitItem().query)
        }
    }

    @Test
    fun `onRemoveCity - délègue au repo`() = runTest(dispatcher) {
        viewModel.onRemoveCity("1")
        coVerify { cityRepo.removeFavorite("1") }
    }

    @Test
    fun `addCityState - query trop court (1 char) ne déclenche pas de recherche`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.addCityState.collect {} }

            viewModel.onSearchQueryChanged("P")
            advanceTimeBy(500)

            coVerify(exactly = 0) { cityRepo.searchCities(any()) }
        }

    @Test
    fun `addCityState - debounce 700ms - frappes rapides ne déclenchent qu'une seule requête`() =
        runTest(dispatcher) {
            coEvery { cityRepo.searchCities(any()) } returns ApiResult.Success(listOf(paris))
            backgroundScope.launch { viewModel.addCityState.collect {} }

            viewModel.onSearchQueryChanged("Pa")
            advanceTimeBy(100)
            viewModel.onSearchQueryChanged("Par")
            advanceTimeBy(100)
            viewModel.onSearchQueryChanged("Pari")
            advanceTimeBy(100)
            viewModel.onSearchQueryChanged("Paris")
            // 4 changements en 300 ms. debounce(700) attend ensuite
            // 700 ms de silence avant de lancer uniquement la dernière recherche.
            advanceTimeBy(699)
            runCurrent()
            coVerify(exactly = 0) { cityRepo.searchCities(any()) }

            advanceTimeBy(1)
            runCurrent()
            coVerify(exactly = 1) { cityRepo.searchCities("Paris") }
            coVerify(exactly = 0) { cityRepo.searchCities("Pa") }
            coVerify(exactly = 0) { cityRepo.searchCities("Par") }
            coVerify(exactly = 0) { cityRepo.searchCities("Pari") }
        }

    @Test
    fun `addCityState - une recherche en succès expose les results et clear l'error`() =
        runTest(dispatcher) {
            coEvery { cityRepo.searchCities("Paris") } returns ApiResult.Success(listOf(paris))

            viewModel.addCityState.test {
                awaitItem() // initial vide
                viewModel.onSearchQueryChanged("Paris")
                advanceTimeBy(700)
                runCurrent()

                // Plusieurs émissions possibles via combine — on attend l'état final
                var state = awaitItem()
                while (state.results.isEmpty() && state.error == null) {
                    state = awaitItem()
                }
                assertEquals(listOf(paris), state.results)
                assertNull(state.error)
                assertEquals(false, state.isSearching)
            }
        }

    @Test
    fun `addCityState - recherche en erreur expose error + results vides`() = runTest(dispatcher) {
        coEvery { cityRepo.searchCities("Xyz") } returns
            ApiResult.Error(RuntimeException("net"), "Pas de connexion")

        viewModel.addCityState.test {
            awaitItem()
            viewModel.onSearchQueryChanged("Xyz")
            advanceTimeBy(700)
            runCurrent()

            var state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertEquals("Pas de connexion", state.error)
            assertTrue(state.results.isEmpty())
        }
    }

    @Test
    fun `cache ancien sans date du jour nest pas presente comme prevision daujourdhui`() = runTest(dispatcher) {
        val yesterday = LocalDate.of(2026, 6, 27)
        val stale = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(
                    model = WeatherModel.GFS,
                    hourly = HourlyForecast(
                        timestamps = listOf(testNow.minusSeconds(24 * 3600)),
                        temperature2m = listOf(18.0),
                        precipitation = listOf(0.0),
                        windSpeed10m = listOf(5.0)
                    ),
                    daily = DailyForecast(
                        dates = listOf(yesterday),
                        tempMax = listOf(22.0),
                        tempMin = listOf(14.0),
                        precipitationSum = listOf(0.0),
                        windSpeedMax = listOf(10.0)
                    )
                )
            )
        )
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(stale))
        val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, testClock, dispatcher, engineContextProvider)

        vm.uiState.test {
            awaitItem()
            favoritesFlow.value = listOf(paris)
            var state = awaitItem()
            while (state.items.firstOrNull()?.forecast !is ForecastState.Error) state = awaitItem()
            assertEquals(
                com.meteocompare.app.R.string.forecast_error_no_today,
                (state.items.first().forecast as ForecastState.Error).messageRes
            )
        }
    }

    @Test
    fun `heure de depart mini forecast suit le slot reel et non le plancher de now`() = runTest(dispatcher) {
        val lateNow = Instant.parse("2026-06-28T12:56:00Z")
        val lateClock = Clock.fixed(lateNow, ZoneOffset.UTC)
        val daily = DailyForecast(
            dates = listOf(LocalDate.of(2026, 6, 28)),
            tempMax = listOf(24.0),
            tempMin = listOf(16.0),
            precipitationSum = listOf(0.0),
            windSpeedMax = listOf(10.0)
        )
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(
                    model = WeatherModel.GFS,
                    hourly = HourlyForecast(
                        timestamps = listOf(Instant.parse("2026-06-28T13:00:00Z")),
                        temperature2m = listOf(22.0),
                        precipitation = listOf(0.0),
                        windSpeed10m = listOf(8.0)
                    ),
                    daily = daily
                )
            )
        )
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))
        val vm = CityListViewModel(cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs, lateClock, dispatcher, engineContextProvider)

        vm.uiState.test {
            awaitItem()
            favoritesFlow.value = listOf(paris)
            var state = awaitItem()
            while (state.items.firstOrNull()?.forecast !is ForecastState.Loaded) state = awaitItem()
            val loaded = state.items.first().forecast as ForecastState.Loaded
            assertEquals(LocalDateTime.of(2026, 6, 28, 13, 0), loaded.hourlyStartTime)
        }
    }


    @Test
    fun `home mini timeline receives condition probability and amount from the same hourly aggregate`() = runTest(dispatcher) {
        val daily = DailyForecast(
            dates = listOf(LocalDate.of(2026, 6, 28)),
            tempMax = listOf(24.0),
            tempMin = listOf(16.0),
            precipitationSum = listOf(1.2),
            windSpeedMax = listOf(10.0)
        )
        val forecast = CityForecast(
            city = paris,
            seriesByModel = mapOf(
                WeatherModel.GFS to ForecastSeries(
                    model = WeatherModel.GFS,
                    hourly = HourlyForecast(
                        timestamps = listOf(testNow),
                        temperature2m = listOf(18.0),
                        precipitation = listOf(1.2),
                        windSpeed10m = listOf(8.0),
                        weatherCode = listOf(61),
                        precipitationProbability = listOf(80),
                        cloudCover = listOf(90)
                    ),
                    daily = daily
                )
            )
        )
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))
        val vm = CityListViewModel(
            cityRepo, forecastRepo, marineRepo, networkMonitor, calculator, prefs,
            testClock, dispatcher, engineContextProvider
        )

        vm.uiState.test {
            awaitItem()
            favoritesFlow.value = listOf(paris)
            var state = awaitItem()
            while (state.items.firstOrNull()?.forecast !is ForecastState.Loaded) state = awaitItem()
            val loaded = state.items.first().forecast as ForecastState.Loaded

            assertEquals(18.0, loaded.next12hTemps.first() ?: error("temperature absente"), 0.001)
            assertEquals(80, loaded.next12hPrecipProb.first())
            assertEquals(1.2, loaded.next12hPrecipMm.first() ?: error("pluie absente"), 0.001)
            assertEquals(WeatherCondition.RAIN, loaded.next12hConditions.first())
        }
    }


    @Test
    fun `marine availability from cache is exposed independently from activation`() = runTest(dispatcher) {
        val cached = mockk<MarineForecast>()
        every { cached.coastal } returns true
        every { cached.fetchedAtEpochMs } returns testClock.millis()
        coEvery { marineRepo.getFreshCached(paris.id) } returns cached

        viewModel.uiState.test {
            awaitItem()
            favoritesFlow.value = listOf(paris)
            var state = awaitItem()
            while (state.items.firstOrNull()?.isMarineAvailable != true) state = awaitItem()

            val item = state.items.first()
            assertTrue(item.isMarineAvailable)
            assertTrue(!item.city.marineEnabled)
            coVerify(exactly = 0) { marineRepo.getMarine(paris, forceRefresh = false) }
        }
    }

    @Test
    fun `expired marine cache triggers a fresh availability check`() = runTest(dispatcher) {
        val fresh = mockk<MarineForecast>()
        every { fresh.coastal } returns true
        every { fresh.fetchedAtEpochMs } returns testClock.millis()
        coEvery { marineRepo.getFreshCached(paris.id) } returns null
        coEvery { marineRepo.getMarine(paris, forceRefresh = false) } returns ApiResult.Success(fresh)

        viewModel.uiState.test {
            awaitItem()
            favoritesFlow.value = listOf(paris)
            var state = awaitItem()
            while (state.items.firstOrNull()?.isMarineAvailable != true) state = awaitItem()

            assertTrue(state.items.first().isMarineAvailable)
            coVerify(exactly = 1) { marineRepo.getFreshCached(paris.id) }
            coVerify(exactly = 1) { marineRepo.getMarine(paris, forceRefresh = false) }
        }
    }

    @Test
    fun `expired marine cache is used offline then revalidated when network returns`() = runTest(dispatcher) {
        val stale = mockk<MarineForecast>()
        every { stale.coastal } returns true
        every { stale.fetchedAtEpochMs } returns
            testClock.millis() - MarineRepository.AVAILABILITY_CACHE_TTL_MS - 1
        val fresh = mockk<MarineForecast>()
        every { fresh.coastal } returns true
        every { fresh.fetchedAtEpochMs } returns testClock.millis()
        coEvery { marineRepo.getFreshCached(paris.id) } returns null
        coEvery { marineRepo.getCached(paris.id) } returns stale
        coEvery { marineRepo.getMarine(paris, forceRefresh = false) } returns ApiResult.Success(fresh)

        onlineFlow.value = false
        viewModel.uiState.test {
            awaitItem()
            favoritesFlow.value = listOf(paris)
            var state = awaitItem()
            while (state.items.firstOrNull()?.isMarineAvailable != true) state = awaitItem()

            assertTrue(state.items.first().isMarineAvailable)
            coVerify(exactly = 1) { marineRepo.getFreshCached(paris.id) }
            coVerify(exactly = 1) { marineRepo.getCached(paris.id) }
            coVerify(exactly = 0) { marineRepo.getMarine(paris, forceRefresh = false) }

            // Régression : l'ancien job hors ligne pouvait encore être présent
            // dans marineAvailabilityJobs au moment exact du retour réseau. Le
            // collector ignorait alors la revalidation et aucun nouvel événement
            // ne la relançait après le finally. Le ViewModel ferme désormais cette
            // fenêtre de concurrence ; runCurrent vide simplement le scheduler afin
            // d'observer la relance déclenchée par le correctif de production.
            onlineFlow.value = true
            runCurrent()

            // Le retour réseau est lui-même une émission de uiState. Il faut
            // la consommer : sinon Turbine termine le bloc avec un événement
            // restant et lève TurbineAssertionError alors que les interactions
            // repository sont déjà correctes.
            var reconnected = awaitItem()
            while (!reconnected.isOnline) reconnected = awaitItem()
            assertTrue(reconnected.items.first().isMarineAvailable)

            coVerify(exactly = 2) { marineRepo.getFreshCached(paris.id) }
            coVerify(exactly = 1) { marineRepo.getCached(paris.id) }
            coVerify(exactly = 1) { marineRepo.getMarine(paris, forceRefresh = false) }
            cancelAndIgnoreRemainingEvents()
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
        dailyMaxTemp: Double,
        model: WeatherModel = WeatherModel.AROME_FRANCE_HD
    ): CityForecast {
        val today = LocalDate.of(2026, 6, 28)
        val now = testNow
        val daily = DailyForecast(
            dates = listOf(today),
            tempMax = listOf(dailyMaxTemp),
            tempMin = listOf(dailyMaxTemp - 8),
            precipitationSum = listOf(0.0),
            windSpeedMax = listOf(10.0)
        )
        val hourly = HourlyForecast(
            timestamps = listOf(now),
            temperature2m = listOf(dailyMaxTemp - 2),
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
