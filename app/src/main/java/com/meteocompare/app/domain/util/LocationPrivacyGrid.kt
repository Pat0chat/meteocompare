package com.meteocompare.app.domain.util

import kotlin.math.roundToInt

/** Pure and deterministic. No Android/GPS types may escape this conversion. */
object LocationPrivacyGrid {
    const val STEP_DEGREES = 0.05
    const val ID_PREFIX = "current:"

    fun cell(latitude: Double, longitude: Double): Pair<Int, Int> {
        require(latitude.isFinite() && longitude.isFinite())
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        return (latitude / STEP_DEGREES).roundToInt() to
            (longitude / STEP_DEGREES).roundToInt()
    }

    fun key(cell: Pair<Int, Int>): String = "$ID_PREFIX${cell.first}:${cell.second}"
    fun isCellId(id: String) = id.startsWith(ID_PREFIX)
}
