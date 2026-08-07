package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Agrégations journalières dérivées uniquement des données du modèle concerné. */
internal fun ForecastSeries.dailyCloudCoverMean(date: LocalDate, zone: ZoneId): Int? {
    if (hourly.cloudCover.isEmpty()) return null
    val values = hourly.timestamps.indices.mapNotNull { index ->
        val timestamp = hourly.timestamps.getOrNull(index) ?: return@mapNotNull null
        if (timestamp.atZone(zone).toLocalDate() != date) return@mapNotNull null
        hourly.cloudCover.getOrNull(index)?.takeIf { it in 0..100 }
    }
    return values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

/** Résultat d'une condition journalière avec provenance explicite. */
internal data class DailyConditionResolution(
    val condition: WeatherCondition,
    val inferred: Boolean
)

/**
 * Résout la condition d'un jour sans jamais emprunter une donnée à un autre modèle.
 * Ordre : code WMO daily → codes WMO hourly → précip/temp hourly → précip/temp daily
 * → couverture nuageuse du même modèle.
 */
internal fun ForecastSeries.resolveDailyCondition(
    date: LocalDate,
    zone: ZoneId
): DailyConditionResolution? {
    val dailyIndex = daily.dates.indexOf(date)
    if (dailyIndex < 0) return null

    WeatherCondition.fromWmoCode(daily.weatherCode.getOrNull(dailyIndex))
        ?.takeUnless { it == WeatherCondition.UNKNOWN }
        ?.let { return DailyConditionResolution(it, inferred = false) }

    val hourlyIndices = hourly.timestamps.indices.filter { index ->
        hourly.timestamps[index].atZone(zone).toLocalDate() == date
    }

    hourlyIndices.mapNotNull { index ->
        WeatherCondition.fromWmoCode(hourly.weatherCode.getOrNull(index))
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
    }.maxByOrNull(WeatherCondition::severityRank)
        ?.let { return DailyConditionResolution(it, inferred = true) }

    hourlyIndices.mapNotNull { index ->
        WeatherCondition.inferFromPrecipAndTemp(
            precipMm = hourly.precipitation.getOrNull(index),
            tempMinC = hourly.temperature2m.getOrNull(index)
        )
    }.maxByOrNull(WeatherCondition::severityRank)
        ?.let { return DailyConditionResolution(it, inferred = true) }

    WeatherCondition.inferFromPrecipAndTemp(
        precipMm = daily.precipitationSum.getOrNull(dailyIndex),
        tempMinC = daily.tempMin.getOrNull(dailyIndex)
    )?.let { return DailyConditionResolution(it, inferred = true) }

    dailyCloudCoverMean(date, zone)?.let { cloudCover ->
        return DailyConditionResolution(
            condition = WeatherCondition.fromCloudCover(cloudCover.toDouble()),
            inferred = true
        )
    }

    return null
}
