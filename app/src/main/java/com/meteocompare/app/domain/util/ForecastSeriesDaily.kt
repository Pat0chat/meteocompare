package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.effectiveCloudCover
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Série d'échantillons valides, dédoublonnée par heure réelle (Instant).
 *
 * Le contrôle de validité doit précéder le dédoublonnage : une valeur manquante
 * en première position ne doit pas masquer une mesure valide au même instant.
 * Les heures d'un changement d'heure d'automne restent distinctes en UTC.
 */
private inline fun <T : Any> ForecastSeries.validHourlyByHour(
    start: Instant,
    end: Instant,
    valueAt: (Int) -> T?
): Map<Instant, T> {
    val result = linkedMapOf<Instant, T>()
    hourly.timestamps.forEachIndexed { index, instant ->
        if (!instant.isBefore(start) && instant.isBefore(end)) {
            val value = valueAt(index) ?: return@forEachIndexed
            result.putIfAbsent(instant.truncatedTo(ChronoUnit.HOURS), value)
        }
    }
    return result
}

/** Agrégations journalières dérivées uniquement des données du modèle concerné. */
internal fun ForecastSeries.dailyCloudCoverMean(date: LocalDate, zone: ZoneId): Int? {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val valuesByHour = validHourlyByHour(start, end) { index ->
        hourly.effectiveCloudCover(index)
    }
    if (!hasRepresentativeHourlyCoverage(start, end, valuesByHour.keys.toList())) return null
    return valuesByHour.values.average().roundToInt()
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
internal fun ForecastSeries.resolveHourlyCondition(index: Int): WeatherCondition? =
    hourly.resolveHourlyCondition(index)

internal fun HourlyForecast.resolveHourlyCondition(index: Int): WeatherCondition? =
    ConditionDecisionEngine.hourly(this, index).condition

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
    val provenance: DailyConditionProvenance,
    val decision: ConditionDecision? = null
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
    val result = ConditionDecisionEngine.period(this, date, zone, period, solarWindow, nextSolarWindow)
    val condition = result.condition ?: return null
    val provenance = when (result.origin) {
        ConditionOrigin.HOURLY_WMO, ConditionOrigin.HOURLY_DOMINANT -> DailyConditionProvenance.HOURLY_WMO
        ConditionOrigin.DAILY_WMO_MAXIMUM -> DailyConditionProvenance.DAILY_WMO
        else -> DailyConditionProvenance.DERIVED_VARIABLES
    }
    return DailyConditionResolution(condition, provenance, result)
}

/**
 * Couverture temporelle minimale pour attribuer une condition à toute une
 * période. Les instants sont triés/dédoublonnés par l'appelant ; aucune série
 * locale de quelques heures n'est considérée comme une journée complète.
 */
internal fun hasRepresentativeHourlyCoverage(
    start: Instant,
    end: Instant,
    samples: List<Instant>
): Boolean {
    val minutes = Duration.between(start, end).toMinutes()
    if (minutes <= 0 || samples.isEmpty()) return false
    val expected = ceil(minutes / 60.0).toInt()
    val required = ceil(expected * 0.75).toInt().coerceAtLeast(1)
    val valid = samples.asSequence()
        .filter { !it.isBefore(start) && it.isBefore(end) }
        .map { it.truncatedTo(ChronoUnit.HOURS) }
        .distinct()
        .sorted()
        .toList()
    if (valid.size < required) return false
    // Un bloc de 18 h au début du jour ne suffit pas à représenter le ciel de
    // toute la journée : les deux bords doivent aussi être échantillonnés.
    if (Duration.between(start, valid.first()).toMinutes() > 120) return false
    if (Duration.between(valid.last(), end).toMinutes() > 120) return false
    // Empêche des grappes d'heures séparées par des trous arbitraires d'être
    // interprétées comme une couverture régulière de la période.
    return valid.zipWithNext().all { (a, b) -> Duration.between(a, b).toMinutes() <= 180 }
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
    val probabilities = validHourlyByHour(start, end) { index ->
        hourly.precipitationProbability.getOrNull(index)?.takeIf { it in 0..100 }
    }
    val cloudCover = validHourlyByHour(start, end) { index ->
        hourly.effectiveCloudCover(index)
    }
    if (probabilities.isEmpty() && cloudCover.isEmpty()) return null
    // Le maximum de risque est un signal ponctuel : une seule heure valide peut
    // suffire à le signaler. Une MOYENNE de nébulosité prétend en revanche
    // décrire toute la période et exige une couverture horaire représentative.
    val representativeCloud = cloudCover.takeIf {
        hasRepresentativeHourlyCoverage(start, end, it.keys.toList())
    }
    return DailyPeriodMetrics(
        precipitationProbabilityMax = probabilities.values.maxOrNull(),
        cloudCoverMean = representativeCloud?.values?.average()?.roundToInt()
    )
}
