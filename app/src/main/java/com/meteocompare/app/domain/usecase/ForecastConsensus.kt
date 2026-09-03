package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.ForecastPhysicalLimits
import com.meteocompare.app.domain.model.WeatherModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Primitives statistiques partagées du consensus robuste MeteoCompare.
 *
 * Les sorties brutes des modèles ne sont jamais modifiées. Ce composant porte
 * l'équilibrage par lignée, les agrégats continus, la pluie et le vote brut.
 * La condition météo discrète est résolue séparément par
 * [WeatherConditionConsensus].
 */
object ForecastConsensus {
    private const val EPS = 1e-12
    private const val OCCURRENCE_TIGHT_STDDEV_PERCENT = 5.0
    private const val OCCURRENCE_WIDE_STDDEV_PERCENT = 30.0

    enum class Group {
        MF_AROME, MF_ARPEGE, DWD_ICON, ECMWF_GLOBAL,
        NOAA_GFS, NOAA_HRRR, UKMO_GLOBAL, ECCC_GEM,
        METNO_NORDIC, UWC_HARMONIE, BOM_ACCESS, CMA_GRAPES
    }

    fun groupFor(model: WeatherModel): Group = when (model) {
        WeatherModel.AROME_FRANCE_HD, WeatherModel.AROME_FRANCE -> Group.MF_AROME
        WeatherModel.ARPEGE_EUROPE, WeatherModel.ARPEGE_WORLD -> Group.MF_ARPEGE
        WeatherModel.ICON_D2, WeatherModel.ICON_EU, WeatherModel.ICON_GLOBAL,
        WeatherModel.METEOSWISS_ICON_CH2 -> Group.DWD_ICON
        WeatherModel.ECMWF, WeatherModel.ECMWF_AIFS -> Group.ECMWF_GLOBAL
        WeatherModel.GFS -> Group.NOAA_GFS
        WeatherModel.HRRR_CONUS -> Group.NOAA_HRRR
        WeatherModel.UKMO_GLOBAL -> Group.UKMO_GLOBAL
        WeatherModel.GEM_GLOBAL -> Group.ECCC_GEM
        WeatherModel.METNO_NORDIC -> Group.METNO_NORDIC
        WeatherModel.KNMI_HARMONIE_EU, WeatherModel.DMI_HARMONIE_EU -> Group.UWC_HARMONIE
        WeatherModel.BOM_ACCESS -> Group.BOM_ACCESS
        WeatherModel.CMA_GRAPES -> Group.CMA_GRAPES
    }

    data class Entry<T>(val model: WeatherModel, val value: T)
    data class WeightedEntry(val model: WeatherModel, val value: Double, val weight: Double)
    data class Stats(
        val mean: Double,
        val stdDev: Double,
        val min: Double,
        val max: Double,
        val count: Int,
        val totalWeight: Double
    )
    data class Continuous(
        val central: Double?,
        val convergencePercent: Int?,
        val modelCount: Int,
        val familyCount: Int,
        val stats: Stats?
    )

    enum class PrecipitationSource { PROBABILITY, MIXED, MODEL_AGREEMENT }
    data class PrecipitationRow(
        val model: WeatherModel,
        val amountMm: Double? = null,
        val probabilityPercent: Int? = null
    )
    data class Precipitation(
        val probabilityPercent: Int?,
        val conditionalAmountMm: Double?,
        val centralAmountMm: Double?,
        val expectedAmountMm: Double?,
        val convergencePercent: Int?,
        val modelCount: Int,
        val familyCount: Int,
        val wetModelCount: Int,
        val wetFamilyCount: Int,
        val source: PrecipitationSource?,
        val minMm: Double?,
        val maxMm: Double?,
        val conditionalStdDev: Double?
    )

    data class Vote<T>(
        val value: T?,
        val percent: Int?,
        val modelCount: Int,
        val familyCount: Int
    )

    /**
     * Chaque lignée reçoit une masse totale voisine de 1. Les multiplicateurs
     * locaux sont bornés à 0,5–1,5 et la masse finale de la famille à 0,75–1,25.
     */
    fun familyBalancedWeights(
        models: Collection<WeatherModel>,
        localWeights: Map<WeatherModel, Double> = emptyMap()
    ): Map<WeatherModel, Double> {
        val unique = models.distinct()
        val groups = unique.groupBy(::groupFor)
        return buildMap {
            groups.values.forEach { siblings ->
                val raw = siblings.associateWith { model ->
                    (localWeights[model] ?: 1.0).takeIf(Double::isFinite)?.coerceIn(0.5, 1.5) ?: 1.0
                }
                val total = raw.values.sum().takeIf { it > 0.0 } ?: 1.0
                val groupMass = (total / siblings.size).coerceIn(0.75, 1.25)
                siblings.forEach { model -> put(model, raw.getValue(model) / total * groupMass) }
            }
        }
    }

