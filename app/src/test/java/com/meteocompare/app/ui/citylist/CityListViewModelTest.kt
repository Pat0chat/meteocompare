package com.meteocompare.app.ui.citylist

import app.cash.turbine.test
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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

    private val paris = City("1", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val lyon = City("2", "Lyon", country = "France", latitude = 45.75, longitude = 4.85)

    private val favoritesFlow = MutableStateFlow<List<City>>(emptyList())
    private val modelsFlow = MutableStateFlow(WeatherModel.MVP_SELECTION)

    private val cityRepo: CityRepository = mockk(relaxed = true) {
        coEvery { observeFavorites() } returns favoritesFlow
    }
    private val forecastRepo: ForecastRepository = mockk(relaxed = true)
    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { observeEnabledModels() } returns modelsFlow
    }
    private val calculator = ConfidenceCalculator(EqualWeighting())

    private lateinit var viewModel: CityListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Par défaut, getCityForecastStream renvoie un flow qui reste en cours.
        // Les tests qui veulent un résultat spécifique l'overrident AVANT
        // d'instancier la VM (sinon l'init de syncStreams capture l'ancien stub).
        coEvery { forecastRepo.getCityForecastStream(any(), any(), any(), any()) } returns flow {
            /* ne rien émettre, ne pas terminer */
        }
        viewModel = CityListViewModel(cityRepo, forecastRepo, calculator, prefs)
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
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))

        // Nouvelle VM qui capturera le bon stub
        val vm = CityListViewModel(cityRepo, forecastRepo, calculator, prefs)

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
    fun `uiState - error path conserve la ville dans la liste avec ForecastState Error`() =
        runTest(dispatcher) {
            coEvery {
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any())
            } returns flowOf(ApiResult.Error(RuntimeException("net"), "Pas de connexion"))

            val vm = CityListViewModel(cityRepo, forecastRepo, calculator, prefs)

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
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any())
        } returns flowOf(ApiResult.Error(RuntimeException(), "boom"))
        // Retry : refreshCityForecast renvoie succès
        val freshForecast = buildForecast(paris, dailyMaxTemp = 25.0)
        coEvery {
            forecastRepo.refreshCityForecast(eq(paris), any(), any())
        } returns ApiResult.Success(freshForecast)

        val vm = CityListViewModel(cityRepo, forecastRepo, calculator, prefs)

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
    fun `addCityState - debounce 300ms - frappes rapides ne déclenchent qu'une seule requête`() =
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
            // 4 changements en 300ms. debounce(300) attend 300ms de silence
            // → seule la dernière valeur déclenche la requête.
            advanceTimeBy(500)

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
                advanceTimeBy(500)

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
            advanceTimeBy(500)

            var state = awaitItem()
            while (state.error == null) state = awaitItem()
            assertEquals("Pas de connexion", state.error)
            assertTrue(state.results.isEmpty())
        }
    }

    // ──────────────── Helpers ────────────────

    private fun buildForecast(city: City, dailyMaxTemp: Double): CityForecast {
        val today = LocalDate.of(2026, 6, 28)
        val now = Instant.parse("2026-06-28T12:00:00Z")
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
            model = WeatherModel.AROME_FRANCE_HD,
            hourly = hourly,
            daily = daily
        )
        return CityForecast(
            city = city,
            seriesByModel = mapOf(WeatherModel.AROME_FRANCE_HD to series),
            errors = emptyMap()
        )
    }
}
