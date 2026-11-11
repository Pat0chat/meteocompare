package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Agrégations journalières dérivées uniquement des données du modèle concerné. */
internal fun ForecastSeries.dailyCloudCoverMean(date: LocalDate, zone: ZoneId): Int? {
    if (hourly.cloudCover.isEmpty()) return null
    var sum = 0L
    var count = 0
    val size = minOf(hourly.timestamps.size, hourly.cloudCover.size)
    for (index in 0 until size) {
        val timestamp = hourly.timestamps[index]
        if (timestamp.atZone(zone).toLocalDate() != date) continue
        val value = hourly.cloudCover[index] ?: continue
        if (value !in 0..100) continue
        sum += value
        count++
    }
    return if (count == 0) null else (sum.toDouble() / count).roundToInt()
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

/** Origine de la condition journalière finalement affichée. */
internal enum class DailyConditionProvenance {
    /** Code WMO journalier fourni par l'API pour ce modèle. */
    DAILY_WMO,

    /** Agrégation de codes WMO horaires fournis par l'API pour ce modèle. */
    HOURLY_WMO,

    /** Condition reconstruite localement depuis précipitations, température ou nébulosité. */
    DERIVED_VARIABLES
}

/** Résultat d'une condition journalière avec provenance explicite. */
internal data class DailyConditionResolution(
    val condition: WeatherCondition,
    val provenance: DailyConditionProvenance
) {
    /** Seules les conditions reconstruites depuis des variables doivent être atténuées. */
    val inferred: Boolean
        get() = provenance == DailyConditionProvenance.DERIVED_VARIABLES
}

/** La nuit affichée avec un jour commence à SON coucher de soleil et finit au lever suivant. */
internal enum class DailyConditionPeriod { FULL_DAY, DAY, NIGHT }

/**
 * Condition représentative d'une période, sans confondre maximum de sévérité et
 * temps majoritaire. Un WMO daily Open-Meteo peut désigner une seule heure de
 * pluie : les WMO horaires, quand ils couvrent suffisamment le jour, priment.
 *
 * La majorité est calculée hiérarchiquement (sec/humide, puis famille, puis
 * sous-type) : 10 h de ciel clair + 8 h de ciel variable ne doivent pas perdre
 * contre 6 h de pluie, même si « pluie » est le code individuel le plus fréquent.
 * Les phénomènes minoritaires restent consultables heure par heure et dans les
 * probabilités/cumuls, ils ne sont simplement plus l'icône dominante.
 *
 * Pour les sous-périodes, aucune condition daily n'est réutilisée : une seule
 * valeur WMO quotidienne ne permet pas de localiser le phénomène jour/nuit.
 */
internal fun ForecastSeries.resolveDailyCondition(
    date: LocalDate,
    zone: ZoneId,
    period: DailyConditionPeriod = DailyConditionPeriod.FULL_DAY,
    solarWindow: ForecastSolarWindow? = null,
    nextSolarWindow: ForecastSolarWindow? = null
): DailyConditionResolution? {
    val dailyIndex = daily.dates.indexOf(date)
    val dailyNative = dailyIndex.takeIf { it >= 0 }
        ?.let { WeatherCondition.fromWmoCode(daily.weatherCode.getOrNull(it)) }
        ?.takeUnless { it == WeatherCondition.UNKNOWN }

    val window = solarWindow ?: dailyIndex.takeIf { it >= 0 }?.let {
        ForecastSolarWindow(daily.sunrise.getOrNull(it), daily.sunset.getOrNull(it))
    }
    val nextIndex = daily.dates.indexOf(date.plusDays(1))
    val nextWindow = nextSolarWindow ?: nextIndex.takeIf { it >= 0 }?.let {
        ForecastSolarWindow(daily.sunrise.getOrNull(it), daily.sunset.getOrNull(it))
    }
    val start = when (period) {
        DailyConditionPeriod.FULL_DAY -> date.atStartOfDay(zone).toInstant()
        DailyConditionPeriod.DAY -> window?.takeIf { it.isValid }?.sunrise ?: return null
        DailyConditionPeriod.NIGHT -> window?.takeIf { it.isValid }?.sunset ?: return null
    }
    val end = when (period) {
        DailyConditionPeriod.FULL_DAY -> date.plusDays(1).atStartOfDay(zone).toInstant()
        DailyConditionPeriod.DAY -> window?.sunset ?: return null
        DailyConditionPeriod.NIGHT -> nextWindow?.takeIf { it.isValid }?.sunrise ?: return null
    }
    if (!end.isAfter(start)) return null

    val indices = hourly.timestamps.indices.filter { index ->
        val instant = hourly.timestamps[index]
        !instant.isBefore(start) && instant.isBefore(end)
    }
    // Une heure avec WMO natif l'emporte sur ses variables. Quand le WMO
    // manque, on utilise les variables de CETTE heure, et non le total daily :
    // 23 h sèches + 1 h pluvieuse restent majoritairement sèches.
    val hourlyConditions = indices.mapNotNull { index ->
        val native = WeatherCondition.fromWmoCode(hourly.weatherCode.getOrNull(index))
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
        val condition = native ?: resolveHourlyCondition(index)
        condition?.let { Triple(index, it, native != null) }
    }
    val enoughCoverage = hourlyConditions.size >= 12 &&
        java.time.Duration.between(
            hourly.timestamps[hourlyConditions.first().first],
            hourly.timestamps[hourlyConditions.last().first]
        ).toHours() >= 12
    val usableHourly = when (period) {
        DailyConditionPeriod.DAY, DailyConditionPeriod.NIGHT -> {
            // Ne pas présenter une seule heure disponible comme représentative
            // d'une nuit complète (cas de fin d'horizon d'un modèle régional).
            val periodHours = java.time.Duration.between(start, end).toHours().toInt()
            hourlyConditions.size >= minOf(3, periodHours.coerceAtLeast(1))
        }
        DailyConditionPeriod.FULL_DAY -> when {
            dailyNative == null -> hourlyConditions.isNotEmpty()
            dailyNative.isSky -> hourlyConditions.size >= 6
            else -> enoughCoverage
        }
    }
    if (usableHourly) {
        WeatherConditionConsensus.resolveUnweighted(hourlyConditions.map { it.second })?.let { result ->
            val provenance = if (hourlyConditions.all { it.third })
                DailyConditionProvenance.HOURLY_WMO
            else DailyConditionProvenance.DERIVED_VARIABLES
            return DailyConditionResolution(result, provenance)
        }
    }
    if (period == DailyConditionPeriod.FULL_DAY && dailyNative != null) {
        return DailyConditionResolution(dailyNative, DailyConditionProvenance.DAILY_WMO)
    }
    if (period != DailyConditionPeriod.FULL_DAY) return null

    // Cas d'une ancienne réponse ne fournissant que des agrégats daily.
    if (dailyIndex >= 0) {
        WeatherCondition.inferFromPrecipAndTemp(
            precipMm = daily.precipitationSum.getOrNull(dailyIndex),
            tempMinC = daily.tempMin.getOrNull(dailyIndex)
        )?.let { return DailyConditionResolution(it, DailyConditionProvenance.DERIVED_VARIABLES) }
    }
    dailyCloudCoverMean(date, zone)?.let { cloud ->
        return DailyConditionResolution(
            WeatherCondition.fromCloudCover(cloud.toDouble()),
            DailyConditionProvenance.DERIVED_VARIABLES
        )
    }
    return null
}

/** Valeurs horaires propres à la période (ne pas répéter le max daily dans les 2 cases). */
internal data class DailyPeriodMetrics(
    val precipitationProbabilityMax: Int?,
    val cloudCoverMean: Int?
)

internal fun ForecastSeries.dailyPeriodMetrics(
    period: DailyConditionPeriod,
    window: ForecastSolarWindow,
    nextWindow: ForecastSolarWindow? = null
): DailyPeriodMetrics? {
    if (!window.isValid || period == DailyConditionPeriod.FULL_DAY) return null
    val start = if (period == DailyConditionPeriod.DAY) window.sunrise!! else window.sunset!!
    val end = if (period == DailyConditionPeriod.DAY) window.sunset!!
        else nextWindow?.takeIf { it.isValid }?.sunrise ?: return null
    if (!end.isAfter(start)) return null
    val indices = hourly.timestamps.indices.filter { index ->
        val time = hourly.timestamps[index]
        !time.isBefore(start) && time.isBefore(end)
    }
    val probabilities = indices.mapNotNull {
        hourly.precipitationProbability.getOrNull(it)?.takeIf { p -> p in 0..100 }
    }
    val cloudCover = indices.mapNotNull {
        hourly.cloudCover.getOrNull(it)?.takeIf { p -> p in 0..100 }
    }
    if (probabilities.isEmpty() && cloudCover.isEmpty()) return null
    return DailyPeriodMetrics(
        precipitationProbabilityMax = probabilities.maxOrNull(),
        cloudCoverMean = cloudCover.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    )
}
