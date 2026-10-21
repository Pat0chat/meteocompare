package com.meteocompare.app.widget

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import com.meteocompare.app.R
import com.meteocompare.app.data.repository.CurrentLocationController
import com.meteocompare.app.data.repository.CurrentLocationStore
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
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) acquire() else status = R.string.current_location_permission_required
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
                            permissionCheckJob = lifecycleScope.launch { checkPermissionAndAcquire() }
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
            permissionCheckJob = lifecycleScope.launch { checkPermissionAndAcquire() }
        } else {
            // An in-flight acquisition was cancelled by recreation; no hidden retry.
            status = R.string.widget_current_location_interrupted
        }
    }

    private suspend fun checkPermissionAndAcquire() {
        // A stale widget must not re-enable a user-disabled feature.
        if (!store.snapshot().enabled) {
            status = R.string.widget_current_location_disabled
        } else if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) acquire()
        else permissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
                status = when (result) {
                    CurrentLocationController.Result.Updated -> R.string.current_location_updated
                    CurrentLocationController.Result.Unchanged -> R.string.widget_current_location_recent
                    CurrentLocationController.Result.PermissionMissing -> R.string.current_location_permission_required
                    CurrentLocationController.Result.ServicesDisabled -> R.string.current_location_services_disabled
                    CurrentLocationController.Result.Unavailable -> R.string.current_location_unavailable
                }
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
