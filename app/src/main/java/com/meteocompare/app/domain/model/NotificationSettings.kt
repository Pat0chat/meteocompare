package com.meteocompare.app.domain.model

import java.time.LocalTime

/**
 * Préférences des notifications météo locales.
 *
 * Tout est calculé sur l'appareil, à partir des mêmes prévisions et du même
 * moteur que l'application : aucun serveur de push, aucun compte, aucune
 * donnée supplémentaire envoyée. Tout est désactivé par défaut.
 *
 * @property dailySummaryEnabled Résumé quotidien à heure fixe.
 * @property dailySummaryTime Heure locale (fuseau de l'appareil) du résumé.
 * @property divergenceAlertsEnabled Alerte quand les modèles divergent nettement
 *           sur aujourd'hui ou demain (convergence faible).
 * @property forecastChangeAlertsEnabled Alerte quand la prévision consensus est
 *           nettement révisée entre deux actualisations (suivi d'évolution local).
 * @property cityIds Villes favorites suivies. Une ville retirée des favoris est
 *           purgée de cet ensemble lors de sa suppression.
 */
data class NotificationSettings(
    val dailySummaryEnabled: Boolean = false,
    val dailySummaryTime: LocalTime = DEFAULT_DAILY_SUMMARY_TIME,
    val divergenceAlertsEnabled: Boolean = false,
    val forecastChangeAlertsEnabled: Boolean = false,
    val cityIds: Set<String> = emptySet()
) {
    /** Vrai si au moins un type d'alerte (hors résumé) est actif. */
    val alertsEnabled: Boolean
        get() = divergenceAlertsEnabled || forecastChangeAlertsEnabled

    /** Vrai si au moins un type de notification est actif. */
    val anyEnabled: Boolean
        get() = dailySummaryEnabled || alertsEnabled

    /** Supprime les références à des villes qui ne sont plus favorites. */
    fun retainingCities(availableCityIds: Set<String>): NotificationSettings =
        copy(cityIds = cityIds intersect availableCityIds)

    companion object {
        val DEFAULT_DAILY_SUMMARY_TIME: LocalTime = LocalTime.of(7, 0)
    }
}
