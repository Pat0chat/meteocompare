package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.WeatherModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Stratégie de pondération des modèles pour le calcul de confiance.
 *
 * Conçue comme une interface injectable pour permettre d'évoluer sans
 * casser le code appelant :
 *   - V1 : pondération par résolution (implémentée ici)
 *   - V2 envisagée : skill historique par variable + saison (need backtest data)
 *   - V3 envisagée : pondération adaptative selon zone géographique
 *     (AROME hors France n'a aucun sens)
 */
interface ModelWeightingStrategy {
    /**
     * Retourne un poids > 0 pour [model]. La valeur absolue importe peu —
     * seuls les ratios entre modèles comptent dans le calcul de moyenne pondérée.
     */
    fun weight(model: WeatherModel): Double
}

/**
 * Pondération par défaut : `1/√résolution`.
 *
 * Choix de la racine carrée (vs `1/résolution`) :
 *   - `1/résolution` donne AROME HD (1.5 km) ≈ 17× ECMWF (25 km), trop agressif.
 *     ECMWF IFS reste l'un des meilleurs modèles globaux malgré sa résolution.
 *   - `1/√résolution` donne AROME HD ≈ 4× ECMWF, plus aligné avec les skill scores
 *     observés en pratique.
 */
@Singleton
class InverseSqrtResolutionWeighting @Inject constructor() : ModelWeightingStrategy {
    override fun weight(model: WeatherModel): Double = 1.0 / sqrt(model.resolutionKm)
}

/** Pondération équitable — utile pour le mode "comparaison brute" et les tests. */
class EqualWeighting : ModelWeightingStrategy {
    override fun weight(model: WeatherModel): Double = 1.0
}
