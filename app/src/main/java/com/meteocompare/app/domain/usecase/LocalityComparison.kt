package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.core.util.meanWindDirection
import com.meteocompare.app.domain.util.dailyAtmosphericMetrics
import com.meteocompare.app.domain.util.dailyCloudCoverMean
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
fun estimatedSunshineHours(forecast: CityForecast, date: LocalDate): Double? {
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
    data class DailySolarSample(
        val model: com.meteocompare.app.domain.model.WeatherModel,
        val cloudMean: Int?,
        val sunshineHours: Double?
    )
    val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
        val day = series.daily.dates.indexOf(date)
        if (day < 0) return@mapNotNull null
        val apiRise = series.daily.sunrise.getOrNull(day)
        val apiSet = series.daily.sunset.getOrNull(day)
        val (rise, set) = if (apiRise != null && apiSet != null && apiSet > apiRise) {
            apiRise to apiSet
        } else fallbackWindow ?: return@mapNotNull null
        DailySolarSample(
            model = model,
            cloudMean = series.dailyCloudCoverMean(date, zone),
            sunshineHours = estimateCloudAdjustedDaylight(
                rise, set, series.hourly.timestamps, series.hourly.cloudCover,
                series.hourly.cloudCoverLow, series.hourly.cloudCoverMid,
                series.hourly.cloudCoverHigh, series.hourly.sunshineDuration,
                series.hourly.directNormalIrradiance
            )
        )
    }

    // Apply the same missing-solar safeguard to DAILY summaries. Otherwise a
    // single partly-cloudy model could provide the entire sunny-day median
    // while the rest predict near-total cloud cover without sunshine data.
    if ((localityDailyCloudCoverPercent(forecast, date) ?: 0) >= 90) {
        val cloudFamilies = samples.filter { it.cloudMean != null }
            .map { ForecastConsensus.groupFor(it.model) }.toSet()
        val overcastWithSun = samples.filter { item ->
            item.cloudMean != null && item.cloudMean >= 90 && item.sunshineHours != null
        }
        val solarFamilies = overcastWithSun.map { ForecastConsensus.groupFor(it.model) }.toSet()
        if (solarFamilies.isEmpty() ||
            (cloudFamilies.size > 1 && solarFamilies.size * 2 <= cloudFamilies.size)
        ) return null
        val perFamily = overcastWithSun.groupBy { ForecastConsensus.groupFor(it.model) }
            .values.map { group ->
                val sorted = group.mapNotNull { it.sunshineHours }.sorted()
                val middle = sorted.size / 2
                if (sorted.size % 2 == 1) sorted[middle]
                else (sorted[middle - 1] + sorted[middle]) / 2.0
            }.sorted()
        val middle = perFamily.size / 2
        return if (perFamily.size % 2 == 1) perFamily[middle]
        else (perFamily[middle - 1] + perFamily[middle]) / 2.0
    }

    val hours = samples.mapNotNull { it.sunshineHours }.sorted()
    if (hours.isEmpty()) return null
    val middle = hours.size / 2
    return if (hours.size % 2 == 1) hours[middle]
    else (hours[middle - 1] + hours[middle]) / 2.0
}

/**
 * A forecast sunshine fraction (NOT observed irradiance or measured sunshine).
 * Evidence order: Open-Meteo's forecast sunshine_duration (DNI above 120 W/m²),
 * direct-normal radiation, cloud-layer transmissivity, total cloud fraction.
 * Each successive fallback is less physically informative. Missing fields are
 * never interpreted as zero. With >=90% cloud cover only the provider's
 * sunshine_duration is used for minute counts; the hour-mean DNI and cloud
 * optical proxies cannot establish how many minutes of direct sun occurred.
 */
internal fun estimatedSunFraction(
    totalCloud: Int?, lowCloud: Int? = null, midCloud: Int? = null,
    highCloud: Int? = null, sunshineSeconds: Double? = null,
    directNormalWm2: Double? = null
): Double? {
    val seconds = sunshineSeconds?.takeIf { it.isFinite() && it in 0.0..3600.0 }
    if (seconds != null) return seconds / 3600.0

    // Neither high-cloud transmissivity nor hourly MEAN irradiance tells us
    // how many minutes passed the direct-sunshine threshold when cloud cover
    // is nearly total. A high DNI can still support the VEILED condition, but
    // only sunshine_duration is a defensible source for a minute count here.
    if (totalCloud != null && totalCloud in 90..100) return null

    val dni = directNormalWm2?.takeIf { it.isFinite() && it in 0.0..1500.0 }
    if (dni != null) {
        // Hour-averaged DNI cannot yield the exact number of sunny seconds.
        // This deliberately smooth proxy should not be called a WMO sunshine measurement.
        return ((dni - 120.0) / 250.0).coerceIn(0.0, 1.0)
    }

    val low = lowCloud?.takeIf { it in 0..100 }
    val mid = midCloud?.takeIf { it in 0..100 }
    val high = highCloud?.takeIf { it in 0..100 }
    if (low != null && mid != null && high != null && totalCloud in 0..100) {
        // Approximate optical attenuation: high clouds are usually more translucent
        // than low/middle clouds, but their exact thickness is unknown to the model.
        return ((1 - 0.95 * low / 100.0) *
            (1 - 0.80 * mid / 100.0) *
            (1 - 0.40 * high / 100.0)).coerceIn(0.0, 1.0)
    }
    return totalCloud?.takeIf { it in 0..100 }?.let { (100 - it) / 100.0 }
}

