package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class ForecastInsightKind {
    HIGH_AGREEMENT,
    DISAGREEMENT,
    RAIN_LIKELY,
    RAIN_UNCERTAIN,
    WIND_RISING,
    TEMPERATURE_CHANGE
}

internal enum class ForecastInsightLevel {
    ALERT,
    WATCH,
    INFO,
    POSITIVE
}

/** Observation courte dérivée localement du consensus multi-modèles. */
internal data class ForecastInsight(
    val kind: ForecastInsightKind,
    val level: ForecastInsightLevel = ForecastInsightLevel.INFO,
    /** Plus la valeur est élevée, plus l'insight doit remonter dans la carte. */
    val priority: Int = 0,
    /** Échéance concernée par le message. */
    val point: SimplifiedTimelinePoint? = null,
    /** Valeur principale générique : pour la température, variation signée en degrés. */
    val value: Int? = null,
    val secondaryValue: Int? = null,
    val precipitationSource: PrecipitationSignalSource? = null,
    /** Point de départ explicite des messages comparatifs, notamment la température. */
    val referencePoint: SimplifiedTimelinePoint? = null,
    /** Température de consensus arrondie au point de départ. */
    val referenceValue: Int? = null,
    /** Température de consensus arrondie à l'échéance cible. */
    val targetValue: Int? = null,
    /** Causes explicites lorsqu'un message synthétise un désaccord. */
    val divergenceReasons: Set<DivergenceReason> = point?.divergenceReasons.orEmpty()
)

/**
 * Produit au maximum trois messages distincts depuis les points d'analyse
 * complets. La liste affichée par la chronologie peut être échantillonnée : les
 * insights, eux, ne perdent donc plus les événements situés entre deux cartes.
 */
