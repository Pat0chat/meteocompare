package com.meteocompare.app.domain.model

/**
 * Métadonnées Consensus v2 pour les précipitations.
 *
 * La probabilité d'occurrence et la quantité conditionnelle sont volontairement
 * séparées. La valeur centrale déterministe vaut [centralAmountMm] : 0 lorsque
 * P(pluie) < 50 %, sinon la médiane pondérée des scénarios humides.
 */
data class PrecipitationConsensusMeta(
    val probabilityPercent: Int? = null,
    val conditionalAmountMm: Double? = null,
    val expectedAmountMm: Double? = null,
    val centralAmountMm: Double? = null,
    val familyCount: Int = 0
)

/**
 * Convergence des modèles sur les précipitations.
 *
 * Le type qualitatif conserve la compatibilité de l'UI historique, mais
 * [meta] expose désormais P(pluie), mm si pluie et espérance séparément.
 */
sealed interface PrecipitationConfidence {
    val percent: Int
    val modelCount: Int
    val meta: PrecipitationConsensusMeta

    data class NoRain(
        override val percent: Int,
        override val modelCount: Int,
        val maxAmountMm: Double,
        override val meta: PrecipitationConsensusMeta = PrecipitationConsensusMeta()
    ) : PrecipitationConfidence

    data class Rain(
        override val percent: Int,
        override val modelCount: Int,
        val minMm: Double,
        val maxMm: Double,
        /** Nom conservé pour compatibilité ; il s'agit désormais de la médiane conditionnelle pondérée. */
        val meanMm: Double,
        override val meta: PrecipitationConsensusMeta = PrecipitationConsensusMeta()
    ) : PrecipitationConfidence

    data class Divided(
        override val percent: Int,
        override val modelCount: Int,
        val modelsForRain: Int,
        val modelsAgainstRain: Int,
        val rainMinMm: Double,
        val rainMaxMm: Double,
        /** Nom conservé pour compatibilité ; il s'agit désormais de la médiane conditionnelle pondérée. */
        val rainMeanMm: Double,
        override val meta: PrecipitationConsensusMeta = PrecipitationConsensusMeta()
    ) : PrecipitationConfidence

    companion object {
        /** Seuil journalier Consensus v2 : >= 1 mm = scénario humide. */
        const val PRECIP_THRESHOLD_MM = 1.0
    }
}
