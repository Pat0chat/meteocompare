package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.ForecastCalibrationProfile
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.WeatherModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Port Kotlin du moteur de prévision V3 de MeteoCompare Web 1.16.0.
 *
 * Les quatre stratégies travaillent sur les mêmes sorties brutes. Elles ne
 * modifient jamais les séries sources et n'ont aucune autorité sur le score de
 * convergence affiché par l'application : ce dernier reste calculé séparément
 * par [ForecastConsensus], conformément à l'audit 1.16.
 */
object ForecastEngineV3 {
    const val MIN_CALIBRATION_SAMPLES = 14
    const val FULL_CALIBRATION_SAMPLES = 30
    const val MIN_CALIBRATION_COVERAGE = 0.34
    const val MIN_CALIBRATED_FAMILIES = 2
    const val MIN_DOMINANT_SCENARIO_SHARE = 0.55
    private const val EPS = 1e-9

    enum class Explanation {
        ROBUST_FAMILY_BALANCED,
        BIAS_CORRECTED_SKILL_WEIGHTED,
        SINGLE_SCENARIO,
        BALANCED_SCENARIOS,
        DOMINANT_SCENARIO,
        ADAPTIVE_SCENARIO,
        ADAPTIVE_CALIBRATION_BLEND,
        ADAPTIVE_ROBUST_FALLBACK
    }

    enum class FallbackReason { INSUFFICIENT_CALIBRATION, SINGLE_SCENARIO, NO_DOMINANT_SCENARIO }

    data class Interval(val low: Double?, val high: Double?)
    data class Scenario(val share: Double, val central: Double?, val low: Double?, val high: Double?)

    data class ContinuousResult(
        val central: Double?,
        val stats: ForecastConsensus.Stats?,
        /** Lignes pondérées internes, conservées pour reproduire exactement les enveloppes V3 Web. */
        val rows: List<ForecastConsensus.WeightedEntry> = emptyList(),
        /** Intervalle descriptif de dispersion, et non intervalle probabiliste calibré. */
        val interval: Interval,
        val engine: ForecastEngine,
        val effectiveEngine: ForecastEngine,
        val fallback: Boolean = false,
        val fallbackReason: FallbackReason? = null,
        val calibrationCoverage: Double = 0.0,
        val calibratedFamilyCount: Int = 0,
        val calibrationStrength: Double = 0.0,
        val historicalScore: Double? = null,
        val scenarioCount: Int = 1,
        val dominantShare: Double? = 1.0,
        val scenarioGap: Double? = null,
        val scenarios: List<Scenario> = emptyList(),
        val explanation: Explanation? = null,
        val adaptiveTrust: Double? = null,
        val adaptiveComponents: Map<ForecastEngine, Double?> = emptyMap(),
        val modelCount: Int = 0,
        val familyCount: Int = 0,
        /** Métadonnée interne V3 ; l'UI doit conserver la convergence brute indépendante. */
        val engineConvergencePercent: Int? = null
    )

    data class PrecipitationResult(
        val engine: ForecastEngine,
        val effectiveEngine: ForecastEngine,
        val probabilityPercent: Int?,
        val conditionalAmountMm: Double?,
        val centralAmountMm: Double?,
        val expectedAmountMm: Double?,
        val modelCount: Int,
        val familyCount: Int,
        val wetModelCount: Int,
        val source: ForecastConsensus.PrecipitationSource?,
        val minMm: Double?,
        val maxMm: Double?,
        val conditionalStdDev: Double?,
        val interval: Interval,
        val scenarioCount: Int,
        val scenarios: List<Scenario>,
        val calibrationCoverage: Double,
        val calibratedFamilyCount: Int,
        val calibrationStrength: Double,
        val occurrenceCalibrationCoverage: Double,
        val fallback: Boolean,
        val fallbackReason: FallbackReason?,
        val explanation: Explanation?
    )

