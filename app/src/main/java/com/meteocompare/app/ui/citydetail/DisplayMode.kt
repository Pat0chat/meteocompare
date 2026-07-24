package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.saveable.Saver
import java.time.Instant
import java.time.ZoneId

/**
 * Mode d'affichage des tableaux et graphes de la page détail :
 *
 *   - [DAILY]  : granularité par jour sur ~7 jours. Vue synthétique historique,
 *                rapide à scanner "quel temps globalement cette semaine ?".
 *   - [HOURLY] : granularité par heure de "maintenant" jusqu'à la fin de la
 *                journée en cours (0-24h). Sert quand on planifie une activité
 *                précise "à quelle heure va-t-il pleuvoir aujourd'hui ?".
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
 * (arrondie à l'heure pleine dans le fuseau de la ville) jusqu'à la fin de la
 * journée en cours (exclu : début du lendemain, à minuit locale).
 *
 * Filtrer avec `timestamp >= start && timestamp < endExclusive`.
 *
 * Trade-off : borner à la fin du jour courant plutôt qu'à J+1 limite la table
 * à 24 lignes MAX (à 00:00) — souvent bien moins (à 18:00 il ne reste que 6
 * heures). C'est un compromis délibéré : on préfère une vue compacte sur les
 * prochaines heures utiles qu'un tableau interminable qui noie l'utilisateur.
 * Pour voir plus loin, on repasse en mode daily.
 *
 * Le fuseau de la ville est essentiel — un utilisateur à Paris consultant la
 * météo de Sydney veut voir "les heures qui restent à Sydney aujourd'hui",
 * pas dans son propre fuseau (ce qui décalerait les heures affichées vs le
 * reste des écrans — TodaySummaryCard, bande de confiance — qui utilisent le
 * fuseau de la ville).
 *
 * Si le timezone est invalide ou null, on retombe silencieusement sur UTC —
 * la fenêtre reste cohérente en interne même si elle est un peu décalée en
 * apparence. Priorité : ne jamais crasher pour un timezone mal formé.
 */
internal fun resolveCityZone(timezone: String?): ZoneId =
    runCatching { ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))

internal fun computeHourlyHorizon(
    timezone: String?,
    now: Instant = Instant.now()
): Pair<Instant, Instant> {
    val zone = resolveCityZone(timezone)
    val nowLocal = now.atZone(zone)
    val startHour = nowLocal
        .withMinute(0).withSecond(0).withNano(0)
        .toInstant()
    // "jusqu'à la fin de la journée en cours" = minuit locale du jour suivant
    // comme borne exclusive supérieure. Avec `plusDays(1)` sur la date courante,
    // on obtient le début du lendemain — c'est la 1re heure du jour d'après,
    // exclue par le filtre `< endExclusive`, donc la dernière heure affichée
    // est 23h du jour courant.
    val endExclusive = nowLocal
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
    return startHour to endExclusive
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
