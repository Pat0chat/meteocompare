package com.meteocompare.app.ui.citydetail

import java.time.Instant
import com.meteocompare.app.core.util.resolveZoneOrUtc
import java.time.ZoneId

/**
 * Mode d'affichage des tableaux et graphes de la page détail :
 *
 *   - [DAILY]  : granularité par jour sur ~7 jours. Vue synthétique historique,
 *                rapide à scanner "quel temps globalement cette semaine ?".
 *   - [HOURLY] : granularité par heure sur un horizon glissant de 24 heures.
 *                Sert quand on planifie une activité
 *                précise "à quelle heure va-t-il pleuvoir aujourd'hui ?".
 *
 * Le mode par défaut est [DAILY] — c'est la vue historique et la plus adaptée
 * à un scan rapide. Le mode par heure est un opt-in explicite via le toggle
 * segmenté sous la TodaySummaryCard.
 */
enum class DisplayMode {
    HOURLY,
    DAILY
}

/**
 * Fenêtre horaire glissante utilisée par les tableaux et la chronologie : de
 * l'heure courante arrondie dans le fuseau de la ville jusqu'à 24 heures plus
 * tard. L'horizon reste ainsi utile le soir et traverse naturellement minuit.
 *
 * Filtrer avec `timestamp >= start && timestamp < endExclusive`.
 *
 * Le calcul porte sur 24 heures réelles à partir de l'heure locale courante.
 * Le fuseau de la ville ne sert qu'à choisir l'heure de départ affichée ; cela
 * évite les fenêtres anormalement longues ou courtes lors d'un changement
 * d'heure tout en conservant exactement 24 échéances horaires possibles.
 */
internal fun resolveCityZone(timezone: String?): ZoneId = resolveZoneOrUtc(timezone)

/** Date civile correspondant à [now] dans le fuseau de la ville. */
internal fun cityLocalDate(timezone: String?, now: Instant): java.time.LocalDate =
    now.atZone(resolveCityZone(timezone)).toLocalDate()

internal fun computeHourlyHorizon(
    timezone: String?,
    now: Instant
): Pair<Instant, Instant> {
    val zone = resolveCityZone(timezone)
    val startHour = now.atZone(zone)
        .withMinute(0)
        .withSecond(0)
        .withNano(0)
        .toInstant()
    return startHour to startHour.plusSeconds(HOURLY_HORIZON_SECONDS)
}

private const val HOURLY_HORIZON_SECONDS = 24L * 60L * 60L

internal fun com.meteocompare.app.domain.model.CityDetailViewMode.toDisplayMode(): DisplayMode =
    when (this) {
        com.meteocompare.app.domain.model.CityDetailViewMode.HOURLY -> DisplayMode.HOURLY
        com.meteocompare.app.domain.model.CityDetailViewMode.DAILY -> DisplayMode.DAILY
    }

internal fun DisplayMode.toPreference(): com.meteocompare.app.domain.model.CityDetailViewMode =
    when (this) {
        DisplayMode.HOURLY -> com.meteocompare.app.domain.model.CityDetailViewMode.HOURLY
        DisplayMode.DAILY -> com.meteocompare.app.domain.model.CityDetailViewMode.DAILY
    }
