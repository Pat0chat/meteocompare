package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.core.util.meanWindDirection
import com.meteocompare.app.domain.util.dailyAtmosphericMetrics
import com.meteocompare.app.domain.util.dailyMoisture
import com.meteocompare.app.domain.util.forecastDailyWindDirection
import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.domain.model.validUvIndexOrNull
import com.meteocompare.app.domain.util.forecastSolarWindow
import com.meteocompare.app.domain.util.ForecastSolarWindow
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.util.SolarTimes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/** A common *calendar date* is evaluated in each locality’s own timezone. */
data class LocalityDayMetrics(
    val date: LocalDate,
    val minC: Double?,
    val maxC: Double?,
    val precipitationMm: Double?,
    /** Consensus-estimated daily chance of rain, not a calibrated observed frequency. */
    val rainProbabilityPercent: Int?,
    /** Median of model daily cloud-cover means, evaluated in the locality timezone. */
    val cloudCoverPercent: Int?,
    val maxWindKmh: Double?,
    /** Cloud-adjusted *estimate* of clear-sky-equivalent hours, NOT measured sunshine. */
    val estimatedSunHours: Double?,
    /** Model convergence (not forecast accuracy or rain probability). */
    val agreementPercent: Int?,
    val modelCount: Int,
    val uvMax: Double? = null,
    val windDirectionDeg: Int? = null,
    val apparentMinC: Double? = null,
    val apparentMaxC: Double? = null,
    val pressureHpa: Double? = null,
    val humidityPercent: Int? = null,
    val dewPointC: Double? = null
)

/**
 * Uses the same robust daily consensus as the detail screen; no averaging of
 * independent model families is performed here for temperature/wind/rain.
 */
fun buildLocalityDayMetrics(
    forecast: CityForecast,
    date: LocalDate,
    calculator: ConfidenceCalculator,
    uv: UvForecast? = null
): LocalityDayMetrics? {
    if (forecast.seriesByModel.values.none { date in it.daily.dates }) return null
    val daily = calculator.dayConfidence(forecast, date)
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    val atmospheric = forecast.seriesByModel.values.map { it.dailyAtmosphericMetrics(date, zone) }
    val apparentMin = medianAvailable(atmospheric.mapNotNull { it.apparentMinC })
    val apparentMax = medianAvailable(atmospheric.mapNotNull { it.apparentMaxC })
    val pressure = medianAvailable(atmospheric.mapNotNull { it.meanPressureHpa })
    val moisture = forecast.seriesByModel.values.map { it.dailyMoisture(date, zone) }
    val humidity = medianAvailable(moisture.mapNotNull { it.humidityPercent })?.roundToInt()
    val dewPoint = medianAvailable(moisture.mapNotNull { it.dewPointC })
    val precipitation = daily.precipitation
    val centralRain = when (precipitation) {
        null -> null
        else -> precipitation.meta.centralAmountMm ?: when (precipitation) {
            is PrecipitationConfidence.NoRain -> 0.0
            is PrecipitationConfidence.Rain -> precipitation.meanMm
            is PrecipitationConfidence.Divided -> precipitation.rainMeanMm
        }
    }?.takeIf { it.isFinite() && it >= 0.0 }
    val count = listOfNotNull(
        daily.tempMin?.modelCount,
        daily.tempMax?.modelCount,
        daily.windMax?.modelCount,
        precipitation?.modelCount
    ).maxOrNull() ?: 0
    return LocalityDayMetrics(
        date = date,
        minC = daily.tempMin?.centralValue?.takeIf(Double::isFinite),
        maxC = daily.tempMax?.centralValue?.takeIf(Double::isFinite),
        precipitationMm = centralRain,
        rainProbabilityPercent = precipitation?.meta?.probabilityPercent?.takeIf { it in 0..100 },
        cloudCoverPercent = localityDailyCloudCoverPercent(forecast, date),
        maxWindKmh = daily.windMax?.centralValue?.takeIf(Double::isFinite),
        estimatedSunHours = estimatedSunshineHours(forecast, date),
        agreementPercent = daily.convergencePercent?.coerceIn(0, 100),
        modelCount = count,
        uvMax = uv?.maxOn(date)?.validUvIndexOrNull(),
        windDirectionDeg = forecastDailyWindDirection(forecast, date),
        apparentMinC = apparentMin,
        apparentMaxC = apparentMax,
        pressureHpa = pressure,
        humidityPercent = humidity,
        dewPointC = dewPoint
    )
}

/**
 * Median of the available per-model daily cloud-cover averages. Require at
 * least half of each locality's calendar day (23/24/25 hours around DST) so
 * a single hourly observation never stands in for a daily percentage.
 * Duplicate/invalid samples and models without this day are ignored.
 */
