package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.WeatherModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stratégie de pondération des modèles pour les agrégations inter-modèles.
 *
 * Le point d'extension reste injectable pour permettre un jour une pondération
 * fondée sur un backtest par variable, zone et échéance. En l'absence d'un tel
 * corpus vérifié, la production utilise des poids égaux : la résolution de
 * grille seule n'est pas une mesure de skill et ne justifie pas de favoriser
 * systématiquement un modèle.
 */
interface ModelWeightingStrategy {
    /** Retourne un poids strictement positif pour [model]. */
    fun weight(model: WeatherModel): Double
}

/**
 * Pondération équitable utilisée en production.
 *
 * Chaque modèle disponible compte une fois. Ce choix rend la moyenne et
 * l'indice d'accord interprétables comme une comparaison de scénarios, sans
 * prétendre qu'une maille plus fine est automatiquement plus juste.
 */
@Singleton
class EqualWeighting @Inject constructor() : ModelWeightingStrategy {
    override fun weight(model: WeatherModel): Double = 1.0
}
