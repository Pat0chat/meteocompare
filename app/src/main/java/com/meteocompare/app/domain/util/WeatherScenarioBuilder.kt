package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.PrecipitationThresholds
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherScenario
import com.meteocompare.app.domain.model.WeatherScenarioKind
import com.meteocompare.app.domain.model.WeatherScenarioTiming
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Regroupe les prévisions déterministes modèle par modèle en quelques scénarios
 * pédagogiques sur les 12 prochaines heures.
 *
 * Important : le soutien affichable est équilibré par familles de modèles et ne
 * doit jamais être interprété comme une probabilité météo. Des modèles issus de
 * familles proches peuvent partager des dépendances et ne constituent pas des
 * tirages indépendants.
 */
object WeatherScenarioBuilder {

    private const val HOUR_COUNT = 12

    fun next12h(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        maxScenarios: Int = 3
    ): List<WeatherScenario> {
        if (maxScenarios <= 0) return emptyList()

        val startInstant = HourlySampling.anchor(forecast, now)
        val modelSummaries = forecast.seriesByModel.mapNotNull { (model, series) ->
            summarizeModel(model, series, startInstant)
        }
        if (modelSummaries.isEmpty()) return emptyList()

        val totalModelCount = modelSummaries.size
        val familyWeights = ForecastConsensus.familyBalancedWeights(modelSummaries.map { it.model })
        val totalVoteWeight = familyWeights.values.sum().takeIf { it > 0.0 } ?: 1.0
        val totalFamilyCount = modelSummaries.map { ForecastConsensus.groupFor(it.model) }.distinct().size
        val grouped = modelSummaries
            .groupBy { ScenarioKey(it.kind, it.timing) }
            .map { (key, models) -> models.toScenario(key, totalModelCount, totalFamilyCount, familyWeights, totalVoteWeight) }
            .sortedWith(
                compareByDescending<WeatherScenario> { it.voteSharePercent ?: 0 }
                    .thenByDescending { it.kind.importance }
            )

        if (grouped.size <= maxScenarios) return grouped

        // Ne jamais fabriquer un pseudo-scénario OTHER à partir de groupes
        // météorologiquement incompatibles : ses min/max (pluie, température,
        // rafales) n'auraient aucune signification. On affiche uniquement de
        // vrais groupes cohérents et on expose séparément le nombre de variantes
        // masquées pour que l'UI puisse le signaler sans agréger leurs métriques.
        val displayed = grouped.take(maxScenarios)
        val hidden = grouped.drop(maxScenarios)
        val hiddenVariantCount = hidden.size
        val hiddenModelCount = hidden.sumOf { it.modelCount }
        return displayed.map { scenario ->
            scenario.copy(
                hiddenVariantCount = hiddenVariantCount,
                hiddenModelCount = hiddenModelCount
            )
        }
    }

