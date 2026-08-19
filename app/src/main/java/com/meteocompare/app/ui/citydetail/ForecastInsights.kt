package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Duration
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class ForecastInsightKind {
    HIGH_AGREEMENT,
    DISAGREEMENT,
    RAIN_LIKELY,
    RAIN_UNCERTAIN,
    WEATHER_CHANGE,
    WIND_EVENT,
    TEMPERATURE_CHANGE
}

internal enum class ForecastInsightLevel {
    ALERT,
    WATCH,
    INFO,
    POSITIVE
}

internal enum class ForecastEventKind {
    PRECIPITATION,
    WIND,
    TEMPERATURE,
    WEATHER_TRANSITION,
    UNCERTAINTY,
    STABLE
}

internal data class ForecastEventEvidence(
    val metric: ForecastMetric? = null,
    val consensus: MetricConsensus? = null,
    val availableModelCount: Int = 0,
    val contributingModelCount: Int = 0,
    val wetModelCount: Int = 0,
    val medianValue: Double? = null,
    val minimumValue: Double? = null,
    val maximumValue: Double? = null,
    val probabilityMinimum: Int? = null,
    val probabilityMaximum: Int? = null,
    val divergenceReasons: Set<DivergenceReason> = emptySet()
)

/**
 * Événement météo structuré partagé par la sélection éditoriale et la timeline.
 * [insightSeed] conserve uniquement les paramètres nécessaires au message final.
 */
internal data class ForecastEvent(
    val kind: ForecastEventKind,
    val impact: ForecastInsightLevel,
    val priority: Int,
    val startPoint: SimplifiedTimelinePoint,
    val peakPoint: SimplifiedTimelinePoint = startPoint,
    val endPoint: SimplifiedTimelinePoint? = null,
    val evidence: ForecastEventEvidence = ForecastEventEvidence(),
    val condition: WeatherCondition? = null,
    /** Graine de présentation, enrichie après la sélection des événements. */
    val insightSeed: ForecastInsight? = null
)

/** Observation courte dérivée localement du consensus multi-modèles. */
internal data class ForecastInsight(
    val kind: ForecastInsightKind,
    val level: ForecastInsightLevel = ForecastInsightLevel.INFO,
    /** Départage les messages de même niveau lors de la sélection finale. */
    val priority: Int = 0,
    /** Échéance principale concernée par le message. */
    val point: SimplifiedTimelinePoint? = null,
    /** Dernière échéance du phénomène lorsqu'il persiste. */
    val endPoint: SimplifiedTimelinePoint? = null,
    /** Nombre d'échéances consécutives couvertes par le phénomène. */
    val eventPointCount: Int = 1,
    /** Valeur principale générique : pour la température, variation signée en degrés. */
    val value: Int? = null,
    val secondaryValue: Int? = null,
    val precipitationSource: PrecipitationSignalSource? = null,
    /** Point de départ explicite des messages comparatifs. */
    val referencePoint: SimplifiedTimelinePoint? = null,
    /** Valeur de consensus arrondie au point de départ. */
    val referenceValue: Int? = null,
    /** Valeur de consensus arrondie à l'échéance cible. */
    val targetValue: Int? = null,
    /** Conditions dominantes avant et après un changement météo notable. */
    val referenceCondition: WeatherCondition? = null,
    val targetCondition: WeatherCondition? = null,
    /** Causes explicites lorsqu'un message synthétise un désaccord. */
    val divergenceReasons: Set<DivergenceReason> = point?.divergenceReasons.orEmpty(),
    /** Événement structuré à l'origine du message. */
    val event: ForecastEvent? = null
) {
    val isPersistent: Boolean
        get() = eventPointCount >= 2 && point != null && endPoint != null &&
            !sameTimelinePoint(point, endPoint)

    val isStrengtheningRainSignal: Boolean
        get() = kind == ForecastInsightKind.RAIN_LIKELY &&
            referencePoint != null && referenceValue != null && targetValue != null &&
            targetValue > referenceValue
}

/**
 * Produit au maximum trois messages réellement utiles depuis les points
 * d'analyse complets. La génération privilégie les phénomènes persistants ou
 * intenses, puis la sélection garantit qu'un signal important plus lointain
 * n'est pas évincé par une évolution mineure plus proche.
 */
internal fun detectForecastEvents(overview: OverviewTimeline): List<ForecastEvent> {
    val points = overview.analysisPoints.sortedBy(::insightSortKey)
    if (points.isEmpty()) return emptyList()

    val candidateInsights = buildCandidateInsights(overview)
        .filter(::isEditoriallyUsefulInsight)
    val specific = candidateInsights.filterNot { it.kind == ForecastInsightKind.DISAGREEMENT }
        .map { insight -> enrichEventEvidence(forecastEventFromInsight(insight), points) }
        .toMutableList()
    if (specific.none { it.kind == ForecastEventKind.TEMPERATURE }) {
        buildTemperatureThresholdEvent(points)?.let(specific::add)
    }
    val disagreements = candidateInsights.filter { it.kind == ForecastInsightKind.DISAGREEMENT }

    disagreements.forEach { disagreement ->
        val point = disagreement.point ?: return@forEach
        val nearbyIndex = specific.indexOfFirst { event ->
            event.kind != ForecastEventKind.STABLE &&
                eventNearPoint(event, point, overview.mode) &&
                eventCoveredReasons(event).intersect(disagreement.divergenceReasons).isNotEmpty()
        }
        if (nearbyIndex >= 0) {
            val current = specific[nearbyIndex]
            specific[nearbyIndex] = current.copy(
                evidence = current.evidence.copy(
                    divergenceReasons = current.evidence.divergenceReasons +
                        disagreement.divergenceReasons
                )
            )
        } else {
            specific += enrichEventEvidence(forecastEventFromInsight(disagreement), points)
        }
    }

    return specific
        .distinctBy { event -> event.kind to timelinePointKey(event.startPoint) }
        .sortedWith(
            compareBy<ForecastEvent> { insightSortKey(it.startPoint) }
                .thenByDescending { insightLevelWeight(it.impact) }
                .thenByDescending(ForecastEvent::priority)
        )
}

internal fun buildForecastInsights(overview: OverviewTimeline): List<ForecastInsight> =
    buildForecastInsights(detectForecastEvents(overview))

internal fun buildForecastInsights(events: List<ForecastEvent>): List<ForecastInsight> =
    selectMostRelevantEvents(events).map { event ->
        val source = requireNotNull(event.insightSeed)
        source.copy(
            level = event.impact,
            priority = event.priority,
            point = event.peakPoint,
            endPoint = event.endPoint,
            divergenceReasons = event.evidence.divergenceReasons,
            event = event
        )
    }