    data class ContinuousOptions(
        val engine: ForecastEngine = ForecastEngine.DEFAULT,
        val localWeights: Map<WeatherModel, Double> = emptyMap(),
        val calibration: Map<WeatherModel, ForecastCalibrationProfile> = emptyMap(),
        val tight: Double = 0.5,
        val wide: Double = 3.0,
        val min: Double? = null,
        val max: Double? = null
    )

    data class PrecipitationOptions(
        val engine: ForecastEngine = ForecastEngine.DEFAULT,
        val threshold: Double = 0.1,
        val localWeights: Map<WeatherModel, Double> = emptyMap(),
        val calibration: Map<WeatherModel, ForecastCalibrationProfile> = emptyMap(),
        val amountTight: Double = 1.0,
        val amountWide: Double = 8.0
    )

    private data class Balanced(val rows: List<ForecastConsensus.WeightedEntry>, val modelCount: Int, val familyCount: Int)
    private data class Robust(val central: Double, val stats: ForecastConsensus.Stats, val interval: Interval, val rows: List<ForecastConsensus.WeightedEntry>, val convergence: Int?, val modelCount: Int, val familyCount: Int)

    fun continuous(entries: List<ForecastConsensus.Entry<Double>>, options: ContinuousOptions = ContinuousOptions()): ContinuousResult =
        when (options.engine) {
            ForecastEngine.CALIBRATION -> calibrationConsensus(entries, options)
            ForecastEngine.SCENARIOS -> scenarioConsensus(entries, options)
            ForecastEngine.ADAPTIVE -> adaptiveConsensus(entries, options)
            ForecastEngine.MULTI_CONSENSUS -> multiConsensus(entries, options)
        }

