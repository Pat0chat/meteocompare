package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Réponse de `/v1/forecast?models=<single>`.
 *
 * En appelant l'API avec UN seul modèle dans `&models=`, les champs ne sont
 * PAS suffixés par le nom du modèle (ex: `temperature_2m` plutôt que
 * `temperature_2m_icon_seamless`). C'est ce qui permet ce DTO simple.
 */
@Serializable
data class ForecastResponseDto(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerialName("timezone_abbreviation")
    val timezoneAbbreviation: String? = null,
    val elevation: Double? = null,
    val hourly: HourlyDto? = null,
    val daily: DailyDto? = null
)

@Serializable
data class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m")
    val temperature2m: List<Double?>? = null,
    val precipitation: List<Double?>? = null,
    @SerialName("wind_speed_10m")
    val windSpeed10m: List<Double?>? = null,
    // WMO weather codes (0=clear, 3=overcast, 61=rain, 95=thunderstorm…)
    // Nullable + `= null` default : si une vieille entrée de cache JSON ne
    // contient pas ce champ (cache écrit avant l'ajout de la feature), kotlinx
    // remet null sans crasher, et le mapper renvoie une liste vide → l'UI ne
    // tente pas d'afficher d'icône. Pas besoin d'invalider le cache existant.
    @SerialName("weather_code")
    val weatherCode: List<Int?>? = null
)

@Serializable
data class DailyDto(
    val time: List<String>,
    @SerialName("temperature_2m_max")
    val temperature2mMax: List<Double?>? = null,
    @SerialName("temperature_2m_min")
    val temperature2mMin: List<Double?>? = null,
    @SerialName("precipitation_sum")
    val precipitationSum: List<Double?>? = null,
    @SerialName("wind_speed_10m_max")
    val windSpeed10mMax: List<Double?>? = null,
    @SerialName("weather_code")
    val weatherCode: List<Int?>? = null
)
