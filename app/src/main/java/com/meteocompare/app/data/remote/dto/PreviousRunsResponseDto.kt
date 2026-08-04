package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Réponse brute de l'API Open-Meteo Previous Runs.
 *
 * Les variables sont dynamiques : elles incluent l'échéance fixe
 * `_previous_day1` et, en mode multi-modèles, la clé du modèle. Un [JsonObject]
 * évite donc de figer à la compilation toutes les combinaisons possibles.
 */
@Serializable
data class PreviousRunsResponseDto(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val hourly: JsonObject? = null
)
