package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.saveable.Saver
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Mode d'affichage des tableaux et graphes de la page détail :
 *
 *   - [DAILY]  : granularité par jour sur ~7 jours. Vue synthétique historique,
 *                rapide à scanner "quel temps globalement cette semaine ?".
 *   - [HOURLY] : granularité par heure de "maintenant" jusqu'à la fin du
 *                lendemain (~24-48h). Sert quand on planifie une activité
 *                précise "à quelle heure va-t-il pleuvoir demain ?".
 *
 * Le mode par défaut est [DAILY] — c'est la vue historique et la plus adaptée
 * à un scan rapide. Le mode par heure est un opt-in explicite via le toggle
 * segmenté sous la TodaySummaryCard.
 */
enum class DisplayMode {
    HOURLY,
    DAILY;

    companion object {
        /**
         * Saver pour rememberSaveable — sérialise via [name] plutôt qu'ordinal,
         * pour que la restauration reste correcte si on réordonne l'enum plus
         * tard (ordinal 0/1 signifie "case 0/1", pas HOURLY/DAILY).
         */
        val Saver: Saver<DisplayMode, String> = Saver(
            save = { it.name },
            restore = { runCatching { valueOf(it) }.getOrDefault(DAILY) }
        )
    }
}

/**
 * Fenêtre horaire à afficher en mode "par heure" : de l'heure courante
 * (arrondie à l'heure pleine dans le fuseau de la ville) jusqu'à la fin du
 * lendemain (exclu : début du sur-lendemain).
 *
 * Filtrer avec `timestamp >= start && timestamp < endExclusive`.
 *
 * Le fuseau de la ville est essentiel — un utilisateur à Paris consultant la
 * météo de Sydney veut voir "les prochaines 24-48h à Sydney", pas dans son
 * propre fuseau (ce qui décalerait les heures affichées vs le reste des
 * écrans — TodaySummaryCard, bande de confiance — qui utilisent le fuseau
 * de la ville).
 *
 * Si le timezone est invalide ou null, on retombe silencieusement sur UTC —
 * la fenêtre reste cohérente en interne même si elle est un peu décalée en
 * apparence. Priorité : ne jamais crasher pour un timezone mal formé.
 */
internal fun computeHourlyHorizon(timezone: String?): Pair<Instant, Instant> {
    val zone = runCatching { ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
    val nowLocal = ZonedDateTime.now(zone)
    val startHour = nowLocal
        .withMinute(0).withSecond(0).withNano(0)
        .toInstant()
    // "jusqu'à la journée suivante" = fin du lendemain inclus. On prend le
    // début du sur-lendemain (à minuit locale) comme borne exclusive.
    val endExclusive = nowLocal
        .toLocalDate()
        .plusDays(2)
        .atStartOfDay(zone)
        .toInstant()
    return startHour to endExclusive
}
