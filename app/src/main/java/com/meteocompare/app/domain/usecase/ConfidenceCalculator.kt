package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEngineVariable
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.PrecipitationConsensusMeta
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.util.dailyCloudCoverMean
import com.meteocompare.app.domain.util.resolveDailyCondition
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Calculateur de convergence instantanée multi-modèles (nom de classe historique).
 *
 * Principe :
 *   - Pour chaque variable continue (température, vent), agrège les prédictions
 *     de tous les modèles disponibles pour un même instant/jour.
 *   - Construit une valeur centrale par médiane pondérée, équilibrée par lignée.
 *   - Convertit la dispersion en pourcentage de convergence via des seuils heuristiques
 *     par variable (cf. [Thresholds]).
 *   - Cas spécial pluie : probabilité d'occurrence + quantité conditionnelle.
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
    fun dayConfidence(
        forecast: CityForecast,
        date: LocalDate,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): DayConfidence {
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
                thresholds = Thresholds.TEMPERATURE,
                engineContext = engineContext,
                variable = ForecastEngineVariable.TEMPERATURE,
                allowCalibration = true
            ),
            tempMin = continuousConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.tempMin.getOrNull(idx)?.let { model to it }
                },
                thresholds = Thresholds.TEMPERATURE,
                engineContext = engineContext,
                variable = ForecastEngineVariable.TEMPERATURE,
                allowCalibration = false
            ),
            windMax = continuousConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.windSpeedMax.getOrNull(idx)?.let { model to it }
                },
                thresholds = Thresholds.WIND,
                engineContext = engineContext,
                variable = ForecastEngineVariable.WIND,
                allowCalibration = true
            ),
            windGustMax = continuousConfidence(
                samples = modelsAtDate.mapNotNull { (model, series, idx) ->
                    series.daily.windGustsMax.getOrNull(idx)?.let { model to it }
                },
                thresholds = Thresholds.WIND,
                engineContext = engineContext,
                variable = ForecastEngineVariable.WIND,
                allowCalibration = false
            ),
            precipitation = precipitationConfidence(
                rows = modelsAtDate.map { (model, series, idx) ->
                    ForecastConsensus.PrecipitationRow(
                        model = model,
                        amountMm = series.daily.precipitationSum.getOrNull(idx),
                        probabilityPercent = series.daily.precipitationProbabilityMax.getOrNull(idx)
                    )
                },
                engineContext = engineContext
            )
        )
    }

    /** Convenience : confidence par jour sur tout l'horizon disponible. */
    fun weeklyConfidence(
        forecast: CityForecast,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): List<DayConfidence> {
        val allDates = forecast.seriesByModel.values
            .flatMap { it.daily.dates }
            .distinct()
            .sorted()
        return allDates.map { dayConfidence(forecast, it, engineContext) }
    }

    /**
     * Température "maintenant" — médiane pondérée consensus robuste de la valeur
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
        now: Instant = Instant.now(),
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): Double? {
        val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@mapNotNull null
            val temp = series.hourly.temperature2m.getOrNull(idx) ?: return@mapNotNull null
            model to temp
        }
        return engineContinuous(
            samples = samples,
            thresholds = Thresholds.TEMPERATURE,
            engineContext = engineContext,
            variable = ForecastEngineVariable.TEMPERATURE,
            allowCalibration = false
        ).central
    }

    /**
     * Condition météo "maintenant" — consensus hybride.
     *
     * Les phénomènes significatifs (pluie, neige, brouillard, orage…) restent
     * issus du vote catégoriel familial des codes WMO bruts. Pour un ciel sec,
     * CLEAR / MAINLY_CLEAR / PARTLY_CLOUDY / OVERCAST sont regroupés avant le
     * vote puis la condition affichée est dérivée de la nébulosité centrale du
     * moteur V3 sélectionné. Cela évite qu'une minorité OVERCAST gagne seulement
     * parce que les autres modèles secs sont fragmentés en plusieurs libellés.
     *
     * La nébulosité horaire n'est jamais calibrée avec l'historique J+1.
     */
    fun currentWeatherCondition(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): WeatherCondition? {
        val conditionEntries = mutableListOf<ForecastConsensus.Entry<WeatherCondition>>()
        val cloudSamples = mutableListOf<Pair<WeatherModel, Double>>()

        forecast.seriesByModel.forEach { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@forEach
            val condition = WeatherCondition.fromWmoCode(series.hourly.weatherCode.getOrNull(idx))
                ?.takeUnless { it == WeatherCondition.UNKNOWN }
                ?: WeatherCondition.inferFromPrecipAndTemp(
                    precipMm = series.hourly.precipitation.getOrNull(idx),
                    tempMinC = series.hourly.temperature2m.getOrNull(idx)
                )
            condition?.let { conditionEntries += ForecastConsensus.Entry(model, it) }
            series.hourly.cloudCover.getOrNull(idx)
                ?.takeIf { it in 0..100 }
                ?.let { cloudSamples += model to it.toDouble() }
        }

        val cloudCentral = engineContinuous(
            samples = cloudSamples,
            thresholds = Thresholds(10.0, 50.0),
            engineContext = engineContext,
            variable = ForecastEngineVariable.CLOUD,
            allowCalibration = false,
            min = 0.0,
            max = 100.0
        ).central
        return ForecastConsensus.conditionHybrid(
            entries = conditionEntries,
            cloudCoverPercent = cloudCentral,
            localWeights = localWeights(conditionEntries.map { it.model })
        ).value
    }

    /**
     * Couverture nuageuse "maintenant" — médiane pondérée consensus robuste
     * du cloud_cover horaire à l'instant courant.
     *
     * Utilisée pour afficher le "% nuageux" sur les cards home et
     * TodaySummaryCard quand la condition affichée est de la famille
     * PARTLY_CLOUDY ou OVERCAST. Comme les autres variables continues, la valeur
     * centrale est une médiane pondérée et équilibrée par lignée.
     *
     * Retourne null si aucun modèle n'a de donnée cloud_cover à l'instant
     * courant — typique d'un cache pré-feature. L'UI omet alors le badge.
     */
    fun currentCloudCover(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): Int? = centralCurrentHourlyPercent(forecast, now, engineContext) { series, idx ->
        series.hourly.cloudCover.getOrNull(idx)
    }

    /**
     * Vitesse du vent "maintenant" — médiane pondérée consensus robuste du
     * wind_speed_10m horaire à l'instant courant, en km/h (unité fournie par
     * l'API via `wind_speed_unit=kmh`).
     *
     * Utilisée pour afficher le vent sur les widgets et la TodaySummaryCard.
     * Comme pour cloud cover et température, la centrale robuste est équilibrée
     * par lignée de modèles.
     *
     * Retourne null si aucun modèle n'a de donnée wind_speed_10m à l'instant
     * courant — cas rare (tous les modèles Open-Meteo supportent la variable),
     * mais possible avec un cache pré-feature. L'UI omet alors le badge.
     *
     * ─── Note d'implémentation ────────────────────────────────────────────
     * On duplique délibérément la logique de [currentTemperature] au lieu de
     * la factoriser via [centralCurrentHourlyPercent] : ce helper renvoie Int?
     * et est optimisé pour les pourcentages arrondis. Pour une valeur Double
     * qu'on veut préserver en précision (le vent à 15.3 km/h n'est pas 15),
     * l'inlining est plus honnête que de convertir Int↔Double dans les deux
     * sens. Voir aussi [currentTemperature] pour le même choix.
     */
    fun currentWindSpeed(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): Double? {
        val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@mapNotNull null
            val wind = series.hourly.windSpeed10m.getOrNull(idx) ?: return@mapNotNull null
            model to wind
        }
        return engineContinuous(
            samples = samples,
            thresholds = Thresholds.WIND,
            engineContext = engineContext,
            variable = ForecastEngineVariable.WIND,
            allowCalibration = false,
            min = 0.0
        ).central
    }

    /**
     * Helper : centrale consensus robuste d'une valeur horaire à l'instant courant.
     *
     * Factorise la logique commune à [currentTemperature] et [currentCloudCover] :
     *   1. Trouver l'index horaire le plus proche de "maintenant" pour chaque modèle
     *   2. Extraire la valeur via [extractor]
     *   3. Équilibrer les lignées et calculer la médiane pondérée
     *
     * Le résultat est arrondi à l'entier — approprié pour les pourcentages qu'on
     * affiche à l'UI. Pour la température (Double), on utilise directement la
     * version inline dans [currentTemperature] au lieu de ce helper.
     */
    private fun centralCurrentHourlyPercent(
        forecast: CityForecast,
        now: Instant,
        engineContext: ForecastEngineContext,
        extractor: (ForecastSeries, Int) -> Int?
    ): Int? {
        val samples = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = nearestCurrentIndex(series, now) ?: return@mapNotNull null
            val value = extractor(series, idx) ?: return@mapNotNull null
            model to value.toDouble()
        }
        val central = engineContinuous(
            samples = samples,
            thresholds = Thresholds(10.0, 50.0),
            engineContext = engineContext,
            variable = ForecastEngineVariable.CLOUD,
            allowCalibration = false,
            min = 0.0,
            max = 100.0
        ).central
        return central?.roundToInt()
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
     * Pour chaque heure couverte par au moins 2 lignées, calcule la médiane pondérée,
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
        horizonHours: Int = 168,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): List<HourlyConfidenceBand> = hourlyConfidenceBand(
        forecast = forecast,
        horizonHours = horizonHours,
        thresholds = Thresholds.TEMPERATURE,
        engineContext = engineContext,
        variable = ForecastEngineVariable.TEMPERATURE,
        extractor = { series, idx -> series.hourly.temperature2m.getOrNull(idx) }
    )

    /**
     * Bandes de convergence horaires sur les précipitations (mm sur l'heure précédente).
     *
     * Utilise le consensus robuste : P(pluie) séparée de la quantité conditionnelle,
     * centrale robuste, min/max et dispersion convertie en %. Les seuils tight/wide sont ceux de
     * [Thresholds.PRECIP] — bien plus larges qu'en température (la pluie est
     * intrinsèquement plus divergente entre modèles, surtout sur la convection).
     *
     * Différence importante avec le calcul journalier ([dayConfidence]) qui
     * distingue les cas "sec/pluie/divisé" via [PrecipitationConfidence] :
     * ici la bande conserve min/max et dispersion, tandis que sa centrale suit
     * exactement le même moteur pluie en deux étapes que le résumé.
     */
    fun hourlyPrecipitationConfidence(
        forecast: CityForecast,
        horizonHours: Int = 168,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): List<HourlyConfidenceBand> {
        val indexed = forecast.seriesByModel.mapValues { (_, series) ->
            series.hourly.timestamps.withIndex().associate { (idx, ts) -> ts to idx }
        }
        val allTimestamps = indexed.values.flatMap { it.keys }.distinct().sorted().take(horizonHours)
        return allTimestamps.mapNotNull timestamp@ { ts ->
            val rows = forecast.seriesByModel.mapNotNull row@ { (model, series) ->
                val idx = indexed[model]?.get(ts) ?: return@row null
                val amount = series.hourly.precipitation.getOrNull(idx)
                val probability = series.hourly.precipitationProbability.getOrNull(idx)
                if (amount == null && probability == null) null
                else ForecastConsensus.PrecipitationRow(model, amount, probability)
            }
            val result = ForecastConsensus.precipitation(
                rows = rows,
                thresholdMm = 0.1,
                localWeights = localWeights(rows.map { it.model }),
                amountTightStdDev = 1.0,
                amountWideStdDev = 8.0
            )
            val engineResult = ForecastEngineV3.precipitation(
                rows,
                ForecastEngineV3.PrecipitationOptions(
                    engine = engineContext.engine,
                    threshold = 0.1,
                    localWeights = localWeights(rows.map { it.model }),
                    calibration = emptyMap(),
                    amountTight = 1.0,
                    amountWide = 8.0
                )
            )
            val percent = result.convergencePercent ?: return@timestamp null
            HourlyConfidenceBand(
                timestamp = ts,
                meanValue = engineResult.centralAmountMm ?: result.centralAmountMm ?: 0.0,
                minValue = result.minMm ?: 0.0,
                maxValue = result.maxMm ?: 0.0,
                stdDev = result.conditionalStdDev ?: 0.0,
                percent = percent,
                modelCount = result.modelCount,
                familyCount = result.familyCount
            )
        }
    }

    /**
     * Bandes de confiance horaires sur le vent à 10m (km/h).
     *
     * Attention : c'est le vent moyen à 10m, pas les rafales. Les seuils sont
     * ceux de [Thresholds.WIND] (heuristique produit : accord serré vers 2 km/h
     * d'écart-type, divergence forte vers 12 km/h).
     */
    fun hourlyWindConfidence(
        forecast: CityForecast,
        horizonHours: Int = 168,
        engineContext: ForecastEngineContext = ForecastEngineContext.DEFAULT
    ): List<HourlyConfidenceBand> = hourlyConfidenceBand(
        forecast = forecast,
        horizonHours = horizonHours,
        thresholds = Thresholds.WIND,
        engineContext = engineContext,
        variable = ForecastEngineVariable.WIND,
        extractor = { series, idx -> series.hourly.windSpeed10m.getOrNull(idx) }
    )

    /**
     * Helper générique — factorise la logique de [hourlyTemperatureConfidence]
     * / [hourlyPrecipitationConfidence] / [hourlyWindConfidence].
     *
     * Pré-indexation par timestamp pour éviter le indexOf quadratique, puis
     * agrégation consensus robuste (médiane pondérée + dispersion),
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
        engineContext: ForecastEngineContext,
        variable: ForecastEngineVariable,
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
            val consensus = ForecastConsensus.continuous(
                entries = samples.map { (model, value) -> ForecastConsensus.Entry(model, value) },
                localWeights = localWeights(samples.map { it.first }),
                tightStdDev = thresholds.tightStdDev,
                wideStdDev = thresholds.wideStdDev
            )
            val stats = consensus.stats ?: return@mapNotNull null
            val percent = consensus.convergencePercent ?: return@mapNotNull null
            val engineResult = engineContinuous(
                samples = samples,
                thresholds = thresholds,
                engineContext = engineContext,
                variable = variable,
                allowCalibration = false,
                min = if (variable == ForecastEngineVariable.WIND) 0.0 else null
            )

            HourlyConfidenceBand(
                timestamp = ts,
                meanValue = engineResult.central ?: consensus.central ?: stats.mean,
                minValue = stats.min,
                maxValue = stats.max,
                stdDev = stats.stdDev,
                percent = percent,
                modelCount = consensus.modelCount,
                familyCount = consensus.familyCount
            )
        }
    }

    // ─────────────────────────── Confidences continues ───────────────────────────

    /**
     * Pour une variable continue (température, vent) : médiane pondérée pour la
     * centrale, écart-type pondéré pour la convergence 0-100 via [thresholds].
     */
    private fun continuousConfidence(
        samples: List<Pair<WeatherModel, Double>>,
        thresholds: Thresholds,
        engineContext: ForecastEngineContext,
        variable: ForecastEngineVariable,
        allowCalibration: Boolean
    ): ConfidenceScore? {
        val consensus = ForecastConsensus.continuous(
            entries = samples.map { (model, value) -> ForecastConsensus.Entry(model, value) },
            localWeights = localWeights(samples.map { it.first }),
            tightStdDev = thresholds.tightStdDev,
            wideStdDev = thresholds.wideStdDev
        )
        val stats = consensus.stats ?: return null
        val engineResult = engineContinuous(
            samples = samples,
            thresholds = thresholds,
            engineContext = engineContext,
            variable = variable,
            allowCalibration = allowCalibration,
            min = if (variable == ForecastEngineVariable.WIND) 0.0 else null
        )
        // Une valeur centrale reste exploitable avec une seule lignée. Seule la
        // convergence inter-familles devient indéfinie dans ce cas.
        val percent = consensus.convergencePercent ?: 0
        return ConfidenceScore(
            percent = percent,
            minValue = stats.min,
            maxValue = stats.max,
            meanValue = engineResult.central ?: consensus.central ?: stats.mean,
            stdDev = stats.stdDev,
            modelCount = consensus.modelCount,
            familyCount = consensus.familyCount,
            convergencePercent = consensus.convergencePercent
        )
    }

    // ─────────────────────────── Confidence pluie ───────────────────────────

    /**
     * Pluie consensus robuste : P(pluie), quantité conditionnelle puis représentation
     * qualitative compatible avec l'UI historique.
     */
    private fun precipitationConfidence(
        rows: List<ForecastConsensus.PrecipitationRow>,
        engineContext: ForecastEngineContext
    ): PrecipitationConfidence? {
        val result = ForecastConsensus.precipitation(
            rows = rows,
            thresholdMm = PrecipitationConfidence.PRECIP_THRESHOLD_MM,
            localWeights = localWeights(rows.map { it.model }),
            amountTightStdDev = Thresholds.PRECIP.tightStdDev,
            amountWideStdDev = Thresholds.PRECIP.wideStdDev
        )
        if (result.modelCount == 0) return null
        val engineResult = ForecastEngineV3.precipitation(
            rows,
            ForecastEngineV3.PrecipitationOptions(
                engine = engineContext.engine,
                threshold = PrecipitationConfidence.PRECIP_THRESHOLD_MM,
                localWeights = localWeights(rows.map { it.model }),
                calibration = engineContext.calibration(ForecastEngineVariable.PRECIPITATION),
                amountTight = Thresholds.PRECIP.tightStdDev,
                amountWide = Thresholds.PRECIP.wideStdDev
            )
        )
        // La quantité centrale reste utile avec une seule famille. En revanche,
        // la convergence inter-familles est alors indéfinie et reste null dans meta.
        val percent = result.convergencePercent ?: 0
        val common = PrecipitationConsensusMeta(
            probabilityPercent = engineResult.probabilityPercent ?: result.probabilityPercent,
            conditionalAmountMm = engineResult.conditionalAmountMm ?: result.conditionalAmountMm,
            expectedAmountMm = engineResult.expectedAmountMm ?: result.expectedAmountMm,
            centralAmountMm = engineResult.centralAmountMm ?: result.centralAmountMm,
            convergencePercent = result.convergencePercent,
            familyCount = result.familyCount
        )
        val wetAmounts = rows.mapNotNull { row ->
            row.amountMm?.takeIf { it.isFinite() && it >= PrecipitationConfidence.PRECIP_THRESHOLD_MM }
        }
        val wetMin = wetAmounts.minOrNull()
        val wetMax = wetAmounts.maxOrNull()
        return when {
            result.wetModelCount == 0 -> PrecipitationConfidence.NoRain(
                percent = percent, modelCount = result.modelCount, maxAmountMm = result.maxMm ?: 0.0, meta = common
            )
            result.wetModelCount == result.modelCount -> PrecipitationConfidence.Rain(
                percent = percent, modelCount = result.modelCount, minMm = wetMin ?: result.minMm ?: 0.0,
                maxMm = wetMax ?: result.maxMm ?: 0.0, meanMm = engineResult.conditionalAmountMm ?: result.conditionalAmountMm ?: 0.0, meta = common
            )
            else -> PrecipitationConfidence.Divided(
                percent = percent, modelCount = result.modelCount, modelsForRain = result.wetModelCount,
                modelsAgainstRain = result.modelCount - result.wetModelCount, rainMinMm = wetMin ?: 0.0,
                rainMaxMm = wetMax ?: 0.0, rainMeanMm = engineResult.conditionalAmountMm ?: result.conditionalAmountMm ?: 0.0, meta = common
            )
        }
    }

    // ─────────────────────────── Moteur V3 ───────────────────────────

    private fun engineContinuous(
        samples: List<Pair<WeatherModel, Double>>,
        thresholds: Thresholds,
        engineContext: ForecastEngineContext,
        variable: ForecastEngineVariable,
        allowCalibration: Boolean,
        min: Double? = null,
        max: Double? = null
    ): ForecastEngineV3.ContinuousResult = ForecastEngineV3.continuous(
        entries = samples.map { (model, value) -> ForecastConsensus.Entry(model, value) },
        options = ForecastEngineV3.ContinuousOptions(
            engine = engineContext.engine,
            localWeights = localWeights(samples.map { it.first }) + engineContext.localWeights(variable),
            calibration = engineContext.calibration(variable, allowCalibration),
            tight = thresholds.tightStdDev,
            wide = thresholds.wideStdDev,
            min = min,
            max = max
        )
    )

    // ─────────────────────────── Math primitives ───────────────────────────

    private fun localWeights(models: Collection<WeatherModel>): Map<WeatherModel, Double> =
        models.distinct().associateWith(::safeWeight)

    /** Défense du contrat de [ModelWeightingStrategy] contre NaN, zéro ou poids négatif. */
    private fun safeWeight(model: WeatherModel): Double = weighting.weight(model).also { weight ->
        require(weight.isFinite() && weight > 0.0) {
            "Model weight must be finite and > 0 for ${model.name}: $weight"
        }
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
