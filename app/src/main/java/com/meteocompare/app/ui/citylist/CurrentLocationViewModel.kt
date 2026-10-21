package com.meteocompare.app.ui.citylist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.data.repository.CurrentLocationController
import com.meteocompare.app.ui.components.AppToastEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CurrentLocationViewModel @Inject constructor(
    private val controller: CurrentLocationController
) : ViewModel() {
    private val feedbackChannel = Channel<AppToastEvent>(Channel.BUFFERED)
    private var manualRefreshJob: Job? = null
    private var locateJob: Job? = null
    var isLocating by mutableStateOf(false)
        private set
    private var foregroundRefreshJob: Job? = null
    val feedback = feedbackChannel.receiveAsFlow()

    fun locate() {
        if (locateJob?.isActive == true) return
        locateJob = viewModelScope.launch {
            isLocating = true
            try {
                val result = controller.enable()
                val event = when (result) {
                    CurrentLocationController.Result.Updated ->
                        AppToastEvent.success(R.string.current_location_updated)
                    CurrentLocationController.Result.PermissionMissing ->
                        AppToastEvent.warning(R.string.current_location_permission_required)
                    CurrentLocationController.Result.ServicesDisabled ->
                        AppToastEvent.warning(R.string.current_location_services_disabled)
                    CurrentLocationController.Result.Unavailable ->
                        AppToastEvent.warning(R.string.current_location_unavailable)
                    CurrentLocationController.Result.Unchanged -> null
                }
                if (event != null) feedbackChannel.send(event)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MeteoCompare/Location", "Foreground location request failed", error)
                feedbackChannel.send(AppToastEvent.warning(R.string.current_location_unavailable))
            } finally {
                isLocating = false
            }
        }
    }
    /** Location first, then weather for the resolved cell: never fetch the old area. */
    fun refreshActive(onLocationResolved: () -> Unit) {
        if (manualRefreshJob?.isActive == true) return
        manualRefreshJob = viewModelScope.launch {
            try {
                // A pull-to-refresh must NOT opt in to location tracking.
                controller.refreshExisting()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MeteoCompare/Location", "Manual location refresh failed", error)
            }
            // No weather fetch after cancellation. GPS errors must not prevent
            // weather updates for fixed cities, using the last known cell.
            onLocationResolved()
        }
    }

    fun refreshOnResume(onLocationResolved: () -> Unit) {
        if (foregroundRefreshJob?.isActive == true) return
        foregroundRefreshJob = viewModelScope.launch {
            try {
                // Foreground policy is shared with MainActivity; the mutex and
                // minimum interval avoid an extra fix on a second lifecycle callback.
                controller.refreshIfEnabled()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MeteoCompare/Location", "Foreground location refresh failed", error)
            }
            onLocationResolved()
        }
    }
    /** Cancel sensors when Home is no longer visible; no background acquisition. */
    fun cancelLocationRequests() {
        locateJob?.cancel()
        foregroundRefreshJob?.cancel()
        manualRefreshJob?.cancel()
    }

    fun denied() {
        viewModelScope.launch {
            feedbackChannel.send(AppToastEvent.warning(R.string.current_location_permission_required))
        }
    }
}
