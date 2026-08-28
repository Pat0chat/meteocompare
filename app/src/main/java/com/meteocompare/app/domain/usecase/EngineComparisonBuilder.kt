package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.util.dailyCloudCoverMean
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.max

/** Variable affichable dans la page de comparaison des moteurs. */
enum class EngineComparisonMetric { TEMP_MAX, TEMP_MIN, PRECIPITATION, WIND, GUST, CLOUD }
enum class EngineDivergenceLevel { LOW, MEDIUM, HIGH }

data class EngineComparisonValues(
    val tempMax: Double?,
    val tempMin: Double?,
    /** Quantité centrale déterministe du moteur (0 si le signal d'occurrence est insuffisant). */
    val precipitationAmountMm: Double?,
    val precipitationProbabilityPercent: Int?,
    /** Espérance P(pluie) × quantité conditionnelle, utilisée par le graphe comme sur le Web 1.16. */
    val precipitationExpectedMm: Double?,
    val windKmh: Double?,
    val gustKmh: Double?,
    val cloudPercent: Double?,
    /**
     * Condition = consensus hiérarchique des WMO natifs quand au moins deux familles
     * indépendantes en disposent ; sinon condition dérivée des variables centrales du moteur.
     * La convergence des conditions reste calculée séparément sur les sorties brutes.
     */
    val condition: WeatherCondition?
) {
    fun value(metric: EngineComparisonMetric): Double? = when (metric) {
        EngineComparisonMetric.TEMP_MAX -> tempMax
        EngineComparisonMetric.TEMP_MIN -> tempMin
        EngineComparisonMetric.PRECIPITATION -> precipitationExpectedMm
        EngineComparisonMetric.WIND -> windKmh
        EngineComparisonMetric.GUST -> gustKmh
        EngineComparisonMetric.CLOUD -> cloudPercent
    }
}

data class EngineDivergence(
    val score: Double,
    val level: EngineDivergenceLevel,
    val temperatureDelta: Double,
    val precipitationDelta: Double,
    val windDelta: Double,
    val cloudDelta: Double,
    val conditionCount: Int
)

data class EngineComparisonDay(
    val date: LocalDate,
    val byEngine: Map<ForecastEngine, EngineComparisonValues>,
    val divergence: EngineDivergence
)

/**
 * Construit les mêmes quatre scénarios à partir du même forecast brut.
 * Aucune requête réseau et aucune mutation des séries sources.
 */
