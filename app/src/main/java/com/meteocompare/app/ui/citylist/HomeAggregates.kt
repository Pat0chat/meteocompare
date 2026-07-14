package com.meteocompare.app.ui.citylist

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Agrégats spécifiques à la home enrichie (mini-forecast 12h par card).
 *
 * ─── Pourquoi un fichier séparé de ConfidenceCalculator ────────────────────
 * Les méthodes ci-dessous suivent le même schéma d'agrégation multi-modèles
 * que `ConfidenceCalculator.currentTemperature`, mais elles sont
 * spécialisées pour le case UI "12 prochaines heures". Les mettre dans
 * ConfidenceCalculator ferait grossir un fichier déjà à ~700 lignes sans
 * qu'aucun autre écran ne les utilise. Ici c'est co-localisé avec les
 * composables consommateurs.
 *
 * On ne pondère pas par [ModelWeightingStrategy] (comme le fait
 * ConfidenceCalculator) — un simple average non-pondéré suffit pour un
 * mini-graphe informatif. La pondération par résolution est visible sur
 * les tableaux détaillés ; en home, on optimise pour la simplicité et la
 * vitesse de rendu.
 *
 * ─── Pourquoi pas dans le ViewModel ────────────────────────────────────────
 * Fonction pure sans état → plus testable en JVM brut, et réutilisable si un
 * autre écran (widget, notification) en a besoin. Le ViewModel n'a rien à
 * faire de plus que la déléguer.
 */
internal object HomeAggregates {

    /**
     * Agrège les températures des 12 prochaines heures à partir de maintenant,
     * en faisant la moyenne non-pondérée des modèles disponibles pour chaque
     * heure.
     *
     * @param forecast prévision multi-modèles pour la ville.
     * @param now instant de référence — injectable pour les tests. Défaut =
     *   `Instant.now()`.
     * @return liste de 12 éléments, `null` pour les heures où aucun modèle ne
     *   fournit de valeur (typique de la fin de fenêtre pour AROME HD).
     */
    fun next12hTemperatures(
        forecast: CityForecast,
        now: Instant = Instant.now()
    ): List<Double?> = aggregateNext12h(forecast, now) { series, idx ->
        series.hourly.temperature2m.getOrNull(idx)
    }

    /**
     * Agrège les probabilités de précipitation des 12 prochaines heures.
     *
     * @return liste de 12 entiers [0, 100], `null` pour les heures sans donnée.
     */
    fun next12hPrecipProbability(
        forecast: CityForecast,
        now: Instant = Instant.now()
    ): List<Int?> = aggregateNext12h(forecast, now) { series, idx ->
        series.hourly.precipitationProbability.getOrNull(idx)?.toDouble()
    }.map { it?.roundToInt() }

    /**
     * Squelette d'agrégation partagé. Pour chaque heure H+0 à H+11, on cherche
     * l'index correspondant dans les séries horaires de chaque modèle, on
     * extrait la valeur via [extractor], et on moyenne.
     *
     * Contrat de l'index : on prend la timestamp la PLUS PROCHE du timestamp
     * cible (H+i), pas nécessairement celle qui vaut exactement H+i. Ça gère
     * les modèles dont les issues horaires ne tombent pas parfaitement sur
     * l'heure locale (AROME HD publie par ex. sur des slots 03/09/15/21).
     *
     * Si l'écart entre timestamp trouvée et cible est > 30 min, on considère
     * que le modèle n'a pas de valeur utile pour cette heure et on l'écarte.
     */
    private inline fun aggregateNext12h(
        forecast: CityForecast,
        now: Instant,
        extractor: (com.meteocompare.app.domain.model.ForecastSeries, Int) -> Double?
    ): List<Double?> {
        val result = mutableListOf<Double?>()
        for (h in 0 until 12) {
            val target = now.plusSeconds(h * 3600L)
            val values = forecast.seriesByModel.mapNotNull { (_, series) ->
                if (series.hourly.timestamps.isEmpty()) return@mapNotNull null
                val idx = series.hourly.timestamps.indices.minBy { i ->
                    kotlin.math.abs(
                        series.hourly.timestamps[i].epochSecond - target.epochSecond
                    )
                }
                val timestampAtIdx = series.hourly.timestamps[idx]
                val deltaMin = kotlin.math.abs(
                    timestampAtIdx.epochSecond - target.epochSecond
                ) / 60
                // Rejet si écart > 30 min — on n'invente pas des interpolations
                // sur des données absentes.
                if (deltaMin > 30) return@mapNotNull null
                extractor(series, idx)
            }
            result.add(if (values.isEmpty()) null else values.average())
        }
        return result
    }

    // Rendu accessible pour un test unitaire hypothétique — le paramètre
    // `models` n'est pas utilisé ici, mais l'exposer permet à un caller de
    // filtrer les modèles s'il veut la home limitée à un subset (feature v1.1
    // envisagée : "n'inclure que les modèles pertinents pour ma région").
    @Suppress("unused")
    fun availableModelsForCity(forecast: CityForecast): List<WeatherModel> =
        forecast.seriesByModel.keys.toList()
}
