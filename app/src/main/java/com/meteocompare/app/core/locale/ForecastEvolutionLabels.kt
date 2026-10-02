package com.meteocompare.app.core.locale

import androidx.annotation.StringRes
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ForecastEvolutionHighlight
import com.meteocompare.app.domain.model.ForecastEvolutionTrend
import com.meteocompare.app.domain.model.ForecastEvolutionVariable

/**
 * Ressource de libellé d'un signal d'évolution.
 *
 * Placée dans la couche de présentation partagée afin que l'UI Compose et les
 * notifications Android utilisent exactement le même vocabulaire sans créer
 * de dépendance `notification -> ui.citydetail`.
 */
@StringRes
internal fun evolutionHighlightTitleRes(highlight: ForecastEvolutionHighlight): Int = when {
    highlight.trend == ForecastEvolutionTrend.VOLATILE -> R.string.forecast_evolution_highlight_volatile
    highlight.variable == ForecastEvolutionVariable.TEMPERATURE &&
        highlight.trend == ForecastEvolutionTrend.INCREASING -> R.string.forecast_evolution_highlight_temp_up
    highlight.variable == ForecastEvolutionVariable.TEMPERATURE -> R.string.forecast_evolution_highlight_temp_down
    highlight.variable == ForecastEvolutionVariable.PRECIPITATION &&
        highlight.trend == ForecastEvolutionTrend.INCREASING -> R.string.forecast_evolution_highlight_precip_up
    highlight.variable == ForecastEvolutionVariable.PRECIPITATION -> R.string.forecast_evolution_highlight_precip_down
    highlight.variable == ForecastEvolutionVariable.WIND &&
        highlight.trend == ForecastEvolutionTrend.INCREASING -> R.string.forecast_evolution_highlight_wind_up
    else -> R.string.forecast_evolution_highlight_wind_down
}
