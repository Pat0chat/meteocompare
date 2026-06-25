package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Réponse de `https://geocoding-api.open-meteo.com/v1/search`.
 */
@Serializable
data class GeocodingResponseDto(
    val results: List<GeocodingResultDto>? = null,
    val generationtime_ms: Double? = null
)

@Serializable
data class GeocodingResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val country: String? = null,
    val country_code: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val timezone: String? = null,
    val population: Long? = null
)
