package com.meteocompare.app.ui.citylist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherScenario
import com.meteocompare.app.domain.model.WeatherScenarioKind
import com.meteocompare.app.domain.model.WeatherScenarioTiming
import com.meteocompare.app.ui.components.AnimatedWeatherIcon
import com.meteocompare.app.ui.components.ShimmerBox
import com.meteocompare.app.ui.components.WeatherMetric
import com.meteocompare.app.ui.components.WeatherMetricLayout
import com.meteocompare.app.ui.settings.DonationDialog
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.theme.windMetricAccent
import java.time.LocalDate
import java.text.NumberFormat
import kotlin.math.roundToInt

// ============================================================================
//  Public screen entry — Hilt + state collection
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityListScreen(
    onCityClick: (cityId: String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: CityListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addState by viewModel.addCityState.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showDonationDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.marineFeedback.collect { feedback ->
            val message = when (feedback) {
                MarineFeedback.Enabled -> resources.getString(R.string.marine_enabled)
                MarineFeedback.Refreshed -> resources.getString(R.string.marine_refreshed)
                MarineFeedback.NotCoastal -> resources.getString(R.string.marine_not_coastal)
                is MarineFeedback.Error -> resources.getString(R.string.marine_error, feedback.message)
            }
            snackbarHostState.showSnackbar(
                message = message,
                duration = if (feedback is MarineFeedback.Error) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    CityListContent(
        uiState = uiState,
        onCityClick = onCityClick,
        onAddClick = { showAddSheet = true },
        onDonateClick = { showDonationDialog = true },
        onSettingsClick = onSettingsClick,
        onRemoveCity = viewModel::onRemoveCity,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefreshAll,
        onMarineAction = viewModel::onMarineAction,
        snackbarHostState = snackbarHostState
    )

    if (showDonationDialog) {
        DonationDialog(onDismiss = { showDonationDialog = false })
    }

    if (showAddSheet) {
        AddCitySheet(
            state = addState,
            onQueryChanged = viewModel::onSearchQueryChanged,
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

// ============================================================================
//  Stateless content — internal so tests can drive it without Hilt
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CityListContent(
    uiState: CityListUiState,
    onCityClick: (cityId: String) -> Unit,
    onAddClick: () -> Unit,
    onDonateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRemoveCity: (cityId: String) -> Unit,
    onRetry: (City) -> Unit,
    onRefresh: () -> Unit,
    onMarineAction: (City) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null
) {
    val effectiveSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(effectiveSnackbarHostState) },
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
                    IconButton(
                        onClick = onDonateClick,
                        modifier = Modifier.testTag(TAG_DONATE_BUTTON)
                    ) {
                        Icon(
                            Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.action_support_dev)
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
                        onRemove = onRemoveCity,
                        onRetry = onRetry,
                        onMarineAction = onMarineAction
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
    onRemove: (String) -> Unit,
    onRetry: (City) -> Unit,
    onMarineAction: (City) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_CITY_LIST),
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 104.dp, // espace pour le FAB
            start = 16.dp,
            end = 16.dp
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
                onClick = { onCityClick(state.city.id) },
                onRemove = { onRemove(state.city.id) },
                onRetry = { onRetry(state.city) },
                onMarineAction = { onMarineAction(state.city) },
                // animateItem() permet aux ajouts/suppressions d'animer
                // proprement à l'intérieur de la LazyColumn.
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(300),
                    fadeOutSpec = tween(200)
                )
            )
        }
    }
}

@Composable
private fun OfflineCityListBanner() {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onMarineAction: () -> Unit = {}
) {
    val resources = LocalResources.current
    val a11yDescription = com.meteocompare.app.ui.accessibility.A11yFormatter
        .cityCardDescription(resources, state)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val loaded = state.forecast as? ForecastState.Loaded
    val accentColor = WeatherAccent.of(
        condition = loaded?.currentCondition,
        isDark = isDark
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("$TAG_CITY_CARD${state.city.id}")
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
                role = Role.Button
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        size = Size(
                            width = 4.dp.toPx(),
                            height = size.height
                        )
                    )
                }
        ) {

            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                CityCardHeader(
                    city = state.city,
                    sunrise = loaded?.sunrise,
                    sunset = loaded?.sunset,
                    marineEnabled = state.city.marineEnabled,
                    marineAvailable = state.isMarineAvailable,
                    marineLoading = state.isMarineLoading,
                    onMarineAction = onMarineAction,
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
                            fetchedAt = forecast.fetchedAt,
                            next12hTemps = forecast.next12hTemps,
                            next12hPrecipProb = forecast.next12hPrecipProb,
                            next12hScenarios = forecast.next12hScenarios,
                            hourlyStartTime = forecast.hourlyStartTime,
                            accentColor = accentColor
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
        }
    }
}

