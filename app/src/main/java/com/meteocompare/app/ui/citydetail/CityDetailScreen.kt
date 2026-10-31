package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.util.windCardinal
import com.meteocompare.app.domain.util.forecastDailyWindDirection
import com.meteocompare.app.domain.util.dailyAtmosphericMetrics

import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat

import com.meteocompare.app.domain.model.UvForecast
import com.meteocompare.app.ui.components.UvIndexGauge

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.LocalWeatherUnits

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.StackedLineChart
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.data.worker.BiasHistoryRefreshState
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.CityDetailContentTab
import com.meteocompare.app.domain.model.CityDetailSection
import com.meteocompare.app.domain.model.CityDetailViewMode
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.ForecastDisplayHorizon
import com.meteocompare.app.domain.model.ForecastEngineContext
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.DayConditionsRow
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.ui.accessibility.A11yFormatter
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.HumidityMetricIcon
import com.meteocompare.app.ui.components.DewPointMetricIcon
import com.meteocompare.app.ui.components.AppToastEffect
import com.meteocompare.app.ui.components.AppToastEvent
import com.meteocompare.app.ui.components.CollapsibleSectionHeader
import com.meteocompare.app.ui.components.OfflineDataBanner
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.components.VigilanceDetailCard
import com.meteocompare.app.ui.components.rememberFormattedLastUpdated
import com.meteocompare.app.ui.theme.WeatherAccent
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.temperatureMinMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.map

// ============================================================================
//  Public screen entry
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityDetailScreen(
    onBack: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit = {},
    onEngineComparisonClick: () -> Unit = {},
    onGraphicViewClick: () -> Unit = {},
    onMeteogramClick: () -> Unit = {},
    onRadarClick: () -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: CityDetailViewModel = hiltViewModel()
) {
    // Seuls les états qui pilotent réellement le scaffold sont observés ici.
    // Les états de sections sont collectés plus bas, dans
    // CityDetailLoadedStateBridge, afin qu'une évolution marine/biais/etc. ne
    // recompose pas l'AppBar ni le conteneur Loading/Error.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIfStale()
    }

    val refreshToasts = remember(viewModel) {
        viewModel.refreshFeedback.map { feedback ->
            when (feedback) {
                RefreshFeedback.Success -> AppToastEvent.success(R.string.refresh_success)
                is RefreshFeedback.Error -> AppToastEvent.error(
                    R.string.refresh_error,
                    feedback.message
                )
                RefreshFeedback.MarineRefreshed -> AppToastEvent.success(R.string.marine_refreshed)
                RefreshFeedback.MarineNotCoastal -> AppToastEvent.warning(R.string.marine_not_coastal)
                is RefreshFeedback.MarineError -> AppToastEvent.error(
                    R.string.marine_error,
                    feedback.message
                )
                RefreshFeedback.SettingsSaveError ->
                    AppToastEvent.error(R.string.toast_settings_save_error)
                RefreshFeedback.BiasHistoryQueued ->
                    AppToastEvent.info(R.string.settings_bias_refresh_queued)
                RefreshFeedback.BiasHistoryError ->
                    AppToastEvent.error(R.string.toast_action_error)
            }
        }
    }
    AppToastEffect(refreshToasts)

    CityDetailScaffold(
        state = state,
        isRefreshing = isRefreshing,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onEngineComparisonClick = onEngineComparisonClick,
        onGraphicViewClick = onGraphicViewClick,
        onMeteogramClick = onMeteogramClick,
        onRadarClick = onRadarClick,
        showBackButton = showBackButton
    ) { loaded, padding ->
        CityDetailLoadedStateBridge(
            loaded = loaded,
            padding = padding,
            viewModel = viewModel,
            onConfidenceClick = onConfidenceClick
        )
    }
}

@Composable
private fun CityDetailLoadedStateBridge(
    loaded: CityDetailUiState.Loaded,
    padding: PaddingValues,
    viewModel: CityDetailViewModel,
    onConfidenceClick: (isoDate: String) -> Unit
) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val uvForecast by viewModel.uvForecast.collectAsStateWithLifecycle()
    val biasState by viewModel.biasState.collectAsStateWithLifecycle()
    val biasHistoryRefreshState by viewModel.biasHistoryRefreshState.collectAsStateWithLifecycle()
    val evolutionState by viewModel.evolutionState.collectAsStateWithLifecycle()
    val marineState by viewModel.marineState.collectAsStateWithLifecycle()
    val vigilanceState by viewModel.vigilanceState.collectAsStateWithLifecycle()
    val collapsedSections by viewModel.collapsedSections.collectAsStateWithLifecycle()
    val detailViewMode by viewModel.detailViewMode.collectAsStateWithLifecycle()
    val detailContentTab by viewModel.detailContentTab.collectAsStateWithLifecycle()

    LoadedView(
        forecast = loaded.forecast,
        weekly = loaded.weeklyConfidence,
        hourlyBands = loaded.hourlyBands,
        hourlyPrecipBands = loaded.hourlyPrecipBands,
        hourlyWindBands = loaded.hourlyWindBands,
        currentTemp = loaded.currentTemp,
        currentCondition = loaded.currentCondition,
        currentCloudCover = loaded.currentCloudCover,
        dailyConditions = loaded.dailyConditions,
        normals = loaded.normals,
        engineContext = loaded.engineContext,
        calculatedAt = loaded.calculatedAt,
        fetchedAt = loaded.fetchedAt,
        uvForecast = uvForecast,
        isOnline = isOnline,
        biasState = biasState,
        biasHistoryRefreshState = biasHistoryRefreshState,
        evolutionState = evolutionState,
        marineState = marineState,
        vigilance = (vigilanceState as? VigilanceUiState.Loaded)?.forecast
            ?.takeIf { loaded.forecast.city.isFrenchLocation },
        collapsedSections = collapsedSections,
        detailViewMode = detailViewMode,
        detailContentTab = detailContentTab,
        padding = padding,
        onSectionExpandedChange = viewModel::setSectionExpanded,
        onDetailViewModeChange = viewModel::setDetailViewMode,
        onDetailContentTabChange = viewModel::setDetailContentTab,
        onRefreshMarine = viewModel::refreshMarine,
        onRequestBiasHistory = viewModel::requestBiasHistory,
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
    biasHistoryRefreshState: BiasHistoryRefreshState = BiasHistoryRefreshState.IDLE,
    evolutionState: ForecastEvolutionState = ForecastEvolutionState.Idle,
    marineState: MarineUiState = MarineUiState.Idle,
    vigilanceState: VigilanceUiState = VigilanceUiState.Idle,
    collapsedSections: Set<CityDetailSection> = emptySet(),
    detailViewMode: CityDetailViewMode = CityDetailViewMode.DEFAULT,
    detailContentTab: CityDetailContentTab = CityDetailContentTab.DEFAULT,
    snackbarHostState: SnackbarHostState? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshMarine: () -> Unit = {},
    onSectionExpandedChange: (CityDetailSection, Boolean) -> Unit = { _, _ -> },
    onDetailViewModeChange: (CityDetailViewMode) -> Unit = {},
    onDetailContentTabChange: (CityDetailContentTab) -> Unit = {},
    onConfidenceClick: (isoDate: String) -> Unit = {},
    onEngineComparisonClick: () -> Unit = {},
    onGraphicViewClick: () -> Unit = {},
    onMeteogramClick: () -> Unit = {},
    onRadarClick: () -> Unit = {},
    onRequestBiasHistory: () -> Unit = {},
    showBackButton: Boolean = true
) {
    CityDetailScaffold(
        state = state,
        isRefreshing = isRefreshing,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = onRefresh,
        onEngineComparisonClick = onEngineComparisonClick,
        onGraphicViewClick = onGraphicViewClick,
        onMeteogramClick = onMeteogramClick,
        onRadarClick = onRadarClick,
        showBackButton = showBackButton
    ) { loaded, padding ->
        LoadedView(
            forecast = loaded.forecast,
            weekly = loaded.weeklyConfidence,
            hourlyBands = loaded.hourlyBands,
            hourlyPrecipBands = loaded.hourlyPrecipBands,
            hourlyWindBands = loaded.hourlyWindBands,
            currentTemp = loaded.currentTemp,
            currentCondition = loaded.currentCondition,
            currentCloudCover = loaded.currentCloudCover,
            dailyConditions = loaded.dailyConditions,
            normals = loaded.normals,
            engineContext = loaded.engineContext,
            calculatedAt = loaded.calculatedAt,
            fetchedAt = loaded.fetchedAt,
            isOnline = isOnline,
            biasState = biasState,
            biasHistoryRefreshState = biasHistoryRefreshState,
            evolutionState = evolutionState,
            marineState = marineState,
            vigilance = (vigilanceState as? VigilanceUiState.Loaded)?.forecast
                ?.takeIf { loaded.forecast.city.isFrenchLocation },
            collapsedSections = collapsedSections,
            detailViewMode = detailViewMode,
            detailContentTab = detailContentTab,
            padding = padding,
            onSectionExpandedChange = onSectionExpandedChange,
            onDetailViewModeChange = onDetailViewModeChange,
            onDetailContentTabChange = onDetailContentTabChange,
            onRefreshMarine = onRefreshMarine,
            onRequestBiasHistory = onRequestBiasHistory,
            onConfidenceClick = onConfidenceClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityDetailScaffold(
    state: CityDetailUiState,
    isRefreshing: Boolean,
    snackbarHostState: SnackbarHostState? = null,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEngineComparisonClick: () -> Unit,
    onGraphicViewClick: () -> Unit,
    onMeteogramClick: () -> Unit,
    onRadarClick: () -> Unit,
    showBackButton: Boolean,
    loadedContent: @Composable (CityDetailUiState.Loaded, PaddingValues) -> Unit
) {
    WeatherAccentTheme(
        condition = (state as? CityDetailUiState.Loaded)?.currentCondition
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                // Deux niveaux : l'identité ne partage plus sa largeur avec les
                // 3 raccourcis, quelle que soit la taille de l'écran / de la police.
                // Le Scaffold ne fournit pas les insets à une topBar personnalisée.
                // L'arrière-plan s'étend sous la barre système, mais les boutons
                // et le titre restent en dessous des icônes Android (edge-to-edge).
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        val loaded = state as? CityDetailUiState.Loaded
                        val city = loaded?.forecast?.city
                        val subtitle = city?.country.orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = if (showBackButton) 4.dp else 16.dp, end = 4.dp)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showBackButton) {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.nav_back)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.weight(1f).padding(start = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(if (subtitle.isNotBlank()) 38.dp else 28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = city?.name ?: stringResource(R.string.title_detail_fallback),
                                        modifier = Modifier.testTag(TAG_DETAIL_CITY_TITLE),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.action_refresh)
                                    )
                                }
                            }
                        }
                        if (loaded != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 1.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DetailHeaderAction(
                                    icon = Icons.Default.Radar,
                                    label = stringResource(R.string.detail_action_radar_short),
                                    description = stringResource(R.string.radar_open),
                                    tag = TAG_RADAR_ACTION,
                                    onClick = onRadarClick
                                )
                                DetailHeaderAction(
                                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                                    label = stringResource(R.string.detail_action_models_short),
                                    description = stringResource(R.string.engine_comparison_open),
                                    tag = TAG_ENGINE_COMPARISON_ACTION,
                                    onClick = onEngineComparisonClick
                                )
                                DetailHeaderAction(
                                    icon = Icons.AutoMirrored.Filled.ShowChart,
                                    label = stringResource(R.string.detail_action_charts_short),
                                    description = stringResource(R.string.graphic_view_open),
                                    tag = TAG_GRAPHIC_VIEW_ACTION,
                                    onClick = onGraphicViewClick
                                )
                                DetailHeaderAction(
                                    icon = Icons.Outlined.StackedLineChart,
                                    label = stringResource(R.string.detail_action_meteogram_short),
                                    description = stringResource(R.string.meteogram_title),
                                    tag = TAG_METEOGRAM_ACTION,
                                    onClick = onMeteogramClick
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            },
            snackbarHost = {
                if (snackbarHostState != null) SnackbarHost(snackbarHostState)
            }
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
            ) { current ->
                when (current) {
                    CityDetailUiState.Loading -> LoadingView(padding)
                    is CityDetailUiState.Error -> ErrorView(
                        message = current.message,
                        onRetry = onRefresh,
                        padding = padding
                    )
                    is CityDetailUiState.Loaded -> PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        loadedContent(current, padding)
                    }
                }
            }
        }
    }
}