/**
 * Cover the daylight interval using one unique hourly value per physical hour.
 * Cloud at timestamp t describes [t,t+1h), but Open-Meteo sunshine_duration
 * and DNI at t+1 describe the PRECEDING hour, so we explicitly align t+1.
 * Reject <50% daylight coverage and substantial overcast hours without
 * solar evidence rather than extrapolating sunny samples across unknown sky.
 */
fun estimateCloudAdjustedDaylight(
    sunrise: Instant,
    sunset: Instant,
    timestamps: List<Instant>,
    cloudCover: List<Int?>,
    cloudCoverLow: List<Int?> = emptyList(),
    cloudCoverMid: List<Int?> = emptyList(),
    cloudCoverHigh: List<Int?> = emptyList(),
    sunshineDuration: List<Double?> = emptyList(),
    directNormalIrradiance: List<Double?> = emptyList()
): Double? {
    if (sunset <= sunrise) return null
    val daylightSeconds = (sunset.epochSecond - sunrise.epochSecond).toDouble()
    if (daylightSeconds <= 0.0 || daylightSeconds > 24.0 * 3600.0) return null
    val indices = timestamps.withIndex().associate { it.value to it.index }
    var coveredSeconds = 0.0
    var sunnySeconds = 0.0
    var unknownOvercastSeconds = 0.0
    val seen = hashSetOf<Instant>()
    for (i in timestamps.indices) {
        val start = timestamps[i]
        if (!seen.add(start)) continue
        val end = start.plusSeconds(3600)
        val overlap = max(0L, minOf(end.epochSecond, sunset.epochSecond) -
            maxOf(start.epochSecond, sunrise.epochSecond)).toDouble()
        if (overlap <= 0.0) continue
        val next = indices[start.plusSeconds(3600)]
        val fraction = estimatedSunFraction(
            cloudCover.getOrNull(i), cloudCoverLow.getOrNull(i),
            cloudCoverMid.getOrNull(i), cloudCoverHigh.getOrNull(i),
            next?.let { sunshineDuration.getOrNull(it) },
            next?.let { directNormalIrradiance.getOrNull(it) }
        )
        if (fraction == null) {
            if (cloudCover.getOrNull(i) in 90..100) unknownOvercastSeconds += overlap
            continue
        }
        coveredSeconds += overlap
        sunnySeconds += overlap * fraction
    }
    // Extrapolating sunny samples into hours where nearly all the sky is
    // covered but opacity is unknown is particularly misleading. Those hours
    // need actual solar data to justify any reported daily sunshine duration.
    if (unknownOvercastSeconds >= daylightSeconds * 0.20) return null
    if (coveredSeconds < daylightSeconds * 0.5) return null
    return (daylightSeconds / 3600.0 * sunnySeconds / coveredSeconds).coerceIn(0.0, 24.0)
}

/** One clock-hour segment [instant, instant + 1h), clipped to daylight. */
fun estimateHourlySunshineHours(
    instant: Instant,
    cloudCoverPercent: Int?,
    solarWindow: ForecastSolarWindow,
    lowCloudPercent: Int? = null,
    midCloudPercent: Int? = null,
    highCloudPercent: Int? = null,
    sunshineSeconds: Double? = null,
    directNormalWm2: Double? = null
): Double? {
    val fraction = estimatedSunFraction(
        cloudCoverPercent, lowCloudPercent, midCloudPercent,
        highCloudPercent, sunshineSeconds, directNormalWm2
    ) ?: return null
    if (!solarWindow.isValid) return null
    val start = maxOf(instant, requireNotNull(solarWindow.sunrise))
    val end = minOf(instant.plusSeconds(3600), requireNotNull(solarWindow.sunset))
    return (maxOf(0L, end.epochSecond - start.epochSecond) / 3600.0 * fraction)
        .coerceIn(0.0, 1.0)
}

