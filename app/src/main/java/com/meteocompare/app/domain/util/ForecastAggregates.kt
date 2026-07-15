package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.CityForecast
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** Résultat agrégé utilisé par la liste des villes et le mini-forecast widget. */
internal data class Next12hForecast(
    val temperatures: List<Double?>,
    val precipitationProbabilities: List<Int?>
)

/**
 * Agrégats partagés entre l'interface principale et les widgets.
 *
 * Les deux métriques sont calculées en une seule passe afin de réutiliser le
 * même index horaire par modèle. Les timestamps Open-Meteo étant triés, une
 * recherche binaire évite de reparcourir toute la série pour chaque heure.
 */
internal object ForecastAggregates {

    private const val HOUR_COUNT = 12
    private const val MAX_TIME_DELTA_SECONDS = 30L * 60L

    /**
     * Agrège température et probabilité de précipitation sur les 12 prochaines
     * heures en faisant une moyenne non pondérée des modèles disponibles.
     */
    fun next12h(
        forecast: CityForecast,
        now: Instant = Instant.now()
    ): Next12hForecast {
        val temperatures = ArrayList<Double?>(HOUR_COUNT)
        val precipitationProbabilities = ArrayList<Int?>(HOUR_COUNT)

        repeat(HOUR_COUNT) { hourOffset ->
            val target = now.plusSeconds(hourOffset * 3_600L)
            val temperatureValues = ArrayList<Double>(forecast.seriesByModel.size)
            val precipitationValues = ArrayList<Int>(forecast.seriesByModel.size)

            forecast.seriesByModel.values.forEach { series ->
                val timestamps = series.hourly.timestamps
                val index = timestamps.nearestIndex(target) ?: return@forEach
                val deltaSeconds = abs(timestamps[index].epochSecond - target.epochSecond)
                if (deltaSeconds > MAX_TIME_DELTA_SECONDS) return@forEach

                series.hourly.temperature2m.getOrNull(index)?.let(temperatureValues::add)
                series.hourly.precipitationProbability.getOrNull(index)
                    ?.let(precipitationValues::add)
            }

            temperatures += temperatureValues.takeIf { it.isNotEmpty() }?.average()
            precipitationProbabilities += precipitationValues
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt()
        }

        return Next12hForecast(
            temperatures = temperatures,
            precipitationProbabilities = precipitationProbabilities
        )
    }

    /** Renvoie l'index de l'instant le plus proche dans une liste triée. */
    private fun List<Instant>.nearestIndex(target: Instant): Int? {
        if (isEmpty()) return null

        val result = binarySearch(target)
        if (result >= 0) return result

        val insertionPoint = -result - 1
        return when (insertionPoint) {
            0 -> 0
            size -> lastIndex
            else -> {
                val before = this[insertionPoint - 1]
                val after = this[insertionPoint]
                if (target.epochSecond - before.epochSecond <=
                    after.epochSecond - target.epochSecond
                ) {
                    insertionPoint - 1
                } else {
                    insertionPoint
                }
            }
        }
    }
}
