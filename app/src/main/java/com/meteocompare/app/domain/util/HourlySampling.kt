package com.meteocompare.app.domain.util

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Grille horaire commune aux agrégats Home/widget et aux scénarios.
 *
 * Open-Meteo renvoie des échéances à l'heure locale pleine. On ancre donc la
 * fenêtre sur l'heure locale la plus proche de [now], dans le fuseau de la
 * ville, puis on avance par heures absolues. Cela évite le décalage historique
 * où une valeur 13:00 pouvait être affichée sous un label 12h à 12:56.
 */
internal object HourlySampling {
    const val MAX_TIME_DELTA_SECONDS: Long = 30L * 60L

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

    fun List<Instant>.nearestIndex(target: Instant): Int? {
        if (isEmpty()) return null
        val result = binarySearch(target)
        if (result >= 0) return result

        val insertionPoint = -result - 1
        return when (insertionPoint) {
            0 -> 0
            size -> lastIndex
            else -> {
                val before = this[insertionPoint - 1]
                val after = this[insertionPoint]
                if (target.epochSecond - before.epochSecond <=
                    after.epochSecond - target.epochSecond
                ) insertionPoint - 1 else insertionPoint
            }
        }
    }

    fun isCloseEnough(actual: Instant, target: Instant): Boolean =
        abs(actual.epochSecond - target.epochSecond) <= MAX_TIME_DELTA_SECONDS
}
