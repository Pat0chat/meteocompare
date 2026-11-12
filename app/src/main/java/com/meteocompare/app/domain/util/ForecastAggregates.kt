package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.domain.model.WeatherCondition
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
    val cloudCoverPercents: List<Int?> = emptyList()
)

/**
 * Agrégats horaires (12 ou 24 h) construits avec le même moteur consensus robuste que le détail ville.
 * Les variantes apparentées partagent une voix ; température = médiane pondérée,
 * pluie = P(pluie) + quantité conditionnelle.
 */
internal object ForecastAggregates {
    private const val HOME_HOUR_COUNT = 24

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
        val startInstant = HourlySampling.anchor(forecast, now)

        repeat(hourCount) { hourOffset ->
            val target = startInstant.plusSeconds(hourOffset * 3_600L)
            val tempRows = mutableListOf<ForecastConsensus.Entry<Double>>()
            val precipRows = mutableListOf<ForecastConsensus.PrecipitationRow>()
            val conditionRows = mutableListOf<ForecastConsensus.Entry<WeatherCondition>>()
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
                    WeatherCondition.fromWmoCode(series.hourly.weatherCode.getOrNull(index))
                        ?.takeUnless { it == WeatherCondition.UNKNOWN }
                        ?.let { native ->
                            conditionRows += ForecastConsensus.Entry(
                                model, if (native == WeatherCondition.OVERCAST)
                                    series.resolveHourlyCondition(index) ?: native else native
                            )
                        }
                    series.hourly.cloudCover.getOrNull(index)
                        ?.takeIf { it in 0..100 }
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
                }
                conditions += WeatherConditionConsensus.resolveAggregate(
                    nativeEntries = conditionRows,
                    temperatureCentralC = temperatureCentral,
                    precipitationCentralMm = precipitation.centralAmountMm,
                    cloudCoverPercent = cloudCentral,
                    cloudCoverFamilyCount = cloudRows.map { ForecastConsensus.groupFor(it.model) }.distinct().size,
                    supportModels = supportModels
                ).vote.value
            }
        }

        return HourlyForecastAggregate(
            startInstant, temperatures, probabilities, amounts, conditions.orEmpty(), clouds.orEmpty()
        )
    }

    /**
     * Compatibilité des tests/utilitaires sans identifiants modèles.
     * Même hiérarchie sémantique que la production, avec une voix par valeur.
     */
    internal fun conditionConsensus(values: List<WeatherCondition>): WeatherCondition? =
        WeatherConditionConsensus.resolveUnweighted(values)
}