    private fun summarizeModel(model: WeatherModel, series: ForecastSeries, startInstant: Instant): ModelScenario? {
        val samples = buildList {
            repeat(HOUR_COUNT) { offset ->
                val target = startInstant.plusSeconds(offset * 3_600L)
                val index = with(HourlySampling) { series.hourly.timestamps.exactIndex(target) } ?: return@repeat

                add(
                    Sample(
                        offset = offset,
                        temperature = series.hourly.temperature2m.getOrNull(index),
                        precipitation = series.hourly.precipitation.getOrNull(index),
                        condition = WeatherCondition
                            .fromWmoCode(series.hourly.weatherCode.getOrNull(index))
                            ?.takeUnless { it == WeatherCondition.UNKNOWN },
                        cloudCover = series.hourly.cloudCover.getOrNull(index),
                        gust = series.hourly.windGusts10m.getOrNull(index)
                    )
                )
            }
        }
        if (samples.isEmpty()) return null
        if (samples.none { sample ->
                sample.temperature != null || sample.precipitation != null ||
                    sample.condition != null || sample.cloudCover != null || sample.gust != null
            }
        ) return null

        val temperatures = samples.mapNotNull { it.temperature }
        val clouds = samples.mapNotNull { it.cloudCover }.sorted()
        val gusts = samples.mapNotNull { it.gust }
        val precipitation = samples.mapNotNull { it.precipitation }
        // Un "cumul 12 h" n'est honnête que si les 12 échéances et leurs
        // quantités sont toutes présentes. Une fenêtre partielle peut encore
        // qualifier le scénario (codes WMO / pluie observée), mais ne publie
        // jamais un faux total sous-estimé.
        val totalPrecip = precipitation.sum().takeIf {
            samples.size == HOUR_COUNT && precipitation.size == HOUR_COUNT
        }

        val wetSamples = samples.filter { sample ->
            (sample.precipitation ?: 0.0) > PrecipitationThresholds.HOURLY_OCCURRENCE_MM || sample.condition.isWet
        }

        val severeKind = when {
            samples.any { it.condition == WeatherCondition.THUNDERSTORM } -> WeatherScenarioKind.THUNDERSTORM
            samples.any { it.condition == WeatherCondition.FREEZING_RAIN } -> WeatherScenarioKind.FREEZING_RAIN
            samples.any { it.condition == WeatherCondition.SNOW || it.condition == WeatherCondition.SNOW_SHOWERS } ->
                WeatherScenarioKind.SNOW
            else -> null
        }

        val kind = severeKind ?: when {
            wetSamples.isNotEmpty() && (
                (totalPrecip ?: 0.0) >= 2.0 ||
                    wetSamples.size >= 3 ||
                    samples.any { it.condition == WeatherCondition.RAIN }
                ) -> WeatherScenarioKind.RAIN
            wetSamples.isNotEmpty() -> WeatherScenarioKind.SHOWERS
            else -> drySkyKind(samples, clouds)
        }

        val timing = if (wetSamples.isEmpty()) {
            WeatherScenarioTiming.NONE
        } else {
            rainTiming(wetSamples.map { it.offset })
        }

        return ModelScenario(
            model = model,
            kind = kind,
            timing = timing,
            temperatureMinC = temperatures.minOrNull(),
            temperatureMaxC = temperatures.maxOrNull(),
            precipitationTotalMm = totalPrecip,
            cloudCoverMedianPercent = clouds.medianInt(),
            gustMaxKmh = gusts.maxOrNull()
        )
    }

    private fun drySkyKind(samples: List<Sample>, clouds: List<Int>): WeatherScenarioKind {
        val cloudMedian = clouds.medianInt()
        if (cloudMedian != null) {
            return when {
                cloudMedian < 30 -> WeatherScenarioKind.CLEAR
                cloudMedian < 90 -> WeatherScenarioKind.VARIABLE_SKY
                else -> WeatherScenarioKind.OVERCAST
            }
        }

        val knownConditions = samples.mapNotNull { it.condition }
        if (knownConditions.any { it == WeatherCondition.FOG }) return WeatherScenarioKind.OVERCAST

        // Sans cloud_cover, ne pas laisser une seule heure OVERCAST dominer les
        // onze autres heures sèches. On classe chaque état de ciel puis on prend
        // la catégorie la plus fréquente ; en cas d'égalité, VARIABLE_SKY est le
        // compromis le plus honnête entre clair et couvert.
        val dryKinds = knownConditions.mapNotNull { condition ->
            when (condition) {
                WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> WeatherScenarioKind.CLEAR
                WeatherCondition.PARTLY_CLOUDY -> WeatherScenarioKind.VARIABLE_SKY
                WeatherCondition.OVERCAST -> WeatherScenarioKind.OVERCAST
                else -> null
            }
        }
        if (dryKinds.isEmpty()) return WeatherScenarioKind.DRY_UNSPECIFIED
        val counts = dryKinds.groupingBy { it }.eachCount()
        val best = counts.values.maxOrNull() ?: return WeatherScenarioKind.DRY_UNSPECIFIED
        val tied = counts.filterValues { it == best }.keys
        return when {
            WeatherScenarioKind.VARIABLE_SKY in tied -> WeatherScenarioKind.VARIABLE_SKY
            tied.size == 1 -> tied.first()
            else -> WeatherScenarioKind.VARIABLE_SKY
        }
    }

