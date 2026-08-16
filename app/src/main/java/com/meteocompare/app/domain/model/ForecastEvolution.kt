package com.meteocompare.app.domain.model

import java.time.Instant
import java.time.LocalDate

/** Variable affichée dans la comparaison run-to-run. */
enum class ForecastEvolutionVariable {
    TEMPERATURE,
    PRECIPITATION,
    WIND
}

/** Lecture qualitative de la révision entre la prévision actuelle et J-1. */
enum class ForecastEvolutionTrend {
    STABLE,
    INCREASING,
    DECREASING,
    VOLATILE,
    INSUFFICIENT_DATA
}

/**
 * Valeur quotidienne issue d'Open-Meteo Previous Runs.
 * [daysAgo] vaut 1, 2 ou 3 et représente l'écart fixe entre le moment où
 * la valeur a été prévue et [targetDate] : 24, 48 ou 72 heures.
 */
data class ForecastEvolutionSample(
    val model: WeatherModel,
    val variable: ForecastEvolutionVariable,
    val targetDate: LocalDate,
    val daysAgo: Int,
    val value: Double
)

/** Snapshot agrégé pour un décalage de prévision donné. */
data class ForecastEvolutionSnapshot(
    val daysAgo: Int,
    val medianValue: Double,
    val valuesByModel: Map<WeatherModel, Double>
)

/** Révision modèle-par-modèle entre l'ancien run et la prévision actuelle. */
data class ForecastRevision(
    val previousDaysAgo: Int,
    val medianDelta: Double,
    val medianAbsoluteDelta: Double,
    val increasedModels: Int,
    val decreasedModels: Int,
    val stableModels: Int,
    val comparedModels: Int,
    val deltasByModel: Map<WeatherModel, Double>,
    val trend: ForecastEvolutionTrend
) {
    val dominantModels: Int
        get() = maxOf(increasedModels, decreasedModels, stableModels)
}

/** Évolution d'une variable pour une journée cible. */
data class VariableForecastEvolution(
    val variable: ForecastEvolutionVariable,
    val targetDate: LocalDate,
    val current: ForecastEvolutionSnapshot,
    /** Snapshots triés du plus ancien au plus récent : J-3, J-2, J-1. */
    val previous: List<ForecastEvolutionSnapshot>,
    /** Révision prioritaire actuelle vs J-1 (ou dernier offset disponible). */
    val revision: ForecastRevision?
) {
    val trend: ForecastEvolutionTrend
        get() = revision?.trend ?: ForecastEvolutionTrend.INSUFFICIENT_DATA

    val allSnapshotsChronological: List<ForecastEvolutionSnapshot>
        get() = previous.sortedByDescending(ForecastEvolutionSnapshot::daysAgo) + current
}

/** Évolution disponible pour une date, par variable. */
data class DayForecastEvolution(
    val date: LocalDate,
    val variables: Map<ForecastEvolutionVariable, VariableForecastEvolution>
)

/** Résultat complet consommé par CityDetail. */
data class ForecastEvolutionReport(
    val days: List<DayForecastEvolution>,
    val fetchedAt: Instant? = null,
    val fromCache: Boolean = false
) {
    val hasUsableData: Boolean
        get() = days.any { day -> day.variables.values.any { it.revision != null } }

    fun day(date: LocalDate): DayForecastEvolution? = days.firstOrNull { it.date == date }
}

/** Signal run-to-run suffisamment important pour remonter dans « À retenir ». */
data class ForecastEvolutionHighlight(
    val targetDate: LocalDate,
    val variable: ForecastEvolutionVariable,
    val trend: ForecastEvolutionTrend,
    val medianDelta: Double,
    val comparedModels: Int,
    val dominantModels: Int,
    val previousDaysAgo: Int
)
