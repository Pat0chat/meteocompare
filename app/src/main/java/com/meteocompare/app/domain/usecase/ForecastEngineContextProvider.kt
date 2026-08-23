package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastCalibrationProfile
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.ModelReliabilityCalculator
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transforme les historiques J+1 existants en profils V3.
 *
 * Le provider ne fetch rien sur le réseau : il lit uniquement Room. Il est
 * donc utilisable par la Home et les widgets sans créer une seconde chaîne de
 * collecte. La calibration n'est ensuite autorisée que sur les agrégats
 * journaliers ; les consommateurs horaires passent explicitement
 * allowCalibration=false.
 */
@Singleton
class ForecastEngineContextProvider @Inject constructor(
    private val biasSamples: BiasSampleRepository
) {
    suspend fun build(
        forecast: CityForecast,
        engine: ForecastEngine,
        now: Instant
    ): ForecastEngineContext = coroutineScope {
        if (engine == ForecastEngine.MULTI_CONSENSUS || engine == ForecastEngine.SCENARIOS) {
            return@coroutineScope ForecastEngineContext(engine = engine)
        }

        val asOf = now.localDateIn(forecast.city.timezone)
        val models = forecast.seriesByModel.keys.toList()
        val variablePairs = listOf(
            ForecastEngineVariable.TEMPERATURE to BiasVariable.TEMPERATURE,
            ForecastEngineVariable.PRECIPITATION to BiasVariable.PRECIPITATION,
            ForecastEngineVariable.WIND to BiasVariable.WIND_SPEED
        )
        val profiles = variablePairs.associate { (engineVariable, biasVariable) ->
            val byModel = models.map { model ->
                async {
                    val samples = biasSamples.observeSamples(
                        cityId = forecast.city.id,
                        model = model,
                        variable = biasVariable,
                        asOf = asOf,
                        timezone = forecast.city.timezone,
                        windowDays = ForecastEngineV3.FULL_CALIBRATION_SAMPLES
                    ).first()
                    val reliability = ModelReliabilityCalculator.compute(
                        variable = biasVariable,
                        samples = samples,
                        windowDays = ForecastEngineV3.FULL_CALIBRATION_SAMPLES
                    )
                    model to reliability?.let {
                        ForecastCalibrationProfile(
                            bias = it.meanBias,
                            score = it.score,
                            standardDeviation = it.standardDeviation,
                            meanAbsoluteError = it.meanAbsoluteError,
                            sampleSize = it.sampleSize,
                            observedWetDays = it.precipitation?.observedWetDays,
                            forecastWetDays = it.precipitation?.forecastWetDays
                        )
                    }
                }
            }.map { it.await() }.mapNotNull { (model, profile) -> profile?.let { model to it } }.toMap()
            engineVariable to byModel
        }
        ForecastEngineContext(engine = engine, calibrationByVariable = profiles)
    }
}
