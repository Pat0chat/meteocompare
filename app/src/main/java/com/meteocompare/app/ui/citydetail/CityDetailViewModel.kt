package com.meteocompare.app.ui.citydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Événement one-shot du résultat d'un refresh manuel.
 *
 * Différent du state (`isRefreshing`, `state`) : on veut afficher une snackbar
 * UNE seule fois par refresh et qu'elle disparaisse. Si on stockait ça dans
 * un StateFlow, un changement de configuration (rotation, dark mode toggle)
 * relancerait la snackbar — pas voulu.
 */
sealed interface RefreshFeedback {
    data object Success : RefreshFeedback
    data class Error(val message: String) : RefreshFeedback
}

@HiltViewModel
class CityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val climateNormalsRepository: ClimateNormalsRepository,
    private val confidenceCalculator: ConfidenceCalculator,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val cityId: String = checkNotNull(
        savedStateHandle.get<String>(Destinations.CITY_DETAIL_ARG)
    )

    private val _state = MutableStateFlow<CityDetailUiState>(CityDetailUiState.Loading)
    val state: StateFlow<CityDetailUiState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Channel des feedbacks refresh — capacity 1 + DROP_OLDEST : si l'utilisateur
    // spam le bouton refresh, on ne fait que montrer le dernier résultat plutôt
    // que d'empiler 5 snackbars.
    private val _refreshFeedback = Channel<RefreshFeedback>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val refreshFeedback: Flow<RefreshFeedback> = _refreshFeedback.receiveAsFlow()

    // Cache en mémoire des normales pour la ville courante. Évite de re-fetch
    // à chaque applyResult() qui s'exécute pour cache + fresh forecasts.
    private var loadedNormals: Map<Int, DayNormals>? = null

    init {
        loadInitial()
    }

    /**
     * Chargement initial : utilise le stream cache+fresh.
     * Émet d'abord le cache si présent, puis le résultat réseau.
     */
    private fun loadInitial() {
        viewModelScope.launch {
            val city = findCity() ?: run {
                _state.value = CityDetailUiState.Error("Ville non trouvée dans les favoris")
                return@launch
            }
            val models = userPreferences.observeEnabledModels().first()

            // Lance le fetch des normales en parallèle — ne bloque pas l'affichage
            // du forecast. Quand les normales arrivent, on met à jour le state
            // existant via copy() pour ajouter le champ normals.
            launchNormalsLoad(city)

            forecastRepository.getCityForecastStream(city, models = models, forecastDays = 7)
                .collect { result -> applyResult(result) }
        }
    }

    /**
     * Pull-to-refresh OU bouton refresh : force le réseau.
     *
     * Envoie un [RefreshFeedback] à la fin pour que l'UI affiche un retour
     * visuel (snackbar). Sans ce signal, un succès ou un échec sont muets —
     * l'utilisateur doute que son tap ait été reçu.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val city = findCity() ?: run {
                    _refreshFeedback.trySend(RefreshFeedback.Error("Ville introuvable"))
                    return@launch
                }
                val models = userPreferences.observeEnabledModels().first()
                val result = forecastRepository.refreshCityForecast(
                    city = city,
                    models = models,
                    forecastDays = 7
                )
                applyResult(result)
                // Feedback explicite : succès si la requête a abouti, erreur sinon.
                // Le repo retourne déjà Success même avec des erreurs partielles
                // (philosophie tolerant aggregation) — on lit le résultat brut.
                when (result) {
                    is ApiResult.Success -> _refreshFeedback.trySend(RefreshFeedback.Success)
                    is ApiResult.Error -> _refreshFeedback.trySend(RefreshFeedback.Error(result.message))
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun launchNormalsLoad(city: City) {
        viewModelScope.launch {
            val result = climateNormalsRepository.getNormalsForCity(city)
            if (result is ApiResult.Success) {
                val byKey = result.data.associateBy { it.key }
                loadedNormals = byKey
                // Patch le state existant : si on est déjà en Loaded, on
                // remplace .normals. Sinon (Loading/Error), on n'altère pas
                // — les normales seules sans forecast n'ont pas de sens.
                _state.update { current ->
                    if (current is CityDetailUiState.Loaded) current.copy(normals = byKey)
                    else current
                }
            }
            // En cas d'erreur, on ignore silencieusement : l'app reste fonctionnelle
            // sans normales (pas de pointillés, pas de coloration). C'est du nice-to-have.
        }
    }

    private suspend fun findCity(): City? =
        cityRepository.observeFavorites().first().firstOrNull { it.id == cityId }

    private fun applyResult(result: ApiResult<CityForecast>) {
        _state.value = when (result) {
            is ApiResult.Success -> {
                val weekly = confidenceCalculator.weeklyConfidence(result.data)
                val hourly = confidenceCalculator.hourlyTemperatureConfidence(result.data)
                val currentTemp = confidenceCalculator.currentTemperature(result.data)
                CityDetailUiState.Loaded(
                    forecast = result.data,
                    weeklyConfidence = weekly,
                    hourlyBands = hourly,
                    currentTemp = currentTemp,
                    normals = loadedNormals
                )
            }
            is ApiResult.Error -> {
                if (_state.value is CityDetailUiState.Loaded) _state.value
                else CityDetailUiState.Error(result.message)
            }
        }
    }
}
