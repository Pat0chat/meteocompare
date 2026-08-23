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
 * Il provient du suivi J+1 déjà existant dans l'application. Les champs sont
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
    val forecastWetDays: Int? = null
)

/** Contexte immuable partagé par Home, Détails, widgets et comparaison. */
data class ForecastEngineContext(
    val engine: ForecastEngine = ForecastEngine.DEFAULT,
    val calibrationByVariable: Map<ForecastEngineVariable, Map<WeatherModel, ForecastCalibrationProfile>> = emptyMap(),
    val localWeightsByVariable: Map<ForecastEngineVariable, Map<WeatherModel, Double>> = emptyMap()
) {
    fun calibration(variable: ForecastEngineVariable, allowCalibration: Boolean = true): Map<WeatherModel, ForecastCalibrationProfile> =
        if (allowCalibration) calibrationByVariable[variable].orEmpty() else emptyMap()

    fun localWeights(variable: ForecastEngineVariable): Map<WeatherModel, Double> =
        localWeightsByVariable[variable].orEmpty()

    fun withEngine(value: ForecastEngine): ForecastEngineContext = copy(engine = value)

    companion object {
        val DEFAULT = ForecastEngineContext()
    }
}
