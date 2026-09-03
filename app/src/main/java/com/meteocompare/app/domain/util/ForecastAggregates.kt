package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import com.meteocompare.app.domain.usecase.ForecastEngineV3
import java.time.Instant

/** Résultat agrégé utilisé par la liste des villes et le mini-forecast widget. */
internal data class Next12hForecast(
    val startInstant: Instant,
    val temperatures: List<Double?>,
    val precipitationProbabilities: List<Int?>,
    val precipitationAmountsMm: List<Double?>,
    val conditions: List<WeatherCondition?>
)

/**
 * Agrégats 12 h construits avec le même moteur consensus robuste que le détail ville.
 * Les variantes apparentées partagent une voix ; température = médiane pondérée,
 * pluie = P(pluie) + quantité conditionnelle.
 */
internal object ForecastAggregates {
    private const val HOUR_COUNT = 12

    fun next12h(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        includeConditions: Boolean = false,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): Next12hForecast {
        val temperatures = ArrayList<Double?>(HOUR_COUNT)
        val probabilities = ArrayList<Int?>(HOUR_COUNT)
        val amounts = ArrayList<Double?>(HOUR_COUNT)
        val conditions = if (includeConditions) ArrayList<WeatherCondition?>(HOUR_COUNT) else null
        val startInstant = HourlySampling.anchor(forecast, now)

        repeat(HOUR_COUNT) { hourOffset ->
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
                        ?.let { conditionRows += ForecastConsensus.Entry(model, it) }
                    series.hourly.cloudCover.getOrNull(index)
                        ?.takeIf { it in 0..100 }
                        ?.let { cloudRows += ForecastConsensus.Entry(model, it.toDouble()) }
                }
            }

            val temperatureCentral = ForecastEngineV3.continuous(
                tempRows,
                ForecastEngineV3.ContinuousOptions(
                    engine = engineContext.engine,
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
                    supportModels = supportModels
                ).vote.value
            }
        }

        return Next12hForecast(startInstant, temperatures, probabilities, amounts, conditions.orEmpty())
    }

    /**
     * Compatibilité des tests/utilitaires sans identifiants modèles.
     * Même hiérarchie sémantique que la production, avec une voix par valeur.
     */
    internal fun conditionConsensus(values: List<WeatherCondition>): WeatherCondition? =
        WeatherConditionConsensus.resolveUnweighted(values)
}
