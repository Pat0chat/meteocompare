package com.meteocompare.app.widget

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.applyPersistedLocale
import com.meteocompare.app.data.repository.CurrentLocationController
import com.meteocompare.app.data.repository.CurrentLocationStore
import com.meteocompare.app.data.repository.LocationPermissionOffer
import com.meteocompare.app.ui.theme.MeteoCompareTheme
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
    private var permissionRequestPending by mutableStateOf(false)
    private var permissionRequestedThisAction = false
    private var startAttemptOnStart = false
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionRequestPending = false
        lifecycleScope.launch {
            LocationPermissionOffer.recordPermissionResult(this@WidgetLocationRefreshActivity)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            if (LocationPermissionOffer.hasAny(this@WidgetLocationRefreshActivity)) acquire()
            else status = R.string.current_location_permission_required
        }
    }

    // La page lancée depuis le widget suit la langue choisie dans MeteoCompare.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyPersistedLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeteoCompareTheme {
                WidgetLocationRefreshContent(
                    status = status,
                    inFlight = inFlight,
                    permissionRequestPending = permissionRequestPending,
                    onRetry = ::startExplicitAttempt,
                    onClose = ::finish
                )
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
        status = null // Retour immédiat au statut "préparation", sans ancien résultat trompeur.
        permissionCheckJob?.cancel()
        permissionCheckJob = lifecycleScope.launch { checkPermissionAndAcquire() }
    }

    private suspend fun launchLocationPermissionRequest() {
        if (permissionRequestPending || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            status = R.string.widget_current_location_interrupted
            return
        }
        permissionRequestPending = true
        try {
            // Both SharedPreferences initialization and apply() run on Dispatchers.IO.
            LocationPermissionOffer.markPrecisionOffered(this, explicitWidgetAction = true)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                status = R.string.widget_current_location_interrupted
                return
            }
            permissionRequest.launch(LocationPermissionOffer.requestedPermissions)
            permissionRequestedThisAction = true
        } finally {
            // A cancelled Activity must not retain a pending dialog gate.
            if (!permissionRequestedThisAction) permissionRequestPending = false
        }
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
        val checking = permissionCheckJob?.isActive == true
        permissionCheckJob?.cancel()
        acquisitionJob?.cancel()
        if (inFlight || (checking && !permissionRequestPending && status == null)) {
            inFlight = false
            status = R.string.widget_current_location_interrupted
        }
        super.onStop()
    }
}


/** États visuels explicites, sans prétendre que la météo est déjà téléchargée. */
internal enum class WidgetLocationVisualState { CHECKING, WAITING_PERMISSION, LOCATING, SUCCESS, NO_CHANGE, ERROR }

internal fun widgetLocationVisualState(
    status: Int?,
    inFlight: Boolean,
    permissionRequestPending: Boolean
): WidgetLocationVisualState = when {
    permissionRequestPending -> WidgetLocationVisualState.WAITING_PERMISSION
    inFlight -> WidgetLocationVisualState.LOCATING
    status == null -> WidgetLocationVisualState.CHECKING
    status == R.string.current_location_updated -> WidgetLocationVisualState.SUCCESS
    status == R.string.widget_current_location_recent -> WidgetLocationVisualState.NO_CHANGE
    else -> WidgetLocationVisualState.ERROR
}

/** Page d'action widget autonome, dans la palette et la langue de MeteoCompare. */
@Composable
internal fun WidgetLocationRefreshContent(
    status: Int?,
    inFlight: Boolean,
    permissionRequestPending: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val state = widgetLocationVisualState(status, inFlight, permissionRequestPending)
    val working = state == WidgetLocationVisualState.CHECKING ||
        state == WidgetLocationVisualState.WAITING_PERMISSION ||
        state == WidgetLocationVisualState.LOCATING
    val titleRes = when (state) {
        WidgetLocationVisualState.CHECKING -> R.string.widget_location_checking_title
        WidgetLocationVisualState.WAITING_PERMISSION -> R.string.widget_location_permission_title
        WidgetLocationVisualState.LOCATING -> R.string.widget_location_locating_title
        WidgetLocationVisualState.SUCCESS -> R.string.widget_location_success_title
        WidgetLocationVisualState.NO_CHANGE -> R.string.widget_location_no_change_title
        WidgetLocationVisualState.ERROR -> R.string.widget_location_error_title
    }
    val descriptionRes = when (state) {
        WidgetLocationVisualState.CHECKING -> R.string.widget_location_checking_description
        WidgetLocationVisualState.WAITING_PERMISSION -> R.string.widget_location_permission_description
        WidgetLocationVisualState.LOCATING -> R.string.widget_location_locating_description
        WidgetLocationVisualState.SUCCESS -> R.string.widget_location_success_description
        WidgetLocationVisualState.NO_CHANGE -> R.string.widget_current_location_recent
        WidgetLocationVisualState.ERROR -> status ?: R.string.current_location_unavailable
    }
    val icon: ImageVector = when (state) {
        WidgetLocationVisualState.SUCCESS -> Icons.Outlined.CheckCircle
        WidgetLocationVisualState.NO_CHANGE -> Icons.Outlined.Info
        WidgetLocationVisualState.ERROR -> Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.MyLocation
    }
    val accent = when (state) {
        WidgetLocationVisualState.SUCCESS -> MaterialTheme.colorScheme.tertiary
        WidgetLocationVisualState.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(25.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.widget_location_widget_context),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            )
                            .padding(horizontal = 22.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_current_location_refresh_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = accent.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (working) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(12.dp).size(24.dp)
                                            .testTag("widget_location_progress"),
                                        strokeWidth = 2.5.dp,
                                        color = accent
                                    )
                                } else {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.padding(12.dp).size(24.dp),
                                        tint = accent
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(titleRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent,
                                    modifier = Modifier.testTag("widget_location_status_title")
                                )
                                Text(
                                    text = stringResource(descriptionRes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("widget_location_status_description")
                                )
                            }
                        }
                        if (state == WidgetLocationVisualState.ERROR ||
                            state == WidgetLocationVisualState.NO_CHANGE) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.fillMaxWidth().testTag("widget_location_retry")
                            ) {
                                Text(stringResource(R.string.widget_current_location_retry))
                            }
                        }
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth().testTag("widget_location_close")
                        ) {
                            Text(stringResource(R.string.widget_current_location_close))
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.widget_location_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
