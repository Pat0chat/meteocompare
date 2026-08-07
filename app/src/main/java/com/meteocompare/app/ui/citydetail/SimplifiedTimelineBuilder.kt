package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.dailyCloudCoverMean
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Origine du signal pluie affiché dans la chronologie. */
internal enum class PrecipitationSignalSource {
    /** Médiane de probabilités explicitement fournies par plusieurs modèles. */
    MODEL_PROBABILITY,

    /** Part des modèles déterministes qui prévoient un cumul au-dessus du seuil pluie. */
    MODEL_AGREEMENT
}

/** Variable principalement responsable d'un désaccord entre les modèles. */
internal enum class DivergenceReason {
    TEMPERATURE,
    PRECIPITATION,
    WIND,
    CONDITION
}

/** Niveau synthétique d'accord multi-modèles à une échéance. */
internal enum class ModelConsensusLevel {
    HIGH,
    MEDIUM,
    LOW
}

/** Variable évaluée séparément dans le consensus multi-modèles. */
internal enum class ForecastMetric {
    TEMPERATURE,
    PRECIPITATION,
    WIND,
    CONDITION
}

/**
 * Consensus propre à une variable. Le score global de la timeline reste une
 * synthèse visuelle, mais les événements et les insights utilisent ce détail.
 */
internal data class MetricConsensus(
    val metric: ForecastMetric,
    val percent: Int,
    val modelCount: Int,
    val level: ModelConsensusLevel,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val isDivergent: Boolean = false
)

/** Point synthétique d'une chronologie de consensus multi-modèles. */
internal data class SimplifiedTimelinePoint(
    val instant: Instant? = null,
    val date: LocalDate? = null,
    val temperatureC: Double? = null,
    val tempMinC: Double? = null,
    val tempMaxC: Double? = null,
    /** Étendue de la température principale entre les modèles à cette échéance. */
    val temperatureMinAcrossModels: Double? = null,
    val temperatureMaxAcrossModels: Double? = null,
    val precipitationPercent: Int? = null,
    val precipitationSource: PrecipitationSignalSource? = null,
    val precipitationModelCount: Int = 0,
    val wetModelCount: Int = 0,
    /** Cumul médian et plage des scénarios, en millimètres. */
    val precipitationMm: Double? = null,
    val precipitationMinAcrossModelsMm: Double? = null,
    val precipitationMaxAcrossModelsMm: Double? = null,
    /** Plage de probabilités lorsque plusieurs modèles la fournissent. */
    val precipitationProbabilityMin: Int? = null,
    val precipitationProbabilityMax: Int? = null,
    /** Couverture nuageuse médiane entre modèles, 0-100%. */
    val cloudCoverPercent: Int? = null,
    val cloudCoverMinAcrossModels: Int? = null,
    val cloudCoverMaxAcrossModels: Int? = null,
    val cloudCoverModelCount: Int = 0,
    val windKmh: Double? = null,
    val windMinAcrossModels: Double? = null,
    val windMaxAcrossModels: Double? = null,
    /** Rafale médiane entre modèles à l'échéance (ou maximum journalier). */
    val windGustKmh: Double? = null,
    val windGustMinAcrossModels: Double? = null,
    val windGustMaxAcrossModels: Double? = null,
    val windGustModelCount: Int = 0,
    val condition: WeatherCondition? = null,
    /** Nombre de modèles ayant fourni au moins une valeur exploitable à cette échéance. */
    val modelCount: Int = 0,
    val temperatureModelCount: Int = 0,
    val windModelCount: Int = 0,
    val conditionModelCount: Int = 0,
    /** Vrai uniquement si au moins deux modèles partagent une même métrique. */
    val hasMultiModelEvidence: Boolean = false,
    /** Score synthétique d'accord, calculé seulement à partir de métriques comparables. */
    val consensusPercent: Int? = null,
    val consensusLevel: ModelConsensusLevel? = null,
    /** Consensus détaillé par variable, source de vérité des insights. */
    val metricConsensus: Map<ForecastMetric, MetricConsensus> = emptyMap(),
    /** Variables qui dépassent les seuils de divergence. */
    val divergenceReasons: Set<DivergenceReason> = emptySet()
) {
    /** Dérivés de la source unique [divergenceReasons], donc jamais incohérents. */
    val isRainDivergent: Boolean
        get() = DivergenceReason.PRECIPITATION in divergenceReasons

    val isDivergent: Boolean
        get() = divergenceReasons.isNotEmpty()

    fun consensusFor(metric: ForecastMetric): MetricConsensus? = metricConsensus[metric]
}

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
    val timestamps = indexed
        .flatMap { it.keys }
        .distinct()
        .sorted()
        .filter { it >= startHour && it < endExclusive }

    // L'analyse conserve toutes les échéances de la fenêtre. La timeline
    // visuelle applique ensuite une grille régulière indépendante des événements.
    return timestamps.mapNotNull { timestamp ->
        val snapshots = indexed.mapNotNull { it[timestamp] }
        timelinePoint(timestamp = timestamp, date = null, snapshots = snapshots, hourly = true)
    }
}

