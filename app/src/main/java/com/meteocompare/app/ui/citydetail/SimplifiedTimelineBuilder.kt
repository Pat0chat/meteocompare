package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import com.meteocompare.app.domain.usecase.ForecastEngineV3
import com.meteocompare.app.domain.util.dailyCloudCoverMean
import com.meteocompare.app.domain.util.resolveDailyCondition
import com.meteocompare.app.domain.util.resolveHourlyCondition
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

/** Niveau synthétique de convergence multi-modèles à une échéance. */
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
    /** Quantité centrale déterministe consensus robuste (0 si P(pluie) < 50 %). */
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
    /** Vrai uniquement si au moins deux familles indépendantes partagent une même métrique. */
    val hasMultiModelEvidence: Boolean = false,
    /** Score synthétique de convergence, calculé seulement à partir de métriques comparables. */
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
    now: Instant = Instant.now(),
    engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
): List<SimplifiedTimelinePoint> = when (mode) {
    DisplayMode.HOURLY -> buildHourlyTimeline(forecast, now, engineContext)
    DisplayMode.DAILY -> buildDailyTimeline(forecast, now, engineContext)
}

private fun buildHourlyTimeline(
    forecast: CityForecast,
    now: Instant,
    engineContext: ForecastEngineContext
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
        timelinePoint(timestamp = timestamp, date = null, snapshots = snapshots, hourly = true, engineContext = engineContext)
    }
}

private fun buildDailyTimeline(
    forecast: CityForecast,
    now: Instant,
    engineContext: ForecastEngineContext
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
        timelinePoint(timestamp = null, date = date, snapshots = snapshots, hourly = false, engineContext = engineContext)
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
    val condition: WeatherCondition?,
    val nativeCondition: WeatherCondition? = null
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
        val condition = nativeCondition ?: series.resolveHourlyCondition(index)
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
            condition = condition,
            nativeCondition = nativeCondition
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
        val condition = nativeCondition ?: series.resolveDailyCondition(date, zone)?.condition
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
            condition = condition,
            nativeCondition = nativeCondition
        )
        if (snapshot.hasAnyValue) put(date, snapshot)
    }
}