    fun continuous(
        entries: List<Entry<Double>>,
        localWeights: Map<WeatherModel, Double> = emptyMap(),
        tightStdDev: Double,
        wideStdDev: Double
    ): Continuous {
        val valid = entries.filter { it.value.isFinite() }
        if (valid.isEmpty()) return Continuous(null, null, 0, 0, null)
        val weights = familyBalancedWeights(valid.map { it.model }, localWeights)
        val weighted = valid.mapNotNull { row ->
            val weight = weights[row.model] ?: return@mapNotNull null
            if (weight <= 0.0) null else WeightedEntry(row.model, row.value, weight)
        }
        val stats = weightedStats(weighted) ?: return Continuous(null, null, 0, 0, null)
        val families = valid.map { groupFor(it.model) }.distinct().size
        return Continuous(
            central = weightedMedian(weighted),
            convergencePercent = if (families >= 2) scoreFromDispersion(stats.stdDev, tightStdDev, wideStdDev) else null,
            modelCount = valid.map { it.model }.distinct().size,
            familyCount = families,
            stats = stats
        )
    }

    fun precipitation(
        rows: List<PrecipitationRow>,
        thresholdMm: Double,
        localWeights: Map<WeatherModel, Double> = emptyMap(),
        amountTightStdDev: Double,
        amountWideStdDev: Double
    ): Precipitation {
        val usable = rows.map { row ->
            row.copy(
                amountMm = ForecastPhysicalLimits.precipitation(row.amountMm),
                probabilityPercent = ForecastPhysicalLimits.percentage(row.probabilityPercent)
            )
        }.filter { row ->
            row.amountMm != null || row.probabilityPercent != null
        }
        if (usable.isEmpty()) return Precipitation(null, null, null, null, null, 0, 0, 0, 0, null, null, null, null)

        val occurrenceWeights = familyBalancedWeights(usable.map { it.model }, localWeights)
        var probabilitySum = 0.0
        var totalWeight = 0.0
        var nativeProbabilityCount = 0
        val occurrenceByModel = linkedMapOf<WeatherModel, Double>()
        usable.forEach { row ->
            val weight = occurrenceWeights[row.model] ?: return@forEach
            val rowProbability = row.probabilityPercent?.takeIf { it in 0..100 }?.let {
                nativeProbabilityCount++
                it / 100.0
            } ?: if ((row.amountMm ?: Double.NEGATIVE_INFINITY) > thresholdMm) 1.0 else 0.0
            occurrenceByModel[row.model] = rowProbability
            probabilitySum += weight * rowProbability
            totalWeight += weight
        }
        val p = if (totalWeight > 0.0) probabilitySum / totalWeight else null
        val wet = usable.filter { (it.amountMm ?: Double.NEGATIVE_INFINITY) > thresholdMm }
        val wetWeights = familyBalancedWeights(wet.map { it.model }, localWeights)
        val weightedWet = wet.mapNotNull { row ->
            val amount = row.amountMm ?: return@mapNotNull null
            val weight = wetWeights[row.model] ?: return@mapNotNull null
            WeightedEntry(row.model, amount, weight)
        }
        val conditional = weightedMedian(weightedWet)
        val amountStats = weightedStats(weightedWet)
        val familyCount = usable.map { groupFor(it.model) }.distinct().size
        val wetFamilyCount = wet.map { groupFor(it.model) }.distinct().size

        // La convergence pluie mesure l'accord entre familles, pas le niveau
        // moyen de probabilité. Ainsi [50,50,50] est parfaitement convergent,
        // tout comme [80,80,80], alors que [100,60,80] l'est moins.
        val familyProbabilities = usable
            .groupBy { groupFor(it.model) }
            .mapNotNull { (_, familyRows) ->
                var sum = 0.0
                var mass = 0.0
                familyRows.forEach { row ->
                    val value = occurrenceByModel[row.model] ?: return@forEach
                    val weight = occurrenceWeights[row.model] ?: return@forEach
                    if (weight <= 0.0) return@forEach
                    sum += value * weight
                    mass += weight
                }
                if (mass > 0.0) sum / mass else null
            }
        val convergence = if (familyProbabilities.size >= 2) {
            val mean = familyProbabilities.average()
            val variance = familyProbabilities.sumOf { value ->
                val delta = value - mean
                delta * delta
            } / familyProbabilities.size
            scoreFromDispersion(
                stdDev = sqrt(variance) * 100.0,
                tight = OCCURRENCE_TIGHT_STDDEV_PERCENT,
                wide = OCCURRENCE_WIDE_STDDEV_PERCENT
            )
        } else null
        val source = when {
            nativeProbabilityCount == usable.size -> PrecipitationSource.PROBABILITY
            nativeProbabilityCount > 0 -> PrecipitationSource.MIXED
            else -> PrecipitationSource.MODEL_AGREEMENT
        }
        val probabilityPercent = p?.let { (it * 100.0).roundToInt().coerceIn(0, 100) }
        val finiteAmounts = usable.mapNotNull { it.amountMm?.takeIf(Double::isFinite) }
        // Ne jamais transformer « quantité inconnue » en 0 mm. Avec au moins
        // une quantité déterministe disponible, 0 reste en revanche un résultat
        // valide lorsque le scénario central est sec.
        val central = when {
            finiteAmounts.isEmpty() -> null
            p != null && p >= 0.5 && conditional != null -> conditional
            else -> 0.0
        }
        val expected = when {
            p != null && conditional != null -> p * conditional
            finiteAmounts.isNotEmpty() && wet.isEmpty() -> 0.0
            else -> null
        }
        return Precipitation(
            probabilityPercent = probabilityPercent,
            conditionalAmountMm = conditional,
            centralAmountMm = central,
            expectedAmountMm = expected,
            convergencePercent = convergence,
            modelCount = usable.size,
            familyCount = familyCount,
            wetModelCount = wet.size,
            wetFamilyCount = wetFamilyCount,
            source = source,
            minMm = usable.mapNotNull { it.amountMm?.takeIf(Double::isFinite) }.minOrNull(),
            maxMm = usable.mapNotNull { it.amountMm?.takeIf(Double::isFinite) }.maxOrNull(),
            conditionalStdDev = amountStats?.stdDev
        )
    }

