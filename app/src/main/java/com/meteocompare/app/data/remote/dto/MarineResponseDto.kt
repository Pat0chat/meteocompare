package com.meteocompare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MarineResponseDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val hourly: MarineHourlyDto? = null
)

@Serializable
data class MarineHourlyDto(
    val time: List<String>? = null,
    @SerialName("wave_height") val waveHeight: List<Double?>? = null,
    @SerialName("wave_direction") val waveDirection: List<Double?>? = null,
    @SerialName("wave_period") val wavePeriod: List<Double?>? = null,
    @SerialName("swell_wave_height") val swellHeight: List<Double?>? = null,
    @SerialName("swell_wave_direction") val swellDirection: List<Double?>? = null,
    @SerialName("swell_wave_period") val swellPeriod: List<Double?>? = null,
    @SerialName("sea_surface_temperature") val seaSurfaceTemperature: List<Double?>? = null,
    @SerialName("sea_level_height_msl") val seaLevelHeightMsl: List<Double?>? = null
)
