package com.meteocompare.app.domain.model

import java.time.Instant
import java.time.LocalDate

/** Variable affichée dans la comparaison d’évolution des prévisions. */
enum class ForecastEvolutionVariable {
    TEMPERATURE,
    PRECIPITATION,
    WIND
}

/** Lecture qualitative d'une révision entre la prévision actuelle et un snapshot local antérieur. */
enum class ForecastEvolutionTrend {
    STABLE,
    INCREASING,
    DECREASING,
    VOLATILE,
    INSUFFICIENT_DATA
}

/**
 * Valeur quotidienne provenant d'un snapshot local d'une prévision enregistrée
 * lors d'un refresh frais. [daysAgo] identifie la cible logique (~24/~48/~72 h), tandis que
 * [ageHours] et [capturedAt] décrivent l'âge réel du snapshot retenu.
 */
data class ForecastEvolutionSample(
    val model: WeatherModel,
    val variable: ForecastEvolutionVariable,
    val targetDate: LocalDate,
    val daysAgo: Int,
    val value: Double,
    val ageHours: Int = daysAgo * 24,
    val capturedAt: Instant? = null
)

/** Snapshot agrégé pour un décalage de prévision donné. */
data class ForecastEvolutionSnapshot(
    val daysAgo: Int,
    val medianValue: Double,
    val valuesByModel: Map<WeatherModel, Double>,
    val ageHours: Int = daysAgo * 24,
    val capturedAt: Instant? = null
)

/** Révision modèle-par-modèle entre un snapshot local antérieur et la prévision actuelle. */
data class ForecastRevision(
    val previousDaysAgo: Int,
    val previousAgeHours: Int,
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
    /** Snapshots triés du plus ancien au plus récent (cibles ~72/~48/~24 h). */
    val previous: List<ForecastEvolutionSnapshot>,
    /** Révision prioritaire actuelle vs le snapshot disponible le plus proche de ~24 h. */
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
    val fetchedAt: Instant? = null
) {
    val hasUsableData: Boolean
        get() = days.any { day -> day.variables.values.any { it.revision != null } }

    fun day(date: LocalDate): DayForecastEvolution? = days.firstOrNull { it.date == date }
}

/** Signal d’évolution suffisamment important pour remonter dans « À retenir ». */
data class ForecastEvolutionHighlight(
    val targetDate: LocalDate,
    val variable: ForecastEvolutionVariable,
    val trend: ForecastEvolutionTrend,
    val medianDelta: Double,
    val comparedModels: Int,
    val dominantModels: Int,
    val previousAgeHours: Int
)
