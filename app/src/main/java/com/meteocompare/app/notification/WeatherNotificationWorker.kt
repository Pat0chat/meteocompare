package com.meteocompare.app.notification

import com.meteocompare.app.core.units.WeatherUnits

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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
        // Réarme avant le réseau et avant l'attente du mutex : une annulation
        // du calcul ne doit pas supprimer le résumé du lendemain.
        if (kind == Kind.DAILY_SUMMARY) {
            runSuspendCatching {
                val settings = entry.userPreferencesRepository().observeNotificationSettings().first()
                WeatherNotificationScheduler.scheduleNextDailySummary(applicationContext, settings)
            }.onFailure { error ->
                Log.w(LOG_TAG, "Unable to schedule next daily summary", error)
            }
        }
        return runSuspendCatching { RUN_MUTEX.withLock { run(kind, entry) } }
            .getOrElse { error ->
                Log.w(LOG_TAG, "Notification cycle failed ($kind)", error)
                // Un retry tardif ne serait plus un résumé « du matin ».
                if (kind == Kind.ALERTS) Result.retry() else Result.success()
            }
    }

    private suspend fun run(kind: Kind, entry: WeatherNotificationEntryPoint): Result {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "Starting notification cycle: $kind")
        val settings = entry.userPreferencesRepository().observeNotificationSettings().first()
        if (!kind.isEnabledIn(settings)) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "Skipping $kind: disabled in settings")
            return Result.success()
        }

        val notifier = WeatherNotifier(applicationContext,
            WeatherUnits(entry.userPreferencesRepository().observeUnitSystem().first()))
        // Permission refusée ou notifications bloquées : inutile de consommer
        // réseau et batterie pour un résultat qui ne serait pas affiché.
        if (!notifier.canPost()) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "Skipping $kind: notifications unavailable")
            return Result.success()
        }

        val cities = entry.cityRepository().observeFavorites().first()
            .filter { it.id in settings.cityIds }
        if (cities.isEmpty()) {
            if (BuildConfig.DEBUG) Log.d(LOG_TAG, "Skipping $kind: no followed favorite city")
            return Result.success()
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Evaluating $kind for ${cities.size} followed city/cities")
        }

        val prefs = entry.userPreferencesRepository()
        val models = prefs.observeEnabledModels().first()
        val maxCacheAgeMs = prefs.observeRefreshInterval().first().maxCacheAgeMs
        val engine = prefs.observeForecastEngine().first()
        val dedup = NotificationDedupStore(applicationContext)
        val clock = entry.clock()

        for (city in cities) {
            val forecast = latestNotificationForecast(
                entry.forecastRepository().getCityForecastStream(
                    city = city, models = models, maxCacheAgeMs = maxCacheAgeMs
                )
            ) ?: continue
            val now = clock.instant()
            val evaluator = entry.weatherNotificationEvaluator()
            // Les signaux sont indépendants : un historique lent ne doit pas
            // faire perdre une divergence déjà calculée. Le chargement garde
            // 40 s, puis chaque signal suspendu dispose de 10 s (60 s au total).
            val notifications = buildList {
                if (kind == Kind.DAILY_SUMMARY || settings.divergenceAlertsEnabled) {
                    evaluateSignal(city) {
                        val engineContext = entry.forecastEngineContextProvider().build(forecast, engine, now)
                        if (kind == Kind.DAILY_SUMMARY) evaluator.dailySummary(forecast, engineContext, now)
                        else evaluator.modelDivergence(forecast, engineContext, now)
                    }?.let(::add)
                }
                if (kind == Kind.ALERTS && settings.forecastChangeAlertsEnabled) {
                    evaluateSignal(city) { forecastChange(entry, forecast, now) }?.let(::add)
                }
            }

            for (notification in notifications) {
                // UPDATE n'interrompt pas un worker périodique déjà lancé.
                // Relire les choix après les I/O, juste avant tout effet visible.
                if (entry.cityRepository().observeFavorites().first().none { it.id == city.id }) continue
                val currentSettings = prefs.observeNotificationSettings().first()
                if (!notification.isEnabledIn(currentSettings)) continue
                currentCoroutineContext().ensureActive()
                if (isStopped) return Result.success()
                if (dedup.alreadyNotified(notification.dedupKey)) continue
                val postResult = notifier.post(notification)
                if (postResult == WeatherNotifier.PostResult.POSTED) {
                    dedup.markNotified(notification.dedupKey, clock.instant())
                    if (BuildConfig.DEBUG) {
                        Log.d(LOG_TAG, "Posted ${notification.dedupKey}")
                    }
                } else if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "Skipped ${notification.dedupKey}: $postResult")
                }
            }
        }
        return Result.success()
    }

    private suspend fun <T> evaluateSignal(city: City, block: suspend () -> T): T? =
        withTimeoutOrNull(SIGNAL_TIMEOUT_MS) {
            runSuspendCatching { block() }
                .onFailure { Log.w(LOG_TAG, "Notification evaluation failed for city=${city.id}", it) }
                .getOrNull()
        }

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
        private const val SIGNAL_TIMEOUT_MS = 10_000L
        private val RUN_MUTEX = Mutex()
    }
}

/** Conserve le cache déjà émis, même si l'actualisation expire ou échoue. */
internal suspend fun latestNotificationForecast(
    stream: Flow<ApiResult<CityForecast>>,
    timeoutMs: Long = 40_000L
): CityForecast? {
    var latest: CityForecast? = null
    withTimeoutOrNull(timeoutMs) {
        runSuspendCatching {
            stream.collect { result ->
                if (result is ApiResult.Success) latest = result.data
            }
        }.onFailure { Log.w("MeteoCompare/Notif", "Unable to refresh notification forecast", it) }
    }
    // Une annulation externe reste propagée par withTimeoutOrNull/runSuspendCatching.
    return latest
}

private fun WeatherNotification.isEnabledIn(settings: NotificationSettings): Boolean =
    city.id in settings.cityIds && when (this) {
        is WeatherNotification.DailySummary -> settings.dailySummaryEnabled
        is WeatherNotification.ModelDivergence -> settings.divergenceAlertsEnabled
        is WeatherNotification.ForecastChange -> settings.forecastChangeAlertsEnabled
    }
