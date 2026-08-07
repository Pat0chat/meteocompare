package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Instant
import kotlin.math.roundToInt

/** Résultat agrégé utilisé par la liste des villes et le mini-forecast widget. */
internal data class Next12hForecast(
    /** Première échéance réellement visée par les listes ci-dessous. */
    val startInstant: Instant,
    val temperatures: List<Double?>,
    val precipitationProbabilities: List<Int?>,
    val precipitationAmountsMm: List<Double?>,
    /** Condition météo de consensus par heure, alignée sur les autres listes. */
    val conditions: List<WeatherCondition?>
)

/**
 * Agrégats partagés entre l'interface principale et les widgets.
 *
 * Les quatre signaux sont calculés en une seule passe afin de réutiliser le
 * même index horaire par modèle. Les timestamps Open-Meteo étant triés, une
 * recherche binaire évite de reparcourir toute la série pour chaque heure.
 */
internal object ForecastAggregates {

    private const val HOUR_COUNT = 12

    /**
     * Agrège température, quantité de pluie, probabilité de précipitation et
     * condition météo sur les 12 prochaines heures. Les valeurs numériques
     * utilisent une moyenne non pondérée. Quand [includeConditions] est vrai,
     * la condition utilise un vote par famille WMO, car un code catégoriel ne
     * peut pas être moyenné. Le calcul est désactivé par défaut pour ne pas
     * alourdir les cartes de villes qui n'affichent pas ces pictogrammes.
     */
    fun next12h(
        forecast: CityForecast,
        now: Instant = Instant.now(),
        includeConditions: Boolean = false
    ): Next12hForecast {
        val temperatures = ArrayList<Double?>(HOUR_COUNT)
        val precipitationProbabilities = ArrayList<Int?>(HOUR_COUNT)
        val precipitationAmountsMm = ArrayList<Double?>(HOUR_COUNT)
        val conditions = if (includeConditions) {
            ArrayList<WeatherCondition?>(HOUR_COUNT)
        } else {
            null
        }

        val startInstant = HourlySampling.anchor(forecast, now)
        repeat(HOUR_COUNT) { hourOffset ->
            val target = startInstant.plusSeconds(hourOffset * 3_600L)
            val temperatureValues = ArrayList<Double>(forecast.seriesByModel.size)
            val precipitationValues = ArrayList<Int>(forecast.seriesByModel.size)
            val precipitationAmountValues = ArrayList<Double>(forecast.seriesByModel.size)
            val conditionValues = if (includeConditions) {
                ArrayList<WeatherCondition>(forecast.seriesByModel.size)
            } else {
                null
            }

            forecast.seriesByModel.values.forEach { series ->
                val timestamps = series.hourly.timestamps
                val index = with(HourlySampling) { timestamps.nearestIndex(target) } ?: return@forEach
                if (!HourlySampling.isCloseEnough(timestamps[index], target)) return@forEach

                series.hourly.temperature2m.getOrNull(index)?.let(temperatureValues::add)
                series.hourly.precipitationProbability.getOrNull(index)
                    ?.let(precipitationValues::add)
                val precipitation = series.hourly.precipitation.getOrNull(index)
                precipitation?.let(precipitationAmountValues::add)

                // Les codes WMO sont catégoriels : on vote par famille, sans
                // jamais moyenner les codes numériques. Si le code manque
                // (réponse partielle/ancien cache), on n'infère que depuis
                // les précipitations et la température du même modèle.
                if (conditionValues != null) {
                    val condition = WeatherCondition
                        .fromWmoCode(series.hourly.weatherCode.getOrNull(index))
                        ?.takeUnless { it == WeatherCondition.UNKNOWN }
                        ?: WeatherCondition.inferFromPrecipAndTemp(
                            precipMm = precipitation,
                            tempMinC = series.hourly.temperature2m.getOrNull(index)
                        )
                    condition?.let(conditionValues::add)
                }
            }

            temperatures += temperatureValues.takeIf { it.isNotEmpty() }?.average()
            precipitationProbabilities += precipitationValues
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt()
            precipitationAmountsMm += precipitationAmountValues
                .takeIf { it.isNotEmpty() }
                ?.average()
            if (conditions != null && conditionValues != null) {
                conditions += conditionConsensus(conditionValues)
            }
        }

        return Next12hForecast(
            startInstant = startInstant,
            temperatures = temperatures,
            precipitationProbabilities = precipitationProbabilities,
            precipitationAmountsMm = precipitationAmountsMm,
            conditions = conditions.orEmpty()
        )
    }

    /**
     * Vote majoritaire conservateur pour une donnée catégorielle. En cas
     * d'égalité, la condition la plus importante pour l'utilisateur gagne
     * (pluie plutôt que ciel clair, orage plutôt que pluie).
     */
    internal fun conditionConsensus(values: List<WeatherCondition>): WeatherCondition? {
        if (values.isEmpty()) return null
        val counts = values.groupingBy { it }.eachCount()
        val bestCount = counts.values.maxOrNull() ?: return null
        return counts
            .filterValues { it == bestCount }
            .keys
            .maxByOrNull { it.severityRank }
    }


}