    fun precipitation(rows: List<ForecastConsensus.PrecipitationRow>, options: PrecipitationOptions = PrecipitationOptions()): PrecipitationResult {
        val usable = rows.map { row ->
            row.copy(
                amountMm = ForecastPhysicalLimits.precipitation(row.amountMm),
                probabilityPercent = ForecastPhysicalLimits.percentage(row.probabilityPercent)
            )
        }.filter { row ->
            row.amountMm != null || row.probabilityPercent != null
        }
        if (usable.isEmpty()) return PrecipitationResult(
            engine = options.engine, effectiveEngine = options.engine, probabilityPercent = null,
            conditionalAmountMm = null, centralAmountMm = null, expectedAmountMm = null,
            modelCount = 0, familyCount = 0, wetModelCount = 0, source = null,
            minMm = null, maxMm = null, conditionalStdDev = null, interval = Interval(null, null),
            scenarioCount = 0, scenarios = emptyList(), calibrationCoverage = 0.0,
            calibratedFamilyCount = 0, calibrationStrength = 0.0, occurrenceCalibrationCoverage = 0.0,
            fallback = false, fallbackReason = null, explanation = null
        )

        val occurrenceWeights = ForecastConsensus.familyBalancedWeights(usable.map { it.model }, options.localWeights)
        val occurrence = occurrenceAdjustment(options.calibration, usable.map { it.model }, options.localWeights)
        val canCalibrateOccurrence = (options.engine == ForecastEngine.CALIBRATION || options.engine == ForecastEngine.ADAPTIVE) &&
            occurrence.coverage >= MIN_CALIBRATION_COVERAGE && occurrence.familyCount >= MIN_CALIBRATED_FAMILIES

        var probabilitySum = 0.0
        var totalWeight = 0.0
        var nativeProbabilityCount = 0
        usable.forEach { row ->
            val weight = occurrenceWeights[row.model] ?: return@forEach
            if (weight <= 0.0) return@forEach
            var probability = row.probabilityPercent?.takeIf { it in 0..100 }?.let {
                nativeProbabilityCount++
                it / 100.0
            } ?: if ((row.amountMm ?: Double.NEGATIVE_INFINITY) > options.threshold) 1.0 else 0.0
            if (canCalibrateOccurrence) probability = clamp(probability + occurrence.delta * 0.65, 0.0, 1.0)
            probabilitySum += weight * probability
            totalWeight += weight
        }
        val probability = if (totalWeight > 0.0) probabilitySum / totalWeight else null
        val wetRows = usable.filter { (it.amountMm ?: Double.NEGATIVE_INFINITY) > options.threshold }
        // La quantité conditionnelle utilise exclusivement la calibration sur
        // les hits pluie (prévu humide ET observé humide). Le biais quotidien
        // générique n'est jamais appliqué aux millimètres conditionnels.
        val amountCalibration = options.calibration.mapNotNull { (model, profile) ->
            val bias = profile.wetHitBias?.takeIf(Double::isFinite) ?: return@mapNotNull null
            val score = profile.wetHitScore ?: return@mapNotNull null
            val stdDev = profile.wetHitStandardDeviation
                ?.takeIf { it.isFinite() && it >= 0.0 } ?: return@mapNotNull null
            val mae = profile.wetHitMeanAbsoluteError?.takeIf(Double::isFinite) ?: return@mapNotNull null
            if (profile.wetHitSampleSize < MIN_CALIBRATION_SAMPLES) return@mapNotNull null
            model to ForecastCalibrationProfile(
                bias = bias,
                score = score,
                standardDeviation = stdDev,
                meanAbsoluteError = mae,
                sampleSize = profile.wetHitSampleSize,
                leadDay = profile.leadDay
            )
        }.toMap()
        val amountResult = continuous(
            wetRows.mapNotNull { row -> row.amountMm?.takeIf(Double::isFinite)?.let { ForecastConsensus.Entry(row.model, it) } },
            ContinuousOptions(
                engine = options.engine,
                localWeights = options.localWeights,
                calibration = amountCalibration,
                tight = options.amountTight,
                wide = options.amountWide,
                // Une valeur classée humide ne doit jamais être calibrée à 0 mm.
                min = options.threshold
            )
        )
        val conditional = amountResult.central
        val amounts = usable.mapNotNull { it.amountMm?.takeIf(Double::isFinite) }
        val source = when {
            nativeProbabilityCount == usable.size -> ForecastConsensus.PrecipitationSource.PROBABILITY
            nativeProbabilityCount > 0 -> ForecastConsensus.PrecipitationSource.MIXED
            else -> ForecastConsensus.PrecipitationSource.MODEL_AGREEMENT
        }
        val centralAmount = when {
            amounts.isEmpty() -> null
            probability != null && probability >= 0.5 && conditional != null -> conditional
            else -> 0.0
        }
        val expectedAmount = when {
            probability != null && conditional != null -> probability * conditional
            amounts.isNotEmpty() && wetRows.isEmpty() -> 0.0
            else -> null
        }
        return PrecipitationResult(
            engine = options.engine,
            effectiveEngine = amountResult.effectiveEngine,
            probabilityPercent = probability?.let { (it * 100).roundToInt().coerceIn(0, 100) },
            conditionalAmountMm = conditional,
            centralAmountMm = centralAmount,
            expectedAmountMm = expectedAmount,
            modelCount = usable.size,
            familyCount = usable.map { ForecastConsensus.groupFor(it.model) }.distinct().size,
            wetModelCount = wetRows.size,
            source = source,
            minMm = amounts.minOrNull(),
            maxMm = amounts.maxOrNull(),
            conditionalStdDev = amountResult.stats?.stdDev,
            interval = amountResult.interval,
            scenarioCount = amountResult.scenarioCount,
            scenarios = amountResult.scenarios,
            calibrationCoverage = amountResult.calibrationCoverage,
            calibratedFamilyCount = amountResult.calibratedFamilyCount,
            calibrationStrength = amountResult.calibrationStrength,
            occurrenceCalibrationCoverage = occurrence.coverage,
            fallback = amountResult.fallback,
            fallbackReason = amountResult.fallbackReason,
            explanation = amountResult.explanation
        )
    }

