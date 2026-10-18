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

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun refreshIfEnabled(force: Boolean = false): Result =
        refresh(force = force, allowEnable = false)

    /** Only an explicit UI activation path may change disabled -> enabled. */
    suspend fun enable(): Result = refresh(force = true, allowEnable = true)

    private suspend fun refresh(force: Boolean, allowEnable: Boolean): Result = mutex.withLock {
        val previous = store.snapshot()
        if (!previous.enabled && !allowEnable) return@withLock Result.Unchanged
        if (!hasPermission()) return@withLock Result.PermissionMissing
        val now = System.currentTimeMillis()
        if (!force && now - previous.updatedAt in 0L until 5 * 60_000L) {
            return@withLock Result.Unchanged
        }
        val fix = withTimeoutOrNull(12_000L) { obtainPosition() }
            ?: return@withLock Result.Unavailable
        val cell = runCatching { CurrentLocationStore.grid(fix.latitude, fix.longitude) }
            .getOrNull() ?: return@withLock Result.Unavailable
        // Only cell coordinates are persisted; the raw Location is discarded.
        store.save(cell, now)
        val oldId = previous.asCity(context)?.id
        val newId = CurrentLocationStore.idFor(cell.first, cell.second)
        if (oldId != null && oldId != newId) {
            // Best-effort purge of old forecasts and evolution samples on movement.
            // Do not rollback a successful position update on a DB/cache error.
            runCatching { forecasts.clearCacheForCity(oldId) }
        }
        if (oldId != newId) {
            // Render widgets from the new stored cell; worker never queries device GPS.
            runCatching { WidgetRefreshScheduler.triggerImmediateRefresh(context) }
        }
        Result.Updated
    }

    suspend fun refreshExisting(): Result {
        if (!store.snapshot().enabled) return Result.Unchanged
        return refreshIfEnabled(force = true)
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
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // Android platform fused provider (API 31+) requires no Google SDK.
        val preferred = if (Build.VERSION.SDK_INT >= 31)
            listOf(LocationManager.FUSED_PROVIDER) else emptyList()
        val provider = (preferred + LocationManager.NETWORK_PROVIDER + LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        return suspendCancellableCoroutine { continuation ->
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
            }
        }
    }
}
