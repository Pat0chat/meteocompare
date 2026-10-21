package com.meteocompare.app.domain.util

import kotlin.math.abs

/** Estimated thermal comfort is a derived index, never a direct measurement. */
object ApparentTemperature {
    const val HOME_SIGNIFICANT_DELTA_C = 2.0

    fun significant(actualC: Double?, apparentC: Double?): Boolean =
        actualC?.isFinite() == true && apparentC?.isFinite() == true &&
            abs(actualC - apparentC) >= HOME_SIGNIFICANT_DELTA_C

    fun median(values: List<Double>): Double? {
        val sorted = values.filter(Double::isFinite).sorted()
        if (sorted.isEmpty()) return null
        val center = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[center]
        else sorted[center - 1] / 2.0 + sorted[center] / 2.0
    }
}
