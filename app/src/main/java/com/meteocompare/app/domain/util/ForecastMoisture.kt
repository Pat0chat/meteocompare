package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.ForecastSeries
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Per-model daily means, only if at least half of the local day's hours are covered.
 * Correct across 23/25-hour DST days and for models with incomplete humidity support.
 */
data class DailyMoisture(val humidityPercent: Double?, val dewPointC: Double?)

fun ForecastSeries.dailyMoisture(date: LocalDate, zone: ZoneId): DailyMoisture {
    val begin = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val required = ChronoUnit.HOURS.between(begin, end).toDouble() / 2.0
    // Dedupe each metric only after checking its value: a legacy cache may
    // repeat one instant with humidity in one row and dew point in another.
    // A missing/invalid duplicate must not mask a valid sample.
    val humidity = linkedMapOf<java.time.Instant, Int>()
    val dewPoint = linkedMapOf<java.time.Instant, Double>()
    hourly.timestamps.forEachIndexed { i, time ->
        if (time < begin || time >= end) return@forEachIndexed
        hourly.relativeHumidity2m.getOrNull(i)?.let(ForecastPhysicalLimits::percentage)
            ?.let { humidity.putIfAbsent(time, it) }
        hourly.dewPoint2m.getOrNull(i)?.let(ForecastPhysicalLimits::dewPoint)
            ?.let { dewPoint.putIfAbsent(time, it) }
    }
    return DailyMoisture(
        humidityPercent = humidity.values.takeIf { it.size.toDouble() >= required }?.average(),
        dewPointC = dewPoint.values.takeIf { it.size.toDouble() >= required }?.average()
    )
}
