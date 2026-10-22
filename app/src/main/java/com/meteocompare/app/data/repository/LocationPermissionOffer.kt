package com.meteocompare.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.meteocompare.app.domain.util.LocationPermissionPolicy

/** A one-time, contextual precision offer; stores only the offer-shown flag, never coordinates. */
object LocationPermissionOffer {
    private const val PREFERENCES = "current_location_permission_ui"
    private const val PRECISION_OFFERED = "precision_offered"
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

    /** Also offers once to users who granted COARSE before this feature existed. */
    fun shouldRequest(context: Context): Boolean = LocationPermissionPolicy.shouldRequest(
        coarseGranted = hasCoarse(context),
        fineGranted = hasFine(context),
        precisionOffered = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(PRECISION_OFFERED, false)
    )

    fun markPrecisionOffered(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(PRECISION_OFFERED, true).apply()
    }
}
