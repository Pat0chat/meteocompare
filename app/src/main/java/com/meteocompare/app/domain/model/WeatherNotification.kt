package com.meteocompare.app.domain.model

import java.time.LocalDate

/**
 * Contenu d'une notification météo, indépendant d'Android.
 *
 * [dedupKey] identifie l'événement notifié : un même événement (même ville,
 * même jour cible, même nature) n'est présenté qu'une fois, même si le worker
 * s'exécute plusieurs fois.
 */
sealed interface WeatherNotification {
    val city: City
    val dedupKey: String

    /**
     * Résumé d'une journée selon le moteur de prévision choisi par l'utilisateur.
     *
     * @property isToday Vrai pour la journée en cours, faux pour le lendemain.
     * @property convergencePercent Convergence des modèles (dispersion actuelle,
     *           pas une probabilité de justesse), null si non calculable.
     */
    data class DailySummary(
        override val city: City,
        val date: LocalDate,
        val isToday: Boolean,
        val condition: WeatherCondition?,
        val tempMin: Double?,
        val tempMax: Double?,
        val precipitationProbabilityPercent: Int?,
        val precipitationAmountMm: Double?,
        val windKmh: Double?,
        val convergencePercent: Int?
    ) : WeatherNotification {
        override val dedupKey: String get() = "daily|${city.id}|$date"
    }

    /** Les modèles divergent nettement sur une journée proche. */
    data class ModelDivergence(
        override val city: City,
        val date: LocalDate,
        val isToday: Boolean,
        val convergencePercent: Int
    ) : WeatherNotification {
        override val dedupKey: String get() = "divergence|${city.id}|$date"
    }

    /** La prévision consensus a été nettement révisée depuis un snapshot local antérieur. */
    data class ForecastChange(
        override val city: City,
        val highlight: ForecastEvolutionHighlight
    ) : WeatherNotification {
        override val dedupKey: String
            get() = "change|${city.id}|${highlight.targetDate}|${highlight.variable}|${highlight.trend}"
    }
}