    private fun multiConsensus(entries: List<ForecastConsensus.Entry<Double>>, options: ContinuousOptions): ContinuousResult {
        val balanced = balance(entries, options.localWeights)
        val robust = robustFromBalanced(balanced, options.tight, options.wide)
            ?: return emptyResult(ForecastEngine.MULTI_CONSENSUS)
        return ContinuousResult(
            central = bound(robust.central, options.min, options.max), stats = robust.stats, rows = robust.rows,
            interval = Interval(bound(robust.interval.low, options.min, options.max), bound(robust.interval.high, options.min, options.max)),
            engine = ForecastEngine.MULTI_CONSENSUS, effectiveEngine = ForecastEngine.MULTI_CONSENSUS,
            explanation = Explanation.ROBUST_FAMILY_BALANCED, modelCount = robust.modelCount,
            familyCount = robust.familyCount, engineConvergencePercent = robust.convergence
        )
    }

    private fun calibrationConsensus(entries: List<ForecastConsensus.Entry<Double>>, options: ContinuousOptions): ContinuousResult {
        val usable = entries.filter { it.value.isFinite() }
        if (usable.isEmpty()) return emptyResult(ForecastEngine.CALIBRATION)
        val familyWeights = ForecastConsensus.familyBalancedWeights(usable.map { it.model }, options.localWeights)
        val totalMass = familyWeights.values.sum().takeIf { it > 0 } ?: 1.0
        val calibratedIds = mutableListOf<WeatherModel>()
        var calibratedMass = 0.0
        var weightedScore = 0.0
        var weightedScoreMass = 0.0
        var noiseSum = 0.0
        var noiseMass = 0.0
        var strengthSum = 0.0
        val skills = mutableMapOf<WeatherModel, Double>()

        val corrected = usable.map { row ->
            val profile = options.calibration[row.model]?.takeIf { it.bias.isFinite() && it.sampleSize >= MIN_CALIBRATION_SAMPLES }
                ?: return@map row
            val strength = calibrationStrength(profile)
            val score = profile.score.coerceIn(0, 100).toDouble()
            val skill = 0.85 + 0.3 * (score / 100.0)
            val familyWeight = familyWeights[row.model] ?: 0.0
            // 0.0 est une vraie dispersion nulle, pas une valeur absente.
            val std = profile.standardDeviation.takeIf { it.isFinite() && it >= 0.0 }
            val mae = profile.meanAbsoluteError.takeIf { it.isFinite() && it >= 0.0 }
            val noise = max(0.0, std ?: mae ?: 0.0)
            calibratedIds += row.model
            calibratedMass += familyWeight
            weightedScore += score * familyWeight
            weightedScoreMass += familyWeight
            noiseSum += noise * familyWeight
            noiseMass += familyWeight
            strengthSum += strength * familyWeight
            skills[row.model] = skill
            row.copy(value = bound(row.value - profile.bias * strength, options.min, options.max) ?: row.value)
        }
        val coverage = clamp(calibratedMass / totalMass, 0.0, 1.0)
        val calibratedFamilies = calibratedIds.map(ForecastConsensus::groupFor).distinct().size
        val averageStrength = if (calibratedMass > 0) strengthSum / calibratedMass else 0.0
        if (calibratedFamilies < MIN_CALIBRATED_FAMILIES || coverage < MIN_CALIBRATION_COVERAGE) {
            val fallback = multiConsensus(entries, options.copy(engine = ForecastEngine.MULTI_CONSENSUS))
            return fallback.copy(
                engine = ForecastEngine.CALIBRATION, fallback = true,
                fallbackReason = FallbackReason.INSUFFICIENT_CALIBRATION,
                calibrationCoverage = coverage, calibratedFamilyCount = calibratedFamilies,
                calibrationStrength = averageStrength
            )
        }
        val skillWeights = options.localWeights.toMutableMap()
        corrected.forEach { row -> skills[row.model]?.let { skill -> skillWeights[row.model] = (skillWeights[row.model] ?: 1.0) * skill } }
        val robust = robustFromBalanced(balance(corrected, skillWeights), options.tight, options.wide)
            ?: return emptyResult(ForecastEngine.CALIBRATION)
        val residualNoise = if (noiseMass > 0) noiseSum / noiseMass else 0.0
        val extraSigma = residualNoise * (0.2 + (1.0 - averageStrength) * 0.25)
        val interval = spreadInterval(robust.rows, robust.central, robust.stats.stdDev, extraSigma)
        return ContinuousResult(
            central = bound(robust.central, options.min, options.max), stats = robust.stats, rows = robust.rows,
            interval = Interval(bound(interval.low, options.min, options.max), bound(interval.high, options.min, options.max)),
            engine = ForecastEngine.CALIBRATION, effectiveEngine = ForecastEngine.CALIBRATION,
            calibrationCoverage = coverage, calibratedFamilyCount = calibratedFamilies,
            calibrationStrength = averageStrength,
            historicalScore = if (weightedScoreMass > 0) weightedScore / weightedScoreMass else null,
            explanation = Explanation.BIAS_CORRECTED_SKILL_WEIGHTED,
            modelCount = robust.modelCount, familyCount = robust.familyCount,
            engineConvergencePercent = robust.convergence
        )
    }

