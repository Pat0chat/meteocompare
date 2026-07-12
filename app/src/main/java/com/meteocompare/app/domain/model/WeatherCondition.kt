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

        /**
         * Fallback empirique quand un modèle ne fournit pas `weather_code`.
         *
         * Cas d'usage principal : **AROME HD**. La documentation Open-Meteo
         * explique noir sur blanc que "AROME France HD has the same model area,
         * but at higher resolution with a smaller selection of weather variables"
         * — `weather_code` fait partie des variables non exposées, sacrifiées
         * au profit de la résolution 1.5 km. Sans ce fallback, la colonne AROME
         * HD dans le tableau Jour × Modèle est entièrement vide, ce que les
         * utilisateurs interprètent (à raison) comme un bug.
         *
         * Règles :
         *   - Précip >= 5 mm → RAIN (SNOW si temp min <= 0°C)
         *   - Précip >= 1 mm → RAIN_SHOWERS (ou SNOW_SHOWERS)
         *   - Précip >= 0.1 mm → DRIZZLE (ou SNOW_SHOWERS si gel)
         *   - Précip == 0 → null (impossible de distinguer clair vs couvert
         *     sans donnée de couverture nuageuse, qu'AROME HD n'expose pas non
         *     plus). Le tableau affiche "—" dans ce cas.
         *
         * Trade-off assumé : on privilégie l'HONNÊTETÉ sur la complétude —
         * mieux vaut ne rien afficher qu'inventer "il fait beau" faute de
         * donnée. Sur les jours secs, les autres modèles fournissent l'info.
         * Sur les jours pluvieux (les plus importants à surfacer), le fallback
         * fait le boulot.
         *
         * @param precipMm cumul de précipitations sur la fenêtre (mm)
         * @param tempMinC température minimale sur la fenêtre (°C), pour
         *   distinguer pluie/neige. Si null, on suppose > 0°C.
         */
        fun inferFromPrecipAndTemp(precipMm: Double?, tempMinC: Double?): WeatherCondition? {
            if (precipMm == null) return null
            val freezing = (tempMinC ?: 10.0) <= 0.0
            return when {
                precipMm >= 5.0 -> if (freezing) SNOW else RAIN
                precipMm >= 1.0 -> if (freezing) SNOW_SHOWERS else RAIN_SHOWERS
                precipMm >= 0.1 -> if (freezing) SNOW_SHOWERS else DRIZZLE
                else -> null
            }
        }

        /**
         * Fallback ULTIME : dérive une famille "ciel non pluvieux" depuis un
         * pourcentage de couverture nuageuse.
         *
         * Utilisé UNIQUEMENT en dernier recours quand un modèle n'expose ni
         * weather_code ni précipitation exploitable, à partir de la MÉDIANE
         * des cloud_cover des modèles PEERS à ce (jour, lieu). Cas typique :
         * AROME HD sur un jour sec — sa colonne restait "—" faute de moyen
         * de dériver l'état du ciel. Voir
         * [com.meteocompare.app.domain.usecase.ConfidenceCalculator.dailyConditionsByModel].
         *
         * ## Signalement à l'utilisateur
         *
         * Le caller DOIT signaler visuellement à l'utilisateur qu'une condition
         * a été inférée depuis les peers (pas la prédiction propre du modèle) —
         * typiquement via `Modifier.alpha(0.55f)` sur la cellule. Sans ce
         * marqueur on trahirait la philosophie "on annote, on ne modifie
         * jamais la donnée brute".
         *
         * ## Ne renvoie QUE 4 valeurs
         *
         * Les 4 familles "ciel non pluvieux" mappées sur les codes WMO 0-3 :
         *   - < 6.25%   → CLEAR         (WMO 0 : 0/8 du ciel couvert)
         *   - < 31.25%  → MAINLY_CLEAR  (WMO 1 : 1/8 à 2/8)
         *   - < 81.25%  → PARTLY_CLOUDY (WMO 2 : 3/8 à 6/8)
         *   - sinon     → OVERCAST      (WMO 3 : 7/8 à 8/8)
         *
         * Seuils positionnés aux MIDPOINTS entre les octas WMO (6.25 = 1/16,
         * milieu entre 0/8 et 2/8), pas au bord des intervalles — un ciel à
         * 30% est plus proche de "clair majoritaire" (WMO 1) que de
         * "partiellement nuageux" (WMO 2 débute à 37.5%).
         *
         * ## Pas de RAIN / FOG / THUNDERSTORM
         *
         * Ces conditions demandent d'autres variables (précipitation,
         * humidité, potentiel convectif) qu'on ne sait pas dériver de la
         * seule couverture nuageuse. Utiliser cette méthode pour un cas
         * non-pluvieux uniquement (le caller garantit ça — précip a déjà
         * été essayée avant).
         */
        fun fromCloudCover(cloudCoverPct: Double): WeatherCondition = when {
            cloudCoverPct < 6.25  -> CLEAR
            cloudCoverPct < 31.25 -> MAINLY_CLEAR
            cloudCoverPct < 81.25 -> PARTLY_CLOUDY
            else                  -> OVERCAST
        }
    }
}
