package com.meteocompare.app.ui.citydetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.DayConditionsRow
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.ModernInlineSelector
import com.meteocompare.app.ui.theme.confidenceColor
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ============================================================================
//  Public screen entry
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDetailScreen(
    onBack: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit = {},
    viewModel: CityDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val biasState by viewModel.biasState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Resources observables capturées hors de LaunchedEffect : contrairement à
    // LocalContext, LocalResources invalide la composition quand la locale ou
    // une autre configuration de ressources change.
    val resources = LocalResources.current

    // Collecte les événements one-shot de refresh — succès ou erreur.
    // LaunchedEffect avec viewModel comme key : si la VM change (changement
    // de cityId via nav), on relance la collecte. flowWithLifecycle évite de
    // collecter quand l'écran est en background — pas indispensable ici car
    // les events sont rares, mais c'est l'habitude.
    LaunchedEffect(viewModel) {
        viewModel.refreshFeedback.collect { feedback ->
            when (feedback) {
                RefreshFeedback.Success -> snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.refresh_success),
                    duration = SnackbarDuration.Short
                )
                is RefreshFeedback.Error -> snackbarHostState.showSnackbar(
                    message = resources.getString(R.string.refresh_error, feedback.message),
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    CityDetailContent(
        state = state,
        isRefreshing = isRefreshing,
        biasState = biasState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onConfidenceClick = onConfidenceClick
    )
}

// ============================================================================
//  Stateless content
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityDetailContent(
    state: CityDetailUiState,
    isRefreshing: Boolean,
    biasState: BiasScreenState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    val title = (state as? CityDetailUiState.Loaded)?.forecast?.city?.name
                        ?: stringResource(R.string.title_detail_fallback)
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    // Pendant le refresh : on désactive le bouton (évite double-tap
                    // qui spammerait le réseau) ET on remplace l'icône par un
                    // spinner. C'est le feedback visuel immédiat "ton tap a été pris".
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "detail-state",
            contentKey = {
                when (it) {
                    CityDetailUiState.Loading -> "loading"
                    is CityDetailUiState.Loaded -> "loaded"
                    is CityDetailUiState.Error -> "error"
                }
            }
        ) { s ->
            when (s) {
                CityDetailUiState.Loading -> LoadingView(padding)
                is CityDetailUiState.Error -> ErrorView(
                    message = s.message,
                    onRetry = onRefresh,
                    padding = padding
                )
                is CityDetailUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LoadedView(
                        forecast = s.forecast,
                        weekly = s.weeklyConfidence,
                        hourlyBands = s.hourlyBands,
                        hourlyPrecipBands = s.hourlyPrecipBands,
                        hourlyWindBands = s.hourlyWindBands,
                        currentTemp = s.currentTemp,
                        currentCondition = s.currentCondition,
                        currentCloudCover = s.currentCloudCover,
                        dailyConditions = s.dailyConditions,
                        normals = s.normals,
                        fetchedAt = s.fetchedAt,
                        biasState = biasState,
                        padding = padding,
                        onConfidenceClick = onConfidenceClick
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).testTag(TAG_DETAIL_LOADING),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).testTag(TAG_DETAIL_ERROR),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun LoadedView(
    forecast: CityForecast,
    weekly: List<DayConfidence>,
    hourlyBands: List<HourlyConfidenceBand>,
    hourlyPrecipBands: List<HourlyConfidenceBand>,
    hourlyWindBands: List<HourlyConfidenceBand>,
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    currentCloudCover: Int?,
    dailyConditions: List<DayConditionsRow>,
    normals: Map<Int, DayNormals>?,
    fetchedAt: Instant?,
    biasState: BiasScreenState,
    padding: PaddingValues,
    onConfidenceClick: (isoDate: String) -> Unit = {}
) {
    // Mode d'affichage piloté par le toggle sous la TodaySummaryCard.
    // rememberSaveable (via le Saver de DisplayMode) pour survivre à la rotation
    // et au dark-mode toggle. Défaut = DAILY — c'est le comportement historique
    // de l'app, la vue la plus rapide à scanner sur 7 jours.
    var displayMode by rememberSaveable(stateSaver = DisplayMode.Saver) {
        mutableStateOf(DisplayMode.DAILY)
    }

    // ── Suivi de biais : sélection courante pour l'ouverture de la sheet ──
    // Persistance sur rotation : on sauvegarde uniquement L'IDENTIFIANT
    // (deux noms d'enum) via deux String nativement saveable — la vraie
    // BiasSelection est ensuite reconstruite déterministiquement depuis
    // ces deux clés + l'état [biasState] courant.
    //
    // Pourquoi deux Strings et pas un data class + Saver custom : Compose 1.x
    // impose `T : Any` sur `rememberSaveable(stateSaver = ...)`, ce qui interdit
    // un état vraiment nullable avec un Saver custom. Contourner via un
    // sentinel "" (chaîne vide = pas de sélection) est plus court, plus
    // portable, et évite un Saver à maintenir.
    var selectedModelName by rememberSaveable { mutableStateOf("") }
    var selectedVariableName by rememberSaveable { mutableStateOf("") }

    // ── Classement local : sheet globale + variable/modèle mis en avant ──
    var isLocalRankingOpen by rememberSaveable { mutableStateOf(false) }
    var localRankingVariableName by rememberSaveable {
        mutableStateOf(BiasVariable.TEMPERATURE.name)
    }
    var highlightedRankingModelName by rememberSaveable { mutableStateOf("") }

    val localRankings = remember(biasState) { buildLocalModelRankings(biasState) }
    val localRankingVariable = remember(localRankingVariableName) {
        enumValueOrNull<BiasVariable>(localRankingVariableName)
            ?: localRankings.firstAvailableVariable
    }
    val highlightedRankingModel = remember(highlightedRankingModelName) {
        enumValueOrNull<WeatherModel>(highlightedRankingModelName)
    }

    // Reconstruction de la BiasSelection à partir des identifiants sauvegardés
    // et du state Room courant. remember(...) mémorise le résultat tant que
    // ni la sélection ni les données amont ne bougent.
    //
    // Retourne null si :
    //   - Aucune sélection en cours (chaîne vide)
    //   - Les enums sauvegardés ne matchent plus (refactor entre versions)
    //   - Les données Room ne sont pas encore chargées (cold start, Flow pas
    //     encore émis) → la sheet ne s'ouvre pas, se rouvrira dès que Room
    //     émettra les samples correspondants.
    val selectedBias: BiasSelection? = remember(selectedModelName, selectedVariableName, biasState) {
        if (selectedModelName.isEmpty() || selectedVariableName.isEmpty()) return@remember null
        val model = enumValueOrNull<com.meteocompare.app.domain.model.WeatherModel>(selectedModelName)
            ?: return@remember null
        val variable = enumValueOrNull<com.meteocompare.app.domain.model.BiasVariable>(selectedVariableName)
            ?: return@remember null

        val varState = when (variable) {
            com.meteocompare.app.domain.model.BiasVariable.TEMPERATURE   -> biasState.temperature
            com.meteocompare.app.domain.model.BiasVariable.PRECIPITATION -> biasState.precipitation
            com.meteocompare.app.domain.model.BiasVariable.WIND_SPEED    -> biasState.wind
        }
        buildBiasSelection(
            model = model,
            variable = variable,
            state = varState
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(TAG_DETAIL_LOADED),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item("today_summary") {
            weekly.firstOrNull()?.let { today ->
                TodaySummaryCard(
                    today = today,
                    modelCount = forecast.availableModels.size,
                    currentTemp = currentTemp,
                    currentCondition = currentCondition,
                    currentCloudCover = currentCloudCover,
                    fetchedAt = fetchedAt,
                    onConfidenceClick = { onConfidenceClick(today.date.toString()) }
                )
            }
        }

        // Bande de confiance horaire — TOUJOURS visible, au-dessus du toggle,
        // parce que c'est le différenciateur clé de l'app. Le composant
        // ConfidenceBandSection encapsule le sélecteur à 3 états (Température /
        // Précipitations / Vent) et le chart correspondant. Il est indépendant
        // du toggle daily/hourly qui pilote uniquement les tableaux du bas.
        //
        // On rend le bloc dès qu'AU MOINS UNE des 3 séries a de la donnée —
        // typiquement dès que la température en a, les 2 autres suivent. Le
        // chart lui-même gère son placeholder "pas assez de données" quand la
        // métrique sélectionnée est vide (métrique optionnelle du modèle).
        if (hourlyBands.size >= 2 || hourlyPrecipBands.size >= 2 || hourlyWindBands.size >= 2) {
            item("hourly_confidence") {
                SectionTitle(stringResource(R.string.section_confidence_band))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    ConfidenceBandSection(
                        tempBands = hourlyBands,
                        precipBands = hourlyPrecipBands,
                        windBands = hourlyWindBands,
                        timezone = forecast.city.timezone,
                        normals = normals
                    )
                }
            }
        }

        if (localRankings.hasAnyRanking) {
            item("local_model_ranking_summary") {
                SectionTitle(stringResource(R.string.local_ranking_summary_title))
                LocalModelRankingSummaryCard(
                    rankings = localRankings,
                    onOpenRanking = { variable ->
                        localRankingVariableName = variable.name
                        highlightedRankingModelName = ""
                        isLocalRankingOpen = true
                    }
                )
            }
        }

        // Toggle "Par heure / Par jour" — placé juste sous la bande de confiance
        // (elle-même sous la TodaySummaryCard). Le reste du contenu (tableaux
        // en mode daily/hourly) réagit à ce toggle.
        item("display_mode_toggle") {
            DisplayModeToggle(
                mode = displayMode,
                onModeChange = { displayMode = it }
            )
        }

        // Hint "historique en cours de collecte" — visible tant qu'aucun chip
        // n'est disponible sur AUCUNE variable pour AUCUN modèle. Une fois qu'un
        // premier ModelBias non-null émerge (14+ jours d'historique dans Room),
        // le hint disparaît automatiquement à la recomposition suivante.
        val hasAnyBias =
            biasState.temperature.biasByModel.values.any { it != null } ||
            biasState.precipitation.biasByModel.values.any { it != null } ||
            biasState.wind.biasByModel.values.any { it != null }
        if (!hasAnyBias) {
            item("bias_history_hint") {
                BiasHistoryHint()
            }
        }

        when (displayMode) {
            DisplayMode.HOURLY -> hourlyItems(
                forecast = forecast,
                // Providers de biais : lambdas qui lisent le map courant du
                // state. Chaque provider est stable tant que le map sous-jacent
                // n'a pas changé (Compose recompose sinon).
                temperatureBiasProvider = { model -> biasState.temperature.biasByModel[model] },
                precipitationBiasProvider = { model -> biasState.precipitation.biasByModel[model] },
                windBiasProvider = { model -> biasState.wind.biasByModel[model] },
                // Providers de progression pour le CalibratingChip. On lit
                // `historyByModel[model].size` qui est déjà dédupliqué par date
                // (cf. docstring VariableBiasState), donc la valeur reflète
                // bien le nombre de jours effectivement observés — c'est le
                // même dénominateur que ComputeBiasUseCase utilise pour son
                // seuil MIN_SAMPLES_FOR_BIAS (14). Un modèle sans historique
                // → size 0 → chip retombe sur "—".
                temperatureSampleCountProvider = { model ->
                    biasState.temperature.historyByModel[model]?.size ?: 0
                },
                precipitationSampleCountProvider = { model ->
                    biasState.precipitation.historyByModel[model]?.size ?: 0
                },
                windSampleCountProvider = { model ->
                    biasState.wind.historyByModel[model]?.size ?: 0
                },
                onBiasChipClick = { model, bias ->
                    // On ne stocke que les deux identifiants — la reconstruction
                    // complète (biais + historique + domain) est faite via le
                    // `remember(selectedModelName, selectedVariableName, biasState)`
                    // en tête de LoadedView.
                    selectedModelName = model.name
                    selectedVariableName = bias.variable.name
                }
            )
            DisplayMode.DAILY -> dailyItems(
                forecast = forecast,
                dailyConditions = dailyConditions,
                normals = normals,
                temperatureBiasProvider = { model -> biasState.temperature.biasByModel[model] },
                precipitationBiasProvider = { model -> biasState.precipitation.biasByModel[model] },
                windBiasProvider = { model -> biasState.wind.biasByModel[model] },
                temperatureSampleCountProvider = { model ->
                    biasState.temperature.historyByModel[model]?.size ?: 0
                },
                precipitationSampleCountProvider = { model ->
                    biasState.precipitation.historyByModel[model]?.size ?: 0
                },
                windSampleCountProvider = { model ->
                    biasState.wind.historyByModel[model]?.size ?: 0
                },
                onBiasChipClick = { model, bias ->
                    // Même handler que hourly.
                    selectedModelName = model.name
                    selectedVariableName = bias.variable.name
                }
            )
        }

        if (forecast.errors.isNotEmpty()) {
            item("errors") { PartialErrorsSection(forecast.errors) }
        }
    }

    // Sheet de détail biais — mounted en dehors du LazyColumn, se pose en
    // overlay via ModalBottomSheet. Recompose uniquement quand selectedBias
    // change (Compose détecte que les autres reads n'ont pas bougé).
    ModelBiasDetailSheet(
        selection = selectedBias,
        onDismiss = {
            // Reset des deux sentinelles → selectedBias devient null → sheet
            // se ferme. Deux writes qui vivent dans le même event handler,
            // pas de risque de désynchronisation.
            selectedModelName = ""
            selectedVariableName = ""
        },
        onOpenRanking = { model, variable ->
            // Le rang du biais sheet devient un second point d'entrée vers le
            // classement global. On ferme d'abord le détail du modèle, puis on
            // ouvre le classement sur la même variable en surlignant ce modèle.
            selectedModelName = ""
            selectedVariableName = ""
            localRankingVariableName = variable.name
            highlightedRankingModelName = model.name
            isLocalRankingOpen = true
        }
    )

    if (isLocalRankingOpen) {
        LocalModelRankingSheet(
            rankings = localRankings,
            cityLabel = forecast.city.shortLabel,
            initialVariable = localRankingVariable,
            highlightedModel = highlightedRankingModel,
            onDismiss = {
                isLocalRankingOpen = false
                highlightedRankingModelName = ""
            }
        )
    }
}

// ============================================================================
//  Contenus par mode (extensions LazyListScope pour rester dans le LazyColumn)
// ============================================================================
//
//  Extraire les items dans des extensions LazyListScope garde LoadedView compact
//  et lisible — sans le `when` on aurait 200 lignes d'imbrication. Chaque
//  extension représente un mode complet (graphe + tableaux) et est autonome.
//
//  Les keys utilisent un suffixe _daily / _hourly pour que LazyColumn traite
//  les items comme distincts entre les deux modes — cela dispose proprement
//  l'état interne (scroll horizontal des tableaux, sélection légende du chart)
//  quand on switche, plutôt que d'essayer de le préserver entre des composants
//  aux données incompatibles.

/**
 * Contenu du mode "par jour" — comportement historique de l'app avant le toggle.
 *
 * Ordre : matrice Temps → tableau min/max avec normales → précip → vent.
 * Rien de global ici : la bande de confiance horaire (rendue au-dessus du
 * toggle, indépendamment du mode) sert de vue d'ensemble à travers les deux
 * modes.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.dailyItems(
    forecast: CityForecast,
    dailyConditions: List<DayConditionsRow>,
    normals: Map<Int, DayNormals>?,
    // Providers de biais — même API que hourlyItems (Phase 1 UI). Le biais est
    // conceptuellement identique en daily et en hourly (moyenné sur 30j, pas
    // sur la journée courante), donc on branche exactement les mêmes providers.
    temperatureBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    precipitationBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    windBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    // Providers de progression parallèles pour le CalibratingChip.
    temperatureSampleCountProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> Int)? = null,
    precipitationSampleCountProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> Int)? = null,
    windSampleCountProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> Int)? = null,
    onBiasChipClick: ((com.meteocompare.app.domain.model.WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null
) {
    // Matrice Jour × Modèle des conditions météo. On ne rend pas le bloc si
    // aucune donnée — typiquement un cache pré-feature sans weather_code.
    if (dailyConditions.isNotEmpty()) {
        item("weather_by_model_daily") {
            SectionTitle(stringResource(R.string.section_weather_by_model))
            DetailTableCard {
                WeatherByModelTable(
                    rows = dailyConditions,
                    modelOrder = forecast.availableModels,
                    modifier = Modifier.padding(8.dp)
                )
            }
            WeatherLegend()
        }
    }

    // Note : le graphe TemperatureComparisonChart (min/max par modèle sur 7j)
    // a été retiré — la bande de confiance horaire rendue en haut de page
    // couvre le même besoin (comparaison inter-modèles sur l'horizon) de
    // manière plus synthétique et plus lisible, sans le doublon visuel.

    // Tableau fusionné max/min — coloration relative aux normales climatiques
    // (rouge si > normale + 2°, bleu si < normale − 2°). Si normals == null,
    // affichage neutre en attendant que les données historiques arrivent.
    item("temp_table_daily") {
        SectionTitle(stringResource(R.string.section_temp_table))
        DetailTableCard {
            MinMaxForecastTable(
                forecast = forecast,
                normals = normals,
                modelBiasProvider = temperatureBiasProvider,
                sampleCountProvider = temperatureSampleCountProvider,
                onBiasChipClick = onBiasChipClick,
                modifier = Modifier.padding(8.dp)
            )
        }
        MinMaxForecastLegend(normalsAvailable = normals != null)
    }

    item("precip_table_daily") {
        ForecastSection(
            title = stringResource(R.string.section_precipitation),
            forecast = forecast,
            extractor = { daily, idx -> daily.precipitationSum.getOrNull(idx) },
            formatter = { mm ->
                if (mm < 0.05) "0" else "${"%.1f".format(mm)} mm"
            },
            valueStyler = ::precipitationStyle,
            modelBiasProvider = precipitationBiasProvider,
            sampleCountProvider = precipitationSampleCountProvider,
            onBiasChipClick = onBiasChipClick,
            legend = { PrecipitationLegend() }
        )
    }

    item("wind_table_daily") {
        ForecastSection(
            title = stringResource(R.string.section_wind),
            forecast = forecast,
            extractor = { daily, idx -> daily.windSpeedMax.getOrNull(idx) },
            formatter = { "${it.roundToInt()} km/h" },
            valueStyler = ::windStyle,
            // Flèche de direction affichée uniquement si le vent MAX dépasse
            // 5 km/h — en dessous, la direction est du bruit statistique
            // (vent trop faible pour avoir une direction bien définie).
            // Retourner null skip la flèche pour cette cellule.
            directionExtractor = { daily, idx ->
                val speed = daily.windSpeedMax.getOrNull(idx)
                if (speed == null || speed < 5.0) null
                else daily.windDirection10mDominant.getOrNull(idx)
            },
            // Cellule plus large que le défaut 64dp pour accommoder
            // "↗ 120 km/h" (flèche 12dp + espace 2dp + valeur 3 chiffres + " km/h" ≈
            // 70-72dp) sans wrap. 80dp donne 8dp de marge visuelle et évite
            // aussi le retour à la ligne des valeurs à 2 chiffres qui étaient
            // trop proches du bord droit.
            cellWidth = 80.dp,
            modelBiasProvider = windBiasProvider,
            sampleCountProvider = windSampleCountProvider,
            onBiasChipClick = onBiasChipClick,
            legend = { WindLegend() }
        )
    }
}

/**
 * Contenu du mode "par heure" — détail horaire sur la fin de la journée en cours.
 *
 * Ordre :
 *   1. matrice Heure × Modèle du temps
 *   2. table température horaire
 *   3. table précipitations horaires
 *   4. table vent horaire
 *
 * Pas de graphe de température ici — la bande de confiance horaire, rendue
 * hors du toggle au-dessus, joue déjà ce rôle et son horizon 7 jours donne
 * plus de contexte que ne le ferait un chart limité à la journée courante.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.hourlyItems(
    forecast: CityForecast,
    // Providers optionnels du biais par variable. Retour null pour un modèle
    // donné = pas de chip (données insuffisantes ou biais non significatif).
    // Passer null au niveau screen = feature désactivée pour cette variable.
    temperatureBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    precipitationBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    windBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    // Providers de progression pour le CalibratingChip — un par variable,
    // parallèles aux providers de biais. Le count vient de
    // VariableBiasState.historyByModel[model].size qui reflète le nombre
    // effectif de jours observés (dédupliqué par date).
    temperatureSampleCountProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> Int)? = null,
    precipitationSampleCountProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> Int)? = null,
    windSampleCountProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> Int)? = null,
    // Callback pour ouvrir la sheet de détail. Signature :
    // (modèle cliqué, biais correspondant — inclut sa variable via bias.variable).
    // Le caller dispatche sur bias.variable pour peupler les données du sparkline.
    onBiasChipClick: ((com.meteocompare.app.domain.model.WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null
) {
    // Matrice Heure × Modèle des conditions météo. Conditions calculées inline
    // dans le composant (via weather_code ou fallback précipitation). Aucun
    // early-return côté LazyColumn : c'est le composant qui affichera "no data"
    // si la fenêtre horaire est vide (rare — nécessiterait cache pré-feature).
    item("weather_by_model_hourly") {
        SectionTitle(stringResource(R.string.section_weather_by_model))
        DetailTableCard {
            HourlyWeatherByModelTable(
                forecast = forecast,
                modifier = Modifier.padding(8.dp)
            )
        }
        WeatherLegend()
    }

    // Table température horaire — pas de min/max ici (une seule valeur par
    // heure), donc une simple table Heure × Modèle avec coloration absolue
    // (canicule/gel), indépendante des normales climatiques journalières.
    // La coloration passe par un HEATMAP (fond de cellule) — bien plus
    // lisible sur une matrice 24×5 qu'une simple teinte de texte : les
    // zones "chaudes" et "froides" ressortent visuellement d'un coup d'œil.
    item("temp_table_hourly") {
        SectionTitle(stringResource(R.string.section_temp_hourly))
        DetailTableCard {
            HourlyForecastTable(
                forecast = forecast,
                valueExtractor = { hourly: HourlyForecast, idx ->
                    hourly.temperature2m.getOrNull(idx)
                },
                valueFormatter = { "${it.roundToInt()}°" },
                heatmapStyler = ::hourlyTemperatureHeatmap,
                modelBiasProvider = temperatureBiasProvider,
                sampleCountProvider = temperatureSampleCountProvider,
                onBiasChipClick = onBiasChipClick,
                modifier = Modifier.padding(8.dp)
            )
        }
        HourlyTemperatureLegend()
    }

    item("precip_table_hourly") {
        SectionTitle(stringResource(R.string.section_precipitation))
        DetailTableCard {
            HourlyForecastTable(
                forecast = forecast,
                valueExtractor = { hourly: HourlyForecast, idx ->
                    hourly.precipitation.getOrNull(idx)
                },
                // Unité "mm" explicite dans chaque cellule — cohérent avec
                // le tableau vent ("km/h") et lève l'ambiguïté "0.5 = mm ?
                // pouces ? probabilité ?" quand on scrolle vite en oubliant
                // le titre de section. Reste sous 60dp même pour "15.4 mm"
                // (7 caractères en labelSmall ≈ 42dp).
                valueFormatter = { mm ->
                    if (mm < 0.05) "0 mm" else "%.1f mm".format(mm)
                },
                heatmapStyler = ::hourlyPrecipitationHeatmap,
                modelBiasProvider = precipitationBiasProvider,
                sampleCountProvider = precipitationSampleCountProvider,
                onBiasChipClick = onBiasChipClick,
                modifier = Modifier.padding(8.dp)
            )
        }
        HourlyPrecipitationLegend()
    }

    item("wind_table_hourly") {
        SectionTitle(stringResource(R.string.section_wind_hourly))
        DetailTableCard {
            HourlyForecastTable(
                forecast = forecast,
                valueExtractor = { hourly: HourlyForecast, idx ->
                    hourly.windSpeed10m.getOrNull(idx)
                },
                // Unité "km/h" explicite dans chaque cellule — cohérent avec
                // le tableau daily et lève l'ambiguïté "24 = degrés ? nœuds ?
                // km/h ?" quand on scrolle vite en oubliant le titre de section.
                valueFormatter = { "${it.roundToInt()} km/h" },
                heatmapStyler = ::hourlyWindHeatmap,
                // Même règle qu'en daily : direction affichée uniquement au
                // dessus de 5 km/h. Sous ce seuil la direction horaire est du
                // bruit (variabilité forte, pas d'info exploitable).
                directionExtractor = { hourly, idx ->
                    val speed = hourly.windSpeed10m.getOrNull(idx)
                    if (speed == null || speed < 5.0) null
                    else hourly.windDirection10m.getOrNull(idx)
                },
                // Cellules plus larges que le défaut 60dp pour accommoder
                // "↗ 120 km/h" (10dp flèche + 2dp espace + valeur + " km/h"
                // ≈ 62-64dp). 76dp donne assez de marge, un peu moins que
                // les 80dp du daily parce que la flèche horaire est plus
                // petite (10dp vs 12dp) — même densité perçue à l'écran.
                cellWidth = 76.dp,
                modelBiasProvider = windBiasProvider,
                sampleCountProvider = windSampleCountProvider,
                onBiasChipClick = onBiasChipClick,
                modifier = Modifier.padding(8.dp)
            )
        }
        HourlyWindLegend()
    }
}

// ============================================================================
//  Toggle segmenté
// ============================================================================

/**
 * Sélecteur coulissant tonal "Par heure / Par jour".
 *
 * Aucun fond commun : l'option inactive reste un simple texte et seule
 * l'option active reçoit une capsule tonale, selon le rendu
 * « Par heure   [ Par jour ] ».
 *
 * Ordre HOURLY-first plutôt que DAILY-first parce que dans l'app finale, la
 * lecture gauche→droite fait naturellement lire "par heure" comme la vue
 * détaillée qu'on active PLUS explicitement. Le sélectionné par défaut reste
 * DAILY (voir LoadedView), donc l'utilisateur voit initialement la capsule sur
 * l'option droite — configuration cohérente avec "j'ai la vue synthétique par
 * défaut, un tap à gauche pour zoomer sur l'heure".
 */
@Composable
private fun DisplayModeToggle(
    mode: DisplayMode,
    onModeChange: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(DisplayMode.HOURLY, DisplayMode.DAILY)
    ModernInlineSelector(
        options = options,
        selected = mode,
        onSelected = onModeChange,
        label = { option ->
            stringResource(
                if (option == DisplayMode.HOURLY) {
                    R.string.display_mode_hourly
                } else {
                    R.string.display_mode_daily
                }
            )
        },
        accent = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
}

/**
 * Légende du tableau température horaire en mode heatmap.
 *
 * Passage chips → barre dégradée à 10 paliers : au-delà de 5-6 chips, la
 * FlowRow devient une pluie de puces et l'œil ne perçoit plus la
 * *continuité* de l'échelle. Une barre dégradée segmentée dit d'un coup d'œil
 * "c'est un dégradé continu du froid vers le chaud" — idiome standard des
 * cartes thermiques (matplotlib, tableau, Grafana...).
 *
 * Les labels de tick sont numériques (universel, pas de traduction) et
 * couvrent les bornes internes tous les 5° : "-10 / -5 / 0 / 5 / 10 / 15 /
 * 20 / 25 / 30" — les endpoints "<-10" et "≥30" sont impliqués par la
 * position des extrémités du dégradé.
 */
@Composable
private fun HourlyTemperatureLegend() {
    HeatmapGradientLegend(
        colors = listOf(
            Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1E88E5),
            Color(0xFF4FC3F7), Color(0xFFB3E5FC), Color(0xFFDCEDC8),
            Color(0xFFFFF59D), Color(0xFFFFB74D), Color(0xFFFF7043),
            Color(0xFFC62828)
        ),
        // 10 segments → 10 labels sous chaque segment (lower bound du bin) :
        // seg1 = "<-10", seg2 = "-10", ..., seg10 = "≥30°".
        // "°" sur les seules bornes extrêmes évite de saturer visuellement.
        tickLabels = listOf(
            "<-10", "-10", "-5", "0", "5", "10", "15", "20", "25", "≥30°"
        )
    )
}

/**
 * Légende du tableau précipitations horaire en mode heatmap.
 *
 * 10 paliers colorés (le palier "sec" < 0.05 mm/h n'apparaît pas dans la
 * légende car les cellules sèches sont NEUTRES — sans couleur — dans la
 * heatmap. La légende ne montre que ce qui EST coloré.)
 *
 * Progression bleu clair → bleu profond, seuils quasi-logarithmiques
 * (0.05 → 10 mm/h) pour refléter la perception logarithmique d'intensité de
 * pluie. Unité "mm" sur le dernier tick uniquement, pour identifier la
 * grandeur sans encombrer.
 */
@Composable
private fun HourlyPrecipitationLegend() {
    HeatmapGradientLegend(
        colors = listOf(
            Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9),
            Color(0xFF64B5F6), Color(0xFF42A5F5), Color(0xFF2196F3),
            Color(0xFF1E88E5), Color(0xFF1976D2), Color(0xFF1565C0),
            Color(0xFF0D47A1)
        ),
        tickLabels = listOf(
            ".05", ".1", ".2", ".5", "1", "2", "3", "5", "7", "≥10 mm"
        )
    )
}

/**
 * Légende du tableau vent horaire en mode heatmap.
 *
 * 10 paliers colorés (le palier "calme" < 20 km/h n'apparaît pas — cellules
 * neutres). Progression jaune → orange → rouge alignée sur l'échelle de
 * Beaufort (B3 à B12), avec un pas de 10 km/h dans la zone perceptible
 * (20-100) et un pas de 20 km/h au-delà (les distinctions "cyclone
 * modéré/fort" s'estompent perceptivement).
 */
@Composable
private fun HourlyWindLegend() {
    HeatmapGradientLegend(
        colors = listOf(
            Color(0xFFFFF9C4), Color(0xFFFFF176), Color(0xFFFFEB3B),
            Color(0xFFFFCA28), Color(0xFFFFB74D), Color(0xFFFF9800),
            Color(0xFFFB8C00), Color(0xFFF57C00), Color(0xFFE64A19),
            Color(0xFFC62828)
        ),
        tickLabels = listOf(
            "20", "30", "40", "50", "60", "70", "80", "90", "100", "≥120 km/h"
        )
    )
}

/**
 * Barre dégradée à N segments avec labels de tick sous chaque segment.
 *
 * Layout : Column
 *   1. Row de N `Box` colorés en poids égal (weight=1f) → barre
 *      typographiquement rendue par blocs jointifs, clippée en coins arrondis
 *      pour un look "pill" plus doux qu'un rectangle carré.
 *   2. Row de N `Text` en poids égal → chaque label centré exactement sous
 *      son bloc (contraste avec SpaceBetween qui aligne les extrémités mais
 *      pas les intermédiaires).
 *
 * Alternatives écartées :
 *   - Dégradé continu via `Brush.horizontalGradient` : "trop lisse", ne
 *     communique plus la nature discrète des paliers du styler → l'œil
 *     s'attendrait à ce que la cellule prenne exactement la couleur du
 *     dégradé au point correspondant, ce qui serait faux.
 *   - Labels aux frontières (positions %0/%10/%20...) : mieux formellement,
 *     mais impose un layout absolu (Constraints ou Layout) au lieu d'un
 *     simple Row de poids — surcoût de code non justifié pour ce cas.
 *
 * @param colors les N couleurs des segments, gauche à droite = du plus petit
 *   au plus grand.
 * @param tickLabels N labels courts (chiffres, notation "≥"/"≤") sous chaque
 *   segment. Doit avoir la même taille que [colors].
 */
@Composable
private fun HeatmapGradientLegend(
    colors: List<Color>,
    tickLabels: List<String>
) {
    require(colors.size == tickLabels.size) {
        "colors and tickLabels must have same size (got ${colors.size} vs ${tickLabels.size})"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
        ) {
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            tickLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun DetailTableCard(content: @Composable () -> Unit) {
    val palette = detailTablePalette()
    Card(
        modifier = Modifier.padding(horizontal = 14.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.tableSurface
        ),
        //border = BorderStroke(1.dp, palette.border.copy(alpha = 0.50f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
private fun ForecastSection(
    title: String,
    forecast: CityForecast,
    extractor: (DailyForecast, Int) -> Double?,
    formatter: (Double) -> String,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    directionExtractor: ((DailyForecast, Int) -> Int?)? = null,
    cellWidth: androidx.compose.ui.unit.Dp = 72.dp,
    legend: @Composable (() -> Unit)? = null,
    // Providers de biais optionnels — passés tels-quels à ForecastTable.
    modelBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    onBiasChipClick: ((com.meteocompare.app.domain.model.WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null,
    // Provider de progression pour le CalibratingChip.
    sampleCountProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> Int)? = null
) {
    Column {
        SectionTitle(title)
        DetailTableCard {
            ForecastTable(
                forecast = forecast,
                valueExtractor = extractor,
                valueFormatter = formatter,
                valueStyler = valueStyler,
                directionExtractor = directionExtractor,
                cellWidth = cellWidth,
                modelBiasProvider = modelBiasProvider,
                onBiasChipClick = onBiasChipClick,
                sampleCountProvider = sampleCountProvider,
                modifier = Modifier.padding(8.dp)
            )
        }
        legend?.invoke()
    }
}

@Composable
internal fun TodaySummaryCard(
    today: DayConfidence,
    modelCount: Int,
    currentTemp: Double?,
    currentCondition: WeatherCondition? = null,
    currentCloudCover: Int? = null,
    fetchedAt: Instant? = null,
    onConfidenceClick: () -> Unit = {}
) {
    // Description unifiée pour TalkBack qui résume toutes les valeurs.
    // On préfixe par "Maintenant X°" si dispo — c'est l'info la plus utile
    // au premier abord pour quelqu'un qui ouvre l'app.
    val resources = LocalResources.current
    val baseDescription = com.meteocompare.app.ui.accessibility.A11yFormatter
        .todaySummaryDescription(resources, today, modelCount)
    val a11yDescription = if (currentTemp != null) {
        resources.getString(R.string.a11y_now_temp, currentTemp.roundToInt()) + ". $baseDescription"
    } else baseDescription

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    // Formatter ré-créé via remember(locale) — sinon le top-level
                    // val resterait sur la locale initiale du process pour toute
                    // sa vie. Pattern "EEEE d MMMM" en FR donne "lundi 1 janvier",
                    // en EN donne "Monday 1 January".
                    val dateLocale = LocalConfiguration.current.locales[0]
                    val longDateFmt = remember(dateLocale) {
                        DateTimeFormatter.ofPattern("EEEE d MMMM", dateLocale)
                    }
                    Text(
                        text = today.date.format(longDateFmt)
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (modelCount > 1)
                            stringResource(R.string.models_analysed_many, modelCount)
                        else
                            stringResource(R.string.models_analysed_one, modelCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                today.overallPercent?.let { ConfidenceBadge(it, onClick = onConfidenceClick) }
            }

            // Température "maintenant" — bloc principal en grand. Placé entre
            // le titre et les détails parce que c'est l'info de premier plan
            // que les utilisateurs cherchent en ouvrant l'app. Si pas dispo,
            // on saute simplement ce bloc (le layout reste cohérent).
            if (currentTemp != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${currentTemp.roundToInt()}°",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (currentCondition != null) {
                        Spacer(Modifier.width(24.dp))
                        val showCloudBadge = currentCloudCover != null &&
                            (currentCondition == WeatherCondition.PARTLY_CLOUDY ||
                                currentCondition == WeatherCondition.OVERCAST)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            AnimatedWeatherIcon(
                                condition = currentCondition,
                                size = 60.dp,
                                animated = true,
                                motionScale = 2.0f,
                                tint = Color.Unspecified
                            )
                            if (showCloudBadge) {
                                Text(
                                    text = "${currentCloudCover}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                        .copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            VariableRow(stringResource(R.string.var_temp_max), today.tempMax, "°")
            today.tempMin?.let {
                Spacer(Modifier.height(4.dp))
                VariableRow(stringResource(R.string.var_temp_min), it, "°")
            }
            Spacer(Modifier.height(4.dp))
            PrecipRow(today.precipitation)
            today.windMax?.let {
                Spacer(Modifier.height(4.dp))
                VariableRow(stringResource(R.string.var_wind_max), it, " km/h")
            }

            // Caption "mis à jour il y a X" — placé tout en bas du bloc parce
            // que c'est une métadonnée (fraîcheur des données), pas un signal
            // primaire. Aligné à droite pour ne pas concurrencer visuellement
            // les valeurs à gauche. Rafraîchit son texte tout seul via
            // rememberFormattedLastUpdated (LaunchedEffect qui re-tick au fil
            // du temps) — sans ça, un écran laissé ouvert 30 min afficherait
            // toujours "à l'instant".
            if (fetchedAt != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = com.meteocompare.app.ui.components
                        .rememberFormattedLastUpdated(fetchedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun VariableRow(label: String, score: ConfidenceScore?, unit: String) {
    if (score == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        val text = if (score.spread <= 1.0) {
            "${score.meanValue.roundToInt()}$unit"
        } else {
            "${score.minValue.roundToInt()}-${score.maxValue.roundToInt()}$unit"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(8.dp))
            ConfidencePill(score.percent)
        }
    }
}

@Composable
private fun PrecipRow(precip: PrecipitationConfidence?) {
    if (precip == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.var_precipitation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        val text = when (precip) {
            is PrecipitationConfidence.NoRain -> stringResource(R.string.precip_dry)
            is PrecipitationConfidence.Rain ->
                "${precip.minMm.roundToInt()}-${precip.maxMm.roundToInt()} mm"
            is PrecipitationConfidence.Divided ->
                stringResource(R.string.precip_divided, precip.modelsForRain, precip.modelCount)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(8.dp))
            ConfidencePill(precip.percent)
        }
    }
}

@Composable
private fun ConfidencePill(percent: Int) {
    // Pattern badge plein (couleur de confiance solide + texte `surface`)
    // identique au gros badge en haut à droite de la carte. La version
    // précédente utilisait un fond tinté à 20% : sur un `primaryContainer`
    // (lui-même coloré), ça donnait un wash très subtil qui se confondait
    // visuellement avec le fond, surtout en thème sombre. Le solide garantit
    // un contraste minimum AAA quel que soit le container de la carte parente
    // (primaryContainer, surfaceContainer, etc.) — la confiance fait partie
    // des infos qu'on veut LIRE d'un coup d'œil, pas deviner.
    val color = confidenceColor(percent)
    Surface(
        color = color,
        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall)
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ConfidenceBadge(percent: Int, onClick: () -> Unit = {}) {
    // confidenceColor() renvoie une couleur calibrée pour le thème :
    //   - clair : foncée → bon contraste avec le texte `surface` (clair)
    //   - sombre : pastel claire → bon contraste avec le texte `surface` (foncé)
    // En gardant le texte sur `surface`, on a un duo couleur/texte qui
    // s'inverse correctement entre les deux thèmes.
    val color = confidenceColor(percent)
    // Modifier.clickable plutôt que Surface(onClick=) : la surcharge onClick
    // de Surface est marquée @ExperimentalMaterial3Api dans certaines
    // versions du BOM, et on évite de propager l'opt-in pour si peu. On
    // garde le ripple natif via clickable(role=Button) qui le configure
    // automatiquement. La chevron Arrow signale visuellement le tap.
    val a11yLabel = stringResource(R.string.a11y_open_confidence_explanation, percent)
    Surface(
        color = color,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = a11yLabel }
            .testTag(TAG_CONFIDENCE_BADGE)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.confidence_badge_percent, percent),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.surface
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null, // décoratif — la sémantique est sur Surface
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun PartialErrorsSection(errors: Map<WeatherModel, String>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.models_unavailable),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(4.dp))
        errors.forEach { (model, message) ->
            Text(
                text = "• ${model.displayName} : $message",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Constants ──────────────────────────────────────────────────────────────

internal const val TAG_DETAIL_LOADING = "detail_loading"
internal const val TAG_DETAIL_ERROR = "detail_error"
internal const val TAG_DETAIL_LOADED = "detail_loaded"
internal const val TAG_CONFIDENCE_BADGE = "confidence_badge"

// ============================================================================
//  Légendes des tableaux précipitations et vent
// ============================================================================
//
//  Pourquoi 4 chips et non 5 (correspondant aux 5 paliers du styler) :
//  le premier palier "neutre" (≈ 0 dans le tableau) n'a pas de couleur dédiée
//  — c'est juste l'absence de styling, donc rien à expliquer en légende.
//  Les 4 chips restants couvrent les 4 paliers visibles.
//
//  Layout en FlowRow : compact sur 1 ligne sur la plupart des téléphones,
//  bascule sur 2 lignes sur les écrans très étroits sans perte d'info.

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrecipitationLegend() {
    LegendChipsRow(
        chips = listOf(
            Color(0xFF4FC3F7) to stringResource(R.string.precip_legend_light),
            Color(0xFF1E88E5) to stringResource(R.string.precip_legend_moderate),
            Color(0xFF1565C0) to stringResource(R.string.precip_legend_strong),
            Color(0xFF0D47A1) to stringResource(R.string.precip_legend_very_strong)
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindLegend() {
    LegendChipsRow(
        chips = listOf(
            Color(0xFFFFB74D) to stringResource(R.string.wind_legend_light),
            Color(0xFFFB8C00) to stringResource(R.string.wind_legend_moderate),
            Color(0xFFE64A19) to stringResource(R.string.wind_legend_strong),
            Color(0xFFC62828) to stringResource(R.string.wind_legend_storm)
        )
    )
}

/** Rangée de chips colorées (dot + label), utilisée par les deux légendes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendChipsRow(chips: List<Pair<Color, String>>) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        chips.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(10.dp)
                        .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
//  Stylers d'intensité — couleur + graisse modulées selon la valeur
// ============================================================================
//
//  Pourquoi des bins discrets et pas une interpolation continue :
//
//  Une interpolation HSL color de "neutral → dark blue" sur [0, 30mm] donne
//  une couleur visuellement légèrement différente à chaque pas, ce qui rend
//  les cellules adjacentes "pas-tout-à-fait-pareil" — pénible à lire, donne
//  une fausse impression de granularité. Des bins de 4-5 paliers calés sur
//  des seuils meteorologiques réels (drizzle, pluie modérée, forte, etc.)
//  rendent les sauts de couleur LISIBLES comme du signal — l'œil identifie
//  immédiatement les jours "remarquables" par rapport au reste.
//
//  Les seuils correspondent grossièrement aux catégories de Météo-France et
//  à l'échelle de Beaufort respectivement, mais simplifiées pour 5 paliers.

/**
 * Style de la cellule en fonction des précipitations en mm/jour.
 *
 *   - < 0.05 mm  : null (neutre)
 *   - 0.05–1    : bleu clair, Normal
 *   - 1–5       : bleu, Medium
 *   - 5–15      : bleu foncé, SemiBold
 *   - > 15      : bleu très foncé, Bold
 *
 *  Au-delà de 15 mm/jour on est dans la pluie forte (avertissement orange en
 *  général). Le maximum visuel est calé là pour que les fortes pluies hivernales
 *  ou orages d'été ressortent clairement.
 */
private fun precipitationStyle(mm: Double): ValueStyle? = when {
    mm < 0.05 -> null
    mm < 1.0  -> ValueStyle(color = Color(0xFF4FC3F7), fontWeight = FontWeight.Normal)
    mm < 5.0  -> ValueStyle(color = Color(0xFF1E88E5), fontWeight = FontWeight.Medium)
    mm < 15.0 -> ValueStyle(color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
    else      -> ValueStyle(color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold)
}

/**
 * Style de la cellule en fonction du vent max en km/h.
 *
 *   - < 20 km/h : null (calme, neutre)
 *   - 20–40     : orange clair, Normal           (brise)
 *   - 40–60     : orange, Medium                 (vent modéré)
 *   - 60–80     : orange foncé, SemiBold         (vent fort, vigilance jaune)
 *   - > 80      : rouge, Bold                    (tempête, vigilance orange/rouge)
 *
 *  Progression orange→rouge plutôt que bleu/vert : cohérent avec les codes de
 *  vigilance Météo-France et l'intuition générale "rouge = attention".
 *  Distinct des températures (bleu/rouge) ET des précipitations (bleu) pour
 *  éviter toute confusion visuelle entre tableaux adjacents.
 */
private fun windStyle(kmh: Double): ValueStyle? = when {
    kmh < 20.0 -> null
    kmh < 40.0 -> ValueStyle(color = Color(0xFFFFB74D), fontWeight = FontWeight.Normal)
    kmh < 60.0 -> ValueStyle(color = Color(0xFFFB8C00), fontWeight = FontWeight.Medium)
    kmh < 80.0 -> ValueStyle(color = Color(0xFFE64A19), fontWeight = FontWeight.SemiBold)
    else       -> ValueStyle(color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
}

// ============================================================================
//  Helper pour la reconstruction de BiasSelection depuis les rememberSaveable
// ============================================================================

/**
 * Bandeau discret affiché en tête de la liste tant qu'aucun chip de biais
 * n'est disponible pour la ville. Communique honnêtement à l'utilisateur que
 * l'app est en train de collecter l'historique, sans être intrusif.
 *
 * Cas d'affichage :
 *   - Première utilisation, avant que le worker n'ait fetché l'observation
 *     historique et/ou le backfill historical-forecast n'ait tourné.
 *   - Cas dégénéré où toutes les variables × modèles sont classées
 *     NOT_SIGNIFICANT (peu probable mais possible avec des modèles très
 *     calibrés — dans ce cas le hint sur-communique un peu, tradeoff accepté).
 *
 * Design : Card à surface `surfaceContainerLow`, icône info, texte muted.
 * Reprend le vocabulaire du reste de l'app (Card 16dp de marge horizontale,
 * même padding interne que les sections météo).
 */
@Composable
private fun BiasHistoryHint() {
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.bias_history_collecting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * enumValueOf tolérant aux noms invalides. Utilisé par la reconstruction de
 * BiasSelection dans LoadedView : si un enum est renommé/supprimé entre deux
 * versions, une valeur sauvegardée en Bundle qui ne matche plus renvoie null
 * plutôt que de crash — la sheet restera simplement fermée après restauration.
 */
private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()
