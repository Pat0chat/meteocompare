package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import java.util.Locale

/** A range is meaningful only with two valid forecast endpoints. Values stay in metric units. */
internal fun dailyTimelineRange(
    min: Double?, max: Double?, units: WeatherUnits, metricUnit: WeatherUnit,
    digits: Int = 0, locale: Locale = Locale.getDefault()
): String? {
    if (min == null || max == null || !min.isFinite() || !max.isFinite() || min > max) return null
    val low = units.value(min, metricUnit, digits, locale)
    val high = units.value(max, metricUnit, digits, locale)
    if (low == "—" || high == "—") return null
    return "($low–$high)"
}

/** Only compare distinct model forecasts; never present a single-model reading as a spread.
 * All endpoints remain canonical metric numbers until WeatherUnits formats them.
 */
internal enum class TimelineRangeMetric {
    HOURLY_TEMPERATURE, HOURLY_APPARENT_TEMPERATURE,
    HIGH_TEMPERATURE, LOW_TEMPERATURE, LOW_APPARENT_TEMPERATURE, HIGH_APPARENT_TEMPERATURE,
    RAIN_AMOUNT, RAIN_PROBABILITY,
    CLOUD_COVER, PRESSURE, HUMIDITY, DEW_POINT, WIND, WIND_GUST
}

internal fun timelineMetricRange(
    point: SimplifiedTimelinePoint,
    metric: TimelineRangeMetric,
    units: WeatherUnits,
    locale: Locale = Locale.getDefault()
): String? {
    // A point belongs to one time scale only. Daily extrema must never be
    // mistaken for the spread at an individual hour (or vice versa).
    val hourly = point.instant != null && point.date == null
    val daily = point.date != null && point.instant == null
    if (!hourly && !daily) return null
    when (metric) {
        TimelineRangeMetric.HOURLY_TEMPERATURE, TimelineRangeMetric.HOURLY_APPARENT_TEMPERATURE -> if (!hourly) return null
        TimelineRangeMetric.HIGH_TEMPERATURE, TimelineRangeMetric.LOW_TEMPERATURE,
        TimelineRangeMetric.LOW_APPARENT_TEMPERATURE, TimelineRangeMetric.HIGH_APPARENT_TEMPERATURE -> if (!daily) return null
        else -> Unit
    }
    val (low, high, modelCount) = when (metric) {
        TimelineRangeMetric.HOURLY_APPARENT_TEMPERATURE -> Triple(
            point.apparentTemperatureMinAcrossModels, point.apparentTemperatureMaxAcrossModels, point.apparentTemperatureModelCount)
        TimelineRangeMetric.HOURLY_TEMPERATURE, TimelineRangeMetric.HIGH_TEMPERATURE -> Triple(
            point.temperatureMinAcrossModels, point.temperatureMaxAcrossModels, point.temperatureModelCount)
        TimelineRangeMetric.LOW_TEMPERATURE -> Triple(
            point.tempMinMinAcrossModels, point.tempMinMaxAcrossModels, point.tempMinModelCount)
        TimelineRangeMetric.LOW_APPARENT_TEMPERATURE -> Triple(
            point.apparentMinMinAcrossModels, point.apparentMinMaxAcrossModels, point.apparentMinModelCount)
        TimelineRangeMetric.HIGH_APPARENT_TEMPERATURE -> Triple(
            point.apparentMaxMinAcrossModels, point.apparentMaxMaxAcrossModels, point.apparentMaxModelCount)
        TimelineRangeMetric.RAIN_AMOUNT -> Triple(
            point.precipitationMinAcrossModelsMm, point.precipitationMaxAcrossModelsMm, point.precipitationAmountModelCount)
        TimelineRangeMetric.RAIN_PROBABILITY -> Triple(
            point.precipitationProbabilityMin?.toDouble(), point.precipitationProbabilityMax?.toDouble(), point.precipitationProbabilityModelCount)
        TimelineRangeMetric.CLOUD_COVER -> Triple(
            point.cloudCoverMinAcrossModels?.toDouble(), point.cloudCoverMaxAcrossModels?.toDouble(), point.cloudCoverModelCount)
        TimelineRangeMetric.PRESSURE -> Triple(
            point.pressureMinAcrossModels, point.pressureMaxAcrossModels, point.pressureModelCount)
        TimelineRangeMetric.HUMIDITY -> Triple(
            point.humidityMinAcrossModels?.toDouble(), point.humidityMaxAcrossModels?.toDouble(), point.humidityModelCount)
        TimelineRangeMetric.DEW_POINT -> Triple(
            point.dewPointMinAcrossModels, point.dewPointMaxAcrossModels, point.dewPointModelCount)
        TimelineRangeMetric.WIND -> Triple(
            point.windMinAcrossModels, point.windMaxAcrossModels, point.windModelCount)
        TimelineRangeMetric.WIND_GUST -> Triple(
            point.windGustMinAcrossModels, point.windGustMaxAcrossModels, point.windGustModelCount)
    }
    if (modelCount < 2) return null
    val unit = when (metric) {
        TimelineRangeMetric.HOURLY_TEMPERATURE, TimelineRangeMetric.HOURLY_APPARENT_TEMPERATURE,
        TimelineRangeMetric.HIGH_TEMPERATURE, TimelineRangeMetric.LOW_TEMPERATURE,
        TimelineRangeMetric.LOW_APPARENT_TEMPERATURE, TimelineRangeMetric.HIGH_APPARENT_TEMPERATURE,
        TimelineRangeMetric.DEW_POINT -> WeatherUnit.TEMPERATURE_COMPACT
        TimelineRangeMetric.RAIN_AMOUNT -> WeatherUnit.PRECIPITATION
        TimelineRangeMetric.RAIN_PROBABILITY, TimelineRangeMetric.CLOUD_COVER,
        TimelineRangeMetric.HUMIDITY -> WeatherUnit.PERCENT
        TimelineRangeMetric.PRESSURE -> WeatherUnit.PRESSURE
        TimelineRangeMetric.WIND, TimelineRangeMetric.WIND_GUST -> WeatherUnit.WIND_SPEED
    }
    val digits = when (metric) {
        TimelineRangeMetric.RAIN_AMOUNT -> 1
        TimelineRangeMetric.PRESSURE -> if (units.pressureInInHg) 2 else 0
        else -> 0
    }
    val formatted = dailyTimelineRange(low, high, units, unit, digits, locale) ?: return null
    return if (unit == WeatherUnit.PERCENT) formatted.dropLast(1) + "%)" else formatted
}

/** Daily extrema, not a model-disagreement range. */
internal fun dailyApparentReading(point: SimplifiedTimelinePoint, units: WeatherUnits): String {
    val low = point.apparentMinC?.takeIf(Double::isFinite)
    val high = point.apparentMaxC?.takeIf(Double::isFinite)
    if (low == null && high == null) return "—"
    return "${units.temp(low)} / ${units.temp(high)}"
}