private fun selectMostRelevantEvents(events: List<ForecastEvent>): List<ForecastEvent> {
    val concrete = events.filter { it.kind != ForecastEventKind.STABLE }
    val candidates = if (concrete.isNotEmpty()) concrete else events
    return candidates
        .sortedWith(
            compareByDescending<ForecastEvent> { insightLevelWeight(it.impact) }
                .thenByDescending(ForecastEvent::priority)
                .thenBy { insightSortKey(it.startPoint) }
        )
        .take(MAX_INSIGHTS)
        .sortedBy { insightSortKey(it.startPoint) }
}

private fun buildTemperatureThresholdEvent(
    points: List<SimplifiedTimelinePoint>
): ForecastEvent? {
    val temperaturePoints = points.filter {
        it.temperatureModelCount >= 2 && (it.temperatureC ?: it.tempMaxC) != null
    }
    val pair = temperaturePoints.zipWithNext().firstOrNull { (before, after) ->
        val start = before.temperatureC ?: before.tempMaxC ?: return@firstOrNull false
        val target = after.temperatureC ?: after.tempMaxC ?: return@firstOrNull false
        (start > 0.0 && target <= 0.0) ||
            (start < 30.0 && target >= 30.0) ||
            target <= -3.0 || target >= 35.0
    } ?: return null
    val (reference, targetPoint) = pair
    val referenceTemperature = reference.temperatureC ?: reference.tempMaxC ?: return null
    val targetTemperature = targetPoint.temperatureC ?: targetPoint.tempMaxC ?: return null
    val referenceValue = referenceTemperature.roundToInt()
    val targetValue = targetTemperature.roundToInt()
    val level = if (targetValue <= -3 || targetValue >= 35) {
        ForecastInsightLevel.ALERT
    } else {
        ForecastInsightLevel.WATCH
    }
    val source = ForecastInsight(
        kind = ForecastInsightKind.TEMPERATURE_CHANGE,
        level = level,
        priority = 88 + abs(targetValue - referenceValue),
        point = targetPoint,
        referencePoint = reference,
        referenceValue = referenceValue,
        targetValue = targetValue,
        value = targetValue - referenceValue,
        divergenceReasons = targetPoint.divergenceReasons
    )
    return forecastEventFromInsight(source)
}

private fun isEditoriallyUsefulInsight(insight: ForecastInsight): Boolean {
    if (insight.kind != ForecastInsightKind.TEMPERATURE_CHANGE) return true
    val start = insight.referenceValue ?: return false
    val target = insight.targetValue ?: return false
    val crossesFreeze = (start > 0 && target <= 0) || target <= -3
    val crossesHeat = (start < 30 && target >= 30) || target >= 35
    val hasTemperatureUncertainty = DivergenceReason.TEMPERATURE in insight.divergenceReasons
    val unusualShift = insight.level == ForecastInsightLevel.WATCH && abs(target - start) >= 7
    return crossesFreeze || crossesHeat || hasTemperatureUncertainty || unusualShift
}

private fun forecastEventFromInsight(insight: ForecastInsight): ForecastEvent {
    val point = requireNotNull(insight.point)
    val metric = when (insight.kind) {
        ForecastInsightKind.RAIN_LIKELY,
        ForecastInsightKind.RAIN_UNCERTAIN -> ForecastMetric.PRECIPITATION
        ForecastInsightKind.WIND_EVENT -> ForecastMetric.WIND
        ForecastInsightKind.TEMPERATURE_CHANGE -> ForecastMetric.TEMPERATURE
        ForecastInsightKind.WEATHER_CHANGE -> ForecastMetric.CONDITION
        ForecastInsightKind.DISAGREEMENT -> primaryDivergenceMetric(insight.divergenceReasons)
        ForecastInsightKind.HIGH_AGREEMENT -> null
    }
    val kind = when (insight.kind) {
        ForecastInsightKind.RAIN_LIKELY,
        ForecastInsightKind.RAIN_UNCERTAIN -> ForecastEventKind.PRECIPITATION
        ForecastInsightKind.WIND_EVENT -> ForecastEventKind.WIND
        ForecastInsightKind.TEMPERATURE_CHANGE -> ForecastEventKind.TEMPERATURE
        ForecastInsightKind.WEATHER_CHANGE -> ForecastEventKind.WEATHER_TRANSITION
        ForecastInsightKind.DISAGREEMENT -> ForecastEventKind.UNCERTAINTY
        ForecastInsightKind.HIGH_AGREEMENT -> ForecastEventKind.STABLE
    }
    val consensus = metric?.let(point::consensusFor)
    val evidence = ForecastEventEvidence(
        metric = metric,
        consensus = consensus,
        availableModelCount = point.modelCount,
        contributingModelCount = consensus?.modelCount ?: when (metric) {
            ForecastMetric.PRECIPITATION -> point.precipitationModelCount
            ForecastMetric.WIND -> point.windModelCount
            ForecastMetric.TEMPERATURE -> point.temperatureModelCount
            ForecastMetric.CONDITION -> point.conditionModelCount
            null -> point.modelCount
        },
        wetModelCount = point.wetModelCount,
        medianValue = when (metric) {
            ForecastMetric.PRECIPITATION -> point.precipitationMm
            ForecastMetric.WIND -> point.windKmh
            ForecastMetric.TEMPERATURE -> point.temperatureC ?: point.tempMaxC
            else -> null
        },
        minimumValue = when (metric) {
            ForecastMetric.PRECIPITATION -> point.precipitationMinAcrossModelsMm
            ForecastMetric.WIND -> point.windMinAcrossModels
            ForecastMetric.TEMPERATURE -> point.temperatureMinAcrossModels
            else -> consensus?.minimum
        },
        maximumValue = when (metric) {
            ForecastMetric.PRECIPITATION -> point.precipitationMaxAcrossModelsMm
            ForecastMetric.WIND -> point.windMaxAcrossModels
            ForecastMetric.TEMPERATURE -> point.temperatureMaxAcrossModels
            else -> consensus?.maximum
        },
        probabilityMinimum = point.precipitationProbabilityMin,
        probabilityMaximum = point.precipitationProbabilityMax,
        divergenceReasons = insight.divergenceReasons
    )
    return ForecastEvent(
        kind = kind,
        impact = insight.level,
        priority = insight.priority,
        startPoint = insight.referencePoint ?: point,
        peakPoint = point,
        endPoint = insight.endPoint,
        evidence = evidence,
        condition = insight.targetCondition ?: point.condition,
        insightSeed = insight
    )
}

