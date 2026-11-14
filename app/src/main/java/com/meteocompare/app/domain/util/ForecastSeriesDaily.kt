package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.effectiveCloudCover
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.SolarSkyInterpretation
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
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

internal fun HourlyForecast.resolveHourlyCondition(index: Int): WeatherCondition? {
    if (index !in timestamps.indices) return null
    // Couvre également les anciens caches dans lesquels la nébulosité totale
    // n'avait pas été reconstruite, sans mélanger les données de deux modèles.
    val effectiveCloud = effectiveCloudCover(index)
    val solarIndex = timestamps.indexOf(timestamps[index].plusSeconds(3600))
    val veiled = SolarSkyInterpretation.isVeiledSky(
        effectiveCloud,
        cloudCoverLow.getOrNull(index), cloudCoverMid.getOrNull(index),
        cloudCoverHigh.getOrNull(index),
        sunshineDuration.getOrNull(solarIndex),
        directNormalIrradiance.getOrNull(solarIndex)
    )
    val basic = WeatherCondition.fromWmoCode(weatherCode.getOrNull(index))
        ?.takeUnless { it == WeatherCondition.UNKNOWN }
        ?: WeatherCondition.inferFromPrecipAndTemp(
            precipMm = precipitation.getOrNull(index),
            tempMinC = temperature2m.getOrNull(index)
        )
        ?: effectiveCloud
            ?.let { WeatherCondition.fromCloudCover(it.toDouble()) }
    // Avoid a false luminous label when another field predicts actual rainfall.
    val wet = precipitation.getOrNull(index)?.let { it.isFinite() && it > 0.1 } == true
    return SolarSkyInterpretation.refine(basic, veiled && !wet)
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
        val condition = resolveHourlyCondition(index)
        condition?.let { Triple(index, it, native != null) }
    }
        // Un cache mal fusionné peut répéter une heure : elle ne doit jamais
        // peser plusieurs fois dans le vote. Priorité au WMO natif en cas de
        // doublon de timestamp, puis tri temporel (pas ordre de l'API).
        .sortedWith(compareBy<Triple<Int, WeatherCondition, Boolean>> {
            hourly.timestamps[it.first].truncatedTo(ChronoUnit.HOURS)
        }.thenByDescending { it.third }.thenBy { hourly.timestamps[it.first] })
        .distinctBy { hourly.timestamps[it.first].truncatedTo(ChronoUnit.HOURS) }

    // Une courte plage de 6 h ne représente pas une journée. Cependant, un
    // jour courant encore incomplet (ex. prévisions seulement 10 h → minuit)
    // peut être résolu si la branche gagnante occupe déjà STRICTEMENT plus de
    // la moitié des heures réelles de la période : les heures manquantes ne
    // peuvent alors pas renverser cette majorité.
    val values = hourlyConditions.map { it.second }
    val expectedHours = ceil(Duration.between(start, end).toMinutes() / 60.0).toInt()
    val dryHours = values.count { it.isSky || it == WeatherCondition.FOG }
    val wetHours = values.size - dryHours
    val hasStableMajority = maxOf(dryHours, wetHours) > expectedHours / 2.0
    val noInternalGaps = hourlyConditions.zipWithNext().all { (a, b) ->
        Duration.between(hourly.timestamps[a.first], hourly.timestamps[b.first]).toMinutes() <= 180
    }
    if (hasStableMajority && noInternalGaps) {
            WeatherConditionConsensus.resolveUnweighted(values)?.let { result ->
                val provenance = if (hourlyConditions.all { it.third })
                    DailyConditionProvenance.HOURLY_WMO
                else DailyConditionProvenance.DERIVED_VARIABLES
                return DailyConditionResolution(result, provenance)
            }
    }
    // Une famille sèche/pluvieuse est mathématiquement majoritaire, mais les
    // échantillons sont séparés par de grandes lacunes. On refuse de déduire
    // une feuille WMO précise de ces fragments. Surtout, ne pas revenir au
    // WMO daily qui pourrait annoncer la famille OPPOSÉE : par exemple,
    // 19 h sèches malgré une lacune de 5 h et un daily « pluie ».
    // Les épisodes horaires restent disponibles dans la chronologie.
    if (hasStableMajority && !noInternalGaps) {
        // Même si une lacune interne interdit de supposer le temps de chaque
        // heure, un sous-type qui occupe déjà STRICTEMENT plus de la moitié
        // de TOUTES les heures de la période est mathématiquement dominant,
        // quel que soit le temps durant les heures inconnues. Ne jamais
        // extrapoler une simple majorité de la branche sec/humide à un sous-type.
        val certainLeaf = values.groupingBy { it }.eachCount()
            .entries.firstOrNull { (condition, count) ->
                condition != WeatherCondition.UNKNOWN && count > expectedHours / 2.0
            }?.key
        if (certainLeaf != null) {
            val provenance = if (hourlyConditions.all { it.third })
                DailyConditionProvenance.HOURLY_WMO
            else DailyConditionProvenance.DERIVED_VARIABLES
            return DailyConditionResolution(certainLeaf, provenance)
        }
        return null
    }

    // Quand les heures couvrent réellement toute la période MAIS ne dégagent
    // aucune majorité stable (égalité sec/humide, par exemple), le code daily
    // peut seulement représenter le phénomène le plus sévère. Le restituer
    // comme « dominant » introduirait précisément le biais de l'issue #40.
    // On préfère alors l'absence de condition à une certitude inventée.
    if (hasRepresentativeHourlyCoverage(
            start, end, hourlyConditions.map { hourly.timestamps[it.first] }
        ) && !hasStableMajority) return null

    if (period == DailyConditionPeriod.FULL_DAY && dailyNative != null) {
        // Sans données horaires représentatives, garder le code API plutôt
        // que prétendre avoir calculé une majorité à partir d'un fragment.
        return DailyConditionResolution(dailyNative, DailyConditionProvenance.DAILY_WMO)
    }
    // Un cumul quotidien de pluie ne prouve pas une condition majoritairement
    // pluvieuse (cas exact de l'issue #40). Il ne sert donc JAMAIS d'inférence
    // de condition dominante ; idem pour une moyenne nuageuse partielle.
    return null
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
