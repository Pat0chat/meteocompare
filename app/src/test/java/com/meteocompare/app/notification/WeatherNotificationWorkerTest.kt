package com.meteocompare.app.notification

import android.app.NotificationManager
import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.NotificationSettings
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.UnitSystem
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.WeatherNotification
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionHistoryData
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.domain.usecase.WeatherNotificationEvaluator
import dagger.hilt.android.EntryPointAccessors
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherNotificationWorkerTest {
    private val now = Instant.parse("2026-10-02T06:00:00Z")
    private val city = City("paris", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val forecast = CityForecast(city, emptyMap(), fetchedAt = now)
    private val settings = MutableStateFlow(NotificationSettings(
        dailySummaryEnabled = true, divergenceAlertsEnabled = true,
        forecastChangeAlertsEnabled = true, cityIds = setOf(city.id)
    ))
    private val favorites = MutableStateFlow(listOf(city))
    private val prefs = mockk<UserPreferencesRepository>()
    private val cities = mockk<CityRepository>()
    private val forecasts = mockk<ForecastRepository>()
    private val history = mockk<ForecastEvolutionRepository>()
    private val engines = mockk<ForecastEngineContextProvider>()
    private val evaluator = mockk<WeatherNotificationEvaluator>()
    private val engineContext = mockk<ForecastEngineContext>()
    private val entry = mockk<WeatherNotificationEntryPoint>()
    private val context = mockk<Context>(relaxed = true)
    private val summary = WeatherNotification.DailySummary(city, LocalDate.of(2026, 10, 2), true,
        null, 10.0, 20.0, 0, 0.0, 15.0, 85)
    private val divergence = WeatherNotification.ModelDivergence(city, LocalDate.of(2026, 10, 3), false, 35)

    @Before fun setUp() {
        val manager = mockk<NotificationManager>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        mockkStatic(EntryPointAccessors::class)
        mockkObject(WeatherNotificationScheduler)
        mockkConstructor(WeatherNotifier::class, NotificationDedupStore::class)
        every { EntryPointAccessors.fromApplication(context, WeatherNotificationEntryPoint::class.java) } returns entry
        every { WeatherNotificationScheduler.scheduleNextDailySummary(any(), any()) } just Runs
        every { anyConstructed<WeatherNotifier>().canPost() } returns true
        every { anyConstructed<WeatherNotifier>().post(any()) } returns WeatherNotifier.PostResult.POSTED
        every { anyConstructed<NotificationDedupStore>().alreadyNotified(any()) } returns false
        every { anyConstructed<NotificationDedupStore>().markNotified(any(), any()) } just Runs
        every { entry.userPreferencesRepository() } returns prefs
        every { entry.cityRepository() } returns cities
        every { entry.forecastRepository() } returns forecasts
        every { entry.forecastEvolutionRepository() } returns history
        every { entry.forecastEngineContextProvider() } returns engines
        every { entry.weatherNotificationEvaluator() } returns evaluator
        every { entry.clock() } returns Clock.fixed(now, ZoneOffset.UTC)
        every { prefs.observeNotificationSettings() } returns settings
        every { prefs.observeUnitSystem() } returns flowOf(UnitSystem.METRIC)
        every { prefs.observeEnabledModels() } returns flowOf(listOf(WeatherModel.GFS))
        every { prefs.observeRefreshInterval() } returns flowOf(RefreshInterval.DEFAULT)
        every { prefs.observeForecastEngine() } returns flowOf(ForecastEngine.DEFAULT)
        every { cities.observeFavorites() } returns favorites
        every { forecasts.getCityForecastStream(any(), any(), any(), any(), any()) } returns flowOf(ApiResult.Success(forecast))
        coEvery { engines.build(any(), any(), any()) } returns engineContext
        every { evaluator.dailySummary(any(), any(), any()) } returns summary
        every { evaluator.modelDivergence(any(), any(), any()) } returns divergence
        coEvery { history.getPreviousForecasts(any(), any(), any(), any(), any()) } returns
            ApiResult.Success(ForecastEvolutionHistoryData(emptyList(), now))
        every { evaluator.forecastChange(any(), any(), any()) } returns null
    }

    @After fun tearDown() {
        unmockkConstructor(WeatherNotifier::class, NotificationDedupStore::class)
        unmockkStatic(EntryPointAccessors::class)
        unmockkObject(WeatherNotificationScheduler)
    }

    private fun worker(kind: WeatherNotificationWorker.Kind): WeatherNotificationWorker {
        val params = mockk<WorkerParameters>(relaxed = true)
        every { params.id } returns UUID.randomUUID()
        every { params.workerContext } returns EmptyCoroutineContext
        every { params.inputData } returns workDataOf(WeatherNotificationScheduler.KIND_INPUT_KEY to kind.name)
        return WeatherNotificationWorker(context, params)
    }

    @Test fun `resume publie depuis le cache lorsque le reseau ne termine pas`() = runTest {
        every { forecasts.getCityForecastStream(any(), any(), any(), any(), any()) } returns flow {
            emit(ApiResult.Success(forecast))
            awaitCancellation()
        }
        assertEquals(ListenableWorker.Result.success(), worker(WeatherNotificationWorker.Kind.DAILY_SUMMARY).doWork())
        verify(exactly = 1) { anyConstructed<WeatherNotifier>().post(summary) }
        verify(exactly = 1) { anyConstructed<NotificationDedupStore>().markNotified(summary.dedupKey, now) }
        assertEquals(40_000L, testScheduler.currentTime)
    }

    @Test fun `historique lent ne supprime pas la divergence deja calculee`() = runTest {
        coEvery { history.getPreviousForecasts(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        worker(WeatherNotificationWorker.Kind.ALERTS).doWork()
        verify(exactly = 1) { anyConstructed<WeatherNotifier>().post(divergence) }
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test fun `historique en erreur ne supprime pas la divergence`() = runTest {
        coEvery { history.getPreviousForecasts(any(), any(), any(), any(), any()) } throws IOException("history unavailable")
        worker(WeatherNotificationWorker.Kind.ALERTS).doWork()
        verify(exactly = 1) { anyConstructed<WeatherNotifier>().post(divergence) }
    }

    @Test fun `desactivation du type pendant le chargement empeche publication et deduplication`() = runTest {
        every { forecasts.getCityForecastStream(any(), any(), any(), any(), any()) } returns flow {
            emit(ApiResult.Success(forecast))
            delay(100)
            settings.value = settings.value.copy(divergenceAlertsEnabled = false)
        }
        worker(WeatherNotificationWorker.Kind.ALERTS).doWork()
        verify(exactly = 0) { anyConstructed<WeatherNotifier>().post(any()) }
        verify(exactly = 0) { anyConstructed<NotificationDedupStore>().markNotified(any(), any()) }
    }

    @Test fun `les preferences sont relues apres la derniere lecture des favoris`() = runTest {
        var reads = 0
        every { cities.observeFavorites() } returns flow {
            reads++
            if (reads > 1) {
                delay(100)
                settings.value = settings.value.copy(divergenceAlertsEnabled = false)
            }
            emit(favorites.value)
        }
        worker(WeatherNotificationWorker.Kind.ALERTS).doWork()
        verify(exactly = 0) { anyConstructed<WeatherNotifier>().post(any()) }
    }

    @Test fun `retrait de la ville pendant le chargement empeche la publication`() = runTest {
        every { forecasts.getCityForecastStream(any(), any(), any(), any(), any()) } returns flow {
            delay(100)
            settings.value = settings.value.copy(cityIds = setOf("lyon"))
            emit(ApiResult.Success(forecast))
        }
        worker(WeatherNotificationWorker.Kind.ALERTS).doWork()
        verify(exactly = 0) { anyConstructed<WeatherNotifier>().post(any()) }
    }

    @Test fun `suppression du favori pendant le chargement empeche la publication`() = runTest {
        every { forecasts.getCityForecastStream(any(), any(), any(), any(), any()) } returns flow {
            delay(100)
            favorites.value = emptyList()
            emit(ApiResult.Success(forecast))
        }
        worker(WeatherNotificationWorker.Kind.ALERTS).doWork()
        verify(exactly = 0) { anyConstructed<WeatherNotifier>().post(any()) }
    }

    @Test fun `annulation du calcul ne publie pas et le lendemain est deja rearme`() = runTest {
        every { forecasts.getCityForecastStream(any(), any(), any(), any(), any()) } returns flow {
            emit(ApiResult.Success(forecast))
            awaitCancellation()
        }
        val task = async { worker(WeatherNotificationWorker.Kind.DAILY_SUMMARY).doWork() }
        runCurrent()
        verify(exactly = 1) { WeatherNotificationScheduler.scheduleNextDailySummary(context, any()) }
        task.cancelAndJoin()
        verify(exactly = 0) { anyConstructed<WeatherNotifier>().post(any()) }
    }

    @Test fun `canal bloque ne consomme pas la deduplication`() = runTest {
        every { anyConstructed<WeatherNotifier>().post(any()) } returns WeatherNotifier.PostResult.BLOCKED_CHANNEL
        worker(WeatherNotificationWorker.Kind.DAILY_SUMMARY).doWork()
        verify(exactly = 0) { anyConstructed<NotificationDedupStore>().markNotified(any(), any()) }
    }
}
