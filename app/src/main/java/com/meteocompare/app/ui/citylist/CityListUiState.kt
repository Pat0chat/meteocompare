package com.meteocompare.app.ui.citylist

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalTime

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
     * @param currentCloudCover couverture nuageuse "maintenant" (0-100),
     *   agrégée entre modèles. Alimente le badge "70% couvert" sur la CityCard
     *   quand la condition courante est cloudy/overcast. Null si non disponible
     *   (cache pré-feature ou modèles sans cloud_cover).
     * @param fetchedAt horodatage de la dernière écriture cache ou fetch
     *   réseau. Null quand la donnée provient d'un cache pré-feature — la
     *   CityCard omet alors le caption "il y a X".
     * @param next12hTemps températures agrégées entre modèles pour les 12
     *   prochaines heures (index 0 = heure actuelle, index 11 = +11h). Alimente
     *   [MiniForecastStrip]. Peut contenir des null pour les heures manquantes
     *   (fin de fenêtre pour AROME HD par exemple).
     * @param next12hPrecipProb probabilités de précipitation agrégées (0-100)
     *   pour les 12 prochaines heures, alignées sur [next12hTemps]. Alimente
     *   les dots précip du [MiniForecastStrip].
     * @param hourlyStartTime moment de la première heure de [next12hTemps],
     *   exprimé dans le fuseau de la ville (pas du device). Sert à afficher
     *   les ancres horaires sous la strip ("15h ... 21h ... 03h"). Null si
     *   la ville n'a pas de fuseau connu ou si le cache est pré-feature.
     * @param sunrise heure de lever du soleil pour la ville aujourd'hui, dans
     *   son fuseau. Null en région polaire (nuit continue) ou si la position
     *   de la ville est manquante. Cf. [com.meteocompare.app.domain.util.SolarTimes].
     * @param sunset heure de coucher, mêmes contraintes que [sunrise].
     */
    data class Loaded(
        val today: DayConfidence,
        val currentTemp: Double?,
        val currentCondition: WeatherCondition? = null,
        val currentCloudCover: Int? = null,
        val fetchedAt: Instant? = null,
        /** Modèles demandés, y compris ceux connus indisponibles/hors zone. */
        val sourceModels: Set<WeatherModel> = emptySet(),
        // ─── Nouveautés pour la home enrichie ────────────────────────────
        val next12hTemps: List<Double?> = emptyList(),
        val next12hPrecipProb: List<Int?> = emptyList(),
        val hourlyStartTime: java.time.LocalDateTime? = null,
        val sunrise: LocalTime? = null,
        val sunset: LocalTime? = null
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