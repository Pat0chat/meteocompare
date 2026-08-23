package com.meteocompare.app.domain.model

/**
 * Indice de confiance calculé à partir de la convergence des modèles
 * sur une variable continue (température, vent…).
 *
 * @property percent Champ historique non nullable. En production il vaut le score de
 *                   convergence lorsqu'il est calculable, sinon 0 ; l'UI doit utiliser
 *                   [convergencePercent] pour distinguer « faible » de « non calculable ».
 * @property minValue Valeur minimale prévue par l'ensemble des modèles.
 * @property maxValue Valeur maximale prévue par l'ensemble des modèles.
 * @property meanValue Nom historique : contient désormais la centrale robuste du consensus.
 * @property stdDev Écart-type pondéré (base du calcul de [convergencePercent]).
 * @property modelCount Nombre de modèles ayant contribué au calcul.
 */
data class ConfidenceScore(
    val percent: Int,
    val minValue: Double,
    val maxValue: Double,
    val meanValue: Double,
    val stdDev: Double,
    val modelCount: Int,
    /** Nombre de lignées numériques indépendantes ayant réellement contribué. */
    val familyCount: Int = modelCount,
    /** Convergence instantanée ; null si une seule lignée indépendante contribue. */
    val convergencePercent: Int? = percent
) {
    /** Alias explicite pour le moteur consensus robuste. */
    val centralValue: Double get() = meanValue

    /** Range visible affichable à l'utilisateur : `maxValue - minValue`. */
    val spread: Double get() = maxValue - minValue

    val level: ConfidenceLevel
        get() = when {
            (convergencePercent ?: percent) >= 80 -> ConfidenceLevel.HIGH
            (convergencePercent ?: percent) >= 50 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }
