package com.meteocompare.app.data.local

import androidx.room.Entity

/**
 * Cache local des normales climatiques d'une ville.
 *
 * Clé primaire composite (cityId, month, day) : une ligne par jour-de-l'année.
 * 366 lignes par ville en théorie (avec Feb 29 si la ville est polaire et a
 * des observations significatives).
 *
 * `computedAt` est utilisé pour invalider le cache après ~6 mois (les normales
 * changent à l'échelle décennale, un refresh semestriel est très conservateur).
 */
@Entity(
    tableName = "climate_normals",
    primaryKeys = ["cityId", "month", "day"]
)
data class ClimateNormalEntity(
    val cityId: String,
    val month: Int,
    val day: Int,
    val tempMaxNormal: Double,
    val tempMinNormal: Double,
    /** Epoch millis du dernier fetch agrégé. */
    val computedAt: Long
)
