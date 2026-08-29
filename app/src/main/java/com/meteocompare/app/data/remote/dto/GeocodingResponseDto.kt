package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Réponse de `https://geocoding-api.open-meteo.com/v1/search`.
 */
@Serializable
data class GeocodingResponseDto(
    val results: List<GeocodingResultDto>? = null
)

@Serializable
data class GeocodingResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val postcodes: List<String> = emptyList(),
    val timezone: String? = null
)
