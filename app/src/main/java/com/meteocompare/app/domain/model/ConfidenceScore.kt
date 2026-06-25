package com.meteocompare.app.domain.model

/**
 * Indice de confiance calculé à partir de la convergence des modèles
 * sur une variable continue (température, vent…).
 *
 * @property percent Score 0-100. Plus c'est élevé, plus les modèles convergent.
 * @property minValue Valeur minimale prévue par l'ensemble des modèles.
 * @property maxValue Valeur maximale prévue par l'ensemble des modèles.
 * @property meanValue Moyenne pondérée par la stratégie de pondération.
 * @property stdDev Écart-type pondéré (base du calcul de [percent]).
 * @property modelCount Nombre de modèles ayant contribué au calcul.
 */
data class ConfidenceScore(
    val percent: Int,
    val minValue: Double,
    val maxValue: Double,
    val meanValue: Double,
    val stdDev: Double,
    val modelCount: Int
) {
    /** Range visible affichable à l'utilisateur : `maxValue - minValue`. */
    val spread: Double get() = maxValue - minValue

    val level: ConfidenceLevel
        get() = when {
            percent >= 80 -> ConfidenceLevel.HIGH
            percent >= 50 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

enum class ConfidenceLevel { HIGH, MEDIUM, LOW }
