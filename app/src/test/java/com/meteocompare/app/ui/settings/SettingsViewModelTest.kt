package com.meteocompare.app.ui.settings


import android.content.Context
import androidx.work.Operation
import app.cash.turbine.test
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import com.meteocompare.app.R
import com.meteocompare.app.data.worker.BiasRefreshScheduler
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.LanguagePreference
import com.meteocompare.app.domain.model.NotificationSettings
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.ThemePreference
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.notification.WeatherNotificationScheduler
import com.meteocompare.app.ui.components.AppToastType
import com.meteocompare.app.widget.WidgetRefreshScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.IOException
import java.time.LocalTime
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
    private val forecastEngineFlow = MutableStateFlow(ForecastEngine.DEFAULT)

    private val notificationFlow = MutableStateFlow(NotificationSettings())
    private val paris = City(id = "paris", name = "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val lyon = City(id = "lyon", name = "Lyon", country = "France", latitude = 45.76, longitude = 4.84)
    private val favoritesFlow = MutableStateFlow(listOf(paris, lyon))

    private val prefs: UserPreferencesRepository = mockk(relaxed = true) {
        coEvery { observeEnabledModels() } returns modelsFlow
        every { observeUnitSystem() } returns kotlinx.coroutines.flow.flowOf(UnitSystem.METRIC)
        coEvery { observeThemePreference() } returns themeFlow
        coEvery { observeLanguagePreference() } returns languageFlow
        coEvery { observeRefreshInterval() } returns refreshIntervalFlow
        every { observeForecastEngine() } returns forecastEngineFlow
        every { observeNotificationSettings() } returns notificationFlow
        // Reproduit la mise à jour atomique DataStore sur le flow en mémoire.
        coEvery { updateNotificationSettings(any()) } answers {
            val transform = firstArg<(NotificationSettings) -> NotificationSettings>()
            transform(notificationFlow.value).also { notificationFlow.value = it }
        }
    }

    private val cityRepository: CityRepository = mockk(relaxed = true) {
        every { observeFavorites() } returns favoritesFlow
    }

    /**
     * Application context mocké. Utilisé UNIQUEMENT pour être passé à
     * WidgetRefreshScheduler.triggerImmediateRefresh() dans les callbacks. La
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
        every { BiasRefreshScheduler.triggerManualRefresh(any<Context>()) } returns enqueueOperation()
        mockkObject(WeatherNotificationScheduler)
        every { WeatherNotificationScheduler.reschedule(any(), any()) } returns Unit

        viewModel = SettingsViewModel(appContext, prefs, cityRepository)
    }

    @After
    fun tearDown() {
        unmockkObject(WeatherNotificationScheduler)
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
    fun `onModelToggled - preference read failure emits a terminal error toast`() =
        runTest(dispatcher) {
            every { prefs.observeEnabledModels() } throws
                IllegalStateException("datastore unavailable")

            viewModel.feedback.test {
                viewModel.onModelToggled(WeatherModel.ECMWF, enabled = true)

                val event = awaitItem()
                assertEquals(AppToastType.ERROR, event.type)
                assertEquals(R.string.toast_settings_save_error, event.messageRes)
            }
            coVerify(exactly = 0) { prefs.setEnabledModels(any()) }
        }

    @Test
    fun `onBiasRefreshRequested - déclenche uniquement le worker manuel`() = runTest(dispatcher) {
        viewModel.feedback.test {
            viewModel.onBiasRefreshRequested()
            val event = awaitItem()
            assertEquals(AppToastType.INFO, event.type)
            assertEquals(R.string.settings_bias_refresh_queued, event.messageRes)
        }
        verify(exactly = 1) { BiasRefreshScheduler.triggerManualRefresh(appContext) }
    }

    @Test
    fun `onBiasRefreshRequested - un echec asynchrone affiche une erreur`() = runTest(dispatcher) {
        every { BiasRefreshScheduler.triggerManualRefresh(any<Context>()) } returns
            enqueueOperation(IllegalStateException("enqueue failed asynchronously"))
        viewModel.feedback.test {
            viewModel.onBiasRefreshRequested()
            val event = awaitItem()
            assertEquals(AppToastType.ERROR, event.type)
            assertEquals(R.string.toast_action_error, event.messageRes)
            expectNoEvents()
        }
    }

    private fun enqueueOperation(error: Throwable? = null): Operation {
        val future = mockk<ListenableFuture<Operation.State.SUCCESS>> {
            every { isDone } returns true
            every { get() } answers {
                if (error != null) throw ExecutionException(error)
                Operation.SUCCESS
            }
        }
        return mockk { every { result } returns future }
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
    fun `echec du trigger widget ne transforme pas un reglage persiste en echec`() =
        runTest(dispatcher) {
            every {
                WidgetRefreshScheduler.triggerImmediateRefresh(any<Context>())
            } throws IllegalStateException("WorkManager indisponible")

            viewModel.feedback.test {
                viewModel.onRefreshIntervalSelected(RefreshInterval.HOURS_3)

                val event = awaitItem()
                assertEquals(R.string.toast_refresh_interval_updated, event.messageRes)
                assertEquals(AppToastType.SUCCESS, event.type)
            }
            coVerify(exactly = 1) { prefs.setRefreshInterval(RefreshInterval.HOURS_3) }
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
        coVerify {
            prefs.setEnabledModels(
                match {
                    it.containsAll(listOf(WeatherModel.GFS, WeatherModel.ECMWF)) && it.size == 2
                }
            )
        }
        // Vérification de l'ordre setEnabled → triggerImmediateRefresh :
        coVerifyOrder {
            prefs.setEnabledModels(any())
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
        backgroundJob.cancel()
    }
    @Test
    fun `forecastEngine - suit le repository et le changement rafraichit le widget`() = runTest(dispatcher) {
        viewModel.forecastEngine.test {
            assertEquals(ForecastEngine.DEFAULT, awaitItem())
            forecastEngineFlow.value = ForecastEngine.ADAPTIVE
            assertEquals(ForecastEngine.ADAPTIVE, awaitItem())
        }

        viewModel.onForecastEngineSelected(ForecastEngine.CALIBRATION)

        coVerifyOrder {
            prefs.setForecastEngine(ForecastEngine.CALIBRATION)
            WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
        }
    }


    // ─────────────────────────── Notifications ───────────────────────────

    @Test
    fun `notifications - premiere activation suit la premiere ville favorite et replanifie`() =
        runTest(dispatcher) {
            viewModel.onDailySummaryToggled(true)

            val expected = NotificationSettings(dailySummaryEnabled = true, cityIds = setOf(paris.id))
            assertEquals(expected, notificationFlow.value)
            verify(exactly = 1) { WeatherNotificationScheduler.reschedule(appContext, expected) }
        }

    @Test
    fun `notifications - une ville decochee n'est pas recochee automatiquement`() = runTest(dispatcher) {
        notificationFlow.value = NotificationSettings(
            divergenceAlertsEnabled = true,
            cityIds = setOf(paris.id)
        )

        viewModel.onNotificationCityToggled(paris.id, followed = false)

        assertEquals(emptySet<String>(), notificationFlow.value.cityIds)
    }

    @Test
    fun `notifications - heure du resume persistee puis replanifiee`() = runTest(dispatcher) {
        notificationFlow.value = NotificationSettings(dailySummaryEnabled = true, cityIds = setOf(lyon.id))

        viewModel.onDailySummaryTimeSelected(LocalTime.of(6, 30))

        assertEquals(LocalTime.of(6, 30), notificationFlow.value.dailySummaryTime)
        verify(exactly = 1) {
            WeatherNotificationScheduler.reschedule(appContext, notificationFlow.value)
        }
    }

    @Test
    fun `notifications - echec d'ecriture signale sans replanifier`() = runTest(dispatcher) {
        coEvery { prefs.updateNotificationSettings(any()) } throws IOException("disk full")

        viewModel.feedback.test {
            viewModel.onForecastChangeAlertsToggled(true)
            assertEquals(AppToastType.ERROR, awaitItem().type)
        }
        verify(exactly = 0) { WeatherNotificationScheduler.reschedule(any(), any()) }
    }
    @Test
    fun `units are persisted before widget refresh and exposed reactively`() = runTest(dispatcher) {
        val units = MutableStateFlow(UnitSystem.METRIC)
        every { prefs.observeUnitSystem() } returns units
        coEvery { prefs.setUnitSystem(any()) } answers { units.value = firstArg() }
        val vm = SettingsViewModel(appContext, prefs, cityRepository)
        vm.unitSystem.test {
            assertEquals(UnitSystem.METRIC, awaitItem())
            vm.onUnitSystemSelected(UnitSystem.IMPERIAL)
            assertEquals(UnitSystem.IMPERIAL, awaitItem())
            coVerifyOrder {
                prefs.setUnitSystem(UnitSystem.IMPERIAL)
                WidgetRefreshScheduler.triggerImmediateRefresh(appContext)
            }
        }
    }

    @Test
    fun `failed unit save reports an error without refreshing widgets`() = runTest(dispatcher) {
        coEvery { prefs.setUnitSystem(any()) } throws IOException("disk unavailable")
        viewModel.feedback.test {
            viewModel.onUnitSystemSelected(UnitSystem.IMPERIAL)
            assertEquals(AppToastType.ERROR, awaitItem().type)
        }
        verify(exactly = 0) { WidgetRefreshScheduler.triggerImmediateRefresh(appContext) }
    }

}
