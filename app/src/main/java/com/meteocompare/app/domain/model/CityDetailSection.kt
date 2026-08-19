package com.meteocompare.app.domain.model

/**
 * Sections de la fiche ville dont l'état replié est mémorisé par utilisateur.
 *
 * Toutes les cards principales de la fiche ville utilisent cette source de vérité
 * (résumé, insights, timeline, évolution, convergence/confiance, détail et marine).
 * Les anciennes valeurs restent conservées afin de relire sans perte les préférences
 * écrites par les versions précédentes de l'application.
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
    WIND,
    TODAY_SUMMARY,
    INSIGHTS,
    TIMELINE,
    DETAILED_FORECAST,
    MARINE
}