/**
 * Hourly sunshine in the *same* [instant, instant + 1h) interval as the sky
 * consensus. Open-Meteo publishes sunshine_duration at the END of that interval.
 *
 * In a mostly-overcast consensus, a median of *only available* solar values is
 * misleading: a single less-cloudy model (or an outlying model with solar data)
 * can report 44 sunny minutes while most models predict overcast and provide no
 * solar duration. Missing values are neither sunny nor zero. For an overcast
 * consensus we therefore require provider sunshine_duration from overcast
 * models in a strict majority of independent model families, or return unknown.
 * With a single model, its own explicitly supplied duration remains available.
 *
 * Outside the mostly-overcast case the existing estimate is preserved.
 */
fun estimatedHourlySunshineFromModels(
    forecast: CityForecast,
    instant: Instant,
    solarWindow: ForecastSolarWindow,
    cloudCoverFallback: Int? = null
): Double? {
    data class ModelHour(
        val model: com.meteocompare.app.domain.model.WeatherModel,
        val cloud: Int?,
        val estimate: Double?,
        val explicitSunshine: Double?
    )

    val hours = forecast.seriesByModel.mapNotNull { (model, series) ->
        val hourly = series.hourly
        val index = hourly.timestamps.indexOf(instant)
        if (index < 0) return@mapNotNull null
        val nextIndex = hourly.timestamps.indexOf(instant.plusSeconds(3600))
        val cloud = hourly.cloudCover.getOrNull(index)?.takeIf { it in 0..100 }
        val explicitSunshine = nextIndex.takeIf { it >= 0 }
            ?.let { hourly.sunshineDuration.getOrNull(it) }
            ?.takeIf { it.isFinite() && it in 0.0..3600.0 }
        val estimate = estimateHourlySunshineHours(
            instant, cloud, solarWindow,
            hourly.cloudCoverLow.getOrNull(index),
            hourly.cloudCoverMid.getOrNull(index),
            hourly.cloudCoverHigh.getOrNull(index),
            explicitSunshine,
            nextIndex.takeIf { it >= 0 }
                ?.let { hourly.directNormalIrradiance.getOrNull(it) }
        )
        ModelHour(model, cloud, estimate, explicitSunshine)
    }

    // Prefer the cloud consensus already used to display the condition; when
    // unavailable, compute the same family-balanced cloud median here.
    val centralCloud = cloudCoverFallback?.takeIf { it in 0..100 } ?: run {
        val entries = hours.mapNotNull { item ->
            item.cloud?.let { ForecastConsensus.Entry(item.model, it.toDouble()) }
        }
        ForecastConsensus.continuous(entries, tightStdDev = 10.0, wideStdDev = 50.0)
            .central?.roundToInt()
    }

    if (centralCloud != null && centralCloud >= 90) {
        val cloudFamilies = hours.mapNotNull { item ->
            item.cloud?.let { ForecastConsensus.groupFor(item.model) }
        }.toSet()
        val explicit = hours.filter { item ->
            item.cloud != null && item.cloud >= 90 && item.explicitSunshine != null &&
                item.estimate != null
        }
        val solarFamilies = explicit.map { ForecastConsensus.groupFor(it.model) }.toSet()
        if (solarFamilies.isEmpty() ||
            (cloudFamilies.size > 1 && solarFamilies.size * 2 <= cloudFamilies.size)
        ) return null

        // Related regional/global variants of one model family must not gain
        // extra weight merely because they provide more solar fields.
        val familyMedians = explicit.groupBy { ForecastConsensus.groupFor(it.model) }
            .values.map { group ->
                val values = group.mapNotNull { it.estimate }.sorted()
                val middle = values.size / 2
                if (values.size % 2 == 1) values[middle]
                else (values[middle - 1] + values[middle]) / 2.0
            }.sorted()
        val middle = familyMedians.size / 2
        return if (familyMedians.size % 2 == 1) familyMedians[middle]
        else (familyMedians[middle - 1] + familyMedians[middle]) / 2.0
    }

    val estimates = hours.mapNotNull { it.estimate }.sorted()
    if (estimates.isNotEmpty()) {
        val middle = estimates.size / 2
        return if (estimates.size % 2 == 1) estimates[middle]
            else (estimates[middle - 1] + estimates[middle]) / 2.0
    }
    return estimateHourlySunshineHours(instant, cloudCoverFallback, solarWindow)
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
    val sunHours = estimatedHourlySunshineFromModels(forecast, instant, window, clouds)
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
