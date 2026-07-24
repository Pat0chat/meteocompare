package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Origine du signal pluie affiché dans la chronologie. */
internal enum class PrecipitationSignalSource {
    /** Médiane de probabilités explicitement fournies par plusieurs modèles. */
    MODEL_PROBABILITY,

    /** Part des modèles déterministes qui prévoient un cumul au-dessus du seuil pluie. */
    MODEL_AGREEMENT
}

/** Point synthétique d'une chronologie de consensus multi-modèles. */
internal data class SimplifiedTimelinePoint(
    val instant: Instant? = null,
    val date: LocalDate? = null,
    val temperatureC: Double? = null,
    val tempMinC: Double? = null,
    val tempMaxC: Double? = null,
    val precipitationPercent: Int? = null,
    val precipitationSource: PrecipitationSignalSource? = null,
    val precipitationModelCount: Int = 0,
    val wetModelCount: Int = 0,
    val windKmh: Double? = null,
    val condition: WeatherCondition? = null,
    /** Nombre de modèles ayant fourni au moins une valeur exploitable à cette échéance. */
    val modelCount: Int,
    val temperatureModelCount: Int = 0,
    val windModelCount: Int = 0,
    val conditionModelCount: Int = 0,
    /** Vrai uniquement si au moins deux modèles partagent une même métrique. */
    val hasMultiModelEvidence: Boolean,
    /** Désaccord spécifiquement lié à la pluie, distinct des autres variables. */
    val isRainDivergent: Boolean = false,
    val isDivergent: Boolean
)

internal fun buildSimplifiedTimeline(
    forecast: CityForecast,
    mode: DisplayMode,
    now: Instant = Instant.now()
): List<SimplifiedTimelinePoint> = when (mode) {
    DisplayMode.HOURLY -> buildHourlyTimeline(forecast, now)
    DisplayMode.DAILY -> buildDailyTimeline(forecast, now)
}

private fun buildHourlyTimeline(
    forecast: CityForecast,
    now: Instant
): List<SimplifiedTimelinePoint> {
    val (startHour, endExclusive) = computeHourlyHorizon(forecast.city.timezone, now)
    val indexed = forecast.seriesByModel.values.map(::indexHourlySnapshots)
    val all = indexed
        .flatMap { it.keys }
        .distinct()
        .sorted()
        .filter { it >= startHour && it < endExclusive }

    val candidates = all.take(MAX_HOURLY_CANDIDATES)
    val sampled = if (candidates.size <= MAX_TIMELINE_POINTS) {
        candidates
    } else {
        candidates.filterIndexed { index, _ -> index % 2 == 0 }.take(MAX_TIMELINE_POINTS)
    }

    return sampled.mapNotNull { timestamp ->
        val snapshots = indexed.mapNotNull { it[timestamp] }
        timelinePoint(timestamp = timestamp, date = null, snapshots = snapshots, hourly = true)
    }
}

