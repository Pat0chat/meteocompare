package com.meteocompare.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Une prévision historique = un couple `(cityId, modelKey, variable)` prévoyant
 * une valeur pour un `targetDate`, snapshottée à un `issuedAtEpochMs` donné.
 *
 * ## Clé primaire
 *
 * Composite sur les 5 champs métier : deux snapshots successifs du même
 * modèle pour la même date (typique — une prévision issue le J−2 et une
 * autre issue le J−1 pour la même journée J) sont conservés côte à côte.
 * Le use case dédupliquera au moment du calcul en gardant le plus récent
 * (ordre `issuedAtEpochMs DESC` dans la requête).
 *
 * On garde plusieurs snapshots plutôt que d'écraser :
 *   1. Debug / audit : voir la trajectoire des prévisions successives d'un
 *      modèle avant le jour J est utile en Phase 3+ pour analyser sa dérive.
 *   2. Robustesse à un race : deux workers concurrent qui recordent la même
 *      cible ne s'écrasent pas mutuellement.
 *
 * ## Index
 *
 * Index sur `targetDateEpochDay` seul pour accélérer les purges quotidiennes
 * (`DELETE ... WHERE targetDateEpochDay < ?`), et sur `(cityId, modelKey,
 * variable)` pour le JOIN avec les observations lors de `observeSamples`.
 * Le composite PK sert de fait d'index pour les insertions.
 *
 * ## Storage
 *
 * `targetDateEpochDay` = LocalDate.toEpochDay() — Long comparable
 * naturellement en SQL, pas besoin de type converter. Même pattern que
 * `computedAt` dans ClimateNormalEntity.
 */
@Entity(
    tableName = "forecast_samples",
    primaryKeys = ["cityId", "modelKey", "variable", "targetDateEpochDay", "issuedAtEpochMs"],
    indices = [
        Index(value = ["targetDateEpochDay"]),
        Index(value = ["cityId", "modelKey", "variable"])
    ]
)
data class ForecastSampleEntity(
    val cityId: String,
    /** `WeatherModel.name` (identifiant enum) — String pour tolérer un renommage progressif. */
    val modelKey: String,
    /** `BiasVariable.name` — même logique. */
    val variable: String,
    /** LocalDate.toEpochDay() du jour prévu. */
    val targetDateEpochDay: Long,
    /** Instant.toEpochMilli() du moment où la prévision a été enregistrée. */
    val issuedAtEpochMs: Long,
    /** Valeur prévue dans l'unité naturelle de la variable (°C, mm, km/h). */
    val value: Double
)
