package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.util.dailyCloudCoverMean
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** Origine du signal pluie affiché dans la chronologie. */
internal enum class PrecipitationSignalSource {
    /** Probabilité d'occurrence agrégée et équilibrée par familles. */
    MODEL_PROBABILITY,

    /** Mélange de probabilités natives et de votes déterministes humide/sec. */
    MIXED,

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
    /** Quantité centrale déterministe Consensus v2 (0 si P(pluie) < 50 %). */
    val precipitationMm: Double? = null,
    /** Médiane pondérée des seuls scénarios humides. */
    val precipitationConditionalMm: Double? = null,
    /** Espérance P(pluie) × quantité conditionnelle. */
    val precipitationExpectedMm: Double? = null,
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
    /** Nombre de lignées numériques indépendantes à cette échéance. */
    val familyCount: Int = 0,
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
    val indexed = forecast.seriesByModel.map { (model, series) -> indexHourlySnapshots(model, series) }
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
    val indexed = forecast.seriesByModel.map { (model, series) -> indexDailySnapshots(model, series, zone) }
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
    val model: WeatherModel,
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

private fun indexHourlySnapshots(model: WeatherModel, series: ForecastSeries): Map<Instant, TimelineSnapshot> = buildMap {
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
            model = model,
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
    model: WeatherModel,
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
            model = model,
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

    fun continuous(
        extractor: (TimelineSnapshot) -> Double?,
        tight: Double,
        wide: Double
    ) = ForecastConsensus.continuous(
        meaningful.mapNotNull { snap -> extractor(snap)?.let { ForecastConsensus.Entry(snap.model, it) } },
        tightStdDev = tight,
        wideStdDev = wide
    )

    val temp = continuous({ if (hourly) it.temperature else it.tempMax }, 0.5, 3.0)
    val tempMin = if (hourly) null else continuous({ it.tempMin }, 0.5, 3.0)
    val wind = continuous({ it.wind }, 2.0, 12.0)
    val gust = continuous({ it.windGust }, 2.0, 12.0)
    val cloud = continuous({ it.cloudCover?.toDouble() }, 10.0, 50.0)

    val rainThreshold = if (hourly) HOURLY_RAIN_THRESHOLD_MM else DAILY_RAIN_THRESHOLD_MM
    val precipitation = ForecastConsensus.precipitation(
        rows = meaningful.map { snap ->
            ForecastConsensus.PrecipitationRow(
                model = snap.model,
                amountMm = snap.precipitation,
                probabilityPercent = snap.precipitationProbability
            )
        },
        thresholdMm = rainThreshold,
        amountTightStdDev = if (hourly) 0.5 else 1.0,
        amountWideStdDev = if (hourly) 4.0 else 8.0
    )
    val conditionVote = ForecastConsensus.conditionVote(
        meaningful.mapNotNull { snap ->
            snap.condition?.takeUnless { it == WeatherCondition.UNKNOWN }
                ?.let { ForecastConsensus.Entry(snap.model, it) }
        }
    )

    fun metricLevel(score: Int): ModelConsensusLevel = when {
        score >= 75 -> ModelConsensusLevel.HIGH
        score >= 50 -> ModelConsensusLevel.MEDIUM
        else -> ModelConsensusLevel.LOW
    }

    val temperatureScores = listOfNotNull(temp.convergencePercent, tempMin?.convergencePercent)
    val temperaturePercent = if (temperatureScores.isEmpty()) null else temperatureScores.average().roundToInt()
    val metricConsensus = buildMap {
        temperaturePercent?.let { score ->
            put(ForecastMetric.TEMPERATURE, MetricConsensus(
                metric = ForecastMetric.TEMPERATURE, percent = score, modelCount = temp.modelCount,
                level = metricLevel(score), minimum = temp.stats?.min, maximum = temp.stats?.max,
                isDivergent = score < 50
            ))
        }
        precipitation.convergencePercent?.let { score ->
            put(ForecastMetric.PRECIPITATION, MetricConsensus(
                metric = ForecastMetric.PRECIPITATION, percent = score, modelCount = precipitation.modelCount,
                level = metricLevel(score), minimum = precipitation.minMm, maximum = precipitation.maxMm,
                isDivergent = score < 50
            ))
        }
        wind.convergencePercent?.let { score ->
            put(ForecastMetric.WIND, MetricConsensus(
                metric = ForecastMetric.WIND, percent = score, modelCount = wind.modelCount,
                level = metricLevel(score), minimum = wind.stats?.min, maximum = wind.stats?.max,
                isDivergent = score < 50
            ))
        }
        conditionVote.percent?.let { score ->
            put(ForecastMetric.CONDITION, MetricConsensus(
                metric = ForecastMetric.CONDITION, percent = score, modelCount = conditionVote.modelCount,
                level = metricLevel(score), isDivergent = score < 50
            ))
        }
    }
    val divergenceReasons = buildSet {
        if (metricConsensus[ForecastMetric.TEMPERATURE]?.isDivergent == true) add(DivergenceReason.TEMPERATURE)
        if (metricConsensus[ForecastMetric.PRECIPITATION]?.isDivergent == true) add(DivergenceReason.PRECIPITATION)
        if (metricConsensus[ForecastMetric.WIND]?.isDivergent == true) add(DivergenceReason.WIND)
        if (metricConsensus[ForecastMetric.CONDITION]?.isDivergent == true) add(DivergenceReason.CONDITION)
    }
    val overall = metricConsensus.values.takeIf { it.isNotEmpty() }
        ?.map { it.percent }?.average()?.roundToInt()?.coerceIn(0, 100)
    val source = when (precipitation.source) {
        ForecastConsensus.PrecipitationSource.PROBABILITY -> PrecipitationSignalSource.MODEL_PROBABILITY
        ForecastConsensus.PrecipitationSource.MIXED -> PrecipitationSignalSource.MIXED
        ForecastConsensus.PrecipitationSource.MODEL_AGREEMENT -> PrecipitationSignalSource.MODEL_AGREEMENT
        null -> null
    }
    val temperatures = meaningful.mapNotNull { if (hourly) it.temperature else it.tempMax }
    val precipitationValues = meaningful.mapNotNull { it.precipitation }
    val probabilities = meaningful.mapNotNull { it.precipitationProbability }
    val cloudValues = meaningful.mapNotNull { it.cloudCover }
    val windValues = meaningful.mapNotNull { it.wind }
    val gustValues = meaningful.mapNotNull { it.windGust }
    val familyCount = listOf(temp.familyCount, wind.familyCount, precipitation.familyCount, conditionVote.familyCount).maxOrNull() ?: 0

    return SimplifiedTimelinePoint(
        instant = timestamp, date = date,
        temperatureC = if (hourly) temp.central else null,
        tempMinC = tempMin?.central, tempMaxC = if (hourly) null else temp.central,
        temperatureMinAcrossModels = temperatures.minOrNull(), temperatureMaxAcrossModels = temperatures.maxOrNull(),
        precipitationPercent = precipitation.probabilityPercent, precipitationSource = source,
        precipitationModelCount = precipitation.modelCount, wetModelCount = precipitation.wetModelCount,
        precipitationMm = precipitation.centralAmountMm, precipitationConditionalMm = precipitation.conditionalAmountMm,
        precipitationExpectedMm = precipitation.expectedAmountMm,
        precipitationMinAcrossModelsMm = precipitationValues.minOrNull(), precipitationMaxAcrossModelsMm = precipitationValues.maxOrNull(),
        precipitationProbabilityMin = probabilities.minOrNull(), precipitationProbabilityMax = probabilities.maxOrNull(),
        cloudCoverPercent = cloud.central?.roundToInt()?.coerceIn(0, 100),
        cloudCoverMinAcrossModels = cloudValues.minOrNull(), cloudCoverMaxAcrossModels = cloudValues.maxOrNull(),
        cloudCoverModelCount = cloud.modelCount,
        windKmh = wind.central, windMinAcrossModels = windValues.minOrNull(), windMaxAcrossModels = windValues.maxOrNull(),
        windGustKmh = gust.central, windGustMinAcrossModels = gustValues.minOrNull(), windGustMaxAcrossModels = gustValues.maxOrNull(),
        windGustModelCount = gust.modelCount,
        condition = conditionVote.value, modelCount = meaningful.size, familyCount = familyCount,
        temperatureModelCount = temp.modelCount, windModelCount = wind.modelCount, conditionModelCount = conditionVote.modelCount,
        hasMultiModelEvidence = metricConsensus.isNotEmpty(), consensusPercent = overall,
        consensusLevel = overall?.let(::metricLevel), metricConsensus = metricConsensus, divergenceReasons = divergenceReasons
    )
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
private const val DAILY_RAIN_THRESHOLD_MM = 1.0
private const val MILLIS_PER_DAY = 86_400_000L
private const val REGULAR_TIMELINE_STEP_HOURS = 3L