    private fun scenarioConsensus(entries: List<ForecastConsensus.Entry<Double>>, options: ContinuousOptions): ContinuousResult {
        val balanced = balance(entries, options.localWeights)
        val base = robustFromBalanced(balanced, options.tight, options.wide) ?: return emptyResult(ForecastEngine.SCENARIOS)
        val split = scenarioSplit(balanced.rows, options.tight)
        if (split == null) {
            val fallback = multiConsensus(entries, options.copy(engine = ForecastEngine.MULTI_CONSENSUS))
            return fallback.copy(
                engine = ForecastEngine.SCENARIOS, effectiveEngine = ForecastEngine.MULTI_CONSENSUS,
                fallback = true, fallbackReason = FallbackReason.SINGLE_SCENARIO,
                scenarioCount = 1, dominantShare = 1.0, explanation = Explanation.SINGLE_SCENARIO
            )
        }
        val all = split.first + split.second
        val totalWeight = all.sumOf { it.weight }
        val clusters = listOf(split.first, split.second).map { rows ->
            val weight = rows.sumOf { it.weight }
            val central = ForecastConsensus.weightedMedian(rows)
            val stats = ForecastConsensus.weightedStats(rows)
            Cluster(rows, weight, if (totalWeight > 0) weight / totalWeight else 0.0, central, weightedQuantile(rows, .1), weightedQuantile(rows, .9), stats?.stdDev ?: 0.0)
        }.sortedByDescending { it.weight }
        val dominant = clusters.first()
        val diagnostics = clusters.map {
            Scenario(
                it.share,
                bound(it.central, options.min, options.max),
                bound(it.low, options.min, options.max),
                bound(it.high, options.min, options.max)
            )
        }
        if (dominant.share + EPS < MIN_DOMINANT_SCENARIO_SHARE) {
            // Partage équilibré : les deux scénarios restent visibles pour le
            // diagnostic, mais la valeur centrale revient au Multi-consensus.
            val fallback = multiConsensus(entries, options.copy(engine = ForecastEngine.MULTI_CONSENSUS))
            return fallback.copy(
                engine = ForecastEngine.SCENARIOS,
                effectiveEngine = ForecastEngine.MULTI_CONSENSUS,
                fallback = true,
                fallbackReason = FallbackReason.NO_DOMINANT_SCENARIO,
                scenarioCount = 2,
                dominantShare = dominant.share,
                scenarioGap = split.third,
                scenarios = diagnostics,
                explanation = Explanation.BALANCED_SCENARIOS
            )
        }
        val central = dominant.central ?: base.central
        val interval = spreadInterval(dominant.rows, central, dominant.stdDev)
        return ContinuousResult(
            central = bound(central, options.min, options.max), stats = ForecastConsensus.weightedStats(dominant.rows), rows = dominant.rows,
            interval = Interval(bound(interval.low, options.min, options.max), bound(interval.high, options.min, options.max)),
            engine = ForecastEngine.SCENARIOS, effectiveEngine = ForecastEngine.SCENARIOS,
            scenarioCount = 2, dominantShare = dominant.share, scenarioGap = split.third,
            scenarios = diagnostics,
            explanation = Explanation.DOMINANT_SCENARIO,
            modelCount = balanced.modelCount, familyCount = balanced.familyCount,
            engineConvergencePercent = (dominant.share * 100).roundToInt().coerceIn(0, 100)
        )
    }

