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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.meteocompare.app.ui.settings.DonationDialog
import com.meteocompare.app.ui.theme.confidenceColor
import com.meteocompare.app.ui.theme.MeteoCompareTheme
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale
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

    CityListContent(
        uiState = uiState,
        onCityClick = onCityClick,
        onAddClick = { showAddSheet = true },
        onDonateClick = { showDonationDialog = true },
        onSettingsClick = onSettingsClick,
        onRemoveCity = viewModel::onRemoveCity,
        onRetry = viewModel::onRetry,
        onRefresh = viewModel::onRefreshAll
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
    onRefresh: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                        onRetry = onRetry
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
    onRetry: (City) -> Unit
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
        item(key = "home-list-heading") {
            Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                Text(
                    text = stringResource(R.string.home_locations_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.home_locations_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
    modifier: Modifier = Modifier
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        // Trait supérieur météo : plus léger qu'une barre latérale et plus
        // cohérent avec les grandes cartes Material 3 arrondies.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(accentColor)
        )

        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            CityCardHeader(
                city = state.city,
                sunrise = loaded?.sunrise,
                sunset = loaded?.sunset,
                onRemove = onRemove
            )

            Spacer(Modifier.height(14.dp))

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
                    is ForecastState.Error -> CityCardError(forecast.message, onRetry)
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
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = city.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val subtitle = city.admin1 ?: city.country
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (sunrise != null || sunset != null) {
                Spacer(Modifier.height(6.dp))
                SunTimesRow(sunrise = sunrise, sunset = sunset)
            }
        }

        CityCardMenu(onRemove = onRemove)
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
            accentColor = accentColor
        )

        Spacer(Modifier.height(10.dp))

        TodayMetricGrid(today = today)

        if (next12hTemps.any { it != null }) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.home_next_12h_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.home_next_12h_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    MiniForecastStrip(
                        hourlyTemps = next12hTemps,
                        hourlyPrecipProb = next12hPrecipProb,
                        startTime = hourlyStartTime
                    )
                }
            }
        }

        if (next12hScenarios.isNotEmpty() &&
            next12hScenarios.first().totalModelCount >= 2
        ) {
            Spacer(Modifier.height(10.dp))
            HomeWeatherScenarios(scenarios = next12hScenarios)
        }

        if (fetchedAt != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = com.meteocompare.app.ui.components
                    .rememberFormattedLastUpdated(fetchedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.End)
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
    accentColor: Color
) {
    Surface(
        color = accentColor.copy(
            alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.16f else 0.10f
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentCondition != null) {
                AnimatedWeatherIcon(
                    condition = currentCondition,
                    size = 62.dp,
                    animated = false,
                    tint = Color.Unspecified
                )
            } else {
                Icon(
                    Icons.Outlined.Thermostat,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTemp?.let { "${it.roundToInt()}°" } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun TodayMetricGrid(today: DayConfidence) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeMetricTile(
            icon = Icons.Outlined.Thermostat,
            label = stringResource(R.string.home_metric_max_temperature),
            value = formatConfidenceScore(today.tempMax),
            modifier = Modifier.weight(1f)
        )

        val precipPresentation = precipitationPresentation(today.precipitation)
        HomeMetricTile(
            icon = Icons.Outlined.WaterDrop,
            label = stringResource(R.string.home_metric_rain),
            value = precipPresentation.first,
            supporting = precipPresentation.second,
            modifier = Modifier.weight(1f)
        )

        HomeMetricTile(
            icon = Icons.Outlined.Air,
            label = stringResource(R.string.home_metric_wind),
            value = formatWindScore(today.windMax),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeMetricTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatConfidenceScore(score: ConfidenceScore?): String {
    if (score == null) return "—"
    return if (score.spread <= 1.0) {
        "${score.meanValue.roundToInt()}°"
    } else {
        "${score.minValue.roundToInt()}-${score.maxValue.roundToInt()}°"
    }
}

private fun formatWindScore(score: ConfidenceScore?): String {
    if (score == null) return "—"
    return if (score.spread <= 2.0) {
        "${score.meanValue.roundToInt()} km/h"
    } else {
        "${score.minValue.roundToInt()}-${score.maxValue.roundToInt()} km/h"
    }
}

@Composable
private fun precipitationPresentation(
    precip: PrecipitationConfidence?
): Pair<String, String?> = when (precip) {
    null -> "—" to null
    is PrecipitationConfidence.NoRain ->
        stringResource(R.string.precip_dry) to null
    is PrecipitationConfidence.Rain -> {
        val value = if (precip.minMm.roundToInt() == precip.maxMm.roundToInt()) {
            "${precip.meanMm.roundToInt()} mm"
        } else {
            "${precip.minMm.roundToInt()}-${precip.maxMm.roundToInt()} mm"
        }
        value to null
    }
    is PrecipitationConfidence.Divided -> {
        val value = if (precip.rainMinMm.roundToInt() == precip.rainMaxMm.roundToInt()) {
            "${precip.rainMeanMm.roundToInt()} mm"
        } else {
            "${precip.rainMinMm.roundToInt()}-${precip.rainMaxMm.roundToInt()} mm"
        }
        value to stringResource(
            R.string.precip_divided,
            precip.modelsForRain,
            precip.modelCount
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
private fun HomeWeatherScenarios(
    scenarios: List<WeatherScenario>
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val totalModels = scenarios.firstOrNull()?.totalModelCount ?: return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "≈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_scenarios_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "${pluralStringResource(R.plurals.home_scenarios_count, scenarios.size, scenarios.size)} · " +
                            pluralStringResource(R.plurals.home_scenarios_models, totalModels, totalModels),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (expanded) R.string.home_scenarios_hide
                            else R.string.home_scenarios_show
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_scenarios_explainer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }

                    scenarios.forEachIndexed { index, scenario ->
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (rank == 0) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f)
        },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (representativeCondition != null) {
                AnimatedWeatherIcon(
                    condition = representativeCondition,
                    size = 36.dp,
                    animated = false,
                    tint = Color.Unspecified
                )
                Spacer(Modifier.width(9.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = weatherScenarioTitle(scenario),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (rank == 0) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

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

            Spacer(Modifier.width(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.forecast_insight_metric_model_ratio,
                        scenario.modelCount,
                        scenario.totalModelCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
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
            val formatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                maximumFractionDigits = 1
                minimumFractionDigits = 0
            }
            val minText = formatter.format(rainMin ?: 0.0)
            val maxText = formatter.format(rainMax)
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
private fun CityCardMenu(onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
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