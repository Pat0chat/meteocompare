package com.meteocompare.app.domain.model

/**
 * Sections de la fiche ville dont l'état replié est mémorisé par utilisateur.
 *
 * Depuis la fiche réorganisée, [CONFIDENCE] pilote le bloc unifié
 * « Fiabilité locale » et [FORECAST_EVOLUTION] la carte run-to-run. Les autres valeurs sont conservées pour relire sans
 * perte les préférences écrites par les versions précédentes de l'application.
 *
 * Les noms sont persistés dans DataStore et doivent donc rester stables.
 */
enum class CityDetailSection {
    CONFIDENCE,
    FORECAST_EVOLUTION,
    LOCAL_RANKING,
    WEATHER,
    TEMPERATURE,
    PRECIPITATION,
    WIND
}
