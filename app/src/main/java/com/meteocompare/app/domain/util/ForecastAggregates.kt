package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.effectiveCloudCover
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.usecase.ForecastEngineV3
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import java.time.Instant
import kotlin.math.roundToInt

/** Résultat agrégé utilisé par la liste des villes et le mini-forecast widget. */
internal data class HourlyForecastAggregate(
    val startInstant: Instant,
    val temperatures: List<Double?>,
    val precipitationProbabilities: List<Int?>,
    val precipitationAmountsMm: List<Double?>,
    val conditions: List<WeatherCondition?>,
    /** Consensus cloud cover, available when conditions were requested. */
    val cloudCoverPercents: List<Int?> = emptyList(),
    val conditionDiagnostics: List<ConditionDecision> = emptyList()
)

/**
 * Agrégats horaires (12 ou 24 h) construits avec le même moteur consensus robuste que le détail ville.
 * Les variantes apparentées partagent une voix ; température = médiane pondérée,
 * pluie = P(pluie) + quantité conditionnelle.
 */
internal object ForecastAggregates {
    private const val HOME_HOUR_COUNT = 24

    /** Canonical current-hour decision for Home, City Detail, widgets and notifications.
     * Uses the exact same UTC anchor and per-model decisions as the forecast strip. */
    fun currentConditionDecision(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): ConditionDecision = nextHours(forecast, now, true, engineContext, 1)
        .conditionDiagnostics.single()

    /** Conserve la fenêtre 12 h pour les appels historiques et les scénarios courts. */
    fun next12h(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        includeConditions: Boolean = false,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): HourlyForecastAggregate = nextHours(forecast, now, includeConditions, engineContext, 12)

    /** 24 échéances consécutives, partant de l'heure de référence réelle. */
    fun next24h(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        includeConditions: Boolean = false,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): HourlyForecastAggregate = nextHours(forecast, now, includeConditions, engineContext, HOME_HOUR_COUNT)

    private fun nextHours(
        forecast: CityForecast,
        now: Instant,
        includeConditions: Boolean,
        engineContext: ForecastEngineContext,
        hourCount: Int
    ): HourlyForecastAggregate {
        val temperatures = ArrayList<Double?>(hourCount)
        val probabilities = ArrayList<Int?>(hourCount)
        val amounts = ArrayList<Double?>(hourCount)
        val conditions = if (includeConditions) ArrayList<WeatherCondition?>(hourCount) else null
        val clouds = if (includeConditions) ArrayList<Int?>(hourCount) else null
        val diagnostics = if (includeConditions) ArrayList<ConditionDecision>(hourCount) else null
        val startInstant = HourlySampling.anchor(forecast, now)

        repeat(hourCount) { hourOffset ->
            val target = startInstant.plusSeconds(hourOffset * 3_600L)
            val tempRows = mutableListOf<ForecastConsensus.Entry<Double>>()
            val precipRows = mutableListOf<ForecastConsensus.PrecipitationRow>()
            val conditionRows = mutableListOf<ForecastConsensus.Entry<WeatherCondition>>()
            val derivedConditionRows = mutableListOf<ForecastConsensus.Entry<WeatherCondition>>()
            val cloudRows = mutableListOf<ForecastConsensus.Entry<Double>>()

            forecast.seriesByModel.forEach { (model, series) ->
                val timestamps = series.hourly.timestamps
                val index = with(HourlySampling) { timestamps.exactIndex(target) } ?: return@forEach

                series.hourly.temperature2m.getOrNull(index)?.let {
                    tempRows += ForecastConsensus.Entry(model, it)
                }
                val amount = series.hourly.precipitation.getOrNull(index)
                val probability = series.hourly.precipitationProbability.getOrNull(index)
                if (amount != null || probability != null) {
                    precipRows += ForecastConsensus.PrecipitationRow(model, amount, probability)
                }
                if (conditions != null) {
                    // Classify once per model. A valid WMO code always stays native;
                    // same-model variables only supply a fallback when WMO is absent.
                    val decision = ConditionDecisionEngine.hourly(series.hourly, index)
                    decision.condition?.let { resolved ->
                        val row = ForecastConsensus.Entry(model, resolved)
                        if (decision.origin == ConditionOrigin.HOURLY_WMO) conditionRows += row
                        else derivedConditionRows += row
                    }
                    series.hourly.effectiveCloudCover(index)
                        ?.let { cloudRows += ForecastConsensus.Entry(model, it.toDouble()) }
                }
            }

            val temperatureCentral = ForecastEngineV3.continuous(
                tempRows,
                ForecastEngineV3.ContinuousOptions(
                    engine = engineContext.engine,
                    localWeights = engineContext.localWeights(ForecastEngineVariable.TEMPERATURE),
                    calibration = emptyMap(), // historique J+1 ≠ prévision horaire
                    tight = 0.5,
                    wide = 3.0
                )
            ).central
            temperatures += temperatureCentral
            val precipitation = ForecastEngineV3.precipitation(
                precipRows,
                ForecastEngineV3.PrecipitationOptions(
                    engine = engineContext.engine,
                    threshold = PrecipitationThresholds.HOURLY_OCCURRENCE_MM,
                    localWeights = engineContext.localWeights(ForecastEngineVariable.PRECIPITATION),
                    calibration = emptyMap(), // historique J+1 ≠ prévision horaire
                    amountTight = 0.5,
                    amountWide = 4.0
                )
            )
            probabilities += precipitation.probabilityPercent
            amounts += precipitation.centralAmountMm
            if (conditions != null) {
                val cloudCentral = ForecastEngineV3.continuous(
                    cloudRows,
                    ForecastEngineV3.ContinuousOptions(
                        engine = engineContext.engine,
                        localWeights = engineContext.localWeights(ForecastEngineVariable.CLOUD),
                        calibration = emptyMap(), // historique J+1 ≠ nébulosité horaire
                        tight = 10.0,
                        wide = 50.0,
                        min = 0.0,
                        max = 100.0
                    )
                ).central
                clouds?.add(cloudCentral?.takeIf(Double::isFinite)?.roundToInt()?.coerceIn(0, 100))
                val supportModels = buildSet {
                    addAll(tempRows.map { it.model })
                    addAll(precipRows.map { it.model })
                    addAll(cloudRows.map { it.model })
                    addAll(conditionRows.map { it.model })
                    addAll(derivedConditionRows.map { it.model })
                }
                val result = WeatherConditionConsensus.resolveDecision(
                    nativeEntries = conditionRows,
                    derivedEntries = derivedConditionRows,
                    cloudCoverPercent = cloudCentral,
                    cloudCoverFamilyCount = cloudRows.map { ForecastConsensus.groupFor(it.model) }.distinct().size,
                    supportModels = supportModels,
                    localWeights = engineContext.localWeights(ForecastEngineVariable.CLOUD)
                )
                diagnostics?.add(result)
                conditions += result.condition
            }
        }

        return HourlyForecastAggregate(
            startInstant, temperatures, probabilities, amounts, conditions.orEmpty(), clouds.orEmpty(),
            diagnostics.orEmpty()
        )
    }

    /**
     * Compatibilité des tests/utilitaires sans identifiants modèles.
     * Même hiérarchie sémantique que la production, avec une voix par valeur.
     */
    internal fun conditionConsensus(values: List<WeatherCondition>): WeatherCondition? =
        WeatherConditionConsensus.resolveUnweighted(values)
}
