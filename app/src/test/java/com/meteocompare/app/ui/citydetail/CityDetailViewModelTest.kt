package com.meteocompare.app.ui.citydetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.ui.navigation.Destinations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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

    private val paris = City("1", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val favoritesFlow = MutableStateFlow(listOf(paris))
    private val modelsFlow = MutableStateFlow(WeatherModel.MVP_SELECTION)

    private val cityRepo: CityRepository = mockk(relaxed = true) {
        coEvery { observeFavorites() } returns favoritesFlow
    }
    private val forecastRepo: ForecastRepository = mockk(relaxed = true)
    private val climateRepo: ClimateNormalsRepository = mockk(relaxed = true) {
        // Par défaut, normales en échec → loadedNormals reste null
        coEvery { getNormalsForCity(any()) } returns
            ApiResult.Error(RuntimeException(), "normals unavailable")
    }
    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { observeEnabledModels() } returns modelsFlow
    }

    // Context : on stub getString pour retourner des strings prévisibles.
    // any<Int>() évite la dépendance sur la génération de R en test pur JVM.
    private val context: Context = mockk(relaxed = true) {
        every { getString(any<Int>()) } returns "stubbed-message"
    }

    private val calculator = ConfidenceCalculator(EqualWeighting())

    private fun buildViewModel(cityId: String = "1"): CityDetailViewModel {
        val saved = SavedStateHandle(mapOf(Destinations.CITY_DETAIL_ARG to cityId))
        return CityDetailViewModel(
            context = context,
            savedStateHandle = saved,
            cityRepository = cityRepo,
            forecastRepository = forecastRepo,
            climateNormalsRepository = climateRepo,
            confidenceCalculator = calculator,
            userPreferences = prefs
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Par défaut, stream forecast ne fait rien (jamais terminé)
        coEvery {
            forecastRepo.getCityForecastStream(any(), any(), any(), any())
        } returns flow { /* hang */ }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any())
        } returns flowOf(ApiResult.Success(forecast))

        val vm = buildViewModel()
        vm.state.test {
            var state = awaitItem()
            while (state !is CityDetailUiState.Loaded) state = awaitItem()
            assertEquals(paris, state.forecast.city)
            assertNotNull(state.currentTemp)
            // Pas de normales (stub renvoie error)
            assertEquals(null, state.normals)
        }
    }

    @Test
    fun `loadInitial - forecast en erreur sans cache → Error`() = runTest(dispatcher) {
        coEvery {
            forecastRepo.getCityForecastStream(eq(paris), any(), any(), any())
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
                forecastRepo.getCityForecastStream(eq(paris), any(), any(), any())
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

    private fun buildForecast(city: City): CityForecast {
        val today = LocalDate.of(2026, 6, 28)
        val now = Instant.parse("2026-06-28T12:00:00Z")
        val daily = DailyForecast(
            dates = listOf(today),
            tempMax = listOf(22.0),
            tempMin = listOf(14.0),
            precipitationSum = listOf(0.0),
            windSpeedMax = listOf(10.0)
        )
        val hourly = HourlyForecast(
            timestamps = listOf(now),
            temperature2m = listOf(20.0),
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
