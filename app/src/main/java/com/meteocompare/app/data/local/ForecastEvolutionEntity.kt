package com.meteocompare.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Snapshot local d'une valeur journalière enregistrée lors d'un refresh frais de MeteoCompare.
 *
 * Contrairement à Previous Runs, cette table ne reconstruit pas un lead-time :
 * elle mémorise la sortie du Forecast API au moment où l'application l'a
 * effectivement rafraîchie. [snapshotBucket] déduplique les refreshs proches
 * sans perdre la chronologie nécessaire aux comparaisons ~24/48/72 h.
 */
@Entity(
    tableName = "forecast_evolution_samples",
    primaryKeys = [
        "cityId",
        "modelKey",
        "variable",
        "targetDateEpochDay",
        "snapshotBucket"
    ],
    indices = [
        Index(value = ["cityId", "snapshotBucket"]),
        Index(value = ["snapshotAtEpochMs"]),
        Index(value = ["cityId", "targetDateEpochDay"])
    ]
)
data class ForecastEvolutionEntity(
    val cityId: String,
    val modelKey: String,
    val variable: String,
    val targetDateEpochDay: Long,
    val snapshotBucket: Long,
    val snapshotAtEpochMs: Long,
    val value: Double,
    /** Source Open-Meteo exacte, utile pour séparer les migrations de modèle. */
    val sourceApiKey: String? = null,
    val resolutionKm: Double? = null
)
