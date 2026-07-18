package com.meteocompare.app.domain.model

import java.time.Instant

/**
 * Agrégat des prévisions multi-modèles pour une ville.
 *
 * @property city Ville concernée.
 * @property seriesByModel Prévisions par modèle ayant répondu avec succès.
 * @property errors Modèles ayant échoué avec leur message d'erreur.
 *                  Permet à l'UI d'afficher "ARPEGE: indisponible" sans
 *                  bloquer l'affichage du reste.
 * @property fetchedAt Horodatage de fraîcheur du lot. Pour un résultat
 *                     réseau, tous les modèles partagent le même instant ;
 *                     pour un lot relu du cache, c'est l'instant du modèle le
 *                     plus ancien afin de ne jamais masquer une série périmée.
 *                     Null quand la donnée provient d'un cache pré-feature
 *                     (pas de garantie côté migration) ou dans les tests qui
 *                     construisent le modèle à la main. L'UI (TodaySummaryCard,
 *                     CityCard) affiche un caption "mis à jour il y a X" quand
 *                     non-null, et cache le caption sinon.
 */
data class CityForecast(
    val city: City,
    val seriesByModel: Map<WeatherModel, ForecastSeries>,
    val errors: Map<WeatherModel, String> = emptyMap(),
    val fetchedAt: Instant? = null
) {
    /** Modèles disponibles, triés par résolution (du plus fin au plus grossier). */
    val availableModels: List<WeatherModel>
        get() = seriesByModel.keys.sortedBy { it.resolutionKm }
}
