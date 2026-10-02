package com.meteocompare.app.core.charts

import kotlin.math.abs
import kotlin.math.max

/** Canvas safety only. This never sanitizes or writes a scientific/cache value. */
fun metricPlotValue(value: Double?): Double? =
    value?.takeIf { it.isFinite() && abs(it) <= 1e12 }

/** All bounds, padding and minimum spans are in canonical metric units. */
data class CanonicalChartRange(val min: Double, val max: Double) {
    init {
        require(min.isFinite() && max.isFinite() && max > min)
    }
    val span: Double get() = max - min
    fun ticks(count: Int): List<Double> {
        require(count >= 2)
        return List(count) { min + span * it / (count - 1) }
    }
}

fun canonicalChartRange(
    values: Iterable<Double?>,
    minimumSpan: Double = 1.0,
    paddingFraction: Double = 0.1,
    minimumPadding: Double = 0.0,
    zeroFloor: Boolean = false,
    includeZero: Boolean = false
): CanonicalChartRange {
    require(minimumSpan.isFinite() && minimumSpan > 0.0 && minimumSpan <= 1e12)
    require(paddingFraction.isFinite() && paddingFraction in 0.0..1.0)
    require(minimumPadding.isFinite() && minimumPadding in 0.0..1e12)
    val finite = values.mapNotNull(::metricPlotValue).filter { !zeroFloor || it >= 0.0 }
    if (finite.isEmpty()) return CanonicalChartRange(0.0, minimumSpan)
    val rawMin = if (includeZero) minOf(0.0, finite.min()) else finite.min()
    val rawMax = if (includeZero) maxOf(0.0, finite.max()) else finite.max()
    val padding = max(minimumPadding, (rawMax - rawMin) * paddingFraction)
    var low = rawMin - padding
    var high = rawMax + padding
    if (high - low < minimumSpan) {
        val center = low / 2.0 + high / 2.0
        low = center - minimumSpan / 2.0
        high = center + minimumSpan / 2.0
    }
    if (zeroFloor) {
        low = max(0.0, low)
        high = max(high, low + minimumSpan)
    }
    return CanonicalChartRange(low, high)
}