    private fun adaptiveConsensus(entries: List<ForecastConsensus.Entry<Double>>, options: ContinuousOptions): ContinuousResult {
        val multi = multiConsensus(entries, options.copy(engine = ForecastEngine.MULTI_CONSENSUS))
        val calibration = calibrationConsensus(entries, options.copy(engine = ForecastEngine.CALIBRATION))
        val scenarios = scenarioConsensus(entries, options.copy(engine = ForecastEngine.SCENARIOS))
        val scenarioGap = scenarios.scenarioGap
        val strongScenario = scenarios.scenarioCount > 1 &&
            scenarios.effectiveEngine == ForecastEngine.SCENARIOS &&
            (scenarios.dominantShare ?: 0.0) >= MIN_DOMINANT_SCENARIO_SHARE && (scenarios.dominantShare ?: 1.0) <= 0.82 &&
            scenarioGap?.isFinite() == true &&
            scenarioGap >= max(options.tight * 1.1, (multi.stats?.stdDev ?: 0.0) * 0.5)
        val components = mapOf(
            ForecastEngine.MULTI_CONSENSUS to multi.central,
            ForecastEngine.CALIBRATION to calibration.central,
            ForecastEngine.SCENARIOS to scenarios.central
        )
        if (strongScenario) return scenarios.copy(
            engine = ForecastEngine.ADAPTIVE, effectiveEngine = ForecastEngine.SCENARIOS,
            adaptiveComponents = components, explanation = Explanation.ADAPTIVE_SCENARIO
        )
        val calibrationReady = !calibration.fallback && calibration.calibrationCoverage >= 0.5 &&
            calibration.calibratedFamilyCount >= MIN_CALIBRATED_FAMILIES &&
            (calibration.historicalScore == null || calibration.historicalScore >= 45.0)
        if (calibrationReady && calibration.central != null && multi.central != null) {
            val trust = clamp(
                0.35 + calibration.calibrationCoverage * 0.25 + calibration.calibrationStrength * 0.2 +
                    ((calibration.historicalScore ?: 50.0) / 100.0) * 0.15,
                0.5, 0.85
            )
            val central = bound(calibration.central * trust + multi.central * (1.0 - trust), options.min, options.max)
            val sigma = max(calibration.stats?.stdDev ?: 0.0, multi.stats?.stdDev ?: 0.0)
            val intervalRows = calibration.rows.ifEmpty { multi.rows }
            val interval = spreadInterval(intervalRows, central ?: calibration.central, sigma)
            return calibration.copy(
                central = central,
                interval = Interval(bound(interval.low, options.min, options.max), bound(interval.high, options.min, options.max)),
                engine = ForecastEngine.ADAPTIVE, effectiveEngine = ForecastEngine.CALIBRATION,
                adaptiveTrust = trust, adaptiveComponents = components,
                explanation = Explanation.ADAPTIVE_CALIBRATION_BLEND
            )
        }
        return multi.copy(
            engine = ForecastEngine.ADAPTIVE, effectiveEngine = ForecastEngine.MULTI_CONSENSUS,
            adaptiveComponents = components, explanation = Explanation.ADAPTIVE_ROBUST_FALLBACK
        )
    }