class EngineComparisonBuilder @Inject constructor(
    private val confidenceCalculator: ConfidenceCalculator
) {
    fun build(
        forecast: CityForecast,
        calibrationContext: ForecastEngineContext,
        now: Instant
    ): List<EngineComparisonDay> {
        val today = now.localDateIn(forecast.city.timezone)
        val dates = forecast.seriesByModel.values
            .flatMap { it.daily.dates }
            .distinct()
            .sorted()
            .filterNot { it.isBefore(today) }
            .take(7)
        return dates.map { date ->
            val nativeConditionEntries = forecast.seriesByModel.mapNotNull { (model, series) ->
                val index = series.daily.dates.indexOf(date)
                if (index < 0) return@mapNotNull null
                WeatherCondition.fromWmoCode(series.daily.weatherCode.getOrNull(index))
                    ?.takeUnless { it == WeatherCondition.UNKNOWN }
                    ?.let { ForecastConsensus.Entry(model, it) }
            }
            val values = ForecastEngine.entries.associateWith { engine ->
                valuesFor(
                    forecast,
                    date,
                    calibrationContext.withEngine(engine),
                    nativeConditionEntries
                )
            }
            EngineComparisonDay(date, values, divergence(values.values.toList()))
        }
    }

    private fun valuesFor(
        forecast: CityForecast,
        date: LocalDate,
        context: ForecastEngineContext,
        nativeConditionEntries: List<ForecastConsensus.Entry<WeatherCondition>>
    ): EngineComparisonValues {
        val day = confidenceCalculator.dayConfidence(forecast, date, context)
        val zone = resolveZoneOrUtc(forecast.city.timezone)
        val cloudEntries = forecast.seriesByModel.mapNotNull { (model, series) ->
            series.dailyCloudCoverMean(date, zone)
                ?.toDouble()?.let { ForecastConsensus.Entry(model, it) }
        }
        val cloud = ForecastEngineV3.continuous(
            cloudEntries,
            ForecastEngineV3.ContinuousOptions(
                engine = context.engine,
                calibration = context.calibration(ForecastEngineVariable.CLOUD, allowCalibration = false),
                localWeights = context.localWeights(ForecastEngineVariable.CLOUD),
                tight = 10.0,
                wide = 50.0,
                min = 0.0,
                max = 100.0
            )
        ).central
        val precipitationMeta = day.precipitation?.meta
        // Même règle que la chronologie et le résumé courant : la provenance
        // d'une condition ne compte que les modèles ayant fourni au moins un
        // input utilisé pour cette condition (WMO, température, pluie ou
        // nébulosité). La simple présence d'une date daily ne suffit pas.
        val supportModels = buildSet {
            addAll(nativeConditionEntries.map { it.model })
            forecast.seriesByModel.forEach { (model, series) ->
                val index = series.daily.dates.indexOf(date)
                if (index < 0) return@forEach
                if (series.daily.tempMin.getOrNull(index)?.isFinite() == true ||
                    series.daily.tempMax.getOrNull(index)?.isFinite() == true ||
                    series.daily.precipitationSum.getOrNull(index)?.let { it.isFinite() && it >= 0.0 } == true ||
                    series.daily.precipitationProbabilityMax.getOrNull(index)?.let { it in 0..100 } == true ||
                    series.dailyCloudCoverMean(date, zone) != null
                ) {
                    add(model)
                }
            }
        }
        val condition = WeatherConditionConsensus.resolveAggregate(
            nativeEntries = nativeConditionEntries,
            temperatureCentralC = day.tempMin?.meanValue ?: day.tempMax?.meanValue,
            precipitationCentralMm = precipitationMeta?.centralAmountMm,
            cloudCoverPercent = cloud,
            supportModels = supportModels
        ).vote.value
        return EngineComparisonValues(
            tempMax = day.tempMax?.meanValue,
            tempMin = day.tempMin?.meanValue,
            precipitationAmountMm = precipitationMeta?.centralAmountMm,
            precipitationProbabilityPercent = precipitationMeta?.probabilityPercent,
            precipitationExpectedMm = precipitationMeta?.expectedAmountMm,
            windKmh = day.windMax?.meanValue,
            gustKmh = day.windGustMax?.meanValue,
            cloudPercent = cloud,
            condition = condition
        )
    }

    /** Même normalisation/thresholds que la frise Web 1.16. */
    private fun divergence(values: List<EngineComparisonValues>): EngineDivergence {
        fun spread(selector: (EngineComparisonValues) -> Double?): Double {
            val numbers = values.mapNotNull(selector).filter(Double::isFinite)
            return if (numbers.size < 2) 0.0 else (numbers.maxOrNull() ?: 0.0) - (numbers.minOrNull() ?: 0.0)
        }
        val temp = max(spread { it.tempMax }, spread { it.tempMin })
        val rain = spread { it.precipitationExpectedMm }
        val wind = max(spread { it.windKmh }, spread { it.gustKmh })
        val cloud = spread { it.cloudPercent }
        val conditionCount = values.mapNotNull { it.condition }.distinct().size
        val conditionSignal = if (conditionCount > 1) 0.42 else 0.0
        val score = max(max(temp / 4.0, rain / 8.0), max(max(wind / 15.0, cloud / 50.0), conditionSignal))
        val level = when {
            score >= 0.75 -> EngineDivergenceLevel.HIGH
            score >= 0.35 -> EngineDivergenceLevel.MEDIUM
            else -> EngineDivergenceLevel.LOW
        }
        return EngineDivergence(score, level, temp, rain, wind, cloud, conditionCount)
    }
}
