package com.meteocompare.app.ui.localitycompare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.util.resolveZoneOrUtc
import com.meteocompare.app.domain.usecase.ConfidenceCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val TAG_LOCALITY_COMPARE = "locality_comparison"

private data class PreparedComparison(
    val daily: Map<String, com.meteocompare.app.domain.usecase.LocalityDayMetrics?>,
    val hourly: List<HourChartCity>
)

@Composable
fun LocalityCompareScreen(onBack: () -> Unit) {
    val viewModel: LocalityCompareViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LocalityCompareContent(
        state = state,
        calculator = viewModel.calculator,
        onBack = onBack,
        onToggleCity = viewModel::toggleCity,
        onRefresh = viewModel::refresh
    )
}

/** One date, one cached snapshot, three progressive levels of reading.
 * Scrolling, changing the chart metric and selecting an hour never touch the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalityCompareContent(
    state: LocalityCompareState,
    calculator: ConfidenceCalculator,
    onBack: () -> Unit,
    onToggleCity: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    currentDate: LocalDate = LocalDate.now(),
    currentHour: Int = LocalTime.now().hour
) {
    val locale = LocalLocale.current.platformLocale
    val clockPreference = LocalClockFormatPreference.current
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val dayChipFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val hourFormat = remember(locale, clockPreference) {
        WeatherTimeFormat.hourPadded(locale, clockPreference)
    }
    val units = remember(state.unitSystem) { WeatherUnits(state.unitSystem) }
    val memo = remember(calculator) { LocalityMetricsMemo(calculator) }
    val selectedCities = state.selectedCities
    val availableDates = remember(state.selectedIds, state.forecasts, currentDate) {
        state.selectedIds.flatMap { id ->
            val forecast = (state.forecasts[id] as? CompareForecastState.Ready)?.forecast
                ?: return@flatMap emptyList()
            val zone = resolveZoneOrUtc(forecast.city.timezone)
            forecast.seriesByModel.values.flatMap { series ->
                series.daily.dates + series.hourly.timestamps.map { it.atZone(zone).toLocalDate() }
            }
        }.filter { !it.isBefore(currentDate) }.distinct().sorted()
    }
    var selectedDateIso by rememberSaveable { mutableStateOf(currentDate.toString()) }
    LaunchedEffect(availableDates) {
        if (availableDates.isNotEmpty() && availableDates.none { it.toString() == selectedDateIso }) {
            selectedDateIso = availableDates.first().toString()
        }
    }
    val selectedDate = runCatching { LocalDate.parse(selectedDateIso) }.getOrDefault(currentDate)
    var selectedHour by rememberSaveable { mutableIntStateOf(currentHour.coerceIn(0, 23)) }
    var chartMetric by rememberSaveable { mutableStateOf(CompareChartMetric.TEMPERATURE) }
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    var infoExpanded by rememberSaveable { mutableStateOf(false) }

    // Confidence aggregation and solar calculations can scan multiple forecast series.
    // Prepare them away from the Compose UI thread, and serialize access to the
    // screen-scoped LRU so an obsolete computation cannot race with a new date.
    // Chart taps, metric changes and scrolling do not appear among the keys.
    val preparationMutex = remember(memo) { Mutex() }
    val prepared by produceState<PreparedComparison?>(
        initialValue = null,
        selectedCities, state.forecasts, state.uvForecasts, selectedDate, memo
    ) {
        value = null
        value = withContext(Dispatchers.Default) {
            preparationMutex.withLock {
                memo.retainCities(selectedCities.mapTo(hashSetOf()) { it.id })
                val daily = selectedCities.associate { city ->
                    currentCoroutineContext().ensureActive()
                    city.id to (state.forecasts[city.id] as? CompareForecastState.Ready)?.forecast
                        ?.let { memo.day(it, selectedDate, state.uvForecasts[city.id]) }
                }
                val hourly = selectedCities.map { city ->
                    currentCoroutineContext().ensureActive()
                    val forecast = (state.forecasts[city.id] as? CompareForecastState.Ready)?.forecast
                    HourChartCity(city.id, city.name, (0..23).map { hour ->
                        currentCoroutineContext().ensureActive()
                        forecast?.let { memo.hour(it, selectedDate, hour, state.uvForecasts[city.id]) }
                    })
                }
                PreparedComparison(daily, hourly)
            }
        }
    }
    val dailySummaries = prepared?.daily.orEmpty()
    val hourlySeries = prepared?.hourly.orEmpty()

    Scaffold(
        modifier = modifier.testTag(TAG_LOCALITY_COMPARE),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_localities_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("locality_compare_refresh")) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.favorites.size < 2) {
                Text(
                    stringResource(R.string.compare_localities_add_cities),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // The city chips are the only selection control; at most three data streams are active.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.favorites, key = { it.id }) { city ->
                        val checked = city.id in state.selectedIds
                        FilterChip(
                            selected = checked,
                            onClick = { onToggleCity(city.id) },
                            enabled = (checked && state.selectedIds.size > 2) ||
                                (!checked && state.selectedIds.size < 3),
                            label = { Text(city.name, maxLines = 1) },
                            modifier = Modifier.testTag("compare_city_${city.id}")
                        )
                    }
                }
                if (availableDates.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.testTag("compare_dates")
                    ) {
                        items(availableDates, key = { it.toString() }) { day ->
                            FilterChip(
                                selected = day == selectedDate,
                                onClick = { selectedDateIso = day.toString() },
                                label = { Text(day.format(dayChipFormat)) },
                                modifier = Modifier.testTag("compare_day_$day")
                            )
                        }
                    }
                } else {
                    val waiting = state.selectedIds.any { id ->
                        state.forecasts[id] == null || state.forecasts[id] is CompareForecastState.Loading
                    }
                    Text(
                        stringResource(if (waiting) R.string.compare_loading else R.string.compare_no_dates),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    selectedDate.format(dateFormat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("compare_selected_date")
                )
                Text(
                    stringResource(R.string.compare_overview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (prepared == null && selectedCities.isNotEmpty()) {
                    Text(
                        stringResource(R.string.compare_loading),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LocalityComparisonOverview(
                        cities = selectedCities,
                        days = dailySummaries,
                        forecasts = state.forecasts,
                        units = units,
                        locale = locale,
                        selectedMetric = chartMetric,
                        onMetricSelected = { chartMetric = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        stringResource(R.string.compare_timeline_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    LocalityHourlyChart(
                        cities = hourlySeries,
                        units = units,
                        locale = locale,
                        hourFormatter = hourFormat,
                        selectedHour = selectedHour,
                        onHourSelected = { selectedHour = it },
                        metric = chartMetric,
                        onMetricSelected = { chartMetric = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        stringResource(R.string.compare_hour_local_disclaimer),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                // Keep the disclosure in the composition while a new comparison is
                // prepared. Previously `prepared == null` removed this subtree,
                // briefly losing the panel and its semantics after a click.
                // Show missing values until the matching snapshot is ready.
                LocalityHourDetails(
                    cities = if (prepared == null) {
                        selectedCities.map { city ->
                            HourChartCity(city.id, city.name, List(24) { null })
                        }
                    } else {
                        hourlySeries
                    },
                    selectedHour = selectedHour,
                    hourLabel = LocalTime.of(selectedHour, 0).format(hourFormat),
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                    units = units,
                    locale = locale,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LocalityComparisonExplanations(
                    expanded = infoExpanded,
                    onToggle = { infoExpanded = !infoExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}