    fun <T> vote(
        entries: List<Entry<T>>,
        localWeights: Map<WeatherModel, Double> = emptyMap(),
        severity: (T) -> Int = { 0 }
    ): Vote<T> {
        if (entries.isEmpty()) return Vote(null, null, 0, 0)
        val weights = familyBalancedWeights(entries.map { it.model }, localWeights)
        val votes = linkedMapOf<T, Double>()
        entries.forEach { row -> votes[row.value] = (votes[row.value] ?: 0.0) + (weights[row.model] ?: 0.0) }
        if (votes.isEmpty()) return Vote(null, null, 0, 0)
        val top = votes.values.maxOrNull() ?: 0.0
        val value = votes.filterValues { abs(it - top) <= EPS }.keys.maxByOrNull(severity)
        val total = votes.values.sum()
        val families = entries.map { groupFor(it.model) }.distinct().size
        return Vote(
            value = value,
            percent = if (families >= 2 && total > 0.0) (top * 100.0 / total).roundToInt().coerceIn(0, 100) else null,
            modelCount = entries.map { it.model }.distinct().size,
            familyCount = families
        )
    }

    fun weightedMedian(entries: List<WeightedEntry>): Double? {
        val rows = entries.filter { it.value.isFinite() && it.weight.isFinite() && it.weight > 0.0 }.sortedBy { it.value }
        if (rows.isEmpty()) return null
        val total = rows.sumOf { it.weight }
        val half = total / 2.0
        var cumulative = 0.0
        rows.forEachIndexed { index, row ->
            cumulative += row.weight
            if (cumulative > half + EPS) return row.value
            if (abs(cumulative - half) <= EPS && index + 1 < rows.size) return (row.value + rows[index + 1].value) / 2.0
        }
        return rows.last().value
    }

    fun weightedStats(entries: List<WeightedEntry>): Stats? {
        val rows = entries.filter { it.value.isFinite() && it.weight.isFinite() && it.weight > 0.0 }
        if (rows.isEmpty()) return null
        val total = rows.sumOf { it.weight }
        val mean = rows.sumOf { it.value * it.weight } / total
        val variance = rows.sumOf { it.weight * (it.value - mean) * (it.value - mean) } / total
        return Stats(mean, sqrt(variance), rows.minOf { it.value }, rows.maxOf { it.value }, rows.size, total)
    }

    fun scoreFromDispersion(stdDev: Double, tight: Double, wide: Double): Int = when {
        !stdDev.isFinite() -> 0
        stdDev <= tight -> 100
        stdDev >= wide -> 0
        else -> (100.0 * (1.0 - (stdDev - tight) / (wide - tight))).roundToInt().coerceIn(0, 100)
    }


}
