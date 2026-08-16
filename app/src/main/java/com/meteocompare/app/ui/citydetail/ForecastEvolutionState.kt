package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.ForecastEvolutionHighlight
import com.meteocompare.app.domain.model.ForecastEvolutionReport

sealed interface ForecastEvolutionState {
    data object Idle : ForecastEvolutionState
    data object Loading : ForecastEvolutionState
    data class Loaded(
        val report: ForecastEvolutionReport,
        val highlight: ForecastEvolutionHighlight?
    ) : ForecastEvolutionState
    data object Unavailable : ForecastEvolutionState
    data class Error(val message: String?) : ForecastEvolutionState
}
