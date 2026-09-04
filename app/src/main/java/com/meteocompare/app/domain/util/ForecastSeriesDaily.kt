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

/**
 * Résout la condition d'un jour sans jamais emprunter une donnée à un autre modèle.
 *
 * Ordre : un phénomène journalier significatif (pluie, neige, brouillard,
 * orage…) reste prioritaire. Pour les seuls états du ciel (0 à 3), le mode des
 * WMO horaires du jour fournit une représentation plus fidèle de la condition
 * dominante qu'un code daily volontairement « le plus sévère ». Viennent
 * ensuite les fallbacks précip/temp/nuages.
 *
 * La provenance distingue les codes WMO réellement fournis des conditions
 * reconstruites localement. L'UI peut ainsi atténuer uniquement ces dernières,
 * sans présenter comme « inféré » un code journalier clair/nuageux valide.
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

    // Les phénomènes significatifs du daily sont intentionnellement
    // conservateurs et doivent rester visibles même s'ils ne durent qu'une
    // partie de la journée.
    if (dailyNative != null && !dailyNative.isSky) {
        return DailyConditionResolution(
            condition = dailyNative,
            provenance = DailyConditionProvenance.DAILY_WMO
        )
    }

    val hourlyIndices = hourly.timestamps.indices.filter { index ->
        hourly.timestamps[index].atZone(zone).toLocalDate() == date
    }

    val hourlyNative = hourlyIndices.mapNotNull { index ->
        WeatherCondition.fromWmoCode(hourly.weatherCode.getOrNull(index))
            ?.takeUnless { it == WeatherCondition.UNKNOWN }
    }

    // Open-Meteo résume le daily par la condition la plus sévère. Pour une
    // simple nébulosité, cela pouvait donc afficher « couvert » à cause de
    // quelques heures tardives alors que la journée était majoritairement
    // claire. Le mode des états de ciel horaires corrige ce biais sans créer
    // une condition locale : la provenance reste bien un WMO natif.
    if (dailyNative?.isSky == true) {
        hourlyNative.filter { it.isSky }.modalCondition()
            ?.let {
                return DailyConditionResolution(
                    condition = it,
                    provenance = DailyConditionProvenance.HOURLY_WMO
                )
            }

        return DailyConditionResolution(
            condition = dailyNative,
            provenance = DailyConditionProvenance.DAILY_WMO
        )
    }

    hourlyNative.modalCondition()
        ?.let {
            return DailyConditionResolution(
                condition = it,
                provenance = DailyConditionProvenance.HOURLY_WMO
            )
        }

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
        )?.let {
            return DailyConditionResolution(
                condition = it,
                provenance = DailyConditionProvenance.DERIVED_VARIABLES
            )
        }
        cloudValues.takeIf { it.isNotEmpty() }
            ?.average()
            ?.let(WeatherCondition::fromCloudCover)
            ?.let {
                return DailyConditionResolution(
                    condition = it,
                    provenance = DailyConditionProvenance.DERIVED_VARIABLES
                )
            }
    }

    if (dailyIndex >= 0) {
        WeatherCondition.inferFromPrecipAndTemp(
            precipMm = daily.precipitationSum.getOrNull(dailyIndex),
            tempMinC = daily.tempMin.getOrNull(dailyIndex)
        )?.let {
            return DailyConditionResolution(
                condition = it,
                provenance = DailyConditionProvenance.DERIVED_VARIABLES
            )
        }
    }

    dailyCloudCoverMean(date, zone)?.let { cloudCover ->
        return DailyConditionResolution(
            condition = WeatherCondition.fromCloudCover(cloudCover.toDouble()),
            provenance = DailyConditionProvenance.DERIVED_VARIABLES
        )
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
