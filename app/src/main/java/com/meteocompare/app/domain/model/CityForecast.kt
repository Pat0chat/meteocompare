package com.meteocompare.app.domain.model

/**
 * Agrégat des prévisions multi-modèles pour une ville.
 *
 * @property city Ville concernée.
 * @property seriesByModel Prévisions par modèle ayant répondu avec succès.
 * @property errors Modèles ayant échoué avec leur message d'erreur.
 *                  Permet à l'UI d'afficher "ARPEGE: indisponible" sans
 *                  bloquer l'affichage du reste.
 */
data class CityForecast(
    val city: City,
    val seriesByModel: Map<WeatherModel, ForecastSeries>,
    val errors: Map<WeatherModel, String> = emptyMap()
) {
    /** True si au moins un modèle a répondu. */
    val hasData: Boolean get() = seriesByModel.isNotEmpty()

    /** Modèles disponibles, triés par résolution (du plus fin au plus grossier). */
    val availableModels: List<WeatherModel>
        get() = seriesByModel.keys.sortedBy { it.resolutionKm }
}
