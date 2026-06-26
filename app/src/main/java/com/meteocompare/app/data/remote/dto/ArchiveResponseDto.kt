package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Réponse de archive-api.open-meteo.com/v1/archive.
 *
 * Format identique au forecast en daily : un tableau aligné par index entre
 * `time` et chaque variable. Les valeurs nulles existent pour les jours
 * manquants (rare en zone tempérée, plus fréquent dans les hautes latitudes).
 */
@Serializable
data class ArchiveResponseDto(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val daily: ArchiveDailyDto
)

@Serializable
data class ArchiveDailyDto(
    /** Dates au format ISO yyyy-MM-dd */
    val time: List<String>,
    @SerialName("temperature_2m_max")
    val tempMax: List<Double?>,
    @SerialName("temperature_2m_min")
    val tempMin: List<Double?>
)