@Composable
private fun CityCardHeader(
    city: City,
    sunrise: java.time.LocalTime?,
    sunset: java.time.LocalTime?,
    marineEnabled: Boolean,
    marineAvailable: Boolean,
    marineLoading: Boolean,
    onMarineAction: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(start = 12.dp).fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
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
                    Spacer(Modifier.width(6.dp))
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
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = city.country,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            if (sunrise != null || sunset != null) {
                Spacer(Modifier.height(3.dp))
                SunTimesRow(sunrise = sunrise, sunset = sunset)
            }
        }

        CityCardMenu(
            cityId = city.id,
            marineEnabled = marineEnabled,
            marineAvailable = marineAvailable,
            marineLoading = marineLoading,
            onMarineAction = onMarineAction,
            onRemove = onRemove
        )
    }

    Spacer(Modifier.height(8.dp))
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
        shape = RoundedCornerShape(18.dp)
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
    fetchedAt: java.time.Instant?,
    next12hTemps: List<Double?>,
    next12hPrecipProb: List<Int?>,
    next12hScenarios: List<WeatherScenario>,
    hourlyStartTime: java.time.LocalDateTime?,
    accentColor: Color
) {
    Column {
        CurrentWeatherHero(
            currentTemp = currentTemp,
            currentCondition = currentCondition,
            currentCloudCover = currentCloudCover,
            agreementPercent = today.overallPercent,
            accentColor = accentColor,
            hourlyTemps = next12hTemps,
            hourlyPrecipProb = next12hPrecipProb,
            startTime = hourlyStartTime
        )

        TodayMetricGrid(today = today)

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
                fetchedAt = fetchedAt
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
    hourlyPrecipProb: List<Int?>,
    startTime: java.time.LocalDateTime?
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        accentColor.copy(
                            alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.14f else 0.08f
                        ),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (currentCondition != null) {
                    AnimatedWeatherIcon(
                        condition = currentCondition,
                        size = 56.dp,
                        animated = true,
                        tint = Color.Unspecified
                    )
                } else {
                    Icon(
                        Icons.Outlined.Thermostat,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTemp?.let { "${it.roundToInt()}°" } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                currentCondition?.let {
                    Text(
                        text = weatherConditionLabel(it),
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

        if (hourlyTemps.any { it != null }) {
            Spacer(Modifier.height(8.dp))
            MiniForecastStrip(
                hourlyTemps = hourlyTemps,
                hourlyPrecipProb = hourlyPrecipProb,
                startTime = startTime
            )
        }
    }
}

@Composable
private fun HomeAgreementBadge(percent: Int) {
    val color = confidenceColor(percent)
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
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
private fun TodayMetricGrid(today: DayConfidence) {
    val temperature = temperatureMetricPresentation(today.tempMax)
    val precipitation = precipitationMetricPresentation(today.precipitation)
    val primaryWind = today.windMax ?: today.windGustMax
    val gustOnly = today.windMax == null && today.windGustMax != null
    val wind = windMetricPresentation(
        score = primaryWind,
        gust = if (gustOnly) null else today.windGustMax
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
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
                    .height(46.dp)
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
                    .height(46.dp)
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
                supporting = wind.supporting,
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

private fun temperatureMetricPresentation(score: ConfidenceScore?): MetricPresentation {
    if (score == null) return MetricPresentation(value = "—")
    val value = if (score.spread <= 1.0) {
        score.meanValue.roundToInt().toString()
    } else {
        "${score.minValue.roundToInt()}–${score.maxValue.roundToInt()}"
    }
    return MetricPresentation(value = value, unit = "°")
}

private fun windValue(score: ConfidenceScore?): String {
    if (score == null) return "—"
    return if (score.spread <= 2.0) {
        score.meanValue.roundToInt().toString()
    } else {
        "${score.minValue.roundToInt()}–${score.maxValue.roundToInt()}"
    }
}

@Composable
private fun windMetricPresentation(
    score: ConfidenceScore?,
    gust: ConfidenceScore?
): MetricPresentation {
    if (score == null) return MetricPresentation(value = "—")
    return MetricPresentation(
        value = windValue(score),
        unit = "km/h",
        supporting = gust?.let {
            stringResource(
                R.string.metric_gust_supporting,
                it.maxValue.roundToInt().toString()
            )
        }
    )
}

@Composable
private fun precipitationMetricPresentation(
    precip: PrecipitationConfidence?
): MetricPresentation = when (precip) {
    null -> MetricPresentation(value = "—")
    is PrecipitationConfidence.NoRain ->
        MetricPresentation(value = stringResource(R.string.precip_dry))
    is PrecipitationConfidence.Rain -> {
        val value = if (precip.minMm.roundToInt() == precip.maxMm.roundToInt()) {
            (precip.meta.centralAmountMm ?: precip.meanMm).roundToInt().toString()
        } else {
            "${precip.minMm.roundToInt()}–${precip.maxMm.roundToInt()}"
        }
        MetricPresentation(value = value, unit = "mm")
    }
    is PrecipitationConfidence.Divided -> {
        val value = if (precip.rainMinMm.roundToInt() == precip.rainMaxMm.roundToInt()) {
            (precip.meta.centralAmountMm ?: precip.rainMeanMm).roundToInt().toString()
        } else {
            "${precip.rainMinMm.roundToInt()}–${precip.rainMaxMm.roundToInt()}"
        }
        MetricPresentation(
            value = value,
            unit = "mm",
            supporting = stringResource(
                R.string.metric_precip_models_short,
                precip.modelsForRain,
                precip.modelCount
            )
        )
    }
}

@Composable
private fun weatherConditionLabel(condition: WeatherCondition): String = stringResource(
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
private fun HomeWeatherFooter(
    scenarios: List<WeatherScenario>,
    fetchedAt: java.time.Instant?
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val lastUpdated = if (fetchedAt != null) {
        com.meteocompare.app.ui.components.rememberFormattedLastUpdated(fetchedAt)
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
                    modifier = Modifier.clickable { expanded = !expanded },
                    shape = RoundedCornerShape(10.dp),
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
                shape = RoundedCornerShape(10.dp),
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
                            rank = index
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
    rank: Int
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
                    tint = Color.Unspecified
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
private fun weatherScenarioMetrics(scenario: WeatherScenario): List<String> {
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
        val value = if (gustMin.roundToInt() == gustMax.roundToInt()) {
            "${gustMax.roundToInt()} km/h"
        } else {
            "${gustMin.roundToInt()}–${gustMax.roundToInt()} km/h"
        }
        "💨 " + stringResource(R.string.home_scenario_gust_short, value)
    } else {
        null
    }

    return buildList {
        val tempMin = scenario.temperatureMinC
        val tempMax = scenario.temperatureMaxC
        if (tempMin != null && tempMax != null) {
            add(if (tempMin.roundToInt() == tempMax.roundToInt()) {
                "🌡 ${tempMin.roundToInt()}°"
            } else {
                "🌡 ${tempMin.roundToInt()}–${tempMax.roundToInt()}°"
            })
        }

        val rainMin = scenario.precipitationMinMm
        val rainMax = scenario.precipitationMaxMm
        if (rainMax != null && rainMax >= 0.05) {
            val minText = precipitationFormatter.format(rainMin ?: 0.0)
            val maxText = precipitationFormatter.format(rainMax)
            add(if ((rainMin ?: 0.0).let { kotlin.math.abs(it - rainMax) } < 0.05) {
                "🌧 $maxText mm"
            } else {
                "🌧 $minText–$maxText mm"
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
    marineAvailable: Boolean,
    marineLoading: Boolean,
    onMarineAction: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        if (marineAvailable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1976D2))
                    .testTag("$TAG_CITY_MARINE_AVAILABLE$cityId")
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (marineEnabled) R.string.action_refresh_marine else R.string.action_activate_marine
                        )
                    )
                },
                enabled = !marineLoading,
                onClick = {
                    expanded = false
                    onMarineAction()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove_from_favorites)) },
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
internal const val TAG_CITY_MARINE_AVAILABLE = "city_marine_available_"
internal const val TAG_CITY_MARINE_ENABLED = "city_marine_enabled_"
internal const val TAG_EMPTY_STATE = "empty_state"
internal const val TAG_ADD_FAB = "add_fab"
internal const val TAG_DONATE_BUTTON = "donate_button"
internal const val TAG_SETTINGS_BUTTON = "settings_button"

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun CityCardLoadedPreview() {
    MeteoCompareTheme {
        val sample = CityCardState(
            city = City(
                id = "1", name = "Paris", admin1 = "Île-de-France",
                country = "France", latitude = 48.85, longitude = 2.35
            ),
            forecast = ForecastState.Loaded(
                today = DayConfidence(
                    date = LocalDate.now(),
                    tempMax = ConfidenceScore(85, 21.0, 24.0, 22.5, 0.8, 5),
                    tempMin = ConfidenceScore(78, 14.0, 17.0, 15.5, 1.0, 5),
                    precipitation = PrecipitationConfidence.NoRain(100, 5, 0.0),
                    windMax = ConfidenceScore(72, 12.0, 18.0, 15.0, 2.5, 5)
                ),
                currentTemp = 19.0,
                currentCondition = WeatherCondition.PARTLY_CLOUDY,
                // Preview des 4 nouvelles features — courbe de temp en cloche
                // sur la journée, un peu de pluie en fin d'après-midi.
                next12hTemps = listOf(19.0, 20.5, 22.0, 23.5, 24.0, 23.5,
                    22.5, 21.0, 19.5, 18.0, 17.0, 16.5),
                next12hPrecipProb = listOf(0, 0, 10, 20, 30, 40, 60, 50, 20, 5, 0, 0),
                hourlyStartTime = java.time.LocalDateTime.of(2026, 7, 14, 15, 0),
                sunrise = java.time.LocalTime.of(6, 12),
                sunset = java.time.LocalTime.of(21, 45)
            )
        )
        Surface { CityCard(state = sample, onClick = {}, onRemove = {}, onRetry = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun CityCardLoadingPreview() {
    MeteoCompareTheme {
        val sample = CityCardState(
            city = City(id = "1", name = "Paris", country = "France",
                latitude = 48.85, longitude = 2.35),
            forecast = ForecastState.Loading
        )
        Surface { CityCard(state = sample, onClick = {}, onRemove = {}, onRetry = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MeteoCompareTheme {
        Surface { EmptyState(onAddClick = {}) }
    }
}