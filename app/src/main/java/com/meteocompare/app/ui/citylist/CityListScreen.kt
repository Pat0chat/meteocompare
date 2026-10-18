package com.meteocompare.app.ui.citylist

import com.meteocompare.app.core.util.windCardinal

import com.meteocompare.app.ui.components.UvIndexGauge

import com.meteocompare.app.core.units.WeatherUnit
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.LocalWeatherUnits

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherScenario
import com.meteocompare.app.domain.model.WeatherScenarioKind
import com.meteocompare.app.domain.model.WeatherScenarioTiming
import com.meteocompare.app.ui.accessibility.A11yFormatter
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.AppToastEffect
import com.meteocompare.app.ui.components.AppToastEvent
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.components.ShimmerBox
import com.meteocompare.app.ui.components.VigilanceCompactBanner
import com.meteocompare.app.ui.components.WeatherMetric
import com.meteocompare.app.ui.components.WeatherMetricLayout
import com.meteocompare.app.ui.components.rememberFormattedLastUpdated
import com.meteocompare.app.ui.settings.DonationDialog
import com.meteocompare.app.ui.theme.WeatherAccent
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

// Soft, synchronized morph: inset, content height, and neighbouring card
// placement share a gradual ease-in/ease-out (no default spring rebound).
// Keep these timings together so cards do not visually fight each other.
internal const val CITY_CARD_RESIZE_DURATION_MS = 800
private const val CITY_CARD_CROSSFADE_DURATION_MS = 620
private const val CITY_CARD_CROSSFADE_DELAY_MS = 120
private const val CITY_CARD_EMPHASIS_DURATION_MS = 500
private val CityCardResizeEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

// ============================================================================
//  Public screen entry — Hilt + state collection
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityListScreen(
    onCityClick: (cityId: String) -> Unit,
    onSettingsClick: () -> Unit,
    onGraphicViewClick: (cityId: String) -> Unit = {},
    onRadarClick: (cityId: String) -> Unit = {},
    onHelpClick: () -> Unit,
    onCompareClick: () -> Unit = {},
    selectedCityId: String? = null,
    selectionEnabled: Boolean = false,
    viewModel: CityListViewModel = hiltViewModel()
) {
    val locationVm: CurrentLocationViewModel = hiltViewModel()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) locationVm.locate() else locationVm.denied() }
    val requestCurrentLocation = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) locationVm.locate()
        else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addState by viewModel.addCityState.collectAsStateWithLifecycle()
    val homeExpansion by viewModel.homeExpansion.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        locationVm.refreshOnResume(viewModel::refreshIfStale)
    }

    val marineToasts = remember(viewModel) {
        viewModel.marineFeedback.map { feedback ->
            when (feedback) {
                MarineFeedback.Enabled -> AppToastEvent.success(R.string.marine_enabled)
                MarineFeedback.Refreshed -> AppToastEvent.success(R.string.marine_refreshed)
                MarineFeedback.NotCoastal -> AppToastEvent.warning(R.string.marine_not_coastal)
                is MarineFeedback.Error -> AppToastEvent.error(
                    R.string.marine_error,
                    feedback.message
                )
            }
        }
    }
    AppToastEffect(marineToasts)
    AppToastEffect(viewModel.actionFeedback)
    AppToastEffect(locationVm.feedback)

    WeatherAccentTheme(
        condition = uiState.weatherAccentCondition(selectedCityId)
    ) {
        // Empêche de déplier brièvement la dernière ville avant la lecture
        // du choix mémorisé (DataStore et favoris chargent indépendamment).
        if (!homeExpansion.isReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else CityListContent(
            uiState = uiState,
            onCityClick = onCityClick,
            onGraphicViewClick = onGraphicViewClick,
            onRadarClick = onRadarClick,
            onAddClick = { showAddSheet = true },
            onDonateClick = { showDonationDialog = true },
            onHelpClick = onHelpClick,
            onCompareClick = onCompareClick,
            onSettingsClick = onSettingsClick,
            onRemoveCity = viewModel::onRemoveCity,
            onRetry = viewModel::onRetry,
            onRefresh = { locationVm.refreshActive(viewModel::onRefreshAll) },
            onMarineAction = viewModel::onMarineAction,
            selectedCityId = selectedCityId,
            selectionEnabled = selectionEnabled,
            homeExpandedCityId = homeExpansion.expandedCityId,
            onHomeCityExpanded = viewModel::onHomeCityExpanded
        )

        if (showDonationDialog) {
            DonationDialog(onDismiss = { showDonationDialog = false })
        }

        if (showAddSheet) {
            AddCitySheet(
                state = addState,
                onQueryChanged = viewModel::onSearchQueryChanged,
                onCurrentLocationSelected = {
                    showAddSheet = false
                    requestCurrentLocation()
                },
                onCitySelected = { city ->
                    viewModel.onAddCity(city)
                    showAddSheet = false
                },
                onDismiss = {
                    showAddSheet = false
                    viewModel.onSearchQueryChanged("")
                }
            )
        }
    }
}

@Composable
private fun DonationHeartButton(onClick: () -> Unit) {
    // L'ancienne infiniteTransition demandait des frames pendant les 6 secondes
    // complètes du cycle, y compris les ~5 secondes où la valeur restait à 0.
    // Un Animatable piloté par delay ne réveille Compose que pendant les courtes
    // impulsions réellement visibles, ce qui réduit le travail GPU/CPU sur la Home.
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(pulse) {
        while (true) {
            delay(4_000)
            pulse.animateTo(1f, animationSpec = tween(200))
            pulse.animateTo(0f, animationSpec = tween(250))
            pulse.animateTo(0.7f, animationSpec = tween(150))
            pulse.animateTo(0f, animationSpec = tween(250))
            delay(1_150)
        }
    }

    val pulseValue = pulse.value
    val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
    val heartColor = lerp(baseColor, Color(0xFFE53935), pulseValue)

    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(TAG_DONATE_BUTTON)
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(R.string.action_support_dev),
            tint = heartColor,
            modifier = Modifier.scale(1f + (0.08f * pulseValue))
        )
    }
}

