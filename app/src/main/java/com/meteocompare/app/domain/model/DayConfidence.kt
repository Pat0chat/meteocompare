package com.meteocompare.app.domain.model

import java.time.LocalDate

/**
 * Bundle des confidences pour un jour donné — c'est l'unité d'affichage
 * principale pour le résumé d'une ville.
 *
 * Tous les champs sont nullables car aucun modèle n'a peut-être de données
 * pour ce jour (rare mais possible aux limites d'horizon).
 */
data class DayConfidence(
    val date: LocalDate,
    val tempMax: ConfidenceScore?,
    val tempMin: ConfidenceScore?,
    val precipitation: PrecipitationConfidence?,
    val windMax: ConfidenceScore?
) {
    /**
     * Score global de la journée — moyenne arithmétique des confidences disponibles.
     * Utile pour trier "jours les plus prévisibles" ou afficher un badge global.
     */
    val overallPercent: Int?
        get() {
            val scores = listOfNotNull(
                tempMax?.percent,
                tempMin?.percent,
                precipitation?.percent,
                windMax?.percent
            )
            return if (scores.isEmpty()) null else scores.average().toInt()
        }
}
