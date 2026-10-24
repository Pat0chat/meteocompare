package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
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
    val feelsLike = mutableListOf<Double>()
    val pressure = mutableListOf<Double>()
    val seen = hashSetOf<java.time.Instant>()
    hourly.timestamps.forEachIndexed { index, timestamp ->
        if (timestamp >= start && timestamp < end && seen.add(timestamp)) {
            hourly.apparentTemperature.getOrNull(index)?.takeIf { it.isFinite() }?.let(feelsLike::add)
            hourly.pressureMsl.getOrNull(index)?.takeIf { it.isFinite() }?.let(pressure::add)
        }
    }
    // Require at least half a local calendar day (23/24/25h across DST).
    // A partial forecast should not imply a full-day minimum, maximum or mean.
    val required = kotlin.math.ceil((end.epochSecond - start.epochSecond) / 7200.0).toInt()
    return DailyAtmosphericMetrics(
        apparentMinC = feelsLike.takeIf { it.size >= required }?.minOrNull(),
        apparentMaxC = feelsLike.takeIf { it.size >= required }?.maxOrNull(),
        meanPressureHpa = pressure.takeIf { it.size >= required }?.average()
    )
}
