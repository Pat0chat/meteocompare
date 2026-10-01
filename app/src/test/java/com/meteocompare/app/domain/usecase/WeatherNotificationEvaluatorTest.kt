package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherNotificationEvaluatorTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 9, 28)
    private val tomorrow = today.plusDays(1)
    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35,
        timezone = "Europe/Paris"
    )
    private val models = listOf(
        WeatherModel.GFS,
        WeatherModel.ECMWF,
        WeatherModel.ICON_GLOBAL,
        WeatherModel.UKMO_GLOBAL
    )
    private val calculator = ConfidenceCalculator(EqualWeighting())
    private val evaluator = WeatherNotificationEvaluator(
        confidenceCalculator = calculator,
        engineComparisonBuilder = EngineComparisonBuilder(calculator),
        computeForecastEvolution = ComputeForecastEvolutionUseCase()
    )
    private val context = ForecastEngineContext.DEFAULT

    // ───────────────────────────── Résumé ─────────────────────────────

    @Test
    fun `le resume porte sur aujourd'hui le matin et sur demain le soir`() {
        val forecast = forecast(models.associateWith { listOf(calm, calm) })

        assertEquals(today, evaluator.summaryDate(forecast, at(7)))
        assertEquals(tomorrow, evaluator.summaryDate(forecast, at(18)))
    }

    @Test
    fun `resume quotidien - valeurs du moteur et convergence du jour`() {
        val forecast = forecast(models.associateWith { listOf(calm, calm) })

        val summary = evaluator.dailySummary(forecast, context, at(7))

        assertNotNull(summary)
        summary!!
        assertEquals(today, summary.date)
        assertTrue(summary.isToday)
        assertEquals(22.0, summary.tempMax!!, 0.01)
        assertEquals(12.0, summary.tempMin!!, 0.01)
        assertEquals(10.0, summary.windKmh!!, 0.01)
        assertEquals(WeatherCondition.CLEAR, summary.condition)
        assertTrue((summary.convergencePercent ?: 0) >= 80)
        assertEquals("daily|paris|$today", summary.dedupKey)
    }

    @Test
    fun `resume du soir - journee du lendemain`() {
        val forecast = forecast(models.associateWith { listOf(calm, calm.copy(tMax = 25.0)) })

        val summary = evaluator.dailySummary(forecast, context, at(20))!!

        assertEquals(tomorrow, summary.date)
        assertEquals(false, summary.isToday)
        assertEquals(25.0, summary.tempMax!!, 0.01)
    }

    // ─────────────────────────── Divergence ───────────────────────────

    @Test
    fun `modeles convergents - aucune alerte de divergence`() {
        val forecast = forecast(models.associateWith { listOf(calm, calm) })

        assertNull(evaluator.modelDivergence(forecast, context, at(8)))
    }

    @Test
    fun `modeles en desaccord demain - alerte pour demain`() {
        val forecast = forecast(divergingTomorrow(models))

        val divergence = evaluator.modelDivergence(forecast, context, at(18))

        assertNotNull(divergence)
        assertEquals(tomorrow, divergence!!.date)
        assertEquals(false, divergence.isToday)
        assertTrue(divergence.convergencePercent < WeatherNotificationEvaluator.DIVERGENCE_THRESHOLD_PERCENT)
    }

    @Test
    fun `desaccord aujourd'hui - ignore l'apres-midi`() {
        val forecast = forecast(divergingToday(models))

        assertNotNull(evaluator.modelDivergence(forecast, context, at(8)))
        assertNull(evaluator.modelDivergence(forecast, context, at(15)))
    }

    @Test
    fun `trop peu de lignees independantes - pas d'alerte de divergence`() {
        val forecast = forecast(divergingTomorrow(listOf(WeatherModel.GFS, WeatherModel.ECMWF)))

        assertNull(evaluator.modelDivergence(forecast, context, at(18)))
    }

    // ───────────────────────── Changement ─────────────────────────

    @Test
    fun `pluie nettement revue a la hausse - alerte de changement`() {
        val forecast = forecast(models.associateWith { listOf(calm, calm.copy(precip = 18.0)) })
        val previous = models.map { precipitationSample(it, tomorrow, 2.0) }

        val change = evaluator.forecastChange(forecast, previous, at(9))

        assertNotNull(change)
        assertEquals(tomorrow, change!!.highlight.targetDate)
        assertEquals(ForecastEvolutionVariable.PRECIPITATION, change.highlight.variable)
        assertEquals(ForecastEvolutionTrend.INCREASING, change.highlight.trend)
        assertEquals("change|paris|$tomorrow|PRECIPITATION|INCREASING", change.dedupKey)
    }

    @Test
    fun `revision au-dela de l'horizon surveille - pas d'alerte`() {
        val farDate = today.plusDays(WeatherNotificationEvaluator.CHANGE_MAX_DAYS_AHEAD + 2L)
        val dates = (0..WeatherNotificationEvaluator.CHANGE_MAX_DAYS_AHEAD + 2).map { today.plusDays(it.toLong()) }
        val forecast = forecast(
            byModel = models.associateWith { dates.map { date -> if (date == farDate) calm.copy(precip = 18.0) else calm } },
            dates = dates
        )
        val previous = models.map { precipitationSample(it, farDate, 2.0) }

        assertNull(evaluator.forecastChange(forecast, previous, at(9)))
    }

    @Test
    fun `sans historique local - pas d'alerte de changement`() {
        val forecast = forecast(models.associateWith { listOf(calm, calm) })

        assertNull(evaluator.forecastChange(forecast, emptyList(), at(9)))
    }

    // ───────────────────────────── Fixtures ─────────────────────────────

    private data class Day(
        val tMax: Double,
        val tMin: Double,
        val precip: Double,
        val wind: Double,
        val code: Int
    )

    private val calm = Day(tMax = 22.0, tMin = 12.0, precip = 0.0, wind = 10.0, code = 0)

    /** Dispersion extrême sur toutes les variables : aucune lecture commune possible. */
    private val scattered = listOf(
        Day(tMax = 8.0, tMin = 0.0, precip = 0.0, wind = 5.0, code = 0),
        Day(tMax = 30.0, tMin = 20.0, precip = 35.0, wind = 80.0, code = 95),
        Day(tMax = 16.0, tMin = 6.0, precip = 0.0, wind = 40.0, code = 3),
        Day(tMax = 24.0, tMin = 15.0, precip = 20.0, wind = 60.0, code = 63)
    )

    private fun divergingTomorrow(models: List<WeatherModel>): Map<WeatherModel, List<Day>> =
        models.withIndex().associate { (index, model) -> model to listOf(calm, scattered[index]) }

    private fun divergingToday(models: List<WeatherModel>): Map<WeatherModel, List<Day>> =
        models.withIndex().associate { (index, model) -> model to listOf(scattered[index], calm) }

    private fun forecast(
        byModel: Map<WeatherModel, List<Day>>,
        dates: List<LocalDate> = listOf(today, tomorrow)
    ): CityForecast = CityForecast(
        city = city,
        seriesByModel = byModel.mapValues { (model, days) ->
            ForecastSeries(
                model = model,
                hourly = HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList()),
                daily = DailyForecast(
                    dates = dates,
                    tempMax = days.map { it.tMax },
                    tempMin = days.map { it.tMin },
                    precipitationSum = days.map { it.precip },
                    windSpeedMax = days.map { it.wind },
                    weatherCode = days.map { it.code }
                )
            )
        },
        fetchedAt = at(6)
    )

    private fun precipitationSample(model: WeatherModel, date: LocalDate, value: Double) =
        ForecastEvolutionSample(
            model = model,
            variable = ForecastEvolutionVariable.PRECIPITATION,
            targetDate = date,
            daysAgo = 1,
            value = value
        )

    private fun at(hour: Int): Instant = today.atTime(hour, 0).atZone(zone).toInstant()
}
