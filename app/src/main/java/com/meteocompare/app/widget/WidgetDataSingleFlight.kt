package com.meteocompare.app.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coalesce concurrent loads for widgets that share a city, forecast mode and
 * refresh tick. Only requests *in flight* are shared; completed results are
 * not cached, so a later refresh can read the newest preferences and weather.
 *
 * Glance can cancel one composition during a launcher resize. If that
 * composition owned the load, its cancellation must not also cancel another,
 * still-active widget waiting for the same data. A waiter may take over once
 * after an owner cancellation. Ordinary failures are propagated unchanged.
 */
internal class WidgetSingleFlight<K, V> {
    private val mutex = Mutex()
    private val pending = mutableMapOf<K, CompletableDeferred<V>>()

    suspend fun getOrLoad(key: K, load: suspend () -> V): V {
        var cancelledOwner: CompletableDeferred<V>? = null
        var retriedAfterCancellation = false
        while (true) {
            val (result, owner) = mutex.withLock {
                val existing = pending[key]
                if (existing != null && existing !== cancelledOwner) {
                    existing to false
                } else {
                    CompletableDeferred<V>().also { pending[key] = it } to true
                }
            }
            if (!owner) {
                try {
                    return result.await()
                } catch (cancelled: CancellationException) {
                    // Cancellation of this waiter must still propagate. Only
                    // a cancelled *owner* is eligible for takeover.
                    currentCoroutineContext().ensureActive()
                    if (retriedAfterCancellation) throw cancelled
                    cancelledOwner = result
                    retriedAfterCancellation = true
                    continue
                }
            }
            try {
                val value = load()
                result.complete(value)
                return value
            } catch (error: Throwable) {
                result.completeExceptionally(error)
                throw error
            } finally {
                // Never retain a cancelled, failed or completed request.
                // The identity check protects a replacement owner from the
                // previous owner's delayed cleanup.
                withContext(NonCancellable) {
                    mutex.withLock { if (pending[key] === result) pending.remove(key) }
                }
            }
        }
    }
}

internal data class WidgetDataRequest(
    val cityId: String?,
    val forecastMode: ForecastMode,
    val withValueSnapshot: Boolean,
    val refreshTick: Long,
    val localeTag: String
)

/** Shared by both Glance providers; keys exclude layout dimensions on purpose. */
internal val sharedWidgetDataLoad = WidgetSingleFlight<WidgetDataRequest, WidgetData>()
