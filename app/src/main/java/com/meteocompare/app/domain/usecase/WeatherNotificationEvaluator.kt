package com.meteocompare.app.domain.usecase

import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.WeatherNotification
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Décide quelles notifications méritent d'être présentées pour une ville, à
 * partir d'une prévision déjà chargée. Aucune I/O ni dépendance Android : la
 * décision est entièrement testable sur la JVM.
 *
 * Les trois signaux réutilisent les calculs existants de l'application, afin
 * qu'une notification ne contredise jamais ce que l'utilisateur voit à l'écran :
 *   - résumé quotidien : valeurs du moteur choisi ([EngineComparisonBuilder]) ;
 *   - divergence : convergence du jour ([ConfidenceCalculator.dayConfidence]) ;
 *   - changement : signal « À retenir » du suivi d'évolution
 *     ([ComputeForecastEvolutionUseCase.buildHighlight]).
 */
@Singleton
class WeatherNotificationEvaluator @Inject constructor(
    private val confidenceCalculator: ConfidenceCalculator,
    private val engineComparisonBuilder: EngineComparisonBuilder,
    private val computeForecastEvolution: ComputeForecastEvolutionUseCase
) {

    /**
     * Journée résumée : la journée en cours avant [SUMMARY_TOMORROW_FROM]
     * (heure locale de la ville), le lendemain ensuite. Un résumé du soir
     * porte ainsi sur la journée qui vient plutôt que sur celle qui s'achève.
     */
    fun summaryDate(forecast: CityForecast, now: Instant): LocalDate {
        val local = now.atZone(resolveZoneOrUtc(forecast.city.timezone))
        return if (local.toLocalTime() < SUMMARY_TOMORROW_FROM) {
            local.toLocalDate()
        } else {
            local.toLocalDate().plusDays(1)
        }
    }

    fun dailySummary(
        forecast: CityForecast,
        engineContext: ForecastEngineContext,
        now: Instant
    ): WeatherNotification.DailySummary? {
        val today = localToday(forecast, now)
        val date = summaryDate(forecast, now)
        val values = engineComparisonBuilder.build(forecast, engineContext, now)
            .firstOrNull { it.date == date }
            ?.byEngine
            ?.get(engineContext.engine)
            ?: return null
        if (values.tempMin == null && values.tempMax == null) return null

        return WeatherNotification.DailySummary(
            city = forecast.city,
            date = date,
            isToday = date == today,
            condition = values.condition,
            tempMin = values.tempMin,
            tempMax = values.tempMax,
            precipitationProbabilityPercent = values.precipitationProbabilityPercent,
            precipitationAmountMm = values.precipitationAmountMm,
            convergencePercent = confidenceCalculator
                .dayConfidence(forecast, date, engineContext)
                .convergencePercent
        )
    }

    /**
     * Première journée proche dont la convergence passe sous
     * [DIVERGENCE_THRESHOLD_PERCENT]. La journée en cours n'est considérée
     * que le matin : l'après-midi, une divergence sur une journée presque
     * écoulée n'apporte plus d'information utile.
     *
     * Une convergence calculée sur trop peu de lignées indépendantes n'est pas
     * significative : il en faut au moins [DIVERGENCE_MIN_FAMILIES].
     */
    fun modelDivergence(
        forecast: CityForecast,
        engineContext: ForecastEngineContext,
        now: Instant
    ): WeatherNotification.ModelDivergence? {
        val local = now.atZone(resolveZoneOrUtc(forecast.city.timezone))
        val today = local.toLocalDate()
        val candidates = buildList {
            if (local.toLocalTime() < DIVERGENCE_TODAY_UNTIL) add(today)
            add(today.plusDays(1))
        }
        return candidates.firstNotNullOfOrNull { date ->
            val confidence = confidenceCalculator.dayConfidence(forecast, date, engineContext)
            val percent = confidence.convergencePercent ?: return@firstNotNullOfOrNull null
            val families = confidence.tempMax?.familyCount ?: 0
            if (families < DIVERGENCE_MIN_FAMILIES || percent >= DIVERGENCE_THRESHOLD_PERCENT) {
                return@firstNotNullOfOrNull null
            }
            WeatherNotification.ModelDivergence(
                city = forecast.city,
                date = date,
                isToday = date == today,
                convergencePercent = percent
            )
        }
    }

    /**
     * Révision notable de la prévision consensus sur les prochains jours, avec
     * les mêmes seuils que le bloc « À retenir » de la fiche ville.
     */
    fun forecastChange(
        forecast: CityForecast,
        previousSamples: List<ForecastEvolutionSample>,
        now: Instant
    ): WeatherNotification.ForecastChange? {
        if (previousSamples.isEmpty()) return null
        val report = computeForecastEvolution(
            currentForecast = forecast,
            previousSamples = previousSamples,
            fetchedAt = forecast.fetchedAt
        )
        if (!report.hasUsableData) return null
        val highlight = computeForecastEvolution.buildHighlight(
            report = report,
            fromDate = localToday(forecast, now),
            maxDaysAhead = CHANGE_MAX_DAYS_AHEAD
        ) ?: return null
        return WeatherNotification.ForecastChange(city = forecast.city, highlight = highlight)
    }

    private fun localToday(forecast: CityForecast, now: Instant): LocalDate =
        now.atZone(resolveZoneOrUtc(forecast.city.timezone)).toLocalDate()

    companion object {
        /** À partir de cette heure locale, le résumé porte sur le lendemain. */
        val SUMMARY_TOMORROW_FROM: LocalTime = LocalTime.of(15, 0)

        /** Même frontière que [com.meteocompare.app.domain.model.ConfidenceLevel.LOW]. */
        const val DIVERGENCE_THRESHOLD_PERCENT: Int = 50

        const val DIVERGENCE_MIN_FAMILIES: Int = 3

        /** Au-delà de cette heure locale, seule la journée du lendemain est surveillée. */
        val DIVERGENCE_TODAY_UNTIL: LocalTime = LocalTime.NOON

        /** Aujourd'hui + 2 jours : les révisions proches sont les plus actionnables. */
        const val CHANGE_MAX_DAYS_AHEAD: Int = 2
    }
}
