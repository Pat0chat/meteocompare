package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.ForecastDisplayHorizon
import java.time.Instant
import java.time.ZoneId

/**
 * Mode d'affichage des tableaux et graphes de la page détail :
 *
 *   - [DAILY]  : granularité par jour sur 10 jours.
 *   - [HOURLY] : granularité par heure sur les 24 prochaines heures.
 *                Sert quand on planifie une activité
 *                précise "à quelle heure va-t-il pleuvoir aujourd'hui ?".
 *
 * Le mode par défaut est [DAILY] — c'est la vue historique et la plus adaptée
 * à un scan rapide. Le mode par heure est un opt-in explicite via le menu de granularité
 * des prévisions détaillées.
 */
enum class DisplayMode {
    HOURLY,
    DAILY
}


/**
 * Horizon propre à la chronologie synthétique de la page détail.
 * Il reste volontairement séparé de [DisplayMode], utilisé aussi par les
 * tableaux détaillés, afin que ceux-ci conservent leur simple choix Hourly/Daily.
 */
internal enum class TimelineRange(
    val displayMode: DisplayMode,
    val maxDisplayPoints: Int
) {
    HOURLY(DisplayMode.HOURLY, ForecastDisplayHorizon.HOURLY_HOURS),
    DAILY(DisplayMode.DAILY, ForecastDisplayHorizon.DAYS);

    companion object {
        fun defaultFor(mode: DisplayMode): TimelineRange = when (mode) {
            DisplayMode.HOURLY -> HOURLY
            DisplayMode.DAILY -> DAILY
        }
    }
}

/**
 * Fenêtre horaire glissante utilisée par les tableaux et la chronologie : les
 * 24 prochaines heures à partir de l'heure courante arrondie dans le fuseau de
 * la ville. Les vues journalières et la Chart View conservent leur horizon 10 jours.
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
    return startHour to startHour.plusSeconds(ForecastDisplayHorizon.HOURLY_HOURS * 3_600L)
}


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