// ============================================================================
//  Stateless content — internal so tests can drive it without Hilt
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityListContent(
    uiState: CityListUiState,
    onCityClick: (cityId: String) -> Unit,
    onGraphicViewClick: (cityId: String) -> Unit = {},
    onRadarClick: (cityId: String) -> Unit = {},
    onAddClick: () -> Unit,
    onDonateClick: () -> Unit,
    onHelpClick: () -> Unit = {},
    onCompareClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onRemoveCity: (cityId: String) -> Unit,
    onRetry: (City) -> Unit,
    onRefresh: () -> Unit,
    onMarineAction: (City) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    selectedCityId: String? = null,
    selectionEnabled: Boolean = false,
    homeExpandedCityId: String? = null,
    onHomeCityExpanded: ((String) -> Unit)? = null
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = {
            if (snackbarHostState != null) SnackbarHost(snackbarHostState)
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold
                        )
                        if (!uiState.isEmpty) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.home_locations_followed,
                                    uiState.items.size,
                                    uiState.items.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                actions = {
                    if (uiState.items.size >= 2) {
                        IconButton(
                            onClick = onCompareClick,
                            modifier = Modifier.testTag("home_compare_localities")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.CompareArrows,
                                contentDescription = stringResource(R.string.compare_localities_title)
                            )
                        }
                    }
                    DonationHeartButton(onClick = onDonateClick)
                    IconButton(
                        onClick = onHelpClick,
                        modifier = Modifier.testTag(TAG_HELP_BUTTON)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.action_how_it_works)
                        )
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag(TAG_SETTINGS_BUTTON)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(TAG_ADD_FAB)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_city))
            }
        }
    ) { padding ->
        Crossfade(
            targetState = uiState.isEmpty,
            animationSpec = tween(250),
            modifier = Modifier.padding(padding),
            label = "list-empty-state"
        ) { empty ->
            if (empty) {
                EmptyState(onAddClick = onAddClick)
            } else {
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CityList(
                        items = uiState.items,
                        isOnline = uiState.isOnline,
                        onCityClick = onCityClick,
                        onGraphicViewClick = onGraphicViewClick,
                        onRadarClick = onRadarClick,
                        onRemove = onRemoveCity,
                        onRetry = onRetry,
                        onMarineAction = onMarineAction,
                        selectedCityId = selectedCityId,
                        selectionEnabled = selectionEnabled,
                        homeExpandedCityId = homeExpandedCityId,
                        onHomeCityExpanded = onHomeCityExpanded
                    )
                }
            }
        }
    }
}

@Composable
internal fun CityList(
    items: List<CityCardState>,
    isOnline: Boolean = true,
    onCityClick: (String) -> Unit,
    onGraphicViewClick: (String) -> Unit = {},
    onRadarClick: (String) -> Unit = {},
    onRemove: (String) -> Unit,
    onRetry: (City) -> Unit,
    onMarineAction: (City) -> Unit = {},
    selectedCityId: String? = null,
    selectionEnabled: Boolean = false,
    homeExpandedCityId: String? = null,
    onHomeCityExpanded: ((String) -> Unit)? = null
) {
    val cityIds = items.map { it.city.id }
    var knownCityIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var requestedExpandedCityId by rememberSaveable { mutableStateOf<String?>(null) }
    val localExpandedId = resolveExpandedCityId(
        expandedCityId = requestedExpandedCityId,
        previousCityIds = knownCityIds,
        cityIds = cityIds
    )
    // The tablet already owns the active city in its master/detail navigation.
    // On the phone, expansion is local to Home and survives configuration changes.
    val expandedCityId = when {
        onHomeCityExpanded != null -> homeExpandedCityId?.takeIf { it in cityIds }
            ?: cityIds.lastOrNull()
        selectionEnabled -> selectedCityId?.takeIf { it in cityIds } ?: localExpandedId
        else -> localExpandedId
    }
    LaunchedEffect(cityIds, onHomeCityExpanded) {
        if (onHomeCityExpanded == null) {
            requestedExpandedCityId = localExpandedId
            knownCityIds = ArrayList(cityIds)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_CITY_LIST),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = 104.dp, // espace pour le FAB
            start = 14.dp,
            end = 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isOnline) {
            item(key = "offline-list-banner") {
                OfflineCityListBanner()
            }
        }

        items(items, key = { it.city.id }) { state ->
            CityCard(
                state = state,
                onClick = {
                    when (cityCardTapAction(state.city.id, expandedCityId)) {
                        CityCardTapAction.EXPAND -> {
                            if (onHomeCityExpanded == null) {
                                requestedExpandedCityId = state.city.id
                            }
                            // On tablet the detail pane is the selection owner.
                            if (selectionEnabled) onCityClick(state.city.id)
                            else onHomeCityExpanded?.invoke(state.city.id)
                        }
                        CityCardTapAction.OPEN_DETAILS -> onCityClick(state.city.id)
                    }
                },
                expanded = state.city.id == expandedCityId,
                onGraphicViewClick = { onGraphicViewClick(state.city.id) },
                onRadarClick = { onRadarClick(state.city.id) },
                onRemove = { onRemove(state.city.id) },
                onRetry = { onRetry(state.city) },
                onMarineAction = { onMarineAction(state.city) },
                isSelected = state.city.id == selectedCityId,
                selectionEnabled = selectionEnabled,
                // Match the card's height morph, instead of using the default
                // spring for neighbouring items (which can feel like a snap).
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(450, easing = CityCardResizeEasing),
                    placementSpec = tween(
                        durationMillis = CITY_CARD_RESIZE_DURATION_MS,
                        easing = CityCardResizeEasing
                    ),
                    fadeOutSpec = tween(450, easing = CityCardResizeEasing)
                )
            )
        }

        item(key = "open-meteo-attribution") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OpenMeteoAttribution(home = true)
            }
        }
    }
}

