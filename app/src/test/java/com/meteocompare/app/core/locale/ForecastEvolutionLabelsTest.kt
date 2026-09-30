package com.meteocompare.app.core.locale

import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ForecastEvolutionHighlight
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastEvolutionLabelsTest {
    @Test
    fun `chaque combinaison fonctionnelle pointe vers le libelle partage attendu`() {
        val cases = listOf(
            Triple(ForecastEvolutionVariable.TEMPERATURE, ForecastEvolutionTrend.INCREASING, R.string.forecast_evolution_highlight_temp_up),
            Triple(ForecastEvolutionVariable.TEMPERATURE, ForecastEvolutionTrend.DECREASING, R.string.forecast_evolution_highlight_temp_down),
            Triple(ForecastEvolutionVariable.PRECIPITATION, ForecastEvolutionTrend.INCREASING, R.string.forecast_evolution_highlight_precip_up),
            Triple(ForecastEvolutionVariable.PRECIPITATION, ForecastEvolutionTrend.DECREASING, R.string.forecast_evolution_highlight_precip_down),
            Triple(ForecastEvolutionVariable.WIND, ForecastEvolutionTrend.INCREASING, R.string.forecast_evolution_highlight_wind_up),
            Triple(ForecastEvolutionVariable.WIND, ForecastEvolutionTrend.DECREASING, R.string.forecast_evolution_highlight_wind_down),
            Triple(ForecastEvolutionVariable.WIND, ForecastEvolutionTrend.VOLATILE, R.string.forecast_evolution_highlight_volatile)
        )

        cases.forEach { (variable, trend, expected) ->
            assertEquals(expected, evolutionHighlightTitleRes(highlight(variable, trend)))
        }
    }

    private fun highlight(
        variable: ForecastEvolutionVariable,
        trend: ForecastEvolutionTrend
    ) = ForecastEvolutionHighlight(
        targetDate = LocalDate.of(2026, 9, 30),
        variable = variable,
        trend = trend,
        medianDelta = 1.0,
        comparedModels = 3,
        dominantModels = 2,
        previousAgeHours = 24
    )
}
