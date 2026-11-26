package com.meteocompare.app.core.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Distance orthodromique en kilomètres (sphère de rayon 6 371 km). */
internal fun haversineKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val radiusKm = 6371.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val q = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * radiusKm * asin(sqrt(q.coerceIn(0.0, 1.0)))
}
