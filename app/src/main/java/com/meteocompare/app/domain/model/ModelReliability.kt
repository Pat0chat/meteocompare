package com.meteocompare.app.domain.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Niveau de lecture simplifié de l'indice local de fiabilité.
 *
 * Cet indice est volontairement pédagogique : il synthétise plusieurs mesures
 * (erreur absolue, biais signé, dispersion et couverture de l'historique), mais
 * ne remplace ni un score probabiliste officiel ni une expertise météo.
 */
enum class ReliabilityLevel { EXCELLENT, GOOD, FAIR, LIMITED }

/** Évolution de l'erreur absolue sur la période récente. */
enum class ReliabilityTrend { IMPROVING, STABLE, DECLINING, INSUFFICIENT_DATA }

/** Diagnostic spécifique aux précipitations, calculé sur un seuil de jour humide. */
@Immutable
data class PrecipitationReliability(
    val hitRate: Double?,
    val falseAlarmRate: Double?,
    val missedEventRate: Double?,
    val observedWetDays: Int,
    val forecastWetDays: Int
)

/**
 * Tableau de fiabilité local d'un modèle pour une variable et une ville.
 *
 * [meanAbsoluteError] répond à « de combien le modèle se trompe en moyenne ? »,
 * alors que [meanBias] répond à « dans quel sens se trompe-t-il ? ». Cette
 * distinction évite qu'une alternance de fortes sur- et sous-estimations ne
 * donne artificiellement un biais moyen proche de zéro.
 */
@Immutable
data class ModelReliability(
    val variable: BiasVariable,
    val score: Int,
    val level: ReliabilityLevel,
    val meanBias: Double,
    val meanAbsoluteError: Double,
    val rootMeanSquareError: Double,
    val standardDeviation: Double,
    val withinToleranceRate: Double,
    val overestimateRate: Double,
    val underestimateRate: Double,
    val closeTolerance: Double,
    val sampleSize: Int,
    val windowDays: Int,
    val recentMeanAbsoluteError: Double?,
    val previousMeanAbsoluteError: Double?,
    val trend: ReliabilityTrend,
    val precipitation: PrecipitationReliability?
)

/** Rang d'un modèle parmi ceux disposant de suffisamment d'historique local. */
@Immutable
data class ReliabilityRank(
    val rank: Int,
    val modelCount: Int
)

/**
 * Calcul pur du tableau de fiabilité. Les seuils sont des repères pratiques,
 * pas des seuils de vigilance ni des normes scientifiques officielles.
 */
object ModelReliabilityCalculator {

    private const val WET_DAY_THRESHOLD_MM = 0.5
    private const val RECENT_WINDOW_DAYS = 7

    private data class VariableScale(
        val closeTolerance: Double,
        val maeScale: Double,
        val biasScale: Double,
        val spreadScale: Double
    )

    private fun scaleFor(variable: BiasVariable): VariableScale = when (variable) {
        BiasVariable.TEMPERATURE -> VariableScale(
            closeTolerance = 1.5,
            maeScale = 2.4,
            biasScale = 1.2,
            spreadScale = 3.0
        )
        BiasVariable.PRECIPITATION -> VariableScale(
            closeTolerance = 1.0,
            maeScale = 3.0,
            biasScale = 1.5,
            spreadScale = 4.0
        )
        BiasVariable.WIND_SPEED -> VariableScale(
            closeTolerance = 5.0,
            maeScale = 8.0,
            biasScale = 5.0,
            spreadScale = 10.0
        )
    }

    /**
     * Calcule la fiabilité d'une série appariée prévision / observation.
     * Retourne null sous le même seuil minimum que le suivi de biais.
     */
    fun compute(
        variable: BiasVariable,
        samples: List<BiasSample>,
        windowDays: Int = 30
    ): ModelReliability? {
        require(windowDays > 0) { "windowDays must be positive, got $windowDays" }

        val ordered = deduplicateAndSort(samples)
        if (ordered.size < ModelBias.MIN_SAMPLES_FOR_BIAS) return null

        val errors = ordered.map(BiasSample::dailyBias)
        val absErrors = errors.map(::abs)
        val meanBias = errors.average()
        val mae = absErrors.average()
        val rmse = sqrt(errors.sumOf { it * it } / errors.size)
        val stdDev = sampleStdDev(errors, meanBias)
        val scale = scaleFor(variable)

        val withinToleranceRate = absErrors.count { it <= scale.closeTolerance }
            .toDouble() / errors.size
        val overestimateRate = errors.count { it > 0.0 }.toDouble() / errors.size
        val underestimateRate = errors.count { it < 0.0 }.toDouble() / errors.size

        val accuracyScore = exponentialScore(mae, scale.maeScale)
        val calibrationScore = exponentialScore(abs(meanBias), scale.biasScale)
        val consistencyScore = exponentialScore(stdDev, scale.spreadScale)
        val closenessScore = withinToleranceRate
        val coverageScore = (ordered.size.toDouble() / windowDays).coerceIn(0.0, 1.0)

        // Pondération orientée usage : l'erreur réellement ressentie prime sur
        // le biais signé. La couverture compte peu, mais évite de sur-noter une
        // série qui vient tout juste d'atteindre le minimum de 14 jours.
        val score = (
            accuracyScore * 0.42 +
                calibrationScore * 0.20 +
                consistencyScore * 0.16 +
                closenessScore * 0.17 +
                coverageScore * 0.05
            ).times(100.0).roundToInt().coerceIn(0, 100)

        val recent = recentTrend(absErrors, scale.closeTolerance)
        val precipitation = if (variable == BiasVariable.PRECIPITATION) {
            precipitationDiagnostics(ordered)
        } else {
            null
        }

        return ModelReliability(
            variable = variable,
            score = score,
            level = levelFor(score),
            meanBias = meanBias,
            meanAbsoluteError = mae,
            rootMeanSquareError = rmse,
            standardDeviation = stdDev,
            withinToleranceRate = withinToleranceRate,
            overestimateRate = overestimateRate,
            underestimateRate = underestimateRate,
            closeTolerance = scale.closeTolerance,
            sampleSize = ordered.size,
            windowDays = windowDays,
            recentMeanAbsoluteError = recent.recent,
            previousMeanAbsoluteError = recent.previous,
            trend = recent.trend,
            precipitation = precipitation
        )
    }

    /**
     * Construit une référence multi-modèles locale : pour chaque date commune à
     * au moins deux modèles, on moyenne les prévisions et les observations.
     */
    fun computeMultiModelBaseline(
        variable: BiasVariable,
        historyByModel: Map<WeatherModel, List<BiasSample>>,
        windowDays: Int = 30
    ): ModelReliability? {
        val samplesByDate = linkedMapOf<LocalDate, MutableList<BiasSample>>()
        historyByModel.values.forEach { history ->
            deduplicateAndSort(history).forEach { sample ->
                samplesByDate.getOrPut(sample.targetDate) { mutableListOf() }.add(sample)
            }
        }

        val ensembleSamples = samplesByDate.mapNotNull { (date, dateSamples) ->
            if (dateSamples.size < 2) return@mapNotNull null
            BiasSample(
                targetDate = date,
                forecast = dateSamples.map(BiasSample::forecast).average(),
                observation = dateSamples.map(BiasSample::observation).average()
            )
        }

        return compute(variable, ensembleSamples, windowDays)
    }

    /** Calcule un rang décroissant par score, puis croissant par MAE en cas d'égalité. */
    fun rank(
        selectedModel: WeatherModel,
        reliabilityByModel: Map<WeatherModel, ModelReliability>
    ): ReliabilityRank? {
        if (selectedModel !in reliabilityByModel) return null
        val sorted = reliabilityByModel.entries.sortedWith(
            compareByDescending<Map.Entry<WeatherModel, ModelReliability>> { it.value.score }
                .thenBy { it.value.meanAbsoluteError }
                .thenBy { it.key.displayName }
        )
        val index = sorted.indexOfFirst { it.key == selectedModel }
        if (index < 0) return null
        return ReliabilityRank(rank = index + 1, modelCount = sorted.size)
    }

    private fun levelFor(score: Int): ReliabilityLevel = when {
        score >= 85 -> ReliabilityLevel.EXCELLENT
        score >= 70 -> ReliabilityLevel.GOOD
        score >= 50 -> ReliabilityLevel.FAIR
        else -> ReliabilityLevel.LIMITED
    }

    private fun exponentialScore(value: Double, scale: Double): Double =
        exp(-value.coerceAtLeast(0.0) / scale).coerceIn(0.0, 1.0)

    private data class TrendResult(
        val recent: Double?,
        val previous: Double?,
        val trend: ReliabilityTrend
    )

    private fun recentTrend(absErrors: List<Double>, closeTolerance: Double): TrendResult {
        if (absErrors.size < 10) {
            return TrendResult(null, null, ReliabilityTrend.INSUFFICIENT_DATA)
        }

        val recentCount = min(RECENT_WINDOW_DAYS, absErrors.size / 2)
        val previousCount = min(recentCount, absErrors.size - recentCount)
        if (previousCount < 3) {
            return TrendResult(null, null, ReliabilityTrend.INSUFFICIENT_DATA)
        }

        val recent = absErrors.takeLast(recentCount).average()
        val previous = absErrors
            .dropLast(recentCount)
            .takeLast(previousCount)
            .average()
        val meaningfulDelta = max(closeTolerance * 0.15, previous * 0.12)
        val trend = when {
            recent < previous - meaningfulDelta -> ReliabilityTrend.IMPROVING
            recent > previous + meaningfulDelta -> ReliabilityTrend.DECLINING
            else -> ReliabilityTrend.STABLE
        }
        return TrendResult(recent, previous, trend)
    }

    private fun precipitationDiagnostics(samples: List<BiasSample>): PrecipitationReliability {
        var hits = 0
        var misses = 0
        var falseAlarms = 0
        var observedWetDays = 0
        var forecastWetDays = 0

        samples.forEach { sample ->
            val forecastWet = sample.forecast >= WET_DAY_THRESHOLD_MM
            val observedWet = sample.observation >= WET_DAY_THRESHOLD_MM
            if (forecastWet) forecastWetDays++
            if (observedWet) observedWetDays++
            when {
                forecastWet && observedWet -> hits++
                forecastWet && !observedWet -> falseAlarms++
                !forecastWet && observedWet -> misses++
            }
        }

        val hitRate = if (observedWetDays > 0) hits.toDouble() / observedWetDays else null
        val missedRate = if (observedWetDays > 0) misses.toDouble() / observedWetDays else null
        val falseAlarmRate = if (forecastWetDays > 0) {
            falseAlarms.toDouble() / forecastWetDays
        } else {
            null
        }

        return PrecipitationReliability(
            hitRate = hitRate,
            falseAlarmRate = falseAlarmRate,
            missedEventRate = missedRate,
            observedWetDays = observedWetDays,
            forecastWetDays = forecastWetDays
        )
    }

    private fun deduplicateAndSort(samples: List<BiasSample>): List<BiasSample> {
        val seen = mutableSetOf<LocalDate>()
        return samples
            .filter { seen.add(it.targetDate) }
            .sortedBy(BiasSample::targetDate)
    }

    private fun sampleStdDev(values: List<Double>, mean: Double): Double {
        if (values.size <= 1) return 0.0
        val sumSquares = values.sumOf { value -> (value - mean) * (value - mean) }
        return sqrt(sumSquares / (values.size - 1))
    }
}
