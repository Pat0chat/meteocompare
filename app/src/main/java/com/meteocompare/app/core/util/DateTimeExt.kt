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
    DateTimeFormatter.ISO_LOCAL_DATE_TIME

/**
 * Résout un fuseau métier en privilégiant celui de la ville et en retombant
 * explicitement sur UTC lorsque la valeur est absente ou invalide.
 *
 * Ce helper est volontairement distinct de [parseOpenMeteoTime] : le parsing
 * d'une réponse API reste strict et renvoie `null` si le fuseau fourni par le
 * serveur est invalide, tandis que les calculs d'interface et de planification
 * ont besoin d'un repli déterministe.
 */
fun resolveZoneOrUtc(timezone: String?): ZoneId =
    timezone
        ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        ?: ZoneId.of("UTC")

/** Date civile de cet instant dans le fuseau métier demandé. */
fun Instant.localDateIn(timezone: String?): LocalDate =
    atZone(resolveZoneOrUtc(timezone)).toLocalDate()

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
