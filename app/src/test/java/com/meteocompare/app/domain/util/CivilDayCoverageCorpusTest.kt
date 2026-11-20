package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Independently generated Python/zoneinfo fixture, checked against Kotlin's java.time. */
class CivilDayCoverageCorpusTest {
    @Test fun pythonZoneinfoCorpusMatchesPhysicalHourCoverage() {
        val stream = javaClass.classLoader!!.getResourceAsStream(
            "weather_regression/v3/civil_day_coverage.tsv"
        ) ?: error("Missing civil-day coverage corpus")
        val rows = stream.bufferedReader().use { it.readLines().drop(1) }
        assertEquals(357, rows.size)
        rows.forEach { row ->
            val fields = row.split('\t')
            require(fields.size == 9) { "Malformed corpus row: $row" }
            val id = fields[0]
            val zoneName = fields[1]
            val dayText = fields[2]
            val variableText = fields[3]
            val mutation = fields[4]
            val expectedText = fields[5]
            val hoursText = fields[6]
            val startText = fields[7]
            val endText = fields[8]
            val zone = ZoneId.of(zoneName)
            val day = LocalDate.parse(dayText)
            val start = Instant.parse(startText)
            val end = Instant.parse(endText)
            assertEquals("$id: day start", day.atStartOfDay(zone).toInstant(), start)
            assertEquals("$id: day end", day.plusDays(1).atStartOfDay(zone).toInstant(), end)
            assertEquals("$id: day duration", hoursText.toDouble(),
                java.time.Duration.between(start, end).seconds / 3600.0, 1e-9)
            val hours = java.time.Duration.between(start, end).toHours().toInt()
            val stamps = (0..hours).map { start.plusSeconds(it * 3_600L) }.toMutableList()
            val temperature = MutableList<Double?>(stamps.size) { 17.0 }
            val precipitation = MutableList<Double?>(stamps.size) { 0.5 }
            val wind = MutableList<Double?>(stamps.size) { 12.0 }
            val mid = stamps.size / 2
            when (mutation) {
                "COMPLETE" -> Unit
                "REMOVE_FIRST", "REMOVE_LAST", "REMOVE_MIDDLE" -> {
                    val index = when (mutation) {
                        "REMOVE_FIRST" -> 0
                        "REMOVE_LAST" -> stamps.lastIndex
                        else -> mid
                    }
                    stamps.removeAt(index)
                    temperature.removeAt(index)
                    precipitation.removeAt(index)
                    wind.removeAt(index)
                }
                "DUPLICATE_MIDDLE" -> stamps[mid] = stamps[mid - 1]
                "INVALID_MIDDLE" -> when (variableText) {
                    "TEMPERATURE" -> temperature[mid] = Double.NaN
                    "PRECIPITATION" -> precipitation[mid] = -1.0
                    "WIND" -> wind[mid] = Double.POSITIVE_INFINITY
                }
                "OFFGRID_MIDDLE" -> stamps[mid] = stamps[mid].plusSeconds(30)
                else -> error("Unknown corpus mutation $mutation")
            }
            val source = ForecastSeries(WeatherModel.GFS, HourlyForecast(
                stamps, temperature, precipitation, wind
            ), DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
            val actual = source.hasCompleteCivilDayHourlyCoverage(
                day, ForecastEvolutionVariable.valueOf(variableText), zone
            )
            assertEquals("$id: expected coverage", expectedText == "true", actual)
        }
    }
}
