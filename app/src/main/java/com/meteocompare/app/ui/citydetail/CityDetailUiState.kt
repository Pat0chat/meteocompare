package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.HourlyConfidenceBand

/**
 * État de l'écran détail.
 *
 * Modélisé en sealed interface plutôt qu'en data class avec champs nullables
 * car les états sont mutuellement exclusifs : on est SOIT en train de charger,
 * SOIT en succès, SOIT en erreur. Pas de mix possible.
 */
sealed interface CityDetailUiState {

    data object Loading : CityDetailUiState

    data class Loaded(
        val forecast: CityForecast,
        val weeklyConfidence: List<DayConfidence>,
        val hourlyBands: List<HourlyConfidenceBand>
    ) : CityDetailUiState

    data class Error(val message: String) : CityDetailUiState
}
