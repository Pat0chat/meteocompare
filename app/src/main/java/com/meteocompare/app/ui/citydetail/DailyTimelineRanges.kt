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
internal enum class DailyRangeMetric {
    HIGH_TEMPERATURE, LOW_TEMPERATURE, LOW_APPARENT_TEMPERATURE, HIGH_APPARENT_TEMPERATURE, RAIN_AMOUNT, RAIN_PROBABILITY,
    CLOUD_COVER, PRESSURE, HUMIDITY, DEW_POINT, WIND, WIND_GUST
}

internal fun dailyMetricRange(
    point: SimplifiedTimelinePoint,
    metric: DailyRangeMetric,
    units: WeatherUnits,
    locale: Locale = Locale.getDefault()
): String? {
    if (point.date == null) return null // hourly timeline must never show daily spreads
    val (low, high, modelCount) = when (metric) {
        DailyRangeMetric.HIGH_TEMPERATURE -> Triple(
            point.temperatureMinAcrossModels, point.temperatureMaxAcrossModels, point.temperatureModelCount)
        DailyRangeMetric.LOW_TEMPERATURE -> Triple(
            point.tempMinMinAcrossModels, point.tempMinMaxAcrossModels, point.tempMinModelCount)
        DailyRangeMetric.LOW_APPARENT_TEMPERATURE -> Triple(
            point.apparentMinMinAcrossModels, point.apparentMinMaxAcrossModels, point.apparentMinModelCount)
        DailyRangeMetric.HIGH_APPARENT_TEMPERATURE -> Triple(
            point.apparentMaxMinAcrossModels, point.apparentMaxMaxAcrossModels, point.apparentMaxModelCount)
        DailyRangeMetric.RAIN_AMOUNT -> Triple(
            point.precipitationMinAcrossModelsMm, point.precipitationMaxAcrossModelsMm, point.precipitationAmountModelCount)
        DailyRangeMetric.RAIN_PROBABILITY -> Triple(
            point.precipitationProbabilityMin?.toDouble(), point.precipitationProbabilityMax?.toDouble(), point.precipitationProbabilityModelCount)
        DailyRangeMetric.CLOUD_COVER -> Triple(
            point.cloudCoverMinAcrossModels?.toDouble(), point.cloudCoverMaxAcrossModels?.toDouble(), point.cloudCoverModelCount)
        DailyRangeMetric.PRESSURE -> Triple(
            point.pressureMinAcrossModels, point.pressureMaxAcrossModels, point.pressureModelCount)
        DailyRangeMetric.HUMIDITY -> Triple(
            point.humidityMinAcrossModels?.toDouble(), point.humidityMaxAcrossModels?.toDouble(), point.humidityModelCount)
        DailyRangeMetric.DEW_POINT -> Triple(
            point.dewPointMinAcrossModels, point.dewPointMaxAcrossModels, point.dewPointModelCount)
        DailyRangeMetric.WIND -> Triple(
            point.windMinAcrossModels, point.windMaxAcrossModels, point.windModelCount)
        DailyRangeMetric.WIND_GUST -> Triple(
            point.windGustMinAcrossModels, point.windGustMaxAcrossModels, point.windGustModelCount)
    }
    if (modelCount < 2) return null
    val unit = when (metric) {
        DailyRangeMetric.HIGH_TEMPERATURE, DailyRangeMetric.LOW_TEMPERATURE,
        DailyRangeMetric.LOW_APPARENT_TEMPERATURE, DailyRangeMetric.HIGH_APPARENT_TEMPERATURE,
        DailyRangeMetric.DEW_POINT -> WeatherUnit.TEMPERATURE_COMPACT
        DailyRangeMetric.RAIN_AMOUNT -> WeatherUnit.PRECIPITATION
        DailyRangeMetric.RAIN_PROBABILITY, DailyRangeMetric.CLOUD_COVER,
        DailyRangeMetric.HUMIDITY -> WeatherUnit.PERCENT
        DailyRangeMetric.PRESSURE -> WeatherUnit.PRESSURE
        DailyRangeMetric.WIND, DailyRangeMetric.WIND_GUST -> WeatherUnit.WIND_SPEED
    }
    val digits = when (metric) {
        DailyRangeMetric.RAIN_AMOUNT -> 1
        DailyRangeMetric.PRESSURE -> if (units.pressureInInHg) 2 else 0
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
