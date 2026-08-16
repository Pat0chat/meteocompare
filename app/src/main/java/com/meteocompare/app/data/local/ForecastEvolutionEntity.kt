package com.meteocompare.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Snapshot quotidien d'un ancien run utilisé par la carte « Évolution de la prévision ».
 * Les données sont entièrement reconstructibles depuis Open-Meteo Previous Runs.
 */
@Entity(
    tableName = "forecast_evolution_samples",
    primaryKeys = ["cityId", "modelKey", "variable", "targetDateEpochDay", "daysAgo"],
    indices = [
        Index(value = ["cityId", "targetDateEpochDay"]),
        Index(value = ["fetchedAtEpochMs"])
    ]
)
data class ForecastEvolutionEntity(
    val cityId: String,
    val modelKey: String,
    val variable: String,
    val targetDateEpochDay: Long,
    val daysAgo: Int,
    val value: Double,
    val fetchedAtEpochMs: Long
)
