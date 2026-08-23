package com.meteocompare.app.domain.model

/**
 * Source de vérité unique des seuils utilisés pour qualifier les révisions
 * run-to-run et pour les représenter dans l'UI.
 *
 * Ces valeurs sont des seuils de lisibilité métier, pas des probabilités.
 */
object ForecastEvolutionThresholds {
    fun stable(variable: ForecastEvolutionVariable): Double = when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> 0.5
        ForecastEvolutionVariable.PRECIPITATION -> 1.0
        ForecastEvolutionVariable.WIND -> 3.0
    }

    fun notable(variable: ForecastEvolutionVariable): Double = when (variable) {
        ForecastEvolutionVariable.TEMPERATURE -> 1.0
        ForecastEvolutionVariable.PRECIPITATION -> 2.0
        ForecastEvolutionVariable.WIND -> 5.0
    }
}