private fun enrichEventEvidence(
    event: ForecastEvent,
    points: List<SimplifiedTimelinePoint>
): ForecastEvent {
    val window = points.filter { point -> pointInsideEventWindow(point, event) }
        .ifEmpty { listOf(event.peakPoint) }
    val metric = event.evidence.metric
    val consensus = metric?.let { selectedMetric ->
        window.mapNotNull { it.consensusFor(selectedMetric) }
            .minByOrNull(MetricConsensus::percent)
    } ?: event.evidence.consensus
    val evidence = when (metric) {
        ForecastMetric.PRECIPITATION -> event.evidence.copy(
            consensus = consensus,
            availableModelCount = window.maxOfOrNull(SimplifiedTimelinePoint::modelCount) ?: 0,
            contributingModelCount = consensus?.modelCount
                ?: window.maxOfOrNull(SimplifiedTimelinePoint::precipitationModelCount) ?: 0,
            wetModelCount = window.maxOfOrNull(SimplifiedTimelinePoint::wetModelCount) ?: 0,
            medianValue = window.mapNotNull(SimplifiedTimelinePoint::precipitationMm)
                .takeIf { it.isNotEmpty() }?.sum(),
            minimumValue = window.mapNotNull(SimplifiedTimelinePoint::precipitationMinAcrossModelsMm)
                .takeIf { it.isNotEmpty() }?.sum(),
            maximumValue = window.mapNotNull(SimplifiedTimelinePoint::precipitationMaxAcrossModelsMm)
                .takeIf { it.isNotEmpty() }?.sum(),
            probabilityMinimum = window.mapNotNull(SimplifiedTimelinePoint::precipitationProbabilityMin)
                .minOrNull(),
            probabilityMaximum = window.mapNotNull(SimplifiedTimelinePoint::precipitationProbabilityMax)
                .maxOrNull(),
            divergenceReasons = event.evidence.divergenceReasons +
                window.flatMap(SimplifiedTimelinePoint::divergenceReasons)
        )
        ForecastMetric.WIND -> event.evidence.copy(
            consensus = consensus,
            availableModelCount = window.maxOfOrNull(SimplifiedTimelinePoint::modelCount) ?: 0,
            contributingModelCount = consensus?.modelCount
                ?: window.maxOfOrNull(SimplifiedTimelinePoint::windModelCount) ?: 0,
            medianValue = window.mapNotNull(SimplifiedTimelinePoint::windKmh).maxOrNull(),
            minimumValue = window.mapNotNull(SimplifiedTimelinePoint::windMinAcrossModels).minOrNull(),
            maximumValue = window.mapNotNull(SimplifiedTimelinePoint::windMaxAcrossModels).maxOrNull(),
            divergenceReasons = event.evidence.divergenceReasons +
                window.flatMap(SimplifiedTimelinePoint::divergenceReasons)
        )
        ForecastMetric.TEMPERATURE -> event.evidence.copy(
            consensus = consensus,
            availableModelCount = window.maxOfOrNull(SimplifiedTimelinePoint::modelCount) ?: 0,
            contributingModelCount = consensus?.modelCount
                ?: window.maxOfOrNull(SimplifiedTimelinePoint::temperatureModelCount) ?: 0,
            medianValue = event.peakPoint.temperatureC ?: event.peakPoint.tempMaxC,
            minimumValue = window.mapNotNull(SimplifiedTimelinePoint::temperatureMinAcrossModels).minOrNull(),
            maximumValue = window.mapNotNull(SimplifiedTimelinePoint::temperatureMaxAcrossModels).maxOrNull(),
            divergenceReasons = event.evidence.divergenceReasons +
                window.flatMap(SimplifiedTimelinePoint::divergenceReasons)
        )
        ForecastMetric.CONDITION -> event.evidence.copy(
            consensus = consensus,
            availableModelCount = window.maxOfOrNull(SimplifiedTimelinePoint::modelCount) ?: 0,
            contributingModelCount = consensus?.modelCount
                ?: window.maxOfOrNull(SimplifiedTimelinePoint::conditionModelCount) ?: 0,
            divergenceReasons = event.evidence.divergenceReasons +
                window.flatMap(SimplifiedTimelinePoint::divergenceReasons)
        )
        null -> event.evidence.copy(
            divergenceReasons = event.evidence.divergenceReasons +
                window.flatMap(SimplifiedTimelinePoint::divergenceReasons)
        )
    }
    return event.copy(evidence = evidence)
}

private fun pointInsideEventWindow(
    point: SimplifiedTimelinePoint,
    event: ForecastEvent
): Boolean = when {
    point.instant != null && event.startPoint.instant != null -> {
        val end = event.endPoint?.instant ?: event.peakPoint.instant ?: event.startPoint.instant
        !point.instant.isBefore(event.startPoint.instant) && !point.instant.isAfter(end)
    }
    point.date != null && event.startPoint.date != null -> {
        val end = event.endPoint?.date ?: event.peakPoint.date ?: event.startPoint.date
        !point.date.isBefore(event.startPoint.date) && !point.date.isAfter(end)
    }
    else -> sameTimelinePoint(point, event.peakPoint)
}

private fun primaryDivergenceMetric(reasons: Set<DivergenceReason>): ForecastMetric? = when {
    DivergenceReason.PRECIPITATION in reasons -> ForecastMetric.PRECIPITATION
    DivergenceReason.WIND in reasons -> ForecastMetric.WIND
    DivergenceReason.TEMPERATURE in reasons -> ForecastMetric.TEMPERATURE
    DivergenceReason.CONDITION in reasons -> ForecastMetric.CONDITION
    else -> null
}

private fun eventCoveredReasons(event: ForecastEvent): Set<DivergenceReason> = buildSet {
    addAll(event.evidence.divergenceReasons)
    when (event.evidence.metric) {
        ForecastMetric.PRECIPITATION -> add(DivergenceReason.PRECIPITATION)
        ForecastMetric.WIND -> add(DivergenceReason.WIND)
        ForecastMetric.TEMPERATURE -> add(DivergenceReason.TEMPERATURE)
        ForecastMetric.CONDITION -> add(DivergenceReason.CONDITION)
        null -> Unit
    }
}

private fun eventNearPoint(
    event: ForecastEvent,
    point: SimplifiedTimelinePoint,
    mode: DisplayMode
): Boolean {
    return when (mode) {
        DisplayMode.HOURLY -> {
            val a = event.peakPoint.instant ?: return false
            val b = point.instant ?: return false
            abs(Duration.between(a, b).toHours()) <= 2
        }
        DisplayMode.DAILY -> {
            val a = event.peakPoint.date ?: return false
            val b = point.date ?: return false
            abs(a.toEpochDay() - b.toEpochDay()) <= 1
        }
    }
}

