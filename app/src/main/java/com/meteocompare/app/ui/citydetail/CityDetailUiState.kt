package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.DayNormals
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

    /**
     * État succès. Les `normals` peuvent être null si la requête réseau est
     * encore en cours (les normales se chargent en background, séparément
     * du forecast principal pour éviter de bloquer l'affichage).
     *
     * Map indexée par `DayNormals.key()` pour lookup O(1) depuis l'UI.
     */
    data class Loaded(
        val forecast: CityForecast,
        val weeklyConfidence: List<DayConfidence>,
        val hourlyBands: List<HourlyConfidenceBand>,
        val normals: Map<Int, DayNormals>? = null
    ) : CityDetailUiState

    data class Error(val message: String) : CityDetailUiState
}