internal fun localityDailyCloudCoverPercent(forecast: CityForecast, date: LocalDate): Int? {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val dayHours = (end.epochSecond - start.epochSecond) / 3600.0
    if (dayHours <= 0.0) return null

    val means = forecast.seriesByModel.values.mapNotNull { series ->
        if (date !in series.daily.dates) return@mapNotNull null
        val seen = hashSetOf<Instant>()
        val valid = series.hourly.timestamps.mapIndexedNotNull { index, timestamp ->
            if (timestamp < start || timestamp >= end) return@mapIndexedNotNull null
            val clouds = series.hourly.cloudCover.getOrNull(index)?.takeIf { it in 0..100 }
                ?: return@mapIndexedNotNull null
            clouds.takeIf { seen.add(timestamp) }
        }
        if (valid.size < dayHours / 2.0) null else valid.average()
    }.sorted()
    if (means.isEmpty()) return null
    val middle = means.size / 2
    val median = if (means.size % 2 == 1) means[middle]
    else (means[middle - 1] + means[middle]) / 2.0
    return median.roundToInt().coerceIn(0, 100)
}

/** Median of independent model estimates avoids over-weighting a dense hourly grid. */
private fun estimatedSunshineHours(forecast: CityForecast, date: LocalDate): Double? {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    // One NOAA fallback per city/date, not one expensive astronomical calculation
    // per model lacking API sunrise/sunset. Individual API windows retain priority.
    val fallbackWindow: Pair<Instant, Instant>? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            SolarTimes.compute(forecast.city.latitude, forecast.city.longitude, date, zone)
        }.getOrNull()?.let { solar ->
            val rise = solar.sunrise?.let { date.atTime(it).atZone(zone).toInstant() }
            val set = solar.sunset?.let { date.atTime(it).atZone(zone).toInstant() }
            if (rise != null && set != null && set > rise) rise to set else null
        }
    }
    val hours = forecast.seriesByModel.values.mapNotNull { series ->
        val day = series.daily.dates.indexOf(date)
        if (day < 0) return@mapNotNull null
        val apiRise = series.daily.sunrise.getOrNull(day)
        val apiSet = series.daily.sunset.getOrNull(day)
        val (rise, set) = if (apiRise != null && apiSet != null && apiSet > apiRise) {
            apiRise to apiSet
        } else fallbackWindow ?: return@mapNotNull null
        estimateCloudAdjustedDaylight(
            rise, set, series.hourly.timestamps, series.hourly.cloudCover
        )
    }.sorted()
    if (hours.isEmpty()) return null
    val middle = hours.size / 2
    return if (hours.size % 2 == 1) hours[middle]
    else (hours[middle - 1] + hours[middle]) / 2.0
}

/**
 * Hourly cloud cover is interpreted as applying to [hour, hour+1h). Only
 * overlapping daylight contributes. Reject partial data (< 50% daylight)
 * instead of presenting an artificially sunny/overcast full day.
 */
fun estimateCloudAdjustedDaylight(
    sunrise: Instant,
    sunset: Instant,
    timestamps: List<Instant>,
    cloudCover: List<Int?>
): Double? {
    if (sunset <= sunrise) return null
    val daylightSeconds = (sunset.epochSecond - sunrise.epochSecond).toDouble()
    if (daylightSeconds <= 0.0 || daylightSeconds > 24.0 * 3600.0) return null
    var coveredSeconds = 0.0
    var clearSeconds = 0.0
    // Only use unique hours; duplicate timestamps cannot double-count coverage.
    val seen = hashSetOf<Instant>()
    for (index in 0 until minOf(timestamps.size, cloudCover.size)) {
        val start = timestamps[index]
        val clouds = cloudCover[index] ?: continue
        if (clouds !in 0..100 || !seen.add(start)) continue
        val end = start.plusSeconds(3600)
        val overlap = max(0L, minOf(end.epochSecond, sunset.epochSecond) -
            maxOf(start.epochSecond, sunrise.epochSecond)).toDouble()
        coveredSeconds += overlap
        clearSeconds += overlap * (100 - clouds) / 100.0
    }
    if (coveredSeconds < daylightSeconds * 0.5) return null
    return (daylightSeconds / 3600.0 * clearSeconds / coveredSeconds).coerceIn(0.0, 24.0)
}


/** Values for a single **local wall-clock hour**, not an absolute global instant.
 * All cities show the same clock hour on the selected date. DST gaps have no
 * value; at an autumn overlap the earlier occurrence is used consistently.
 */
data class LocalityHourMetrics(
    val instant: Instant,
    val temperatureC: Double?,
    val precipitationMm: Double?,
    val rainProbabilityPercent: Int?,
    val cloudCoverPercent: Int?,
    val windKmh: Double?,
    val estimatedSunHours: Double?,
    /** Temperature-model convergence at the selected exact instant. */
    val agreementPercent: Int?,
    val uvIndex: Double?,
    val modelCount: Int,
    val windDirectionDeg: Int? = null,
    val apparentTemperatureC: Double? = null,
    val pressureHpa: Double? = null,
    val humidityPercent: Int? = null,
    val dewPointC: Double? = null
)

