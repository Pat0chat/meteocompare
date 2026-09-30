package com.meteocompare.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.NotificationSettings
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.domain.usecase.WeatherNotificationEvaluator
import com.meteocompare.app.testutil.FakeCityRepository
import com.meteocompare.app.testutil.FakeForecastRepository
import com.meteocompare.app.testutil.FakeUserPreferencesRepository
import com.meteocompare.app.testutil.TestFixtures
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test bout en bout du pipeline réel :
 * WorkManager -> WeatherNotificationWorker -> permission Android -> canal ->
 * NotificationManager -> NotificationDedupStore.
 *
 * Les repositories sont les fakes Hilt instrumentés du projet afin d'éviter le
 * réseau, mais le WorkManager, la permission, les canaux et NotificationManager
 * sont les composants Android réels.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WeatherNotificationPipelineTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var cities: FakeCityRepository
    @Inject lateinit var forecasts: FakeForecastRepository
    @Inject lateinit var preferences: FakeUserPreferencesRepository
    @Inject lateinit var evaluator: WeatherNotificationEvaluator
    @Inject lateinit var engineContextProvider: ForecastEngineContextProvider
    @Inject lateinit var clock: Clock

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val platformManager: NotificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }
    private val dedup: NotificationDedupStore by lazy { NotificationDedupStore(context) }

    @Before
    fun setUp() {
        runBlocking {
            hiltRule.inject()
            grantPostNotificationsPermission()
            clearNotificationState()
            cities.reset()
            forecasts.reset()
            forecasts.finiteStreams = true
            preferences.reset()
            cities.setFavorites(listOf(TestFixtures.paris))
            forecasts.setForecast(TestFixtures.paris, TestFixtures.forecast(TestFixtures.paris))
            preferences.updateNotificationSettings {
                NotificationSettings(
                    dailySummaryEnabled = true,
                    cityIds = setOf(TestFixtures.paris.id)
                )
            }
        }
    }

    @After
    fun tearDown() {
        clearNotificationState()
    }

    @Test
    fun blockedChannelDoesNotConsumeDeduplication() = runBlocking {
        val forecast = divergentTomorrowForecast()
        val engineContext = engineContextProvider.build(
            forecast = forecast,
            engine = ForecastEngine.DEFAULT,
            now = clock.instant()
        )
        val expected = requireNotNull(evaluator.modelDivergence(forecast, engineContext, clock.instant())) {
            "la fixture doit produire une divergence avant de tester le canal"
        }

        // Le canal divergence est volontairement bloqué. On utilise un canal
        // distinct du résumé quotidien afin que le test reste répétable :
        // Android restaure les réglages d'un canal supprimé/recréé.
        platformManager.createNotificationChannel(
            NotificationChannel(
                WeatherNotifier.CHANNEL_DIVERGENCE,
                "blocked-test",
                NotificationManager.IMPORTANCE_NONE
            )
        )
        assertEquals(
            NotificationManager.IMPORTANCE_NONE,
            platformManager.getNotificationChannel(WeatherNotifier.CHANNEL_DIVERGENCE).importance
        )
        preferences.updateNotificationSettings {
            NotificationSettings(
                divergenceAlertsEnabled = true,
                cityIds = setOf(TestFixtures.paris.id)
            )
        }
        forecasts.setForecast(TestFixtures.paris, forecast)

        runWorkerThroughWorkManager(WeatherNotificationWorker.Kind.ALERTS)

        assertEquals(0, activeWeatherNotifications().size)
        assertFalse(
            "un canal bloqué ne doit pas consommer la déduplication",
            dedup.alreadyNotified(expected.dedupKey)
        )
    }

    @Test
    fun enablingDivergenceTriggersImmediateCheck() = runBlocking {
        val forecast = divergentTomorrowForecast()
        val engineContext = engineContextProvider.build(
            forecast = forecast,
            engine = ForecastEngine.DEFAULT,
            now = clock.instant()
        )
        val expected = requireNotNull(evaluator.modelDivergence(forecast, engineContext, clock.instant()))
        forecasts.setForecast(TestFixtures.paris, forecast)
        val settings = preferences.updateNotificationSettings {
            NotificationSettings(
                divergenceAlertsEnabled = true,
                cityIds = setOf(TestFixtures.paris.id)
            )
        }
        val previousWorkIds = workManager
            .getWorkInfosForUniqueWork(WeatherNotificationScheduler.ALERTS_IMMEDIATE_WORK_NAME)
            .get(5, TimeUnit.SECONDS)
            .map { it.id }
            .toSet()

        WeatherNotificationScheduler.reschedule(context, settings)
        awaitNewUniqueWorkFinished(
            WeatherNotificationScheduler.ALERTS_IMMEDIATE_WORK_NAME,
            previousWorkIds
        )

        val delivered = activeWeatherNotifications()
        assertEquals(1, delivered.size)
        assertEquals(WeatherNotifier.CHANNEL_DIVERGENCE, delivered.single().notification.channelId)
        assertTrue(dedup.alreadyNotified(expected.dedupKey))
    }

    @Test
    fun dailyAlarmTriggersWorkManagerNotificationAndDeduplication() {
        assertTrue("la permission système doit autoriser les notifications", WeatherNotifier(context).canPost())
        val expectedKey = expectedDailyDedupKey()

        val previousWorkIds = workManager
            .getWorkInfosForUniqueWork(WeatherNotificationScheduler.DAILY_SUMMARY_WORK_NAME)
            .get(5, TimeUnit.SECONDS)
            .map { it.id }
            .toSet()

        WeatherNotificationAlarmReceiver().onReceive(
            context,
            Intent(context, WeatherNotificationAlarmReceiver::class.java)
                .setAction(WeatherNotificationScheduler.DAILY_SUMMARY_ALARM_ACTION)
        )
        awaitNewUniqueWorkFinished(
            WeatherNotificationScheduler.DAILY_SUMMARY_WORK_NAME,
            previousWorkIds
        )

        val delivered = activeWeatherNotifications()
        assertEquals(1, delivered.size)
        assertTrue(dedup.alreadyNotified(expectedKey))
    }

    @Test
    fun permissionChannelNotificationAndDeduplicationWorkEndToEnd() {
        assertTrue("la permission système doit autoriser les notifications", WeatherNotifier(context).canPost())
        val expectedKey = expectedDailyDedupKey()

        runWorkerThroughWorkManager(WeatherNotificationWorker.Kind.DAILY_SUMMARY)

        val delivered = activeWeatherNotifications()
        assertEquals(1, delivered.size)
        assertEquals(
            WeatherNotifier.notificationId(expectedDailySummary()),
            delivered.single().id
        )
        val posted = delivered.single().notification
        assertEquals(WeatherNotifier.CHANNEL_DAILY_SUMMARY, posted.channelId)
        assertEquals(Notification.CATEGORY_STATUS, posted.category)
        assertNotEquals("la notification doit porter un accent MeteoCompare", 0, posted.color)
        assertTrue("la livraison doit être inscrite dans le ledger", dedup.alreadyNotified(expectedKey))

        // Même événement : le ledger empêche une seconde publication.
        platformManager.cancel(delivered.single().id)
        runWorkerThroughWorkManager(WeatherNotificationWorker.Kind.DAILY_SUMMARY)
        assertEquals(0, activeWeatherNotifications().size)
        assertTrue(dedup.alreadyNotified(expectedKey))
    }

    private fun runWorkerThroughWorkManager(kind: WeatherNotificationWorker.Kind) {
        val request = OneTimeWorkRequestBuilder<WeatherNotificationWorker>()
            .setInputData(
                Data.Builder()
                    .putString(WeatherNotificationScheduler.KIND_INPUT_KEY, kind.name)
                    .build()
            )
            .build()
        workManager.enqueue(request)
        awaitFinished(request.id)
    }

    private fun awaitFinished(id: UUID) {
        val deadline = SystemClock.elapsedRealtime() + WORK_TIMEOUT_MS
        var info: WorkInfo? = null
        do {
            info = workManager.getWorkInfoById(id).get(5, TimeUnit.SECONDS)
            if (info?.state?.isFinished == true) break
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)

        val finalInfo = requireNotNull(info) { "WorkManager ne connaît pas le work $id" }
        assertTrue("WorkManager n'a pas terminé: ${finalInfo.state}", finalInfo.state.isFinished)
        assertEquals(WorkInfo.State.SUCCEEDED, finalInfo.state)
    }

    private fun awaitNewUniqueWorkFinished(name: String, previousIds: Set<UUID>) {
        val deadline = SystemClock.elapsedRealtime() + WORK_TIMEOUT_MS
        var infos = emptyList<WorkInfo>()
        do {
            infos = workManager.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)
            val current = infos.firstOrNull { it.id !in previousIds }
            if (current?.state == WorkInfo.State.SUCCEEDED) return
            if (current?.state?.isFinished == true) {
                error("Le nouveau work unique $name a terminé en ${current.state}")
            }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)

        error("Le nouveau work unique $name n'a pas terminé: ${infos.map { it.id to it.state }}")
    }

    private fun divergentTomorrowForecast(): CityForecast {
        val models = listOf(
            WeatherModel.GFS,
            WeatherModel.ECMWF,
            WeatherModel.ICON_GLOBAL,
            WeatherModel.UKMO_GLOBAL
        )
        val tomorrow = TestFixtures.today.plusDays(1)
        val scattered = listOf(
            listOf(22.0, 8.0),
            listOf(22.0, 30.0),
            listOf(22.0, 16.0),
            listOf(22.0, 24.0)
        )
        return CityForecast(
            city = TestFixtures.paris,
            seriesByModel = models.withIndex().associate { (index, model) ->
                model to ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList()),
                    daily = DailyForecast(
                        dates = listOf(TestFixtures.today, tomorrow),
                        tempMax = scattered[index],
                        tempMin = listOf(12.0, scattered[index][1] - 8.0),
                        precipitationSum = listOf(0.0, if (index % 2 == 0) 0.0 else 30.0),
                        windSpeedMax = listOf(10.0, 10.0 + index * 20.0),
                        weatherCode = listOf(0, listOf(0, 95, 3, 63)[index])
                    )
                )
            },
            fetchedAt = TestFixtures.now
        )
    }

    private fun expectedDailySummary() = com.meteocompare.app.domain.model.WeatherNotification.DailySummary(
        city = TestFixtures.paris,
        date = expectedDailyDate(),
        isToday = expectedDailyDate() == Instant.now().atZone(ZoneId.of(TestFixtures.paris.timezone!!)).toLocalDate(),
        condition = null,
        tempMin = null,
        tempMax = null,
        precipitationProbabilityPercent = null,
        precipitationAmountMm = null,
        convergencePercent = null
    )

    private fun expectedDailyDedupKey(): String = "daily|${TestFixtures.paris.id}|${expectedDailyDate()}"

    private fun expectedDailyDate(): java.time.LocalDate {
        val local = Instant.now().atZone(ZoneId.of(TestFixtures.paris.timezone!!))
        return if (local.toLocalTime() < WeatherNotificationEvaluator.SUMMARY_TOMORROW_FROM) {
            local.toLocalDate()
        } else {
            local.toLocalDate().plusDays(1)
        }
    }

    private fun activeWeatherNotifications() = platformManager.activeNotifications.filter { status ->
        status.notification.channelId in setOf(
            WeatherNotifier.CHANNEL_DAILY_SUMMARY,
            WeatherNotifier.CHANNEL_DIVERGENCE,
            WeatherNotifier.CHANNEL_FORECAST_CHANGE
        )
    }

    private fun grantPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private fun clearNotificationState() {
        workManager.cancelUniqueWork(WeatherNotificationScheduler.DAILY_SUMMARY_WORK_NAME)
        workManager.cancelUniqueWork(WeatherNotificationScheduler.ALERTS_WORK_NAME)
        workManager.cancelUniqueWork(WeatherNotificationScheduler.ALERTS_IMMEDIATE_WORK_NAME)
        WeatherNotificationScheduler.reschedule(context, NotificationSettings())
        platformManager.cancelAll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            listOf(
                WeatherNotifier.CHANNEL_DAILY_SUMMARY,
                WeatherNotifier.CHANNEL_DIVERGENCE,
                WeatherNotifier.CHANNEL_FORECAST_CHANGE
            ).forEach(platformManager::deleteNotificationChannel)
        }
        dedup.clear()
    }

    private companion object {
        const val WORK_TIMEOUT_MS = 20_000L
        const val POLL_MS = 100L
    }
}
