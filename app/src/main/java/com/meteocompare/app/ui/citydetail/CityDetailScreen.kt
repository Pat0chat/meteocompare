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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.DayConditionsRow
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.CollapsibleSectionHeader
import com.meteocompare.app.ui.components.OfflineDataBanner
import com.meteocompare.app.ui.citylist.WeatherAccent
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.temperatureMinMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
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
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val biasState by viewModel.biasState.collectAsStateWithLifecycle()
    val evolutionState by viewModel.evolutionState.collectAsStateWithLifecycle()
    val marineState by viewModel.marineState.collectAsStateWithLifecycle()
    val collapsedSections by viewModel.collapsedSections.collectAsStateWithLifecycle()
    val detailViewMode by viewModel.detailViewMode.collectAsStateWithLifecycle()
    val detailContentTab by viewModel.detailContentTab.collectAsStateWithLifecycle()
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
        isOnline = isOnline,
        biasState = biasState,
        evolutionState = evolutionState,
        marineState = marineState,
        collapsedSections = collapsedSections,
        detailViewMode = detailViewMode,
        detailContentTab = detailContentTab,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRefreshMarine = viewModel::refreshMarine,
        onSectionExpandedChange = viewModel::setSectionExpanded,
        onDetailViewModeChange = viewModel::setDetailViewMode,
        onDetailContentTabChange = viewModel::setDetailContentTab,
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
    isOnline: Boolean = true,
    biasState: BiasScreenState,
    evolutionState: ForecastEvolutionState = ForecastEvolutionState.Idle,
    marineState: MarineUiState = MarineUiState.Idle,
    collapsedSections: Set<CityDetailSection> = emptySet(),
    detailViewMode: CityDetailViewMode = CityDetailViewMode.DEFAULT,
    detailContentTab: CityDetailContentTab = CityDetailContentTab.DEFAULT,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshMarine: () -> Unit = {},
    onSectionExpandedChange: (CityDetailSection, Boolean) -> Unit = { _, _ -> },
    onDetailViewModeChange: (CityDetailViewMode) -> Unit = {},
    onDetailContentTabChange: (CityDetailContentTab) -> Unit = {},
    onConfidenceClick: (isoDate: String) -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    val loaded = state as? CityDetailUiState.Loaded
                    Column {
                        Text(
                            text = loaded?.forecast?.city?.name
                                ?: stringResource(R.string.title_detail_fallback),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        loaded?.forecast?.city?.let { city ->
                            val subtitle = city.country
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_refresh)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
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
                        calculatedAt = s.calculatedAt,
                        fetchedAt = s.fetchedAt,
                        isOnline = isOnline,
                        biasState = biasState,
                        evolutionState = evolutionState,
                        marineState = marineState,
                        collapsedSections = collapsedSections,
                        detailViewMode = detailViewMode,
                        detailContentTab = detailContentTab,
                        padding = padding,
                        onSectionExpandedChange = onSectionExpandedChange,
                        onDetailViewModeChange = onDetailViewModeChange,
                        onDetailContentTabChange = onDetailContentTabChange,
                        onRefreshMarine = onRefreshMarine,
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

internal fun simplifiedTimelineItemIndex(
    isOnline: Boolean,
    hasInsights: Boolean
): Int = (if (isOnline) 0 else 1) + 1 + (if (hasInsights) 1 else 0)

internal fun insightTimelineTarget(insight: ForecastInsight): SimplifiedTimelinePoint? =
    insight.point ?: insight.event?.peakPoint ?: insight.referencePoint

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
    calculatedAt: Instant,
    fetchedAt: Instant?,
    isOnline: Boolean,
    biasState: BiasScreenState,
    evolutionState: ForecastEvolutionState,
    marineState: MarineUiState,
    collapsedSections: Set<CityDetailSection>,
    detailViewMode: CityDetailViewMode,
    detailContentTab: CityDetailContentTab,
    padding: PaddingValues,
    onSectionExpandedChange: (CityDetailSection, Boolean) -> Unit,
    onDetailViewModeChange: (CityDetailViewMode) -> Unit,
    onDetailContentTabChange: (CityDetailContentTab) -> Unit,
    onRefreshMarine: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit = {}
) {
    val displayMode = detailViewMode.toDisplayMode()
    val reliabilityExpanded = CityDetailSection.CONFIDENCE !in collapsedSections
    val evolutionExpanded = CityDetailSection.FORECAST_EVOLUTION !in collapsedSections
    val todaySummaryExpanded = CityDetailSection.TODAY_SUMMARY !in collapsedSections
    val insightsExpanded = CityDetailSection.INSIGHTS !in collapsedSections
    val timelineExpanded = CityDetailSection.TIMELINE !in collapsedSections
    val detailedForecastExpanded = CityDetailSection.DETAILED_FORECAST !in collapsedSections
    val marineExpanded = CityDetailSection.MARINE !in collapsedSections
    // Même instant que celui utilisé par le ViewModel pour les agrégats
    // « maintenant » : résumé, chronologie et tableaux restent cohérents.
    val presentationNow = calculatedAt
    val overviewTimeline = remember(forecast, presentationNow) {
        buildOverviewTimeline(forecast, presentationNow)
    }
    val forecastEvents = remember(overviewTimeline) { detectForecastEvents(overviewTimeline) }
    val insights = remember(forecastEvents) { buildForecastInsights(forecastEvents) }
    val evolutionHighlight = (evolutionState as? ForecastEvolutionState.Loaded)?.highlight
    val hasInsightSection = insights.isNotEmpty() || evolutionHighlight != null
    val hourlyTimelinePoints = remember(forecast, presentationNow) {
        buildSimplifiedTimeline(forecast, DisplayMode.HOURLY, presentationNow)
    }
    val dailyTimelinePoints = remember(forecast, presentationNow) {
        buildSimplifiedTimeline(forecast, DisplayMode.DAILY, presentationNow)
    }
    val timelineAvailableModes = remember(hourlyTimelinePoints, dailyTimelinePoints) {
        listOfNotNull(
            DisplayMode.HOURLY.takeIf { hourlyTimelinePoints.isNotEmpty() },
            DisplayMode.DAILY.takeIf { dailyTimelinePoints.isNotEmpty() }
        ).toSet()
    }
    var timelineMode by remember(overviewTimeline) { mutableStateOf(overviewTimeline.mode) }
    val timelineAnalysisPoints = when (timelineMode) {
        DisplayMode.HOURLY -> hourlyTimelinePoints
        DisplayMode.DAILY -> dailyTimelinePoints
    }
    val timelineEvents = remember(timelineMode, timelineAnalysisPoints, forecast.city.timezone) {
        detectForecastEvents(
            OverviewTimeline(
                mode = timelineMode,
                analysisPoints = timelineAnalysisPoints,
                timezone = forecast.city.timezone
            )
        )
    }
    val timelineDisplayPoints = remember(timelineAnalysisPoints) {
        selectRegularTimelinePoints(timelineAnalysisPoints)
    }
    var focusedTimelinePoint by remember(overviewTimeline) {
        mutableStateOf<SimplifiedTimelinePoint?>(null)
    }
    var timelineFocusRequestId by remember(overviewTimeline) { mutableStateOf(0) }
    val contentListState = rememberLazyListState()
    // Items preceding the timeline: optional offline banner, today summary,
    // then optional insights. Keep this count in sync with the LazyColumn below.
    val timelineItemIndex = simplifiedTimelineItemIndex(
        isOnline = isOnline,
        hasInsights = hasInsightSection
    )
    LaunchedEffect(timelineFocusRequestId) {
        if (timelineFocusRequestId > 0) {
            contentListState.animateScrollToItem(timelineItemIndex)
        }
    }
    val cityToday = remember(forecast.city.timezone, presentationNow) {
        cityLocalDate(forecast.city.timezone, presentationNow)
    }
    val summaryDay = remember(weekly, cityToday) {
        weekly.firstOrNull { !it.date.isBefore(cityToday) } ?: weekly.lastOrNull()
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
        state = contentListState,
        modifier = Modifier.fillMaxSize().testTag(TAG_DETAIL_LOADED),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isOnline) {
            item("offline_data_banner") {
                OfflineDataBanner(fetchedAt = fetchedAt)
            }
        }

        item("today_summary") {
            summaryDay?.let { today ->
                TodaySummaryCard(
                    today = today,
                    modelCount = forecast.availableModels.size,
                    currentTemp = currentTemp,
                    currentCondition = currentCondition,
                    currentCloudCover = currentCloudCover,
                    fetchedAt = fetchedAt,
                    isOnline = isOnline,
                    expanded = todaySummaryExpanded,
                    onExpandedChange = { expanded ->
                        onSectionExpandedChange(CityDetailSection.TODAY_SUMMARY, expanded)
                    },
                    onConfidenceClick = { onConfidenceClick(today.date.toString()) }
                )
            }
        }

        if (hasInsightSection) {
            item("forecast_insights") {
                ForecastInsightsSection(
                    insights = insights,
                    evolutionHighlight = evolutionHighlight,
                    timezone = forecast.city.timezone,
                    modelCount = forecast.availableModels.size,
                    referencePoint = overviewTimeline.analysisPoints.firstOrNull(),
                    expanded = insightsExpanded,
                    onExpandedChange = { expanded ->
                        onSectionExpandedChange(CityDetailSection.INSIGHTS, expanded)
                    },
                    onInsightClick = { insight ->
                        val target = insightTimelineTarget(insight)
                        if (target != null) {
                            when {
                                target.instant != null && DisplayMode.HOURLY in timelineAvailableModes ->
                                    timelineMode = DisplayMode.HOURLY
                                target.date != null && DisplayMode.DAILY in timelineAvailableModes ->
                                    timelineMode = DisplayMode.DAILY
                            }
                            focusedTimelinePoint = target
                            timelineFocusRequestId += 1
                        }
                    }
                )
            }
        }

        if (timelineDisplayPoints.isNotEmpty()) {
            item("simplified_timeline_overview") {
                SimplifiedTimelineCard(
                    points = timelineDisplayPoints,
                    events = timelineEvents,
                    mode = timelineMode,
                    timezone = forecast.city.timezone,
                    focusPoint = focusedTimelinePoint,
                    focusRequestId = timelineFocusRequestId,
                    onModeChange = { newMode ->
                        timelineMode = newMode
                        focusedTimelinePoint = null
                    },
                    availableModes = timelineAvailableModes,
                    now = presentationNow,
                    expanded = timelineExpanded,
                    onExpandedChange = { expanded ->
                        onSectionExpandedChange(CityDetailSection.TIMELINE, expanded)
                    }
                )
            }
        }

        if (evolutionState != ForecastEvolutionState.Idle &&
            evolutionState != ForecastEvolutionState.Unavailable &&
            evolutionState !is ForecastEvolutionState.Error
        ) {
            item("forecast_evolution") {
                ForecastEvolutionSection(
                    state = evolutionState,
                    expanded = evolutionExpanded,
                    onExpandedChange = { expanded ->
                        onSectionExpandedChange(CityDetailSection.FORECAST_EVOLUTION, expanded)
                    }
                )
            }
        }

        val hasConfidenceData = hourlyBands.size >= 2 ||
            hourlyPrecipBands.size >= 2 ||
            hourlyWindBands.size >= 2
        if (hasConfidenceData || localRankings.hasAnyRanking) {
            item("local_reliability") {
                LocalReliabilitySection(
                    overallConfidencePercent = summaryDay?.overallPercent,
                    familyCount = forecast.availableModels
                        .map(ForecastConsensus::groupFor)
                        .distinct()
                        .size,
                    rankings = localRankings,
                    tempBands = hourlyBands,
                    precipBands = hourlyPrecipBands,
                    windBands = hourlyWindBands,
                    timezone = forecast.city.timezone,
                    normals = normals,
                    expanded = reliabilityExpanded,
                    onExpandedChange = { expanded ->
                        onSectionExpandedChange(CityDetailSection.CONFIDENCE, expanded)
                    },
                    onOpenRanking = { variable ->
                        localRankingVariableName = variable.name
                        highlightedRankingModelName = ""
                        isLocalRankingOpen = true
                    }
                )
            }
        }

        val hasAnyBias =
            biasState.temperature.biasByModel.values.any { it != null } ||
            biasState.precipitation.biasByModel.values.any { it != null } ||
            biasState.wind.biasByModel.values.any { it != null }

        item("detailed_forecast_section") {
            DetailedForecastSection(
                mode = displayMode,
                tab = detailContentTab,
                forecast = forecast,
                dailyConditions = dailyConditions,
                normals = normals,
                presentationNow = presentationNow,
                cityToday = cityToday,
                showBiasHistoryHint = !hasAnyBias &&
                    detailContentTab != CityDetailContentTab.CONDITIONS,
                onModeChange = { onDetailViewModeChange(it.toPreference()) },
                onTabChange = onDetailContentTabChange,
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
                    selectedModelName = model.name
                    selectedVariableName = bias.variable.name
                },
                expanded = detailedForecastExpanded,
                onExpandedChange = { expanded ->
                    onSectionExpandedChange(CityDetailSection.DETAILED_FORECAST, expanded)
                }
            )
        }

        if (marineState !is MarineUiState.Idle) {
            item("marine_section") {
                MarineSection(
                    state = marineState,
                    onRefresh = onRefreshMarine,
                    expanded = marineExpanded,
                    onExpandedChange = { expanded ->
                        onSectionExpandedChange(CityDetailSection.MARINE, expanded)
                    }
                )
            }
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
//  Comparaison détaillée : une seule famille de données à la fois
// ============================================================================

@Composable
private fun DetailedForecastSection(
    mode: DisplayMode,
    tab: CityDetailContentTab,
    forecast: CityForecast,
    dailyConditions: List<DayConditionsRow>,
    normals: Map<Int, DayNormals>?,
    presentationNow: Instant,
    cityToday: java.time.LocalDate,
    showBiasHistoryHint: Boolean,
    onModeChange: (DisplayMode) -> Unit,
    onTabChange: (CityDetailContentTab) -> Unit,
    temperatureBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    precipitationBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    windBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    temperatureSampleCountProvider: ((WeatherModel) -> Int)? = null,
    precipitationSampleCountProvider: ((WeatherModel) -> Int)? = null,
    windSampleCountProvider: ((WeatherModel) -> Int)? = null,
    onBiasChipClick: ((WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            DetailedComparisonControls(
                mode = mode,
                selectedTab = tab,
                onModeChange = onModeChange,
                onTabChange = onTabChange,
                expanded = expanded,
                onExpandedChange = onExpandedChange
            )

            if (expanded) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            )

            if (showBiasHistoryHint) {
                BiasHistoryHint(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            DetailedComparisonContent(
                mode = mode,
                tab = tab,
                forecast = forecast,
                dailyConditions = dailyConditions,
                normals = normals,
                presentationNow = presentationNow,
                cityToday = cityToday,
                temperatureBiasProvider = temperatureBiasProvider,
                precipitationBiasProvider = precipitationBiasProvider,
                windBiasProvider = windBiasProvider,
                temperatureSampleCountProvider = temperatureSampleCountProvider,
                precipitationSampleCountProvider = precipitationSampleCountProvider,
                windSampleCountProvider = windSampleCountProvider,
                onBiasChipClick = onBiasChipClick
            )
            }
        }
    }
}

@Composable
private fun DetailedComparisonContent(
    mode: DisplayMode,
    tab: CityDetailContentTab,
    forecast: CityForecast,
    dailyConditions: List<DayConditionsRow>,
    normals: Map<Int, DayNormals>?,
    presentationNow: Instant,
    cityToday: java.time.LocalDate,
    temperatureBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    precipitationBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    windBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    temperatureSampleCountProvider: ((WeatherModel) -> Int)? = null,
    precipitationSampleCountProvider: ((WeatherModel) -> Int)? = null,
    windSampleCountProvider: ((WeatherModel) -> Int)? = null,
    onBiasChipClick: ((WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null
) {
    Column {
        when (tab) {
            CityDetailContentTab.CONDITIONS -> {
                if (mode == DisplayMode.DAILY) {
                    if (dailyConditions.isEmpty()) {
                        DetailedEmptyState()
                    } else {
                        DetailTableCard {
                            WeatherByModelTable(
                                rows = dailyConditions,
                                modelOrder = forecast.availableModels,
                                today = cityToday,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        WeatherLegend()
                    }
                } else {
                    DetailTableCard {
                        HourlyWeatherByModelTable(
                            forecast = forecast,
                            now = presentationNow,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    WeatherLegend()
                }
            }

            CityDetailContentTab.TEMPERATURE -> {
                if (mode == DisplayMode.DAILY) {
                    DetailTableCard {
                        MinMaxForecastTable(
                            forecast = forecast,
                            normals = normals,
                            now = presentationNow,
                            modelBiasProvider = temperatureBiasProvider,
                            sampleCountProvider = temperatureSampleCountProvider,
                            onBiasChipClick = onBiasChipClick,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    MinMaxForecastLegend(normalsAvailable = normals != null)
                } else {
                    DetailTableCard {
                        HourlyForecastTable(
                            forecast = forecast,
                            now = presentationNow,
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
            }

            CityDetailContentTab.PRECIPITATION -> {
                if (mode == DisplayMode.DAILY) {
                    ForecastTableContent(
                        forecast = forecast,
                        now = presentationNow,
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
                } else {
                    DetailTableCard {
                        HourlyForecastTable(
                            forecast = forecast,
                            now = presentationNow,
                            valueExtractor = { hourly: HourlyForecast, idx ->
                                hourly.precipitation.getOrNull(idx)
                            },
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
            }

            CityDetailContentTab.WIND -> {
                val gustAbbreviation = stringResource(R.string.wind_gust_abbreviation)
                if (mode == DisplayMode.DAILY) {
                    ForecastTableContent(
                        forecast = forecast,
                        now = presentationNow,
                        extractor = { daily, idx -> daily.windSpeedMax.getOrNull(idx) },
                        formatter = { "${it.roundToInt()} km/h" },
                        valueStyler = ::windStyle,
                        secondaryExtractor = { daily, idx -> daily.windGustsMax.getOrNull(idx) },
                        secondaryFormatter = { "$gustAbbreviation ${it.roundToInt()}" },
                        directionExtractor = { daily, idx ->
                            val speed = daily.windSpeedMax.getOrNull(idx)
                            if (speed == null || speed < 5.0) null
                            else daily.windDirection10mDominant.getOrNull(idx)
                        },
                        modelBiasProvider = windBiasProvider,
                        sampleCountProvider = windSampleCountProvider,
                        onBiasChipClick = onBiasChipClick,
                        legend = { WindLegend() }
                    )
                } else {
                    DetailTableCard {
                        HourlyForecastTable(
                            forecast = forecast,
                            now = presentationNow,
                            valueExtractor = { hourly: HourlyForecast, idx ->
                                hourly.windSpeed10m.getOrNull(idx)
                            },
                            valueFormatter = { "${it.roundToInt()} km/h" },
                            heatmapStyler = ::hourlyWindHeatmap,
                            secondaryValueExtractor = { hourly, idx ->
                                hourly.windGusts10m.getOrNull(idx)
                            },
                            secondaryValueFormatter = { "$gustAbbreviation ${it.roundToInt()}" },
                            directionExtractor = { hourly, idx ->
                                val speed = hourly.windSpeed10m.getOrNull(idx)
                                if (speed == null || speed < 5.0) null
                                else hourly.windDirection10m.getOrNull(idx)
                            },
                            modelBiasProvider = windBiasProvider,
                            sampleCountProvider = windSampleCountProvider,
                            onBiasChipClick = onBiasChipClick,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    HourlyWindLegend()
                }
            }
        }
    }
}

@Composable
private fun DetailTableCard(
    content: @Composable () -> Unit
) {
    // La section détaillée possède désormais sa propre Surface englobante.
    // Le tableau conserve uniquement son cadre interne (DetailTableShape),
    // sans ajouter une seconde Card visuelle.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        content()
    }
}

@Composable
private fun ForecastTableContent(
    forecast: CityForecast,
    now: Instant,
    extractor: (DailyForecast, Int) -> Double?,
    formatter: (Double) -> String,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    secondaryExtractor: ((DailyForecast, Int) -> Double?)? = null,
    secondaryFormatter: ((Double) -> String)? = null,
    directionExtractor: ((DailyForecast, Int) -> Int?)? = null,
    legend: @Composable (() -> Unit)? = null,
    modelBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    onBiasChipClick: ((WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null,
    sampleCountProvider: ((WeatherModel) -> Int)? = null
) {
    DetailTableCard {
        ForecastTable(
            forecast = forecast,
            now = now,
            valueExtractor = extractor,
            valueFormatter = formatter,
            valueStyler = valueStyler,
            secondaryValueExtractor = secondaryExtractor,
            secondaryValueFormatter = secondaryFormatter,
            directionExtractor = directionExtractor,
            modelBiasProvider = modelBiasProvider,
            onBiasChipClick = onBiasChipClick,
            sampleCountProvider = sampleCountProvider,
            modifier = Modifier.padding(8.dp)
        )
    }
    legend?.invoke()
}

@Composable
private fun DetailedEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.detail_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
 * 10 paliers colorés (le palier "sec" < 0.05 mm sur l’heure n'apparaît pas dans la
 * légende car les cellules sèches sont NEUTRES — sans couleur — dans la
 * heatmap. La légende ne montre que ce qui EST coloré.)
 *
 * Progression bleu clair → bleu profond, seuils quasi-logarithmiques
 * (0.05 → 10 mm sur l’heure) pour refléter la perception logarithmique d'intensité de
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
    Column {
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
        WindGustLegendHint()
    }
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
internal fun TodaySummaryCard(
    today: DayConfidence,
    modelCount: Int,
    currentTemp: Double?,
    currentCondition: WeatherCondition? = null,
    currentCloudCover: Int? = null,
    fetchedAt: Instant? = null,
    isOnline: Boolean = true,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    onConfidenceClick: () -> Unit = {}
) {
    val resources = LocalResources.current
    val baseDescription = com.meteocompare.app.ui.accessibility.A11yFormatter
        .todaySummaryDescription(resources, today, modelCount)
    val a11yDescription = if (currentTemp != null) {
        resources.getString(R.string.a11y_now_temp, currentTemp.roundToInt()) + ". $baseDescription"
    } else baseDescription
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val weatherAccent = WeatherAccent.of(currentCondition, isDark)
    val dateLocale = LocalConfiguration.current.locales[0]
    val longDateFmt = remember(dateLocale) {
        DateTimeFormatter.ofPattern("EEEE d MMMM", dateLocale)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(weatherAccent)
        )
        Column(modifier = Modifier.padding(vertical = 7.dp)) {
            CollapsibleSectionHeader(
                text = today.date.format(longDateFmt).replaceFirstChar { it.uppercase() },
                subtitle = if (modelCount > 1) {
                    stringResource(R.string.models_analysed_many, modelCount)
                } else {
                    stringResource(R.string.models_analysed_one, modelCount)
                },
                expanded = expanded,
                onToggle = { onExpandedChange(!expanded) },
                trailingContent = if (fetchedAt != null) {
                    { DataFreshnessPill(fetchedAt = fetchedAt, isOnline = isOnline) }
                } else null
            )

            if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            weatherAccent.copy(alpha = if (isDark) 0.14f else 0.08f),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentCondition != null) {
                        AnimatedWeatherIcon(
                            condition = currentCondition,
                            size = 56.dp,
                            animated = true,
                            motionScale = 2.0f,
                            tint = Color.Unspecified
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Thermostat,
                            contentDescription = null,
                            tint = weatherAccent,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTemp?.let { "${it.roundToInt()}°" } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                    currentCondition?.let { condition ->
                        Text(
                            text = detailWeatherConditionLabel(condition),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (currentCloudCover != null) {
                        Text(
                            text = stringResource(R.string.home_cloud_cover, currentCloudCover),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                today.overallPercent?.let { percent ->
                    ConfidenceBadge(percent, onClick = onConfidenceClick)
                }
            }

            DetailMetricGrid(today = today)
            }
            }
        }
    }
}

@Composable
private fun DataFreshnessPill(
    fetchedAt: Instant,
    isOnline: Boolean
) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isOnline) {
                com.meteocompare.app.ui.components.rememberFormattedLastUpdated(fetchedAt)
            } else {
                stringResource(
                    R.string.offline_saved_data_age_inline,
                    com.meteocompare.app.ui.components.rememberFormattedLastUpdated(fetchedAt)
                )
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (isOnline) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (isOnline) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}

@Composable
private fun DetailMetricGrid(today: DayConfidence) {
    val hasTemperature = today.tempMax != null || today.tempMin != null
    val hasWeather = today.precipitation != null || today.windMax != null || today.windGustMax != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hasTemperature) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                today.tempMin?.let { score ->
                    DetailWeatherMetric(
                        label = stringResource(R.string.var_temp_min),
                        icon = Icons.Outlined.Thermostat,
                        accent = temperatureMinMetricAccent(),
                        value = score.meanValue.roundToInt().toString(),
                        unit = "°",
                        supporting = stringResource(R.string.metric_summary_average),
                        detail = stringResource(
                            R.string.metric_summary_range_and_spread,
                            detailRange(score, unit = "°"),
                            detailSpread(score, unit = "°")
                        ),
                        confidence = score.convergencePercent,
                        modifier = Modifier.weight(1f)
                    )
                } ?: Spacer(Modifier.weight(1f))

                today.tempMax?.let { score ->
                    DetailWeatherMetric(
                        label = stringResource(R.string.var_temp_max),
                        icon = Icons.Outlined.Thermostat,
                        accent = temperatureMetricAccent(),
                        value = score.meanValue.roundToInt().toString(),
                        unit = "°",
                        supporting = stringResource(R.string.metric_summary_average),
                        detail = stringResource(
                            R.string.metric_summary_range_and_spread,
                            detailRange(score, unit = "°"),
                            detailSpread(score, unit = "°")
                        ),
                        confidence = score.convergencePercent,
                        modifier = Modifier.weight(1f)
                    )
                } ?: Spacer(Modifier.weight(1f))
            }
        }

        if (hasWeather) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                today.precipitation?.let { precipitation ->
                    val presentation = detailPrecipitationPresentation(precipitation)
                    DetailWeatherMetric(
                        label = stringResource(R.string.var_precipitation),
                        icon = Icons.Outlined.WaterDrop,
                        accent = precipitationMetricAccent(),
                        value = presentation.value,
                        unit = presentation.unit,
                        supporting = presentation.supporting,
                        detail = presentation.detail,
                        confidence = precipitation.convergencePercent,
                        modifier = Modifier.weight(1f)
                    )
                } ?: Spacer(Modifier.weight(1f))

                val windScore = today.windMax ?: today.windGustMax
                windScore?.let { score ->
                    val isGustOnly = today.windMax == null
                    DetailWeatherMetric(
                        label = stringResource(
                            if (isGustOnly) R.string.metric_detail_gusts else R.string.metric_detail_wind
                        ),
                        icon = Icons.Outlined.Air,
                        accent = windMetricAccent(),
                        value = score.meanValue.roundToInt().toString(),
                        unit = "km/h",
                        supporting = stringResource(R.string.metric_summary_average),
                        detail = stringResource(
                            R.string.metric_summary_range,
                            detailRange(score, unit = " km/h")
                        ),
                        tertiary = if (isGustOnly) null else today.windGustMax?.let { gust ->
                            stringResource(
                                R.string.metric_gust_detail,
                                detailRange(gust, unit = "")
                            )
                        },
                        confidence = score.convergencePercent,
                        modifier = Modifier.weight(1f)
                    )
                } ?: Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailWeatherMetric(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    value: String,
    confidence: Int?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    supporting: String? = null,
    detail: String? = null,
    tertiary: String? = null
) {
    Surface(
        modifier = modifier.heightIn(min = 180.dp),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
        border = BorderStroke(
            width = 1.dp,
            color = accent.copy(alpha = 0.16f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!unit.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            if (!supporting.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!tertiary.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tertiary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.weight(1f))
            confidence?.let { DetailMetricMeta(confidence = it) }
        }
    }
}

@Composable
private fun DetailMetricMeta(
    confidence: Int
) {
    val confidenceTint = confidenceColor(confidence)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(confidenceTint, androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.metric_agreement, confidence),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private data class DetailMetricPresentation(
    val value: String,
    val unit: String? = null,
    val supporting: String? = null,
    val detail: String? = null
)

private fun detailRange(
    score: ConfidenceScore,
    unit: String
): String = "${score.minValue.roundToInt()}–${score.maxValue.roundToInt()}$unit"

private fun detailSpread(
    score: ConfidenceScore,
    unit: String
): String = "${score.spread.roundToInt()}$unit"

@Composable
private fun detailPrecipitationPresentation(
    precipitation: PrecipitationConfidence
): DetailMetricPresentation = when (precipitation) {
    is PrecipitationConfidence.NoRain ->
        DetailMetricPresentation(
            value = stringResource(R.string.precip_dry),
            supporting = precipitation.meta.probabilityPercent?.let { probability ->
                stringResource(R.string.metric_precip_probability_only, probability)
            } ?: stringResource(R.string.confidence_precip_all_dry),
            detail = stringResource(
                R.string.metric_summary_max_model_precip,
                precipitation.maxAmountMm
            )
        )

    is PrecipitationConfidence.Rain -> {
        val conditional = precipitation.meta.conditionalAmountMm ?: precipitation.meanMm
        val probability = precipitation.meta.probabilityPercent
        val rawRange = "${precipitation.minMm.roundToInt()}–${precipitation.maxMm.roundToInt()} mm"
        DetailMetricPresentation(
            value = (precipitation.meta.centralAmountMm ?: conditional).roundToInt().toString(),
            unit = "mm",
            supporting = if (probability != null) {
                stringResource(R.string.metric_precip_probability_conditional, probability, conditional)
            } else stringResource(R.string.metric_precip_if_rain, "${conditional.roundToInt()} mm"),
            detail = stringResource(R.string.metric_precip_rain_range, rawRange)
        )
    }

    is PrecipitationConfidence.Divided -> {
        val conditional = precipitation.meta.conditionalAmountMm ?: precipitation.rainMeanMm
        val probability = precipitation.meta.probabilityPercent
        val rawRange = "${precipitation.rainMinMm.roundToInt()}–${precipitation.rainMaxMm.roundToInt()} mm"
        DetailMetricPresentation(
            value = (precipitation.meta.centralAmountMm ?: conditional).roundToInt().toString(),
            unit = "mm",
            supporting = if (probability != null) {
                stringResource(R.string.metric_precip_probability_conditional, probability, conditional)
            } else stringResource(R.string.metric_precip_if_rain, "${conditional.roundToInt()} mm"),
            detail = stringResource(R.string.metric_precip_rain_range, rawRange)
        )
    }
}

@Composable
private fun detailWeatherConditionLabel(condition: WeatherCondition): String = stringResource(
    when (condition) {
        WeatherCondition.CLEAR -> R.string.weather_clear
        WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherCondition.OVERCAST -> R.string.weather_overcast
        WeatherCondition.FOG -> R.string.weather_fog
        WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
        WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
        WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
        WeatherCondition.UNKNOWN -> R.string.weather_unknown
    }
)

@Composable
private fun ConfidenceBadge(percent: Int, onClick: () -> Unit = {}) {
    val color = confidenceColor(percent)
    val a11yLabel = stringResource(R.string.a11y_open_confidence_explanation, percent)
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = a11yLabel }
            .testTag(TAG_CONFIDENCE_BADGE)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ){
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_agreement_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }
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
    Column {
        LegendChipsRow(
            chips = listOf(
                Color(0xFFFFB74D) to stringResource(R.string.wind_legend_light),
                Color(0xFFFB8C00) to stringResource(R.string.wind_legend_moderate),
                Color(0xFFE64A19) to stringResource(R.string.wind_legend_strong),
                Color(0xFFC62828) to stringResource(R.string.wind_legend_storm)
            )
        )
        WindGustLegendHint()
    }
}

@Composable
private fun WindGustLegendHint() {
    Text(
        text = stringResource(R.string.wind_table_legend),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
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
 *     J+1 n'ait accumulé assez de jours correspondants.
 *   - Cas dégénéré où toutes les variables × modèles sont classées
 *     NOT_SIGNIFICANT (peu probable mais possible avec des modèles très
 *     calibrés — dans ce cas le hint sur-communique un peu, tradeoff accepté).
 *
 * Design : bandeau tonal léger intégré directement dans la Surface des
 * prévisions détaillées, sans Card imbriquée supplémentaire.
 */
@Composable
private fun BiasHistoryHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.48f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
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

/**
 * enumValueOf tolérant aux noms invalides. Utilisé par la reconstruction de
 * BiasSelection dans LoadedView : si un enum est renommé/supprimé entre deux
 * versions, une valeur sauvegardée en Bundle qui ne matche plus renvoie null
 * plutôt que de crash — la sheet restera simplement fermée après restauration.
 */
private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()