private fun buildDailyTimeline(
    forecast: CityForecast,
    now: Instant
): List<SimplifiedTimelinePoint> {
    val zone = resolveCityZone(forecast.city.timezone)
    val today = now.atZone(zone).toLocalDate()
    val indexed = forecast.seriesByModel.values.map { series -> indexDailySnapshots(series, zone) }
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
    val cloudCover: Int?,
    val wind: Double?,
    val windGust: Double?,
    val condition: WeatherCondition?
) {
    val hasAnyValue: Boolean
        get() = temperature != null || tempMin != null || tempMax != null ||
            precipitation != null || precipitationProbability != null || cloudCover != null ||
            wind != null || windGust != null ||
            (condition != null && condition != WeatherCondition.UNKNOWN)
}

private fun indexHourlySnapshots(series: ForecastSeries): Map<Instant, TimelineSnapshot> = buildMap {
    series.hourly.timestamps.forEachIndexed { index, timestamp ->
        val temperature = series.hourly.temperature2m.getOrNull(index)
        val precipitation = series.hourly.precipitation.getOrNull(index)
        val probability = series.hourly.precipitationProbability.getOrNull(index)
        val cloudCover = series.hourly.cloudCover.getOrNull(index)
        val wind = series.hourly.windSpeed10m.getOrNull(index)
        val windGust = series.hourly.windGusts10m.getOrNull(index)
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
            cloudCover = cloudCover,
            wind = wind,
            windGust = windGust,
            condition = condition
        )
        if (snapshot.hasAnyValue) put(timestamp, snapshot)
    }
}

