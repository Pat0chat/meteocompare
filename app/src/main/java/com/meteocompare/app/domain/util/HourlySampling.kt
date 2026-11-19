package com.meteocompare.app.domain.util

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.HourlyForecast
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Grille horaire commune aux agrégats Home/widget et aux scénarios.
 *
 * Open-Meteo renvoie des échéances à l'heure locale pleine. On ancre donc la
 * fenêtre sur l'heure locale la plus proche de [now], dans le fuseau de la
 * ville, puis on avance par heures absolues. Cela évite le décalage historique
 * où une valeur 13:00 pouvait être affichée sous un label 12h à 12:56.
 */
internal object HourlySampling {

    fun anchor(forecast: CityForecast, now: Instant): Instant {
        val zone = resolveZoneOrUtc(forecast.city.timezone)
        val localNow = now.atZone(zone)
        // Garder le ZonedDateTime (et donc son offset) est essentiel pendant
        // l'heure répétée du passage heure d'été → heure d'hiver. Passer par
        // LocalDateTime ferait perdre la distinction entre les deux « 02:xx ».
        val floor = localNow.truncatedTo(ChronoUnit.HOURS)
        val secondsIntoHour = localNow.minute * 60L + localNow.second
        val rounded = if (secondsIntoHour > 30L * 60L) floor.plusHours(1) else floor
        return rounded.toInstant()
    }

    /**
     * A display slot at H always represents [H,H+1h). The source provider
     * timestamps precipitation, probability, gusts and sunshine at the END
     * of their accumulation interval. WMO, temperature and clouds stay at H.
     *
     * Resolve the actual UTC instant instead of index+1: missing timestamps,
     * half-hour-offset zones, midnight and both DST transitions remain safe.
     * Never silently take an adjacent hour when H+1 is unavailable.
     */
    fun nextHourIndex(hourly: HourlyForecast, atIndex: Int): Int? {
        val start = hourly.timestamps.getOrNull(atIndex) ?: return null
        return with(this) { hourly.timestamps.exactIndex(start.plusSeconds(3_600L)) }
    }

    data class SlotPrecipitation(val amountMm: Double?, val probabilityPercent: Int?)

    fun precipitationForSlot(hourly: HourlyForecast, atIndex: Int): SlotPrecipitation {
        val source = nextHourIndex(hourly, atIndex)
        return SlotPrecipitation(
            source?.let { hourly.precipitation.getOrNull(it) },
            source?.let { hourly.precipitationProbability.getOrNull(it) }
        )
    }

    /**
     * Retourne uniquement l'échéance exactement demandée.
     *
     * Une heure voisine n'est jamais utilisée comme substitut : un modèle sans
     * valeur au timestamp cible est simplement absent du consensus de ce slot.
     */
    fun List<Instant>.exactIndex(target: Instant): Int? {
        if (isEmpty()) return null
        val index = indexOf(target)
        return index.takeIf { it >= 0 }
    }

}
