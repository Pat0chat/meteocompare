package com.meteocompare.app.domain.model

/**
 * Garde-fous physiques volontairement larges appliqués avant tout consensus.
 *
 * Le but n'est pas de déclarer une valeur météorologiquement probable mais
 * d'empêcher une valeur manifestement impossible/corrompue d'entrer dans les
 * statistiques. Les limites sont donc bien plus larges que les normales
 * climatiques usuelles.
 */
object ForecastPhysicalLimits {
    const val MIN_TEMPERATURE_C = -100.0
    const val MAX_TEMPERATURE_C = 70.0
    const val MAX_PRECIPITATION_MM = 5_000.0
    const val MAX_WIND_KMH = 500.0
    const val MAX_GUST_KMH = 600.0

    fun temperature(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in MIN_TEMPERATURE_C..MAX_TEMPERATURE_C }

    fun precipitation(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in 0.0..MAX_PRECIPITATION_MM }

    fun wind(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in 0.0..MAX_WIND_KMH }

    fun gust(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it in 0.0..MAX_GUST_KMH }

    fun percentage(value: Int?): Int? = value?.takeIf { it in 0..100 }

    fun direction(value: Int?): Int? = value?.takeIf { it in 0..360 }

    /** Codes WMO effectivement utilisés par l'API Open-Meteo. */
    private val VALID_WMO_CODES = setOf(
        0, 1, 2, 3,
        45, 48,
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        71, 73, 75, 77,
        80, 81, 82,
        85, 86,
        95, 96, 99
    )

    fun weatherCode(value: Int?): Int? = value?.takeIf(VALID_WMO_CODES::contains)

    /**
     * Une paire quotidienne incohérente est rejetée intégralement : garder
     * seulement Tmin ou Tmax ferait entrer une journée corrompue dans des
     * agrégats différents selon l'écran.
     */
    fun dailyTemperaturePair(max: Double?, min: Double?): Pair<Double?, Double?> {
        val safeMax = temperature(max)
        val safeMin = temperature(min)
        return if (safeMax != null && safeMin != null && safeMax < safeMin) {
            null to null
        } else {
            safeMax to safeMin
        }
    }
}
