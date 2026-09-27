package com.meteocompare.app.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.NotificationSettings
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.WeatherNotification
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ForecastEngineContextProvider
import com.meteocompare.app.domain.usecase.WeatherNotificationEvaluator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridge Hilt → [WeatherNotificationWorker], même choix que pour le suivi de
 * biais et les widgets : pas de dépendance `androidx.hilt:hilt-work`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WeatherNotificationEntryPoint {
    fun cityRepository(): CityRepository
    fun userPreferencesRepository(): UserPreferencesRepository
    fun forecastRepository(): ForecastRepository
    fun forecastEvolutionRepository(): ForecastEvolutionRepository
    fun forecastEngineContextProvider(): ForecastEngineContextProvider
    fun weatherNotificationEvaluator(): WeatherNotificationEvaluator
    fun clock(): Clock
}

/**
 * Exécute un cycle de notifications ([Kind.DAILY_SUMMARY] ou [Kind.ALERTS])
 * pour les villes favorites suivies.
 *
 * Les prévisions passent par le repository habituel : cache Room d'abord,
 * réseau seulement si le cache est plus ancien que l'intervalle de
 * rafraîchissement choisi (jamais en mode manuel s'il existe un cache). Un
 * refresh réseau enrichit au passage l'historique local du suivi d'évolution,
 * exactement comme une ouverture de l'application.
 */
internal class WeatherNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    enum class Kind { DAILY_SUMMARY, ALERTS }

    override suspend fun doWork(): Result {
        val kind = inputData.getString(WeatherNotificationScheduler.KIND_INPUT_KEY)
            ?.let { name -> Kind.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            WeatherNotificationEntryPoint::class.java
        )
        return try {
            runSuspendCatching { RUN_MUTEX.withLock { run(kind, entry) } }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "Notification cycle failed ($kind)", error)
                    // Le résumé quotidien est replanifié ci-dessous : un retry
                    // tardif ne serait plus un résumé « du matin ».
                    if (kind == Kind.ALERTS) Result.retry() else Result.success()
                }
        } finally {
            if (kind == Kind.DAILY_SUMMARY) {
                runSuspendCatching {
                    val settings = entry.userPreferencesRepository().observeNotificationSettings().first()
                    WeatherNotificationScheduler.scheduleNextDailySummary(applicationContext, settings)
                }.onFailure { error ->
                    // Réparé au prochain démarrage de l'application (ensureScheduled).
                    Log.w(LOG_TAG, "Unable to schedule next daily summary", error)
                }
            }
        }
    }

    private suspend fun run(kind: Kind, entry: WeatherNotificationEntryPoint): Result {
        val settings = entry.userPreferencesRepository().observeNotificationSettings().first()
        if (!kind.isEnabledIn(settings)) return Result.success()

        val notifier = WeatherNotifier(applicationContext)
        // Permission refusée ou notifications bloquées : inutile de consommer
        // réseau et batterie pour un résultat qui ne serait pas affiché.
        if (!notifier.canPost()) return Result.success()

        val cities = entry.cityRepository().observeFavorites().first()
            .filter { it.id in settings.cityIds }
        if (cities.isEmpty()) return Result.success()

        val prefs = entry.userPreferencesRepository()
        val models = prefs.observeEnabledModels().first()
        val maxCacheAgeMs = prefs.observeRefreshInterval().first().maxCacheAgeMs
        val engine = prefs.observeForecastEngine().first()
        val dedup = NotificationDedupStore(applicationContext)
        val clock = entry.clock()

        for (city in cities) {
            val notifications = withTimeoutOrNull(PER_CITY_TIMEOUT_MS) {
                runSuspendCatching {
                    val forecast = loadForecast(entry, city, models, maxCacheAgeMs)
                        ?: return@runSuspendCatching emptyList()
                    val now = clock.instant()
                    val engineContext = entry.forecastEngineContextProvider().build(forecast, engine, now)
                    val evaluator = entry.weatherNotificationEvaluator()
                    when (kind) {
                        Kind.DAILY_SUMMARY -> listOfNotNull(
                            evaluator.dailySummary(forecast, engineContext, now)
                        )
                        Kind.ALERTS -> listOfNotNull(
                            if (settings.divergenceAlertsEnabled) {
                                evaluator.modelDivergence(forecast, engineContext, now)
                            } else {
                                null
                            },
                            if (settings.forecastChangeAlertsEnabled) {
                                forecastChange(entry, forecast, now)
                            } else {
                                null
                            }
                        )
                    }
                }.onFailure { error ->
                    Log.w(LOG_TAG, "Notification evaluation failed for city=${city.id}", error)
                }.getOrNull()
            }.orEmpty()

            for (notification in notifications) {
                if (dedup.alreadyNotified(notification.dedupKey)) continue
                notifier.post(notification)
                dedup.markNotified(notification.dedupKey, clock.instant())
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "Posted ${notification.dedupKey}")
                }
            }
        }
        return Result.success()
    }

    /** Dernière prévision exploitable : cache frais, sinon réseau, sinon cache ancien. */
    private suspend fun loadForecast(
        entry: WeatherNotificationEntryPoint,
        city: City,
        models: List<WeatherModel>,
        maxCacheAgeMs: Long
    ): CityForecast? = entry.forecastRepository()
        .getCityForecastStream(city = city, models = models, maxCacheAgeMs = maxCacheAgeMs)
        .toList()
        .mapNotNull { result -> (result as? ApiResult.Success)?.data }
        .lastOrNull()

    private suspend fun forecastChange(
        entry: WeatherNotificationEntryPoint,
        forecast: CityForecast,
        now: Instant
    ): WeatherNotification.ForecastChange? {
        val today = now.localDateIn(forecast.city.timezone)
        val history = entry.forecastEvolutionRepository().getPreviousForecasts(
            city = forecast.city,
            models = forecast.availableModels,
            startDate = today,
            endDate = today.plusDays(WeatherNotificationEvaluator.CHANGE_MAX_DAYS_AHEAD.toLong()),
            referenceAt = forecast.fetchedAt ?: now
        )
        val samples = (history as? ApiResult.Success)?.data?.samples ?: return null
        return entry.weatherNotificationEvaluator().forecastChange(forecast, samples, now)
    }

    private fun Kind.isEnabledIn(settings: NotificationSettings): Boolean = when (this) {
        Kind.DAILY_SUMMARY -> settings.dailySummaryEnabled
        Kind.ALERTS -> settings.alertsEnabled
    }

    companion object {
        private const val LOG_TAG = "MeteoCompare/Notif"
        private const val PER_CITY_TIMEOUT_MS = 60_000L
        private val RUN_MUTEX = Mutex()
    }
}
