package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherCondition
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
    val windKmh: Double? = null,
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
    /** Variables qui dépassent les seuils de divergence. */
    val divergenceReasons: Set<DivergenceReason> = emptySet()
) {
    /** Dérivés de la source unique [divergenceReasons], donc jamais incohérents. */
    val isRainDivergent: Boolean
        get() = DivergenceReason.PRECIPITATION in divergenceReasons

    val isDivergent: Boolean
        get() = divergenceReasons.isNotEmpty()
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

    // L'analyse conserve toutes les échéances de la fenêtre. La réduction à
    // huit cartes est faite ensuite par selectTimelinePoints(), en tenant compte
    // des événements importants et des insights retenus.
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
    val probabilitySpread = spread(probabilities.map(Int::toDouble))
    val splitRain = wetShare != null && wetShare in 30.0..70.0
    // Une forte dispersion de probabilités n'est représentative que lorsque
    // la couverture est suffisante. Sinon l'interface affiche et évalue le
    // vote déterministe des modèles disponibles.
    val isRainDivergent = when {
        hasRobustProbabilityCoverage -> probabilitySpread > 50.0
        else -> splitRain
    }

    val divergenceReasons = buildSet {
        if (conditionDivergent) add(DivergenceReason.CONDITION)
        if (temperatureSpread > (if (hourly) 4.0 else 5.0)) add(DivergenceReason.TEMPERATURE)
        if (windSpread > 20.0) add(DivergenceReason.WIND)
        if (isRainDivergent) add(DivergenceReason.PRECIPITATION)
    }

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

    val consensusScores = buildList {
        if (temperatureModelCount >= 2) {
            add(spreadAgreementScore(temperatureSpread, if (hourly) 8.0 else 10.0))
        }
        if (winds.size >= 2) add(spreadAgreementScore(windSpread, 40.0))
        when {
            hasRobustProbabilityCoverage -> {
                add((100.0 - probabilitySpread).coerceIn(0.0, 100.0))
            }
            wetShare != null -> add(maxOf(wetShare, 100.0 - wetShare))
        }
        conditionAgreement?.let(::add)
    }
    val consensusPercent = consensusScores
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
        ?.coerceIn(0, 100)
    val consensusLevel = consensusPercent?.let {
        when {
            it >= 75 -> ModelConsensusLevel.HIGH
            it >= 50 -> ModelConsensusLevel.MEDIUM
            else -> ModelConsensusLevel.LOW
        }
    }

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
        windKmh = median(winds),
        condition = consensusCondition,
        modelCount = meaningful.size,
        temperatureModelCount = temperatureModelCount,
        windModelCount = winds.size,
        conditionModelCount = conditions.size,
        hasMultiModelEvidence = hasMultiModelEvidence,
        consensusPercent = consensusPercent,
        consensusLevel = consensusLevel,
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
 * Sélectionne au plus [maxPoints] échéances pour l'affichage sans perdre les
 * événements importants. Les points requis (par exemple ceux des insights)
 * passent avant les événements automatiques, puis la liste est complétée par
 * des jalons régulièrement espacés afin de préserver la continuité temporelle.
 */
internal fun selectTimelinePoints(
    points: List<SimplifiedTimelinePoint>,
    maxPoints: Int = MAX_TIMELINE_POINTS,
    requiredPoints: List<SimplifiedTimelinePoint> = emptyList()
): List<SimplifiedTimelinePoint> {
    if (points.size <= maxPoints) return points.sortedBy(::timelineSortKey)
    if (maxPoints <= 0) return emptyList()

    val ordered = points.sortedBy(::timelineSortKey)
    val selected = linkedSetOf<SimplifiedTimelinePoint>()

    fun add(point: SimplifiedTimelinePoint?) {
        if (point != null && selected.size < maxPoints) selected += point
    }

    add(ordered.first())
    add(ordered.last())

    requiredPoints
        .mapNotNull { required -> nearestTimelinePoint(required, ordered) }
        .sortedBy(::timelineSortKey)
        .forEach(::add)

    val precipitationTransitions = ordered.zipWithNext()
        .mapNotNull { (before, after) ->
            val wasWet = (before.precipitationPercent ?: 0) >= DISPLAY_RAIN_THRESHOLD_PERCENT
            val becomesWet = (after.precipitationPercent ?: 0) >= DISPLAY_RAIN_THRESHOLD_PERCENT
            after.takeIf { wasWet != becomesWet }
        }
    precipitationTransitions.forEach(::add)

    add(ordered.firstOrNull { it.isDivergent })
    add(ordered.maxByOrNull { it.windKmh ?: Double.NEGATIVE_INFINITY })

    val temperaturePoints = ordered.filter { (it.temperatureC ?: it.tempMaxC) != null }
    add(temperaturePoints.minByOrNull { it.temperatureC ?: it.tempMaxC ?: Double.POSITIVE_INFINITY })
    add(temperaturePoints.maxByOrNull { it.temperatureC ?: it.tempMaxC ?: Double.NEGATIVE_INFINITY })

    ordered.zipWithNext()
        .mapNotNull { (before, after) -> after.takeIf { before.condition != after.condition } }
        .forEach(::add)

    // Complète les trous avec des jalons répartis sur toute la fenêtre.
    if (selected.size < maxPoints) {
        for (slot in 1 until maxPoints - 1) {
            val index = ((ordered.lastIndex.toDouble() * slot) / (maxPoints - 1))
                .roundToInt()
                .coerceIn(0, ordered.lastIndex)
            add(ordered[index])
        }
    }

    // Dernier remplissage séquentiel pour les séries irrégulières ou les doublons.
    ordered.forEach(::add)

    return selected.sortedBy(::timelineSortKey)
}

private fun nearestTimelinePoint(
    required: SimplifiedTimelinePoint,
    points: List<SimplifiedTimelinePoint>
): SimplifiedTimelinePoint? {
    points.firstOrNull { sameTimelinePoint(it, required) }?.let { return it }
    return points.minByOrNull { point ->
        when {
            required.instant != null && point.instant != null ->
                kotlin.math.abs(point.instant.toEpochMilli() - required.instant.toEpochMilli())
            required.date != null && point.date != null ->
                kotlin.math.abs(point.date.toEpochDay() - required.date.toEpochDay())
            else -> Long.MAX_VALUE
        }
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
    val displayPoints: List<SimplifiedTimelinePoint> = selectTimelinePoints(analysisPoints)
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
            displayPoints = selectTimelinePoints(hourly)
        )
    } else {
        val daily = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now)
        OverviewTimeline(
            mode = DisplayMode.DAILY,
            analysisPoints = daily,
            displayPoints = selectTimelinePoints(daily)
        )
    }
}

private const val MAX_TIMELINE_POINTS = 8
private const val MAX_DAILY_POINTS = 7
private const val DISPLAY_RAIN_THRESHOLD_PERCENT = 50
private const val HOURLY_RAIN_THRESHOLD_MM = 0.1
private const val DAILY_RAIN_THRESHOLD_MM = 0.2
private const val MILLIS_PER_DAY = 86_400_000L
