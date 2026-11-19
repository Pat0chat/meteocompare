package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-zone, physically aligned coverage; never count only 23..25 samples. */
class ForecastEvolutionHourlyCoverageTest {
    private fun series(date: LocalDate, zone: ZoneId): ForecastSeries {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val hours = Duration.between(start, end).toHours().toInt()
        val instants = (0..hours).map { start.plusSeconds(it * 3_600L) }
        return ForecastSeries(WeatherModel.GFS, HourlyForecast(
            timestamps = instants,
            temperature2m = List(instants.size) { 18.0 },
            precipitation = List(instants.size) { 0.5 },
            windSpeed10m = List(instants.size) { 12.0 }
        ), DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
    }

    private val locales = mapOf(
        "Europe/Paris" to listOf("2026-03-29", "2026-10-25", "2026-11-13"),
        "America/New_York" to listOf("2026-03-08", "2026-11-01", "2026-11-13"),
        "Asia/Kolkata" to listOf("2026-11-13"),
        "Asia/Kathmandu" to listOf("2026-11-13"),
        "UTC" to listOf("2026-03-29", "2026-10-25")
    )

    @Test fun completeRealHoursAcrossDstAndFractionalOffsets() {
        locales.forEach { (name, dates) ->
            val zone = ZoneId.of(name)
            dates.forEach { day ->
                val date = LocalDate.parse(day)
                val source = series(date, zone)
                ForecastEvolutionVariable.entries.forEach { variable ->
                    assertTrue("$name $day $variable", source.hasCompleteCivilDayHourlyCoverage(date, variable, zone))
                }
            }
        }
    }

    @Test fun omittedNextMidnightOnlyInvalidatesDailyRain() {
        val zone = ZoneId.of("Europe/Paris")
        val date = LocalDate.parse("2026-10-25")
        val source = series(date, zone)
        val shortened = source.copy(hourly = source.hourly.copy(
            timestamps = source.hourly.timestamps.dropLast(1),
            temperature2m = source.hourly.temperature2m.dropLast(1),
            precipitation = source.hourly.precipitation.dropLast(1),
            windSpeed10m = source.hourly.windSpeed10m.dropLast(1)
        ))
        assertFalse(shortened.hasCompleteCivilDayHourlyCoverage(date, ForecastEvolutionVariable.PRECIPITATION, zone))
        assertTrue(shortened.hasCompleteCivilDayHourlyCoverage(date, ForecastEvolutionVariable.TEMPERATURE, zone))
        assertTrue(shortened.hasCompleteCivilDayHourlyCoverage(date, ForecastEvolutionVariable.WIND, zone))
    }

    @Test fun omittedStartingMidnightOnlyInvalidatesInstantaneousVariables() {
        val zone = ZoneId.of("Asia/Kolkata")
        val date = LocalDate.parse("2026-11-13")
        val source = series(date, zone)
        val shortened = source.copy(hourly = source.hourly.copy(
            timestamps = source.hourly.timestamps.drop(1),
            temperature2m = source.hourly.temperature2m.drop(1),
            precipitation = source.hourly.precipitation.drop(1),
            windSpeed10m = source.hourly.windSpeed10m.drop(1)
        ))
        assertTrue(shortened.hasCompleteCivilDayHourlyCoverage(date, ForecastEvolutionVariable.PRECIPITATION, zone))
        assertFalse(shortened.hasCompleteCivilDayHourlyCoverage(date, ForecastEvolutionVariable.TEMPERATURE, zone))
        assertFalse(shortened.hasCompleteCivilDayHourlyCoverage(date, ForecastEvolutionVariable.WIND, zone))
    }

    @Test fun duplicateTimestampCannotFillMissingPhysicalHour() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.parse("2026-11-01")
        val source = series(date, zone)
        val badHours = source.hourly.timestamps.toMutableList()
        badHours[4] = badHours[3]
        val duplicate = source.copy(hourly = source.hourly.copy(timestamps = badHours))
        ForecastEvolutionVariable.entries.forEach { variable ->
            assertFalse(duplicate.hasCompleteCivilDayHourlyCoverage(date, variable, zone))
        }
    }

    @Test fun physicalOutliersAndMissingHoursAreRejectedIndependently() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.parse("2026-11-13")
        val source = series(date, zone)
        val bad = source.copy(hourly = source.hourly.copy(
            temperature2m = source.hourly.temperature2m.toMutableList().also { it[10] = Double.NaN },
            precipitation = source.hourly.precipitation.toMutableList().also { it[24] = -1.0 },
            windSpeed10m = source.hourly.windSpeed10m.toMutableList().also { it[8] = 20_000.0 }
        ))
        ForecastEvolutionVariable.entries.forEach { variable ->
            assertFalse(bad.hasCompleteCivilDayHourlyCoverage(date, variable, zone))
        }
    }

    @Test fun missingOneOfTwentyFourSamplesNotMistakenForDstDay() {
        val zone = ZoneId.of("Europe/Paris")
        val date = LocalDate.parse("2026-11-13")
        val source = series(date, zone)
        val stamps = source.hourly.timestamps.toMutableList().apply { removeAt(12) }
        val broken = source.copy(hourly = source.hourly.copy(timestamps = stamps))
        ForecastEvolutionVariable.entries.forEach { variable ->
            assertFalse(broken.hasCompleteCivilDayHourlyCoverage(date, variable, zone))
        }
    }
}