private fun buildCandidateInsights(overview: OverviewTimeline): List<ForecastInsight> {
    val points = overview.analysisPoints.sortedBy(::insightSortKey)
    if (points.isEmpty()) return emptyList()

    val candidates = buildList {
        buildRainInsight(points, overview.mode)?.let(::add)
        buildWeatherChangeInsight(points, overview.mode)?.let(::add)
        buildWindInsight(points, overview.mode)?.let(::add)
        buildDisagreementInsight(points, overview.mode)?.let(::add)
        buildTemperatureInsight(points, overview.mode, overview.timezone)?.let(::add)
        buildAgreementInsight(points, overview.mode)?.let(::add)
    }

    val distinct = removeNearbyDuplicates(candidates, overview.mode)
        .distinctBy { it.kind }

    return selectMostRelevantInsights(distinct)
        .sortedWith(
            compareBy<ForecastInsight> { pointIndex(it.point, points) }
                .thenByDescending { insightLevelWeight(it.level) }
                .thenByDescending { it.priority }
        )
}

private fun buildRainInsight(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode
): ForecastInsight? {
    val episodes = contiguousEpisodes(points, mode) { point ->
        val signal = point.precipitationPercent
        point.precipitationModelCount >= 2 && signal != null && signal >= RAIN_SIGNAL_MIN_PERCENT
    }

    val candidates = episodes.mapNotNull { episode ->
        val likelyPoints = episode.filter(::isLikelyRainPoint)
        val strongestLikely = likelyPoints.maxByOrNull { it.precipitationPercent ?: 0 }
        val qualifies = when {
            likelyPoints.isNotEmpty() -> likelyPoints.size >= 2 || episode.size >= 2 ||
                (strongestLikely?.precipitationPercent ?: 0) >= STRONG_SINGLE_RAIN_PERCENT ||
                strongestLikely?.condition in SEVERE_CONDITIONS
            else -> episode.count(::isUncertainRainPoint) >= 2 ||
                episode.any { it.isRainDivergent }
        }
        if (!qualifies) return@mapNotNull null

        val firstLikely = episode.firstOrNull(::isLikelyRainPoint)
        if (firstLikely != null) {
            val likelyIndex = episode.indexOf(firstLikely)
            val earlierUncertain = episode
                .take(likelyIndex)
                .firstOrNull(::isUncertainRainPoint)
            val strengtheningReference = earlierUncertain
                ?: firstLikely.takeIf { strongestLikely != null && strongestLikely != firstLikely }
            val notableConditionPoint = likelyPoints
                .filter { it.condition in SPECIALIZED_PRECIPITATION_CONDITIONS }
                .maxWithOrNull(
                    compareBy<SimplifiedTimelinePoint> { it.condition?.severityRank ?: 0 }
                        .thenBy { it.precipitationPercent ?: 0 }
                )
            // Pour un orage, de la neige ou de la pluie verglaçante, l'heure
            // affichée doit être celle du phénomène précis et non seulement le
            // début générique de la pluie.
            val primaryPoint = notableConditionPoint ?: firstLikely
            val persistentEnd = episode.lastOrNull { (it.precipitationPercent ?: 0) >= 50 }
                ?: primaryPoint
            val primaryIndex = episode.indexOf(primaryPoint)
            val targetCondition = notableConditionPoint?.condition ?: strongestLikely?.condition
            val severeCondition = targetCondition in SEVERE_CONDITIONS
            val strongestSignal = strongestLikely?.precipitationPercent ?: 0
            val displayedSignal = if (notableConditionPoint != null) {
                primaryPoint.precipitationPercent ?: strongestSignal
            } else {
                strongestSignal
            }
            val level = if (
                severeCondition || strongestSignal >= STRONG_SINGLE_RAIN_PERCENT
            ) {
                ForecastInsightLevel.ALERT
            } else {
                ForecastInsightLevel.WATCH
            }
            rainInsight(
                kind = ForecastInsightKind.RAIN_LIKELY,
                point = primaryPoint,
                endPoint = persistentEnd,
                eventPointCount = episode.subList(
                    primaryIndex,
                    episode.indexOf(persistentEnd) + 1
                ).size,
                level = level,
                priority = 90 + rainIntensity(strongestLikely ?: primaryPoint) -
                    urgencyPenalty(primaryPoint, points),
                referencePoint = strengtheningReference,
                referenceValue = strengtheningReference?.precipitationPercent,
                targetValue = displayedSignal,
                targetCondition = targetCondition
            )
        } else {
            val firstUncertain = episode.firstOrNull(::isUncertainRainPoint)
                ?: return@mapNotNull null
            rainInsight(
                kind = ForecastInsightKind.RAIN_UNCERTAIN,
                point = firstUncertain,
                endPoint = episode.last(),
                eventPointCount = episode.size,
                level = ForecastInsightLevel.WATCH,
                priority = 80 + episode.maxOf(::rainIntensity) -
                    urgencyPenalty(firstUncertain, points)
            )
        }
    }

    return candidates.maxWithOrNull(
        compareBy<ForecastInsight> { insightLevelWeight(it.level) }
            .thenBy { it.priority }
    )
}

private fun buildWeatherChangeInsight(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode
): ForecastInsight? {
    val conditionPoints = points.filter { point ->
        point.conditionModelCount >= 2 &&
            point.condition != null &&
            point.condition != WeatherCondition.UNKNOWN
    }
    if (conditionPoints.size < 2) return null

    val candidates = conditionPoints.indices.drop(1).mapNotNull { index ->
        // Comparer au dernier état réellement observé évite des formulations
        // trompeuses lorsque plusieurs transitions se succèdent dans l'horizon.
        val referencePoint = conditionPoints[index - 1]
        val referenceCondition = referencePoint.condition ?: return@mapNotNull null
        val targetPoint = conditionPoints[index]
        val targetCondition = targetPoint.condition ?: return@mapNotNull null
        if (!isMeaningfulConditionChange(referenceCondition, targetCondition)) {
            return@mapNotNull null
        }
        val targetFamily = targetCondition.conditionFamily()
        val persistentTail = persistentConditionTail(conditionPoints, index, targetFamily, mode)
        if (
            targetCondition !in SEVERE_CONDITIONS &&
            mode == DisplayMode.HOURLY &&
            persistentTail.size < REQUIRED_HOURLY_CONDITION_POINTS
        ) {
            return@mapNotNull null
        }

        val severityDelta = targetCondition.severityRank - referenceCondition.severityRank
        val improves = severityDelta < 0
        val level = when {
            targetCondition in SEVERE_CONDITIONS -> ForecastInsightLevel.ALERT
            improves -> ForecastInsightLevel.POSITIVE
            targetCondition in DISRUPTIVE_CONDITIONS -> ForecastInsightLevel.WATCH
            else -> ForecastInsightLevel.INFO
        }

        ForecastInsight(
            kind = ForecastInsightKind.WEATHER_CHANGE,
            level = level,
            priority = 68 + abs(severityDelta) * 3 - urgencyPenalty(targetPoint, points),
            point = targetPoint,
            endPoint = persistentTail.lastOrNull() ?: targetPoint,
            eventPointCount = persistentTail.size.coerceAtLeast(1),
            referencePoint = referencePoint,
            referenceCondition = referenceCondition,
            targetCondition = targetCondition,
            divergenceReasons = targetPoint.divergenceReasons
        )
    }

    return candidates.maxWithOrNull(
        compareBy<ForecastInsight> { insightLevelWeight(it.level) }
            .thenBy { it.priority }
    )
}

