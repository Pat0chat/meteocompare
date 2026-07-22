package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** Point synthétique d'une chronologie de consensus multi-modèles. */
internal data class SimplifiedTimelinePoint(
    val instant: Instant? = null,
    val date: LocalDate? = null,
    val temperatureC: Double? = null,
    val tempMinC: Double? = null,
    val tempMaxC: Double? = null,
    val precipitationPercent: Int? = null,
    val windKmh: Double? = null,
    val condition: WeatherCondition? = null,
    val modelCount: Int,
    val isDivergent: Boolean
)

internal fun buildSimplifiedTimeline(
    forecast: CityForecast,
    mode: DisplayMode
): List<SimplifiedTimelinePoint> = when (mode) {
    DisplayMode.HOURLY -> buildHourlyTimeline(forecast)
    DisplayMode.DAILY -> buildDailyTimeline(forecast)
}

private fun buildHourlyTimeline(forecast: CityForecast): List<SimplifiedTimelinePoint> {
    val (startHour, endExclusive) = computeHourlyHorizon(forecast.city.timezone)
    val all = forecast.seriesByModel.values
        .flatMap { it.hourly.timestamps }
        .distinct()
        .sorted()
        .filter { it >= startHour && it < endExclusive }

    val candidates = all.take(16)
    val sampled = if (candidates.size <= 8) {
        candidates
    } else {
        candidates.filterIndexed { index, _ -> index % 2 == 0 }.take(8)
    }

    return sampled.mapNotNull { timestamp ->
        val snapshots = forecast.seriesByModel.values.mapNotNull seriesLoop@{ series ->
            val index = series.hourly.timestamps.indexOf(timestamp)
            if (index < 0) return@seriesLoop null
            val temperature = series.hourly.temperature2m.getOrNull(index)
            val precipitation = series.hourly.precipitation.getOrNull(index)
            val probability = series.hourly.precipitationProbability.getOrNull(index)
            val wind = series.hourly.windSpeed10m.getOrNull(index)
            val condition = WeatherCondition.fromWmoCode(series.hourly.weatherCode.getOrNull(index))
                ?: WeatherCondition.inferFromPrecipAndTemp(precipitation, temperature)
            TimelineSnapshot(temperature, null, null, precipitation, probability, wind, condition)
        }
        timelinePoint(timestamp = timestamp, date = null, snapshots = snapshots, hourly = true)
    }
}

private fun buildDailyTimeline(forecast: CityForecast): List<SimplifiedTimelinePoint> {
    val dates = forecast.seriesByModel.values
        .flatMap { it.daily.dates }
        .distinct()
        .sorted()
        .take(7)

    return dates.mapNotNull { date ->
        val snapshots = forecast.seriesByModel.values.mapNotNull seriesLoop@{ series ->
            val index = series.daily.dates.indexOf(date)
            if (index < 0) return@seriesLoop null
            val min = series.daily.tempMin.getOrNull(index)
            val max = series.daily.tempMax.getOrNull(index)
            val precipitation = series.daily.precipitationSum.getOrNull(index)
            val probability = series.daily.precipitationProbabilityMax.getOrNull(index)
            val wind = series.daily.windSpeedMax.getOrNull(index)
            val condition = WeatherCondition.fromWmoCode(series.daily.weatherCode.getOrNull(index))
                ?: WeatherCondition.inferFromPrecipAndTemp(precipitation, min)
            TimelineSnapshot(null, min, max, precipitation, probability, wind, condition)
        }
        timelinePoint(timestamp = null, date = date, snapshots = snapshots, hourly = false)
    }
}

private data class TimelineSnapshot(
    val temperature: Double?,
    val tempMin: Double?,
    val tempMax: Double?,
    val precipitation: Double?,
    val precipitationProbability: Int?,
    val wind: Double?,
    val condition: WeatherCondition?
)

private fun timelinePoint(
    timestamp: Instant?,
    date: LocalDate?,
    snapshots: List<TimelineSnapshot>,
    hourly: Boolean
): SimplifiedTimelinePoint? {
    if (snapshots.isEmpty()) return null

    val temperatures = snapshots.mapNotNull { it.temperature }
    val minTemperatures = snapshots.mapNotNull { it.tempMin }
    val maxTemperatures = snapshots.mapNotNull { it.tempMax }
    val precipitationValues = snapshots.mapNotNull { it.precipitation }
    val probabilities = snapshots.mapNotNull { it.precipitationProbability }
    val winds = snapshots.mapNotNull { it.wind }
    val conditions = snapshots.mapNotNull { it.condition }

    val conditionCounts = conditions.groupingBy { it }.eachCount()
    val consensusCondition = conditionCounts.entries
        .sortedWith(compareByDescending<Map.Entry<WeatherCondition, Int>> { it.value }
            .thenBy { it.key.ordinal })
        .firstOrNull()
        ?.key
    val conditionAgreement = conditionCounts.values.maxOrNull()?.let { top ->
        if (conditions.isEmpty()) 100 else (top * 100.0 / conditions.size).roundToInt()
    } ?: 100

    val rainThreshold = if (hourly) 0.1 else 0.2
    val wetVotes = precipitationValues.count { it >= rainThreshold }
    val wetShare = if (precipitationValues.isEmpty()) null
    else wetVotes * 100.0 / precipitationValues.size
    val precipitationPercent = when {
        probabilities.isNotEmpty() -> median(probabilities.map { it.toDouble() })?.roundToInt()
        wetShare != null -> wetShare.roundToInt()
        else -> null
    }?.coerceIn(0, 100)

    val temperatureSpread = when {
        hourly -> spread(temperatures)
        else -> maxOf(spread(minTemperatures), spread(maxTemperatures))
    }
    val windSpread = spread(winds)
    val probabilitySpread = if (probabilities.isEmpty()) 0.0
    else (probabilities.maxOrNull()!! - probabilities.minOrNull()!!).toDouble()
    val splitRain = wetShare != null && wetShare in 30.0..70.0

    val isDivergent = conditionAgreement < 60 ||
        temperatureSpread > (if (hourly) 4.0 else 5.0) ||
        windSpread > 20.0 ||
        probabilitySpread > 50.0 ||
        splitRain

    return SimplifiedTimelinePoint(
        instant = timestamp,
        date = date,
        temperatureC = median(temperatures),
        tempMinC = median(minTemperatures),
        tempMaxC = median(maxTemperatures),
        precipitationPercent = precipitationPercent,
        windKmh = median(winds),
        condition = consensusCondition,
        modelCount = snapshots.size,
        isDivergent = isDivergent
    )
}

private fun median(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle]
    else (sorted[middle - 1] + sorted[middle]) / 2.0
}

private fun spread(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    return (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
}
