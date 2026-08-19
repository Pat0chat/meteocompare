package com.meteocompare.app.ui.citydetail

import androidx.compose.runtime.saveable.Saver

/**
 * Métrique visualisée par la bande de convergence horaire.
 *
 *   - [TEMPERATURE] : température à 2m en °C. Métrique historique et défaut
 *     de l'app — c'est celle qui a toujours été rendue avant l'ajout du
 *     sélecteur à 3 états.
 *   - [PRECIPITATION] : précipitations en mm sur l’heure. La bande représente
 *     l'amplitude min/max des modèles ; le trait pointillé "repère historique 10 ans"
 *     est aplati car les repères historiques sont journalières.
 *   - [WIND] : vent moyen à 10m en km/h (pas les rafales — cf. section_wind).
 *
 * Le [Saver] permet de survivre à la rotation et au dark-mode toggle. On
 * sérialise via `name` plutôt qu'ordinal pour rester robuste à une future
 * réorganisation de l'enum — sinon un vieux Bundle contenant "0" pourrait
 * pointer sur PRECIPITATION après un swap.
 */
enum class ConfidenceMetric {
    TEMPERATURE,
    PRECIPITATION,
    WIND;

    companion object {
        val Saver: Saver<ConfidenceMetric, String> = Saver(
            save = { it.name },
            restore = { runCatching { valueOf(it) }.getOrDefault(TEMPERATURE) }
        )
    }
}
