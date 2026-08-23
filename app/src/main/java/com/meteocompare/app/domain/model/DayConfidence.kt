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
    val windMax: ConfidenceScore?,
    /** Rafales maximales à 10 m sur la journée, agrégées entre modèles. */
    val windGustMax: ConfidenceScore? = null
) {
    /**
     * Score global de la journée — moyenne arithmétique des confidences disponibles.
     * Utile pour trier "jours les plus prévisibles" ou afficher un badge global.
     * Les rafales restent une information complémentaire du vent et ne modifient
     * pas ce score historique, afin de ne pas changer sa sémantique produit.
     */
    val overallPercent: Int?
        get() {
            val scores = listOfNotNull(
                tempMax?.convergencePercent,
                tempMin?.convergencePercent,
                precipitation?.convergencePercent,
                windMax?.convergencePercent
            )
            return if (scores.isEmpty()) null else scores.average().toInt()
        }

    /** Nom explicite consensus robuste : dispersion actuelle, pas probabilité de justesse. */
    val convergencePercent: Int? get() = overallPercent

}
