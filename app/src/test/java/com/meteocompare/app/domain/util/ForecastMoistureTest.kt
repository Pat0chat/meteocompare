package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.DailyForecast
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastMoistureTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val autumn = LocalDate.of(2026, 10, 25)
    private val start = autumn.atStartOfDay(zone).toInstant()

    private fun series(hours: Int, timestamps: List<java.time.Instant> =
        List(hours) { start.plusSeconds(it * 3600L) }): ForecastSeries {
        val hourly = HourlyForecast(
            timestamps = timestamps,
            temperature2m = List(hours) { 14.0 },
            precipitation = List(hours) { 0.0 },
            windSpeed10m = List(hours) { 4.0 },
            relativeHumidity2m = List(hours) { 75 },
            dewPoint2m = List(hours) { 9.5 }
        )
        return ForecastSeries(
            WeatherModel.GFS, hourly,
            DailyForecast(listOf(autumn), listOf(16.0), listOf(10.0), listOf(0.0), listOf(7.0))
        )
    }

    @Test fun fullDstDayUses25HoursWithoutLosingRepeatedLocalClockHour() {
        val moisture = series(25).dailyMoisture(autumn, zone)
        assertEquals(75.0, moisture.humidityPercent!!, 0.001)
        assertEquals(9.5, moisture.dewPointC!!, 0.001)
    }

    @Test fun incompleteDayIsNotPresentedAsDailyAverage() {
        val moisture = series(12).dailyMoisture(autumn, zone)
        assertNull(moisture.humidityPercent)
        assertNull(moisture.dewPointC)
    }

    @Test fun repeatedAbsoluteTimestampsAreNotCountedTwice() {
        val duplicates = List(24) { start.plusSeconds((it / 2).toLong() * 3600L) }
        val moisture = series(24, duplicates).dailyMoisture(autumn, zone)
        assertNull(moisture.humidityPercent)
        assertNull(moisture.dewPointC)
    }
    @Test fun validLaterDuplicateCanSupplyMissingMoistureMetric() {
        val validTimes = List(25) { start.plusSeconds(it * 3600L) }
        val allTimes = validTimes + validTimes
        val source = series(50, allTimes)
        val updated = source.copy(hourly = source.hourly.copy(
            relativeHumidity2m = List(25) { null } + List(25) { 75 },
            dewPoint2m = List(25) { null } + List(25) { 9.5 }
        ))
        val result = updated.dailyMoisture(autumn, zone)
        assertEquals(75.0, result.humidityPercent ?: error("humidity missing"), 1e-9)
        assertEquals(9.5, result.dewPointC ?: error("dew point missing"), 1e-9)
    }

}
