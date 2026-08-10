package com.meteocompare.app.ui.settings

import android.content.Context
import app.cash.turbine.test
import com.meteocompare.app.data.worker.BiasRefreshScheduler
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.widget.WidgetRefreshScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
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
 *
 * Note sur [WidgetRefreshScheduler] : c'est un `object` (singleton Kotlin) —
 * on utilise `mockkObject` de MockK pour intercepter les appels statiques.
 * Sinon, chaque appel `WidgetRefreshScheduler.schedule(...)` ou
 * `triggerImmediateRefresh(...)` tenterait d'invoquer WorkManager.getInstance()
 * qui crasherait dans un unit test sans ApplicationContext instrumenté. Le
 * mockObject renvoie des Unit no-op et permet de vérifier les invocations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val modelsFlow = MutableStateFlow(WeatherModel.MVP_SELECTION)
    private val themeFlow = MutableStateFlow(ThemePreference.SYSTEM)
    private val languageFlow = MutableStateFlow(LanguagePreference.SYSTEM)
    private val refreshIntervalFlow = MutableStateFlow(RefreshInterval.DEFAULT)

    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { observeEnabledModels() } returns modelsFlow
        coEvery { observeThemePreference() } returns themeFlow
        coEvery { observeLanguagePreference() } returns languageFlow
        coEvery { observeRefreshInterval() } returns refreshIntervalFlow
    }

    /**
     * Application context mocké. Utilisé UNIQUEMENT pour être passé à
     * WidgetRefreshScheduler.schedule() dans onRefreshIntervalSelected. La
     * VM ne s'en sert pas autrement. On peut donc un mock relaxé.
     */
    private val appContext: Context = mockk(relaxed = true)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Intercepte les appels au singleton WidgetRefreshScheduler pour
        // éviter tout accès WorkManager réel depuis les tests unitaires.
        // La logique de programmation elle-même sera couverte par ses propres
        // tests instrumentés séparés.
        //
        // NB : chaque méthode a maintenant DEUX overloads (Context et
        // WorkManager) pour la testabilité — le call-site production utilise
        // Context, l'internal(WorkManager) est utilisé par les tests
        // spécifiques du scheduler. Ici on stub UNIQUEMENT l'overload Context
        // car c'est celui que SettingsViewModel appelle. `any<Context>()`
        // rend le choix explicite pour le compilateur — sans ça il ne peut
        // pas résoudre l'overload et échoue en "Cannot infer type for T".
        mockkObject(WidgetRefreshScheduler)
        mockkObject(BiasRefreshScheduler)
        every { WidgetRefreshScheduler.schedule(any<Context>()) } returns Unit
        every { WidgetRefreshScheduler.triggerImmediateRefresh(any<Context>()) } returns Unit
        every { WidgetRefreshScheduler.cancel(any<Context>()) } returns Unit
        every { BiasRefreshScheduler.triggerManualRefresh(any<Context>()) } returns Unit

        viewModel = SettingsViewModel(appContext, prefs)
    }

    @After
    fun tearDown() {
        unmockkObject(BiasRefreshScheduler)
        unmockkObject(WidgetRefreshScheduler)
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
    fun `onBiasRefreshRequested - déclenche uniquement le worker manuel`() {
        viewModel.onBiasRefreshRequested()

        verify(exactly = 1) {
            BiasRefreshScheduler.triggerManualRefresh(appContext)
        }
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
        // La VM persiste dans l'unique source canonique. L'écran attend cette
        // écriture avant Activity.recreate(), donc pas de course avec
        // attachBaseContext().
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

    // ────────────────────────────────────────────────────────────────────
    //  RefreshInterval
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `refreshInterval - initial value est DEFAULT`() = runTest(dispatcher) {
        viewModel.refreshInterval.test {
            assertEquals(RefreshInterval.DEFAULT, awaitItem())
        }
    }

    @Test
    fun `refreshInterval - émet la valeur du repo quand elle change`() = runTest(dispatcher) {
        viewModel.refreshInterval.test {
            assertEquals(RefreshInterval.DEFAULT, awaitItem())
            refreshIntervalFlow.value = RefreshInterval.HOURS_3
            assertEquals(RefreshInterval.HOURS_3, awaitItem())
        }
    }

    @Test
    fun `onRefreshIntervalSelected - persiste ET force un tick immédiat`() = runTest(dispatcher) {
        viewModel.onRefreshIntervalSelected(RefreshInterval.HOURS_6)

        // Ordre : persistance AVANT trigger. Sinon le tick immédiat qu'on
        // vient de forcer lirait l'ancienne valeur pour le seuil de fraîcheur
        // cache — sur le run de test avec UnconfinedTestDispatcher ce n'est
        // pas critique mais on documente l'invariant.
        //
        // Note : depuis le découplage tick/fetch, on n'appelle plus
        // `schedule(context, interval)` — la cadence tick est fixe (15 min)
        // et la nouvelle valeur d'intervalle sera lue au prochain
        // loadWidgetData comme seuil `maxCacheAgeMs`. On force juste un
        // tick immédiat pour ne pas attendre 15 min.
        coVerifyOrder {
            prefs.setRefreshInterval(RefreshInterval.HOURS_6)
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
    }

    @Test
    fun `onRefreshIntervalSelected MANUAL - trigger aussi le tick immédiat`() =
        runTest(dispatcher) {
            // Cas frontière : MANUAL signifie "aucun fetch réseau automatique"
            // (le loadWidgetData va lire un maxCacheAgeMs = Long.MAX_VALUE et
            // ne fetchera plus). Mais on veut quand même refléter tout de suite
            // que ce choix est actif — d'où le tick immédiat qui va
            // recomposer le widget avec la nouvelle règle.
            viewModel.onRefreshIntervalSelected(RefreshInterval.MANUAL)

            coVerify {
                prefs.setRefreshInterval(RefreshInterval.MANUAL)
            }
            verify {
                WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
            }
        }

    @Test
    fun `onModelToggled - persiste ET force un tick immédiat du widget`() = runTest(dispatcher) {
        // Le widget lit `observeEnabledModels()` à chaque loadWidgetData.
        // Sans trigger explicite, l'utilisateur devrait attendre le prochain
        // tick périodique (jusqu'à 15 min) pour voir un nouveau modèle activé
        // se refléter sur l'écran d'accueil. C'est spécifiquement la
        // régression qu'on garde-fou ici.
        //
        // Souscription active pour que `enabledModels.value` reflète le
        // modelsFlow amont — sinon stateIn WhileSubscribed sert l'initialValue.
        val backgroundJob = backgroundScope.launch {
            viewModel.enabledModels.collect { /* actif tant qu'on est dans runTest */ }
        }
        modelsFlow.value = listOf(WeatherModel.GFS)  // état source connu

        viewModel.onModelToggled(WeatherModel.ECMWF, enabled = true)

        // On vérifie l'ORDRE (persist → trigger) sans dépendre du contenu
        // exact de la liste — l'ordre d'itération d'un Set + toList() est
        // spécifié pour LinkedHashSet mais on préfère ne pas tester ça ici.
        // Vérification du contenu :
        coVerify { prefs.setEnabledModels(match { it.containsAll(listOf(WeatherModel.GFS, WeatherModel.ECMWF)) && it.size == 2 }) }
        // Vérification de l'ordre setEnabled → triggerImmediateRefresh :
        coVerifyOrder {
            prefs.setEnabledModels(any())
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
        backgroundJob.cancel()
    }
}
