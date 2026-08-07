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
    validZoneOrNull(timezone) ?: ZoneId.of("UTC")

/** Retourne un fuseau IANA valide ou null, sans accepter la pseudo-valeur API `auto`. */
fun validZoneOrNull(timezone: String?): ZoneId? =
    timezone
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { ZoneId.of(it) }.getOrNull() }

/** Valeur à envoyer à Open-Meteo : fuseau IANA valide, sinon résolution serveur `auto`. */
fun apiTimezoneOrAuto(timezone: String?): String =
    validZoneOrNull(timezone)?.id ?: "auto"

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
 * Parse une timeline locale Open-Meteo sans écraser l'heure répétée lors du
 * passage à l'heure d'hiver.
 *
 * Une chaîne ISO locale ne porte pas son offset. Quand les règles du fuseau
 * proposent deux offsets (par ex. deux occurrences de 02:00), on choisit le
 * premier instant strictement postérieur au précédent. Ainsi une suite
 * `01:00, 02:00, 02:00, 03:00` reste strictement chronologique. Une heure
 * locale inexistante pendant le saut de printemps est rejetée (`null`) au lieu
 * d'être silencieusement décalée par `LocalDateTime.atZone()`.
 */
fun parseOpenMeteoTimeline(times: List<String>, timezone: String): List<Instant?> {
    val zone = runCatching { ZoneId.of(timezone) }.getOrNull()
        ?: return List(times.size) { null }
    var previous: Instant? = null

    return times.map { raw ->
        val local = runCatching { LocalDateTime.parse(raw, OPEN_METEO_HOURLY_FORMAT) }
            .getOrNull() ?: return@map null
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) return@map null

        val candidates = offsets
            .map { offset -> local.atOffset(offset).toInstant() }
            .sorted()
        val chosen = previous
            ?.let { last -> candidates.firstOrNull { it > last } }
            ?: candidates.firstOrNull()
        if (chosen != null) previous = chosen
        chosen
    }
}

/**
 * Parse une date Open-Meteo (`yyyy-MM-dd`) en [LocalDate].
 */
fun parseOpenMeteoDate(date: String): LocalDate? = try {
    LocalDate.parse(date)
} catch (e: DateTimeParseException) {
    null
}
