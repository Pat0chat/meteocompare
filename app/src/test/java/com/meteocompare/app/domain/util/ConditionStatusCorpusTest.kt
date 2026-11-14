package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Frozen expectations designed independently of the implementation, across DST and gaps. */
class ConditionStatusCorpusTest {
    @Test fun replayStatusGrid() {
        val resource = requireNotNull(javaClass.getResourceAsStream("/weather_regression/v2/day_status.tsv"))
        var cases = 0
        resource.bufferedReader().use { reader ->
            val header = reader.readLine().split('\t')
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val row = header.zip(line.split('\t')).toMap()
                val id = row.getValue("id")
                val zone = ZoneId.of(row.getValue("zone"))
                val date = LocalDate.parse(row.getValue("date"))
                val start = date.atStartOfDay(zone).toInstant()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant()
                val n = ((end.epochSecond - start.epochSecond) / 3600).toInt()
                assertEquals("$id actual DST hours", row.getValue("total_hours").toInt(), n)
                val k = row.getValue("known_hours").toInt()
                val times = List(n) { start.plusSeconds(it * 3600L) }
                val native = row.getValue("source") == "native"
                val wmo = row.getValue("wmo").toInt()
                val series = ForecastSeries(WeatherModel.GFS,
                    HourlyForecast(
                        timestamps = times,
                        temperature2m = if (native) emptyList() else List(n) { 10.0 },
                        precipitation = if (native) emptyList() else List(n) {
                            if (it < k && wmo == 61) 5.5 else 0.0
                        },
                        windSpeed10m = emptyList(),
                        weatherCode = if (native) List(n) { if (it < k) wmo else null } else emptyList(),
                        cloudCover = if (native) emptyList() else List(n) {
                            if (it < k && wmo == 0) 0 else null
                        }
                    ),
                    DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
                val result = ConditionDecisionEngine.periodBetween(series, start, end)
                val expected = row.getValue("expected_condition").takeUnless { it == "_" }
                assertEquals("$id condition", expected, result.condition?.name)
                assertEquals("$id status", row.getValue("expected_status"), result.status.name)
                assertEquals("$id coverage", k.toDouble() / n, result.coverage)
                assertEquals("$id origin", row.getValue("expected_origin"), result.origin.name)
                cases++
            }
        }
        assertEquals("Status corpus unexpectedly shrank", true, cases >= 1200)
    }
}
