package com.meteocompare.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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
import com.meteocompare.app.domain.util.LocationFixPolicy
import com.meteocompare.app.domain.util.LocationRefreshPolicy
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    }

    private val mutex = Mutex()
    // Independent guards: an automatic attempt must never consume a user's explicit tap.
    private var lastAutomaticAttemptMs = 0L
    private var lastExplicitAttemptMs = 0L

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun refreshIfEnabled(force: Boolean = false): Result =
        refresh(force = force, allowEnable = false)

    /** Explicit user opt-in; failure does not save an enabled location or coordinates. */
    suspend fun enable(): Result = refresh(force = true, allowEnable = true)

    private suspend fun refresh(
        force: Boolean,
        allowEnable: Boolean,
        updateWidgetsOnSameCell: Boolean = false
    ): Result = mutex.withLock {
        val previous = store.snapshot()
        if (!previous.enabled && !allowEnable) return@withLock Result.Unchanged
        if (!hasPermission()) return@withLock Result.PermissionMissing

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withLock Result.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return@withLock Result.ServicesDisabled

        val now = System.currentTimeMillis()
        // If the user has just acquired a position via the app, the widget can refresh
        // that same cell without initiating a redundant request for another fix.
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

        val timeoutMs = if (force) LocationFixPolicy.EXPLICIT_TIMEOUT_MS
            else LocationFixPolicy.AUTOMATIC_TIMEOUT_MS
        val fix = withTimeoutOrNull(timeoutMs) { obtainPosition(manager, force) }
            ?: run {
                Log.w("MeteoCompare/Location", "No coarse location fix from enabled providers")
                return@withLock Result.Unavailable
            }
        val cell = runCatching { CurrentLocationStore.grid(fix.latitude, fix.longitude) }
            .getOrNull() ?: return@withLock Result.Unavailable

        // Raw coordinates, provider, accuracy and history are never persisted or logged.
        store.save(cell, System.currentTimeMillis())
        val oldId = previous.asCity(context)?.id
        val newId = CurrentLocationStore.idFor(cell.first, cell.second)
        if (oldId != null && oldId != newId) {
            // Best-effort purge of the previous region's cache; no rollback of location.
            runCatching { forecasts.clearCacheForCity(oldId) }
        }
        if (oldId != newId || updateWidgetsOnSameCell) {
            runCatching { WidgetRefreshScheduler.triggerImmediateRefresh(context) }
        }
        Result.Updated
    }

    suspend fun refreshExisting(updateWidgetsOnSameCell: Boolean = false): Result {
        if (!store.snapshot().enabled) return Result.Unchanged
        return refresh(force = true, allowEnable = false,
            updateWidgetsOnSameCell = updateWidgetsOnSameCell)
    }

    suspend fun disable() = mutex.withLock {
        val oldId = store.snapshot().asCity(context)?.id
        store.clear()
        if (oldId != null) {
            runCatching { forecasts.clearCacheForCity(oldId) }
            runCatching { WidgetRefreshScheduler.triggerImmediateRefresh(context) }
        }
    }

    private fun availableProviders(manager: LocationManager): List<String> {
        // FUSED on API 31+ can combine network signals. NETWORK is a fallback.
        // GPS_PROVIDER requires precise access and is deliberately never requested.
        val candidates = if (Build.VERSION.SDK_INT >= 31) {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else listOf(LocationManager.NETWORK_PROVIDER)
        return candidates.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission") // Checked by refresh() before entering this foreground-only flow.
    private suspend fun obtainPosition(manager: LocationManager, explicit: Boolean): Location? {
        val providers = availableProviders(manager)
        if (providers.isEmpty()) return null

        // Do not waste a cold request if the system has a recent coarse fix,
        // e.g. generated by Maps. Use elapsed real time, not adjustable wall time.
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()
        val cached = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                ?.takeIf { LocationFixPolicy.isRecentCachedFix(elapsedNanos, it.elapsedRealtimeNanos) }
        }.minByOrNull { elapsedNanos - it.elapsedRealtimeNanos }
        if (cached != null) return cached

        // Cold providers need longer than 6 seconds, especially after permission
        // is granted. Each provider gets a bounded one-shot attempt.
        for (provider in providers) {
            val fix = withTimeoutOrNull(if (explicit) 10_000L else 6_000L) {
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

    @SuppressLint("MissingPermission") // Coarse permission checked by refresh().
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

    @SuppressLint("MissingPermission") // Coarse permission checked by refresh().
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
