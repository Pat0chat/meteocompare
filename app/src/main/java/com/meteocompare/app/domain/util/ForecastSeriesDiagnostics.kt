package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.ForecastSeries
import java.time.Duration
import java.time.Instant

/**
 * Diagnostic structurel d'une série horaire.
 *
 * Les absences de début/fin d'horizon sont normales pour certains modèles et ne
 * sont pas considérées comme des « trous internes ». En revanche, une séquence
 * de valeurs nulles encadrée par des valeurs valides, ou un saut de timestamps
 * supérieur à une heure au milieu de la série, est explicitement signalé.
 */
internal object ForecastSeriesDiagnostics {
    const val LONG_INTERNAL_MISSING_RUN_HOURS = 3

    enum class Variable {
        TEMPERATURE,
        PRECIPITATION,
        PRECIPITATION_PROBABILITY,
        CLOUD_COVER,
        WIND_SPEED,
        WIND_GUST,
        WIND_DIRECTION,
        WEATHER_CODE
    }

    data class MissingRun(
        val variable: Variable,
        val startIndex: Int,
        val endIndexInclusive: Int,
        val lengthHours: Int,
        val startInstant: Instant?,
        val endInstant: Instant?
    )

    data class TimestampGap(
        val before: Instant,
        val after: Instant,
        /** Nombre d'échéances horaires absentes entre les deux timestamps. */
        val missingHours: Int
    )

    data class Diagnostic(
        val timestampGaps: List<TimestampGap>,
        val internalMissingRuns: List<MissingRun>
    ) {
        val hasLongInternalMissingSequence: Boolean
            get() = timestampGaps.any { it.missingHours >= LONG_INTERNAL_MISSING_RUN_HOURS } ||
                internalMissingRuns.any { it.lengthHours >= LONG_INTERNAL_MISSING_RUN_HOURS }

        val longestInternalMissingSequenceHours: Int
            get() = maxOf(
                timestampGaps.maxOfOrNull(TimestampGap::missingHours) ?: 0,
                internalMissingRuns.maxOfOrNull(MissingRun::lengthHours) ?: 0
            )
    }

    fun analyze(series: ForecastSeries): Diagnostic {
        val hourly = series.hourly
        val timestampGaps = hourly.timestamps.zipWithNext().mapNotNull { (before, after) ->
            val seconds = Duration.between(before, after).seconds
            if (seconds <= 3_600L) return@mapNotNull null
            // 2h entre deux timestamps = une échéance horaire absente.
            val missing = (seconds / 3_600L).toInt() - 1
            if (missing > 0) TimestampGap(before, after, missing) else null
        }

        val runs = buildList {
            addAll(findInternalRuns(Variable.TEMPERATURE, hourly.temperature2m, hourly.timestamps))
            addAll(findInternalRuns(Variable.PRECIPITATION, hourly.precipitation, hourly.timestamps))
            addAll(findInternalRuns(Variable.PRECIPITATION_PROBABILITY, hourly.precipitationProbability, hourly.timestamps))
            addAll(findInternalRuns(Variable.CLOUD_COVER, hourly.cloudCover, hourly.timestamps))
            addAll(findInternalRuns(Variable.WIND_SPEED, hourly.windSpeed10m, hourly.timestamps))
            addAll(findInternalRuns(Variable.WIND_GUST, hourly.windGusts10m, hourly.timestamps))
            addAll(findInternalRuns(Variable.WIND_DIRECTION, hourly.windDirection10m, hourly.timestamps))
            addAll(findInternalRuns(Variable.WEATHER_CODE, hourly.weatherCode, hourly.timestamps))
        }

        return Diagnostic(timestampGaps = timestampGaps, internalMissingRuns = runs)
    }

    private fun <T> findInternalRuns(
        variable: Variable,
        values: List<T?>,
        timestamps: List<Instant>
    ): List<MissingRun> {
        if (values.size < 3) return emptyList()
        val limit = minOf(values.size, timestamps.size)
        if (limit < 3) return emptyList()

        val firstValid = (0 until limit).firstOrNull { values[it] != null } ?: return emptyList()
        val lastValid = (limit - 1 downTo 0).firstOrNull { values[it] != null } ?: return emptyList()
        if (lastValid - firstValid < 2) return emptyList()

        val result = mutableListOf<MissingRun>()
        var index = firstValid + 1
        while (index < lastValid) {
            if (values[index] != null) {
                index++
                continue
            }
            val start = index
            while (index <= lastValid && values[index] == null) index++
            val end = index - 1
            // La borne droite est valide puisque lastValid est la dernière valeur non nulle.
            result += MissingRun(
                variable = variable,
                startIndex = start,
                endIndexInclusive = end,
                lengthHours = end - start + 1,
                startInstant = timestamps.getOrNull(start),
                endInstant = timestamps.getOrNull(end)
            )
        }
        return result
    }
}
