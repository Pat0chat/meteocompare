package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Calculateur d'indice de confiance multi-modèles.
 *
 * Principe :
 *   - Pour chaque variable continue (température, vent), agrège les prédictions
 *     de tous les modèles disponibles pour un même instant/jour.
 *   - Calcule moyenne et écart-type **pondérés** par [ModelWeightingStrategy].
 *   - Convertit l'écart-type en pourcentage de confiance via des seuils calibrés
 *     par variable (cf. [Thresholds]).
 *   - Cas spécial pluie : agreement binaire + spread sur l'intensité.
 *
 * Conventions :
 *   - On utilise toujours les **séries journalières** (`daily`) pour les confidences,
 *     car c'est ce que l'UI affiche dans le résumé ville. L'extension aux séries
 *     horaires viendra avec les graphiques détaillés.
 *   - L'alignement entre modèles se fait **par date explicite** (pas par index),
 *     car AROME HD ne va que 2 jours quand GFS va 16 — les positions ne correspondent
 *     pas forcément aux mêmes jours.
 */
@Singleton
class ConfidenceCalculator @Inject constructor(
    private val weighting: ModelWeightingStrategy
) {

    /** Calcule le bundle de confidences pour [date]. */
    fun dayConfidence(forecast: CityForecast, date: LocalDate): DayConfidence {
        val modelsAtDate = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = series.daily.dates.indexOf(date)
            if (idx >= 0) Triple(model, series, idx) else null
        }

        return DayConfidence(
            date = date,
            tempMax = continuousConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.tempMax.getOrNull(idx)?.let { model to it }
                },
                thresholds = Thresholds.TEMPERATURE
            ),
            tempMin = continuousConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.tempMin.getOrNull(idx)?.let { model to it }
                },
                thresholds = Thresholds.TEMPERATURE
            ),
            windMax = continuousConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.windSpeedMax.getOrNull(idx)?.let { model to it }
                },
                thresholds = Thresholds.WIND
            ),
            precipitation = precipitationConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.precipitationSum.getOrNull(idx)?.let { model to it }
                }
            )
        )
    }

    /** Convenience : confidence par jour sur tout l'horizon disponible. */
    fun weeklyConfidence(forecast: CityForecast): List<DayConfidence> {
        val allDates = forecast.seriesByModel.values
            .flatMap { it.daily.dates }
            .distinct()
            .sorted()
        return allDates.map { dayConfidence(forecast, it) }
    }

    /**
     * Température "maintenant" — moyenne pondérée entre modèles de la valeur
     * horaire la plus proche de l'instant courant.
     *
     * Open-Meteo retourne typiquement les heures depuis 00:00 du jour. À 14:30,
     * l'heure 14:00 est dans le passé (1h) et 15:00 dans le futur (30min) — on
     * prend la plus proche en valeur absolue.
     *
     * Pondération identique aux autres calculs (1/√résolution par défaut) :
     * AROME HD pèse plus que GFS pour les localisations en France.
     *
     * Retourne null si aucun modèle n'a de donnée horaire disponible
     * (ne devrait jamais arriver en pratique sauf bug Open-Meteo).
     */
    fun currentTemperature(forecast: CityForecast): Double? {
        val now = Instant.now()
        val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
            if (series.hourly.timestamps.isEmpty()) return@mapNotNull null
            val idx = series.hourly.timestamps.indices.minBy { i ->
                kotlin.math.abs(series.hourly.timestamps[i].epochSecond - now.epochSecond)
            }
            val temp = series.hourly.temperature2m.getOrNull(idx) ?: return@mapNotNull null
            model to temp
        }
        if (samples.isEmpty()) return null
        val totalWeight = samples.sumOf { (model, _) -> weighting.weight(model) }
        if (totalWeight == 0.0) return null
        val weightedSum = samples.sumOf { (model, temp) -> temp * weighting.weight(model) }
        return weightedSum / totalWeight
    }

    /**
     * Condition météo "maintenant" — vote pondéré par résolution sur la famille
     * de code WMO la plus voisine de l'instant courant.
     *
     * Pourquoi un vote majoritaire et non une "moyenne" comme la température :
     * les codes WMO sont catégoriels (pluie ≠ neige ≠ ciel clair). Faire la
     * moyenne de "61" (pluie) et "71" (neige) donnerait "66" (pluie verglaçante),
     * ce qui est une condition meteorologique sans rapport avec ce que prédit
     * la moitié des modèles. On agrège donc en famille (CLEAR/RAIN/SNOW/…) et
     * on prend la famille majoritaire pondérée — c'est l'équivalent du "mode"
     * statistique pour des données catégorielles.
     *
     * En cas d'égalité de poids, on prend la famille la plus "sévère" — un modèle
     * dit clair, un autre dit pluie, on garde pluie. C'est le côté tolérant aux
     * erreurs de prudence : mieux vaut afficher la pluie à tort que la cacher.
     */
    fun currentWeatherCondition(forecast: CityForecast): WeatherCondition? {
        val now = Instant.now()
        val votes = mutableMapOf<WeatherCondition, Double>()
        forecast.seriesByModel.forEach { (model, series) ->
            if (series.hourly.timestamps.isEmpty()) return@forEach
            val idx = series.hourly.timestamps.indices.minBy { i ->
                kotlin.math.abs(series.hourly.timestamps[i].epochSecond - now.epochSecond)
            }
            // 1) Priorité au weather_code natif du modèle si dispo.
            // 2) Sinon, fallback empirique depuis les précipitations horaires —
            //    permet à AROME HD (qui n'expose pas weather_code) de contribuer
            //    au vote quand il pleut. Sans ce fallback, AROME HD ne vote
            //    jamais et son poids fort n'est pas utilisé.
            val code = series.hourly.weatherCode.getOrNull(idx)
            val condition = WeatherCondition.fromWmoCode(code)
                ?: WeatherCondition.inferFromPrecipAndTemp(
                    precipMm = series.hourly.precipitation.getOrNull(idx),
                    tempMinC = series.hourly.temperature2m.getOrNull(idx)
                )
                ?: return@forEach
            votes.merge(condition, weighting.weight(model), Double::plus)
        }
        if (votes.isEmpty()) return null
        val maxVote = votes.maxOf { it.value }
        // Tie-breaker : on prend la condition la plus haute en ordinal — qui
        // correspond à peu près au "plus sévère" dans l'ordre déclaré de l'enum
        // (CLEAR=0 … THUNDERSTORM=11). Pas parfait mais raisonnable et
        // déterministe vs un random sur les égalités.
        return votes.filterValues { it == maxVote }
            .keys
            .maxByOrNull { it.ordinal }
    }

    /**
     * Tableau Jour × Modèle des conditions météo journalières.
     *
     * Utilisé par l'écran détail pour afficher une matrice d'icônes — utile
     * pour comparer d'un coup d'œil "tous les modèles disent soleil jeudi
     * mais ICON prévoit de la pluie" : ce désaccord est le genre de signal
     * éditorial qu'on veut surfacer.
     *
     * Pour chaque jour, on conserve les valeurs par modèle (pas d'agrégation
     * type "condition majoritaire") parce que l'intérêt est justement de
     * laisser l'utilisateur voir le désaccord — l'agrégation l'occulterait.
     */
    fun dailyConditionsByModel(forecast: CityForecast): List<DayConditionsRow> {
        val allDates = forecast.seriesByModel.values
            .flatMap { it.daily.dates }
            .distinct()
            .sorted()
        return allDates.map { date ->
            val byModel = forecast.seriesByModel.mapNotNull { (model, series) ->
                val idx = series.daily.dates.indexOf(date)
                if (idx < 0) return@mapNotNull null
                // Priorité au weather_code — sémantique la plus précise.
                // Fallback précipitation-based pour les modèles sans code
                // (AROME HD notamment) : mieux vaut une famille inférée pour
                // les jours pluvieux que rien du tout — les jours secs restent
                // en "—" plutôt que d'inventer clair vs couvert sans donnée.
                val code = series.daily.weatherCode.getOrNull(idx)
                val condition = WeatherCondition.fromWmoCode(code)
                    ?: WeatherCondition.inferFromPrecipAndTemp(
                        precipMm = series.daily.precipitationSum.getOrNull(idx),
                        tempMinC = series.daily.tempMin.getOrNull(idx)
                    )
                    ?: return@mapNotNull null
                model to condition
            }.toMap()
            DayConditionsRow(date = date, byModel = byModel)
        }.filter { it.byModel.isNotEmpty() }
        // Skip les jours sans aucun code disponible — ça arrive avec un cache
        // antérieur à la feature (weather_code = empty list). Plutôt que
        // d'afficher une ligne entière de "—", on masque la ligne et l'UI
        // n'affiche pas le tableau si la liste finale est vide.
    }

    /**
     * Bandes de confiance horaires sur la température.
     *
     * Pour chaque heure couverte par au moins 2 modèles, calcule la moyenne pondérée,
     * le min, le max et l'écart-type. Le résultat se visualise comme une bande qui
     * s'élargit avec l'horizon — c'est la signature visuelle de la divergence
     * inter-modèles.
     *
     * Au démarrage (J+0 → J+1) typiquement 5+ modèles contribuent, la bande est
     * étroite. À J+5 il ne reste souvent que GFS et ECMWF (les modèles haute-
     * résolution ne vont pas si loin), la bande s'élargit naturellement.
     *
     * @param horizonHours Limite l'horizon retourné (défaut 7 jours).
     */
    fun hourlyTemperatureConfidence(
        forecast: CityForecast,
        horizonHours: Int = 168
    ): List<HourlyConfidenceBand> {
        // Pré-indexation : Map<timestamp, temp> par modèle pour des lookups O(1).
        // Évite le `indexOf` quadratique sur les 168 × 5 itérations.
        val indexedByModel: Map<WeatherModel, Map<Instant, Double>> =
            forecast.seriesByModel.mapValues { (_, series) ->
                series.hourly.timestamps.mapIndexedNotNull { idx, ts ->
                    series.hourly.temperature2m.getOrNull(idx)?.let { ts to it }
                }.toMap()
            }

        val allTimestamps = indexedByModel.values
            .flatMap { it.keys }
            .distinct()
            .sorted()
            .take(horizonHours)

        return allTimestamps.mapNotNull { ts ->
            val samples = indexedByModel.mapNotNull { (model, map) ->
                map[ts]?.let { model to it }
            }
            if (samples.size < 2) return@mapNotNull null

            val weighted = samples.map { (model, value) ->
                WeightedSample(value, weighting.weight(model))
            }
            val stats = computeWeightedStats(weighted)
            val percent = stdDevToConfidence(
                stdDev = stats.stdDev,
                tight = Thresholds.TEMPERATURE.tightStdDev,
                wide = Thresholds.TEMPERATURE.wideStdDev
            )

            HourlyConfidenceBand(
                timestamp = ts,
                meanValue = stats.mean,
                minValue = stats.min,
                maxValue = stats.max,
                stdDev = stats.stdDev,
                percent = percent,
                modelCount = samples.size
            )
        }
    }

    // ─────────────────────────── Confidences continues ───────────────────────────

    /**
     * Pour une variable continue (température, vent) : calcule la moyenne et
     * l'écart-type pondérés, puis convertit en score 0-100 via [thresholds].
     */
    private fun continuousConfidence(
        samples: List<Pair<WeatherModel, Double>>,
        thresholds: Thresholds
    ): ConfidenceScore? {
        if (samples.size < 2) return null  // pas de "confiance" possible avec un seul modèle

        val weighted = samples.map { (model, value) -> WeightedSample(value, weighting.weight(model)) }
        val stats = computeWeightedStats(weighted)

        val percent = stdDevToConfidence(
            stdDev = stats.stdDev,
            tight = thresholds.tightStdDev,
            wide = thresholds.wideStdDev
        )

        return ConfidenceScore(
            percent = percent,
            minValue = stats.min,
            maxValue = stats.max,
            meanValue = stats.mean,
            stdDev = stats.stdDev,
            modelCount = samples.size
        )
    }

    // ─────────────────────────── Confidence pluie ───────────────────────────

    /**
     * Pluie : trois cas selon l'agreement binaire sur "est-ce qu'il pleut ?".
     */
    private fun precipitationConfidence(
        samples: List<Pair<WeatherModel, Double>>
    ): PrecipitationConfidence? {
        if (samples.isEmpty()) return null

        val threshold = PrecipitationConfidence.PRECIP_THRESHOLD_MM
        val rainModels = samples.filter { it.second >= threshold }
        val dryModels = samples.filter { it.second < threshold }
        val total = samples.size

        return when {
            // Cas 1 : tout le monde d'accord — sec
            rainModels.isEmpty() -> {
                val maxAmount = samples.maxOf { it.second }
                // Si tout le monde annonce 0.0 strict, confiance maximale.
                // Quelques modèles avec 0.3mm "trace" → confiance légèrement réduite.
                val percent = if (maxAmount < 0.1) 100 else 90
                PrecipitationConfidence.NoRain(
                    percent = percent,
                    modelCount = total,
                    maxAmountMm = maxAmount
                )
            }

            // Cas 2 : tout le monde d'accord — pluie. La confiance dépend du spread sur l'intensité.
            dryModels.isEmpty() -> {
                val weighted = rainModels.map { (model, value) ->
                    WeightedSample(value, weighting.weight(model))
                }
                val stats = computeWeightedStats(weighted)
                val percent = stdDevToConfidence(
                    stdDev = stats.stdDev,
                    tight = Thresholds.PRECIP.tightStdDev,
                    wide = Thresholds.PRECIP.wideStdDev
                )
                PrecipitationConfidence.Rain(
                    percent = percent,
                    modelCount = total,
                    minMm = stats.min,
                    maxMm = stats.max,
                    meanMm = stats.mean
                )
            }

            // Cas 3 : désaccord binaire — c'est le cas le plus incertain.
            else -> {
                // % d'agreement = max(rain, dry) / total. Toujours ≥ 50%.
                // On le ramène sur l'échelle [0..100] : un 60/40 split est faiblement informatif.
                val agreement = maxOf(rainModels.size, dryModels.size).toDouble() / total
                // 50/50 → 0% confiance, 100/0 → 100% confiance.
                // roundToInt (pas toInt) : (3/5 - 0.5) * 200 vaut 19.999...
                // en double à cause de l'imprécision flottante, et un toInt
                // tronquerait à 19 au lieu du 20 mathématiquement correct.
                val percent = ((agreement - 0.5) * 200).roundToInt().coerceIn(0, 100)
                PrecipitationConfidence.Divided(
                    percent = percent,
                    modelCount = total,
                    modelsForRain = rainModels.size,
                    modelsAgainstRain = dryModels.size
                )
            }
        }
    }

    // ─────────────────────────── Math primitives ───────────────────────────

    private data class WeightedSample(val value: Double, val weight: Double)

    private data class WeightedStats(
        val mean: Double,
        val stdDev: Double,
        val min: Double,
        val max: Double
    )

    /**
     * Moyenne et écart-type pondérés.
     *
     * Formules standard :
     *   weighted_mean = Σ(w_i · x_i) / Σ(w_i)
     *   weighted_var  = Σ(w_i · (x_i − mean)²) / Σ(w_i)
     *
     * On n'applique PAS la correction de Bessel (`N-1`) car la population de
     * modèles n'est pas un échantillon d'une plus grande population — c'est
     * littéralement tous les modèles dont on dispose.
     */
    private fun computeWeightedStats(samples: List<WeightedSample>): WeightedStats {
        require(samples.isNotEmpty())
        val totalWeight = samples.sumOf { it.weight }
        val mean = samples.sumOf { it.value * it.weight } / totalWeight
        val variance = samples.sumOf { it.weight * (it.value - mean) * (it.value - mean) } / totalWeight
        return WeightedStats(
            mean = mean,
            stdDev = sqrt(variance),
            min = samples.minOf { it.value },
            max = samples.maxOf { it.value }
        )
    }

    /**
     * Convertit un écart-type en pourcentage de confiance via interpolation
     * linéaire entre deux seuils calibrés par variable.
     *
     * - σ ≤ tight → 100% (modèles très alignés)
     * - σ ≥ wide  → 0%   (divergence forte)
     * - entre les deux : interpolation linéaire
     */
    private fun stdDevToConfidence(stdDev: Double, tight: Double, wide: Double): Int {
        if (stdDev <= tight) return 100
        if (stdDev >= wide) return 0
        val ratio = (stdDev - tight) / (wide - tight)
        return (100.0 * (1.0 - ratio)).toInt()
    }

    /**
     * Seuils calibrés par variable.
     *
     * Calibration empirique basée sur l'observation des écarts inter-modèles
     * typiques aux échéances 24-72h sur l'Europe :
     *
     * - **Température** : σ ≈ 0.5°C en accord très serré, σ ≈ 3°C en divergence forte.
     *   Référence : spread des ensembles ECMWF ENS à J+3.
     *
     * - **Vent** : plus naturellement variable que la température. σ ≈ 2 km/h en accord,
     *   σ ≈ 12 km/h en divergence.
     *
     * - **Pluie** (sur cas où tous prédisent pluie) : σ ≈ 1 mm en accord, σ ≈ 8 mm en divergence.
     *   La pluie convective notamment a des écarts inter-modèles très importants.
     */
    private data class Thresholds(val tightStdDev: Double, val wideStdDev: Double) {
        companion object {
            val TEMPERATURE = Thresholds(tightStdDev = 0.5, wideStdDev = 3.0)
            val WIND = Thresholds(tightStdDev = 2.0, wideStdDev = 12.0)
            val PRECIP = Thresholds(tightStdDev = 1.0, wideStdDev = 8.0)
        }
    }
}

/**
 * Une ligne du tableau Jour × Modèle des conditions météo.
 *
 * Au niveau fichier (pas imbriquée dans `ConfidenceCalculator`) pour pouvoir
 * être référencée depuis le ViewModel et l'UI sans avoir à importer la classe
 * englobante. `byModel` peut contenir < N entrées si certains modèles n'ont
 * pas fourni de code pour ce jour (cas d'un horizon dépassé pour AROME HD).
 */
data class DayConditionsRow(
    val date: LocalDate,
    val byModel: Map<WeatherModel, WeatherCondition>
)
