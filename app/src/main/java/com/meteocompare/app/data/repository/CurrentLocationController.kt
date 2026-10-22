package com.meteocompare.app.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.core.util.runSuspendCatching
import com.meteocompare.app.domain.util.LocationFixPolicy
import com.meteocompare.app.domain.util.LocationProviderPolicy
import com.meteocompare.app.domain.util.LocationRefreshPolicy
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Foreground one-shot acquisition. Only a coarse grid cell is ever persisted. */
@Singleton
class CurrentLocationController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: CurrentLocationStore,
    private val forecasts: ForecastRepository
) {
    sealed interface Result {
        data object Updated : Result
        data object Unchanged : Result
        data object PermissionMissing : Result
        data object ServicesDisabled : Result
        data object Unavailable : Result
        data object ApproximateUnavailable : Result
    }

    val locationState = store.state

    private val mutex = Mutex()
    // A foreground user action preempts the automatic fix rather than waiting 12 s.
    // An explicit opt-out invalidates every already-started acquisition immediately.
    private val activeFix = AtomicReference<Deferred<Location?>?>(null)
    private val automaticFix = AtomicReference<Deferred<Location?>?>(null)
    private val disableGeneration = AtomicLong(0L)
    private val manualGeneration = AtomicLong(0L)
    // Independent guards: an automatic attempt must never consume a user's explicit tap.
    private var lastAutomaticAttemptMs = 0L
    private var lastExplicitAttemptMs = 0L

    fun hasPermission(): Boolean = LocationPermissionOffer.hasAny(context)

    private fun hasFinePermission(): Boolean = LocationPermissionOffer.hasFine(context)

    suspend fun refreshIfEnabled(force: Boolean = false): Result =
        refresh(force = force, allowEnable = false)

    /** Explicit user opt-in; failure does not save an enabled location or coordinates. */
    suspend fun enable(): Result = refresh(force = true, allowEnable = true)

    private suspend fun refresh(
        force: Boolean,
        allowEnable: Boolean,
        updateWidgetsOnSameCell: Boolean = false
    ): Result {
        // Record the intention before entering the mutex: even a manual tap that
        // races with the automatic preflight can preempt its future provider request.
        val manualAtStart = if (force) manualGeneration.incrementAndGet()
            else manualGeneration.get()
        if (force) automaticFix.get()?.cancel()
        return mutex.withLock {
            val generation = disableGeneration.get()
            val previous = store.snapshot()
            if (!previous.enabled && !allowEnable) return@withLock Result.Unchanged
            if (!hasPermission()) return@withLock Result.PermissionMissing

            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return@withLock Result.Unavailable
            if (!LocationManagerCompat.isLocationEnabled(manager)) return@withLock Result.ServicesDisabled

            val now = System.currentTimeMillis()
            // If the user has just acquired a position via the app, the widget can refresh
            // that same cell without initiating a redundant request for another fix.
            if (disableGeneration.get() != generation) return@withLock Result.Unchanged
            if (force && previous.enabled && previous.latCell != null && previous.lonCell != null &&
                LocationFixPolicy.canReuseFreshSuccess(now, previous.updatedAt)) {
                if (updateWidgetsOnSameCell) {
                    runCatching { WidgetRefreshScheduler.triggerImmediateRefresh(context) }
                }
                return@withLock Result.Updated
            }

            val lastAttempt = if (force) lastExplicitAttemptMs else lastAutomaticAttemptMs
            if (!LocationRefreshPolicy.shouldAcquire(
                    nowMs = now,
                    lastSuccessfulMs = previous.updatedAt,
                    lastAttemptMs = lastAttempt,
                    explicit = force
                )) return@withLock Result.Unchanged
            if (force) lastExplicitAttemptMs = now else lastAutomaticAttemptMs = now

            val fineAtStart = hasFinePermission()
            val timeoutMs = if (force) LocationFixPolicy.EXPLICIT_TIMEOUT_MS
                else LocationFixPolicy.AUTOMATIC_TIMEOUT_MS
            val fix = try {
                coroutineScope {
                    val task = async { withTimeoutOrNull(timeoutMs) { obtainPosition(manager, force) } }
                    activeFix.set(task)
                    if (!force) automaticFix.set(task)
                    // Covers disable() or a manual tap racing with task registration.
                    if (disableGeneration.get() != generation ||
                        (!force && manualGeneration.get() != manualAtStart)) task.cancel()
                    try {
                        task.await()
                    } finally {
                        activeFix.compareAndSet(task, null)
                        automaticFix.compareAndSet(task, null)
                    }
                }
            } catch (cancelled: CancellationException) {
                // A cancelled parent Activity must still receive cancellation.
                if (!currentCoroutineContext().isActive) throw cancelled
                // A user tap preempted an automatic fix, or the location was disabled.
                return@withLock Result.Unchanged
            }
            // A pending opt-out must never save a sample or enqueue widget/network work.
            if (disableGeneration.get() != generation) return@withLock Result.Unchanged
            if (!LocationFixPolicy.mayCommitFix(
                    generation, disableGeneration.get(), hasPermission(),
                    fineAtStart, hasFinePermission()
                )) {
                Log.w("MeteoCompare/Location", "Foreground location permission expired")
                return@withLock Result.PermissionMissing
            }
            if (fix == null) {
                Log.w("MeteoCompare/Location", "No foreground location fix from enabled providers " +
                    "(precision=" + (if (hasFinePermission()) "exact" else "approximate") + ")")
                return@withLock if (hasFinePermission()) Result.Unavailable
                    else Result.ApproximateUnavailable
            }
            val cell = runCatching { CurrentLocationStore.grid(fix.latitude, fix.longitude) }
                .getOrNull() ?: return@withLock Result.Unavailable

            // Never publish a fix acquired before an explicit disable/permission revocation.
            if (disableGeneration.get() != generation) return@withLock Result.Unchanged
            if (!LocationFixPolicy.mayCommitFix(
                    generation, disableGeneration.get(), hasPermission(),
                    fineAtStart, hasFinePermission()
                )) return@withLock Result.PermissionMissing
            // Raw coordinates, provider, accuracy and history are never persisted or logged.
            store.save(cell, System.currentTimeMillis())
            // If an opt-out was requested during the DataStore write, disable()
            // will clear the cell under this mutex. Never schedule obsolete work.
            if (disableGeneration.get() != generation) return@withLock Result.Unchanged
            val oldId = previous.asCity(context)?.id
            val newId = CurrentLocationStore.idFor(cell.first, cell.second)
            if (oldId != null && oldId != newId) {
                // Best-effort purge of the previous region's cache; no rollback of location.
                runSuspendCatching { forecasts.clearCacheForCity(oldId) }
            }
            if (oldId != newId || updateWidgetsOnSameCell) {
                runCatching { WidgetRefreshScheduler.triggerImmediateRefresh(context) }
            }
            Result.Updated
        }
    }

    suspend fun refreshExisting(updateWidgetsOnSameCell: Boolean = false): Result {
        if (!store.snapshot().enabled) return Result.Unchanged
        return refresh(force = true, allowEnable = false,
            updateWidgetsOnSameCell = updateWidgetsOnSameCell)
    }

    suspend fun disable() {
        // Do this BEFORE acquiring the mutex: a cold fix cannot re-enable a removed card.
        disableGeneration.incrementAndGet()
        activeFix.get()?.cancel()
        mutex.withLock {
            val oldId = store.snapshot().asCity(context)?.id
            store.clear()
            if (oldId != null) {
                runSuspendCatching { forecasts.clearCacheForCity(oldId) }
                runCatching { WidgetRefreshScheduler.triggerImmediateRefresh(context) }
            }
        }
    }

    @SuppressLint("InlinedApi") // FUSED_PROVIDER is selected only when API >= 31.
    private fun availableProviders(manager: LocationManager): List<String> {
        // On emulators, coordinates injected through Extended Controls > Location
        // can be visible only through GPS_PROVIDER. Never query it with COARSE only.
        val preferred = LocationProviderPolicy.priority(Build.VERSION.SDK_INT, hasFinePermission())
        return preferred.map { source ->
            when (source) {
                LocationProviderPolicy.Source.GPS -> LocationManager.GPS_PROVIDER
                LocationProviderPolicy.Source.FUSED -> LocationManager.FUSED_PROVIDER
                LocationProviderPolicy.Source.NETWORK -> LocationManager.NETWORK_PROVIDER
            }
        }.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission") // Checked by refresh() before entering this foreground-only flow.
    private suspend fun obtainPosition(manager: LocationManager, explicit: Boolean): Location? {
        val providers = availableProviders(manager)
        if (providers.isEmpty()) {
            Log.w("MeteoCompare/Location", "No enabled location provider for granted accuracy")
            return null
        }
        // Log only provider names, never location coordinates, altitude or accuracy.
        Log.d("MeteoCompare/Location", "Foreground providers=" + providers.joinToString() +
            ", exactPermission=" + hasFinePermission())

        // Do not waste a cold request if the system has a recent coarse fix,
        // e.g. generated by Maps. Use elapsed real time, not adjustable wall time.
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()
        val cached = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                ?.takeIf { LocationFixPolicy.isRecentCachedFix(elapsedNanos, it.elapsedRealtimeNanos, explicit = explicit) }
        }.minByOrNull { elapsedNanos - it.elapsedRealtimeNanos }
        if (cached != null) return cached

        // Cold providers need longer than 6 seconds, especially after permission
        // is granted. Each provider gets a bounded one-shot attempt.
        for (provider in providers) {
            val fix = withTimeoutOrNull(if (explicit) 7_000L else 4_000L) {
                currentFix(manager, provider)
            }
            if (fix != null) return fix
        }

        // Some vendor/emulator implementations return null from getCurrentLocation
        // before their provider has produced the *first* sample. Register a strictly
        // short-lived listener only for a manual foreground request; cancellation
        // (including Activity destruction) always removes it.
        if (explicit) {
            for (provider in providers) {
                val fix = withTimeoutOrNull(5_000L) { firstUpdate(manager, provider) }
                if (fix != null) return fix
            }
        }
        return null
    }

    @SuppressLint("MissingPermission") // Foreground COARSE/FINE permission checked by refresh().
    private suspend fun currentFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, cancellation, ContextCompat.getMainExecutor(context)
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Provider errors should not crash the Activity or reveal coordinates.
                Log.w("MeteoCompare/Location", "One-shot location provider unavailable", error)
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @SuppressLint("MissingPermission") // Foreground COARSE/FINE permission checked by refresh().
    private suspend fun firstUpdate(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (continuation.isActive) continuation.resume(location)
                }
                override fun onProviderDisabled(disabledProvider: String) {
                    runCatching { manager.removeUpdates(this) }
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("MeteoCompare/Location", "Foreground location listener unavailable", error)
                runCatching { manager.removeUpdates(listener) }
                if (continuation.isActive) continuation.resume(null)
            }
        }
}