private fun buildWindInsight(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode
): ForecastInsight? {
    val windPoints = points.filter { it.windKmh != null && it.windModelCount >= 2 }
    if (windPoints.isEmpty()) return null

    val minimumNotableWind = if (mode == DisplayMode.HOURLY) 30.0 else 35.0
    val candidates = windPoints.mapIndexedNotNull { peakIndex, peakPoint ->
        val peak = peakPoint.windKmh ?: return@mapIndexedNotNull null
        val referenceWindow = contiguousWindReferenceWindow(windPoints, peakIndex, mode)
        val referencePoint = referenceWindow.minByOrNull { it.windKmh ?: Double.POSITIVE_INFINITY }
            ?: peakPoint
        val baseline = referencePoint.windKmh ?: peak
        val rise = peak - baseline
        val isClearRise = rise >= WIND_RISE_THRESHOLD_KMH && peak >= minimumNotableWind
        val isStrongWithoutRise = peak >= STRONG_WIND_THRESHOLD_KMH
        if (!isClearRise && !isStrongWithoutRise) return@mapIndexedNotNull null

        val sustained = persistentWindTail(
            points = windPoints,
            startIndex = peakIndex,
            minimumWind = minOf(peak * 0.8, STRONG_WIND_THRESHOLD_KMH),
            mode = mode
        )
        val level = when {
            peak >= 60.0 -> ForecastInsightLevel.ALERT
            peak >= 40.0 -> ForecastInsightLevel.WATCH
            else -> ForecastInsightLevel.INFO
        }

        ForecastInsight(
            kind = ForecastInsightKind.WIND_EVENT,
            level = level,
            priority = 66 + peak.roundToInt() + if (isClearRise) rise.roundToInt() / 2 else 0 -
                urgencyPenalty(peakPoint, points),
            point = peakPoint,
            endPoint = sustained.lastOrNull() ?: peakPoint,
            eventPointCount = sustained.size.coerceAtLeast(1),
            value = baseline.roundToInt(),
            secondaryValue = peak.roundToInt(),
            referencePoint = referencePoint,
            divergenceReasons = peakPoint.divergenceReasons
        )
    }

    return candidates.maxWithOrNull(
        compareBy<ForecastInsight> { insightLevelWeight(it.level) }
            .thenBy { it.priority }
    )
}

private fun contiguousWindReferenceWindow(
    points: List<SimplifiedTimelinePoint>,
    endIndex: Int,
    mode: DisplayMode
): List<SimplifiedTimelinePoint> {
    val peakPoint = points[endIndex]
    val result = mutableListOf(peakPoint)
    for (index in endIndex - 1 downTo 0) {
        val point = points[index]
        if (!timelinePointsAreConsecutive(point, result.first(), mode)) break
        val withinWindow = when (mode) {
            DisplayMode.HOURLY -> {
                val start = point.instant ?: break
                val end = peakPoint.instant ?: break
                Duration.between(start, end).toMinutes() / 60.0 <= MAX_WIND_RISE_WINDOW_HOURS
            }
            DisplayMode.DAILY -> {
                val start = point.date ?: break
                val end = peakPoint.date ?: break
                end.toEpochDay() - start.toEpochDay() <= MAX_WIND_RISE_WINDOW_DAYS
            }
        }
        if (!withinWindow) break
        result.add(0, point)
    }
    return result
}

private fun buildDisagreementInsight(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode
): ForecastInsight? {
    val episodes = contiguousEpisodes(points, mode) { point ->
        point.isDivergent && point.hasMultiModelEvidence
    }
    if (episodes.isEmpty()) return null

    val strongestByEpisode = episodes.mapNotNull { episode ->
        episode.minWithOrNull(
            compareBy<SimplifiedTimelinePoint> { it.consensusPercent ?: 100 }
                .thenByDescending { it.divergenceReasons.size }
        )
    }
    val point = strongestByEpisode.maxByOrNull { candidate ->
        val uncertainty = 100 - (candidate.consensusPercent ?: 100)
        candidate.divergenceReasons.size * 24 + uncertainty - urgencyPenalty(candidate, points)
    } ?: return null

    val episode = episodes.first { point in it }
    val level = if (
        point.divergenceReasons.size >= 2 &&
        (point.consensusPercent ?: 100) < 35
    ) {
        ForecastInsightLevel.ALERT
    } else {
        ForecastInsightLevel.WATCH
    }

    return ForecastInsight(
        kind = ForecastInsightKind.DISAGREEMENT,
        level = level,
        priority = 76 + point.divergenceReasons.size * 8 +
            (100 - (point.consensusPercent ?: 100)) / 5 - urgencyPenalty(point, points),
        point = point,
        endPoint = episode.last(),
        eventPointCount = episode.size,
        divergenceReasons = point.divergenceReasons
    )
}

private fun buildTemperatureInsight(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    timezone: String?
): ForecastInsight? {
    val temperatures = points.mapNotNull { point ->
        if (point.temperatureModelCount < 2) return@mapNotNull null
        (point.temperatureC ?: point.tempMaxC)?.let { value -> point to value }
    }
    if (temperatures.size < 2) return null
    val zone = resolveCityZone(timezone)

    data class Transition(
        val referencePoint: SimplifiedTimelinePoint,
        val referenceTemperature: Double,
        val targetPoint: SimplifiedTimelinePoint,
        val targetTemperature: Double,
        val delta: Double,
        val score: Double,
        val followsExpectedDiurnalCycle: Boolean
    )

    val transitions = buildList {
        temperatures.forEachIndexed { startIndex, (startPoint, startTemperature) ->
            temperatures.drop(startIndex + 1).forEach { (targetPoint, targetTemperature) ->
                val delta = targetTemperature - startTemperature
                val absoluteDelta = abs(delta)
                val (score, followsExpectedCycle) = when (mode) {
                    DisplayMode.HOURLY -> {
                        val startInstant = startPoint.instant ?: return@forEach
                        val targetInstant = targetPoint.instant ?: return@forEach
                        val hours = Duration.between(startInstant, targetInstant).toMinutes() / 60.0
                        if (hours <= 0.0 || hours > MAX_TEMPERATURE_WINDOW_HOURS) return@forEach
                        val rate = absoluteDelta / hours
                        val followsCycle = followsExpectedDiurnalTemperatureCycle(
                            start = startInstant,
                            target = targetInstant,
                            delta = delta,
                            zone = zone
                        )
                        val isRelevant = if (followsCycle) {
                            // Une hausse matinale ou une baisse en soirée est le cycle normal.
                            // Elle ne devient un insight que si elle est exceptionnellement brutale.
                            absoluteDelta >= EXCEPTIONAL_DIURNAL_CHANGE_C &&
                                hours <= EXCEPTIONAL_DIURNAL_MAX_HOURS &&
                                rate >= EXCEPTIONAL_DIURNAL_RATE_C_PER_HOUR
                        } else {
                            absoluteDelta >= HOURLY_TEMPERATURE_CHANGE_THRESHOLD_C &&
                                (rate >= MIN_TEMPERATURE_RATE_C_PER_HOUR ||
                                    absoluteDelta >= VERY_LARGE_TEMPERATURE_CHANGE_C)
                        }
                        if (!isRelevant) return@forEach
                        val cyclePenalty = if (followsCycle) DIURNAL_CYCLE_SCORE_PENALTY else 0.0
                        (rate * 100.0 + absoluteDelta * 5.0 - cyclePenalty) to followsCycle
                    }
                    DisplayMode.DAILY -> {
                        val startDate = startPoint.date ?: return@forEach
                        val targetDate = targetPoint.date ?: return@forEach
                        val days = targetDate.toEpochDay() - startDate.toEpochDay()
                        if (days <= 0 || days > MAX_TEMPERATURE_WINDOW_DAYS) return@forEach
                        if (absoluteDelta < DAILY_TEMPERATURE_CHANGE_THRESHOLD_C) return@forEach
                        (absoluteDelta * 20.0 / days) to false
                    }
                }
                add(
                    Transition(
                        referencePoint = startPoint,
                        referenceTemperature = startTemperature,
                        targetPoint = targetPoint,
                        targetTemperature = targetTemperature,
                        delta = delta,
                        score = score,
                        followsExpectedDiurnalCycle = followsExpectedCycle
                    )
                )
            }
        }
    }

    val strongest = transitions.maxWithOrNull(
        compareBy<Transition> { it.score }
            .thenBy { abs(it.delta) }
            .thenBy { pointIndex(it.targetPoint, points) }
    ) ?: return null
    val roundedDelta = strongest.delta.roundToInt()

    return ForecastInsight(
        kind = ForecastInsightKind.TEMPERATURE_CHANGE,
        level = if (
            abs(roundedDelta) >= 9 && !strongest.followsExpectedDiurnalCycle
        ) ForecastInsightLevel.WATCH else ForecastInsightLevel.INFO,
        priority = 56 + abs(roundedDelta) * 2 - urgencyPenalty(strongest.targetPoint, points),
        point = strongest.targetPoint,
        value = roundedDelta,
        referencePoint = strongest.referencePoint,
        referenceValue = strongest.referenceTemperature.roundToInt(),
        targetValue = strongest.targetTemperature.roundToInt(),
        divergenceReasons = strongest.targetPoint.divergenceReasons
    )
}


private fun followsExpectedDiurnalTemperatureCycle(
    start: java.time.Instant,
    target: java.time.Instant,
    delta: Double,
    zone: ZoneId
): Boolean {
    if (delta == 0.0) return false
    val localStart = start.atZone(zone)
    val localTarget = target.atZone(zone)
    val startHour = localStart.hour
    val targetHour = localTarget.hour
    val dayDistance = localTarget.toLocalDate().toEpochDay() - localStart.toLocalDate().toEpochDay()

    return if (delta > 0.0) {
        // Réchauffement habituel du lever du jour au milieu/fin d'après-midi.
        dayDistance == 0L && startHour in EXPECTED_WARMING_START_HOUR..EXPECTED_WARMING_END_HOUR &&
            targetHour <= EXPECTED_WARMING_TARGET_MAX_HOUR
    } else {
        // Refroidissement habituel de l'après-midi à la nuit, y compris après minuit.
        when (dayDistance) {
            0L -> startHour >= EXPECTED_COOLING_START_HOUR || targetHour <= EXPECTED_COOLING_END_HOUR
            1L -> startHour >= EXPECTED_COOLING_START_HOUR && targetHour <= EXPECTED_COOLING_END_HOUR
            else -> false
        }
    }
}

private fun buildAgreementInsight(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode
): ForecastInsight? {
    val hasAnyDisagreement = points.any { it.hasMultiModelEvidence && it.isDivergent }
    if (hasAnyDisagreement) return null

    val stableWindow = points.take(MAX_AGREEMENT_POINTS).takeWhile { point ->
        point.hasMultiModelEvidence &&
            point.consensusLevel in setOf(ModelConsensusLevel.HIGH, ModelConsensusLevel.MEDIUM) &&
            !point.isDivergent
    }
    val minimumPoints = if (mode == DisplayMode.HOURLY) 3 else 2
    if (stableWindow.size < minimumPoints) return null

    val averageAgreement = stableWindow.mapNotNull { it.consensusPercent }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()

    return ForecastInsight(
        kind = ForecastInsightKind.HIGH_AGREEMENT,
        level = ForecastInsightLevel.POSITIVE,
        priority = 30,
        point = stableWindow.last(),
        endPoint = stableWindow.last(),
        eventPointCount = stableWindow.size,
        value = averageAgreement
    )
}

private fun selectMostRelevantInsights(
    candidates: List<ForecastInsight>
): List<ForecastInsight> {
    val selected = mutableListOf<ForecastInsight>()

    fun takeFrom(levels: Set<ForecastInsightLevel>, excludeAgreement: Boolean = false) {
        candidates
            .filter {
                it.level in levels &&
                    it !in selected &&
                    (!excludeAgreement || it.kind != ForecastInsightKind.HIGH_AGREEMENT)
            }
            .sortedWith(
                compareByDescending<ForecastInsight> { insightLevelWeight(it.level) }
                    .thenByDescending { it.priority }
            )
            .forEach { candidate ->
                if (selected.size < MAX_INSIGHTS) selected += candidate
            }
    }

    // Les alertes et signaux à surveiller sont toujours sélectionnés avant les
    // simples évolutions, même s'ils surviennent plus tard dans l'horizon.
    takeFrom(setOf(ForecastInsightLevel.ALERT, ForecastInsightLevel.WATCH))
    takeFrom(setOf(ForecastInsightLevel.INFO))
    // Une amélioration concrète peut compléter la synthèse. Le message générique
    // de convergence sert uniquement de repli lorsqu'aucun autre point clé ne ressort.
    takeFrom(setOf(ForecastInsightLevel.POSITIVE), excludeAgreement = true)
    if (selected.isEmpty()) {
        candidates
            .filter { it.kind == ForecastInsightKind.HIGH_AGREEMENT }
            .maxByOrNull { it.priority }
            ?.let(selected::add)
    }
    return selected
}

