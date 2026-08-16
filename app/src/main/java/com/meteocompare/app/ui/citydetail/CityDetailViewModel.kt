package com.meteocompare.app.ui.citydetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meteocompare.app.R
import com.meteocompare.app.core.util.localDateIn
import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.core.network.NetworkMonitor
import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.RefreshInterval
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.di.DefaultDispatcher
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.ComputeBiasUseCase
import com.meteocompare.app.domain.usecase.ComputeForecastEvolutionUseCase
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import com.meteocompare.app.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
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
@OptIn(ExperimentalCoroutinesApi::class)
class CityDetailViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val cityRepository: CityRepository,
    private val forecastRepository: ForecastRepository,
    private val networkMonitor: NetworkMonitor,
    private val climateNormalsRepository: ClimateNormalsRepository,
    private val confidenceCalculator: ConfidenceCalculator,
    private val userPreferences: UserPreferencesRepository,
    private val biasSampleRepository: BiasSampleRepository,
    private val computeBias: ComputeBiasUseCase,
    private val forecastEvolutionRepository: ForecastEvolutionRepository,
    private val computeForecastEvolution: ComputeForecastEvolutionUseCase,
    private val clock: Clock,
    @param:DefaultDispatcher private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val cityId: String = checkNotNull(
        savedStateHandle.get<String>(Destinations.CITY_DETAIL_ARG)
    )

    private val _state = MutableStateFlow<CityDetailUiState>(CityDetailUiState.Loading)
    val state: StateFlow<CityDetailUiState> = _state.asStateFlow()

    /** Fuseau de la ville courante, source de vérité des fenêtres calendaires. */
    private val cityTimezone = MutableStateFlow<String?>(null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isOnline = MutableStateFlow(networkMonitor.isOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _evolutionState = MutableStateFlow<ForecastEvolutionState>(ForecastEvolutionState.Idle)
    val evolutionState: StateFlow<ForecastEvolutionState> = _evolutionState.asStateFlow()
    private var evolutionJob: Job? = null
    private var evolutionRequestKey: String? = null

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

    // Sérialise cache, refresh local et refresh externe. Sans ce verrou, deux
    // calculs de confiance concurrents pouvaient lire le même ancien state puis
    // terminer dans l'ordre inverse et laisser la prévision la plus vieille.
    private val resultMutex = Mutex()

    // ── Suivi de biais : StateFlow composé depuis Room ────────────────────
    //
    // Structure : à chaque changement de la liste des modèles activés, on
    // (ré)abonne aux flows Room correspondants (un par (model, variable)),
    // on les combine, et on applique [computeBias] pour produire l'état
    // consommé par la screen.
    //
    // WhileSubscribed(5s) — le calcul repart quand un subscriber revient
    // dans les 5s, sinon on désabonne pour économiser (background app,
    // navigation vers une autre ville). 5s couvre les rotations et les
    // transitions courtes.
    //
    // État initial vide — l'UI n'affiche simplement pas de chip tant que
    // Room n'a pas émis. Aucun placeholder à gérer.
    val biasState: StateFlow<BiasScreenState> = combine(
        userPreferences.observeEnabledModels(),
        cityTimezone
    ) { models, timezone ->
        Triple(models, timezone, clock.instant().localDateIn(timezone))
    }.flatMapLatest { (models, timezone, asOf) ->
        observeBiasScreenState(models, timezone, asOf)
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = BiasScreenState.EMPTY
        )

    /**
     * Sections repliées, persistées dans DataStore séparément pour cette ville.
     * Eagerly démarre la lecture dès la création du ViewModel afin de réduire le
     * bref affichage des sections ouvertes lors d'un retour dans l'application.
     */
    val collapsedSections: StateFlow<Set<CityDetailSection>> =
        userPreferences.observeCollapsedCityDetailSections(cityId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet()
            )

    /** Dernier mode horaire/journalier choisi pour cette ville. */
    val detailViewMode: StateFlow<CityDetailViewMode> =
        userPreferences.observeCityDetailViewMode(cityId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = CityDetailViewMode.DEFAULT
            )

    /** Dernier onglet de comparaison détaillée choisi pour cette ville. */
    val detailContentTab: StateFlow<CityDetailContentTab> =
        userPreferences.observeCityDetailContentTab(cityId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = CityDetailContentTab.DEFAULT
            )

    init {
        observeConnectivity()
        observeExternalForecastUpdates()
        loadInitial()
    }

    /** Met à jour la bannière hors connexion sans attendre un nouveau refresh. */
    private fun observeConnectivity() {
        viewModelScope.launch {
            networkMonitor.observeOnline().collect { online -> _isOnline.value = online }
        }
    }

    /**
     * Reçoit les refresh réussis lancés depuis la Home ou un autre composant.
     *
     * Le flux transporte directement le résultat déjà téléchargé : aucune
     * lecture Room supplémentaire et surtout aucune seconde requête réseau.
     * Le filtre par [cityId] empêche une mise à jour d'une autre CityCard de
     * recomposer cette page Détails.
     */
    private fun observeExternalForecastUpdates() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            forecastRepository.observeForecastUpdates().collect { forecast ->
                if (forecast.city.id != cityId) return@collect
                applyResult(ApiResult.Success(forecast))
            }
        }
    }

    /**
     * Compose les flows de samples Room en un [BiasScreenState] complet.
     *
     * Pour chacune des 3 variables : lance un [observeVariableBiasState]
     * dédié, puis combine les 3 en un [BiasScreenState] agrégé.
     */
    private fun observeBiasScreenState(
        models: List<WeatherModel>,
        timezone: String?,
        asOf: LocalDate
    ): Flow<BiasScreenState> {
        if (models.isEmpty()) return flowOf(BiasScreenState.EMPTY)
        return combine(
            observeVariableBiasState(models, BiasVariable.TEMPERATURE, timezone, asOf),
            observeVariableBiasState(models, BiasVariable.PRECIPITATION, timezone, asOf),
            observeVariableBiasState(models, BiasVariable.WIND_SPEED, timezone, asOf)
        ) { t, p, w -> BiasScreenState(temperature = t, precipitation = p, wind = w) }
    }

    /**
     * Compose les flows par modèle pour UNE variable. Chaque flow individuel
     * est `biasSampleRepository.observeSamples(...)` → `Flow<List<BiasSample>>`.
     *
     * Le combine émet dès qu'UN des flows amont change. Non-problematique
     * en pratique : Room ne re-émet que sur écriture dans la table, et les
     * écritures sont rares (bootstrap manuel ou cycle quotidien Previous Runs).
     * Coût par émission : quelques ms pour 7 modèles.
     */
    private fun observeVariableBiasState(
        models: List<WeatherModel>,
        variable: BiasVariable,
        timezone: String?,
        asOf: LocalDate
    ): Flow<VariableBiasState> {
        val perModelFlows: List<Flow<Pair<WeatherModel, List<BiasSample>>>> = models.map { model ->
            biasSampleRepository.observeSamples(
                cityId = cityId,
                model = model,
                variable = variable,
                asOf = asOf,
                timezone = timezone,
                windowDays = BIAS_WINDOW_DAYS
            ).map { samples -> model to samples }
        }
        return combine(perModelFlows) { pairs ->
            val historyByModel: Map<WeatherModel, List<BiasSample>> = pairs.toMap()
            val biasByModel: Map<WeatherModel, ModelBias?> = historyByModel.mapValues { (_, samples) ->
                computeBias(
                    variable = variable,
                    samples = samples,
                    asOf = asOf,
                    windowDays = BIAS_WINDOW_DAYS
                )
            }
            val yDomain = computeYDomain(historyByModel, variable)
            VariableBiasState(
                biasByModel = biasByModel,
                historyByModel = historyByModel,
                yDomainMin = yDomain?.first,
                yDomainMax = yDomain?.second
            )
        }.flowOn(computationDispatcher)
    }

    /**
     * Bornes de l'axe Y du sparkline pour une variable, calculées sur l'union
     * de toutes les valeurs (forecast + observation) de tous les modèles.
     *
     * Semantics par variable :
     *   - **Température** : marge symétrique de ±1° autour de la plage. Peut
     *     être négative (pas de plancher physique en °C).
     *   - **Précipitations** : plancher forcé à 0 (pas de pluie négative),
     *     plafond avec marge de +0.5 mm.
     *   - **Vent** : plancher forcé à 0 (vitesse scalaire), plafond +3 km/h.
     *
     * Retourne `null` si aucun sample n'existe encore → le sparkline ne
     * s'affichera pas (la sheet ne sera pas ouvrable non plus, faute de bias).
     */
    private fun computeYDomain(
        historyByModel: Map<WeatherModel, List<BiasSample>>,
        variable: BiasVariable
    ): Pair<Double, Double>? {
        val allValues = historyByModel.values.asSequence()
            .flatMap { samples -> samples.asSequence() }
            .flatMap { sequenceOf(it.forecast, it.observation) }
            .toList()
        if (allValues.isEmpty()) return null
        val min = allValues.min()
        val max = allValues.max()
        return when (variable) {
            BiasVariable.TEMPERATURE -> (min - 1.0) to (max + 1.0)
            BiasVariable.PRECIPITATION -> 0.0 to (max + 0.5)
            BiasVariable.WIND_SPEED -> 0.0 to (max + 3.0)
        }
    }

    /**
     * Chargement initial : utilise le stream cache+fresh.
     * Émet d'abord le cache si présent, puis le résultat réseau — SAUF si le
     * cache est plus récent que l'intervalle de rafraîchissement utilisateur,
     * auquel cas on n'émet QUE le cache (pas de requête réseau).
     *
     * ─── Économie batterie/data ─────────────────────────────────────────
     * Sans ce garde, chaque navigation vers l'écran détail déclenche une requête
     * batched vers Open-Meteo — même si l'utilisateur vient d'ouvrir
     * cette même ville 30 secondes plus tôt. Avec le seuil `maxCacheAgeMs`
     * égal à l'intervalle utilisateur, on saute complètement le fetch quand
     * le cache est encore frais. Pull-to-refresh continue de fonctionner
     * normalement — c'est un chemin séparé via `refresh()` qui bypasse ce
     * seuil (utilise `refreshCityForecast` sans seuil).
     */
    private fun loadInitial() {
        viewModelScope.launch {
            val city = findCity() ?: run {
                _state.value = CityDetailUiState.Error(
                    context.getString(R.string.city_not_found_in_favorites)
                )
                return@launch
            }
            cityTimezone.value = city.timezone

            // Les repères historiques sont indépendantes du jeu de modèles météo, mais leur
            // premier calcul peut télécharger dix années d'archives. On attend le
            // premier forecast exploitable avant de les lancer afin de donner la
            // priorité au contenu principal et à sa première frame.
            var normalsStarted = false

            // La page peut rester vivante dans la back stack pendant un passage
            // par Settings. Modèles et intervalle doivent donc être observés,
            // pas seulement lus une fois au démarrage. distinctUntilChanged
            // évite toute relance lorsqu'une préférence sans rapport change.
            combine(
                userPreferences.observeEnabledModels(),
                userPreferences.observeRefreshInterval()
            ) { models, interval -> models to interval }
                .distinctUntilChanged()
                .flatMapLatest { (models, interval) ->
                    val maxCacheAgeMs = if (interval == RefreshInterval.MANUAL) {
                        Long.MAX_VALUE
                    } else {
                        interval.millis
                    }
                    forecastRepository.getCityForecastStream(
                        city = city,
                        models = models,
                        forecastDays = 7,
                        maxCacheAgeMs = maxCacheAgeMs
                    )
                }
                .collect { result ->
                    applyResult(result)
                    if (!normalsStarted && result is ApiResult.Success) {
                        normalsStarted = true
                        launchNormalsLoad(city)
                    }
                }
        }
    }

    /**
     * Enregistre immédiatement le nouvel état d'une section. Le repository
     * réémet ensuite [collapsedSections], ce qui devient la source de vérité UI.
     */
    fun setSectionExpanded(section: CityDetailSection, expanded: Boolean) {
        viewModelScope.launch {
            userPreferences.setCityDetailSectionCollapsed(
                cityId = cityId,
                section = section,
                collapsed = !expanded
            )
        }
    }

    fun setDetailViewMode(mode: CityDetailViewMode) {
        viewModelScope.launch {
            userPreferences.setCityDetailViewMode(cityId, mode)
        }
    }

    fun setDetailContentTab(tab: CityDetailContentTab) {
        viewModelScope.launch {
            userPreferences.setCityDetailContentTab(cityId, tab)
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
                    _refreshFeedback.trySend(RefreshFeedback.Error(context.getString(R.string.refresh_city_not_found)))
                    return@launch
                }
                val models = userPreferences.observeEnabledModels().first()
                val result = forecastRepository.refreshCityForecast(
                    city = city,
                    models = models,
                    forecastDays = 7
                )
                applyResult(result, forceEvolutionRefresh = result is ApiResult.Success)
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
                // — les repères historiques seules sans forecast n'ont pas de sens.
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

    private suspend fun applyResult(
        result: ApiResult<CityForecast>,
        forceEvolutionRefresh: Boolean = false
    ) = resultMutex.withLock {
        val previous = _state.value

        // Le chargement initial, un refresh manuel local et le signal partagé
        // peuvent recevoir le même fetch coalescé dans un ordre différent. Ne
        // jamais laisser une valeur identique ou plus ancienne écraser le
        // forecast le plus frais déjà affiché.
        if (result is ApiResult.Success && previous is CityDetailUiState.Loaded) {
            val incomingFetchedAt = result.data.fetchedAt
            val currentFetchedAt = previous.fetchedAt
            val incomingModels = result.data.seriesByModel.keys + result.data.errors.keys
            val currentModels = previous.forecast.seriesByModel.keys + previous.forecast.errors.keys
            val isOlder = currentFetchedAt != null &&
                (incomingFetchedAt == null || incomingFetchedAt.isBefore(currentFetchedAt))
            val isSameVersion = currentFetchedAt != null &&
                incomingFetchedAt == currentFetchedAt &&
                incomingModels == currentModels
            if (isOlder || isSameVersion) return@withLock
        }

        val next = when (result) {
            is ApiResult.Success -> withContext(computationDispatcher) {
                val calculationNow = clock.instant()
                val weekly = confidenceCalculator.weeklyConfidence(result.data)
                val hourly = confidenceCalculator.hourlyTemperatureConfidence(result.data)
                val hourlyPrecip = confidenceCalculator.hourlyPrecipitationConfidence(result.data)
                val hourlyWind = confidenceCalculator.hourlyWindConfidence(result.data)
                val currentTemp = confidenceCalculator.currentTemperature(result.data, calculationNow)
                val currentCondition = confidenceCalculator.currentWeatherCondition(result.data, calculationNow)
                val dailyConditions = confidenceCalculator.dailyConditionsByModel(result.data)
                CityDetailUiState.Loaded(
                    forecast = result.data,
                    weeklyConfidence = weekly,
                    hourlyBands = hourly,
                    hourlyPrecipBands = hourlyPrecip,
                    hourlyWindBands = hourlyWind,
                    currentTemp = currentTemp,
                    currentCondition = currentCondition,
                    currentCloudCover = confidenceCalculator.currentCloudCover(result.data, calculationNow),
                    dailyConditions = dailyConditions,
                    normals = loadedNormals,
                    calculatedAt = calculationNow,
                    fetchedAt = result.data.fetchedAt
                )
            }
            is ApiResult.Error -> {
                if (previous is CityDetailUiState.Loaded) previous
                else CityDetailUiState.Error(result.message)
            }
        }
        _state.value = next
        if (next is CityDetailUiState.Loaded) {
            launchEvolutionLoad(next.forecast, forceRefresh = forceEvolutionRefresh)
        }
    }

    private fun launchEvolutionLoad(forecast: CityForecast, forceRefresh: Boolean) {
        val today = clock.instant().localDateIn(forecast.city.timezone)
        val dates = forecast.seriesByModel.values.asSequence()
            .flatMap { it.daily.dates.asSequence() }
            .filter { !it.isBefore(today) }
            .distinct()
            .sorted()
            .take(EVOLUTION_FORECAST_DAYS)
            .toList()
        if (dates.isEmpty() || forecast.availableModels.isEmpty()) {
            _evolutionState.value = ForecastEvolutionState.Unavailable
            return
        }
        val key = buildString {
            append(forecast.city.id)
            append('|')
            append(forecast.availableModels.joinToString(",") { it.name })
            append('|')
            append(dates.first())
            append('|')
            append(dates.last())
            append('|')
            append(forecast.fetchedAt)
        }
        if (!forceRefresh && evolutionRequestKey == key &&
            _evolutionState.value is ForecastEvolutionState.Loaded
        ) return

        evolutionRequestKey = key
        evolutionJob?.cancel()
        evolutionJob = viewModelScope.launch {
            _evolutionState.value = ForecastEvolutionState.Loading
            when (val result = forecastEvolutionRepository.getPreviousForecasts(
                city = forecast.city,
                models = forecast.availableModels,
                startDate = dates.first(),
                endDate = dates.last(),
                forceRefresh = forceRefresh
            )) {
                is ApiResult.Success -> {
                    val report = withContext(computationDispatcher) {
                        computeForecastEvolution(
                            currentForecast = forecast,
                            previousSamples = result.data.samples,
                            fetchedAt = result.data.fetchedAt,
                            fromCache = result.data.fromCache
                        )
                    }
                    if (report.hasUsableData) {
                        val highlight = withContext(computationDispatcher) {
                            computeForecastEvolution.buildHighlight(report, today)
                        }
                        _evolutionState.value = ForecastEvolutionState.Loaded(report, highlight)
                    } else {
                        _evolutionState.value = ForecastEvolutionState.Unavailable
                    }
                }
                is ApiResult.Error -> {
                    _evolutionState.value = ForecastEvolutionState.Error(result.message)
                }
            }
        }
    }

    companion object {
        /**
         * Fenêtre glissante du suivi de biais. Aligné sur le défaut de
         * [ComputeBiasUseCase] et sur la stratégie de retention du worker
         * (35 jours, marge de 5 sur les 30 utilisés ici).
         */
        private const val BIAS_WINDOW_DAYS: Int = 30
        private const val EVOLUTION_FORECAST_DAYS: Int = 7
    }
}