@Composable
private fun OfflineCityListBanner() {
    Surface(
        shape = RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
            bottomStart = 12.dp
        ),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_data_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.offline_list_message),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityCard(
    state: CityCardState,
    onClick: () -> Unit,
    onGraphicViewClick: () -> Unit = {},
    onRadarClick: () -> Unit = {},
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onMarineAction: () -> Unit = {},
    isSelected: Boolean = false,
    selectionEnabled: Boolean = false,
    expanded: Boolean = true,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val resources = LocalResources.current
    val loaded = state.forecast as? ForecastState.Loaded
    val uv = loaded?.let { state.uvForecast?.at(it.calculatedAt ?: java.time.Instant.now()) }
    val uvDescription = uv?.let {
        val value = resources.getString(R.string.uv_index_value, it)
        val riskRes = com.meteocompare.app.ui.components.uvRiskLabelRes(it)
        resources.getString(R.string.uv_accessibility, value, resources.getString(riskRes))
    }
    val a11yDescription = A11yFormatter
        .cityCardDescription(resources, state, units = units) +
        (uvDescription?.let { ". $it" } ?: "")
    val a11yTapHint = stringResource(
        if (expanded) R.string.home_city_card_open_details else R.string.home_city_card_expand
    )
    WeatherAccentTheme(condition = loaded?.currentCondition) {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val accentColor = WeatherAccent.of(condition = loaded?.currentCondition, isDark = isDark)
        val selectionVisuals = cityCardSelectionVisuals(
            selectionEnabled = selectionEnabled,
            isSelected = isSelected
        )
        val emphasisAlpha by animateFloatAsState(
            targetValue = selectionVisuals.targetAlpha,
            animationSpec = tween(
                durationMillis = CITY_CARD_EMPHASIS_DURATION_MS,
                easing = CityCardResizeEasing
            ),
            label = "city-card-selection-emphasis"
        )
        val targetAccentColor = if (
            selectionVisuals.emphasis == CityCardVisualEmphasis.DEEMPHASIZED
        ) {
            deemphasizedCardAccentColor(isDark)
        } else {
            accentColor
        }
        val displayedAccentColor by animateColorAsState(
            targetValue = targetAccentColor,
            animationSpec = tween(
                durationMillis = CITY_CARD_EMPHASIS_DURATION_MS,
                easing = CityCardResizeEasing
            ),
            label = "city-card-weather-accent"
        )
        val targetContainerColor = when {
            selectionVisuals.usesWeatherTint -> weatherTintedCardColor(
                baseColor = MaterialTheme.colorScheme.surfaceContainerLow,
                weatherAccent = accentColor,
                isDark = isDark
            )
            selectionVisuals.emphasis == CityCardVisualEmphasis.DEEMPHASIZED -> {
                deemphasizedCardContainerColor(isDark)
            }
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
        val containerColor by animateColorAsState(
            targetValue = targetContainerColor,
            animationSpec = tween(
                durationMillis = CITY_CARD_EMPHASIS_DURATION_MS,
                easing = CityCardResizeEasing
            ),
            label = "city-card-container"
        )
        val targetGradientAccentAlpha = cityCardGradientAccentAlpha(
            emphasis = selectionVisuals.emphasis,
            isDark = isDark
        )
        val gradientAccentAlpha by animateFloatAsState(
            targetValue = targetGradientAccentAlpha,
            animationSpec = tween(
                durationMillis = CITY_CARD_EMPHASIS_DURATION_MS,
                easing = CityCardResizeEasing
            ),
            label = "city-card-gradient-accent"
        )
        // Keep the right edge aligned with full cards. The animated start
        // inset narrows compact cards from the left without moving their right
        // edge or recomputing weather data on each animation frame.
        val compactStartInset by animateDpAsState(
            targetValue = if (expanded) 0.dp else 32.dp,
            animationSpec = tween(
                durationMillis = CITY_CARD_RESIZE_DURATION_MS,
                easing = CityCardResizeEasing
            ),
            label = "city-card-compact-width"
        )

        Card(
            onClick = onClick,
            modifier = modifier
                .padding(start = compactStartInset)
                .fillMaxWidth()
                .alpha(emphasisAlpha)
                .testTag("$TAG_CITY_CARD${state.city.id}")
                .semantics(mergeDescendants = true) {
                    contentDescription = "$a11yDescription. $a11yTapHint"
                    role = Role.Button
                    if (selectionEnabled) this.selected = isSelected
                    this[CityCardExpandedKey] = expanded
                    this[CityCardVisualEmphasisKey] = selectionVisuals.emphasis
                    this[CityCardTargetAlphaKey] =
                        (selectionVisuals.targetAlpha * 100).roundToInt()
                },
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 0.dp,
                bottomEnd = 0.dp,
                bottomStart = 12.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = containerColor
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val gradientStart = displayedAccentColor.copy(alpha = gradientAccentAlpha)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    gradientStart,
                                    displayedAccentColor.copy(
                                        alpha = gradientAccentAlpha * 0.35f
                                    ),
                                    displayedAccentColor.copy(alpha = 0f)
                                )
                            )
                        )
                        drawRect(
                            color = displayedAccentColor,
                            size = Size(
                                width = 4.dp.toPx(),
                                height = size.height
                            )
                        )
                    }
            ) {

                // Keep the outgoing layout visible while the card starts to
                // resize; bring the replacement in through a long, overlapping
                // fade. Only size and opacity change, not weather calculations
                // or scale of the forecast content.
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        (fadeIn(tween(
                            durationMillis = CITY_CARD_CROSSFADE_DURATION_MS,
                            delayMillis = CITY_CARD_CROSSFADE_DELAY_MS,
                            easing = CityCardResizeEasing
                        )) togetherWith fadeOut(tween(
                            durationMillis = CITY_CARD_CROSSFADE_DURATION_MS,
                            easing = CityCardResizeEasing
                        ))).using(
                            SizeTransform(clip = true, sizeAnimationSpec = { _, _ ->
                                tween(
                                    durationMillis = CITY_CARD_RESIZE_DURATION_MS,
                                    easing = CityCardResizeEasing
                                )
                            })
                        )
                    },
                    contentAlignment = Alignment.TopStart,
                    label = "city-card-expanded"
                ) { isExpanded ->
                    if (isExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            CityCardHeader(
                                city = state.city,
                                sunrise = loaded?.sunrise,
                                sunset = loaded?.sunset,
                                marineEnabled = state.city.marineEnabled,
                                marineLoading = state.isMarineLoading,
                                onMarineAction = onMarineAction,
                                onGraphicViewClick = onGraphicViewClick,
                                onRadarClick = onRadarClick,
                                onRemove = onRemove
                            )

                            AnimatedContent(
                                targetState = state.forecast,
                                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(220)) },
                                label = "forecast-state",
                                contentKey = {
                                    when (it) {
                                        ForecastState.Loading -> "loading"
                                        is ForecastState.Loaded -> "loaded"
                                        is ForecastState.Error -> "error"
                                    }
                                }
                            ) { forecast ->
                                when (forecast) {
                                    ForecastState.Loading -> CityCardLoading()
                                    is ForecastState.Loaded -> CityCardLoaded(
                                        today = forecast.today,
                                        currentTemp = forecast.currentTemp,
                                        currentCondition = forecast.currentCondition,
                                        currentCloudCover = forecast.currentCloudCover,
                                        uvIndex = uv,
                                        fetchedAt = forecast.fetchedAt,
                                        next12hTemps = forecast.next12hTemps,
                                        next12hPrecipProb = forecast.next12hPrecipProb,
                                        next12hPrecipMm = forecast.next12hPrecipMm,
                                        next12hConditions = forecast.next12hConditions,
                                        next12hScenarios = forecast.next12hScenarios,
                                        hourlyStartTime = forecast.hourlyStartTime,
                                        vigilance = state.vigilance,
                                        cityTimezone = state.city.timezone,
                                        latitude = state.city.latitude,
                                        longitude = state.city.longitude,
                                        accentColor = accentColor,
                                        windDirectionDeg = forecast.windDirectionDeg
                                    )

                                    is ForecastState.Error -> CityCardError(
                                        message = forecast.message
                                            ?: forecast.messageRes?.let { stringResource(it) }
                                            ?: stringResource(R.string.error_unknown),
                                        onRetry = onRetry
                                    )
                                }
                            }
                        }
                    } else {
                        CompactCityCardContent(
                            state = state,
                            units = units,
                            accentColor = accentColor
                        )
                    }
                }
            }
        }
    }
}

