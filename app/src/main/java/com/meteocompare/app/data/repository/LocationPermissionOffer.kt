package com.meteocompare.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.meteocompare.app.domain.util.LocationPermissionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores only consent-UI history, never coordinates, accuracy, providers or a location trace. */
object LocationPermissionOffer {
    private const val PREFERENCES = "current_location_permission_ui"
    private const val PRECISION_OFFERED = "precision_offered"
    private const val FINE_GRANTED_BEFORE = "fine_granted_before"
    private const val PRECISION_RECOVERY_OFFERED = "precision_recovery_offered"
    private const val WIDGET_REOFFER_USED = "widget_reoffer_used"

    val requestedPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasCoarse(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasFine(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAny(context: Context): Boolean = hasCoarse(context) || hasFine(context)

    /**
     * Recheck the OS grant at each tap. COARSE may outlive an "Only this time" FINE grant.
     * Preference file initialization can read disk, so do all consent bookkeeping on IO.
     * Re-offers are bounded: a refusal/approximate choice does not prompt indefinitely.
     */
    suspend fun shouldRequest(context: Context, explicitWidgetAction: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            LocationPermissionPolicy.shouldRequest(
                coarseGranted = hasCoarse(context),
                fineGranted = hasFine(context),
                precisionOffered = prefs.getBoolean(PRECISION_OFFERED, false),
                previouslyFineGranted = prefs.getBoolean(FINE_GRANTED_BEFORE, false),
                recoveryOffered = prefs.getBoolean(PRECISION_RECOVERY_OFFERED, false),
                explicitWidgetAction = explicitWidgetAction,
                widgetReofferUsed = prefs.getBoolean(WIDGET_REOFFER_USED, false)
            )
        }

    /** Called before launching Android's dialog, not after an unsuccessful location fix. */
    suspend fun markPrecisionOffered(context: Context, explicitWidgetAction: Boolean = false) =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val recovery = prefs.getBoolean(FINE_GRANTED_BEFORE, false) &&
                hasCoarse(context) && !hasFine(context)
            val editor = prefs.edit().putBoolean(PRECISION_OFFERED, true)
            if (recovery) editor.putBoolean(PRECISION_RECOVERY_OFFERED, true)
            if (explicitWidgetAction) editor.putBoolean(WIDGET_REOFFER_USED, true)
            editor.apply()
        }

    /** Reset the recovery gate only after Android actually grants precise location. */
    suspend fun recordPermissionResult(context: Context) = withContext(Dispatchers.IO) {
        if (!hasFine(context)) return@withContext
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FINE_GRANTED_BEFORE, false) &&
            !prefs.getBoolean(PRECISION_RECOVERY_OFFERED, false) &&
            !prefs.getBoolean(WIDGET_REOFFER_USED, false)) return@withContext
        prefs.edit()
            .putBoolean(FINE_GRANTED_BEFORE, true)
            .putBoolean(PRECISION_RECOVERY_OFFERED, false)
            .putBoolean(WIDGET_REOFFER_USED, false)
            .apply()
    }
}
