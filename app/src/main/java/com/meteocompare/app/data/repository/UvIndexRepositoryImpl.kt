package com.meteocompare.app.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.data.remote.OpenMeteoApi
import com.meteocompare.app.data.remote.dto.UvForecastDto
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.model.validUvIndexOrNull
import com.meteocompare.app.domain.repository.UvIndexRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class UvCacheRecord(val fetchedAtMs: Long, val response: UvForecastDto)

/**
 * UV are location-level data, not members of the model comparison batch.
 * A single persisted record is reused across Home, Detail and Graphic views.
 * Concurrent callers share one Deferred. Neither switching engines nor tapping
 * through timeline points fetches UV again. Cache TTL is independent of the
 * refresh policy for multi-model forecasts.
 */
@Singleton
class UvIndexRepositoryImpl @Inject constructor(
    private val api: OpenMeteoApi,
    private val json: Json,
    private val clock: Clock,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UvIndexRepository {
    // Hilt can construct this singleton from a ViewModel on the UI thread.
    // Defer filesystem-backed preferences initialization until get() runs on ioDispatcher.
    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.getSharedPreferences("uv_forecast_cache_v1", Context.MODE_PRIVATE)
    }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<UvForecast?>>()
    private val lastFailure = ConcurrentHashMap<String, Long>()

    override suspend fun get(city: City): UvForecast? = withContext(ioDispatcher) {
        val key = locationKey(city)
        val currentZone = CurrentLocationStore.isCurrentCity(city.id)
        val previous = if (currentZone) null else read(key)
        val now = clock.millis()
        if (previous != null && isUvCacheFresh(previous.fetchedAtMs, now)) {
            return@withContext previous.response.toDomainOrNull()
        }
        // Keep displaying stale data offline or when an endpoint is temporarily down.
        if (!networkMonitor.isOnline() ||
            (lastFailure[key]?.let { now - it in 0 until UV_FAILURE_BACKOFF_MS } == true)
        ) return@withContext previous?.response?.toDomainOrNull()

        val request = mutex.withLock {
            inFlight[key]?.takeIf { !it.isCompleted } ?: scope.async(start = CoroutineStart.LAZY) {
                // Check again after another subscriber may have completed the fetch.
                val recent = if (currentZone) null else read(key)
                val requestTime = clock.millis()
                if (recent != null && isUvCacheFresh(recent.fetchedAtMs, requestTime)) {
                    return@async recent.response.toDomainOrNull()
                }
                // Another caller may have failed or connectivity may have changed
                // after our initial check but before this deferred started.
                if (!networkMonitor.isOnline() ||
                    (lastFailure[key]?.let { requestTime - it in 0 until UV_FAILURE_BACKOFF_MS } == true)
                ) return@async recent?.response?.toDomainOrNull()
                try {
                    val response = api.getUvForecast(city.latitude, city.longitude)
                    val parsed = response.toDomainOrNull()
                    if (parsed != null) {
                        if (!currentZone) prefs.edit {
                            putString(
                                key,
                                json.encodeToString(
                                    UvCacheRecord.serializer(),
                                    UvCacheRecord(clock.millis(), response)
                                )
                            )
                        }
                        lastFailure.remove(key)
                        parsed
                    } else {
                        lastFailure[key] = clock.millis()
                        recent?.response?.toDomainOrNull()
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Log.w("MeteoCompare/UV", "UV refresh failed; retaining existing cache", error)
                    lastFailure[key] = clock.millis()
                    recent?.response?.toDomainOrNull()
                }
            }.also { created ->
                inFlight[key] = created
                created.invokeOnCompletion {
                    scope.launch {
                        mutex.withLock { if (inFlight[key] === created) inFlight.remove(key) }
                    }
                }
            }
        }
        request.start()
        request.await() ?: previous?.response?.toDomainOrNull()
    }

    private fun read(key: String): UvCacheRecord? = prefs.getString(key, null)?.let { text ->
        runCatching { json.decodeFromString(UvCacheRecord.serializer(), text) }.getOrNull()
    }
}

internal const val UV_CACHE_TTL_MS = 3 * 60 * 60 * 1000L
internal const val UV_FAILURE_BACKOFF_MS = 20 * 60 * 1000L

internal fun isUvCacheFresh(fetchedAtMs: Long, nowMs: Long): Boolean =
    nowMs - fetchedAtMs in 0 until UV_CACHE_TTL_MS

/** Coordinates, not city ID/model/engine, determine UV identity. */
internal fun locationKey(city: City): String = "${city.latitude},${city.longitude}"

internal fun UvForecastDto.toDomainOrNull(): UvForecast? {
    val zone = runCatching { ZoneId.of(timezone) }.getOrNull() ?: return null
    val byHour = hourly?.time.orEmpty().mapIndexedNotNull { index, text ->
        val value = hourly?.uvIndex?.getOrNull(index)?.validUvIndexOrNull() ?: return@mapIndexedNotNull null
        val time = runCatching { LocalDateTime.parse(text).atZone(zone).toInstant() }.getOrNull()
            ?: return@mapIndexedNotNull null
        time to value
    }.toMap()
    val byDay = daily?.time.orEmpty().mapIndexedNotNull { index, text ->
        val value = daily?.uvIndexMax?.getOrNull(index)?.validUvIndexOrNull() ?: return@mapIndexedNotNull null
        val date = runCatching { LocalDate.parse(text) }.getOrNull() ?: return@mapIndexedNotNull null
        date to value
    }.toMap()
    return if (byHour.isEmpty() && byDay.isEmpty()) null else UvForecast(byHour, byDay)
}
