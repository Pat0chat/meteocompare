package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastCalibrationProfile
import com.meteocompare.app.domain.model.ForecastEngine
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.ModelReliabilityCalculator
import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construit les profils de calibration V3 séparément pour J+1…J+7.
 *
 * Le provider ne fetch rien sur le réseau. Il lit Room, calcule une empreinte
 * exacte de l'historique (les vrais zéros participent à l'empreinte), puis
 * mémorise le profil par historique + jour civil. Une correction J+1 n'est
 * donc jamais réutilisée à J+2…J+7.
 */
@Singleton
class ForecastEngineContextProvider @Inject constructor(
    private val biasSamples: BiasSampleRepository
) {
    private data class ProfileCacheKey(
        val cityId: String,
        val model: WeatherModel,
        val variable: BiasVariable,
        val leadDay: Int,
        val asOf: LocalDate,
        val historySignature: Long
    )

    private val profileCache = object : LinkedHashMap<ProfileCacheKey, ForecastCalibrationProfile?>(128, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ProfileCacheKey, ForecastCalibrationProfile?>?
        ): Boolean = size > MAX_PROFILE_CACHE_ENTRIES
    }

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

        val byLead = variablePairs.associate { (engineVariable, biasVariable) ->
            val leads = (MIN_LEAD_DAY..MAX_LEAD_DAY).associateWith { leadDay ->
                models.map { model ->
                    async {
                        val samples = biasSamples.observeSamples(
                            cityId = forecast.city.id,
                            model = model,
                            variable = biasVariable,
                            asOf = asOf,
                            timezone = forecast.city.timezone,
                            windowDays = ForecastEngineV3.FULL_CALIBRATION_SAMPLES,
                            leadDay = leadDay
                        ).first()
                        model to cachedProfile(
                            cityId = forecast.city.id,
                            model = model,
                            variable = biasVariable,
                            leadDay = leadDay,
                            asOf = asOf,
                            samples = samples
                        )
                    }
                }.map { it.await() }
                    .mapNotNull { (model, profile) -> profile?.let { model to it } }
                    .toMap()
            }
            engineVariable to leads
        }

        ForecastEngineContext(
            engine = engine,
            calibrationByVariable = byLead.mapValues { (_, leads) -> leads[1].orEmpty() },
            calibrationByLeadDay = byLead
        )
    }

    private fun cachedProfile(
        cityId: String,
        model: WeatherModel,
        variable: BiasVariable,
        leadDay: Int,
        asOf: LocalDate,
        samples: List<BiasSample>
    ): ForecastCalibrationProfile? {
        val key = ProfileCacheKey(
            cityId = cityId,
            model = model,
            variable = variable,
            leadDay = leadDay,
            asOf = asOf,
            historySignature = historySignature(samples)
        )
        synchronized(profileCache) {
            if (profileCache.containsKey(key)) return profileCache[key]
        }
        val computed = buildProfile(variable, leadDay, samples)
        synchronized(profileCache) { profileCache[key] = computed }
        return computed
    }

    private fun buildProfile(
        variable: BiasVariable,
        leadDay: Int,
        samples: List<BiasSample>
    ): ForecastCalibrationProfile? {
        val reliability = ModelReliabilityCalculator.compute(
            variable = variable,
            samples = samples,
            windowDays = ForecastEngineV3.FULL_CALIBRATION_SAMPLES
        ) ?: return null

        val wetHitReliability = if (variable == BiasVariable.PRECIPITATION) {
            val threshold = PrecipitationThresholds.DAILY_OCCURRENCE_MM
            val hits = samples.filter { sample ->
                sample.forecast > threshold && sample.observation > threshold
            }
            ModelReliabilityCalculator.compute(
                variable = BiasVariable.PRECIPITATION,
                samples = hits,
                windowDays = ForecastEngineV3.FULL_CALIBRATION_SAMPLES
            )
        } else {
            null
        }
        val wetHitCount = if (variable == BiasVariable.PRECIPITATION) {
            val threshold = PrecipitationThresholds.DAILY_OCCURRENCE_MM
            samples.count { it.forecast > threshold && it.observation > threshold }
        } else 0

        return ForecastCalibrationProfile(
            bias = reliability.meanBias,
            score = reliability.score,
            standardDeviation = reliability.standardDeviation,
            meanAbsoluteError = reliability.meanAbsoluteError,
            sampleSize = reliability.sampleSize,
            observedWetDays = reliability.precipitation?.observedWetDays,
            forecastWetDays = reliability.precipitation?.forecastWetDays,
            leadDay = leadDay,
            wetHitBias = wetHitReliability?.meanBias,
            wetHitScore = wetHitReliability?.score,
            wetHitStandardDeviation = wetHitReliability?.standardDeviation,
            wetHitMeanAbsoluteError = wetHitReliability?.meanAbsoluteError,
            wetHitSampleSize = wetHitCount
        )
    }

    /** FNV-1a 64-bit stable, incluant explicitement toutes les valeurs 0. */
    private fun historySignature(samples: List<BiasSample>): Long {
        var hash = -0x340d631b7bdddcdbL
        fun mix(value: Long) {
            hash = hash xor value
            hash *= 0x100000001b3L
        }
        samples.sortedWith(compareBy<BiasSample> { it.targetDate }.thenBy { it.issuedAt }).forEach { sample ->
            mix(sample.targetDate.toEpochDay())
            mix(sample.forecast.toBits())
            mix(sample.observation.toBits())
            mix(sample.issuedAt?.toEpochMilli() ?: Long.MIN_VALUE)
            mix(sample.leadDay.toLong())
        }
        return hash
    }

    companion object {
        const val MIN_LEAD_DAY = 1
        const val MAX_LEAD_DAY = 7
        private const val MAX_PROFILE_CACHE_ENTRIES = 1024
    }
}
