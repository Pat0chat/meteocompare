package com.meteocompare.app.ui.citydetail

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

/** Observation courte dérivée localement du consensus multi-modèles. */
internal data class ForecastInsight(
    val kind: ForecastInsightKind,
    val point: SimplifiedTimelinePoint? = null,
    val value: Int? = null,
    val secondaryValue: Int? = null,
    val precipitationSource: PrecipitationSignalSource? = null
)

/**
 * Produit au maximum trois messages réellement distincts à partir d'une
 * chronologie déjà calculée. Réutiliser [overview] évite de parcourir les
 * séries une seconde fois pendant la composition.
 */
internal fun buildForecastInsights(overview: OverviewTimeline): List<ForecastInsight> {
    val points = overview.points
    if (points.isEmpty()) return emptyList()

    val result = mutableListOf<ForecastInsight>()

    val uncertainRain = points.firstOrNull { point ->
        val signal = point.precipitationPercent ?: return@firstOrNull false
        point.precipitationModelCount >= 2 &&
            signal in 30..69 &&
            (point.precipitationSource == PrecipitationSignalSource.MODEL_PROBABILITY ||
                point.isRainDivergent)
    }
    val likelyRain = points.firstOrNull { point ->
        val signal = point.precipitationPercent ?: return@firstOrNull false
        point.precipitationModelCount >= 2 && signal >= 70
    }

    when {
        uncertainRain != null -> result += rainInsight(
            kind = ForecastInsightKind.RAIN_UNCERTAIN,
            point = uncertainRain
        )
        likelyRain != null -> result += rainInsight(
            kind = ForecastInsightKind.RAIN_LIKELY,
            point = likelyRain
        )
    }

    val windPoints = points.filter { it.windKmh != null && it.windModelCount >= 2 }
    val firstWindPoint = windPoints.firstOrNull()
    val firstWind = firstWindPoint?.windKmh
    val windRisePoint = if (firstWind == null) null else windPoints.firstOrNull { point ->
        val wind = point.windKmh ?: return@firstOrNull false
        wind - firstWind >= 15.0
    }
    if (windRisePoint != null) {
        result += ForecastInsight(
            kind = ForecastInsightKind.WIND_RISING,
            point = windRisePoint,
            value = firstWind?.roundToInt(),
            secondaryValue = windRisePoint.windKmh?.roundToInt()
        )
    }

    val divergentPoint = points.firstOrNull { it.isDivergent && it.hasMultiModelEvidence }
    if (divergentPoint != null && result.none { it.point == divergentPoint }) {
        result += ForecastInsight(
            kind = ForecastInsightKind.DISAGREEMENT,
            point = divergentPoint
        )
    }

    if (result.size < MAX_INSIGHTS) {
        val temperatures = points.mapNotNull { point ->
            if (point.temperatureModelCount < 2) return@mapNotNull null
            (point.temperatureC ?: point.tempMaxC)?.let { value -> point to value }
        }
        if (temperatures.size >= 2) {
            val first = temperatures.first().second
            val strongest = temperatures.maxByOrNull { (_, value) -> abs(value - first) }
            val delta = strongest?.second?.minus(first)?.roundToInt() ?: 0
            if (abs(delta) >= 6) {
                result += ForecastInsight(
                    kind = ForecastInsightKind.TEMPERATURE_CHANGE,
                    point = strongest?.first,
                    value = delta
                )
            }
        }
    }

    val agreementWindow = points.take(MAX_AGREEMENT_POINTS)
    val hasDefensibleAgreement = agreementWindow.size >= 2 &&
        agreementWindow.all { it.hasMultiModelEvidence && !it.isDivergent }
    if ((result.isEmpty() || result.size < MAX_INSIGHTS) && hasDefensibleAgreement) {
        result += ForecastInsight(
            kind = ForecastInsightKind.HIGH_AGREEMENT,
            point = agreementWindow.last()
        )
    }

    return result.distinctBy { it.kind }.take(MAX_INSIGHTS)
}

private fun rainInsight(
    kind: ForecastInsightKind,
    point: SimplifiedTimelinePoint
): ForecastInsight = when (point.precipitationSource) {
    PrecipitationSignalSource.MODEL_PROBABILITY -> ForecastInsight(
        kind = kind,
        point = point,
        value = point.precipitationPercent,
        precipitationSource = point.precipitationSource
    )
    PrecipitationSignalSource.MODEL_AGREEMENT -> ForecastInsight(
        kind = kind,
        point = point,
        value = point.wetModelCount,
        secondaryValue = point.precipitationModelCount,
        precipitationSource = point.precipitationSource
    )
    null -> ForecastInsight(kind = kind, point = point)
}

private const val MAX_INSIGHTS = 3
private const val MAX_AGREEMENT_POINTS = 4
