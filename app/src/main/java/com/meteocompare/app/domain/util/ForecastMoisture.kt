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
    val humidity = mutableListOf<Int>()
    val dewPoint = mutableListOf<Double>()
    val seen = hashSetOf<java.time.Instant>()
    hourly.timestamps.forEachIndexed { i, time ->
        if (time < begin || time >= end || !seen.add(time)) return@forEachIndexed
        hourly.relativeHumidity2m.getOrNull(i)?.let(ForecastPhysicalLimits::percentage)?.let(humidity::add)
        hourly.dewPoint2m.getOrNull(i)?.let(ForecastPhysicalLimits::dewPoint)?.let(dewPoint::add)
    }
    return DailyMoisture(
        humidityPercent = humidity.takeIf { it.size.toDouble() >= required }?.average(),
        dewPointC = dewPoint.takeIf { it.size.toDouble() >= required }?.average()
    )
}
