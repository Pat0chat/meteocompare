package com.meteocompare.app.domain.util

/**
 * Permission choices are evaluated on every explicit action, not cached across activities.
 *
 * Android can keep COARSE granted while a one-time FINE grant expires. In that case the
 * widget needs a bounded opportunity to ask for precision again; repeated denials must
 * not result in a permission-dialog loop.
 */
object LocationPermissionPolicy {
    fun shouldRequest(
        coarseGranted: Boolean,
        fineGranted: Boolean,
        precisionOffered: Boolean,
        previouslyFineGranted: Boolean = false,
        recoveryOffered: Boolean = false,
        explicitWidgetAction: Boolean = false,
        widgetReofferUsed: Boolean = false
    ): Boolean = when {
        fineGranted -> false
        !coarseGranted -> true  // Includes the expiry of a one-time grant.
        !precisionOffered -> true
        previouslyFineGranted && !recoveryOffered -> true
        // One extra contextual offer for installations predating grant-history tracking.
        explicitWidgetAction && !widgetReofferUsed -> true
        else -> false
    }
}