internal fun buildForecastInsights(overview: OverviewTimeline): List<ForecastInsight> {
    val points = overview.analysisPoints
    if (points.isEmpty()) return emptyList()

    val candidates = mutableListOf<ForecastInsight>()

    points.firstOrNull(::isUncertainRainPoint)?.let { point ->
        candidates += rainInsight(
            kind = ForecastInsightKind.RAIN_UNCERTAIN,
            point = point,
            level = ForecastInsightLevel.WATCH,
            priority = 82 + rainIntensity(point) - urgencyPenalty(point, points)
        )
    }

    points.firstOrNull(::isLikelyRainPoint)?.let { point ->
        val severeCondition = point.condition in setOf(
            WeatherCondition.THUNDERSTORM,
            WeatherCondition.FREEZING_RAIN
        )
        val level = if (severeCondition || (point.precipitationPercent ?: 0) >= 85) {
            ForecastInsightLevel.ALERT
        } else {
            ForecastInsightLevel.WATCH
        }
        candidates += rainInsight(
            kind = ForecastInsightKind.RAIN_LIKELY,
            point = point,
            level = level,
            priority = 88 + rainIntensity(point) - urgencyPenalty(point, points)
        )
    }

    val windPoints = points.filter { it.windKmh != null && it.windModelCount >= 2 }
    val firstWindPoint = windPoints.firstOrNull()
    val baselineWind = firstWindPoint?.windKmh
    val windRisePoint = if (baselineWind == null) null else windPoints
        .filter { (it.windKmh ?: baselineWind) - baselineWind >= WIND_RISE_THRESHOLD_KMH }
        .maxByOrNull { it.windKmh ?: Double.NEGATIVE_INFINITY }
    if (windRisePoint != null && firstWindPoint != null && baselineWind != null) {
        val targetWind = windRisePoint.windKmh ?: baselineWind
        val level = when {
            targetWind >= 60.0 -> ForecastInsightLevel.ALERT
            targetWind >= 40.0 -> ForecastInsightLevel.WATCH
            else -> ForecastInsightLevel.INFO
        }
        candidates += ForecastInsight(
            kind = ForecastInsightKind.WIND_RISING,
            level = level,
            priority = 64 + targetWind.roundToInt() - urgencyPenalty(windRisePoint, points),
            point = windRisePoint,
            value = baselineWind.roundToInt(),
            secondaryValue = targetWind.roundToInt(),
            referencePoint = firstWindPoint,
            divergenceReasons = windRisePoint.divergenceReasons
        )
    }

    val divergentPoint = points
        .filter { it.isDivergent && it.hasMultiModelEvidence }
        .minWithOrNull(
            compareBy<SimplifiedTimelinePoint> { pointIndex(it, points) }
                .thenBy { it.consensusPercent ?: 100 }
        )
    if (divergentPoint != null) {
        val level = if (
            divergentPoint.divergenceReasons.size >= 2 &&
            (divergentPoint.consensusPercent ?: 100) < 35
        ) {
            ForecastInsightLevel.ALERT
        } else {
            ForecastInsightLevel.WATCH
        }
        candidates += ForecastInsight(
            kind = ForecastInsightKind.DISAGREEMENT,
            level = level,
            priority = 76 + divergentPoint.divergenceReasons.size * 5 -
                urgencyPenalty(divergentPoint, points),
            point = divergentPoint,
            divergenceReasons = divergentPoint.divergenceReasons
        )
    }

    val temperatures = points.mapNotNull { point ->
        if (point.temperatureModelCount < 2) return@mapNotNull null
        (point.temperatureC ?: point.tempMaxC)?.let { value -> point to value }
    }
    if (temperatures.size >= 2) {
        val (referencePoint, referenceTemperature) = temperatures.first()
        val strongest = temperatures.maxByOrNull { (_, value) -> abs(value - referenceTemperature) }
        val targetPoint = strongest?.first
        val targetTemperature = strongest?.second
        val delta = targetTemperature?.minus(referenceTemperature)?.roundToInt() ?: 0
        if (targetPoint != null && targetTemperature != null && abs(delta) >= TEMPERATURE_CHANGE_THRESHOLD_C) {
            candidates += ForecastInsight(
                kind = ForecastInsightKind.TEMPERATURE_CHANGE,
                level = if (abs(delta) >= 10) ForecastInsightLevel.WATCH else ForecastInsightLevel.INFO,
                priority = 58 + abs(delta) * 2 - urgencyPenalty(targetPoint, points),
                point = targetPoint,
                value = delta,
                referencePoint = referencePoint,
                referenceValue = referenceTemperature.roundToInt(),
                targetValue = targetTemperature.roundToInt(),
                divergenceReasons = targetPoint.divergenceReasons
            )
        }
    }

    val agreementWindow = points.take(MAX_AGREEMENT_POINTS)
    val hasAnyDisagreement = points.any { it.hasMultiModelEvidence && it.isDivergent } ||
        candidates.any { it.kind == ForecastInsightKind.DISAGREEMENT }
    val hasDefensibleAgreement = !hasAnyDisagreement &&
        agreementWindow.size >= 2 &&
        agreementWindow.all {
            it.hasMultiModelEvidence &&
                it.consensusLevel in setOf(
                    ModelConsensusLevel.HIGH,
                    ModelConsensusLevel.MEDIUM
                ) &&
                !it.isDivergent
        }
    if (hasDefensibleAgreement) {
        candidates += ForecastInsight(
            kind = ForecastInsightKind.HIGH_AGREEMENT,
            level = ForecastInsightLevel.POSITIVE,
            priority = 30,
            point = agreementWindow.last()
        )
    }

    val grouped = removeNearbyDuplicates(candidates, overview.mode)
    val nonPositive = grouped.filterNot { it.level == ForecastInsightLevel.POSITIVE }
    val eligible = if (nonPositive.isEmpty()) {
        grouped
    } else {
        // Le message positif peut compléter deux signaux utiles, mais ne doit
        // jamais prendre leur place dans une carte limitée à trois éléments.
        nonPositive + grouped.filter { it.level == ForecastInsightLevel.POSITIVE }
    }

    return eligible
        .distinctBy { it.kind }
        // « À retenir » se lit comme une mini-chronologie : l'événement le plus
        // proche doit toujours apparaître avant les échéances plus lointaines.
        // La gravité et la priorité ne départagent que les messages portant sur
        // la même échéance.
        .sortedWith(
            compareBy<ForecastInsight> { pointIndex(it.point, points) }
                .thenByDescending { insightLevelWeight(it.level) }
                .thenByDescending { it.priority }
        )
        .take(MAX_INSIGHTS)
}

private fun isUncertainRainPoint(point: SimplifiedTimelinePoint): Boolean {
    val signal = point.precipitationPercent ?: return false
    return point.precipitationModelCount >= 2 &&
        signal in 30..69 &&
        (point.precipitationSource == PrecipitationSignalSource.MODEL_PROBABILITY ||
            point.isRainDivergent)
}

private fun isLikelyRainPoint(point: SimplifiedTimelinePoint): Boolean {
    val signal = point.precipitationPercent ?: return false
    return point.precipitationModelCount >= 2 && signal >= 70
}

private fun rainIntensity(point: SimplifiedTimelinePoint): Int = when (point.precipitationSource) {
    PrecipitationSignalSource.MODEL_PROBABILITY -> (point.precipitationPercent ?: 0) / 5
    PrecipitationSignalSource.MODEL_AGREEMENT -> {
        val total = point.precipitationModelCount.coerceAtLeast(1)
        (point.wetModelCount * 20 / total)
    }
    null -> 0
}

private fun rainInsight(
    kind: ForecastInsightKind,
    point: SimplifiedTimelinePoint,
    level: ForecastInsightLevel,
    priority: Int
): ForecastInsight = when (point.precipitationSource) {
    PrecipitationSignalSource.MODEL_PROBABILITY -> ForecastInsight(
        kind = kind,
        level = level,
        priority = priority,
        point = point,
        value = point.precipitationPercent,
        secondaryValue = point.precipitationModelCount,
        precipitationSource = point.precipitationSource,
        divergenceReasons = point.divergenceReasons
    )
    PrecipitationSignalSource.MODEL_AGREEMENT -> ForecastInsight(
        kind = kind,
        level = level,
        priority = priority,
        point = point,
        value = point.wetModelCount,
        secondaryValue = point.precipitationModelCount,
        precipitationSource = point.precipitationSource,
        divergenceReasons = point.divergenceReasons
    )
    null -> ForecastInsight(
        kind = kind,
        level = level,
        priority = priority,
        point = point,
        divergenceReasons = point.divergenceReasons
    )
}