/** Short Home summary: the same consensus values as the full card, without the
 * timeline, scenarios, UV, sun times or contextual actions. Tapping expands it. */
@Composable
private fun CompactCityCardContent(
    state: CityCardState,
    units: WeatherUnits,
    accentColor: Color
) {
    val loaded = state.forecast as? ForecastState.Loaded
    val precipitation = precipitationMetricPresentation(loaded?.today?.precipitation, units)
    val wind = windMetricPresentation(
        score = loaded?.today?.windMax ?: loaded?.today?.windGustMax,
        gust = null,
        units = units
    )
    val conditionLabel = when (val forecast = state.forecast) {
        ForecastState.Loading -> stringResource(R.string.widget_error_loading)
        is ForecastState.Error -> forecast.message
            ?: forecast.messageRes?.let { stringResource(it) }
            ?: stringResource(R.string.error_unknown)
        is ForecastState.Loaded -> forecast.currentCondition?.let {
            stringResource(weatherConditionLabelRes(it))
        } ?: stringResource(R.string.weather_unknown)
    }
    val agreement = loaded?.today?.overallPercent
    val upcomingTemperature = homeTemperatureTrend(
        currentTemp = loaded?.currentTemp,
        hourlyTemps = loaded?.next12hTemps.orEmpty()
    )
    val cloudCover = MetricPresentation(
        value = loaded?.currentCloudCover?.let { "$it%" } ?: "—"
    )
    val isNight = remember(
        state.city.latitude, state.city.longitude, state.city.timezone, loaded?.calculatedAt
    ) {
        val zone = runCatching {
            java.time.ZoneId.of(state.city.timezone ?: "UTC")
        }.getOrDefault(java.time.ZoneOffset.UTC)
        com.meteocompare.app.domain.util.isNightAtCoordinates(
            state.city.latitude, state.city.longitude, Instant.now(), zone
        ) == true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 14.dp, top = 10.dp, bottom = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Title and convergence share the first line; the temperatures follow
        // the condition label without being pushed to the right edge.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.city.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.testTag("$TAG_CITY_CARD_COMPACT_AGREEMENT${state.city.id}"),
                color = accentColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_agreement_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = agreement?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (agreement != null) confidenceColor(agreement)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Match the squared weather pictogram of the expanded card.
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .testTag("$TAG_CITY_CARD_COMPACT_CONDITION${state.city.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (loaded?.currentCondition != null) {
                    AnimatedWeatherIcon(
                        condition = loaded.currentCondition,
                        size = 40.dp,
                        animated = false,
                        tint = Color.Unspecified,
                        isNight = isNight
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Thermostat,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = conditionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // fill=false: a short label does not stretch to occupy the
                // row, so the two temperatures follow it immediately.
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("$TAG_CITY_CARD_COMPACT_CONDITION_LABEL${state.city.id}")
            )
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier.testTag("$TAG_CITY_CARD_COMPACT_TEMPERATURES${state.city.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = loaded?.currentTemp?.let { units.temp(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // Same H+3 forecast and trend chip as on the expanded card.
                upcomingTemperature?.let { HomeTemperatureTrendChip(it, units) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("$TAG_CITY_CARD_COMPACT_METRICS${state.city.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Equal-width cells keep the three measures visually centered and
            // predictable, including when one value is temporarily missing.
            CompactCityMetric(
                label = stringResource(R.string.metric_home_precipitation),
                icon = Icons.Outlined.WaterDrop,
                value = precipitation,
                modifier = Modifier
                    .weight(1f)
                    .testTag("$TAG_CITY_CARD_COMPACT_RAIN${state.city.id}")
            )
            CompactCityMetric(
                label = stringResource(
                    if (loaded?.today?.windMax == null && loaded?.today?.windGustMax != null)
                        R.string.metric_home_gusts else R.string.metric_home_wind
                ),
                icon = Icons.Outlined.Air,
                value = wind,
                modifier = Modifier
                    .weight(1f)
                    .testTag("$TAG_CITY_CARD_COMPACT_WIND${state.city.id}")
            )
            CompactCityMetric(
                label = stringResource(R.string.compare_hour_cloud_cover),
                icon = Icons.Outlined.Cloud,
                value = cloudCover,
                modifier = Modifier
                    .weight(1f)
                    .testTag("$TAG_CITY_CARD_COMPACT_CLOUD${state.city.id}")
            )
        }
    }
}

@Composable
private fun CompactCityMetric(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: MetricPresentation,
    modifier: Modifier = Modifier
) {
    val displayedValue = listOfNotNull(value.value, value.unit).joinToString(" ")
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label : $displayedValue"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = displayedValue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CityCardHeader(
    city: City,
    sunrise: LocalTime?,
    sunset: LocalTime?,
    marineEnabled: Boolean,
    marineLoading: Boolean,
    onMarineAction: () -> Unit,
    onGraphicViewClick: () -> Unit,
    onRadarClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 24.dp).fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (marineEnabled) {
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Outlined.Waves,
                        contentDescription = stringResource(R.string.marine_enabled),
                        tint = Color(0xFF1976D2),
                        modifier = Modifier
                            .size(17.dp)
                            .testTag("$TAG_CITY_MARINE_ENABLED${city.id}")
                    )
                }
                if (city.country.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = city.country,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (sunrise != null || sunset != null) {
                Spacer(Modifier.height(1.dp))
                SunTimesRow(sunrise = sunrise, sunset = sunset)
            }
        }

        CityCardMenu(
            cityId = city.id,
            marineEnabled = marineEnabled,
            marineLoading = marineLoading,
            onMarineAction = onMarineAction,
            onGraphicViewClick = onGraphicViewClick,
            onRadarClick = onRadarClick,
            onRemove = onRemove
        )
    }
}