    private fun rainTiming(offsets: List<Int>): WeatherScenarioTiming {
        if (offsets.isEmpty()) return WeatherScenarioTiming.NONE
        val sorted = offsets.sorted()
        val first = sorted.first()
        val last = sorted.last()
        if (sorted.size >= 8 || (first <= 1 && last >= 9)) return WeatherScenarioTiming.THROUGHOUT

        val median = sorted[sorted.size / 2]
        return when {
            median <= 3 -> WeatherScenarioTiming.EARLY
            median >= 8 -> WeatherScenarioTiming.LATE
            else -> WeatherScenarioTiming.MIDDLE
        }
    }

    private fun List<ModelScenario>.toScenario(
        key: ScenarioKey,
        totalModelCount: Int,
        totalFamilyCount: Int,
        weights: Map<WeatherModel, Double>,
        totalVoteWeight: Double
    ): WeatherScenario = WeatherScenario(
        kind = key.kind,
        timing = key.timing,
        modelCount = size,
        totalModelCount = totalModelCount,
        voteSharePercent = (sumOf { weights[it.model] ?: 0.0 } * 100.0 / totalVoteWeight).roundToInt(),
        familyCount = map { ForecastConsensus.groupFor(it.model) }.distinct().size,
        totalFamilyCount = totalFamilyCount,
        temperatureMinC = mapNotNull { it.temperatureMinC }.minOrNull(),
        temperatureMaxC = mapNotNull { it.temperatureMaxC }.maxOrNull(),
        precipitationMinMm = mapNotNull { it.precipitationTotalMm }.minOrNull(),
        precipitationMaxMm = mapNotNull { it.precipitationTotalMm }.maxOrNull(),
        cloudCoverMinPercent = mapNotNull { it.cloudCoverMedianPercent }.minOrNull(),
        cloudCoverMaxPercent = mapNotNull { it.cloudCoverMedianPercent }.maxOrNull(),
        gustMinKmh = mapNotNull { it.gustMaxKmh }.minOrNull(),
        gustMaxKmh = mapNotNull { it.gustMaxKmh }.maxOrNull()
    )

    private data class ScenarioKey(
        val kind: WeatherScenarioKind,
        val timing: WeatherScenarioTiming
    )

    private data class ModelScenario(
        val model: WeatherModel,
        val kind: WeatherScenarioKind,
        val timing: WeatherScenarioTiming,
        val temperatureMinC: Double?,
        val temperatureMaxC: Double?,
        val precipitationTotalMm: Double?,
        val cloudCoverMedianPercent: Int?,
        val gustMaxKmh: Double?
    )

    private data class Sample(
        val offset: Int,
        val temperature: Double?,
        val precipitation: Double?,
        val condition: WeatherCondition?,
        val cloudCover: Int?,
        val gust: Double?
    )

    private val WeatherCondition?.isWet: Boolean
        get() = when (this) {
            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN,
            WeatherCondition.FREEZING_RAIN,
            WeatherCondition.SNOW,
            WeatherCondition.RAIN_SHOWERS,
            WeatherCondition.SNOW_SHOWERS,
            WeatherCondition.THUNDERSTORM -> true
            else -> false
        }

    private val WeatherScenarioKind.importance: Int
        get() = when (this) {
            WeatherScenarioKind.THUNDERSTORM -> 8
            WeatherScenarioKind.FREEZING_RAIN -> 7
            WeatherScenarioKind.SNOW -> 6
            WeatherScenarioKind.RAIN -> 5
            WeatherScenarioKind.SHOWERS -> 4
            WeatherScenarioKind.OVERCAST -> 3
            WeatherScenarioKind.VARIABLE_SKY -> 2
            WeatherScenarioKind.CLEAR -> 1
            WeatherScenarioKind.DRY_UNSPECIFIED -> 0
            WeatherScenarioKind.OTHER -> -1
        }

    private fun List<Int>.medianInt(): Int? {
        if (isEmpty()) return null
        val middle = size / 2
        return if (size % 2 == 1) this[middle]
        else ((this[middle - 1] + this[middle]) / 2.0).roundToInt()
    }


}
