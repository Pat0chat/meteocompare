package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.usecase.ForecastConsensus
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
 * Agrégats 12 h construits avec le même moteur Consensus v2 que le détail ville.
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

            forecast.seriesByModel.forEach { (model, series) ->
                val timestamps = series.hourly.timestamps
                val index = with(HourlySampling) { timestamps.nearestIndex(target) } ?: return@forEach
                if (!HourlySampling.isCloseEnough(timestamps[index], target)) return@forEach

                series.hourly.temperature2m.getOrNull(index)?.let {
                    tempRows += ForecastConsensus.Entry(model, it)
                }
                val amount = series.hourly.precipitation.getOrNull(index)
                val probability = series.hourly.precipitationProbability.getOrNull(index)
                if (amount != null || probability != null) {
                    precipRows += ForecastConsensus.PrecipitationRow(model, amount, probability)
                }
                if (conditions != null) {
                    val condition = WeatherCondition.fromWmoCode(series.hourly.weatherCode.getOrNull(index))
                        ?.takeUnless { it == WeatherCondition.UNKNOWN }
                        ?: WeatherCondition.inferFromPrecipAndTemp(
                            precipMm = amount,
                            tempMinC = series.hourly.temperature2m.getOrNull(index)
                        )
                    condition?.let { conditionRows += ForecastConsensus.Entry(model, it) }
                }
            }

            temperatures += ForecastEngineV3.continuous(
                tempRows,
                ForecastEngineV3.ContinuousOptions(
                    engine = engineContext.engine,
                    calibration = emptyMap(), // historique J+1 ≠ prévision horaire
                    tight = 0.5,
                    wide = 3.0
                )
            ).central
            val precipitation = ForecastEngineV3.precipitation(
                precipRows,
                ForecastEngineV3.PrecipitationOptions(
                    engine = engineContext.engine,
                    threshold = 0.1,
                    calibration = emptyMap(), // historique J+1 ≠ prévision horaire
                    amountTight = 0.5,
                    amountWide = 4.0
                )
            )
            probabilities += precipitation.probabilityPercent
            amounts += precipitation.centralAmountMm
            if (conditions != null) {
                conditions += ForecastConsensus.conditionVote(conditionRows).value
            }
        }

        return Next12hForecast(startInstant, temperatures, probabilities, amounts, conditions.orEmpty())
    }

    /** Compatibilité des tests/utilitaires : vote familial, pas vote brut par modèle. */
    internal fun conditionConsensus(values: List<WeatherCondition>): WeatherCondition? {
        if (values.isEmpty()) return null
        // Sans identifiants modèles on ne peut pas appliquer la parenté ; ce helper
        // ne sert qu'aux anciens tests. Le chemin de production ci-dessus est familial.
        val counts = values.groupingBy { it }.eachCount()
        val best = counts.values.maxOrNull() ?: return null
        return counts.filterValues { it == best }.keys.maxByOrNull { it.severityRank }
    }
}
