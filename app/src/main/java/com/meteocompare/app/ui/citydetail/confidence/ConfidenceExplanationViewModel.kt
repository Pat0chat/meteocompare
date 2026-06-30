package com.meteocompare.app.ui.citydetail.confidence

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * État de l'écran "Pourquoi cette confiance ?".
 *
 * Même pattern que [com.meteocompare.app.ui.citydetail.CityDetailUiState] :
 * sealed interface car les trois cas (Loading, Loaded, Error) sont
 * mutuellement exclusifs et l'UI fait un `when` exhaustif dessus.
 */
sealed interface ConfidenceExplanationUiState {

    data object Loading : ConfidenceExplanationUiState

    /**
     * État succès complet.
     *
     * @property dayConfidence Agrégats déjà calculés (les % et spread visibles
     *           dans le résumé du jour) — on les ré-affiche tels quels pour
     *           assurer la cohérence entre les deux écrans.
     * @property variableBreakdowns Décomposition par variable de ce qu'a dit
     *           chaque modèle. C'est le cœur pédagogique de l'écran.
     * @property contributingModels Liste des modèles qui ont produit AU MOINS
     *           une valeur pour ce jour — utile pour la section éducative
     *           "Pourquoi les modèles diffèrent ?" qui n'a de sens que pour
     *           les modèles réellement utilisés ce jour-là.
     */
    data class Loaded(
        val city: City,
        val date: LocalDate,
        val dayConfidence: DayConfidence,
        val variableBreakdowns: List<VariableBreakdown>,
        val contributingModels: List<WeatherModel>
    ) : ConfidenceExplanationUiState

    data class Error(val message: String) : ConfidenceExplanationUiState
}

/**
 * Décomposition par variable : pour chaque modèle ayant produit une valeur
 * ce jour-là, on garde la valeur brute (à formatter dans l'UI).
 *
 * `kind` détermine la variable affichée et son unité dans l'UI ; on ne
 * stocke pas l'unité ici parce qu'elle est statique et déjà localisée
 * via les strings resources.
 */
data class VariableBreakdown(
    val kind: VariableKind,
    val perModel: List<ModelValue>
)

data class ModelValue(
    val model: WeatherModel,
    val value: Double
)

enum class VariableKind { TEMP_MAX, TEMP_MIN, PRECIPITATION, WIND_MAX }

