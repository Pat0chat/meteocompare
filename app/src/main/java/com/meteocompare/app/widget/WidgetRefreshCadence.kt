package com.meteocompare.app.widget

import java.util.concurrent.TimeUnit

/**
 * Cadence effective des reconstructions RemoteViews.
 *
 * Le worker WorkManager conserve son tick de sécurité à 15 minutes et chaque
 * tick reconstruit Glance. Le seuil de fetch réseau reste, lui, appliqué dans
 * `loadWidgetData` : avancer l'heure affichée ne signifie donc pas télécharger
 * à nouveau les prévisions.
 *
 * Cette séparation est volontaire : l'ancienne optimisation espaçait aussi le
 * rendu à 1 h lorsque le réseau était réglé sur 1/3/6 h ou MANUAL. Un worker
 * légèrement retardé par le constructeur pouvait alors laisser une échéance
 * visible figée pendant près de deux heures.
 */
internal fun widgetDispatchIntervalMs(): Long = TimeUnit.MINUTES.toMillis(15)

/**
 * Compare des buckets alignés sur l'horloge plutôt qu'un simple delta.
 * Ainsi un rendu forcé à 14:59 n'empêche pas le passage à la colonne 15 h à
 * 15:01. Un recul d'horloge force aussi une reconstruction de sécurité.
 */
internal fun isWidgetDispatchDue(
    lastDispatchAtMs: Long,
    nowMs: Long,
    force: Boolean
): Boolean {
    if (force || lastDispatchAtMs <= 0L || nowMs < lastDispatchAtMs) return true
    val cadence = widgetDispatchIntervalMs()
    return nowMs / cadence > lastDispatchAtMs / cadence
}