/** Accès directs, avec libellé visible et zone tactile Material 3. */
@Composable
private fun RowScope.DetailHeaderAction(
    icon: ImageVector,
    label: String,
    description: String,
    tag: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).testTag(tag).semantics {
            contentDescription = description
        },
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    hasInsights: Boolean,
    hasVigilance: Boolean = false
): Int =
    (if (isOnline) 0 else 1) + // bannière offline
            1 + // résumé du jour
            (if (hasVigilance) 1 else 0) +
            (if (hasInsights) 1 else 0)

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
    engineContext: ForecastEngineContext,
    calculatedAt: Instant,
    fetchedAt: Instant?,
    uvForecast: UvForecast? = null,
    isOnline: Boolean,
    biasState: BiasScreenState,
    biasHistoryRefreshState: BiasHistoryRefreshState,
    evolutionState: ForecastEvolutionState,
    marineState: MarineUiState,
    vigilance: VigilanceForecast?,
    collapsedSections: Set<CityDetailSection>,
    detailViewMode: CityDetailViewMode,
    detailContentTab: CityDetailContentTab,
    padding: PaddingValues,
    onSectionExpandedChange: (CityDetailSection, Boolean) -> Unit,
    onDetailViewModeChange: (CityDetailViewMode) -> Unit,
    onDetailContentTabChange: (CityDetailContentTab) -> Unit,
    onRefreshMarine: () -> Unit,
    onConfidenceClick: (isoDate: String) -> Unit = {},
    onRequestBiasHistory: () -> Unit = {}
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
    val engineCacheSignature = engineContext.cacheSignature
    // Keep expensive consensus/event analysis independent of asynchronous UV arrival.
    val baseHourlyTimeline = remember(forecast, presentationNow, engineCacheSignature) {
        buildSimplifiedTimeline(
            forecast = forecast,
            mode = DisplayMode.HOURLY,
            now = presentationNow,
            engineContext = engineContext,
            hourlyHorizonHours = ForecastDisplayHorizon.HOURLY_HOURS
        )
    }
    val baseDailyTimeline = remember(forecast, presentationNow, engineCacheSignature) {
        buildSimplifiedTimeline(
            forecast = forecast,
            mode = DisplayMode.DAILY,
            now = presentationNow,
            engineContext = engineContext,
            dailyHorizonDays = ForecastDisplayHorizon.DAYS
        )
    }
    // Reuse the already computed hourly consensus; do not recompute it in TodaySummary.
    // Only display a value for the *current* hour, never a future first available hour.
    val currentApparentForSummary = remember(baseHourlyTimeline, presentationNow, forecast.city.timezone) {
        val currentHour = computeHourlyHorizon(forecast.city.timezone, presentationNow).first
        currentHourlyApparentTemperature(baseHourlyTimeline, currentHour)
    }
    val hourlyTimelinePoints = remember(baseHourlyTimeline, uvForecast) {
        baseHourlyTimeline.withUvIndex(uvForecast)
    }
    val dailyTimelinePoints = remember(baseDailyTimeline, uvForecast) {
        baseDailyTimeline.withUvIndex(uvForecast)
    }
    val overviewTimeline = remember(baseHourlyTimeline, baseDailyTimeline, forecast.city.timezone) {
        overviewFromTimelines(baseHourlyTimeline, baseDailyTimeline, forecast.city.timezone)
    }
    val forecastEvents = remember(overviewTimeline) { detectForecastEvents(overviewTimeline) }
    val insights = remember(forecastEvents) { buildForecastInsights(forecastEvents) }
    val evolutionHighlight = (evolutionState as? ForecastEvolutionState.Loaded)?.highlight
    val hasInsightSection = insights.isNotEmpty() || evolutionHighlight != null
    val timelineAvailableRanges = remember(hourlyTimelinePoints, dailyTimelinePoints) {
        buildSet {
            if (hourlyTimelinePoints.isNotEmpty()) add(TimelineRange.HOURLY)
            if (dailyTimelinePoints.isNotEmpty()) add(TimelineRange.DAILY)
        }
    }
    var timelineRange by remember(forecast.city.id) {
        mutableStateOf(TimelineRange.defaultFor(overviewTimeline.mode))
    }
    val timelineMode = timelineRange.displayMode
    var timelineLayoutName by rememberSaveable(forecast.city.id) {
        mutableStateOf(TimelineLayout.COLUMNS.name)
    }
    val timelineLayout = remember(timelineLayoutName) {
        TimelineLayout.entries.firstOrNull { it.name == timelineLayoutName } ?: TimelineLayout.COLUMNS
    }
    val timelineAnalysisPoints = when (timelineRange) {
        TimelineRange.HOURLY -> hourlyTimelinePoints
        TimelineRange.DAILY -> dailyTimelinePoints
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
    val timelineDisplayPoints = remember(timelineAnalysisPoints, timelineRange) {
        selectRegularTimelinePoints(
            points = timelineAnalysisPoints,
            maxPoints = timelineRange.maxDisplayPoints
        )
    }
    var focusedTimelinePoint by remember(overviewTimeline) {
        mutableStateOf<SimplifiedTimelinePoint?>(null)
    }
    var timelineFocusRequestId by remember(overviewTimeline) { mutableIntStateOf(0) }
    val contentListState = rememberLazyListState()
    // Items preceding the timeline: optional offline banner, today summary,
    // optional official vigilance, then optional insights. The engine comparison
    // action lives in the fixed two-level header and does not affect this index.
    val timelineItemIndex = simplifiedTimelineItemIndex(
        isOnline = isOnline,
        hasInsights = hasInsightSection,
        hasVigilance = vigilance?.activeAlerts?.isNotEmpty() == true
    )
    LaunchedEffect(timelineFocusRequestId) {
        if (timelineFocusRequestId > 0) {
            contentListState.animateScrollToItem(timelineItemIndex)
        }
    }
    val cityToday = remember(forecast.city.timezone, presentationNow) {
        cityLocalDate(forecast.city.timezone, presentationNow)
    }
    // Toutes les vues approfondies de la page détail partagent le même horizon
    // de 10 jours. Les séries horaires utilisent la fenêtre glissante commune.
    val detailedForecast = remember(forecast) {
        forecast.limitDailyHorizon(ForecastDisplayHorizon.DAYS)
    }
    val detailedDailyConditions = remember(dailyConditions) {
        dailyConditions.take(ForecastDisplayHorizon.DAYS)
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
        val model = enumValueOrNull<WeatherModel>(selectedModelName)
            ?: return@remember null
        val variable = enumValueOrNull<BiasVariable>(selectedVariableName)
            ?: return@remember null

        val varState = when (variable) {
            BiasVariable.TEMPERATURE -> biasState.temperature
            BiasVariable.PRECIPITATION -> biasState.precipitation
            BiasVariable.WIND_SPEED -> biasState.wind
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
            start = 8.dp,
            // Respiration sous l'en-tête fixe : la première carte ne doit pas
            // toucher visuellement la barre d'actions, y compris en edge-to-edge.
            top = padding.calculateTopPadding() + 12.dp,
            end = 8.dp,
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
                    forecast = forecast,
                    now = presentationNow,
                    currentTemp = currentTemp,
                    currentApparentTemp = currentApparentForSummary,
                    currentCondition = currentCondition,
                    currentCloudCover = currentCloudCover,
                    uvIndex = uvForecast?.at(presentationNow),
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

        vigilance?.takeIf { forecast.city.isFrenchLocation && it.activeAlerts.isNotEmpty() }?.let { alertForecast ->
            item("official_vigilance") {
                VigilanceDetailCard(
                    vigilance = alertForecast,
                    timezone = forecast.city.timezone,
                    modifier = Modifier.padding(horizontal = 0.dp)
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
                            onSectionExpandedChange(CityDetailSection.TIMELINE, true)
                            when {
                                target.instant != null && TimelineRange.HOURLY in timelineAvailableRanges ->
                                    timelineRange = TimelineRange.HOURLY
                                target.date != null && TimelineRange.DAILY in timelineAvailableRanges ->
                                    timelineRange = TimelineRange.DAILY
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
                    range = timelineRange,
                    onRangeChange = { newRange ->
                        timelineRange = newRange
                        focusedTimelinePoint = null
                    },
                    availableRanges = timelineAvailableRanges,
                    layout = timelineLayout,
                    onLayoutChange = { newLayout ->
                        timelineLayoutName = newLayout.name
                    },
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

        // Avancement de la collecte pour la variable de l'onglet affiché : les
        // pastilles « N/14 » et le bandeau parlent ainsi de la même chose.
        // L'onglet Conditions n'a pas d'indicateur par modèle, donc pas de bandeau.
        val biasHistoryProgress = when (detailContentTab) {
            CityDetailContentTab.TEMPERATURE -> biasState.temperature
            CityDetailContentTab.PRECIPITATION -> biasState.precipitation
            CityDetailContentTab.WIND -> biasState.wind
            CityDetailContentTab.CONDITIONS -> null
        }
            ?.historyProgress(forecast.availableModels)
            ?.takeIf { it.shouldShowBanner }

        item("detailed_forecast_section") {
            DetailedForecastSection(
                mode = displayMode,
                tab = detailContentTab,
                forecast = detailedForecast,
                dailyConditions = detailedDailyConditions,
                normals = normals,
                presentationNow = presentationNow,
                cityToday = cityToday,
                biasHistoryProgress = biasHistoryProgress,
                biasHistoryRefreshState = biasHistoryRefreshState,
                onRequestBiasHistory = onRequestBiasHistory,
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
                    coastalVigilance = vigilance?.coastalFloodingAlert,
                    vigilanceTimezone = forecast.city.timezone,
                    onExpandedChange = { expanded ->
                        onSectionExpandedChange(CityDetailSection.MARINE, expanded)
                    }
                )
            }
        }

        item("open_meteo_attribution") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                OpenMeteoAttribution(home = false)
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

private fun CityForecast.limitDailyHorizon(days: Int): CityForecast {
    val limit = days.coerceAtLeast(1)
    return copy(
        seriesByModel = seriesByModel.mapValues { (_, series) ->
            val daily = series.daily
            series.copy(
                daily = daily.copy(
                    dates = daily.dates.take(limit),
                    tempMax = daily.tempMax.take(limit),
                    tempMin = daily.tempMin.take(limit),
                    precipitationSum = daily.precipitationSum.take(limit),
                    windSpeedMax = daily.windSpeedMax.take(limit),
                    weatherCode = daily.weatherCode.take(limit),
                    windDirection10mDominant = daily.windDirection10mDominant.take(limit),
                    precipitationProbabilityMax = daily.precipitationProbabilityMax.take(limit),
                    windGustsMax = daily.windGustsMax.take(limit),
                    sunrise = daily.sunrise.take(limit),
                    sunset = daily.sunset.take(limit)
                )
            )
        }
    )
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
    cityToday: LocalDate,
    biasHistoryProgress: BiasHistoryProgress?,
    biasHistoryRefreshState: BiasHistoryRefreshState,
    onRequestBiasHistory: () -> Unit,
    onModeChange: (DisplayMode) -> Unit,
    onTabChange: (CityDetailContentTab) -> Unit,
    temperatureBiasProvider: ((WeatherModel) -> ModelBias?)? = null,
    precipitationBiasProvider: ((WeatherModel) -> ModelBias?)? = null,
    windBiasProvider: ((WeatherModel) -> ModelBias?)? = null,
    temperatureSampleCountProvider: ((WeatherModel) -> Int)? = null,
    precipitationSampleCountProvider: ((WeatherModel) -> Int)? = null,
    windSampleCountProvider: ((WeatherModel) -> Int)? = null,
    onBiasChipClick: ((WeatherModel, ModelBias) -> Unit)? = null,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        shape = RoundedCornerShape(16.dp),
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

                if (biasHistoryProgress != null) {
                    BiasHistoryHint(
                        progress = biasHistoryProgress,
                        refreshState = biasHistoryRefreshState,
                        onRequestHistory = onRequestBiasHistory,
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
internal fun DetailedComparisonContent(
    mode: DisplayMode,
    tab: CityDetailContentTab,
    forecast: CityForecast,
    dailyConditions: List<DayConditionsRow>,
    normals: Map<Int, DayNormals>?,
    presentationNow: Instant,
    cityToday: LocalDate,
    temperatureBiasProvider: ((WeatherModel) -> ModelBias?)? = null,
    precipitationBiasProvider: ((WeatherModel) -> ModelBias?)? = null,
    windBiasProvider: ((WeatherModel) -> ModelBias?)? = null,
    temperatureSampleCountProvider: ((WeatherModel) -> Int)? = null,
    precipitationSampleCountProvider: ((WeatherModel) -> Int)? = null,
    windSampleCountProvider: ((WeatherModel) -> Int)? = null,
    onBiasChipClick: ((WeatherModel, ModelBias) -> Unit)? = null,
    units: WeatherUnits = LocalWeatherUnits.current
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
                    val apparentPrefix = stringResource(R.string.apparent_temperature_widget_prefix)
                    DetailTableCard {
                        HourlyForecastTable(
                            forecast = forecast,
                            now = presentationNow,
                            valueExtractor = { hourly: HourlyForecast, idx ->
                                hourly.temperature2m.getOrNull(idx)
                            },
                            valueFormatter = { units.temp(it) },
                            // One cell per model/hour: air temperature above feels-like.
                            // Reuses the existing hourly series, without a second table.
                            secondaryValueExtractor = { hourly: HourlyForecast, idx ->
                                hourly.apparentTemperature.getOrNull(idx)?.takeIf(Double::isFinite)
                            },
                            secondaryValueFormatter = { apparent ->
                                "$apparentPrefix ${units.temp(apparent)}"
                            },
                            heatmapStyler = ::hourlyTemperatureHeatmap,
                            modelBiasProvider = temperatureBiasProvider,
                            sampleCountProvider = temperatureSampleCountProvider,
                            onBiasChipClick = onBiasChipClick,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    HourlyTemperatureLegend()
                    if (forecast.seriesByModel.values.any { series ->
                            series.hourly.relativeHumidity2m.any { it != null } ||
                                series.hourly.dewPoint2m.any { it?.isFinite() == true }
                        }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(
                                start = 14.dp, end = 14.dp, top = 12.dp, bottom = 5.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(HumidityMetricIcon, contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(17.dp))
                            Text(stringResource(R.string.metric_humidity),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(DewPointMetricIcon, contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(17.dp))
                            Text(stringResource(R.string.metric_dew_point),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DetailTableCard {
                            HourlyForecastTable(
                                forecast = forecast,
                                now = presentationNow,
                                valueExtractor = { hourly, idx ->
                                    hourly.relativeHumidity2m.getOrNull(idx)?.toDouble()
                                },
                                valueFormatter = { "${units.number(it)} %" },
                                secondaryValueExtractor = { hourly, idx ->
                                    hourly.dewPoint2m.getOrNull(idx)
                                },
                                secondaryValueFormatter = { units.temp(it) },
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    // Full definition remains available in detailed forecasts, not in Home cards.
                    if (forecast.seriesByModel.values.any { series ->
                            series.hourly.apparentTemperature.any { it?.isFinite() == true }
                        }) {
                        Text(
                            text = stringResource(R.string.apparent_temperature_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            CityDetailContentTab.PRECIPITATION -> {
                if (mode == DisplayMode.DAILY) {
                    ForecastTableContent(
                        forecast = forecast,
                        now = presentationNow,
                        extractor = { daily, idx -> daily.precipitationSum.getOrNull(idx) },
                        formatter = { mm ->
                            units.rain(mm)
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
                                units.rain(mm)
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
                        formatter = { units.speed(it) },
                        valueStyler = ::windStyle,
                        secondaryExtractor = { daily, idx -> daily.windGustsMax.getOrNull(idx) },
                        secondaryFormatter = { "$gustAbbreviation ${units.value(it, WeatherUnit.WIND_SPEED)}" },
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
                            valueFormatter = { units.speed(it) },
                            heatmapStyler = ::hourlyWindHeatmap,
                            secondaryValueExtractor = { hourly, idx ->
                                hourly.windGusts10m.getOrNull(idx)
                            },
                            secondaryValueFormatter = { "$gustAbbreviation ${units.value(it, WeatherUnit.WIND_SPEED)}" },
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
    modelBiasProvider: ((WeatherModel) -> ModelBias?)? = null,
    onBiasChipClick: ((WeatherModel, ModelBias) -> Unit)? = null,
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
private fun HourlyTemperatureLegend(units: WeatherUnits = LocalWeatherUnits.current) {
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
            "<${units.value(-10.0, WeatherUnit.TEMPERATURE_COMPACT)}", *listOf(-10.0, -5.0, 0.0, 5.0, 10.0, 15.0, 20.0, 25.0).map { units.value(it, WeatherUnit.TEMPERATURE_COMPACT) }.toTypedArray(), "≥${units.value(30.0, WeatherUnit.TEMPERATURE_COMPACT)}"
        ),
        unitLabel = units.temperatureUnit
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
private fun HourlyPrecipitationLegend(units: WeatherUnits = LocalWeatherUnits.current) {
    HeatmapGradientLegend(
        colors = listOf(
            Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9),
            Color(0xFF64B5F6), Color(0xFF42A5F5), Color(0xFF2196F3),
            Color(0xFF1E88E5), Color(0xFF1976D2), Color(0xFF1565C0),
            Color(0xFF0D47A1)
        ),
        tickLabels = if (units.imperial) {
            listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 3.0, 5.0, 7.0).map {
                units.value(it, WeatherUnit.PRECIPITATION, 3).removePrefix("0")
            } + "≥${units.value(10.0, WeatherUnit.PRECIPITATION, 3).removePrefix("0")}"
        } else listOf(".05", ".1", ".2", ".5", "1", "2", "3", "5", "7", "≥10"),
        unitLabel = units.precipitationUnit
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
private fun HourlyWindLegend(units: WeatherUnits = LocalWeatherUnits.current) {
    Column {
        HeatmapGradientLegend(
            colors = listOf(
                Color(0xFFFFF9C4), Color(0xFFFFF176), Color(0xFFFFEB3B),
                Color(0xFFFFCA28), Color(0xFFFFB74D), Color(0xFFFF9800),
                Color(0xFFFB8C00), Color(0xFFF57C00), Color(0xFFE64A19),
                Color(0xFFC62828)
            ),
            tickLabels = listOf(
                *listOf(20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0).map { units.value(it, WeatherUnit.WIND_SPEED) }.toTypedArray(), "≥${units.value(120.0, WeatherUnit.WIND_SPEED)}"
            ),
            unitLabel = units.windUnit
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
    tickLabels: List<String>,
    unitLabel: String? = null
) {
    require(colors.size == tickLabels.size) {
        "colors and tickLabels must have same size (got ${colors.size} vs ${tickLabels.size})"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (unitLabel != null) {
            Text(unitLabel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
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
                    textAlign = TextAlign.Center,
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
    currentApparentTemp: Double? = null,
    currentCondition: WeatherCondition? = null,
    currentCloudCover: Int? = null,
    uvIndex: Double? = null,
    fetchedAt: Instant? = null,
    isOnline: Boolean = true,
    expanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    onConfidenceClick: () -> Unit = {},
    forecast: CityForecast? = null,
    units: WeatherUnits = LocalWeatherUnits.current,
    now: Instant = Instant.now()
) {
    val solarWindow = remember(forecast, today.date) {
        forecast?.let { detailSolarWindow(it, today.date) }
    }
    val solarZone = remember(forecast?.city?.timezone) {
        resolveCityZone(forecast?.city?.timezone)
    }
    // Même décision jour/nuit que les widgets, y compris si la date change.
    val currentIsNight = remember(forecast, now) {
        forecast?.let { com.meteocompare.app.domain.util.forecastIsNightAt(it, now) } == true
    }
    val resources = LocalResources.current
    val baseDescription = A11yFormatter
        .todaySummaryDescription(resources, today, modelCount, units = units)
    val validApparent = currentApparentTemp?.takeIf(Double::isFinite)
    val moistureHour = remember(forecast, now) {
        val hour = computeHourlyHorizon(forecast?.city?.timezone, now).first
        val series = forecast?.seriesByModel.orEmpty()
        val humidity = series.mapNotNull { (model, values) ->
            val index = values.hourly.timestamps.indexOf(hour)
            values.hourly.relativeHumidity2m.getOrNull(index)?.let { ForecastConsensus.Entry(model, it.toDouble()) }
        }
        val dewPoint = series.mapNotNull { (model, values) ->
            val index = values.hourly.timestamps.indexOf(hour)
            values.hourly.dewPoint2m.getOrNull(index)?.let { ForecastConsensus.Entry(model, it) }
        }
        ForecastConsensus.continuous(humidity, tightStdDev = 5.0, wideStdDev = 25.0).central
            ?.roundToInt()?.coerceIn(0, 100) to
            ForecastConsensus.continuous(dewPoint, tightStdDev = 0.5, wideStdDev = 3.0).central
    }
    val moistureModelValues = remember(forecast, now) {
        val hour = computeHourlyHorizon(forecast?.city?.timezone, now).first
        val series = forecast?.seriesByModel.orEmpty()
        val humidity = series.mapNotNull { (model, values) ->
            val index = values.hourly.timestamps.indexOf(hour)
            values.hourly.relativeHumidity2m.getOrNull(index)?.let { model.displayName to it }
        }.sortedBy { it.first }
        val dewPoint = series.mapNotNull { (model, values) ->
            val index = values.hourly.timestamps.indexOf(hour)
            values.hourly.dewPoint2m.getOrNull(index)?.let { model.displayName to it }
        }.sortedBy { it.first }
        humidity to dewPoint
    }
    val temperatureDescription = if (currentTemp != null) {
        com.meteocompare.app.core.units.weatherString(resources, units, R.string.a11y_now_temp, currentTemp) + ". $baseDescription"
    } else baseDescription
    val uvDescription = uvIndex?.takeIf { it.isFinite() && it >= 0.0 }?.let {
        val uvValue = resources.getString(R.string.uv_index_value, it)
        val riskRes = com.meteocompare.app.ui.components.uvRiskLabelRes(it)
        resources.getString(R.string.uv_accessibility, uvValue, resources.getString(riskRes))
    }
    val solarDescription = if (expanded && solarWindow != null) {
        val formatter = WeatherTimeFormat.clock(LocalConfiguration.current.locales[0], LocalClockFormatPreference.current)
        stringResource(
            R.string.sun_times_a11y,
            solarWindow.sunrise?.atZone(solarZone)?.toLocalTime()?.format(formatter) ?: "—",
            solarWindow.sunset?.atZone(solarZone)?.toLocalTime()?.format(formatter) ?: "—"
        )
    } else null
    val a11yDescription = temperatureDescription +
        (validApparent?.let { ". " + resources.getString(R.string.apparent_temperature_summary, units.temp(it)) } ?: "") +
        (uvDescription?.let { ". $it" } ?: "") +
        (solarDescription?.let { ". $it" } ?: "")
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val weatherAccent = WeatherAccent.of(currentCondition, isDark)
    val dateLocale = LocalConfiguration.current.locales[0]
    // Préparer la direction ici, où la prévision et la locale sont disponibles.
    // La grille reçoit un libellé prêt à afficher et ne recalcule rien.
    val windDirectionLabel = remember(forecast, today.date, dateLocale) {
        windCardinal(forecast?.let { forecastDailyWindDirection(it, today.date) }, dateLocale)
    }
    val longDateFmt = remember(dateLocale) {
        DateTimeFormatter.ofPattern("EEEE d MMMM", dateLocale)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
            }
    ) {
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
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentCondition != null) {
                                AnimatedWeatherIcon(
                                    condition = currentCondition,
                                    size = 56.dp,
                                    animated = true,
                                    motionScale = 2.0f,
                                    tint = Color.Unspecified,
                                    isNight = currentIsNight
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentTemp?.let { units.temp(it) } ?: "—",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                validApparent?.let { apparent ->
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.apparent_temperature_summary, units.temp(apparent)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).testTag(TAG_TODAY_SUMMARY_APPARENT)
                                    )
                                }
                            }
                            currentCondition?.let { condition ->
                                Text(
                                    text = stringResource(weatherConditionLabelRes(condition)),
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

                    if (moistureHour.first != null || moistureHour.second != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MoistureTodayValue(
                                label = stringResource(R.string.metric_humidity),
                                icon = HumidityMetricIcon,
                                accent = MaterialTheme.colorScheme.secondary,
                                value = moistureHour.first?.let { "$it %" } ?: "—",
                                modelValues = moistureModelValues.first.map { (model, humidity) -> model to "$humidity %" },
                                modifier = Modifier.weight(1f).testTag("today_summary_humidity")
                            )
                            MoistureTodayValue(
                                label = stringResource(R.string.metric_dew_point),
                                icon = DewPointMetricIcon,
                                accent = MaterialTheme.colorScheme.tertiary,
                                value = moistureHour.second?.let { units.temp(it) } ?: "—",
                                modelValues = moistureModelValues.second.map { (model, dewPoint) -> model to units.temp(dewPoint) },
                                modifier = Modifier.weight(1f).testTag("today_summary_dew_point")
                            )
                        }
                    }

                    if (solarWindow != null) {
                        Spacer(Modifier.height(8.dp))
                        DetailSolarCard(
                            window = solarWindow,
                            timezone = solarZone,
                            date = today.date,
                            now = now,
                            uvIndex = uvIndex
                        )
                        if (today.tempMin != null || today.tempMax != null ||
                            today.precipitation != null || today.windMax != null ||
                            today.windGustMax != null) {
                            Spacer(Modifier.height(6.dp))
                            SummarySectionDivider()
                            Spacer(Modifier.height(6.dp))
                        }
                    } else if (uvIndex != null && uvIndex.isFinite() && uvIndex >= 0.0) {
                        // Sans données solaires (prévisions absentes), conserver
                        // l'indice UV accessible au lieu de le masquer.
                        Spacer(Modifier.height(10.dp))
                        UvIndexGauge(value = uvIndex, modifier = Modifier.fillMaxWidth())
                    }
                    DetailMetricGrid(
                        today = today,
                        samples = remember(forecast, today.date) {
                            buildTodaySummaryDispersionSamples(forecast, today.date)
                        },
                        windDirectionLabel = windDirectionLabel
                    )
                }
            }
        }
    }
}

@Composable
private fun MoistureTodayValue(
    label: String,
    icon: ImageVector,
    accent: Color,
    value: String,
    modelValues: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    var detailsOpen by remember { mutableStateOf(false) }
    Surface(
        onClick = { detailsOpen = true },
        enabled = modelValues.isNotEmpty(),
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = accent.copy(alpha = 0.13f), shape = RoundedCornerShape(9.dp)) {
                Icon(
                    imageVector = icon, contentDescription = null, tint = accent,
                    modifier = Modifier.padding(7.dp).size(19.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
    if (detailsOpen) {
        AlertDialog(
            onDismissRequest = { detailsOpen = false },
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.metric_summary_average),
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text(value, fontWeight = FontWeight.Bold)
                        }
                    }
                    modelValues.forEach { (model, modelValue) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(model, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f))
                                Text(modelValue, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsOpen = false }) {
                    Text(stringResource(R.string.toast_dismiss))
                }
            }
        )
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
                rememberFormattedLastUpdated(fetchedAt)
            } else {
                stringResource(
                    R.string.offline_saved_data_age_inline,
                    rememberFormattedLastUpdated(fetchedAt)
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
                    shape = CircleShape
                )
        )
    }
}

private data class DispersionSample(
    val model: WeatherModel,
    val value: Double
)

private data class TodaySummaryDispersionSamples(
    val tempMin: List<DispersionSample> = emptyList(),
    val tempMax: List<DispersionSample> = emptyList(),
    val apparentMin: List<DispersionSample> = emptyList(),
    val apparentMax: List<DispersionSample> = emptyList(),
    val precipitation: List<DispersionSample> = emptyList(),
    val wind: List<DispersionSample> = emptyList(),
    val windGust: List<DispersionSample> = emptyList()
)

/**
 * Valeurs journalières brutes utilisées uniquement pour dessiner les frises.
 *
 * Le consensus central reste celui de [ConfidenceScore] / [PrecipitationConfidence]
 * (médiane pondérée et équilibrée par famille). Les points, eux, représentent
 * volontairement chaque modèle disponible comme dans la version web : la frise
 * montre la dispersion brute sans transformer plusieurs modèles apparentés en
 * plusieurs votes dans le calcul central.
 */
private fun buildTodaySummaryDispersionSamples(
    forecast: CityForecast?,
    date: LocalDate
): TodaySummaryDispersionSamples {
    if (forecast == null) return TodaySummaryDispersionSamples()

    val tempMin = mutableListOf<DispersionSample>()
    val tempMax = mutableListOf<DispersionSample>()
    val apparentMin = mutableListOf<DispersionSample>()
    val apparentMax = mutableListOf<DispersionSample>()
    val zone = resolveCityZone(forecast.city.timezone)
    val precipitation = mutableListOf<DispersionSample>()
    val wind = mutableListOf<DispersionSample>()
    val windGust = mutableListOf<DispersionSample>()

    forecast.seriesByModel.forEach { (model, series) ->
        val index = series.daily.dates.indexOf(date)
        if (index < 0) return@forEach
        val atmospheric = series.dailyAtmosphericMetrics(date, zone)
        atmospheric.apparentMinC?.let { apparentMin += DispersionSample(model, it) }
        atmospheric.apparentMaxC?.let { apparentMax += DispersionSample(model, it) }
        series.daily.tempMin.getOrNull(index)?.takeIf { it.isFinite() }?.let {
            tempMin += DispersionSample(model, it)
        }
        series.daily.tempMax.getOrNull(index)?.takeIf { it.isFinite() }?.let {
            tempMax += DispersionSample(model, it)
        }
        series.daily.precipitationSum.getOrNull(index)?.takeIf { it.isFinite() }?.let {
            precipitation += DispersionSample(model, it.coerceAtLeast(0.0))
        }
        series.daily.windSpeedMax.getOrNull(index)?.takeIf { it.isFinite() }?.let {
            wind += DispersionSample(model, it.coerceAtLeast(0.0))
        }
        series.daily.windGustsMax.getOrNull(index)?.takeIf { it.isFinite() }?.let {
            windGust += DispersionSample(model, it.coerceAtLeast(0.0))
        }
    }

    return TodaySummaryDispersionSamples(
        tempMin = tempMin,
        tempMax = tempMax,
        apparentMin = apparentMin,
        apparentMax = apparentMax,
        precipitation = precipitation,
        wind = wind,
        windGust = windGust
    )
}

private data class ApparentDispersion(val central: Double, val min: Double, val max: Double, val convergence: Int?)

/** A thin coloured feels-like range, overlaid on the corresponding air-temperature rail. */
private data class InlineApparentBar(
    val dispersion: ApparentDispersion,
    val label: String,
    val semanticLabel: String,
    val accent: Color,
    val testTag: String
)

private fun apparentDispersion(samples: List<DispersionSample>): ApparentDispersion? {
    val consensus = ForecastConsensus.continuous(
        samples.map { ForecastConsensus.Entry(it.model, it.value) },
        tightStdDev = 0.5,
        wideStdDev = 3.0
    )
    return consensus.central?.let { central ->
        ApparentDispersion(central, samples.minOf { it.value }, samples.maxOf { it.value }, consensus.convergencePercent)
    }
}

@Composable
private fun DetailMetricGrid(
    today: DayConfidence,
    samples: TodaySummaryDispersionSamples,
    windDirectionLabel: String?
) {
    val apparentMin = remember(samples.apparentMin) { apparentDispersion(samples.apparentMin) }
    val apparentMax = remember(samples.apparentMax) { apparentDispersion(samples.apparentMax) }
    val hasAnyMetric = apparentMin != null || apparentMax != null || today.tempMin != null || today.tempMax != null ||
        today.precipitation != null || today.windMax != null || today.windGustMax != null
    if (!hasAnyMetric) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (today.tempMin != null || today.tempMax != null || apparentMin != null || apparentMax != null) {
            // One °C domain for all four bands: air Min/Max and feels-like Min/Max.
            val tempDomain = compactTemperatureDispersionDomain(today, samples, apparentMin, apparentMax)
            SummaryMetricSection(tag = TAG_TODAY_SUMMARY_TEMP_PANEL) {
                SummaryMetricGroupHeader(
                    title = stringResource(R.string.metric_temperature),
                    icon = Icons.Outlined.Thermostat,
                    accent = temperatureMetricAccent()
                )
                today.tempMin?.let { score ->
                    CompactSummaryMetricRow(
                        semanticLabel = stringResource(R.string.var_temp_min),
                        shortLabel = stringResource(R.string.today_summary_temp_min_short),
                        score = score,
                        samples = samples.tempMin,
                        unit = WeatherUnit.TEMPERATURE_COMPACT,
                        digits = 1,
                        accent = temperatureMinMetricAccent(),
                        domain = tempDomain,
                        centralTestTag = TAG_TODAY_SUMMARY_TEMP_MIN_CENTRAL,
                        convergenceTestTag = TAG_TODAY_SUMMARY_TEMP_MIN_CONVERGENCE,
                        apparent = apparentMin?.let {
                            InlineApparentBar(
                                dispersion = it,
                                label = stringResource(R.string.today_apparent_min_short),
                                semanticLabel = stringResource(R.string.today_apparent_min_accessibility),
                                accent = temperatureMinMetricAccent(),
                                testTag = "today_apparent_min_central"
                            )
                        },
                        apparentSamples = samples.apparentMin
                    )
                } ?: apparentMin?.let { felt ->
                    CompactApparentOnlyRow(
                        label = stringResource(R.string.today_apparent_min_short),
                        semanticLabel = stringResource(R.string.today_apparent_min_accessibility),
                        apparent = felt,
                        samples = samples.apparentMin,
                        accent = temperatureMinMetricAccent(),
                        domain = tempDomain,
                        testTag = "today_apparent_min_central"
                    )
                }
                if ((today.tempMin != null || apparentMin != null) &&
                    (today.tempMax != null || apparentMax != null)) Spacer(Modifier.height(5.dp))
                today.tempMax?.let { score ->
                    CompactSummaryMetricRow(
                        semanticLabel = stringResource(R.string.var_temp_max),
                        shortLabel = stringResource(R.string.today_summary_temp_max_short),
                        score = score,
                        samples = samples.tempMax,
                        unit = WeatherUnit.TEMPERATURE_COMPACT,
                        digits = 1,
                        accent = temperatureMetricAccent(),
                        domain = tempDomain,
                        centralTestTag = TAG_TODAY_SUMMARY_TEMP_MAX_CENTRAL,
                        convergenceTestTag = TAG_TODAY_SUMMARY_TEMP_MAX_CONVERGENCE,
                        apparent = apparentMax?.let {
                            InlineApparentBar(
                                dispersion = it,
                                label = stringResource(R.string.today_apparent_max_short),
                                semanticLabel = stringResource(R.string.today_apparent_max_accessibility),
                                accent = temperatureMetricAccent(),
                                testTag = "today_apparent_max_central"
                            )
                        },
                        apparentSamples = samples.apparentMax
                    )
                } ?: apparentMax?.let { felt ->
                    CompactApparentOnlyRow(
                        label = stringResource(R.string.today_apparent_max_short),
                        semanticLabel = stringResource(R.string.today_apparent_max_accessibility),
                        apparent = felt,
                        samples = samples.apparentMax,
                        accent = temperatureMetricAccent(),
                        domain = tempDomain,
                        testTag = "today_apparent_max_central"
                    )
                }
            }
        }

        today.precipitation?.let { precipitation ->
            if (today.tempMin != null || today.tempMax != null || apparentMin != null || apparentMax != null) {
                SummarySectionDivider()
            }
            val rain = precipitationDispersionPresentation(precipitation)
            val rainHeaderDetail = rain.probabilityPercent?.let { probability ->
                stringResource(R.string.metric_precip_probability_only, probability)
            }

            SummaryMetricSection(tag = TAG_TODAY_SUMMARY_PRECIP_PANEL) {
                SummaryMetricGroupHeader(
                    title = stringResource(R.string.var_precipitation),
                    icon = Icons.Outlined.WaterDrop,
                    accent = precipitationMetricAccent(),
                    trailing = rainHeaderDetail
                )
                DispersionMetricRow(
                    semanticLabel = stringResource(R.string.var_precipitation),
                    subLabel = stringResource(R.string.metric_summary_average),
                    accent = precipitationMetricAccent(),
                    central = rain.central,
                    fallbackMin = rain.min,
                    fallbackMax = rain.max,
                    samples = samples.precipitation,
                    unit = WeatherUnit.PRECIPITATION,
                    digits = 1,
                    // The rain probability and model split already explain uncertainty.
                    nonNegative = true,
                    centralTestTag = TAG_TODAY_SUMMARY_PRECIP_CENTRAL
                )
                RainModelSummary(
                    presentation = rain,
                    accent = precipitationMetricAccent()
                )
            }
        }

        if (today.windMax != null || today.windGustMax != null) {
            if (today.tempMin != null || today.tempMax != null || apparentMin != null || apparentMax != null || today.precipitation != null) {
                SummarySectionDivider()
            }
            val isGustOnly = today.windMax == null
            val sharedDomain = windDispersionDomain(today, samples)
            SummaryMetricSection(tag = TAG_TODAY_SUMMARY_WIND_PANEL) {
                SummaryMetricGroupHeader(
                    title = if (isGustOnly) stringResource(R.string.metric_detail_gusts)
                        else stringResource(R.string.metric_detail_wind) + " / " + stringResource(R.string.metric_detail_gusts),
                    icon = Icons.Outlined.Air,
                    accent = windMetricAccent(),
                    trailing = windDirectionLabel
                )
                today.windMax?.let { score ->
                    CompactSummaryMetricRow(
                        semanticLabel = stringResource(R.string.metric_detail_wind),
                        shortLabel = stringResource(R.string.metric_detail_wind),
                        score = score,
                        samples = samples.wind,
                        unit = WeatherUnit.WIND_SPEED,
                        digits = 0,
                        accent = windMetricAccent(),
                        domain = sharedDomain,
                        centralTestTag = TAG_TODAY_SUMMARY_WIND_CENTRAL,
                        convergenceTestTag = TAG_TODAY_SUMMARY_WIND_CONVERGENCE
                    )
                }
                if (today.windMax != null && today.windGustMax != null) Spacer(Modifier.height(5.dp))
                today.windGustMax?.let { score ->
                    CompactSummaryMetricRow(
                        semanticLabel = stringResource(R.string.metric_detail_gusts),
                        shortLabel = stringResource(R.string.metric_detail_gusts),
                        score = score,
                        samples = samples.windGust,
                        unit = WeatherUnit.WIND_SPEED,
                        digits = 0,
                        accent = windMetricAccent(),
                        domain = sharedDomain,
                        centralTestTag = if (isGustOnly) TAG_TODAY_SUMMARY_WIND_CENTRAL else TAG_TODAY_SUMMARY_GUST_CENTRAL,
                        convergenceTestTag = if (isGustOnly) TAG_TODAY_SUMMARY_WIND_CONVERGENCE else TAG_TODAY_SUMMARY_GUST_CONVERGENCE
                    )
                }
            }
        }
    }
}

/** Séparation raccourcie et plus lisible, commune à tous les groupes du résumé. */
@Composable
internal fun SummarySectionDivider() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .testTag(TAG_TODAY_SUMMARY_SECTION_DIVIDER),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.90f),
            thickness = 1.dp
        )
    }
}

/** Groupes intégrés au fond de TodaySummary : pas de tuiles imbriquées. */
@Composable
internal fun SummaryMetricSection(
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
            .padding(horizontal = 3.dp, vertical = 3.dp)
    ) {
        content()
    }
}

@Composable
internal fun SummaryMetricGroupHeader(
    title: String,
    icon: ImageVector,
    accent: Color,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(9.dp)) {
            Box(modifier = Modifier.size(27.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.let { detail ->
            Spacer(Modifier.width(6.dp))
            Surface(color = accent.copy(alpha = 0.10f), shape = CircleShape) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

private data class PrecipitationDispersionPresentation(
    val central: Double,
    val min: Double,
    val max: Double,
    val probabilityPercent: Int?,
    val conditionalAmount: Double?,
    val wetModelCount: Int,
    val modelCount: Int
)

private fun precipitationDispersionPresentation(
    precipitation: PrecipitationConfidence
): PrecipitationDispersionPresentation = when (precipitation) {
    is PrecipitationConfidence.NoRain -> PrecipitationDispersionPresentation(
        central = precipitation.meta.centralAmountMm ?: 0.0,
        min = 0.0,
        max = precipitation.maxAmountMm.coerceAtLeast(0.0),
        probabilityPercent = precipitation.meta.probabilityPercent ?: 0,
        conditionalAmount = precipitation.meta.conditionalAmountMm,
        wetModelCount = 0,
        modelCount = precipitation.modelCount
    )
    is PrecipitationConfidence.Rain -> PrecipitationDispersionPresentation(
        central = precipitation.meta.centralAmountMm
            ?: precipitation.meta.conditionalAmountMm
            ?: precipitation.meanMm,
        min = precipitation.minMm.coerceAtLeast(0.0),
        max = precipitation.maxMm.coerceAtLeast(0.0),
        probabilityPercent = precipitation.meta.probabilityPercent ?: 100,
        conditionalAmount = precipitation.meta.conditionalAmountMm ?: precipitation.meanMm,
        wetModelCount = precipitation.modelCount,
        modelCount = precipitation.modelCount
    )
    is PrecipitationConfidence.Divided -> {
        val probability = precipitation.meta.probabilityPercent
            ?: if (precipitation.modelCount > 0) {
                ((precipitation.modelsForRain.toDouble() / precipitation.modelCount) * 100.0).roundToInt()
            } else null
        val conditional = precipitation.meta.conditionalAmountMm ?: precipitation.rainMeanMm
        PrecipitationDispersionPresentation(
            central = precipitation.meta.centralAmountMm
                ?: if ((probability ?: 0) >= 50) conditional else 0.0,
            // Un état Divided comporte explicitement des scénarios secs : la
            // dispersion brute commence donc à 0 même si rainMinMm ne décrit
            // que les membres humides.
            min = 0.0,
            max = precipitation.rainMaxMm.coerceAtLeast(0.0),
            probabilityPercent = probability,
            conditionalAmount = conditional,
            wetModelCount = precipitation.modelsForRain,
            modelCount = precipitation.modelCount
        )
    }
}

@Composable
private fun RainModelSummary(
    presentation: PrecipitationDispersionPresentation,
    accent: Color,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    if (presentation.modelCount <= 0) return
    val locale = LocalLocale.current.platformLocale
    val modelsLabel = stringResource(
        R.string.metric_precip_models_rain,
        presentation.wetModelCount,
        presentation.modelCount
    )

    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modelsLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            presentation.conditionalAmount?.takeIf { it.isFinite() && it > 0.0 }?.let { amount ->
                Text(
                    text = stringResource(
                        R.string.metric_precip_if_rain,
                        formatDispersionValue(amount, WeatherUnit.PRECIPITATION, 1, locale, units = units)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        RainModelVoteRail(
            wetModelCount = presentation.wetModelCount,
            modelCount = presentation.modelCount,
            accent = accent,
            contentDescription = modelsLabel
        )
    }
}

@Composable
private fun RainModelVoteRail(
    wetModelCount: Int,
    modelCount: Int,
    accent: Color,
    contentDescription: String
) {
    val dryColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val count = modelCount.coerceAtLeast(1)
        val wetCount = wetModelCount.coerceIn(0, count)
        val gap = 3.dp.toPx()
        val segmentWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        repeat(count) { index ->
            val left = index * (segmentWidth + gap)
            drawRoundRect(
                color = if (index < wetCount) accent else dryColor,
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2f)
            )
        }
    }
}

private data class DispersionDomain(val min: Double, val max: Double)

private fun paddedDispersionDomain(
    min: Double,
    max: Double,
    nonNegative: Boolean
): DispersionDomain {
    val safeMin = min.takeIf(Double::isFinite) ?: 0.0
    val safeMax = max.takeIf(Double::isFinite) ?: safeMin
    val span = (safeMax - safeMin).coerceAtLeast(0.0)
    val padding = if (span > 0.0001) {
        span * 0.16
    } else {
        max(abs(safeMin) * 0.08, 1.0)
    }
    val domainMin = (safeMin - padding).let { if (nonNegative) it.coerceAtLeast(0.0) else it }
    val domainMax = (safeMax + padding).coerceAtLeast(domainMin + 0.0001)
    return DispersionDomain(domainMin, domainMax)
}

/** Padded domain shared by both rails, in canonical °C (unit conversion happens at display). */
internal fun combinedTemperatureDispersionDomain(
    primaryMin: Double,
    primaryMax: Double,
    apparentMin: Double?,
    apparentMax: Double?,
    apparentCentral: Double?,
    nonNegative: Boolean = false
): Pair<Double, Double> {
    val values = listOfNotNull(primaryMin, primaryMax, apparentMin, apparentMax, apparentCentral)
        .filter(Double::isFinite)
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: min
    val domain = paddedDispersionDomain(min, max, nonNegative)
    return domain.min to domain.max
}

/** Prefer the observed model envelope; use forecast-score bounds only without raw samples. */
private fun actualDispersionBounds(
    score: ConfidenceScore,
    samples: List<DispersionSample>
): Pair<Double, Double> {
    val values = samples.map(DispersionSample::value).filter(Double::isFinite)
    val effective = (if (values.isEmpty()) listOf(score.minValue, score.maxValue) else values) + score.centralValue
    val finite = effective.filter(Double::isFinite)
    return (finite.minOrNull() ?: 0.0) to (finite.maxOrNull() ?: 0.0)
}

private fun windDispersionDomain(
    today: DayConfidence,
    samples: TodaySummaryDispersionSamples
): DispersionDomain {
    val values = buildList {
        today.windMax?.let { actualDispersionBounds(it, samples.wind).let { (low, high) -> add(low); add(high) } }
        today.windGustMax?.let { actualDispersionBounds(it, samples.windGust).let { (low, high) -> add(low); add(high) } }
    }.filter(Double::isFinite)
    return paddedDispersionDomain(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0, true)
}

/** Common axis for air and apparent temperatures of both daily extremes (canonical °C). */
private fun compactTemperatureDispersionDomain(
    today: DayConfidence,
    samples: TodaySummaryDispersionSamples,
    apparentMin: ApparentDispersion?,
    apparentMax: ApparentDispersion?
): DispersionDomain {
    val values = buildList {
        today.tempMin?.let { actualDispersionBounds(it, samples.tempMin).let { (low, high) -> add(low); add(high) } }
        today.tempMax?.let { actualDispersionBounds(it, samples.tempMax).let { (low, high) -> add(low); add(high) } }
        listOfNotNull(apparentMin, apparentMax).forEach { addAll(listOf(it.min, it.central, it.max)) }
    }.filter(Double::isFinite)
    val (lower, upper) = combinedTemperatureDispersionDomain(
        values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0,
        apparentMin = null, apparentMax = null, apparentCentral = null
    )
    return DispersionDomain(lower, upper)
}

/** Keep most of the row for the comparison rails, including on compact phones.
 * The upper bound prevents a wide device from growing the text column forever;
 * the plot reserve prevents oversized fonts from consuming the entire rail.
 */
private fun compactSummaryLabelWidth(availableWidth: Dp, temperature: Boolean): Dp {
    val preferred = availableWidth * (if (temperature) 0.36f else 0.32f)
    val bounded = preferred.coerceIn(
        if (temperature) 104.dp else 92.dp,
        if (temperature) 128.dp else 116.dp
    )
    return bounded.coerceAtMost((availableWidth - 104.dp).coerceAtLeast(88.dp))
}

/**
 * The summary is a dashboard, not a chart detail view. Values live in a fixed
 * left column, with the air/model dispersion and optional feels-like rail on
 * the right. No numerical labels can collide with a marker, even when the
 * model range is very narrow or the phone has a large display font.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactSummaryMetricRow(
    semanticLabel: String,
    shortLabel: String,
    score: ConfidenceScore,
    samples: List<DispersionSample>,
    unit: WeatherUnit,
    digits: Int,
    accent: Color,
    domain: DispersionDomain,
    centralTestTag: String,
    convergenceTestTag: String,
    apparent: InlineApparentBar? = null,
    apparentSamples: List<DispersionSample> = emptyList(),
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val locale = LocalLocale.current.platformLocale
    val center = formatDispersionValue(score.centralValue, unit, digits, locale, units)
    val (minimum, maximum) = actualDispersionBounds(score, samples)
    val felt = apparent?.dispersion
    val agreementDescription = "${stringResource(R.string.home_agreement_label)} ${score.convergencePercent?.let { "$it%" } ?: "—"}"
    val isTemperature = unit == WeatherUnit.TEMPERATURE_COMPACT
    // Values and convergence stay grouped, while the rails get more width.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelColumnWidth = compactSummaryLabelWidth(maxWidth, temperature = isTemperature)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(modifier = Modifier.width(labelColumnWidth)) {
                if (isTemperature) {
                    // Wrap only on tiny screens or enlarged fonts: normal text stays inline.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        // Deliberate breathing room AFTER the temperature, not
                        // between the short Min/Max label and its value.
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = shortLabel,
                                modifier = Modifier.testTag(centralTestTag + "_label"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            SummaryConvergenceValue(
                                percent = score.convergencePercent,
                                testTag = convergenceTestTag,
                                description = agreementDescription
                            )
                            Text(
                                text = center,
                                modifier = Modifier.testTag(centralTestTag),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    // A fixed gap keeps Wind/Gusts and convergence together.
                    // FlowRow wraps cleanly if large fonts need more space.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = shortLabel,
                            modifier = Modifier.testTag(centralTestTag + "_label"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        SummaryConvergenceValue(
                            percent = score.convergencePercent,
                            testTag = convergenceTestTag,
                            description = agreementDescription
                        )
                    }
                    Text(
                        text = center,
                        modifier = Modifier.testTag(centralTestTag),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (apparent != null && felt != null) {
                    val feltValue = formatDispersionValue(felt.central, unit, digits, locale, units)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = apparent.label,
                            modifier = Modifier.testTag(apparent.testTag + "_name"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = feltValue,
                            modifier = Modifier.weight(1f).testTag(apparent.testTag).semantics {
                                contentDescription = "${apparent.semanticLabel}: $feltValue"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = apparent.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CompactDispersionRail(
                    label = semanticLabel,
                    rawMin = minimum,
                    rawMax = maximum,
                    center = score.centralValue,
                    samples = samples,
                    domain = domain,
                    accent = accent,
                    unit = unit,
                    digits = digits,
                    units = units,
                    convergence = score.convergencePercent,
                    testTag = centralTestTag.removeSuffix("_central") + "_rail"
                )
                if (apparent != null && felt != null) {
                    CompactDispersionRail(
                        label = apparent.semanticLabel,
                        rawMin = felt.min,
                        rawMax = felt.max,
                        center = felt.central,
                        samples = apparentSamples,
                        domain = domain,
                        accent = apparent.accent,
                        unit = unit,
                        digits = digits,
                        units = units,
                        convergence = felt.convergence,
                        testTag = apparent.testTag.removeSuffix("_central") + "_inline_bar"
                    )
                }
            }
        }
    }
}

/** The summary must use the same convergence thresholds/colours as the rest of the app. */
@Composable
private fun SummaryConvergenceValue(percent: Int?, testTag: String, description: String) {
    Text(
        text = percent?.let { "$it%" } ?: "—",
        modifier = Modifier.testTag(testTag).semantics { contentDescription = description },
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (percent != null) confidenceColor(percent) else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun CompactApparentOnlyRow(
    label: String,
    semanticLabel: String,
    apparent: ApparentDispersion,
    samples: List<DispersionSample>,
    accent: Color,
    domain: DispersionDomain,
    testTag: String,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val locale = LocalLocale.current.platformLocale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Match the width of the air-temperature rows even when only feels-like is available.
        val labelColumnWidth = compactSummaryLabelWidth(maxWidth, temperature = true)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.width(labelColumnWidth),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.testTag(testTag + "_name"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = formatDispersionValue(apparent.central, WeatherUnit.TEMPERATURE_COMPACT, 1, locale, units),
                    modifier = Modifier.weight(1f).testTag(testTag),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.weight(1f)) {
                CompactDispersionRail(
                    label = semanticLabel,
                    rawMin = apparent.min,
                    rawMax = apparent.max,
                    center = apparent.central,
                    samples = samples,
                    domain = domain,
                    accent = accent,
                    unit = WeatherUnit.TEMPERATURE_COMPACT,
                    digits = 1,
                    units = units,
                    convergence = apparent.convergence,
                    testTag = testTag.removeSuffix("_central") + "_inline_bar"
                )
            }
        }
    }
}

// Give the summary rails a wider visual span without changing their shared domains.
private const val COMPACT_RAIL_START_FRACTION = 0.05f
private const val COMPACT_RAIL_END_FRACTION = 0.95f

/** Tap for model-level values; the compact summary never displays colliding bounds. */
@Composable
private fun CompactDispersionRail(
    label: String,
    rawMin: Double,
    rawMax: Double,
    center: Double,
    samples: List<DispersionSample>,
    domain: DispersionDomain,
    accent: Color,
    unit: WeatherUnit,
    digits: Int,
    units: WeatherUnits,
    convergence: Int? = null,
    testTag: String? = null
) {
    val locale = LocalLocale.current.platformLocale
    val lower = formatDispersionValue(rawMin, unit, digits, locale, units)
    val upper = formatDispersionValue(rawMax, unit, digits, locale, units)
    val median = formatDispersionValue(center, unit, digits, locale, units)
    val rangeText = "$lower – $upper"
    val modelTexts = samples.joinToString(" · ") { sample ->
        "${sample.model.displayName}: ${formatDispersionValue(sample.value, unit, digits, locale, units)}"
    }
    val content = "$label · $median · $rangeText" + if (modelTexts.isNotEmpty()) " · $modelTexts" else ""
    var detailsOpen by remember { mutableStateOf(false) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val centerColor = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = Modifier.fillMaxWidth().height(19.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = { detailsOpen = true })
            .semantics { contentDescription = content; role = Role.Button }
    ) {
        val start = size.width * COMPACT_RAIL_START_FRACTION
        val end = size.width * COMPACT_RAIL_END_FRACTION
        fun xFor(value: Double): Float = start + (end - start) * dispersionRatio(value, domain.min, domain.max)
        val y = size.height / 2f
        drawRoundRect(track, Offset(start, y - 2.5.dp.toPx()), Size(end - start, 5.dp.toPx()),
            cornerRadius = CornerRadius(99.dp.toPx()))
        val xMin = xFor(rawMin)
        val xMax = xFor(rawMax)
        drawRoundRect(accent.copy(alpha = 0.36f), Offset(xMin, y - 4.5.dp.toPx()),
            Size((xMax - xMin).coerceAtLeast(3.dp.toPx()), 9.dp.toPx()),
            cornerRadius = CornerRadius(99.dp.toPx()))
        samples.forEachIndexed { index, sample ->
            if (sample.value.isFinite()) {
                val point = Offset(xFor(sample.value), y + if (index % 2 == 0) -2.dp.toPx() else 2.dp.toPx())
                drawCircle(surface, radius = 4.3.dp.toPx(), center = point)
                drawCircle(accent, radius = 2.7.dp.toPx(), center = point)
            }
        }
        val x = xFor(center)
        drawRoundRect(
            centerColor,
            Offset(x - 1.5.dp.toPx(), y - 8.dp.toPx()),
            Size(3.dp.toPx(), 16.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
    }
    if (detailsOpen) {
        DispersionDetailsDialog(
            label = label,
            median = median,
            rangeText = rangeText,
            rawMin = rawMin,
            rawMax = rawMax,
            center = center,
            samples = samples,
            domain = domain,
            accent = accent,
            convergence = convergence,
            unit = unit,
            digits = digits,
            units = units,
            onDismiss = { detailsOpen = false }
        )
    }
}

/** Compact rain-amount row: temperature and wind use CompactSummaryMetricRow instead. */
@Composable
private fun DispersionMetricRow(
    semanticLabel: String,
    accent: Color,
    central: Double,
    fallbackMin: Double,
    fallbackMax: Double,
    samples: List<DispersionSample>,
    unit: WeatherUnit,
    digits: Int,
    subLabel: String? = null,
    nonNegative: Boolean = false,
    centralTestTag: String? = null,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val locale = LocalLocale.current.platformLocale
    val sampleValues = samples.map(DispersionSample::value).filter(Double::isFinite)
    // The consensus may be outside raw wet-model bounds when dry probability dominates.
    val rawMin = sampleValues.minOrNull() ?: fallbackMin
    val rawMax = sampleValues.maxOrNull() ?: fallbackMax
    val safeMin = listOf(rawMin, central).filter(Double::isFinite).minOrNull() ?: central
    val safeMax = listOf(rawMax, central).filter(Double::isFinite).maxOrNull() ?: central
    val domain = paddedDispersionDomain(safeMin, safeMax, nonNegative)
    val minLabel = formatDispersionValue(safeMin, unit, digits, locale, units)
    val maxLabel = formatDispersionValue(safeMax, unit, digits, locale, units)
    val railDescription = if (samples.isNotEmpty()) {
        "$semanticLabel · " + samples.joinToString(" · ") { sample ->
            "${sample.model.displayName} ${formatDispersionValue(sample.value, unit, digits, locale, units)}"
        }
    } else {
        "$semanticLabel · $minLabel – $maxLabel"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subLabel ?: semanticLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatDispersionValue(central, unit, digits, locale, units),
                modifier = centralTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        DispersionRail(
            samples = sampleValues,
            rangeMin = safeMin,
            rangeMax = safeMax,
            domain = domain,
            central = central,
            accent = accent,
            contentDescription = railDescription
        )
        DispersionBoundsLabels(
            minLabel = minLabel,
            maxLabel = maxLabel,
            min = safeMin,
            max = safeMax,
            domain = domain
        )
    }
}

@Composable
private fun ConvergenceTonalChip(
    convergence: Int?,
    testTag: String?
) {
    val agreementLabel = stringResource(R.string.home_agreement_label)
    val convergenceLabel = convergence?.let { "$it%" } ?: "—"
    val tint = if (convergence != null) {
        confidenceColor(convergence)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = CircleShape,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
        modifier = Modifier.semantics {
            contentDescription = "$agreementLabel $convergenceLabel"
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(tint, CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = convergenceLabel,
                modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

@Composable
private fun DispersionBoundsLabels(
    minLabel: String,
    maxLabel: String,
    min: Double,
    max: Double,
    domain: DispersionDomain
) {
    // Une même valeur affichée (arrondis inclus) n'a pas besoin d'être
    // répétée deux fois : le consensus est déjà visible dans l'en-tête.
    if (abs(max - min) <= 0.0001 || minLabel == maxLabel) return
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelGap = 8.dp
    val verticalGap = 2.dp

    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Text(
                text = minLabel,
                modifier = Modifier,
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = maxLabel,
                modifier = Modifier,
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val minPlaceable = placeables[0]
        val maxPlaceable = placeables[1]
        val width = constraints.maxWidth
        val railStart = width * DISPERSION_RAIL_START_FRACTION
        val railEnd = width * DISPERSION_RAIL_END_FRACTION
        val minCenter = railStart + (railEnd - railStart) * dispersionRatio(min, domain.min, domain.max)
        val maxCenter = railStart + (railEnd - railStart) * dispersionRatio(max, domain.min, domain.max)
        val placement = dispersionBoundsPlacement(
            width = width,
            minLabelWidth = minPlaceable.width,
            maxLabelWidth = maxPlaceable.width,
            minCenter = minCenter,
            maxCenter = maxCenter,
            gap = labelGap.roundToPx()
        )
        val height = if (placement.maxOnSecondLine) {
            minPlaceable.height + verticalGap.roundToPx() + maxPlaceable.height
        } else {
            max(minPlaceable.height, maxPlaceable.height)
        }
        layout(width, height) {
            minPlaceable.placeRelative(placement.minX, 0)
            maxPlaceable.placeRelative(
                placement.maxX,
                if (placement.maxOnSecondLine) minPlaceable.height + verticalGap.roundToPx() else 0
            )
        }
    }
}

private const val DISPERSION_RAIL_START_FRACTION = 0.08f
private const val DISPERSION_RAIL_END_FRACTION = 0.92f

private fun dispersionRatio(value: Double, min: Double, max: Double): Float {
    val span = max - min
    if (!value.isFinite() || !span.isFinite() || abs(span) <= 0.0001) return 0.5f
    return ((value - min) / span).coerceIn(0.0, 1.0).toFloat()
}

/** Precipitation-only plot, without the obsolete inline feels-like branch. */
@Composable
private fun DispersionRail(
    samples: List<Double>,
    rangeMin: Double,
    rangeMax: Double,
    domain: DispersionDomain,
    central: Double,
    accent: Color,
    contentDescription: String
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val centerColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier.fillMaxWidth().height(20.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val startX = size.width * DISPERSION_RAIL_START_FRACTION
        val endX = size.width * DISPERSION_RAIL_END_FRACTION
        val centerY = size.height / 2f
        fun xFor(value: Double): Float =
            startX + (endX - startX) * dispersionRatio(value, domain.min, domain.max)
        drawRoundRect(
            color = track,
            topLeft = Offset(startX, centerY - 2.5.dp.toPx()),
            size = Size(endX - startX, 5.dp.toPx()),
            cornerRadius = CornerRadius(99.dp.toPx())
        )
        val rangeStart = xFor(rangeMin)
        val rangeEnd = xFor(rangeMax)
        drawRoundRect(
            color = accent.copy(alpha = 0.24f),
            topLeft = Offset(rangeStart, centerY - 5.dp.toPx()),
            size = Size((rangeEnd - rangeStart).coerceAtLeast(3.dp.toPx()), 10.dp.toPx()),
            cornerRadius = CornerRadius(99.dp.toPx())
        )
        samples.forEachIndexed { index, value ->
            val y = centerY + if (index % 2 == 0) -3.dp.toPx() else 3.dp.toPx()
            drawCircle(color = surface, radius = 5.5.dp.toPx(), center = Offset(xFor(value), y))
            drawCircle(color = accent, radius = 3.75.dp.toPx(), center = Offset(xFor(value), y))
        }
        val centerX = xFor(central)
        drawRoundRect(
            color = centerColor,
            topLeft = Offset(centerX - 1.5.dp.toPx(), 1.dp.toPx()),
            size = Size(3.dp.toPx(), size.height - 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
    }
}

@Composable
private fun DispersionDetailsDialog(
    label: String,
    median: String,
    rangeText: String,
    rawMin: Double,
    rawMax: Double,
    center: Double,
    samples: List<DispersionSample>,
    domain: DispersionDomain,
    accent: Color,
    convergence: Int?,
    unit: WeatherUnit,
    digits: Int,
    units: WeatherUnits,
    onDismiss: () -> Unit
) {
    val locale = LocalLocale.current.platformLocale
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryDialogValueCard(
                        title = stringResource(R.string.metric_summary_average),
                        value = median,
                        accent = accent,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryDialogValueCard(
                        title = stringResource(R.string.metric_summary_range_short),
                        value = rangeText,
                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    convergence?.let {
                        ConvergenceTonalChip(convergence = it, testTag = "summary_dispersion_dialog_convergence")
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.metric_summary_models_count, samples.size, samples.size),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.metric_summary_range, rangeText),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        DispersionDetailRailPreview(
                            samples = samples,
                            rangeMin = rawMin,
                            rangeMax = rawMax,
                            domain = domain,
                            central = center,
                            accent = accent
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryDialogMiniStat(
                                title = stringResource(R.string.metric_summary_min_label),
                                value = formatDispersionValue(rawMin, unit, digits, locale, units),
                                accent = accent,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryDialogMiniStat(
                                title = stringResource(R.string.metric_summary_average),
                                value = median,
                                accent = accent,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryDialogMiniStat(
                                title = stringResource(R.string.metric_summary_max_label),
                                value = formatDispersionValue(rawMax, unit, digits, locale, units),
                                accent = accent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    samples.forEach { sample ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(10.dp).background(accent.copy(alpha = 0.9f), CircleShape)
                                )
                                Text(
                                    text = sample.model.displayName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatDispersionValue(sample.value, unit, digits, locale, units),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.toast_dismiss)) }
        }
    )
}

@Composable
private fun SummaryDialogValueCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryDialogMiniStat(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DispersionDetailRailPreview(
    samples: List<DispersionSample>,
    rangeMin: Double,
    rangeMax: Double,
    domain: DispersionDomain,
    central: Double,
    accent: Color
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val centerColor = MaterialTheme.colorScheme.onSurface
    val sampleBg = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier.fillMaxWidth().height(34.dp)
            .testTag("summary_dispersion_dialog_preview")
    ) {
        val startX = size.width * DISPERSION_RAIL_START_FRACTION
        val endX = size.width * DISPERSION_RAIL_END_FRACTION
        val y = size.height / 2f
        fun xFor(value: Double): Float = startX + (endX - startX) * dispersionRatio(value, domain.min, domain.max)

        drawRoundRect(
            color = track,
            topLeft = Offset(startX, y - 3.dp.toPx()),
            size = Size(endX - startX, 6.dp.toPx()),
            cornerRadius = CornerRadius(99.dp.toPx())
        )
        val rangeStart = xFor(rangeMin)
        val rangeEnd = xFor(rangeMax)
        drawRoundRect(
            color = accent.copy(alpha = 0.26f),
            topLeft = Offset(rangeStart, y - 6.dp.toPx()),
            size = Size((rangeEnd - rangeStart).coerceAtLeast(4.dp.toPx()), 12.dp.toPx()),
            cornerRadius = CornerRadius(99.dp.toPx())
        )
        samples.forEachIndexed { index, sample ->
            val point = Offset(xFor(sample.value), y + if (index % 2 == 0) -4.dp.toPx() else 4.dp.toPx())
            drawCircle(sampleBg, radius = 5.dp.toPx(), center = point)
            drawCircle(accent, radius = 3.dp.toPx(), center = point)
        }
        val centerX = xFor(central)
        drawRoundRect(
            color = centerColor,
            topLeft = Offset(centerX - 1.5.dp.toPx(), y - 10.dp.toPx()),
            size = Size(3.dp.toPx(), 20.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx())
        )
    }
}

private fun formatDispersionValue(
    value: Double,
    unit: WeatherUnit,
    digits: Int,
    locale: Locale,
    units: WeatherUnits
): String {
    return units.format(value, unit, digits, locale)
}

@Composable
private fun ConfidenceBadge(percent: Int, onClick: () -> Unit = {}) {
    val color = confidenceColor(percent)
    val a11yLabel = stringResource(R.string.a11y_open_confidence_explanation, percent)
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = shape,
        modifier = Modifier
            .semantics { contentDescription = a11yLabel }
            .testTag(TAG_CONFIDENCE_BADGE),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ){
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_agreement_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color
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
internal const val TAG_RADAR_ACTION = "radar_action"
internal const val TAG_ENGINE_COMPARISON_ACTION = "engine_comparison_action"
internal const val TAG_GRAPHIC_VIEW_ACTION = "graphic_view_action"
internal const val TAG_METEOGRAM_ACTION = "meteogram_action"
internal const val TAG_CONFIDENCE_BADGE = "confidence_badge"
internal const val TAG_TODAY_SUMMARY_SECTION_DIVIDER = "today_summary_section_divider"
internal const val TAG_TODAY_SUMMARY_TEMP_PANEL = "today_summary_temp_panel"
internal const val TAG_TODAY_SUMMARY_APPARENT = "today_summary_apparent"
internal const val TAG_TODAY_SUMMARY_PRECIP_PANEL = "today_summary_precip_panel"
internal const val TAG_TODAY_SUMMARY_WIND_PANEL = "today_summary_wind_panel"
internal const val TAG_TODAY_SUMMARY_TEMP_MIN_CENTRAL = "today_summary_temp_min_central"
internal const val TAG_TODAY_SUMMARY_TEMP_MAX_CENTRAL = "today_summary_temp_max_central"
internal const val TAG_TODAY_SUMMARY_PRECIP_CENTRAL = "today_summary_precip_central"
internal const val TAG_TODAY_SUMMARY_WIND_CENTRAL = "today_summary_wind_central"
internal const val TAG_TODAY_SUMMARY_GUST_CENTRAL = "today_summary_gust_central"
internal const val TAG_TODAY_SUMMARY_TEMP_MIN_CONVERGENCE = "today_summary_temp_min_convergence"
internal const val TAG_TODAY_SUMMARY_TEMP_MAX_CONVERGENCE = "today_summary_temp_max_convergence"
internal const val TAG_TODAY_SUMMARY_PRECIP_CONVERGENCE = "today_summary_precip_convergence"
internal const val TAG_TODAY_SUMMARY_WIND_CONVERGENCE = "today_summary_wind_convergence"
internal const val TAG_TODAY_SUMMARY_GUST_CONVERGENCE = "today_summary_gust_convergence"

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
    FlowRow(
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
                        .background(color, shape = CircleShape)
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
 * Bandeau d'avancement de la fiabilité locale, affiché au-dessus du tableau
 * de la variable sélectionnée (température, pluie ou vent).
 *
 * Il explique ce que signifie « J+1 » (la prévision émise la veille, pas
 * « demain »), ce que comptent les pastilles « N/14 » sous chaque modèle, et
 * reste affiché tant qu'une partie des modèles se complète encore, au lieu de
 * disparaître dès le premier modèle prêt sur n'importe quelle variable.
 *
 * Le bouton lance le rattrapage manuel (jusqu'à 3 semaines d'archives), déjà
 * proposé dans les Réglages, pour ne pas attendre deux semaines de collecte.
 */
@Composable
internal fun BiasHistoryHint(
    progress: BiasHistoryProgress,
    refreshState: BiasHistoryRefreshState,
    onRequestHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = ModelBias.MIN_SAMPLES_FOR_BIAS
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.48f))
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag(TAG_BIAS_HISTORY_HINT),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (progress.isPreparing) {
                    stringResource(R.string.bias_history_preparing, target)
                } else {
                    stringResource(
                        R.string.bias_history_partial,
                        progress.readyModels,
                        progress.totalModels
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (progress.bestSampleCount > 0) {
                Text(
                    text = stringResource(
                        R.string.bias_history_best_progress,
                        progress.bestSampleCount,
                        target
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            TextButton(
                onClick = onRequestHistory,
                enabled = !refreshState.isActive,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                modifier = Modifier.testTag(TAG_BIAS_HISTORY_FETCH)
            ) {
                Text(
                    text = stringResource(
                        when (refreshState) {
                            BiasHistoryRefreshState.QUEUED -> R.string.bias_history_fetch_queued
                            BiasHistoryRefreshState.RUNNING -> R.string.bias_history_fetch_requested
                            BiasHistoryRefreshState.FAILED -> R.string.action_retry
                            else -> R.string.bias_history_fetch_action
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

internal const val TAG_BIAS_HISTORY_HINT = "bias_history_hint"
internal const val TAG_BIAS_HISTORY_FETCH = "bias_history_fetch"

/**
 * enumValueOf tolérant aux noms invalides. Utilisé par la reconstruction de
 * BiasSelection dans LoadedView : si un enum est renommé/supprimé entre deux
 * versions, une valeur sauvegardée en Bundle qui ne matche plus renvoie null
 * plutôt que de crash — la sheet restera simplement fermée après restauration.
 */
private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    runCatching { enumValueOf<T>(name) }.getOrNull()

internal const val TAG_DETAIL_CITY_TITLE = "detail_city_title"
