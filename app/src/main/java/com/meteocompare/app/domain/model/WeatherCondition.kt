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

    /**
     * Rang conservateur utilisé pour départager un vote catégoriel à égalité.
     * Une condition potentiellement plus gênante gagne sur une condition
     * bénigne afin de ne pas minimiser un signal météo partagé.
     */
    val severityRank: Int
        get() = when (this) {
            CLEAR -> 0
            MAINLY_CLEAR -> 1
            PARTLY_CLOUDY -> 2
            OVERCAST -> 3
            FOG -> 4
            DRIZZLE -> 5
            RAIN_SHOWERS -> 6
            RAIN -> 7
            SNOW_SHOWERS -> 8
            SNOW -> 9
            FREEZING_RAIN -> 10
            THUNDERSTORM -> 11
            UNKNOWN -> -1
        }

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

        /**
         * Fallback empirique lorsqu'un code WMO manque dans une réponse partielle
         * ou un ancien cache. Il n'utilise que les variables du MÊME modèle.
         *
         * Règles :
         *   - Précip >= 5 mm → RAIN (SNOW si temp <= 0°C)
         *   - Précip >= 1 mm → RAIN_SHOWERS (ou SNOW_SHOWERS)
         *   - Précip >= 0.1 mm → DRIZZLE (ou SNOW_SHOWERS)
         *   - Précip < 0.1 mm → null : pluie/température seules ne permettent
         *     pas de distinguer honnêtement ciel clair et ciel couvert.
         *
         * @param precipMm cumul de précipitations sur la fenêtre (mm)
         * @param tempMinC température représentative/minimale (°C), utilisée
         *   uniquement pour distinguer pluie/neige.
         */
        fun inferFromPrecipAndTemp(precipMm: Double?, tempMinC: Double?): WeatherCondition? {
            if (precipMm == null) return null
            val freezing = (tempMinC ?: 10.0) <= 0.0
            return when {
                precipMm >= 5.0 -> if (freezing) SNOW else RAIN
                precipMm >= 1.0 -> if (freezing) SNOW_SHOWERS else RAIN_SHOWERS
                precipMm > PrecipitationThresholds.HOURLY_OCCURRENCE_MM -> if (freezing) SNOW_SHOWERS else DRIZZLE
                else -> null
            }
        }

        /**
         * Dérive une famille de ciel non pluvieux depuis une couverture
         * nuageuse 0-100 %. Le caller doit utiliser la couverture du MÊME
         * modèle et signaler qu'il s'agit d'une interprétation, pas d'un code
         * WMO journalier fourni tel quel.
         *
         * Seuils pédagogiques volontairement conservateurs pour éviter de
         * sur-classer un ciel encore percé d'éclaircies comme « couvert » :
         * <20 CLEAR, <45 MAINLY_CLEAR, <85 PARTLY_CLOUDY, sinon OVERCAST.
         * Cette méthode ne déduit jamais pluie, brouillard ou orage de la seule
         * nébulosité.
         */
        fun fromCloudCover(cloudCoverPct: Double): WeatherCondition = when {
            cloudCoverPct < 20.0 -> CLEAR
            cloudCoverPct < 45.0 -> MAINLY_CLEAR
            cloudCoverPct < 85.0 -> PARTLY_CLOUDY
            else                 -> OVERCAST
        }
    }
}
