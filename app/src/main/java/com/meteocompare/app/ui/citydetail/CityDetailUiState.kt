package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.usecase.DayConditionsRow
import java.time.Instant

/**
 * État de l'écran détail.
 *
 * Modélisé en sealed interface plutôt qu'en data class avec champs nullables
 * car les états sont mutuellement exclusifs : on est SOIT en train de charger,
 * SOIT en succès, SOIT en erreur.
 */
sealed interface CityDetailUiState {

    data object Loading : CityDetailUiState

    /**
     * État succès. Les `normals` peuvent être null si la requête réseau est
     * encore en cours (chargement séparé, en parallèle du forecast).
     *
     * `hourlyBands` (température), `hourlyPrecipBands` (précipitations), et
     * `hourlyWindBands` (vent) alimentent les trois modes du graphe unique de
     * bande de confiance. On les précalcule dans le ViewModel pour éviter que
     * changer de mode dans l'UI ne déclenche un recalcul coûteux (jusqu'à
     * 168 timestamps × N modèles).
     */
    data class Loaded(
        val forecast: CityForecast,
        val weeklyConfidence: List<DayConfidence>,
        val hourlyBands: List<HourlyConfidenceBand>,
        /**
         * Bande de confiance précipitation. Peut être vide si aucun modèle ne
         * fournit la variable horaire à l'instant courant — dans ce cas l'UI
         * affiche le placeholder "pas assez de données" typique du chart.
         */
        val hourlyPrecipBands: List<HourlyConfidenceBand> = emptyList(),
        /**
         * Bande de confiance vent (moyen à 10m, km/h). Idem : vide si aucun
         * modèle n'a la variable, UI affiche le placeholder.
         */
        val hourlyWindBands: List<HourlyConfidenceBand> = emptyList(),
        val currentTemp: Double?,
        val currentCondition: WeatherCondition? = null,
        val currentCloudCover: Int? = null,
        val dailyConditions: List<DayConditionsRow> = emptyList(),
        val normals: Map<Int, DayNormals>? = null,
        /** Instant unique utilisé pour calculer et présenter les agrégats « maintenant ». */
        val calculatedAt: Instant,
        val fetchedAt: Instant? = null
    ) : CityDetailUiState

    data class Error(val message: String) : CityDetailUiState
}
