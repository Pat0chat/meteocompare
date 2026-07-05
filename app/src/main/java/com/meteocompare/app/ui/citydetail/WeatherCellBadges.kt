package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.WeatherCondition

/**
 * Décide quel badge afficher sous une icône météo dans les tableaux Jour × Modèle
 * ou Heure × Modèle.
 *
 * Règles :
 *   - Famille pluie/orage/neige → probabilité de précipitation (0-100%)
 *   - Famille nuageuse (`PARTLY_CLOUDY` / `OVERCAST`) → couverture nuageuse (0-100%)
 *   - Autres familles (clair, brouillard, unknown) → pas de badge
 *
 * Retourne également null si la valeur nécessaire n'est pas fournie par le
 * modèle (un modèle peut fournir la condition sans les extras — cache
 * pré-feature). On ne bricole PAS de fallback ("~ 50%") : mieux vaut ne rien
 * montrer qu'un chiffre inventé qui donnerait l'illusion d'une donnée réelle.
 *
 * Source unique de vérité — factorisée depuis [WeatherByModelTable] (daily)
 * et [HourlyWeatherByModelTable] (hourly) qui portaient chacune une copie
 * quasi-identique. Un ajout de famille (ex : brume légère avec % de visibilité)
 * se fait maintenant à un seul endroit.
 *
 * @param condition famille météo à afficher.
 * @param precipProbability probabilité de pluie (0-100) — max journalier pour
 *   la vue daily, valeur horaire pour la vue hourly. Null si non fournie.
 * @param cloudCover couverture nuageuse (0-100) — moyenne journalière pour
 *   la vue daily, valeur horaire pour la vue hourly. Null si non fournie.
 * @return badge formaté "60%" ou null si aucun badge n'est justifié.
 */
internal fun weatherBadgeFor(
    condition: WeatherCondition,
    precipProbability: Int?,
    cloudCover: Int?
): String? = when (condition) {
    WeatherCondition.RAIN,
    WeatherCondition.DRIZZLE,
    WeatherCondition.RAIN_SHOWERS,
    WeatherCondition.THUNDERSTORM,
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.SNOW,
    WeatherCondition.SNOW_SHOWERS ->
        precipProbability?.let { "$it%" }
    WeatherCondition.PARTLY_CLOUDY,
    WeatherCondition.OVERCAST ->
        cloudCover?.let { "$it%" }
    else -> null
}
