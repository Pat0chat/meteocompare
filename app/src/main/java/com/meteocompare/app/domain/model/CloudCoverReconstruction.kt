package com.meteocompare.app.domain.model

import kotlin.math.roundToInt

/**
 * Reconstitution *approximative* de la nébulosité totale quand l'API omet
 * cette variable, mais fournit les TROIS couches au même instant.
 *
 * Les couches ne s'additionnent pas physiquement : cette combinaison est
 * uniquement le repli historique de MeteoCompare, et non une observation WMO.
 * Aucune couche absente ou invalide n'est remplacée par 0 %.
 */
object CloudCoverReconstruction {
    fun resolve(total: Int?, low: Int?, mid: Int?, high: Int?): Int? {
        total?.takeIf { it in 0..100 }?.let { return it }
        val l = low?.takeIf { it in 0..100 } ?: return null
        val m = mid?.takeIf { it in 0..100 } ?: return null
        val h = high?.takeIf { it in 0..100 } ?: return null
        return (l * 0.9 + m * 0.6 + h * 0.3).coerceIn(0.0, 100.0).roundToInt()
    }
}

/** Point d'entrée partagé : DTO récents et anciens caches utilisent le même calcul. */
fun HourlyForecast.effectiveCloudCover(index: Int): Int? =
    CloudCoverReconstruction.resolve(
        cloudCover.getOrNull(index), cloudCoverLow.getOrNull(index),
        cloudCoverMid.getOrNull(index), cloudCoverHigh.getOrNull(index)
    )
