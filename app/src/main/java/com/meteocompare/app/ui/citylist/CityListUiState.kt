package com.meteocompare.app.ui.citylist

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.WeatherCondition

/**
 * État affichable de la liste des villes favorites.
 *
 * On ne porte PAS le booléen `isLoading` global : chaque ville a son propre état
 * (Loading / Loaded / Error) car les fetches sont parallèles et indépendants.
 * Ça évite le bug typique "on bloque l'écran tant que la requête la plus lente
 * n'est pas finie".
 */
data class CityListUiState(
    val items: List<CityCardState> = emptyList(),
    val isRefreshing: Boolean = false
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

data class CityCardState(
    val city: City,
    val forecast: ForecastState
)

sealed interface ForecastState {
    data object Loading : ForecastState
    /**
     * @param currentTemp moyenne pondérée des modèles à l'heure la plus proche
     *   de maintenant. Null si aucune donnée horaire dispo.
     * @param currentCondition famille de temps actuelle (mode pondéré par
     *   résolution). Null si aucun modèle ne fournit weather_code — typique-
     *   ment un cache antérieur à la feature.
     */
    data class Loaded(
        val today: DayConfidence,
        val currentTemp: Double?,
        val currentCondition: WeatherCondition? = null
    ) : ForecastState
    data class Error(val message: String) : ForecastState
}

/**
 * État de la feuille d'ajout de ville. Séparé du UiState principal car son cycle
 * de vie est indépendant (le BottomSheet peut être ouvert/fermé sans toucher
 * à la liste).
 */
data class AddCityUiState(
    val query: String = "",
    val results: List<City> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)
