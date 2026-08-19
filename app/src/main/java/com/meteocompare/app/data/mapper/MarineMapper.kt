package com.meteocompare.app.data.mapper

import com.meteocompare.app.data.remote.dto.MarineResponseDto
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.MarineDaily
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.MarineGrid
import com.meteocompare.app.domain.model.MarineHourly
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun MarineResponseDto.toDomain(
    city: City,
    fetchedAtEpochMs: Long = System.currentTimeMillis()
): MarineForecast {
    val raw = hourly
    val timestamps = raw?.time.orEmpty()
    val timezoneId = sequenceOf(timezone, city.timezone)
        .filterNotNull()
        .firstOrNull { candidate -> runCatching { ZoneId.of(candidate) }.isSuccess }
        ?: "UTC"
    val zone = ZoneId.of(timezoneId)
    val epochs = timestamps.map { value ->
        runCatching { LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }
    val hourlyDomain = MarineHourly(
        timestamps = timestamps,
        timestampEpochMs = epochs,
        waveHeight = raw?.waveHeight.orEmpty(),
        waveDirection = raw?.waveDirection.orEmpty(),
        wavePeriod = raw?.wavePeriod.orEmpty(),
        swellHeight = raw?.swellHeight.orEmpty(),
        swellDirection = raw?.swellDirection.orEmpty(),
        swellPeriod = raw?.swellPeriod.orEmpty(),
        seaSurfaceTemperature = raw?.seaSurfaceTemperature.orEmpty(),
        seaLevelHeightMsl = raw?.seaLevelHeightMsl.orEmpty()
    )
    val gridLat = latitude
    val gridLon = longitude
    val distanceKm = if (gridLat != null && gridLon != null) {
        haversineKm(city.latitude, city.longitude, gridLat, gridLon)
    } else null
    val usable = hourlyDomain.waveHeight.count { it != null && it.isFinite() }
    return MarineForecast(
        fetchedAtEpochMs = fetchedAtEpochMs,
        timezone = timezoneId,
        grid = MarineGrid(gridLat, gridLon, distanceKm),
        hourly = hourlyDomain,
        daily = deriveDaily(hourlyDomain),
        usablePoints = usable,
        coastal = distanceKm != null &&
            distanceKm <= MarineRepository.COASTAL_MAX_DISTANCE_KM &&
            usable >= 6
    )
}

private fun deriveDaily(hourly: MarineHourly): MarineDaily {
    val groups = linkedMapOf<String, MutableList<Int>>()
    hourly.timestamps.forEachIndexed { index, timestamp ->
        val day = timestamp.take(10)
        if (day.isNotBlank()) groups.getOrPut(day) { mutableListOf() }.add(index)
    }
    val dates = mutableListOf<String>()
    val waveHeightMax = mutableListOf<Double?>()
    val waveDirectionDominant = mutableListOf<Double?>()
    val wavePeriodMax = mutableListOf<Double?>()
    val swellHeightMax = mutableListOf<Double?>()
    val swellDirectionDominant = mutableListOf<Double?>()
    val swellPeriodMax = mutableListOf<Double?>()

    groups.forEach { (day, indices) ->
        dates += day
        waveHeightMax += maxAt(indices, hourly.waveHeight)
        waveDirectionDominant += circularMean(indices, hourly.waveDirection, hourly.waveHeight)
        wavePeriodMax += maxAt(indices, hourly.wavePeriod)
        swellHeightMax += maxAt(indices, hourly.swellHeight)
        swellDirectionDominant += circularMean(indices, hourly.swellDirection, hourly.swellHeight)
        swellPeriodMax += maxAt(indices, hourly.swellPeriod)
    }
    return MarineDaily(
        dates = dates,
        waveHeightMax = waveHeightMax,
        waveDirectionDominant = waveDirectionDominant,
        wavePeriodMax = wavePeriodMax,
        swellHeightMax = swellHeightMax,
        swellDirectionDominant = swellDirectionDominant,
        swellPeriodMax = swellPeriodMax
    )
}

private fun maxAt(indices: List<Int>, values: List<Double?>): Double? =
    indices.mapNotNull { values.getOrNull(it) }.filter { it.isFinite() }.maxOrNull()

private fun circularMean(
    indices: List<Int>,
    directions: List<Double?>,
    weights: List<Double?>
): Double? {
    var x = 0.0
    var y = 0.0
    var total = 0.0
    for (i in indices) {
        val direction = directions.getOrNull(i) ?: continue
        if (!direction.isFinite()) continue
        val weight = weights.getOrNull(i)?.takeIf { it.isFinite() && it > 0 } ?: 1.0
        val radians = Math.toRadians(direction)
        x += cos(radians) * weight
        y += sin(radians) * weight
        total += weight
    }
    if (total == 0.0 || (x == 0.0 && y == 0.0)) return null
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

private fun haversineKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val radiusKm = 6371.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val q = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * radiusKm * asin(sqrt(q.coerceIn(0.0, 1.0)))
}
