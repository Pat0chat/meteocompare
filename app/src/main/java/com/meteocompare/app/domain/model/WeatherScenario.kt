package com.meteocompare.app.domain.model

/**
 * Résumé d'un groupe de modèles qui racontent un scénario météo proche sur
 * les prochaines heures. Ce n'est volontairement PAS une probabilité : les
 * modèles ne sont pas statistiquement indépendants.
 */
data class WeatherScenario(
    val kind: WeatherScenarioKind,
    val timing: WeatherScenarioTiming = WeatherScenarioTiming.NONE,
    val modelCount: Int,
    val totalModelCount: Int,
    val temperatureMinC: Double? = null,
    val temperatureMaxC: Double? = null,
    val precipitationMinMm: Double? = null,
    val precipitationMaxMm: Double? = null,
    val cloudCoverMinPercent: Int? = null,
    val cloudCoverMaxPercent: Int? = null,
    val gustMinKmh: Double? = null,
    val gustMaxKmh: Double? = null
)

enum class WeatherScenarioKind {
    CLEAR,
    VARIABLE_SKY,
    OVERCAST,
    DRY_UNSPECIFIED,
    SHOWERS,
    RAIN,
    SNOW,
    FREEZING_RAIN,
    THUNDERSTORM,
    OTHER
}

enum class WeatherScenarioTiming {
    NONE,
    EARLY,
    MIDDLE,
    LATE,
    THROUGHOUT
}
