package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyAtmosphericMetricsTest {
    @Test fun dailyExtremesAndMeanUseLocalDayOnly() {
        val date = LocalDate.of(2026, 7, 23)
        val zone = ZoneId.of("Europe/Paris")
        val start = date.atStartOfDay(zone).toInstant()
        val times = (-2..25).map { start.plusSeconds(it * 3600L) }
        val apparent = times.indices.map { index -> if (index == 0) 70.0 else (index - 2).toDouble() }
        val pressure = times.indices.map { 1000.0 + it }
        val series = ForecastSeries(WeatherModel.GFS, HourlyForecast(
            timestamps = times, temperature2m = emptyList(),
            precipitation = emptyList(), windSpeed10m = emptyList(),
            apparentTemperature = apparent, pressureMsl = pressure
        ), DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        val daily = series.dailyAtmosphericMetrics(date, zone)
        assertEquals(0.0, daily.apparentMinC!!, 0.001)
        assertEquals(23.0, daily.apparentMaxC!!, 0.001)
        assertEquals(1013.5, daily.meanPressureHpa!!, 0.001)
    }

    @Test fun sparseHoursAreNotMisrepresentedAsFullDayExtremes() {
        val date = LocalDate.of(2026, 7, 23)
        val series = ForecastSeries(WeatherModel.GFS, HourlyForecast(
            timestamps = listOf(date.atStartOfDay(ZoneId.of("UTC")).toInstant()),
            temperature2m = emptyList(), precipitation = emptyList(), windSpeed10m = emptyList(),
            apparentTemperature = listOf(25.0), pressureMsl = listOf(1013.0)
        ), DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        val daily = series.dailyAtmosphericMetrics(date, ZoneId.of("UTC"))
        assertNull(daily.apparentMinC)
        assertNull(daily.apparentMaxC)
        assertNull(daily.meanPressureHpa)
    }
    @Test fun invalidFirstDuplicateCannotMaskValidPressureOrApparentTemperature() {
        val date = LocalDate.of(2026, 10, 25)
        val zone = ZoneId.of("Europe/Paris")
        val start = date.atStartOfDay(zone).toInstant()
        val hours = List(25) { start.plusSeconds(it * 3600L) }
        val series = ForecastSeries(WeatherModel.GFS, HourlyForecast(
            timestamps = hours + hours,
            temperature2m = emptyList(), precipitation = emptyList(), windSpeed10m = emptyList(),
            apparentTemperature = List(25) { Double.NaN } + List(25) { 15.0 },
            pressureMsl = List(25) { 2000.0 } + List(25) { 1012.0 }
        ), DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        val daily = series.dailyAtmosphericMetrics(date, zone)
        assertEquals(15.0, daily.apparentMinC ?: error("apparent temp missing"), 1e-9)
        assertEquals(15.0, daily.apparentMaxC ?: error("apparent temp missing"), 1e-9)
        assertEquals(1012.0, daily.meanPressureHpa ?: error("pressure missing"), 1e-9)
    }

}
