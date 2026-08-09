package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.util.dailyCloudCoverMean
import com.meteocompare.app.domain.util.resolveDailyCondition
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Calculateur d'indice d'accord multi-modèles.
 *
 * Principe :
 *   - Pour chaque variable continue (température, vent), agrège les prédictions
 *     de tous les modèles disponibles pour un même instant/jour.
 *   - Calcule moyenne et écart-type **pondérés** par [ModelWeightingStrategy].
 *   - Convertit l'écart-type en pourcentage d'accord via des seuils heuristiques
 *     par variable (cf. [Thresholds]).
 *   - Cas spécial pluie : agreement binaire + spread sur l'intensité.
 *
 * Conventions :
 *   - On utilise toujours les **séries journalières** (`daily`) pour les confidences,
 *     car c'est ce que l'UI affiche dans le résumé ville. L'extension aux séries
 *     horaires viendra avec les graphiques détaillés.
 *   - L'alignement entre modèles se fait **par date explicite** (pas par index),
 *     car les modèles régionaux ont un horizon bien plus court que GFS — les positions ne correspondent
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
            windGustMax = continuousConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.windGustsMax.getOrNull(idx)?.let { model to it }
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
     * La stratégie de production donne le même poids à chaque modèle. Une
     * pondération différente ne serait justifiée qu'avec un backtest par zone,
     * variable et échéance.
     *
     * Retourne null si aucun modèle n'a de donnée horaire disponible
     * (ne devrait jamais arriver en pratique sauf bug Open-Meteo).
     */
    fun currentTemperature(
        forecast: CityForecast,
        now: Instant = Instant.now()
    ): Double? {
        val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@mapNotNull null
            val temp = series.hourly.temperature2m.getOrNull(idx) ?: return@mapNotNull null
            model to temp
        }
        if (samples.isEmpty()) return null
        val totalWeight = samples.sumOf { (model, _) -> safeWeight(model) }
        val weightedSum = samples.sumOf { (model, temp) -> temp * safeWeight(model) }
        return weightedSum / totalWeight
    }

    /**
     * Condition météo "maintenant" — vote selon la stratégie de pondération sur la famille
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
    fun currentWeatherCondition(
        forecast: CityForecast,
        now: Instant = Instant.now()
    ): WeatherCondition? {
        val votes = mutableMapOf<WeatherCondition, Double>()
        forecast.seriesByModel.forEach { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@forEach
            // 1) Priorité au weather_code du modèle si disponible.
            // 2) Sinon, fallback strictement local au même modèle depuis
            //    précipitations + température. Aucun signal d'un peer n'est
            //    injecté dans la prédiction de ce modèle.
            val code = series.hourly.weatherCode.getOrNull(idx)
            val condition = WeatherCondition.fromWmoCode(code)
                ?.takeUnless { it == WeatherCondition.UNKNOWN }
                ?: WeatherCondition.inferFromPrecipAndTemp(
                    precipMm = series.hourly.precipitation.getOrNull(idx),
                    tempMinC = series.hourly.temperature2m.getOrNull(idx)
                )
                ?: return@forEach
            votes.merge(condition, safeWeight(model), Double::plus)
        }
        if (votes.isEmpty()) return null
        val maxVote = votes.maxOf { it.value }
        // Tie-breaker conservateur et explicite. On n'utilise pas l'ordinal :
        // UNKNOWN est déclaré en dernier et pourrait sinon gagner une égalité.
        return votes.filterValues { it == maxVote }
            .keys
            .maxByOrNull { it.severityRank }
    }

    /**
     * Couverture nuageuse "maintenant" — moyenne selon la stratégie de pondération
     * du cloud_cover horaire à l'instant courant.
     *
     * Utilisée pour afficher le "% nuageux" sur les cards home et
     * TodaySummaryCard quand la condition affichée est de la famille
     * PARTLY_CLOUDY ou OVERCAST. La moyenne pondérée reste le bon agrégat
     * pour un pourcentage : le mode/vote catégoriel (comme les codes WMO)
     * n'aurait pas de sens sur une échelle 0-100.
     *
     * Retourne null si aucun modèle n'a de donnée cloud_cover à l'instant
     * courant — typique d'un cache pré-feature. L'UI omet alors le badge.
     */
    fun currentCloudCover(
        forecast: CityForecast,
        now: Instant = Instant.now()
    ): Int? = weightedMeanCurrentHourly(forecast, now) { series, idx ->
        series.hourly.cloudCover.getOrNull(idx)
    }

    /**
     * Vitesse du vent "maintenant" — moyenne selon la stratégie de pondération du
     * wind_speed_10m horaire à l'instant courant, en km/h (unité fournie par
     * l'API via `wind_speed_unit=kmh`).
     *
     * Utilisée pour afficher le vent sur les widgets et la TodaySummaryCard.
     * Comme pour cloud cover et température, la moyenne pondérée est le bon
     * agrégat pour une valeur continue.
     *
     * Retourne null si aucun modèle n'a de donnée wind_speed_10m à l'instant
     * courant — cas rare (tous les modèles Open-Meteo supportent la variable),
     * mais possible avec un cache pré-feature. L'UI omet alors le badge.
     *
     * ─── Note d'implémentation ────────────────────────────────────────────
     * On duplique délibérément la logique de [currentTemperature] au lieu de
     * la factoriser via [weightedMeanCurrentHourly] : ce helper renvoie Int?
     * et est optimisé pour les pourcentages arrondis. Pour une valeur Double
     * qu'on veut préserver en précision (le vent à 15.3 km/h n'est pas 15),
     * l'inlining est plus honnête que de convertir Int↔Double dans les deux
     * sens. Voir aussi [currentTemperature] pour le même choix.
     */
    fun currentWindSpeed(
        forecast: CityForecast,
        now: Instant = Instant.now()
    ): Double? {
        val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@mapNotNull null
            val wind = series.hourly.windSpeed10m.getOrNull(idx) ?: return@mapNotNull null
            model to wind
        }
        if (samples.isEmpty()) return null
        val totalWeight = samples.sumOf { (model, _) -> safeWeight(model) }
        val weightedSum = samples.sumOf { (model, w) -> w * safeWeight(model) }
        return weightedSum / totalWeight
    }

    /**
     * Helper : moyenne pondérée d'une valeur horaire à l'instant courant.
     *
     * Factorise la logique commune à [currentTemperature] et [currentCloudCover] :
     *   1. Trouver l'index horaire le plus proche de "maintenant" pour chaque modèle
     *   2. Extraire la valeur via [extractor]
     *   3. Pondérer par le poids du modèle et faire la moyenne
     *
     * Le résultat est arrondi à l'entier — approprié pour les pourcentages qu'on
     * affiche à l'UI. Pour la température (Double), on utilise directement la
     * version inline dans [currentTemperature] au lieu de ce helper.
     */
    private fun weightedMeanCurrentHourly(
        forecast: CityForecast,
        now: Instant,
        extractor: (ForecastSeries, Int) -> Int?
    ): Int? {
        val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@mapNotNull null
            val value = extractor(series, idx) ?: return@mapNotNull null
            model to value.toDouble()
        }
        if (samples.isEmpty()) return null
        val totalWeight = samples.sumOf { (model, _) -> safeWeight(model) }
        val weightedSum = samples.sumOf { (model, v) -> v * safeWeight(model) }
        return (weightedSum / totalWeight).roundToInt()
    }

    /**
     * Index horaire le plus proche de [now], uniquement s'il représente encore
     * réellement l'instant courant. Sans ce garde, un cache vieux de plusieurs
     * jours pouvait afficher sa dernière valeur sous le libellé « Maintenant ».
     */
    private fun nearestCurrentIndex(series: ForecastSeries, now: Instant): Int? {
        if (series.hourly.timestamps.isEmpty()) return null
        val index = series.hourly.timestamps.indices.minBy { i ->
            kotlin.math.abs(series.hourly.timestamps[i].epochSecond - now.epochSecond)
        }
        val distanceSeconds = kotlin.math.abs(
            series.hourly.timestamps[index].epochSecond - now.epochSecond
        )
        return index.takeIf { distanceSeconds <= MAX_CURRENT_SAMPLE_DISTANCE_SECONDS }
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
        val zone = com.meteocompare.app.core.util.resolveZoneOrUtc(forecast.city.timezone)
        val allDates = forecast.seriesByModel.values
            .flatMap { it.daily.dates }
            .distinct()
            .sorted()

        return allDates.map { date ->
            val byModel = mutableMapOf<WeatherModel, WeatherCondition>()
            val extrasByModel = mutableMapOf<WeatherModel, DayCellExtras>()
            val inferredModels = mutableSetOf<WeatherModel>()

            forecast.seriesByModel.forEach { (model, series) ->
                val idx = series.daily.dates.indexOf(date)
                if (idx < 0) return@forEach

                series.resolveDailyCondition(date, zone)?.let { resolved ->
                    byModel[model] = resolved.condition
                    if (resolved.inferred) inferredModels += model
                }

                val precipProb = series.daily.precipitationProbabilityMax.getOrNull(idx)
                    ?.takeIf { it in 0..100 }
                val cloudMean = series.dailyCloudCoverMean(date, zone)
                if (precipProb != null || cloudMean != null) {
                    extrasByModel[model] = DayCellExtras(
                        precipProbabilityMax = precipProb,
                        cloudCoverMean = cloudMean
                    )
                }
            }

            DayConditionsRow(
                date = date,
                byModel = byModel,
                extrasByModel = extrasByModel,
                inferredByModel = inferredModels
            )
        }.filter { it.byModel.isNotEmpty() }
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
    ): List<HourlyConfidenceBand> = hourlyConfidenceBand(
        forecast = forecast,
        horizonHours = horizonHours,
        thresholds = Thresholds.TEMPERATURE,
        extractor = { series, idx -> series.hourly.temperature2m.getOrNull(idx) }
    )

    /**
     * Bandes d'accord horaires sur les précipitations (mm sur l'heure précédente).
     *
     * Utilise le même modèle mathématique que la température : moyenne pondérée,
     * min/max, écart-type converti en %. Les seuils tight/wide sont ceux de
     * [Thresholds.PRECIP] — bien plus larges qu'en température (la pluie est
     * intrinsèquement plus divergente entre modèles, surtout sur la convection).
     *
     * Différence importante avec le calcul journalier ([dayConfidence]) qui
     * distingue les cas "sec/pluie/divisé" via [PrecipitationConfidence] :
     * ici on reste sur une bande continue, c'est ce que le graphe attend.
     * Le graphe visualise l'AMPLITUDE des prévisions pluie, pas leur
     * classification qualitative — les deux vues sont complémentaires.
     */
    fun hourlyPrecipitationConfidence(
        forecast: CityForecast,
        horizonHours: Int = 168
    ): List<HourlyConfidenceBand> = hourlyConfidenceBand(
        forecast = forecast,
        horizonHours = horizonHours,
        thresholds = Thresholds.PRECIP,
        extractor = { series, idx -> series.hourly.precipitation.getOrNull(idx) }
    )

    /**
     * Bandes de confiance horaires sur le vent à 10m (km/h).
     *
     * Attention : c'est le vent moyen à 10m, pas les rafales. Les seuils sont
     * ceux de [Thresholds.WIND] (heuristique produit : accord serré vers 2 km/h
     * d'écart-type, divergence forte vers 12 km/h).
     */
    fun hourlyWindConfidence(
        forecast: CityForecast,
        horizonHours: Int = 168
    ): List<HourlyConfidenceBand> = hourlyConfidenceBand(
        forecast = forecast,
        horizonHours = horizonHours,
        thresholds = Thresholds.WIND,
        extractor = { series, idx -> series.hourly.windSpeed10m.getOrNull(idx) }
    )

    /**
     * Helper générique — factorise la logique de [hourlyTemperatureConfidence]
     * / [hourlyPrecipitationConfidence] / [hourlyWindConfidence].
     *
     * Concept identique à la version historique : pré-indexation par timestamp
     * pour éviter le indexOf quadratique, agrégation pondérée (moyenne + std),
     * conversion σ → % via les seuils passés en paramètre.
     *
     * Le paramètre `extractor` isole la seule chose qui varie entre variables :
     * quelle valeur horaire piocher dans la série. Tout le reste (alignment
     * temporel, agrégation stat, conversion en %) est identique.
     */
    private fun hourlyConfidenceBand(
        forecast: CityForecast,
        horizonHours: Int,
        thresholds: Thresholds,
        extractor: (ForecastSeries, Int) -> Double?
    ): List<HourlyConfidenceBand> {
        val indexedByModel: Map<WeatherModel, Map<Instant, Double>> =
            forecast.seriesByModel.mapValues { (_, series) ->
                series.hourly.timestamps.mapIndexedNotNull { idx, ts ->
                    extractor(series, idx)?.let { ts to it }
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
                WeightedSample(value, safeWeight(model))
            }
            val stats = computeWeightedStats(weighted)
            val percent = stdDevToConfidence(
                stdDev = stats.stdDev,
                tight = thresholds.tightStdDev,
                wide = thresholds.wideStdDev
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

        val weighted = samples.map { (model, value) -> WeightedSample(value, safeWeight(model)) }
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
                    WeightedSample(value, safeWeight(model))
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
                // L'accord binaire suit la même stratégie de pondération que les
                // autres agrégats. Avec EqualWeighting (production actuelle),
                // cela reste exactement le ratio de modèles. Ce calcul évite
                // une incohérence future si une stratégie backtestée est ajoutée.
                val rainWeight = rainModels.sumOf { (model, _) -> safeWeight(model) }
                val dryWeight = dryModels.sumOf { (model, _) -> safeWeight(model) }
                val agreement = maxOf(rainWeight, dryWeight) / (rainWeight + dryWeight)
                // 50/50 → 0% d'accord, 100/0 → 100%. Le nombre de modèles
                // affiché reste un compte brut, distinct du poids statistique.
                val percent = ((agreement - 0.5) * 200).roundToInt().coerceIn(0, 100)
                val rainStats = computeWeightedStats(
                    rainModels.map { (model, value) ->
                        WeightedSample(value, safeWeight(model))
                    }
                )
                PrecipitationConfidence.Divided(
                    percent = percent,
                    modelCount = total,
                    modelsForRain = rainModels.size,
                    modelsAgainstRain = dryModels.size,
                    rainMinMm = rainStats.min,
                    rainMaxMm = rainStats.max,
                    rainMeanMm = rainStats.mean
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

    /** Défense du contrat de [ModelWeightingStrategy] contre NaN, zéro ou poids négatif. */
    private fun safeWeight(model: WeatherModel): Double = weighting.weight(model).also { weight ->
        require(weight.isFinite() && weight > 0.0) {
            "Model weight must be finite and > 0 for ${model.name}: $weight"
        }
    }

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
        require(samples.all { it.value.isFinite() && it.weight.isFinite() && it.weight > 0.0 })
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
     * Convertit un écart-type en indice d'accord 0-100 via interpolation
     * linéaire entre deux seuils heuristiques par variable.
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
     * Seuils heuristiques de lisibilité par variable.
     *
     * Ils transforment un spread inter-modèles en un indicateur pédagogique
     * monotone. Ce ne sont ni des probabilités, ni des seuils de skill validés
     * scientifiquement, ni une calibration ECMWF. Ils devront être remplacés
     * ou recalibrés à partir d'un corpus de vérification local par variable et
     * échéance si l'application veut un jour annoncer une fiabilité prédictive.
     *
     * - Température : 0,5°C = accord très serré ; 3°C = divergence forte.
     * - Vent à 10 m : 2 km/h = accord très serré ; 12 km/h = divergence forte.
     * - Pluie (si tous prévoient pluie) : 1 mm = accord serré ; 8 mm = divergence forte.
     */
    private data class Thresholds(val tightStdDev: Double, val wideStdDev: Double) {
        companion object {
            val TEMPERATURE = Thresholds(tightStdDev = 0.5, wideStdDev = 3.0)
            val WIND = Thresholds(tightStdDev = 2.0, wideStdDev = 12.0)
            val PRECIP = Thresholds(tightStdDev = 1.0, wideStdDev = 8.0)
        }
    }

    private companion object {
        /** Une grille horaire fraîche doit rester à moins de 90 minutes de maintenant. */
        const val MAX_CURRENT_SAMPLE_DISTANCE_SECONDS: Long = 90L * 60L
    }
}

/**
 * Une ligne du tableau Jour × Modèle des conditions météo.
 *
 * Au niveau fichier (pas imbriquée dans `ConfidenceCalculator`) pour pouvoir
 * être référencée depuis le ViewModel et l'UI sans avoir à importer la classe
 * englobante. `byModel` peut contenir < N entrées si certains modèles n'ont
 * pas fourni de code pour ce jour (cas d'un horizon régional dépassé).
 */
data class DayConditionsRow(
    val date: LocalDate,
    val byModel: Map<WeatherModel, WeatherCondition>,
    /**
     * Métadonnées supplémentaires par modèle : probabilité de pluie et
     * couverture nuageuse moyenne. Séparé de `byModel` pour rester rétro-
     * compatible (les tests et l'API publique n'ont pas à connaître ces
     * extras). Un modèle peut être dans `byModel` sans être dans `extrasByModel`
     * si ces variables ne sont pas fournies (cache pré-feature notamment).
     */
    val extrasByModel: Map<WeatherModel, DayCellExtras> = emptyMap(),

    /**
     * Modèles dont la condition affichée est DÉRIVÉE de leurs propres variables
     * (codes horaires, précip/temp ou couverture nuageuse), faute de code WMO
     * journalier exploitable. Aucune donnée d'un autre modèle n'est injectée.
     *
     * L'UI signale ces cellules (typiquement alpha réduit) afin de distinguer
     * une valeur WMO directe d'une interprétation locale et transparente.
     *
     * Défaut vide pour rétro-compat des tests + du cache pré-feature.
     */
    val inferredByModel: Set<WeatherModel> = emptySet()
)

/**
 * Données affichées EN COMPLÉMENT de l'icône dans une cellule Jour × Modèle.
 *
 * Elles servent à surfacer 2 signaux quand la famille météo le justifie :
 *   - **precipProbabilityMax** : probabilité de pluie max de la journée
 *     (0-100). Utile quand la condition est de la famille pluie/orage —
 *     l'utilisateur veut savoir si c'est un "peut-être" ou une "quasi-certitude".
 *   - **cloudCoverMean** : couverture nuageuse moyenne (0-100). Utile quand la
 *     condition est PARTLY_CLOUDY ou OVERCAST — permet de distinguer un ciel
 *     à 60% de nuages (partly) d'un ciel à 95% de nuages (overcast marqué).
 *
 * Les deux sont nullables : un modèle peut fournir l'une sans l'autre.
 */
data class DayCellExtras(
    val precipProbabilityMax: Int? = null,
    val cloudCoverMean: Int? = null
)
