package com.meteocompare.app.domain.util

/** Prevents repeatedly asking for optional precision after the user chose approximate. */
object LocationPermissionPolicy {
    fun shouldRequest(coarseGranted: Boolean, fineGranted: Boolean, precisionOffered: Boolean): Boolean =
        (!coarseGranted && !fineGranted) || (!fineGranted && !precisionOffered)
}
