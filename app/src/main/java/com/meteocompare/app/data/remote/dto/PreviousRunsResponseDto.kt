package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Réponse brute de l'API Open-Meteo Previous Runs.
 *
 * Les variables sont dynamiques : elles incluent l'échéance fixe
 * `_previous_day1` et, en mode multi-modèles, la clé du modèle. Un [JsonObject]
 * évite donc de figer à la compilation toutes les combinaisons possibles.
 *
 * Comme pour la Forecast API, `latitude`/`longitude` valent `null` quand le
 * premier modèle demandé ne couvre pas le point : ils restent donc optionnels
 * pour ne pas perdre les séries des autres modèles.
 */
@Serializable
data class PreviousRunsResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String,
    val hourly: JsonObject? = null
)
