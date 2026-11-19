package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Independently generated v3 UTC time intervals, including DST and half-hour zones. */
class TemporalWindowsCorpusTest {
    @Test fun physicalHourlyEvidenceMatchesReviewedUtcCorpus() {
        val stream = javaClass.classLoader!!.getResourceAsStream(
            "weather_regression/v3/temporal_windows.tsv"
        ) ?: error("Missing physical-time regression corpus")
        val rows = stream.bufferedReader().use { it.readLines().drop(1) }
        assertEquals(300, rows.size)
        rows.forEach { row ->
            val cells = row.split('\t')
            val id = cells[0]
            val h = Instant.parse(cells[4])
            val dayStart = Instant.parse(cells[7])
            val dayEnd = Instant.parse(cells[8])
            val precipitation = cells[3] == "RAIN"
            val series = ForecastSeries(
                WeatherModel.GFS,
                HourlyForecast(
                    timestamps = listOf(h),
                    temperature2m = listOf(12.0),
                    precipitation = listOf(if (precipitation) 2.0 else 0.0),
                    windSpeed10m = emptyList(),
                    weatherCode = listOf(if (precipitation) null else 0)
                ),
                DailyForecast(emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
            )
            val hourly = ConditionDecisionEngine.hourly(series.hourly, 0)
            assertEquals("$id: start", Instant.parse(cells[5]), hourly.timeWindow?.start)
            assertEquals("$id: end", Instant.parse(cells[6]), hourly.timeWindow?.endExclusive)
            val daily = ConditionDecisionEngine.periodBetween(series, dayStart, dayEnd)
            val duration = Duration.between(dayStart,dayEnd).seconds
            assertEquals("$id: overlap", cells[9].toLong().toDouble() / duration,
                daily.coverage, 1e-9)
        }
    }
}
