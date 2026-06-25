package com.meteocompare.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Format renvoyé par Open-Meteo dans le champ `time` des prévisions horaires.
 * Exemple : "2026-06-23T15:00" (heure locale, pas de timezone offset).
 */
private val OPEN_METEO_HOURLY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/**
 * Parse une heure Open-Meteo en [Instant] absolu en utilisant la [timezone]
 * retournée par la même réponse API.
 *
 * Retourne null si le format est invalide — utile pour les imports défensifs.
 */
fun parseOpenMeteoTime(time: String, timezone: String): Instant? = try {
    LocalDateTime.parse(time, OPEN_METEO_HOURLY_FORMAT)
        .atZone(ZoneId.of(timezone))
        .toInstant()
} catch (e: DateTimeParseException) {
    null
} catch (e: java.time.zone.ZoneRulesException) {
    null
}

/**
 * Parse une date Open-Meteo (`yyyy-MM-dd`) en [LocalDate].
 */
fun parseOpenMeteoDate(date: String): LocalDate? = try {
    LocalDate.parse(date)
} catch (e: DateTimeParseException) {
    null
}
