package com.meteocompare.app.domain.util

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Identité temporelle des valeurs météo affichées.
 *
 * Le forecast brut peut rester parfaitement valable plusieurs heures. Ce qui
 * change, sans nouveau téléchargement, est l'échéance considérée comme
 * « maintenant » ainsi que le jour civil de la ville. Conserver ces deux
 * informations dans une clé évite de recalculer les agrégats à chaque minute :
 * le ticker vérifie souvent, mais les ViewModels ne reconstruisent leur état
 * que lorsqu'une frontière réellement visible est franchie.
 */
internal data class ForecastPresentationKey(
    val localDate: LocalDate,
    val hourlyAnchor: Instant
)

internal fun forecastPresentationKey(
    forecast: CityForecast,
    at: Instant
): ForecastPresentationKey {
    val zone = resolveZoneOrUtc(forecast.city.timezone)
    return ForecastPresentationKey(
        localDate = at.atZone(zone).toLocalDate(),
        hourlyAnchor = HourlySampling.anchor(forecast, at)
    )
}

/** Vrai quand les données brutes identiques doivent être représentées à nouveau. */
internal fun hasForecastPresentationChanged(
    forecast: CityForecast,
    previouslyCalculatedAt: Instant,
    now: Instant
): Boolean = forecastPresentationKey(forecast, previouslyCalculatedAt) !=
    forecastPresentationKey(forecast, now)

/**
 * Délai jusqu'à la prochaine minute d'horloge, borné à ]0, 60 s].
 *
 * L'alignement évite la dérive d'un simple `delay(60_000)` et permet de
 * détecter rapidement les changements manuels d'heure, de fuseau ou les
 * reprises après mise en veille du process.
 */
internal fun nextMinuteBoundaryDelayMs(now: Instant): Long {
    val minuteMs = 60_000L
    val elapsed = Math.floorMod(now.toEpochMilli(), minuteMs)
    return if (elapsed == 0L) minuteMs else minuteMs - elapsed
}

/**
 * Horloge légère commune aux écrans Home et Détails.
 *
 * Elle ne déclenche aucun accès réseau. Chaque émission permet uniquement de
 * re-présenter le forecast brut déjà en mémoire à la bonne échéance horaire.
 */
internal fun forecastPresentationTicks(clock: Clock): Flow<Instant> = flow {
    while (true) {
        delay(nextMinuteBoundaryDelayMs(clock.instant()))
        emit(clock.instant())
    }
}