private fun persistentConditionTail(
    points: List<SimplifiedTimelinePoint>,
    startIndex: Int,
    family: WeatherConditionFamily,
    mode: DisplayMode
): List<SimplifiedTimelinePoint> {
    val result = mutableListOf<SimplifiedTimelinePoint>()
    for (index in startIndex until points.size) {
        val point = points[index]
        if (point.condition?.conditionFamily() != family) break
        if (result.isNotEmpty() && !timelinePointsAreConsecutive(result.last(), point, mode)) break
        result += point
    }
    return result
}

private fun persistentWindTail(
    points: List<SimplifiedTimelinePoint>,
    startIndex: Int,
    minimumWind: Double,
    mode: DisplayMode
): List<SimplifiedTimelinePoint> {
    val result = mutableListOf<SimplifiedTimelinePoint>()
    for (index in startIndex until points.size) {
        val point = points[index]
        if ((point.windKmh ?: 0.0) < minimumWind) break
        if (result.isNotEmpty() && !timelinePointsAreConsecutive(result.last(), point, mode)) break
        result += point
    }
    return result
}

private fun isMeaningfulConditionChange(
    reference: WeatherCondition,
    target: WeatherCondition
): Boolean {
    if (reference == target) return false
    val referenceFamily = reference.conditionFamily()
    val targetFamily = target.conditionFamily()
    if (referenceFamily == targetFamily) return false

    val precipitationTransition =
        referenceFamily in PRECIPITATING_FAMILIES || targetFamily in PRECIPITATING_FAMILIES
    val visibilityTransition =
        referenceFamily == WeatherConditionFamily.FOG || targetFamily == WeatherConditionFamily.FOG
    val severeTransition = reference in SEVERE_CONDITIONS || target in SEVERE_CONDITIONS
    return precipitationTransition || visibilityTransition || severeTransition ||
        abs(target.severityRank - reference.severityRank) >= 3
}

private enum class WeatherConditionFamily {
    FAIR, CLOUDY, FOG, WET, WINTRY, STORM
}

private fun WeatherCondition.conditionFamily(): WeatherConditionFamily = when (this) {
    WeatherCondition.CLEAR,
    WeatherCondition.MAINLY_CLEAR,
    WeatherCondition.PARTLY_CLOUDY -> WeatherConditionFamily.FAIR
    WeatherCondition.OVERCAST -> WeatherConditionFamily.CLOUDY
    WeatherCondition.FOG -> WeatherConditionFamily.FOG
    WeatherCondition.DRIZZLE,
    WeatherCondition.RAIN,
    WeatherCondition.RAIN_SHOWERS -> WeatherConditionFamily.WET
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.SNOW,
    WeatherCondition.SNOW_SHOWERS -> WeatherConditionFamily.WINTRY
    WeatherCondition.THUNDERSTORM -> WeatherConditionFamily.STORM
    WeatherCondition.UNKNOWN -> WeatherConditionFamily.CLOUDY
}

private val PRECIPITATING_FAMILIES = setOf(
    WeatherConditionFamily.WET,
    WeatherConditionFamily.WINTRY,
    WeatherConditionFamily.STORM
)

private val SEVERE_CONDITIONS = setOf(
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.THUNDERSTORM
)

private val SPECIALIZED_PRECIPITATION_CONDITIONS = setOf(
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.SNOW,
    WeatherCondition.SNOW_SHOWERS,
    WeatherCondition.THUNDERSTORM
)

private val DISRUPTIVE_CONDITIONS = setOf(
    WeatherCondition.FOG,
    WeatherCondition.DRIZZLE,
    WeatherCondition.RAIN,
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.SNOW,
    WeatherCondition.RAIN_SHOWERS,
    WeatherCondition.SNOW_SHOWERS,
    WeatherCondition.THUNDERSTORM
)

private fun isUncertainRainPoint(point: SimplifiedTimelinePoint): Boolean {
    val signal = point.precipitationPercent ?: return false
    return point.precipitationModelCount >= 2 &&
        signal in RAIN_SIGNAL_MIN_PERCENT until LIKELY_RAIN_PERCENT &&
        (point.precipitationSource in setOf(PrecipitationSignalSource.MODEL_PROBABILITY, PrecipitationSignalSource.MIXED) ||
            point.isRainDivergent)
}

private fun isLikelyRainPoint(point: SimplifiedTimelinePoint): Boolean {
    val signal = point.precipitationPercent ?: return false
    return point.precipitationModelCount >= 2 && signal >= LIKELY_RAIN_PERCENT
}

private fun rainIntensity(point: SimplifiedTimelinePoint): Int = when (point.precipitationSource) {
    PrecipitationSignalSource.MODEL_PROBABILITY, PrecipitationSignalSource.MIXED -> (point.precipitationPercent ?: 0) / 5
    PrecipitationSignalSource.MODEL_AGREEMENT -> {
        val total = point.precipitationModelCount.coerceAtLeast(1)
        point.wetModelCount * 20 / total
    }
    null -> 0
}

private fun rainInsight(
    kind: ForecastInsightKind,
    point: SimplifiedTimelinePoint,
    endPoint: SimplifiedTimelinePoint,
    eventPointCount: Int,
    level: ForecastInsightLevel,
    priority: Int,
    referencePoint: SimplifiedTimelinePoint? = null,
    referenceValue: Int? = null,
    targetValue: Int? = null,
    targetCondition: WeatherCondition? = null
): ForecastInsight = when (point.precipitationSource) {
    PrecipitationSignalSource.MODEL_PROBABILITY, PrecipitationSignalSource.MIXED -> ForecastInsight(
        kind = kind,
        level = level,
        priority = priority,
        point = point,
        endPoint = endPoint,
        eventPointCount = eventPointCount,
        value = point.precipitationPercent,
        secondaryValue = point.precipitationModelCount,
        precipitationSource = point.precipitationSource,
        referencePoint = referencePoint,
        referenceValue = referenceValue,
        targetValue = targetValue,
        targetCondition = targetCondition,
        divergenceReasons = point.divergenceReasons
    )
    PrecipitationSignalSource.MODEL_AGREEMENT -> ForecastInsight(
        kind = kind,
        level = level,
        priority = priority,
        point = point,
        endPoint = endPoint,
        eventPointCount = eventPointCount,
        value = point.wetModelCount,
        secondaryValue = point.precipitationModelCount,
        precipitationSource = point.precipitationSource,
        referencePoint = referencePoint,
        referenceValue = referenceValue,
        targetValue = targetValue,
        targetCondition = targetCondition,
        divergenceReasons = point.divergenceReasons
    )
    null -> ForecastInsight(
        kind = kind,
        level = level,
        priority = priority,
        point = point,
        endPoint = endPoint,
        eventPointCount = eventPointCount,
        referencePoint = referencePoint,
        referenceValue = referenceValue,
        targetValue = targetValue,
        targetCondition = targetCondition,
        divergenceReasons = point.divergenceReasons
    )
}

