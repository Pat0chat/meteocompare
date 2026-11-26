package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.ForecastSeries
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * Checks *every physical hourly slot* of a civil day, not just the number of samples.
 * Instantaneous temperature and wind are stamped at the beginning of their slot:
 * [dayStart, dayEnd). Open-Meteo precipitation is stamped at the END of its
 * accumulation interval, hence (dayStart, dayEnd].
 *
 * ZoneId supplies the correct 23/24/25 real hours on DST transition days. Using
 * Instant rather than local wall-clock strings also preserves repeated hours and
 * non-integer UTC offsets. An invalid duplicate cannot mask a valid observation.
 */
internal fun ForecastSeries.hasCompleteCivilDayHourlyCoverage(
    date: LocalDate,
    variable: ForecastEvolutionVariable,
    zone: ZoneId
): Boolean {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val duration = Duration.between(start, end)
    val hours = duration.toHours()
    if (hours !in 23L..25L || duration != Duration.ofHours(hours)) return false

    val first = if (variable == ForecastEvolutionVariable.PRECIPITATION)
        start.plusSeconds(3_600L) else start
    val last = first.plusSeconds((hours - 1L) * 3_600L)
    // Keep only this day's physical instants, not all days of every model.
    // Invalid duplicates are still ignored rather than masking valid rows.
    val validInstants = hourly.timestamps.indices.asSequence().filter { index ->
        val instant = hourly.timestamps[index]
        if (instant < first || instant > last) return@filter false
        when (variable) {
            ForecastEvolutionVariable.TEMPERATURE ->
                ForecastPhysicalLimits.temperature(hourly.temperature2m.getOrNull(index)) != null
            ForecastEvolutionVariable.PRECIPITATION ->
                ForecastPhysicalLimits.precipitation(hourly.precipitation.getOrNull(index)) != null
            ForecastEvolutionVariable.WIND ->
                ForecastPhysicalLimits.wind(hourly.windSpeed10m.getOrNull(index)) != null
        }
    }.map { hourly.timestamps[it] }.toSet()

    return (0L until hours).all { offset ->
        first.plusSeconds(offset * 3_600L) in validInstants
    }
}