    private data class Cluster(val rows: List<ForecastConsensus.WeightedEntry>, val weight: Double, val share: Double, val central: Double?, val low: Double?, val high: Double?, val stdDev: Double)
    private data class OccurrenceAdjustment(val delta: Double, val coverage: Double, val familyCount: Int)

    private fun occurrenceAdjustment(calibration: Map<WeatherModel, ForecastCalibrationProfile>, models: List<WeatherModel>, localWeights: Map<WeatherModel, Double>): OccurrenceAdjustment {
        val ids = models.distinct()
        if (ids.isEmpty()) return OccurrenceAdjustment(0.0, 0.0, 0)
        val balance = ForecastConsensus.familyBalancedWeights(ids, localWeights)
        val totalMass = balance.values.sum().takeIf { it > 0 } ?: 1.0
        var adjustment = 0.0
        var adjustmentMass = 0.0
        val calibratedIds = mutableListOf<WeatherModel>()
        ids.forEach { model ->
            val profile = calibration[model] ?: return@forEach
            if (profile.sampleSize < MIN_CALIBRATION_SAMPLES || profile.observedWetDays == null || profile.forecastWetDays == null) return@forEach
            val n = max(1, profile.sampleSize)
            val observed = profile.observedWetDays.toDouble() / n
            val forecast = profile.forecastWetDays.toDouble() / n
            val score = profile.score.coerceIn(0, 100)
            val quality = clamp(score / 100.0, 0.25, 1.0)
            val strength = calibrationStrength(profile)
            val familyWeight = balance[model] ?: 0.0
            val weight = familyWeight * quality * strength
            if (weight <= 0.0) return@forEach
            calibratedIds += model
            adjustment += (observed - forecast) * weight
            adjustmentMass += weight
        }
        val calibratedMass = calibratedIds.sumOf { balance[it] ?: 0.0 }
        return OccurrenceAdjustment(
            delta = if (adjustmentMass > 0) clamp(adjustment / adjustmentMass, -0.2, 0.2) else 0.0,
            coverage = clamp(calibratedMass / totalMass, 0.0, 1.0),
            familyCount = calibratedIds.map(ForecastConsensus::groupFor).distinct().size
        )
    }

    private fun calibrationStrength(profile: ForecastCalibrationProfile): Double =
        if (profile.sampleSize < MIN_CALIBRATION_SAMPLES) 0.0 else clamp(profile.sampleSize.toDouble() / FULL_CALIBRATION_SAMPLES, 0.45, 1.0)

    private fun balance(entries: List<ForecastConsensus.Entry<Double>>, localWeights: Map<WeatherModel, Double>): Balanced {
        val valid = entries.filter { it.value.isFinite() }
        val weights = ForecastConsensus.familyBalancedWeights(valid.map { it.model }, localWeights)
        val rows = valid.mapNotNull { row ->
            val weight = weights[row.model] ?: return@mapNotNull null
            if (weight <= 0.0) null else ForecastConsensus.WeightedEntry(row.model, row.value, weight)
        }
        return Balanced(rows, valid.map { it.model }.distinct().size, valid.map { ForecastConsensus.groupFor(it.model) }.distinct().size)
    }

    private fun robustFromBalanced(balanced: Balanced, tight: Double, wide: Double): Robust? {
        if (balanced.rows.isEmpty()) return null
        val median = ForecastConsensus.weightedMedian(balanced.rows) ?: return null
        val mad = weightedQuantile(balanced.rows.map { it.copy(value = abs(it.value - median)) }, 0.5) ?: 0.0
        val robustScale = max(tight * 0.35, max(mad * 1.4826, EPS))
        val huberLimit = 1.5 * robustScale
        val rows = balanced.rows.map { row ->
            val distance = abs(row.value - median)
            val factor = if (distance <= huberLimit) 1.0 else huberLimit / max(distance, EPS)
            row.copy(weight = row.weight * factor)
        }
        val stats = ForecastConsensus.weightedStats(rows) ?: return null
        val central = weightedMean(rows) ?: return null
        return Robust(
            central = central, stats = stats, interval = spreadInterval(rows, central, stats.stdDev), rows = rows,
            convergence = if (balanced.familyCount >= 2) ForecastConsensus.scoreFromDispersion(stats.stdDev, tight, wide) else null,
            modelCount = balanced.modelCount, familyCount = balanced.familyCount
        )
    }