/**
 * Évite deux phrases sur le même phénomène dans une fenêtre courte. Un message
 * météo précis absorbe le désaccord générique uniquement s'il exprime lui-même
 * la divergence correspondante.
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
            val reason = specificReason(specific.kind)
            pointsAreNear(candidate.point, specific.point, mode) &&
                reason != null &&
                reason in candidate.divergenceReasons &&
                reason in specific.divergenceReasons
        }
    }

    val withoutRedundantWeatherChange = withoutRedundantDisagreement.filterNot { candidate ->
        if (candidate.kind != ForecastInsightKind.WEATHER_CHANGE) return@filterNot false
        val targetIsWet = candidate.targetCondition
            ?.conditionFamily()
            ?.let { it in PRECIPITATING_FAMILIES } == true
        targetIsWet && specifics.any { specific ->
            specific.kind in setOf(
                ForecastInsightKind.RAIN_LIKELY,
                ForecastInsightKind.RAIN_UNCERTAIN
            ) && pointsAreNear(candidate.point, specific.point, mode)
        }
    }

    val result = mutableListOf<ForecastInsight>()
    withoutRedundantWeatherChange
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
    ForecastInsightKind.WEATHER_CHANGE -> "weather"
    ForecastInsightKind.WIND_EVENT -> "wind"
    ForecastInsightKind.TEMPERATURE_CHANGE -> "temperature"
    ForecastInsightKind.HIGH_AGREEMENT -> "agreement"
}

private fun specificReason(kind: ForecastInsightKind): DivergenceReason? = when (kind) {
    ForecastInsightKind.RAIN_LIKELY,
    ForecastInsightKind.RAIN_UNCERTAIN -> DivergenceReason.PRECIPITATION
    ForecastInsightKind.WEATHER_CHANGE -> DivergenceReason.CONDITION
    ForecastInsightKind.WIND_EVENT -> DivergenceReason.WIND
    ForecastInsightKind.TEMPERATURE_CHANGE -> DivergenceReason.TEMPERATURE
    ForecastInsightKind.HIGH_AGREEMENT,
    ForecastInsightKind.DISAGREEMENT -> null
}

private fun contiguousEpisodes(
    points: List<SimplifiedTimelinePoint>,
    mode: DisplayMode,
    predicate: (SimplifiedTimelinePoint) -> Boolean
): List<List<SimplifiedTimelinePoint>> {
    val result = mutableListOf<List<SimplifiedTimelinePoint>>()
    var current = mutableListOf<SimplifiedTimelinePoint>()

    points.forEach { point ->
        val continues = predicate(point) &&
            (current.isEmpty() || timelinePointsAreConsecutive(current.last(), point, mode))
        when {
            continues -> current += point
            predicate(point) -> {
                if (current.isNotEmpty()) result += current
                current = mutableListOf(point)
            }
            else -> {
                if (current.isNotEmpty()) result += current
                current = mutableListOf()
            }
        }
    }
    if (current.isNotEmpty()) result += current
    return result
}

private fun timelinePointsAreConsecutive(
    first: SimplifiedTimelinePoint,
    second: SimplifiedTimelinePoint,
    mode: DisplayMode
): Boolean {
    return when (mode) {
        DisplayMode.HOURLY -> {
            val firstInstant = first.instant ?: return false
            val secondInstant = second.instant ?: return false
            val hours = Duration.between(firstInstant, secondInstant).toMinutes() / 60.0
            hours > 0.0 && hours <= MAX_CONTIGUOUS_HOURLY_GAP
        }
        DisplayMode.DAILY -> {
            val firstDate = first.date ?: return false
            val secondDate = second.date ?: return false
            secondDate.toEpochDay() - firstDate.toEpochDay() == 1L
        }
    }
}

private fun insightSortKey(point: SimplifiedTimelinePoint): Long = when {
    point.instant != null -> point.instant.toEpochMilli()
    point.date != null -> point.date.toEpochDay() * MILLIS_PER_DAY
    else -> Long.MAX_VALUE
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

private const val MILLIS_PER_DAY = 86_400_000L
private const val MAX_INSIGHTS = 3
private const val MAX_AGREEMENT_POINTS = 5
private const val REQUIRED_HOURLY_CONDITION_POINTS = 2
private const val MAX_CONTIGUOUS_HOURLY_GAP = 2.0
private const val RAIN_SIGNAL_MIN_PERCENT = 30
private const val LIKELY_RAIN_PERCENT = 70
private const val STRONG_SINGLE_RAIN_PERCENT = 80
private const val WIND_RISE_THRESHOLD_KMH = 15.0
private const val STRONG_WIND_THRESHOLD_KMH = 45.0
private const val MAX_WIND_RISE_WINDOW_HOURS = 8.0
private const val MAX_WIND_RISE_WINDOW_DAYS = 2L
private const val HOURLY_TEMPERATURE_CHANGE_THRESHOLD_C = 6.0
private const val DAILY_TEMPERATURE_CHANGE_THRESHOLD_C = 5.0
private const val VERY_LARGE_TEMPERATURE_CHANGE_C = 9.0
private const val MIN_TEMPERATURE_RATE_C_PER_HOUR = 1.5
private const val MAX_TEMPERATURE_WINDOW_HOURS = 8.0
private const val MAX_TEMPERATURE_WINDOW_DAYS = 2L
private const val EXCEPTIONAL_DIURNAL_CHANGE_C = 10.0
private const val EXCEPTIONAL_DIURNAL_MAX_HOURS = 3.0
private const val EXCEPTIONAL_DIURNAL_RATE_C_PER_HOUR = 3.0
private const val DIURNAL_CYCLE_SCORE_PENALTY = 180.0
private const val EXPECTED_WARMING_START_HOUR = 4
private const val EXPECTED_WARMING_END_HOUR = 14
private const val EXPECTED_WARMING_TARGET_MAX_HOUR = 17
private const val EXPECTED_COOLING_START_HOUR = 14
private const val EXPECTED_COOLING_END_HOUR = 8