/** No implicit DST gap-shifting: e.g. 02:00 on a spring-forward day is missing. */
fun localityHourInstant(date: LocalDate, hour: Int, zone: ZoneId): Instant? {
    if (hour !in 0..23) return null
    val time = LocalDateTime.of(date.year, date.monthValue, date.dayOfMonth, hour, 0)
    val offsets = zone.rules.getValidOffsets(time)
    return offsets.firstOrNull()?.let(time::toInstant)
}

private fun medianAvailable(values: List<Double>): Double? {
    val sorted = values.filter(Double::isFinite).sorted()
    if (sorted.isEmpty()) return null
    val i = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[i - 1] + sorted[i]) / 2 else sorted[i]
}

/**
 * Model-median values at the *exact* hourly timestamp. Missing fields do not
 * become zero; UV comes from the same independent Open-Meteo UV product used
 * by Home/Detail/Chart View, not from a fictitious model consensus.
 */
fun buildLocalityHourMetrics(
    forecast: CityForecast,
    date: LocalDate,
    hour: Int,
    calculator: ConfidenceCalculator,
    uv: UvForecast? = null,
    solarWindow: ForecastSolarWindow? = null
): LocalityHourMetrics? {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    val instant = localityHourInstant(date, hour, zone) ?: return null
    val samples = forecast.seriesByModel.values.mapNotNull { series ->
        val index = series.hourly.timestamps.indexOf(instant)
        if (index < 0) null else series.hourly to index
    }
    if (samples.isEmpty()) return null

    val temperature = medianAvailable(samples.mapNotNull { (data, i) ->
        data.temperature2m.getOrNull(i)?.takeIf(Double::isFinite)
    })
    val apparent = medianAvailable(samples.mapNotNull { (data, i) ->
        data.apparentTemperature.getOrNull(i)?.takeIf(Double::isFinite)
    })
    val pressure = medianAvailable(samples.mapNotNull { (data, i) ->
        data.pressureMsl.getOrNull(i)?.takeIf(Double::isFinite)
    })
    val humidity = medianAvailable(samples.mapNotNull { (data, i) ->
        data.relativeHumidity2m.getOrNull(i)?.takeIf { it in 0..100 }?.toDouble()
    })?.roundToInt()
    val dewPoint = medianAvailable(samples.mapNotNull { (data, i) ->
        data.dewPoint2m.getOrNull(i)?.takeIf(Double::isFinite)
    })
    val rain = medianAvailable(samples.mapNotNull { (data, i) ->
        data.precipitation.getOrNull(i)?.takeIf { it.isFinite() && it >= 0.0 }
    })
    val probability = medianAvailable(samples.mapNotNull { (data, i) ->
        data.precipitationProbability.getOrNull(i)?.takeIf { it in 0..100 }?.toDouble()
    })?.roundToInt()
    val clouds = medianAvailable(samples.mapNotNull { (data, i) ->
        data.cloudCover.getOrNull(i)?.takeIf { it in 0..100 }?.toDouble()
    })?.roundToInt()
    val wind = medianAvailable(samples.mapNotNull { (data, i) ->
        data.windSpeed10m.getOrNull(i)?.takeIf { it.isFinite() && it >= 0.0 }
    })
    val windDirection = meanWindDirection(samples.mapNotNull { (data, i) ->
        if (data.windSpeed10m.getOrNull(i)?.takeIf { it.isFinite() && it > 0.0 } == null) null
        else data.windDirection10m.getOrNull(i)
    })
    val window = solarWindow ?: forecastSolarWindow(forecast, date)
    val sunHours = if (clouds != null && window.isValid) {
        val sunrise = requireNotNull(window.sunrise)
        val sunset = requireNotNull(window.sunset)
        val start = maxOf(instant, sunrise)
        val end = minOf(instant.plusSeconds(3600), sunset)
        val seconds = maxOf(0L, end.epochSecond - start.epochSecond)
        (seconds / 3600.0 * (100 - clouds) / 100.0).coerceIn(0.0, 1.0)
    } else null
    return LocalityHourMetrics(
        instant = instant,
        temperatureC = temperature,
        precipitationMm = rain,
        rainProbabilityPercent = probability,
        cloudCoverPercent = clouds,
        windKmh = wind,
        estimatedSunHours = sunHours,
        agreementPercent = calculator.hourlyTemperatureAgreementAt(forecast, instant),
        // An IANA local hour can fall at :15/:30/:45 UTC; do not truncate UTC hours.
        uvIndex = uv?.hourly?.get(instant)?.validUvIndexOrNull(),
        modelCount = samples.size,
        windDirectionDeg = windDirection,
        apparentTemperatureC = apparent,
        pressureHpa = pressure,
        humidityPercent = humidity,
        dewPointC = dewPoint
    )
}
