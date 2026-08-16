package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastEvolutionSample
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class ComputeForecastEvolutionUseCaseTest {
    private val useCase = ComputeForecastEvolutionUseCase()
    private val date = LocalDate.of(2026, 8, 17)
    private val models = listOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_GLOBAL)

    @Test
    fun `rain revised upward is classified with comparable models only`() {
        val current = forecast(
            mapOf(
                WeatherModel.GFS to 10.0,
                WeatherModel.ECMWF to 12.0,
                WeatherModel.ICON_GLOBAL to 14.0
            )
        )
        val previous = listOf(
            sample(WeatherModel.GFS, 5.0, 1),
            sample(WeatherModel.ECMWF, 7.0, 1)
            // ICON missing intentionally: it must not be treated as zero.
        )

        val report = useCase(current, previous)
        val evolution = report.day(date)!!.variables.getValue(ForecastEvolutionVariable.PRECIPITATION)
        val revision = assertNotNull(evolution.revision).let { evolution.revision!! }

        assertEquals(2, revision.comparedModels)
        assertEquals(2, revision.increasedModels)
        assertEquals(ForecastEvolutionTrend.INCREASING, revision.trend)
        assertEquals(5.0, revision.medianDelta, 0.001)
    }

    @Test
    fun `mixed model revisions are volatile`() {
        val current = forecast(
            mapOf(
                WeatherModel.GFS to 10.0,
                WeatherModel.ECMWF to 10.0,
                WeatherModel.ICON_GLOBAL to 10.0
            )
        )
        val previous = listOf(
            sample(WeatherModel.GFS, 5.0, 1),   // +5
            sample(WeatherModel.ECMWF, 15.0, 1), // -5
            sample(WeatherModel.ICON_GLOBAL, 10.0, 1) // stable
        )

        val revision = useCase(current, previous)
            .day(date)!!
            .variables.getValue(ForecastEvolutionVariable.PRECIPITATION)
            .revision!!

        assertEquals(ForecastEvolutionTrend.VOLATILE, revision.trend)
        assertEquals(1, revision.increasedModels)
        assertEquals(1, revision.decreasedModels)
        assertEquals(1, revision.stableModels)
    }

    @Test
    fun `large strengthening rain produces highlight`() {
        val current = forecast(models.associateWith { 18.0 })
        val previous = models.map { sample(it, 7.0, 1) }
        val report = useCase(current, previous)

        val highlight = useCase.buildHighlight(report, date)

        assertNotNull(highlight)
        assertEquals(ForecastEvolutionVariable.PRECIPITATION, highlight!!.variable)
        assertEquals(ForecastEvolutionTrend.INCREASING, highlight.trend)
        assertEquals(3, highlight.dominantModels)
    }

    private fun sample(model: WeatherModel, value: Double, daysAgo: Int) = ForecastEvolutionSample(
        model = model,
        variable = ForecastEvolutionVariable.PRECIPITATION,
        targetDate = date,
        daysAgo = daysAgo,
        value = value
    )

    private fun forecast(precipByModel: Map<WeatherModel, Double>): CityForecast {
        val city = City("paris", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
        val series = precipByModel.mapValues { (model, precip) ->
            ForecastSeries(
                model = model,
                hourly = HourlyForecast(emptyList(), emptyList(), emptyList(), emptyList()),
                daily = DailyForecast(
                    dates = listOf(date),
                    tempMax = listOf(20.0),
                    tempMin = listOf(10.0),
                    precipitationSum = listOf(precip),
                    windSpeedMax = listOf(20.0)
                )
            )
        }
        return CityForecast(city = city, seriesByModel = series)
    }
}
