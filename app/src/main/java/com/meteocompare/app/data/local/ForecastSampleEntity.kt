package com.meteocompare.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Une prévision Previous Runs J+1…J+7 = un couple `(cityId, modelKey, variable)`
 * prévoyant une valeur pour un `targetDate`, enregistré pour une journée
 * locale d'émission représentée par `issuedAtEpochMs`.
 *
 * ## Clé primaire
 *
 * Composite sur les 5 champs métier historiques. `issuedAtEpochMs` encode déjà
 * l'échéance (date cible - leadDay), tandis que [leadDay] la rend explicite pour
 * les requêtes et la rétrocompatibilité. Les anciennes lignes migrées reçoivent
 * `leadDay = 1` et restent donc strictement interprétées comme J+1.
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
    /** Début UTC de la journée locale `targetDate - leadDay`. */
    val issuedAtEpochMs: Long,
    /** Valeur prévue dans l'unité naturelle de la variable (°C, mm, km/h). */
    val value: Double,
    /** Source Open-Meteo exacte, distincte de l'identité UI [modelKey]. */
    val sourceApiKey: String? = null,
    /** Résolution native de la source lors de l'enregistrement. */
    val resolutionKm: Double? = null,
    /** Échéance réelle du Previous Runs : 1 = J+1 … 7 = J+7. */
    @ColumnInfo(defaultValue = "1")
    val leadDay: Int = 1
)
