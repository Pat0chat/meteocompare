package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Réponse de archive-api.open-meteo.com/v1/archive.
 *
 * Format identique au forecast en daily : un tableau aligné par index entre
 * `time` et chaque variable. Les valeurs nulles existent pour les jours
 * manquants (rare en zone tempérée, plus fréquent dans les hautes latitudes).
 *
 * Les séries optionnelles ne sont pas supposées présentes : les deux
 * consommateurs de cet endpoint demandent des ensembles de variables différents.
 * Chaque pipeline valide donc explicitement les champs dont il a besoin.
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
    val tempMin: List<Double?>? = null,
    /**
     * Cumul journalier de précipitations en mm. Nullable (par élément) : un
     * jour sans mesure exploitable est représenté par null, PAS par 0.0 —
     * distinction critique pour la moyenne (un null ne pénalise pas, un 0.0
     * indique explicitement "il n'a pas plu").
     *
     * Nullable au niveau liste également : si l'API archive ne retournait pas
     * la variable pour une raison exceptionnelle, `null` ici est mieux qu'un
     * crash de sérialisation.
     */
    @SerialName("precipitation_sum")
    val precipSum: List<Double?>? = null,
    /** Vitesse maximale quotidienne du vent moyen à 10 m, en km/h. */
    @SerialName("wind_speed_10m_max")
    val windSpeedMax: List<Double?>? = null
)