@Composable
private fun CityCardLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            cornerRadius = 22.dp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    cornerRadius = 18.dp
                )
            }
        }
    }
}

@Composable
private fun CityCardError(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(
            topStart = 12.dp,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
            bottomStart = 12.dp
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun CityCardLoaded(
    today: DayConfidence,
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    currentCloudCover: Int?,
    uvIndex: Double?,
    fetchedAt: Instant?,
    next12hTemps: List<Double?>,
    next12hPrecipProb: List<Int?>,
    next12hPrecipMm: List<Double?>,
    next12hConditions: List<WeatherCondition?>,
    next12hScenarios: List<WeatherScenario>,
    hourlyStartTime: LocalDateTime?,
    vigilance: VigilanceForecast?,
    cityTimezone: String?,
    latitude: Double,
    longitude: Double,
    accentColor: Color,
    windDirectionDeg: Int? = null
) {
    val zone = remember(cityTimezone) {
        try { java.time.ZoneId.of(cityTimezone ?: "UTC") } catch (_: Exception) { java.time.ZoneOffset.UTC }
    }
    // Not cached against the download timestamp: sunset can pass without a new forecast.
    val currentIsNight = com.meteocompare.app.domain.util.isNightAtCoordinates(
        latitude, longitude, Instant.now(), zone
    ) == true
    val miniNightFlags = remember(latitude, longitude, zone, hourlyStartTime) {
        hourlyStartTime?.let { start ->
            List(12) { offset ->
                com.meteocompare.app.domain.util.isNightAtCoordinates(
                    latitude, longitude,
                    start.atZone(zone).toInstant().plusSeconds(offset * 3600L), zone
                )
            }
        }.orEmpty()
    }
    Column {
        CurrentWeatherHero(
            currentTemp = currentTemp,
            currentCondition = currentCondition,
            currentCloudCover = currentCloudCover,
            agreementPercent = today.overallPercent,
            accentColor = accentColor,
            hourlyTemps = next12hTemps,
            isNight = currentIsNight
        )

        if (uvIndex != null) {
            Spacer(Modifier.height(9.dp))
            UvIndexGauge(uvIndex, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(7.dp))
        }
        TodayMetricGrid(today = today, windDirectionDeg = windDirectionDeg)

        vigilance?.takeIf { it.activeAlerts.isNotEmpty() }?.let { alertForecast ->
            Spacer(Modifier.height(4.dp))
            VigilanceCompactBanner(
                vigilance = alertForecast,
                timezone = cityTimezone
            )
            Spacer(Modifier.height(3.dp))
        }

        if (next12hTemps.any { it != null }) {
            Spacer(Modifier.height(4.dp))
            MiniForecastStrip(
                hourlyTemps = next12hTemps,
                hourlyPrecipProb = next12hPrecipProb,
                hourlyPrecipMm = next12hPrecipMm,
                hourlyConditions = next12hConditions,
                startTime = hourlyStartTime,
                nightFlags = miniNightFlags
            )
        }

        val visibleScenarios = if (
            next12hScenarios.isNotEmpty() &&
            next12hScenarios.first().totalModelCount >= 2
        ) {
            next12hScenarios
        } else {
            emptyList()
        }

        if (visibleScenarios.isNotEmpty() || fetchedAt != null) {
            HomeWeatherFooter(
                scenarios = visibleScenarios,
                fetchedAt = fetchedAt,
                nightFlags = miniNightFlags
            )
        }
    }
}

@Composable
private fun CurrentWeatherHero(
    currentTemp: Double?,
    currentCondition: WeatherCondition?,
    currentCloudCover: Int?,
    agreementPercent: Int?,
    accentColor: Color,
    hourlyTemps: List<Double?>,
    isNight: Boolean = false,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val trend = homeTemperatureTrend(currentTemp, hourlyTemps)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(
                        accentColor.copy(
                            alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.14f else 0.08f
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (currentCondition != null) {
                    AnimatedWeatherIcon(
                        condition = currentCondition,
                        size = 60.dp,
                        animated = true,
                        tint = Color.Unspecified,
                        isNight = isNight
                    )
                } else {
                    Icon(
                        Icons.Outlined.Thermostat,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTemp?.let { units.temp(it) } ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    trend?.let {
                        Spacer(Modifier.width(8.dp))
                        HomeTemperatureTrendChip(it)
                    }
                }

                currentCondition?.let {
                    Text(
                        text = stringResource(weatherConditionLabelRes(it)),
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

            if (agreementPercent != null) {
                HomeAgreementBadge(percent = agreementPercent)
            }
        }
    }
}

private const val HOME_TEMPERATURE_TREND_THRESHOLD_C = 0.7
internal const val TAG_HOME_TEMPERATURE_TREND = "home_temperature_trend"

internal enum class HomeTemperatureTrendDirection { RISING, FALLING, STABLE }

internal data class HomeTemperatureTrend(
    val direction: HomeTemperatureTrendDirection,
    val targetTemperature: Int,
    /** Keep the source precision until the selected display unit is applied. */
    val targetTemperatureC: Double = targetTemperature.toDouble()
)

/** Tendance légère basée sur une échéance proche (~H+3), sans nouveau calcul météo. */
internal fun homeTemperatureTrend(
    currentTemp: Double?,
    hourlyTemps: List<Double?>
): HomeTemperatureTrend? {
    val current = currentTemp?.takeIf { it.isFinite() } ?: return null
    val target = hourlyTemps.getOrNull(3)?.takeIf { it.isFinite() }
        ?: hourlyTemps.drop(1).take(4).filterNotNull().lastOrNull { it.isFinite() }
        ?: return null
    val delta = target - current
    val direction = when {
        delta >= HOME_TEMPERATURE_TREND_THRESHOLD_C -> HomeTemperatureTrendDirection.RISING
        delta <= -HOME_TEMPERATURE_TREND_THRESHOLD_C -> HomeTemperatureTrendDirection.FALLING
        else -> HomeTemperatureTrendDirection.STABLE
    }
    return HomeTemperatureTrend(direction, target.roundToInt(), target)
}

@Composable
private fun HomeTemperatureTrendChip(trend: HomeTemperatureTrend, units: WeatherUnits = LocalWeatherUnits.current) {
    val (symbol, a11y, color) = when (trend.direction) {
        HomeTemperatureTrendDirection.RISING -> Triple(
            "↑",
            stringResource(R.string.home_temperature_trend_rising, trend.targetTemperatureC),
            temperatureMetricAccent()
        )
        HomeTemperatureTrendDirection.FALLING -> Triple(
            "↓",
            stringResource(R.string.home_temperature_trend_falling, trend.targetTemperatureC),
            if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF90CAF9) else Color(0xFF1565C0)
        )
        HomeTemperatureTrendDirection.STABLE -> Triple(
            "→",
            stringResource(R.string.home_temperature_trend_stable, trend.targetTemperatureC),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        modifier = Modifier
            .testTag(TAG_HOME_TEMPERATURE_TREND)
            .semantics { contentDescription = a11y },
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.16f else 0.10f)
    ) {
        Text(
            text = "$symbol ${units.temp(trend.targetTemperatureC)}",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun HomeAgreementBadge(percent: Int) {
    val color = confidenceColor(percent)
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_agreement_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun TodayMetricGrid(
    today: DayConfidence,
    units: WeatherUnits = LocalWeatherUnits.current,
    windDirectionDeg: Int? = null
) {
    val temperature = temperatureMetricPresentation(today.tempMax, units = units)
    val precipitation = precipitationMetricPresentation(today.precipitation)
    val primaryWind = today.windMax ?: today.windGustMax
    val gustOnly = today.windMax == null && today.windGustMax != null
    val wind = windMetricPresentation(
        score = primaryWind,
        gust = if (gustOnly) null else today.windGustMax
    )
    val windCardinalLabel = windCardinal(windDirectionDeg, androidx.compose.ui.platform.LocalConfiguration.current.locales[0])

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            WeatherMetric(
                label = stringResource(R.string.metric_home_temperature),
                icon = Icons.Outlined.Thermostat,
                value = temperature.value,
                unit = temperature.unit,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp),
                accent = temperatureMetricAccent(),
                layout = WeatherMetricLayout.Compact
            )

            VerticalDivider(
                modifier = Modifier
                    .height(30.dp)
                    .align(Alignment.CenterVertically),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            WeatherMetric(
                label = stringResource(R.string.metric_home_precipitation),
                icon = Icons.Outlined.WaterDrop,
                value = precipitation.value,
                unit = precipitation.unit,
                supporting = precipitation.supporting,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp),
                accent = precipitationMetricAccent(),
                layout = WeatherMetricLayout.Compact
            )

            VerticalDivider(
                modifier = Modifier
                    .height(30.dp)
                    .align(Alignment.CenterVertically),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            WeatherMetric(
                label = stringResource(
                    if (gustOnly) R.string.metric_home_gusts else R.string.metric_home_wind
                ),
                icon = Icons.Outlined.Air,
                value = wind.value,
                unit = wind.unit,
                supporting = listOfNotNull(windCardinalLabel, wind.supporting).joinToString(" · ").ifEmpty { null },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 5.dp),
                accent = windMetricAccent(),
                layout = WeatherMetricLayout.Compact
            )
        }
    }
}

private data class MetricPresentation(
    val value: String,
    val unit: String? = null,
    val supporting: String? = null
)

private fun temperatureMetricPresentation(score: ConfidenceScore?, units: WeatherUnits): MetricPresentation {
    if (score == null) return MetricPresentation(value = "—")
    val value = if (score.spread <= 1.0) {
        units.value(score.meanValue, WeatherUnit.TEMPERATURE_COMPACT)
    } else {
        "${units.value(score.minValue, WeatherUnit.TEMPERATURE_COMPACT)}–${units.value(score.maxValue, WeatherUnit.TEMPERATURE_COMPACT)}"
    }
    return MetricPresentation(value = value, unit = units.temperatureSuffix)
}

private fun windValue(score: ConfidenceScore?, units: WeatherUnits): String {
    if (score == null) return "—"
    return if (score.spread <= 2.0) {
        units.value(score.meanValue, WeatherUnit.WIND_SPEED)
    } else {
        "${units.value(score.minValue, WeatherUnit.WIND_SPEED)}–${units.value(score.maxValue, WeatherUnit.WIND_SPEED)}"
    }
}

@Composable
private fun windMetricPresentation(
    score: ConfidenceScore?,
    gust: ConfidenceScore?,
    units: WeatherUnits = LocalWeatherUnits.current
): MetricPresentation {
    if (score == null) return MetricPresentation(value = "—")
    return MetricPresentation(
        value = windValue(score, units = units),
        unit = units.windUnit,
        supporting = gust?.let {
            stringResource(
                R.string.metric_gust_supporting,
                units.value(it.maxValue, WeatherUnit.WIND_SPEED)
            )
        }
    )
}

@Composable
private fun precipitationMetricPresentation(
    precip: PrecipitationConfidence?,
    units: WeatherUnits = LocalWeatherUnits.current
): MetricPresentation = when (precip) {
    null -> MetricPresentation(value = "—")
    is PrecipitationConfidence.NoRain ->
        MetricPresentation(value = stringResource(R.string.precip_dry))
    is PrecipitationConfidence.Rain -> {
        val value = if (units.sameDisplayedValue(precip.minMm, precip.maxMm, WeatherUnit.PRECIPITATION, 1)) {
            units.value((precip.meta.centralAmountMm ?: precip.meanMm), WeatherUnit.PRECIPITATION, if (units.imperial) 2 else 0)
        } else {
            "${units.value(precip.minMm, WeatherUnit.PRECIPITATION, if (units.imperial) 2 else 0)}–${units.value(precip.maxMm, WeatherUnit.PRECIPITATION, if (units.imperial) 2 else 0)}"
        }
        MetricPresentation(value = value, unit = units.precipitationUnit)
    }
    is PrecipitationConfidence.Divided -> {
        val value = if (units.sameDisplayedValue(precip.rainMinMm, precip.rainMaxMm, WeatherUnit.PRECIPITATION, 1)) {
            units.value((precip.meta.centralAmountMm ?: precip.rainMeanMm), WeatherUnit.PRECIPITATION, if (units.imperial) 2 else 0)
        } else {
            "${units.value(precip.rainMinMm, WeatherUnit.PRECIPITATION, if (units.imperial) 2 else 0)}–${units.value(precip.rainMaxMm, WeatherUnit.PRECIPITATION, if (units.imperial) 2 else 0)}"
        }
        MetricPresentation(
            value = value,
            unit = units.precipitationUnit,
            supporting = stringResource(
                R.string.metric_precip_models_short,
                precip.modelsForRain,
                precip.modelCount
            )
        )
    }
}

@Composable
private fun HomeWeatherFooter(
    scenarios: List<WeatherScenario>,
    fetchedAt: Instant?,
    nightFlags: List<Boolean?> = emptyList()
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val lastUpdated = if (fetchedAt != null) {
        rememberFormattedLastUpdated(fetchedAt)
    } else {
        null
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (scenarios.isNotEmpty()) {
                Surface(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 10.dp,
                            end = 7.dp,
                            top = 5.dp,
                            bottom = 5.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = pluralStringResource(
                                R.plurals.home_scenarios_count,
                                scenarios.size,
                                scenarios.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = stringResource(
                                if (expanded) {
                                    R.string.home_scenarios_hide
                                } else {
                                    R.string.home_scenarios_show
                                }
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (lastUpdated != null) {
                if (scenarios.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = lastUpdated,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        AnimatedVisibility(
            visible = expanded && scenarios.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier.padding(vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
            ) {
                Column {
                    scenarios.forEachIndexed { index, scenario ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                        HomeWeatherScenarioRow(
                            scenario = scenario,
                            rank = index,
                            // Approximate the scenario's representative hour rather than
                            // using the phone's theme or a fixed 6-18 h window.
                            isNight = nightFlags.getOrNull(when (scenario.timing) {
                                com.meteocompare.app.domain.model.WeatherScenarioTiming.EARLY -> 1
                                com.meteocompare.app.domain.model.WeatherScenarioTiming.MIDDLE -> 5
                                com.meteocompare.app.domain.model.WeatherScenarioTiming.LATE -> 10
                                else -> 0
                            }) == true
                        )
                    }
                    scenarios.firstOrNull()?.takeIf { it.hiddenVariantCount > 0 }?.let { scenario ->
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.home_scenario_hidden_variants,
                                scenario.hiddenVariantCount,
                                scenario.hiddenVariantCount,
                                scenario.hiddenModelCount,
                                scenario.totalModelCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWeatherScenarioRow(
    scenario: WeatherScenario,
    rank: Int,
    isNight: Boolean = false
) {
    val representativeCondition = scenarioRepresentativeCondition(scenario.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(38.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (representativeCondition != null) {
                AnimatedWeatherIcon(
                    condition = representativeCondition,
                    size = 30.dp,
                    animated = false,
                    tint = Color.Unspecified,
                    isNight = isNight
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weatherScenarioTitle(scenario),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (rank == 0) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = scenario.voteSharePercent?.let { support ->
                        stringResource(R.string.home_scenario_family_support, support)
                    } ?: stringResource(
                        R.string.forecast_insight_metric_model_ratio,
                        scenario.modelCount,
                        scenario.totalModelCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rank == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (rank == 0) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            val metrics = weatherScenarioMetrics(scenario)
            if (metrics.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = metrics.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun weatherScenarioTitle(scenario: WeatherScenario): String = when (scenario.kind) {
    WeatherScenarioKind.CLEAR -> stringResource(R.string.home_scenario_clear)
    WeatherScenarioKind.VARIABLE_SKY -> stringResource(R.string.home_scenario_variable_sky)
    WeatherScenarioKind.OVERCAST -> stringResource(R.string.home_scenario_overcast)
    WeatherScenarioKind.DRY_UNSPECIFIED -> stringResource(R.string.home_scenario_dry_unspecified)
    WeatherScenarioKind.SHOWERS -> when (scenario.timing) {
        WeatherScenarioTiming.EARLY -> stringResource(R.string.home_scenario_showers_early)
        WeatherScenarioTiming.LATE -> stringResource(R.string.home_scenario_showers_late)
        WeatherScenarioTiming.THROUGHOUT -> stringResource(R.string.home_scenario_showers_throughout)
        else -> stringResource(R.string.home_scenario_showers_middle)
    }
    WeatherScenarioKind.RAIN -> when (scenario.timing) {
        WeatherScenarioTiming.EARLY -> stringResource(R.string.home_scenario_rain_early)
        WeatherScenarioTiming.LATE -> stringResource(R.string.home_scenario_rain_late)
        WeatherScenarioTiming.THROUGHOUT -> stringResource(R.string.home_scenario_rain_throughout)
        else -> stringResource(R.string.home_scenario_rain_middle)
    }
    WeatherScenarioKind.SNOW -> stringResource(R.string.home_scenario_snow)
    WeatherScenarioKind.FREEZING_RAIN -> stringResource(R.string.home_scenario_freezing_rain)
    WeatherScenarioKind.THUNDERSTORM -> stringResource(R.string.home_scenario_thunderstorm)
    WeatherScenarioKind.OTHER -> stringResource(R.string.home_scenario_other)
}

private fun scenarioRepresentativeCondition(kind: WeatherScenarioKind): WeatherCondition? = when (kind) {
    WeatherScenarioKind.CLEAR -> WeatherCondition.CLEAR
    WeatherScenarioKind.VARIABLE_SKY -> WeatherCondition.PARTLY_CLOUDY
    WeatherScenarioKind.OVERCAST -> WeatherCondition.OVERCAST
    WeatherScenarioKind.DRY_UNSPECIFIED -> null
    WeatherScenarioKind.SHOWERS -> WeatherCondition.RAIN_SHOWERS
    WeatherScenarioKind.RAIN -> WeatherCondition.RAIN
    WeatherScenarioKind.SNOW -> WeatherCondition.SNOW
    WeatherScenarioKind.FREEZING_RAIN -> WeatherCondition.FREEZING_RAIN
    WeatherScenarioKind.THUNDERSTORM -> WeatherCondition.THUNDERSTORM
    WeatherScenarioKind.OTHER -> null
}

@Composable
private fun weatherScenarioMetrics(scenario: WeatherScenario, units: WeatherUnits = LocalWeatherUnits.current): List<String> {
    val platformLocale = LocalLocale.current.platformLocale
    val precipitationFormatter = remember(platformLocale) {
        NumberFormat.getNumberInstance(platformLocale).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
    }
    val gustMin = scenario.gustMinKmh
    val gustMax = scenario.gustMaxKmh
    val gustMetric = if (gustMin != null && gustMax != null) {
        val value = if (units.sameDisplayedValue(gustMin, gustMax, WeatherUnit.WIND_SPEED)) {
            units.speed(gustMax)
        } else {
            "${units.value(gustMin, WeatherUnit.WIND_SPEED)}–${units.speed(gustMax)}"
        }
        "💨 " + stringResource(R.string.home_scenario_gust_short, value)
    } else {
        null
    }

    return buildList {
        val tempMin = scenario.temperatureMinC
        val tempMax = scenario.temperatureMaxC
        if (tempMin != null && tempMax != null) {
            add(if (units.sameDisplayedValue(tempMin, tempMax, WeatherUnit.TEMPERATURE_COMPACT)) {
                "🌡 ${units.temp(tempMin)}"
            } else {
                "🌡 ${units.value(tempMin, WeatherUnit.TEMPERATURE_COMPACT)}–${units.temp(tempMax)}"
            })
        }

        val rainMin = scenario.precipitationMinMm
        val rainMax = scenario.precipitationMaxMm
        if (rainMax != null && rainMax >= 0.05) {
            val minText = units.value(rainMin ?: 0.0, WeatherUnit.PRECIPITATION, 1, platformLocale)
            val maxText = units.value(rainMax, WeatherUnit.PRECIPITATION, 1, platformLocale)
            add(if ((rainMin ?: 0.0).let { abs(it - rainMax) } < 0.05) {
                "🌧 $maxText ${units.precipitationUnit}"
            } else {
                "🌧 $minText–$maxText ${units.precipitationUnit}"
            })
        }

        val cloudMin = scenario.cloudCoverMinPercent
        val cloudMax = scenario.cloudCoverMaxPercent
        if (cloudMin != null && cloudMax != null) {
            add(if (cloudMin == cloudMax) "☁ $cloudMax%" else "☁ $cloudMin–$cloudMax%")
        }

        gustMetric?.let(::add)
    }
}

@Composable
private fun CityCardMenu(
    cityId: String,
    marineEnabled: Boolean,
    marineLoading: Boolean,
    onMarineAction: () -> Unit,
    onGraphicViewClick: () -> Unit,
    onRadarClick: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .padding(vertical = 4.dp)
        ) {
            val menuItemModifier = Modifier
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
            DropdownMenuItem(
                modifier = menuItemModifier
                    .testTag("$TAG_CITY_MARINE_MENU$cityId"),
                text = {
                    Text(
                        stringResource(
                            if (marineEnabled) R.string.action_refresh_marine else R.string.action_activate_marine
                        )
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (marineEnabled) Icons.Filled.Refresh else Icons.Outlined.Waves,
                        contentDescription = null,
                        modifier = Modifier.testTag("$TAG_CITY_MARINE_MENU_ICON$cityId")
                    )
                },
                enabled = !marineLoading,
                onClick = {
                    expanded = false
                    onMarineAction()
                }
            )
            DropdownMenuItem(
                modifier = menuItemModifier
                    .testTag("$TAG_CITY_GRAPHIC_VIEW_MENU$cityId"),
                text = { Text(stringResource(R.string.graphic_view_open)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onGraphicViewClick()
                }
            )
            DropdownMenuItem(
                modifier = menuItemModifier
                    .testTag("$TAG_CITY_RADAR_MENU$cityId"),
                text = { Text(stringResource(R.string.radar_open)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Radar,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onRadarClick()
                }
            )
            DropdownMenuItem(
                modifier = menuItemModifier,
                text = { Text(stringResource(R.string.action_remove_from_favorites)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.testTag("$TAG_CITY_REMOVE_MENU_ICON$cityId")
                    )
                },
                onClick = {
                    expanded = false
                    onRemove()
                }
            )
        }
    }
}

@Composable
internal fun EmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_EMPTY_STATE)
            // Padding horizontal porté par le Box (et hérité par la Column
            // centrée), pour que le sous-titre — qui est long — ne vienne
            // pas coller au bord de l'écran sur les petits téléphones. Le
            // padding extérieur du Scaffold ne s'occupe pas des bords
            // latéraux, donc il faut bien le mettre ici.
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.LocationCity,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.empty_favorites_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_favorites_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // textAlign=Center pour la lisibilité d'une description
                // centrée sous un titre — un texte aligné à gauche dans
                // une Column centrée donne un look brouillon.
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_city))
            }
        }
    }
}

// ─── Test tags exposés pour les tests d'instrumentation ─────────────────────
internal const val TAG_CITY_LIST = "city_list"
internal const val TAG_CITY_CARD = "city_card_"
internal const val TAG_CITY_MARINE_ENABLED = "city_marine_enabled_"
internal const val TAG_CITY_GRAPHIC_VIEW_MENU = "city_graphic_view_menu_"
internal const val TAG_CITY_RADAR_MENU = "city_radar_menu_"
internal const val TAG_CITY_MARINE_MENU = "city_marine_menu_"
internal const val TAG_CITY_MARINE_MENU_ICON = "city_marine_menu_icon_"
internal const val TAG_CITY_REMOVE_MENU_ICON = "city_remove_menu_icon_"
internal const val TAG_EMPTY_STATE = "empty_state"
internal const val TAG_ADD_FAB = "add_fab"
internal const val TAG_DONATE_BUTTON = "donate_button"
internal const val TAG_HELP_BUTTON = "help_button"
internal const val TAG_SETTINGS_BUTTON = "settings_button"
