package com.meteocompare.app.domain.model

/**
 * Confiance sur les précipitations — modélisée comme sealed class car
 * la pluie n'est pas qu'une variable continue : il y a un saut qualitatif
 * entre "il ne pleut pas" et "il pleut", indépendamment de la quantité.
 *
 * Trois cas :
 *
 * 1. [NoRain] — tous les modèles s'accordent sur l'absence de pluie significative
 *    (< [PRECIP_THRESHOLD_MM]). C'est l'agreement le plus simple.
 *
 * 2. [Rain] — tous les modèles annoncent de la pluie. La confiance dépend
 *    alors du spread sur la quantité (mm).
 *
 * 3. [Divided] — les modèles ne s'accordent pas sur l'occurrence elle-même.
 *    C'est le cas le plus incertain : 3/5 annoncent pluie, 2/5 sec.
 */
sealed interface PrecipitationConfidence {
    val percent: Int
    val modelCount: Int

    data class NoRain(
        override val percent: Int,
        override val modelCount: Int,
        val maxAmountMm: Double
    ) : PrecipitationConfidence

    data class Rain(
        override val percent: Int,
        override val modelCount: Int,
        val minMm: Double,
        val maxMm: Double,
        val meanMm: Double
    ) : PrecipitationConfidence

    data class Divided(
        override val percent: Int,
        override val modelCount: Int,
        val modelsForRain: Int,
        val modelsAgainstRain: Int
    ) : PrecipitationConfidence

    companion object {
        /** Seuil journalier : > 1 mm cumulé sur la journée = "il a plu". */
        const val PRECIP_THRESHOLD_MM = 1.0
    }
}