private fun buildDailyTimeline(
    forecast: CityForecast,
    now: Instant
): List<SimplifiedTimelinePoint> {
    val zone = safeZone(forecast.city.timezone)
    val today = now.atZone(zone).toLocalDate()
    val indexed = forecast.seriesByModel.values.map(::indexDailySnapshots)
    val dates = indexed
        .flatMap { it.keys }
        .distinct()
        .sorted()
        // Un cache ancien reste accessible dans les tableaux, mais ne doit pas
        // être présenté comme les « prochains jours » dans la synthèse.
        .filterNot { it.isBefore(today) }
        .take(MAX_DAILY_POINTS)

    return dates.mapNotNull { date ->
        val snapshots = indexed.mapNotNull { it[date] }
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
) {
    val hasAnyValue: Boolean
        get() = temperature != null || tempMin != null || tempMax != null ||
            precipitation != null || precipitationProbability != null || wind != null ||
            (condition != null && condition != WeatherCondition.UNKNOWN)
}

private fun indexHourlySnapshots(series: ForecastSeries): Map<Instant, TimelineSnapshot> = buildMap {
    series.hourly.timestamps.forEachIndexed { index, timestamp ->
        val temperature = series.hourly.temperature2m.getOrNull(index)
        val precipitation = series.hourly.precipitation.getOrNull(index)
        val probability = series.hourly.precipitationProbability.getOrNull(index)
        val wind = series.hourly.windSpeed10m.getOrNull(index)
        val nativeCondition = WeatherCondition.fromWmoCode(series.hourly.weatherCode.getOrNull(index))
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
        val condition = nativeCondition
            ?: WeatherCondition.inferFromPrecipAndTemp(precipitation, temperature)
        val snapshot = TimelineSnapshot(
            temperature = temperature,
            tempMin = null,
            tempMax = null,
            precipitation = precipitation,
            precipitationProbability = probability,
            wind = wind,
            condition = condition
        )
        if (snapshot.hasAnyValue) put(timestamp, snapshot)
    }
}

private fun indexDailySnapshots(series: ForecastSeries): Map<LocalDate, TimelineSnapshot> = buildMap {
    series.daily.dates.forEachIndexed { index, date ->
        val min = series.daily.tempMin.getOrNull(index)
        val max = series.daily.tempMax.getOrNull(index)
        val precipitation = series.daily.precipitationSum.getOrNull(index)
        val probability = series.daily.precipitationProbabilityMax.getOrNull(index)
        val wind = series.daily.windSpeedMax.getOrNull(index)
        val nativeCondition = WeatherCondition.fromWmoCode(series.daily.weatherCode.getOrNull(index))
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
        val condition = nativeCondition
            ?: WeatherCondition.inferFromPrecipAndTemp(precipitation, min)
        val snapshot = TimelineSnapshot(
            temperature = null,
            tempMin = min,
            tempMax = max,
            precipitation = precipitation,
            precipitationProbability = probability,
            wind = wind,
            condition = condition
        )
        if (snapshot.hasAnyValue) put(date, snapshot)
    }
}

private fun timelinePoint(
    timestamp: Instant?,
    date: LocalDate?,
    snapshots: List<TimelineSnapshot>,
    hourly: Boolean
): SimplifiedTimelinePoint? {
    val meaningful = snapshots.filter(TimelineSnapshot::hasAnyValue)
    if (meaningful.isEmpty()) return null

    val temperatures = meaningful.mapNotNull { it.temperature }
    val minTemperatures = meaningful.mapNotNull { it.tempMin }
    val maxTemperatures = meaningful.mapNotNull { it.tempMax }
    val precipitationValues = meaningful.mapNotNull { it.precipitation }
    val probabilities = meaningful.mapNotNull { it.precipitationProbability }
    val winds = meaningful.mapNotNull { it.wind }
    val conditions = meaningful.mapNotNull { it.condition }
        .filterNot { it == WeatherCondition.UNKNOWN }

    val conditionCounts = conditions.groupingBy { it }.eachCount()
    val topConditionCount = conditionCounts.values.maxOrNull() ?: 0
    val consensusCondition = conditionCounts.entries
        .filter { it.value == topConditionCount }
        .maxByOrNull { conditionSeverity(it.key) }
        ?.key
    val conditionAgreement = if (conditions.size >= 2) {
        topConditionCount * 100.0 / conditions.size
    } else {
        null
    }
    val conditionDivergent = conditionAgreement != null && conditionAgreement < 60.0

    val rainThreshold = if (hourly) HOURLY_RAIN_THRESHOLD_MM else DAILY_RAIN_THRESHOLD_MM
    val wetVotes = precipitationValues.count { it >= rainThreshold }
    val wetShare = if (precipitationValues.size >= 2) {
        wetVotes * 100.0 / precipitationValues.size
    } else {
        null
    }

    // Une probabilité isolée ne représente pas un consensus multi-modèles.
    // On ne l'utilise que si au moins deux modèles la fournissent et si la
    // couverture atteint la moitié des modèles disposant d'un signal pluie.
    val rainCapableModelCount = meaningful.count {
        it.precipitationProbability != null || it.precipitation != null
    }
    val minimumProbabilityCoverage = maxOf(2, ceil(rainCapableModelCount / 2.0).toInt())
    val hasRobustProbabilityCoverage = probabilities.size >= minimumProbabilityCoverage

    val precipitationSource: PrecipitationSignalSource?
    val precipitationPercent: Int?
    val precipitationModelCount: Int
    when {
        hasRobustProbabilityCoverage -> {
            precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY
            precipitationPercent = median(probabilities.map(Int::toDouble))
                ?.roundToInt()
                ?.coerceIn(0, 100)
            precipitationModelCount = probabilities.size
        }
        wetShare != null -> {
            precipitationSource = PrecipitationSignalSource.MODEL_AGREEMENT
            precipitationPercent = wetShare.roundToInt().coerceIn(0, 100)
            precipitationModelCount = precipitationValues.size
        }
        else -> {
            precipitationSource = null
            precipitationPercent = null
            precipitationModelCount = maxOf(probabilities.size, precipitationValues.size)
        }
    }

    val temperatureSpread = when {
        hourly -> spread(temperatures)
        else -> maxOf(spread(minTemperatures), spread(maxTemperatures))
    }
    val windSpread = spread(winds)
    val probabilitySpread = if (probabilities.size < 2) 0.0
    else (probabilities.maxOrNull()!! - probabilities.minOrNull()!!).toDouble()
    val splitRain = wetShare != null && wetShare in 30.0..70.0
    val isRainDivergent = probabilitySpread > 50.0 || splitRain

    val isDivergent = conditionDivergent ||
        temperatureSpread > (if (hourly) 4.0 else 5.0) ||
        windSpread > 20.0 ||
        isRainDivergent

    val temperatureModelCount = if (hourly) temperatures.size
    else maxOf(minTemperatures.size, maxTemperatures.size)
    // Deux modèles présents ne constituent pas nécessairement une comparaison
    // s'ils renseignent des variables différentes. L'évidence multi-modèles
    // exige au moins une métrique réellement partagée par deux contributeurs.
    val hasMultiModelEvidence = listOf(
        temperatureModelCount,
        precipitationModelCount,
        winds.size,
        conditions.size
    ).any { it >= 2 }

    return SimplifiedTimelinePoint(
        instant = timestamp,
        date = date,
        temperatureC = median(temperatures),
        tempMinC = median(minTemperatures),
        tempMaxC = median(maxTemperatures),
        precipitationPercent = precipitationPercent,
        precipitationSource = precipitationSource,
        precipitationModelCount = precipitationModelCount,
        wetModelCount = wetVotes,
        windKmh = median(winds),
        condition = consensusCondition,
        modelCount = meaningful.size,
        temperatureModelCount = temperatureModelCount,
        windModelCount = winds.size,
        conditionModelCount = conditions.size,
        hasMultiModelEvidence = hasMultiModelEvidence,
        isRainDivergent = isRainDivergent,
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

/**
 * Classement conservateur utilisé seulement pour départager deux catégories
 * ayant reçu exactement le même nombre de votes. UNKNOWN est volontairement
 * absent et filtré avant le vote.
 */
private fun conditionSeverity(condition: WeatherCondition): Int = when (condition) {
    WeatherCondition.CLEAR -> 0
    WeatherCondition.MAINLY_CLEAR -> 1
    WeatherCondition.PARTLY_CLOUDY -> 2
    WeatherCondition.OVERCAST -> 3
    WeatherCondition.FOG -> 4
    WeatherCondition.DRIZZLE -> 5
    WeatherCondition.RAIN_SHOWERS -> 6
    WeatherCondition.RAIN -> 7
    WeatherCondition.SNOW_SHOWERS -> 8
    WeatherCondition.SNOW -> 9
    WeatherCondition.FREEZING_RAIN -> 10
    WeatherCondition.THUNDERSTORM -> 11
    WeatherCondition.UNKNOWN -> -1
}

private fun safeZone(timezone: String?): ZoneId = resolveCityZone(timezone)

/** Chronologie principale : privilégie les prochaines heures, avec repli sur 7 jours. */
internal data class OverviewTimeline(
    val mode: DisplayMode,
    val points: List<SimplifiedTimelinePoint>
)

internal fun buildOverviewTimeline(
    forecast: CityForecast,
    now: Instant = Instant.now()
): OverviewTimeline {
    val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, now)
    return if (hourly.size >= 2) {
        OverviewTimeline(DisplayMode.HOURLY, hourly)
    } else {
        OverviewTimeline(
            DisplayMode.DAILY,
            buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now)
        )
    }
}

private const val MAX_HOURLY_CANDIDATES = 16
private const val MAX_TIMELINE_POINTS = 8
private const val MAX_DAILY_POINTS = 7
private const val HOURLY_RAIN_THRESHOLD_MM = 0.1
private const val DAILY_RAIN_THRESHOLD_MM = 0.2
