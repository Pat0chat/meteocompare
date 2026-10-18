package com.meteocompare.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.util.LocationRefreshPolicy
import com.meteocompare.app.widget.WidgetRefreshScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Must ONLY be called from a visible Activity, never from a worker or widget receiver. */
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
        data object Unavailable : Result
    }

    private val mutex = Mutex()
    // Process-local attempt guard. Failed fixes must not cause GPS retries on each resume.
    private var lastAttemptMs = 0L

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun refreshIfEnabled(force: Boolean = false): Result =
        refresh(force = force, allowEnable = false)

    /** Only an explicit UI activation path may change disabled -> enabled. */
    suspend fun enable(): Result = refresh(force = true, allowEnable = true)

    private suspend fun refresh(
        force: Boolean,
        allowEnable: Boolean,
        updateWidgetsOnSameCell: Boolean = false
    ): Result = mutex.withLock {
        val previous = store.snapshot()
        if (!previous.enabled && !allowEnable) return@withLock Result.Unchanged
        if (!hasPermission()) return@withLock Result.PermissionMissing
        val now = System.currentTimeMillis()
        if (!LocationRefreshPolicy.shouldAcquire(
                nowMs = now,
                lastSuccessfulMs = previous.updatedAt,
                lastAttemptMs = lastAttemptMs,
                explicit = force
            )) return@withLock Result.Unchanged
        lastAttemptMs = now
        val fix = withTimeoutOrNull(12_000L) { obtainPosition() }
            ?: return@withLock Result.Unavailable
        val cell = runCatching { CurrentLocationStore.grid(fix.latitude, fix.longitude) }
            .getOrNull() ?: return@withLock Result.Unavailable
        // Only cell coordinates are persisted; the raw Location is discarded.
        store.save(cell, System.currentTimeMillis())
        val oldId = previous.asCity(context)?.id
        val newId = CurrentLocationStore.idFor(cell.first, cell.second)
        if (oldId != null && oldId != newId) {
            // Best-effort purge of old forecasts and evolution samples on movement.
            // Do not rollback a successful position update on a DB/cache error.
            runCatching { forecasts.clearCacheForCity(oldId) }
        }
        if (oldId != newId || updateWidgetsOnSameCell) {
            // Exactly one widget refresh for a new cell or a user widget tap.
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

    private suspend fun obtainPosition(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        // NETWORK first suits COARSE permission; fused is a fallback when available.
        // PASSIVE cannot request a fresh fix and is intentionally excluded.
        val preferred = listOf(LocationManager.NETWORK_PROVIDER) +
            if (Build.VERSION.SDK_INT >= 31) listOf(LocationManager.FUSED_PROVIDER) else emptyList()
        val enabled = preferred.filter {
            runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
        for (provider in enabled) {
            val fix = withTimeoutOrNull(6_000L) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val cancellation = CancellationSignal()
                    try {
                        LocationManagerCompat.getCurrentLocation(
                            manager, provider, cancellation, ContextCompat.getMainExecutor(context)
                        ) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                        continuation.invokeOnCancellation { cancellation.cancel() }
                    } catch (_: SecurityException) {
                        if (continuation.isActive) continuation.resume(null)
                    } catch (_: IllegalArgumentException) {
                        if (continuation.isActive) continuation.resume(null)
                    } catch (error: Exception) {
                        // A broken provider should not crash the foreground Activity.
                        android.util.Log.w("MeteoCompare/Location", "Location provider unavailable", error)
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
            if (fix != null) return fix
        }
        return null
    }
}
