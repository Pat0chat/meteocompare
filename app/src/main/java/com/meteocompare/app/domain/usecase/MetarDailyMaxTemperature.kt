package com.meteocompare.app.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Tmax journalière d'une station à partir de ses METAR horaires (CSV IEM
 * `station,valid,tmpc,…`, heures en UTC).
 *
 * Même définition que côté prévision ([BootstrapBiasHistoryUseCase]) : le
 * maximum des valeurs horaires sur la journée civile locale. Portage exact de
 * `metar_daily` du banc d'essai (`scripts/backtest/dataset.py`) :
 *   - un relevé par heure locale, le plus proche de l'heure pleine (à égalité,
 *     le premier lu) ;
 *   - température hors de [-70, 60] °C ignorée ;
 *   - journée retenue seulement avec au moins [MIN_OBSERVED_HOURS] heures.
 *
 * Les maxima officiels SYNOP couvrent d'autres fenêtres horaires et ne sont
 * volontairement pas utilisés.
 */
object MetarDailyMaxTemperature {
    const val MIN_OBSERVED_HOURS = 20
    private val VALID_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private data class Slot(val offsetMinutes: Int, val temperature: Double?)

    fun parse(csv: String, zone: ZoneId): Map<LocalDate, Double> {
        val lines = csv.lineSequence().map(String::trim).filter(String::isNotEmpty).iterator()
        if (!lines.hasNext()) return emptyMap()
        val header = lines.next().split(',')
        val validIndex = header.indexOf("valid")
        val tempIndex = header.indexOf("tmpc")
        // Réponse non CSV (limite de débit, page d'erreur) : aucune observation.
        if (validIndex < 0 || tempIndex < 0) return emptyMap()

        val best = HashMap<Instant, Slot>()
        val order = ArrayList<Instant>()
        lines.forEach { line ->
            val cells = line.split(',')
            val utc = runCatching {
                LocalDateTime.parse(cells.getOrNull(validIndex), VALID_FORMAT).toInstant(ZoneOffset.UTC)
            }.getOrNull() ?: return@forEach
            val local = utc.atZone(zone)
            val minute = local.minute
            var hour = local.truncatedTo(ChronoUnit.HOURS)
            if (minute > 30) hour = hour.plusHours(1)
            val offset = if (minute <= 30) minute else 60 - minute
            val temperature = cells.getOrNull(tempIndex)?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in -70.0..60.0 }
            val key = hour.toInstant()
            val current = best[key]
            if (current == null) order += key
            if (current == null || offset < current.offsetMinutes) best[key] = Slot(offset, temperature)
        }

        val byDay = LinkedHashMap<LocalDate, MutableList<Double>>()
        order.forEach { key ->
            val temperature = best.getValue(key).temperature ?: return@forEach
            byDay.getOrPut(key.atZone(zone).toLocalDate(), ::mutableListOf) += temperature
        }
        return byDay
            .filterValues { it.size >= MIN_OBSERVED_HOURS }
            .mapValues { (_, values) -> values.max() }
    }
}