    private fun scenarioSplit(rows: List<ForecastConsensus.WeightedEntry>, tight: Double): Triple<List<ForecastConsensus.WeightedEntry>, List<ForecastConsensus.WeightedEntry>, Double>? {
        val sorted = rows.filter { it.value.isFinite() && it.weight.isFinite() && it.weight > 0 }.sortedBy { it.value }
        if (sorted.size < 4) return null
        val total = sorted.sumOf { it.weight }
        val center = ForecastConsensus.weightedMedian(sorted) ?: return null
        val mad = weightedQuantile(sorted.map { it.copy(value = abs(it.value - center)) }, .5) ?: 0.0
        val minimumGap = max(tight * .9, mad * 1.25)
        var bestIndex = -1
        var bestScore = Double.NEGATIVE_INFINITY
        var leftWeight = 0.0
        var bestGap = 0.0
        for (index in 0 until sorted.lastIndex) {
            leftWeight += sorted[index].weight
            val rightWeight = total - leftWeight
            val gap = sorted[index + 1].value - sorted[index].value
            val minority = min(leftWeight, rightWeight) / total
            if (minority < .18 || gap < minimumGap) continue
            val score = gap * (.5 + minority)
            if (score > bestScore) { bestScore = score; bestIndex = index; bestGap = gap }
        }
        return if (bestIndex >= 0) Triple(sorted.take(bestIndex + 1), sorted.drop(bestIndex + 1), bestGap) else null
    }

    private fun spreadInterval(rows: List<ForecastConsensus.WeightedEntry>, center: Double, stdDev: Double, extraSigma: Double = 0.0): Interval {
        val q10 = weightedQuantile(rows, .1)
        val q90 = weightedQuantile(rows, .9)
        val sigma = sqrt(max(0.0, stdDev * stdDev + extraSigma * extraSigma))
        val normalLow = center - 1.2816 * sigma
        val normalHigh = center + 1.2816 * sigma
        return Interval(if (q10 != null) min(q10, normalLow) else normalLow, if (q90 != null) max(q90, normalHigh) else normalHigh)
    }

    private fun weightedQuantile(entries: List<ForecastConsensus.WeightedEntry>, quantile: Double): Double? {
        val rows = entries.filter { it.value.isFinite() && it.weight.isFinite() && it.weight > 0 }.sortedBy { it.value }
        if (rows.isEmpty()) return null
        val target = clamp(quantile, 0.0, 1.0) * rows.sumOf { it.weight }
        var cumulative = 0.0
        rows.forEach { row -> cumulative += row.weight; if (cumulative + EPS >= target) return row.value }
        return rows.last().value
    }

    private fun weightedMean(entries: List<ForecastConsensus.WeightedEntry>): Double? {
        val rows = entries.filter { it.value.isFinite() && it.weight.isFinite() && it.weight > 0 }
        val total = rows.sumOf { it.weight }
        return if (total > 0) rows.sumOf { it.value * it.weight } / total else null
    }

    private fun bound(value: Double?, min: Double?, max: Double?): Double? {
        if (value == null || !value.isFinite()) return value
        return when {
            min != null && max != null -> clamp(value, min, max)
            min != null -> kotlin.math.max(min, value)
            max != null -> kotlin.math.min(max, value)
            else -> value
        }
    }

    private fun clamp(value: Double, min: Double, max: Double): Double = kotlin.math.max(min, kotlin.math.min(max, value))

    private fun emptyResult(engine: ForecastEngine) = ContinuousResult(
        central = null, stats = null, interval = Interval(null, null), engine = engine,
        effectiveEngine = engine, scenarioCount = 0, dominantShare = null
    )
}
