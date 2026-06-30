package com.meteocompare.app.domain.model

/**
 * Famille sémantique de temps observée/prévue.
 *
 * Open-Meteo expose les codes WMO 4677 (`weather_code`), une liste de ~30 valeurs
 * granulaires (0=clair, 51=bruine légère, 53=bruine modérée, 55=bruine forte…).
 * Pour un affichage iconique, ce niveau de détail est trop fin : on n'a ni
 * l'icônographie ni la place pour distinguer 3 nuances de bruine. On collapse
 * en familles qui correspondent chacune à une icône unique.
 *
 * Le mapping est volontairement conservateur :
 *   - 56/57 (verglas léger/fort) → FREEZING_RAIN, distinct de RAIN (icône
 *     différente possible — risque d'usage opérationnel : verglas = info
 *     critique pour la route)
 *   - 66/67 (pluie verglaçante) idem
 *   - 95/96/99 (orage avec ou sans grêle) → tous THUNDERSTORM (la grêle reste
 *     trop rare et trop locale pour mériter sa propre icône au niveau MVP)
 *
 * Voir https://open-meteo.com/en/docs (section "Weather variable documentation").
 */
enum class WeatherCondition {
    CLEAR,
    MAINLY_CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    FREEZING_RAIN,
    SNOW,
    RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    UNKNOWN;

    companion object {
        /** Renvoie la famille de temps pour un code WMO. `null` → pas de donnée. */
        fun fromWmoCode(code: Int?): WeatherCondition? = when (code) {
            null -> null
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_RAIN
            61, 63, 65 -> RAIN
            66, 67 -> FREEZING_RAIN
            71, 73, 75, 77 -> SNOW
            80, 81, 82 -> RAIN_SHOWERS
            85, 86 -> SNOW_SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}
