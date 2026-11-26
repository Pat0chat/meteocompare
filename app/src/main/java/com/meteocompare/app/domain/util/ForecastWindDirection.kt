package com.meteocompare.app.domain.util

import com.meteocompare.app.core.util.meanWindDirection
import com.meteocompare.app.domain.model.CityForecast
import java.time.Instant
import java.time.LocalDate

/**
 * Wind direction is the meteorological origin (where it comes FROM).
 * Reuses already downloaded forecast series; there is no I/O here.
 * Keep only engines reporting a valid wind speed for the same date/hour.
 */
fun forecastDailyWindDirection(forecast: CityForecast, date: LocalDate): Int? =
    meanWindDirection(forecast.seriesByModel.values.mapNotNull { series ->
        val index = series.daily.dates.indexOf(date)
        val speed = series.daily.windSpeedMax.getOrNull(index)
        if (speed == null || !speed.isFinite() || speed <= 0.0) null
        else series.daily.windDirection10mDominant.getOrNull(index)
    })

fun forecastHourlyWindDirection(forecast: CityForecast, timestamp: Instant): Int? =
    meanWindDirection(forecast.seriesByModel.values.mapNotNull { series ->
        val index = series.hourly.indexOfTimestamp(timestamp)
        val speed = series.hourly.windSpeed10m.getOrNull(index)
        if (speed == null || !speed.isFinite() || speed <= 0.0) null
        else series.hourly.windDirection10m.getOrNull(index)
    })
