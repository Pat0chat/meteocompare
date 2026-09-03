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

/**
 * Condition d'une heure pour UN modèle, avec un fallback unique partagé par
 * Home, timelines et widgets : WMO natif → précip/température → nébulosité.
 *
 * L'inférence utilise exclusivement les variables du même modèle. Cela permet
 * notamment à un ancien cache sans `weather_code`, mais avec `cloud_cover`, de
 * participer correctement à la branche NON_PRECIPITATION du consensus
 * hiérarchique au lieu de disparaître du vote racine.
 */
internal fun ForecastSeries.resolveHourlyCondition(index: Int): WeatherCondition? {
    if (index !in hourly.timestamps.indices) return null
    return WeatherCondition.fromWmoCode(hourly.weatherCode.getOrNull(index))
        ?.takeUnless { it == WeatherCondition.UNKNOWN }
        ?: WeatherCondition.inferFromPrecipAndTemp(
            precipMm = hourly.precipitation.getOrNull(index),
            tempMinC = hourly.temperature2m.getOrNull(index)
        )
        ?: hourly.cloudCover.getOrNull(index)
            ?.takeIf { it in 0..100 }
            ?.let { WeatherCondition.fromCloudCover(it.toDouble()) }
}

/** Résultat d'une condition journalière avec provenance explicite. */
internal data class DailyConditionResolution(
    val condition: WeatherCondition,
    val inferred: Boolean
)

/**
 * Résout la condition d'un jour sans jamais emprunter une donnée à un autre modèle.
 *
 * Ordre : un phénomène WMO daily significatif (pluie/neige/brouillard/orage)
 * reste prioritaire ; un simple état de ciel daily (0..3) est réévalué depuis
 * la nébulosité horaire du même modèle afin d'éviter l'effet MAX journalier ;
 * puis viennent le mode des WMO horaires et les fallbacks précip/temp/nuages.
 *
 * Ainsi, on ne banalise jamais un phénomène potentiellement important, tout en
 * évitant qu'une seule heure WMO=3 transforme artificiellement toute une
 * journée sèche avec éclaircies en « couvert ».
 */
internal fun ForecastSeries.resolveDailyCondition(
    date: LocalDate,
    zone: ZoneId
): DailyConditionResolution? {
    val dailyIndex = daily.dates.indexOf(date)

    val dailyNative = if (dailyIndex >= 0) {
        WeatherCondition.fromWmoCode(daily.weatherCode.getOrNull(dailyIndex))
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
    } else {
        null
    }

    // Open-Meteo agrège le weather_code journalier par MAX des codes horaires.
    // Pour pluie/neige/brouillard/orage, ce signal de phénomène significatif
    // reste utile. Pour les seuls codes de ciel 0..3, en revanche, MAX peut
    // transformer une seule heure couverte en « journée couverte ». On affine
    // donc les états SKY à partir de la nébulosité horaire du même modèle.
    if (dailyNative != null && !dailyNative.isSky) {
        return DailyConditionResolution(dailyNative, inferred = false)
    }

    val hourlyIndices = hourly.timestamps.indices.filter { index ->
        hourly.timestamps[index].atZone(zone).toLocalDate() == date
    }

    if (dailyNative?.isSky == true) {
        val cloudValues = hourlyIndices.mapNotNull { index ->
            hourly.cloudCover.getOrNull(index)?.takeIf { it in 0..100 }
        }
        cloudValues.takeIf { it.isNotEmpty() }
            ?.average()
            ?.let(WeatherCondition::fromCloudCover)
            ?.let { return DailyConditionResolution(it, inferred = true) }
    }

    hourlyIndices.mapNotNull { index ->
        WeatherCondition.fromWmoCode(hourly.weatherCode.getOrNull(index))
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
    }.modalCondition()
        ?.let { return DailyConditionResolution(it, inferred = true) }

    if (hourlyIndices.isNotEmpty()) {
        val precipitationValues = hourlyIndices.mapNotNull { index ->
            hourly.precipitation.getOrNull(index)?.takeIf { it.isFinite() && it >= 0.0 }
        }
        val temperatures = hourlyIndices.mapNotNull { index ->
            hourly.temperature2m.getOrNull(index)?.takeIf(Double::isFinite)
        }
        val cloudValues = hourlyIndices.mapNotNull { index ->
            hourly.cloudCover.getOrNull(index)?.takeIf { it in 0..100 }
        }
        WeatherCondition.inferFromPrecipAndTemp(
            precipMm = precipitationValues.takeIf { it.isNotEmpty() }?.sum(),
            tempMinC = temperatures.minOrNull()
        )?.let { return DailyConditionResolution(it, inferred = true) }
        cloudValues.takeIf { it.isNotEmpty() }
            ?.average()
            ?.let(WeatherCondition::fromCloudCover)
            ?.let { return DailyConditionResolution(it, inferred = true) }
    }

    if (dailyIndex >= 0) {
        WeatherCondition.inferFromPrecipAndTemp(
            precipMm = daily.precipitationSum.getOrNull(dailyIndex),
            tempMinC = daily.tempMin.getOrNull(dailyIndex)
        )?.let { return DailyConditionResolution(it, inferred = true) }
    }

    dailyCloudCoverMean(date, zone)?.let { cloudCover ->
        return DailyConditionResolution(
            condition = WeatherCondition.fromCloudCover(cloudCover.toDouble()),
            inferred = true
        )
    }

    // Un axe horaire peut exister tout en ne contenant aucune variable
    // exploitable (cache ancien, réponse partielle). Dans ce cas, ne pas
    // perdre le code daily SKY valide : il reste le dernier signal natif.
    if (dailyNative?.isSky == true) {
        return DailyConditionResolution(dailyNative, inferred = false)
    }

    return null
}

/** Mode catégoriel stable ; la sévérité n'intervient qu'en tie-break. */
private fun List<WeatherCondition>.modalCondition(): WeatherCondition? {
    if (isEmpty()) return null
    val counts = groupingBy { it }.eachCount()
    val maxCount = counts.values.maxOrNull() ?: return null
    return counts.asSequence()
        .filter { (_, count) -> count == maxCount }
        .map { (condition, _) -> condition }
        .maxByOrNull(WeatherCondition::severityRank)
}
