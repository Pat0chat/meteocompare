package com.meteocompare.app.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import com.meteocompare.app.R
import com.meteocompare.app.data.repository.CurrentLocationController
import com.meteocompare.app.data.repository.CurrentLocationStore
import com.meteocompare.app.data.repository.LocationPermissionOffer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The widget MUST start a visible Activity, never query GPS in a worker/receiver. */
@AndroidEntryPoint
class WidgetLocationRefreshActivity : ComponentActivity() {
    @Inject lateinit var controller: CurrentLocationController
    @Inject lateinit var store: CurrentLocationStore
    private var inFlight by mutableStateOf(false)
    private var acquisitionJob: Job? = null
    private var permissionCheckJob: Job? = null
    private var status by mutableStateOf<Int?>(null)
    private var permissionRequestPending = false
    private var permissionRequestedThisAction = false
    private var startAttemptOnStart = false
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionRequestPending = false
        LocationPermissionOffer.recordPermissionResult(this)
        if (LocationPermissionOffer.hasAny(this)) acquire()
        else status = R.string.current_location_permission_required
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(getString(R.string.widget_current_location_refresh_title))
                    if (inFlight) CircularProgressIndicator()
                    else {
                        Text(getString(status ?: R.string.current_location_privacy_hint))
                        Button(onClick = {
                            startExplicitAttempt()
                        }) {
                            Text(getString(R.string.widget_current_location_retry))
                        }
                        Button(onClick = { finish() }) {
                            Text(getString(R.string.widget_current_location_close))
                        }
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            // onCreate can finish before STARTED (especially with a warm DataStore).
            // Launching the Android dialog earlier may fail silently on widget entry.
            startAttemptOnStart = true
        } else {
            // An in-flight acquisition was cancelled by recreation; no hidden retry.
            status = R.string.widget_current_location_interrupted
        }
    }

    override fun onStart() {
        super.onStart()
        if (startAttemptOnStart) {
            startAttemptOnStart = false
            startExplicitAttempt()
        }
    }

    /** A new widget tap is the only way to restart consent after a refusal. */
    private fun startExplicitAttempt() {
        if (inFlight || permissionRequestPending) return
        permissionRequestedThisAction = false
        permissionCheckJob?.cancel()
        permissionCheckJob = lifecycleScope.launch { checkPermissionAndAcquire() }
    }

    private fun launchLocationPermissionRequest() {
        if (permissionRequestPending || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            status = R.string.widget_current_location_interrupted
            return
        }
        permissionRequestPending = true
        permissionRequestedThisAction = true
        LocationPermissionOffer.markPrecisionOffered(this, explicitWidgetAction = true)
        permissionRequest.launch(LocationPermissionOffer.requestedPermissions)
    }

    private suspend fun checkPermissionAndAcquire() {
        // A stale widget must not re-enable a user-disabled feature.
        if (!store.snapshot().enabled) {
            status = R.string.widget_current_location_disabled
        } else {
            // Read the ACTUAL grant for each tap: "Only this time" may have expired
            // while an older approximate grant is still present.
            LocationPermissionOffer.recordPermissionResult(this)
            val request = LocationPermissionOffer.shouldRequest(this, explicitWidgetAction = true)
            android.util.Log.d("MeteoCompare/Location", "Widget permission check: " +
                "coarse=" + LocationPermissionOffer.hasCoarse(this) +
                ", fine=" + LocationPermissionOffer.hasFine(this) +
                ", requesting=" + request)
            if (request) launchLocationPermissionRequest() else acquire()
        }
    }

    private fun acquire() {
        if (inFlight) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            status = R.string.widget_current_location_interrupted
            return
        }
        inFlight = true
        acquisitionJob = lifecycleScope.launch {
            try {
                val result = controller.refreshExisting(updateWidgetsOnSameCell = true)
                val resultStatus = when (result) {
                    CurrentLocationController.Result.Updated -> R.string.current_location_updated
                    CurrentLocationController.Result.Unchanged -> R.string.widget_current_location_recent
                    CurrentLocationController.Result.PermissionMissing -> {
                        // Grant may expire between the preflight check and location API.
                        // Re-prompt once on this visible action; no automatic dialog loop.
                        if (!permissionRequestedThisAction && !LocationPermissionOffer.hasAny(this@WidgetLocationRefreshActivity)) {
                            launchLocationPermissionRequest()
                        }
                        R.string.current_location_permission_required
                    }
                    CurrentLocationController.Result.ServicesDisabled -> R.string.current_location_services_disabled
                    CurrentLocationController.Result.Unavailable -> R.string.current_location_unavailable
                    CurrentLocationController.Result.ApproximateUnavailable -> R.string.current_location_approximate_unavailable
                }
                if (!permissionRequestPending) status = resultStatus
                // Controller owns the single enqueue, including on same-cell updates.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MeteoCompare/Location", "Widget location refresh failed", error)
                status = R.string.current_location_unavailable
            } finally {
                inFlight = false
            }
        }
    }

    override fun onStop() {
        // A widget action never keeps locating after its Activity is hidden.
        permissionCheckJob?.cancel()
        acquisitionJob?.cancel()
        if (inFlight) {
            inFlight = false
            status = R.string.widget_current_location_interrupted
        }
        super.onStop()
    }
}
