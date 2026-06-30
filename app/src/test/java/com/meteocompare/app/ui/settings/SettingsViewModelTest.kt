package com.meteocompare.app.ui.settings

import app.cash.turbine.test
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [SettingsViewModel].
 *
 * Note sur `stateIn(WhileSubscribed)` : `.value` retourne `initialValue` tant
 * qu'aucun subscriber n'est actif. Tous les tests qui dépendent de la valeur
 * réelle du flow source doivent maintenir une souscription active —
 * via `backgroundScope.launch` (auto-cancellé par runTest) ou via `.test {}`.
 *
 * Sans ça, `enabledModels.value` retourne `MVP_SELECTION` (la sélection
 * par défaut) même si on a changé `modelsFlow.value`, ce qui fait passer
 * le test pour de mauvaises raisons.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val modelsFlow = MutableStateFlow(WeatherModel.MVP_SELECTION)
    private val themeFlow = MutableStateFlow(ThemePreference.SYSTEM)
    private val languageFlow = MutableStateFlow(LanguagePreference.SYSTEM)

    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { observeEnabledModels() } returns modelsFlow
        coEvery { observeThemePreference() } returns themeFlow
        coEvery { observeLanguagePreference() } returns languageFlow
    }

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SettingsViewModel(prefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enabledModels - initial value reflects MVP_SELECTION`() = runTest(dispatcher) {
        viewModel.enabledModels.test {
            assertEquals(WeatherModel.MVP_SELECTION.toSet(), awaitItem())
        }
    }

    @Test
    fun `enabledModels - émet le set du repository quand il change`() = runTest(dispatcher) {
        viewModel.enabledModels.test {
            assertEquals(WeatherModel.MVP_SELECTION.toSet(), awaitItem())

            modelsFlow.value = listOf(WeatherModel.GFS, WeatherModel.ECMWF)
            assertEquals(setOf(WeatherModel.GFS, WeatherModel.ECMWF), awaitItem())
        }
    }

    @Test
    fun `onModelToggled - activer un nouveau modèle l'ajoute au set`() = runTest(dispatcher) {
        // Maintient la souscription pour que enabledModels.value reflète
        // réellement modelsFlow.value (pas l'initialValue par défaut).
        backgroundScope.launch { viewModel.enabledModels.collect {} }
        modelsFlow.value = listOf(WeatherModel.GFS)
        viewModel.enabledModels.first { it == setOf(WeatherModel.GFS) }

        viewModel.onModelToggled(WeatherModel.ECMWF, enabled = true)

        coVerify {
            prefs.setEnabledModels(match {
                it.toSet() == setOf(WeatherModel.GFS, WeatherModel.ECMWF)
            })
        }
    }

    @Test
    fun `onModelToggled - désactiver un modèle le retire du set`() = runTest(dispatcher) {
        backgroundScope.launch { viewModel.enabledModels.collect {} }
        modelsFlow.value = listOf(WeatherModel.GFS, WeatherModel.ECMWF)
        viewModel.enabledModels.first { it == setOf(WeatherModel.GFS, WeatherModel.ECMWF) }

        viewModel.onModelToggled(WeatherModel.ECMWF, enabled = false)

        coVerify {
            prefs.setEnabledModels(match { it.toSet() == setOf(WeatherModel.GFS) })
        }
    }

    @Test
    fun `onModelToggled - désactiver le DERNIER modèle est ignoré (jamais set vide)`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.enabledModels.collect {} }
            modelsFlow.value = listOf(WeatherModel.GFS)
            viewModel.enabledModels.first { it == setOf(WeatherModel.GFS) }

            viewModel.onModelToggled(WeatherModel.GFS, enabled = false)

            // Contrainte métier : la VM refuse de persister un set vide pour
            // que l'app puisse toujours afficher quelque chose.
            coVerify(exactly = 0) { prefs.setEnabledModels(any()) }
        }

    @Test
    fun `onThemeSelected - délègue au repo`() = runTest(dispatcher) {
        viewModel.onThemeSelected(ThemePreference.DARK)
        coVerify { prefs.setThemePreference(ThemePreference.DARK) }
    }

    @Test
    fun `themePreference - émet la valeur du repo`() = runTest(dispatcher) {
        viewModel.themePreference.test {
            assertEquals(ThemePreference.SYSTEM, awaitItem())
            themeFlow.value = ThemePreference.LIGHT
            assertEquals(ThemePreference.LIGHT, awaitItem())
        }
    }

    @Test
    fun `onLanguageSelected - délègue au repo sans appeler AppCompat`() = runTest(dispatcher) {
        // La VM ne fait QUE persister. L'application effective de la locale
        // (AppCompatDelegate.setApplicationLocales + Activity.recreate) est
        // responsabilité du Composable — sinon race condition entre la coroutine
        // async de la VM et le recreate sync UI.
        viewModel.onLanguageSelected(LanguagePreference.ENGLISH)
        coVerify { prefs.setLanguagePreference(LanguagePreference.ENGLISH) }
    }

    @Test
    fun `languagePreference - émet la valeur du repo`() = runTest(dispatcher) {
        viewModel.languagePreference.test {
            assertEquals(LanguagePreference.SYSTEM, awaitItem())
            languageFlow.value = LanguagePreference.FRENCH
            assertEquals(LanguagePreference.FRENCH, awaitItem())
        }
    }

    @Test
    fun `onModelToggled - séquence de toggles utilise le set actuel à chaque fois`() =
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.enabledModels.collect {} }
            modelsFlow.value = listOf(WeatherModel.GFS)
            viewModel.enabledModels.first { it == setOf(WeatherModel.GFS) }

            viewModel.onModelToggled(WeatherModel.ECMWF, true)
            // Simule la persistance qui re-émet via le repo
            modelsFlow.value = listOf(WeatherModel.GFS, WeatherModel.ECMWF)
            viewModel.enabledModels.first { it == setOf(WeatherModel.GFS, WeatherModel.ECMWF) }

            viewModel.onModelToggled(WeatherModel.ICON_GLOBAL, true)
            modelsFlow.value = listOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_GLOBAL)
            viewModel.enabledModels.first {
                it == setOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_GLOBAL)
            }

            viewModel.onModelToggled(WeatherModel.GFS, false)

            // Chaque appel utilise le SET COURANT (pas un cache obsolète).
            coVerifyOrder {
                prefs.setEnabledModels(match {
                    it.toSet() == setOf(WeatherModel.GFS, WeatherModel.ECMWF)
                })
                prefs.setEnabledModels(match {
                    it.toSet() == setOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_GLOBAL)
                })
                prefs.setEnabledModels(match {
                    it.toSet() == setOf(WeatherModel.ECMWF, WeatherModel.ICON_GLOBAL)
                })
            }
        }
}