private fun timelinePoint(
    timestamp: Instant?,
    date: LocalDate?,
    snapshots: List<TimelineSnapshot>,
    hourly: Boolean,
    engineContext: ForecastEngineContext
): SimplifiedTimelinePoint? {
    val meaningful = snapshots.filter(TimelineSnapshot::hasAnyValue)
    if (meaningful.isEmpty()) return null

    data class ContinuousPair(
        val agreement: ForecastConsensus.Continuous,
        val forecastValue: ForecastEngineV3.ContinuousResult
    )

    fun continuous(
        extractor: (TimelineSnapshot) -> Double?,
        variable: ForecastEngineVariable,
        tight: Double,
        wide: Double,
        allowCalibration: Boolean,
        min: Double? = null,
        max: Double? = null
    ): ContinuousPair {
        val entries = meaningful.mapNotNull { snap ->
            extractor(snap)?.let { ForecastConsensus.Entry(snap.model, it) }
        }
        val agreement = ForecastConsensus.continuous(entries, tightStdDev = tight, wideStdDev = wide)
        val forecastValue = ForecastEngineV3.continuous(
            entries,
            ForecastEngineV3.ContinuousOptions(
                engine = engineContext.engine,
                calibration = engineContext.calibration(variable, allowCalibration),
                localWeights = engineContext.localWeights(variable),
                tight = tight,
                wide = wide,
                min = min,
                max = max
            )
        )
        return ContinuousPair(agreement, forecastValue)
    }

    // Même garde-fou que la version Web 1.16 : l'historique de biais est J+1.
    // Il peut corriger Tmax et vent max journalier, mais pas les heures, Tmin,
    // rafales ou nuages dont la sémantique de calibration serait différente.
    val temp = continuous(
        { if (hourly) it.temperature else it.tempMax }, ForecastEngineVariable.TEMPERATURE,
        0.5, 3.0, allowCalibration = !hourly
    )
    val tempMin = if (hourly) null else continuous(
        { it.tempMin }, ForecastEngineVariable.TEMPERATURE, 0.5, 3.0, allowCalibration = false
    )
    val wind = continuous(
        { it.wind }, ForecastEngineVariable.WIND, 2.0, 12.0, allowCalibration = !hourly, min = 0.0
    )
    val gust = continuous(
        { it.windGust }, ForecastEngineVariable.WIND, 2.0, 12.0, allowCalibration = false, min = 0.0
    )
    val cloud = continuous(
        { it.cloudCover?.toDouble() }, ForecastEngineVariable.CLOUD, 10.0, 50.0,
        allowCalibration = false, min = 0.0, max = 100.0
    )

    val rainThreshold = if (hourly) HOURLY_RAIN_THRESHOLD_MM else DAILY_RAIN_THRESHOLD_MM
    val precipitationRows = meaningful.map { snap ->
        ForecastConsensus.PrecipitationRow(
            model = snap.model,
            amountMm = snap.precipitation,
            probabilityPercent = snap.precipitationProbability
        )
    }
    val precipitation = ForecastConsensus.precipitation(
        rows = precipitationRows,
        thresholdMm = rainThreshold,
        amountTightStdDev = if (hourly) 0.5 else 1.0,
        amountWideStdDev = if (hourly) 4.0 else 8.0
    )
    val precipitationForecast = ForecastEngineV3.precipitation(
        precipitationRows,
        ForecastEngineV3.PrecipitationOptions(
            engine = engineContext.engine,
            threshold = rainThreshold,
            localWeights = engineContext.localWeights(ForecastEngineVariable.PRECIPITATION),
            calibration = engineContext.calibration(ForecastEngineVariable.PRECIPITATION, allowCalibration = !hourly),
            amountTight = if (hourly) 0.5 else 1.0,
            amountWide = if (hourly) 4.0 else 8.0
        )
    )
    val conditionResolution = WeatherConditionConsensus.resolveAggregate(
        nativeEntries = meaningful.mapNotNull { snap ->
            snap.nativeCondition?.let { ForecastConsensus.Entry(snap.model, it) }
        },
        temperatureCentralC = if (hourly) {
            temp.forecastValue.central
        } else {
            tempMin?.forecastValue?.central ?: temp.forecastValue.central
        },
        precipitationCentralMm = precipitationForecast.centralAmountMm,
        cloudCoverPercent = cloud.forecastValue.central,
        supportModels = meaningful.map { it.model },
        localWeights = emptyMap()
    )
    val conditionConsensus = conditionResolution.vote

    fun metricLevel(score: Int): ModelConsensusLevel = when {
        score >= 75 -> ModelConsensusLevel.HIGH
        score >= 50 -> ModelConsensusLevel.MEDIUM
        else -> ModelConsensusLevel.LOW
    }

    val temperatureScores = listOfNotNull(temp.agreement.convergencePercent, tempMin?.agreement?.convergencePercent)
    val temperaturePercent = if (temperatureScores.isEmpty()) null else temperatureScores.average().roundToInt()
    val metricConsensus = buildMap {
        temperaturePercent?.let { score ->
            put(ForecastMetric.TEMPERATURE, MetricConsensus(
                metric = ForecastMetric.TEMPERATURE, percent = score, modelCount = temp.agreement.modelCount,
                level = metricLevel(score), minimum = temp.agreement.stats?.min, maximum = temp.agreement.stats?.max,
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
        wind.agreement.convergencePercent?.let { score ->
            put(ForecastMetric.WIND, MetricConsensus(
                metric = ForecastMetric.WIND, percent = score, modelCount = wind.agreement.modelCount,
                level = metricLevel(score), minimum = wind.agreement.stats?.min, maximum = wind.agreement.stats?.max,
                isDivergent = score < 50
            ))
        }
        conditionConsensus.percent?.let { score ->
            put(ForecastMetric.CONDITION, MetricConsensus(
                metric = ForecastMetric.CONDITION, percent = score, modelCount = conditionConsensus.modelCount,
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
    val familyCount = listOf(temp.agreement.familyCount, wind.agreement.familyCount, precipitation.familyCount, conditionConsensus.familyCount).maxOrNull() ?: 0

    return SimplifiedTimelinePoint(
        instant = timestamp, date = date,
        temperatureC = if (hourly) temp.forecastValue.central else null,
        tempMinC = tempMin?.forecastValue?.central, tempMaxC = if (hourly) null else temp.forecastValue.central,
        temperatureMinAcrossModels = temperatures.minOrNull(), temperatureMaxAcrossModels = temperatures.maxOrNull(),
        precipitationPercent = precipitationForecast.probabilityPercent ?: precipitation.probabilityPercent, precipitationSource = source,
        precipitationModelCount = precipitation.modelCount, wetModelCount = precipitation.wetModelCount,
        precipitationMm = precipitationForecast.centralAmountMm, precipitationConditionalMm = precipitationForecast.conditionalAmountMm,
        precipitationExpectedMm = precipitationForecast.expectedAmountMm,
        precipitationMinAcrossModelsMm = precipitationValues.minOrNull(), precipitationMaxAcrossModelsMm = precipitationValues.maxOrNull(),
        precipitationProbabilityMin = probabilities.minOrNull(), precipitationProbabilityMax = probabilities.maxOrNull(),
        cloudCoverPercent = cloud.forecastValue.central?.roundToInt()?.coerceIn(0, 100),
        cloudCoverMinAcrossModels = cloudValues.minOrNull(), cloudCoverMaxAcrossModels = cloudValues.maxOrNull(),
        cloudCoverModelCount = cloud.agreement.modelCount,
        windKmh = wind.forecastValue.central, windMinAcrossModels = windValues.minOrNull(), windMaxAcrossModels = windValues.maxOrNull(),
        windGustKmh = gust.forecastValue.central, windGustMinAcrossModels = gustValues.minOrNull(), windGustMaxAcrossModels = gustValues.maxOrNull(),
        windGustModelCount = gust.agreement.modelCount,
        condition = conditionConsensus.value, modelCount = meaningful.size, familyCount = familyCount,
        temperatureModelCount = temp.agreement.modelCount, windModelCount = wind.agreement.modelCount, conditionModelCount = conditionConsensus.modelCount,
        hasMultiModelEvidence = metricConsensus.isNotEmpty(), consensusPercent = overall,
        consensusLevel = overall?.let(::metricLevel), metricConsensus = metricConsensus, divergenceReasons = divergenceReasons
    )
}

/**
 * Sélectionne une grille temporelle prévisible. En mode horaire, une carte est
 * affichée pour chaque heure de la fenêtre de 24 h à partir de la première
 * échéance disponible. Les événements ne déplacent pas les cartes : ils sont
 * portés par la réglette dédiée dans [SimplifiedTimelineCard].
 */
internal fun selectRegularTimelinePoints(
    points: List<SimplifiedTimelinePoint>,
    maxPoints: Int = MAX_TIMELINE_POINTS,
    stepHours: Long = REGULAR_TIMELINE_STEP_HOURS
): List<SimplifiedTimelinePoint> {
    if (maxPoints <= 0 || points.isEmpty()) return emptyList()
    val ordered = points.sortedBy(::timelineSortKey)
    if (ordered.first().instant == null) {
        return ordered.take(minOf(maxPoints, MAX_DAILY_TIMELINE_DISPLAY_POINTS))
    }

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
    now: Instant = Instant.now(),
    engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
): OverviewTimeline {
    val hourly = buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, now, engineContext)
    return if (hourly.size >= 2) {
        OverviewTimeline(
            mode = DisplayMode.HOURLY,
            analysisPoints = hourly,
            timezone = forecast.city.timezone
        )
    } else {
        val daily = buildSimplifiedTimeline(forecast, DisplayMode.DAILY, now, engineContext)
        OverviewTimeline(
            mode = DisplayMode.DAILY,
            analysisPoints = daily,
            timezone = forecast.city.timezone
        )
    }
}

private const val MAX_TIMELINE_POINTS = 24
private const val MAX_DAILY_POINTS = 7
private const val MAX_DAILY_TIMELINE_DISPLAY_POINTS = 8
private const val HOURLY_RAIN_THRESHOLD_MM = PrecipitationThresholds.HOURLY_OCCURRENCE_MM
private const val DAILY_RAIN_THRESHOLD_MM = PrecipitationThresholds.DAILY_OCCURRENCE_MM
private const val MILLIS_PER_DAY = 86_400_000L
private const val REGULAR_TIMELINE_STEP_HOURS = 1L
