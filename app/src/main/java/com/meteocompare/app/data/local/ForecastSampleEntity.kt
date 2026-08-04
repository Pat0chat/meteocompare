package com.meteocompare.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Une prévision Previous Runs J+1 = un couple `(cityId, modelKey, variable)`
 * prévoyant une valeur pour un `targetDate`, enregistré pour une journée
 * locale d'émission représentée par `issuedAtEpochMs`.
 *
 * ## Clé primaire
 *
 * Composite sur les 5 champs métier. Le suivi actuel ne conserve que J+1 :
 * [com.meteocompare.app.domain.usecase.BootstrapBiasHistoryUseCase] écrit les
 * valeurs Previous Runs à échéance fixe J+1 et normalise `issuedAtEpochMs` au
 * début de la veille locale. Les rechargements quotidiens sont idempotents.
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
    /** Début UTC de la veille locale correspondant à l’échéance fixe J+1. */
    val issuedAtEpochMs: Long,
    /** Valeur prévue dans l'unité naturelle de la variable (°C, mm, km/h). */
    val value: Double
)
