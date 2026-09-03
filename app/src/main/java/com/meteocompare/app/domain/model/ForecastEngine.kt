package com.meteocompare.app.domain.model

/**
 * Moteur utilisé pour construire la prévision centrale MeteoCompare.
 *
 * La convergence reste volontairement calculée à partir des sorties brutes des
 * modèles, indépendamment de cette sélection. Le moteur ne remplace ni ne
 * modifie les séries sources : il construit uniquement une centrale dérivée.
 */
enum class ForecastEngine {
    MULTI_CONSENSUS,
    CALIBRATION,
    SCENARIOS,
    ADAPTIVE;

    companion object {
        val DEFAULT: ForecastEngine = MULTI_CONSENSUS

        fun fromString(raw: String?): ForecastEngine =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

/** Variables pour lesquelles un profil de calibration locale peut exister. */
enum class ForecastEngineVariable {
    TEMPERATURE,
    PRECIPITATION,
    WIND,
    CLOUD
}

/**
 * Snapshot de fiabilité locale consommé par le moteur V3.
 *
 * Il provient du suivi séparé par échéance J+1…J+7. Les champs sont
 * descriptifs : les intervalles produits par V3 sont des intervalles de
 * dispersion, pas des intervalles probabilistes calibrés.
 */
data class ForecastCalibrationProfile(
    val bias: Double,
    val score: Int,
    val standardDeviation: Double,
    val meanAbsoluteError: Double,
    val sampleSize: Int,
    val observedWetDays: Int? = null,
    val forecastWetDays: Int? = null,
    /** Échéance exacte du profil. Une ancienne valeur sans lead est J+1. */
    val leadDay: Int = 1,
    /** Calibration de quantité de pluie, uniquement sur les hits humide/humide. */
    val wetHitBias: Double? = null,
    val wetHitScore: Int? = null,
    val wetHitStandardDeviation: Double? = null,
    val wetHitMeanAbsoluteError: Double? = null,
    val wetHitSampleSize: Int = 0
)

/** Contexte immuable partagé par Home, Détails, widgets et comparaison. */
data class ForecastEngineContext(
    val engine: ForecastEngine = ForecastEngine.DEFAULT,
    /** Compatibilité : profils J+1 historiques. */
    val calibrationByVariable: Map<ForecastEngineVariable, Map<WeatherModel, ForecastCalibrationProfile>> = emptyMap(),
    /** Profils strictement séparés par échéance J+1…J+7. */
    val calibrationByLeadDay: Map<ForecastEngineVariable, Map<Int, Map<WeatherModel, ForecastCalibrationProfile>>> = emptyMap(),
    val localWeightsByVariable: Map<ForecastEngineVariable, Map<WeatherModel, Double>> = emptyMap()
) {
    fun calibration(variable: ForecastEngineVariable, allowCalibration: Boolean = true): Map<WeatherModel, ForecastCalibrationProfile> =
        calibration(variable, leadDay = 1, allowCalibration = allowCalibration)

    fun calibration(
        variable: ForecastEngineVariable,
        leadDay: Int,
        allowCalibration: Boolean = true
    ): Map<WeatherModel, ForecastCalibrationProfile> {
        if (!allowCalibration || leadDay !in 1..7) return emptyMap()
        return calibrationByLeadDay[variable]?.get(leadDay)
            ?: if (leadDay == 1) calibrationByVariable[variable].orEmpty() else emptyMap()
    }

    fun localWeights(variable: ForecastEngineVariable): Map<WeatherModel, Double> =
        localWeightsByVariable[variable].orEmpty()

    /**
     * Empreinte déterministe de tout ce qui influence la calibration. Les bits
     * des doubles sont conservés : 0.0 n'est jamais confondu avec une absence.
     */
    val calibrationSignature: String
        get() = buildString {
            append(engine.name)
            val variables = ForecastEngineVariable.entries.sortedBy { it.name }
            variables.forEach { variable ->
                append('|').append(variable.name)
                val leads = calibrationByLeadDay[variable].orEmpty()
                (1..7).forEach { lead ->
                    append("|J").append(lead)
                    val profiles = leads[lead]
                        ?: if (lead == 1) calibrationByVariable[variable].orEmpty() else emptyMap()
                    profiles.entries.sortedBy { it.key.name }.forEach { (model, p) ->
                        append('|').append(model.name)
                        append(':').append(p.bias.toBits())
                        append(':').append(p.score)
                        append(':').append(p.standardDeviation.toBits())
                        append(':').append(p.meanAbsoluteError.toBits())
                        append(':').append(p.sampleSize)
                        append(':').append(p.observedWetDays ?: "null")
                        append(':').append(p.forecastWetDays ?: "null")
                        append(':').append(p.leadDay)
                        append(':').append(p.wetHitBias?.toBits() ?: "null")
                        append(':').append(p.wetHitScore ?: "null")
                        append(':').append(p.wetHitStandardDeviation?.toBits() ?: "null")
                        append(':').append(p.wetHitMeanAbsoluteError?.toBits() ?: "null")
                        append(':').append(p.wetHitSampleSize)
                    }
                }
                localWeightsByVariable[variable].orEmpty()
                    .entries.sortedBy { it.key.name }
                    .forEach { (model, weight) ->
                        append("|W:").append(model.name).append(':').append(weight.toBits())
                    }
            }
        }

    /** Empreinte à utiliser pour tout cache de prévision dérivée/calibrée. */
    val cacheSignature: String get() = calibrationSignature

    fun withEngine(value: ForecastEngine): ForecastEngineContext = copy(engine = value)

    companion object {
        val DEFAULT = ForecastEngineContext()
    }
}
