package com.meteocompare.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MarineForecast(
    val fetchedAtEpochMs: Long,
    val timezone: String,
    val grid: MarineGrid,
    val hourly: MarineHourly,
    val daily: MarineDaily,
    val usablePoints: Int,
    val coastal: Boolean
)

@Serializable
data class MarineGrid(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val distanceKm: Double? = null
)

@Serializable
data class MarineHourly(
    val timestamps: List<String> = emptyList(),
    val timestampEpochMs: List<Long?> = emptyList(),
    val waveHeight: List<Double?> = emptyList(),
    val waveDirection: List<Double?> = emptyList(),
    val wavePeriod: List<Double?> = emptyList(),
    val swellHeight: List<Double?> = emptyList(),
    val swellDirection: List<Double?> = emptyList(),
    val swellPeriod: List<Double?> = emptyList(),
    val seaSurfaceTemperature: List<Double?> = emptyList(),
    val seaLevelHeightMsl: List<Double?> = emptyList()
)

@Serializable
data class MarineDaily(
    val dates: List<String> = emptyList(),
    val waveHeightMax: List<Double?> = emptyList(),
    val waveDirectionDominant: List<Double?> = emptyList(),
    val wavePeriodMax: List<Double?> = emptyList(),
    val swellHeightMax: List<Double?> = emptyList(),
    val swellDirectionDominant: List<Double?> = emptyList(),
    val swellPeriodMax: List<Double?> = emptyList()
)

enum class TideEventType { HIGH, LOW }

data class TideEvent(
    val timestamp: String,
    val epochMs: Long,
    val value: Double,
    val type: TideEventType
)

data class TideRange(
    val min: Double,
    val max: Double,
    val range: Double
)
