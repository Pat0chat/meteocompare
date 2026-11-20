package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import java.time.LocalDate
import java.time.ZoneId

/** Daily extremes are computed from real hourly samples, never from the current hour. */
data class DailyAtmosphericMetrics(
    val apparentMinC: Double? = null,
    val apparentMaxC: Double? = null,
    val meanPressureHpa: Double? = null
)

fun ForecastSeries.dailyAtmosphericMetrics(date: LocalDate, zone: ZoneId): DailyAtmosphericMetrics {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val feelsLike = linkedMapOf<java.time.Instant, Double>()
    val pressure = linkedMapOf<java.time.Instant, Double>()
    hourly.timestamps.forEachIndexed { index, timestamp ->
        if (timestamp < start || timestamp >= end) return@forEachIndexed
        ForecastPhysicalLimits.temperature(hourly.apparentTemperature.getOrNull(index))
            ?.let { feelsLike.putIfAbsent(timestamp, it) }
        ForecastPhysicalLimits.pressure(hourly.pressureMsl.getOrNull(index))
            ?.let { pressure.putIfAbsent(timestamp, it) }
    }
    // Require at least half a local calendar day (23/24/25h across DST).
    // Values are deduplicated separately *after validation* to avoid an
    // incomplete early duplicate masking a valid later sample of either field.
    val required = kotlin.math.ceil((end.epochSecond - start.epochSecond) / 7200.0).toInt()
    return DailyAtmosphericMetrics(
        apparentMinC = feelsLike.values.takeIf { it.size >= required }?.minOrNull(),
        apparentMaxC = feelsLike.values.takeIf { it.size >= required }?.maxOrNull(),
        meanPressureHpa = pressure.values.takeIf { it.size >= required }?.average()
    )
}
