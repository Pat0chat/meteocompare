package com.meteocompare.app.widget

import com.meteocompare.app.domain.model.RefreshInterval
import java.util.concurrent.TimeUnit

/**
 * Cadence effective des reconstructions RemoteViews.
 *
 * Le worker WorkManager conserve son tick de sécurité à 15 minutes, mais une
 * reconstruction Glance complète n'est utile que lorsque :
 *  - le seuil réseau utilisateur est atteint, ou
 *  - l'heure affichée peut changer.
 *
 * Le plafond d'une heure garantit que les widgets continuent d'avancer même
 * avec un intervalle réseau de 3 h, 6 h ou MANUAL. Le plancher de 15 minutes
 * respecte le choix le plus frais exposé dans Settings.
 */
internal fun widgetDispatchIntervalMs(interval: RefreshInterval): Long {
    val displayInterval = TimeUnit.HOURS.toMillis(1)
    val requested = if (interval == RefreshInterval.MANUAL) {
        displayInterval
    } else {
        interval.millis
    }
    return requested
        .coerceAtMost(displayInterval)
        .coerceAtLeast(TimeUnit.MINUTES.toMillis(15))
}

/**
 * Compare des buckets alignés sur l'horloge plutôt qu'un simple delta.
 * Ainsi un rendu forcé à 14:59 n'empêche pas le passage à la colonne 15 h à
 * 15:01. Un recul d'horloge force aussi une reconstruction de sécurité.
 */
internal fun isWidgetDispatchDue(
    lastDispatchAtMs: Long,
    nowMs: Long,
    interval: RefreshInterval,
    force: Boolean
): Boolean {
    if (force || lastDispatchAtMs <= 0L || nowMs < lastDispatchAtMs) return true
    val cadence = widgetDispatchIntervalMs(interval)
    return nowMs / cadence > lastDispatchAtMs / cadence
}