@HiltViewModel
class ConfidenceExplanationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val userPreferences: UserPreferencesRepository,
    private val confidenceCalculator: ConfidenceCalculator
) : ViewModel() {

    private val cityId: String = checkNotNull(
        savedStateHandle.get<String>(Destinations.CITY_DETAIL_ARG)
    )

    // Date passée en route comme ISO yyyy-MM-dd. Parsing en LocalDate au boot —
    // si la route a été manipulée à la main avec un format invalide, on bascule
    // sur Error plutôt que de crasher.
    private val targetDate: LocalDate? = runCatching {
        LocalDate.parse(checkNotNull(savedStateHandle.get<String>(Destinations.CONFIDENCE_DATE_ARG)))
    }.getOrNull()

    private val _state =
        MutableStateFlow<ConfidenceExplanationUiState>(ConfidenceExplanationUiState.Loading)
    val state: StateFlow<ConfidenceExplanationUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * Stratégie de chargement :
     *   1. Si la date est invalide → Error immédiat.
     *   2. Lookup de la ville dans les favoris (même contrat que [CityDetailViewModel]).
     *   3. On consomme le stream cache+fresh — première émission affiche tout
     *      ce qu'on a (souvent quasi instant grâce au cache), seconde émission
     *      raffraîchit avec les données réseau.
     *      On ne s'arrête PAS à la première : si seul le cache est dispo
     *      maintenant mais que le réseau arrive 2s plus tard avec un set de
     *      modèles plus complet, on veut basculer dessus.
     */
    private fun load() {
        viewModelScope.launch {
            val date = targetDate ?: run {
                _state.value = ConfidenceExplanationUiState.Error(
                    context.getString(R.string.confidence_explanation_invalid_date)
                )
                return@launch
            }
            val city = findCity() ?: run {
                _state.value = ConfidenceExplanationUiState.Error(
                    context.getString(R.string.city_not_found_in_favorites)
                )
                return@launch
            }
            val models = userPreferences.observeEnabledModels().first()

            forecastRepository
                .getCityForecastStream(city, models = models, forecastDays = 7)
                .collect { result ->
                    _state.value = when (result) {
                        is ApiResult.Success -> buildLoadedState(city, date, result.data)
                        is ApiResult.Error -> {
                            // Si on avait déjà un Loaded (cache émis avant
                            // une erreur réseau), on le garde — l'utilisateur
                            // a déjà des données utiles à l'écran.
                            if (_state.value is ConfidenceExplanationUiState.Loaded) _state.value
                            else ConfidenceExplanationUiState.Error(result.message)
                        }
                    }
                }
        }
    }

    private fun buildLoadedState(
        city: City,
        date: LocalDate,
        forecast: CityForecast
    ): ConfidenceExplanationUiState {
        val dayConfidence = confidenceCalculator.dayConfidence(forecast, date)
        val breakdowns = buildBreakdowns(forecast, date)
        // Modèles "contributeurs" = ceux qui apparaissent dans AU MOINS une
        // breakdown. distinct() préserve l'ordre d'apparition, puis on
        // trie par résolution (du plus fin au plus grossier) — c'est l'ordre
        // pertinent pour la section éducative qui parle de résolution.
        val contributing = breakdowns
            .flatMap { it.perModel.map { mv -> mv.model } }
            .distinct()
            .sortedBy { it.resolutionKm }

        return ConfidenceExplanationUiState.Loaded(
            city = city,
            date = date,
            dayConfidence = dayConfidence,
            variableBreakdowns = breakdowns,
            contributingModels = contributing
        )
    }

    /**
     * Extrait les valeurs par modèle pour chaque variable, pour la date demandée.
     *
     * On itère explicitement sur les 4 variables plutôt que de réfléchir une
     * abstraction "extracteur générique" — il n'y en a que 4 et elles ont
     * chacune leur logique de tri (résolution pour T° et vent ; pluie en mm
     * croissants pour mieux voir le split sec/pluie).
     */
    private fun buildBreakdowns(
        forecast: CityForecast,
        date: LocalDate
    ): List<VariableBreakdown> {
        // Précalcul : (model, série, index-du-jour) pour les modèles qui
        // couvrent la date. Évite de re-faire `dates.indexOf(date)` 4 fois.
        data class ModelAt(val model: WeatherModel, val idx: Int, val series: com.meteocompare.app.domain.model.ForecastSeries)

        val modelsAtDate: List<ModelAt> = forecast.seriesByModel.mapNotNull { (model, series) ->
            val idx = series.daily.dates.indexOf(date)
            if (idx >= 0) ModelAt(model, idx, series) else null
        }

        val result = mutableListOf<VariableBreakdown>()

        fun collect(extract: (ModelAt) -> Double?): List<ModelValue> =
            modelsAtDate.mapNotNull { ma ->
                extract(ma)?.let { ModelValue(ma.model, it) }
            }.sortedBy { it.model.resolutionKm }

        collect { it.series.daily.tempMax.getOrNull(it.idx) }
            .takeIf { it.isNotEmpty() }
            ?.let { result += VariableBreakdown(VariableKind.TEMP_MAX, it) }

        collect { it.series.daily.tempMin.getOrNull(it.idx) }
            .takeIf { it.isNotEmpty() }
            ?.let { result += VariableBreakdown(VariableKind.TEMP_MIN, it) }

        // Pluie : on garde les 0 mm car ils sont eux-mêmes une information
        // ("ce modèle dit qu'il ne pleut pas"). Tri par valeur croissante :
        // les modèles "secs" se regroupent en haut, les "pluvieux" en bas —
        // visuellement on voit immédiatement s'il y a un désaccord.
        val precip = modelsAtDate.mapNotNull { ma ->
            ma.series.daily.precipitationSum.getOrNull(ma.idx)?.let {
                ModelValue(ma.model, it)
            }
        }.sortedBy { it.value }
        if (precip.isNotEmpty()) {
            result += VariableBreakdown(VariableKind.PRECIPITATION, precip)
        }

        collect { it.series.daily.windSpeedMax.getOrNull(it.idx) }
            .takeIf { it.isNotEmpty() }
            ?.let { result += VariableBreakdown(VariableKind.WIND_MAX, it) }

        return result
    }

    private suspend fun findCity(): City? =
        cityRepository.observeFavorites().first().firstOrNull { it.id == cityId }
}