/**
 * Évite deux phrases sur le même phénomène dans une fenêtre courte. Un message
 * météo précis (pluie, vent, température) absorbe le désaccord générique si la
 * variable correspondante est déjà explicitée.
 */
private fun removeNearbyDuplicates(
    candidates: List<ForecastInsight>,
    mode: DisplayMode
): List<ForecastInsight> {
    val specifics = candidates.filterNot {
        it.kind == ForecastInsightKind.DISAGREEMENT ||
            it.kind == ForecastInsightKind.HIGH_AGREEMENT
    }

    val withoutRedundantDisagreement = candidates.filterNot { candidate ->
        if (candidate.kind != ForecastInsightKind.DISAGREEMENT) return@filterNot false
        specifics.any { specific ->
            pointsAreNear(candidate.point, specific.point, mode) &&
                specificReason(specific.kind)?.let { it in candidate.divergenceReasons } == true
        }
    }

    val result = mutableListOf<ForecastInsight>()
    withoutRedundantDisagreement
        .sortedByDescending { it.priority }
        .forEach { candidate ->
            val sameFamilyNearby = result.indexOfFirst { existing ->
                insightFamily(existing.kind) == insightFamily(candidate.kind) &&
                    pointsAreNear(existing.point, candidate.point, mode)
            }
            if (sameFamilyNearby < 0) {
                result += candidate
            } else if (candidate.priority > result[sameFamilyNearby].priority) {
                result[sameFamilyNearby] = candidate
            }
        }
    return result
}

private fun insightFamily(kind: ForecastInsightKind): String = when (kind) {
    ForecastInsightKind.RAIN_LIKELY,
    ForecastInsightKind.RAIN_UNCERTAIN -> "rain"
    ForecastInsightKind.DISAGREEMENT -> "disagreement"
    ForecastInsightKind.WIND_RISING -> "wind"
    ForecastInsightKind.TEMPERATURE_CHANGE -> "temperature"
    ForecastInsightKind.HIGH_AGREEMENT -> "agreement"
}

private fun specificReason(kind: ForecastInsightKind): DivergenceReason? = when (kind) {
    ForecastInsightKind.RAIN_LIKELY,
    ForecastInsightKind.RAIN_UNCERTAIN -> DivergenceReason.PRECIPITATION
    ForecastInsightKind.WIND_RISING -> DivergenceReason.WIND
    ForecastInsightKind.TEMPERATURE_CHANGE -> DivergenceReason.TEMPERATURE
    ForecastInsightKind.HIGH_AGREEMENT,
    ForecastInsightKind.DISAGREEMENT -> null
}

private fun pointsAreNear(
    first: SimplifiedTimelinePoint?,
    second: SimplifiedTimelinePoint?,
    mode: DisplayMode
): Boolean {
    if (first == null || second == null) return false
    if (sameTimelinePoint(first, second)) return true
    return when (mode) {
        DisplayMode.HOURLY -> {
            val firstInstant = first.instant ?: return false
            val secondInstant = second.instant ?: return false
            abs(Duration.between(firstInstant, secondInstant).toHours()) <= 2
        }
        DisplayMode.DAILY -> {
            val firstDate = first.date ?: return false
            val secondDate = second.date ?: return false
            abs(firstDate.toEpochDay() - secondDate.toEpochDay()) <= 1
        }
    }
}

private fun pointIndex(
    point: SimplifiedTimelinePoint?,
    points: List<SimplifiedTimelinePoint>
): Int {
    if (point == null) return Int.MAX_VALUE
    return points.indexOfFirst { sameTimelinePoint(it, point) }
        .takeIf { it >= 0 }
        ?: Int.MAX_VALUE
}

private fun urgencyPenalty(
    point: SimplifiedTimelinePoint,
    points: List<SimplifiedTimelinePoint>
): Int = pointIndex(point, points).coerceAtLeast(0).coerceAtMost(12)

private fun insightLevelWeight(level: ForecastInsightLevel): Int = when (level) {
    ForecastInsightLevel.ALERT -> 4
    ForecastInsightLevel.WATCH -> 3
    ForecastInsightLevel.INFO -> 2
    ForecastInsightLevel.POSITIVE -> 1
}

private const val MAX_INSIGHTS = 3
private const val MAX_AGREEMENT_POINTS = 4
private const val WIND_RISE_THRESHOLD_KMH = 15.0
private const val TEMPERATURE_CHANGE_THRESHOLD_C = 6
