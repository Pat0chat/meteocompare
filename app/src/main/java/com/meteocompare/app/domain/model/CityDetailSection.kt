package com.meteocompare.app.domain.model

/**
 * Sections de la fiche ville dont l'état replié est mémorisé par utilisateur.
 *
 * Les noms des valeurs sont persistés dans DataStore. Ils doivent donc rester
 * stables : en cas de renommage, prévoir une compatibilité dans le repository.
 */
enum class CityDetailSection {
    CONFIDENCE,
    LOCAL_RANKING,
    WEATHER,
    TEMPERATURE,
    PRECIPITATION,
    WIND
}