private fun indexDailySnapshots(
    series: ForecastSeries,
    zone: java.time.ZoneId
): Map<LocalDate, TimelineSnapshot> = buildMap {
    series.daily.dates.forEachIndexed { index, date ->
        val min = series.daily.tempMin.getOrNull(index)
        val max = series.daily.tempMax.getOrNull(index)
        val precipitation = series.daily.precipitationSum.getOrNull(index)
        val probability = series.daily.precipitationProbabilityMax.getOrNull(index)
        val cloudCover = series.dailyCloudCoverMean(date, zone)
        val wind = series.daily.windSpeedMax.getOrNull(index)
        val windGust = series.daily.windGustsMax.getOrNull(index)
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
            cloudCover = cloudCover,
            wind = wind,
            windGust = windGust,
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
    val cloudCovers = meaningful.mapNotNull { it.cloudCover }
    val winds = meaningful.mapNotNull { it.wind }
    val windGusts = meaningful.mapNotNull { it.windGust }
    val conditions = meaningful.mapNotNull { it.condition }
        .filterNot { it == WeatherCondition.UNKNOWN }

    val conditionCounts = conditions.groupingBy { it }.eachCount()
    val topConditionCount = conditionCounts.values.maxOrNull() ?: 0
    val consensusCondition = conditionCounts.entries
        .filter { it.value == topConditionCount }
        .maxByOrNull { it.key.severityRank }
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
    val probabilityValues = probabilities.map(Int::toDouble)
    val probabilitySpread = spread(probabilityValues)
    val splitRain = wetShare != null && wetShare in 30.0..70.0
    val isRainDivergent = when {
        hasRobustProbabilityCoverage -> probabilitySpread > 50.0
        else -> splitRain
    }

    val temperatureModelCount = if (hourly) temperatures.size
    else maxOf(minTemperatures.size, maxTemperatures.size)
    val temperatureScore = if (temperatureModelCount >= 2) {
        spreadAgreementScore(temperatureSpread, if (hourly) 8.0 else 10.0)
    } else null
    val windScore = if (winds.size >= 2) spreadAgreementScore(windSpread, 40.0) else null
    val precipitationScore = when {
        hasRobustProbabilityCoverage -> (100.0 - probabilitySpread).coerceIn(0.0, 100.0)
        wetShare != null -> maxOf(wetShare, 100.0 - wetShare)
        else -> null
    }

    fun metricLevel(score: Double): ModelConsensusLevel = when {
        score >= 75.0 -> ModelConsensusLevel.HIGH
        score >= 50.0 -> ModelConsensusLevel.MEDIUM
        else -> ModelConsensusLevel.LOW
    }

    val metricConsensus = buildMap {
        temperatureScore?.let { score ->
            put(
                ForecastMetric.TEMPERATURE,
                MetricConsensus(
                    metric = ForecastMetric.TEMPERATURE,
                    percent = score.roundToInt().coerceIn(0, 100),
                    modelCount = temperatureModelCount,
                    level = metricLevel(score),
                    minimum = (if (hourly) temperatures else minTemperatures).minOrNull(),
                    maximum = (if (hourly) temperatures else maxTemperatures).maxOrNull(),
                    isDivergent = temperatureSpread > (if (hourly) 4.0 else 5.0)
                )
            )
        }
        precipitationScore?.let { score ->
            put(
                ForecastMetric.PRECIPITATION,
                MetricConsensus(
                    metric = ForecastMetric.PRECIPITATION,
                    percent = score.roundToInt().coerceIn(0, 100),
                    modelCount = precipitationModelCount,
                    level = metricLevel(score),
                    minimum = when {
                        hasRobustProbabilityCoverage -> probabilityValues.minOrNull()
                        else -> precipitationValues.minOrNull()
                    },
                    maximum = when {
                        hasRobustProbabilityCoverage -> probabilityValues.maxOrNull()
                        else -> precipitationValues.maxOrNull()
                    },
                    isDivergent = isRainDivergent
                )
            )
        }
        windScore?.let { score ->
            put(
                ForecastMetric.WIND,
                MetricConsensus(
                    metric = ForecastMetric.WIND,
                    percent = score.roundToInt().coerceIn(0, 100),
                    modelCount = winds.size,
                    level = metricLevel(score),
                    minimum = winds.minOrNull(),
                    maximum = winds.maxOrNull(),
                    isDivergent = windSpread > 20.0
                )
            )
        }
        conditionAgreement?.let { score ->
            put(
                ForecastMetric.CONDITION,
                MetricConsensus(
                    metric = ForecastMetric.CONDITION,
                    percent = score.roundToInt().coerceIn(0, 100),
                    modelCount = conditions.size,
                    level = metricLevel(score),
                    isDivergent = conditionDivergent
                )
            )
        }
    }

    val divergenceReasons = buildSet {
        if (metricConsensus[ForecastMetric.CONDITION]?.isDivergent == true) add(DivergenceReason.CONDITION)
        if (metricConsensus[ForecastMetric.TEMPERATURE]?.isDivergent == true) add(DivergenceReason.TEMPERATURE)
        if (metricConsensus[ForecastMetric.WIND]?.isDivergent == true) add(DivergenceReason.WIND)
        if (metricConsensus[ForecastMetric.PRECIPITATION]?.isDivergent == true) add(DivergenceReason.PRECIPITATION)
    }

    val hasMultiModelEvidence = metricConsensus.isNotEmpty()
    val consensusPercent = metricConsensus.values
        .takeIf { it.isNotEmpty() }
        ?.map(MetricConsensus::percent)
        ?.average()
        ?.roundToInt()
        ?.coerceIn(0, 100)
    val consensusLevel = consensusPercent?.let { metricLevel(it.toDouble()) }
    val primaryTemperatureValues = if (hourly) temperatures else maxTemperatures

    return SimplifiedTimelinePoint(
        instant = timestamp,
        date = date,
        temperatureC = median(temperatures),
        tempMinC = median(minTemperatures),
        tempMaxC = median(maxTemperatures),
        temperatureMinAcrossModels = primaryTemperatureValues.minOrNull(),
        temperatureMaxAcrossModels = primaryTemperatureValues.maxOrNull(),
        precipitationPercent = precipitationPercent,
        precipitationSource = precipitationSource,
        precipitationModelCount = precipitationModelCount,
        wetModelCount = wetVotes,
        precipitationMm = median(precipitationValues),
        precipitationMinAcrossModelsMm = precipitationValues.minOrNull(),
        precipitationMaxAcrossModelsMm = precipitationValues.maxOrNull(),
        precipitationProbabilityMin = probabilities.minOrNull(),
        precipitationProbabilityMax = probabilities.maxOrNull(),
        cloudCoverPercent = median(cloudCovers.map(Int::toDouble))?.roundToInt()?.coerceIn(0, 100),
        cloudCoverMinAcrossModels = cloudCovers.minOrNull(),
        cloudCoverMaxAcrossModels = cloudCovers.maxOrNull(),
        cloudCoverModelCount = cloudCovers.size,
        windKmh = median(winds),
        windMinAcrossModels = winds.minOrNull(),
        windMaxAcrossModels = winds.maxOrNull(),
        windGustKmh = median(windGusts),
        windGustMinAcrossModels = windGusts.minOrNull(),
        windGustMaxAcrossModels = windGusts.maxOrNull(),
        windGustModelCount = windGusts.size,
        condition = consensusCondition,
        modelCount = meaningful.size,
        temperatureModelCount = temperatureModelCount,
        windModelCount = winds.size,
        conditionModelCount = conditions.size,
        hasMultiModelEvidence = hasMultiModelEvidence,
        consensusPercent = consensusPercent,
        consensusLevel = consensusLevel,
        metricConsensus = metricConsensus,
        divergenceReasons = divergenceReasons
    )

}

private fun spreadAgreementScore(spread: Double, zeroAgreementSpread: Double): Double =
    (100.0 - (spread / zeroAgreementSpread * 100.0)).coerceIn(0.0, 100.0)

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
 * Sélectionne une grille temporelle prévisible. En mode horaire, les cartes
 * sont espacées de trois heures à partir de la première échéance disponible.
 * Les événements ne déplacent plus les cartes : ils sont portés par la
 * réglette dédiée dans [SimplifiedTimelineCard].
 */
internal fun selectRegularTimelinePoints(
    points: List<SimplifiedTimelinePoint>,
    maxPoints: Int = MAX_TIMELINE_POINTS,
    stepHours: Long = REGULAR_TIMELINE_STEP_HOURS
): List<SimplifiedTimelinePoint> {
    if (maxPoints <= 0 || points.isEmpty()) return emptyList()
    val ordered = points.sortedBy(::timelineSortKey)
    if (ordered.first().instant == null) return ordered.take(maxPoints)

    val hourly = ordered.filter { it.instant != null }
    val firstInstant = hourly.firstOrNull()?.instant ?: return ordered.take(maxPoints)
    val byInstant = hourly.associateBy { requireNotNull(it.instant) }
    return List(maxPoints) { slot ->
        val target = firstInstant.plusSeconds(slot * stepHours * 3_600L)
        byInstant[target] ?: SimplifiedTimelinePoint(instant = target)
    }
}

internal fun sameTimelinePoint(
    first: SimplifiedTimelinePoint,
    second: SimplifiedTimelinePoint
): Boolean = when {
    first.instant != null || second.instant != null -> first.instant == second.instant
    first.date != null || second.date != null -> first.date == second.date
    else -> first === second
}

internal fun timelinePointKey(point: SimplifiedTimelinePoint): String = when {
    point.instant != null -> "instant:${point.instant.toEpochMilli()}"
    point.date != null -> "date:${point.date}"
    else -> "point:${point.hashCode()}"
}

private fun timelineSortKey(point: SimplifiedTimelinePoint): Long = when {
    point.instant != null -> point.instant.toEpochMilli()
    point.date != null -> point.date.toEpochDay() * MILLIS_PER_DAY
    else -> Long.MAX_VALUE
}

/** Chronologie principale : 24 heures glissantes, avec repli sur 7 jours. */
internal data class OverviewTimeline(
    val mode: DisplayMode,
    val analysisPoints: List<SimplifiedTimelinePoint>,
    /** Fuseau local utilisé pour distinguer les variations météo du cycle jour/nuit. */
    val timezone: String? = null
)

internal fun buildOverviewTimeline(
    forecast: CityForecast,
    now: Instant = Instant.now()
): OverviewTimeline {
    val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, now)
    return if (hourly.size >= 2) {
        OverviewTimeline(
            mode = DisplayMode.HOURLY,
            analysisPoints = hourly,
            timezone = forecast.city.timezone
        )
    } else {
        val daily = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now)
        OverviewTimeline(
            mode = DisplayMode.DAILY,
            analysisPoints = daily,
            timezone = forecast.city.timezone
        )
    }
}

private const val MAX_TIMELINE_POINTS = 8
private const val MAX_DAILY_POINTS = 7
private const val HOURLY_RAIN_THRESHOLD_MM = 0.1
private const val DAILY_RAIN_THRESHOLD_MM = 0.2
private const val MILLIS_PER_DAY = 86_400_000L
private const val REGULAR_TIMELINE_STEP_HOURS = 3L
