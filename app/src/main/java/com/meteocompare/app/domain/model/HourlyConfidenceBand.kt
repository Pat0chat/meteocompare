package com.meteocompare.app.domain.model

import java.time.Instant

/**
 * Bande de confiance horaire pour la température.
 *
 * Chaque instance représente UN instant temporel avec les statistiques calculées
 * sur tous les modèles ayant des données pour cet instant.
 *
 * Visualisation cible (cf. HourlyConfidenceChart) :
 *
 *   T° ▲      ┌────╮        ╱────╲
 *      │ ┌────╯    ╰────╮  ╱      ╲      ← max (limite haute de la bande)
 *      │ │ ░░░░░░░░░░░░░╲░╱░░░░░░░░░╲    ← mean (ligne pleine)
 *      │ │░░░░░░░░░░░░░░░╳░░░░░░░░░░░╲
 *      │ └────╮    ╭────╯ ╲          ╲   ← min (limite basse de la bande)
 *      └──────┴────┴───────┴─────────► t
 *        J+0   J+1   J+2    J+3       J+7
 *
 * Au début, tous les modèles sont disponibles et convergent → bande étroite.
 * À mesure qu'on avance dans l'horizon, les modèles haute-résolution (AROME)
 * disparaissent et les écarts grandissent → bande qui s'élargit.
 *
 * @property modelCount Nombre de modèles ayant contribué (varie selon l'horizon).
 */
data class HourlyConfidenceBand(
    val timestamp: Instant,
    val meanValue: Double,
    val minValue: Double,
    val maxValue: Double,
    val stdDev: Double,
    val percent: Int,
    val modelCount: Int
) {
    val spread: Double get() = maxValue - minValue
}
