package com.meteocompare.app.ui.meteogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.units.LocalWeatherUnits
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.ui.graphicview.GraphicForecastUiState
import com.meteocompare.app.ui.graphicview.GraphicForecastViewModel
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private val HourWidth = 38.dp
private val AxisWidth = 68.dp
private val WeatherHeight = 176.dp
private val TemperatureHeight = 162.dp
private val HumidityHeight = 132.dp
private val PressureHeight = 132.dp
private val WindHeight = 150.dp
private val DailyHeight = 114.dp
private val TimeHeight = 48.dp
private val CombinedHeight = 330.dp

internal const val TAG_METEOGRAM_CONTENT = "meteogram_content"
internal const val TAG_METEOGRAM_TIME = "meteogram_time"
internal const val TAG_METEOGRAM_WEATHER = "meteogram_weather"
internal const val TAG_METEOGRAM_TEMPERATURE = "meteogram_temperature"
internal const val TAG_METEOGRAM_HUMIDITY = "meteogram_humidity"
internal const val TAG_METEOGRAM_PRESSURE = "meteogram_pressure"
internal const val TAG_METEOGRAM_WIND = "meteogram_wind"
internal const val TAG_METEOGRAM_DAILY = "meteogram_daily"
internal const val TAG_METEOGRAM_SELECTED = "meteogram_selected"
internal const val TAG_METEOGRAM_COMBINED = "meteogram_combined_plot"
internal const val TAG_METEOGRAM_LAYOUT_COMBINED = "meteogram_layout_combined"
internal const val TAG_METEOGRAM_CARD_PREFIX = "meteogram_card_"
internal const val TAG_METEOGRAM_COMPARE = "meteogram_compare"
internal const val TAG_METEOGRAM_LAYOUT = "meteogram_layout"

private val RainColor = Color(0xFF0A67B3)
private val CloudColor = Color(0xFF7D8894)
private val WindColor = Color(0xFF1C8A49)
private val GustColor = Color(0xFF7BC89A)
private val TemperatureColor = Color(0xFF1776B8)
private val ApparentColor = Color(0xFFA34BC3)
private val DewColor = Color(0xFF1B98AA)
private val HumidityColor = Color(0xFFD14E8B)
private val PressureColor = Color(0xFF2E3440)
private val WarmDeltaColor = Color(0xFFE56C45)
private val CoolDeltaColor = Color(0xFF389BCC)
private val CurrentTimeColor = Color(0xFFFF7F3E)
private val SelectionColor = Color(0xFFCF6471)
private val SunriseColor = Color(0xFFF2A93B)
private val SunsetColor = Color(0xFF8F6DE1)
private val DayBoundaryColor = Color(0xFF65748B)
private val MinorBoundaryColor = Color(0xFFB1BAC6)
private val DayBackgroundColor = Color(0xFFFFD66B)
private val NightBackgroundColor = Color(0xFF2E415D)

internal data class MeteogramHour(
    val instant: Instant,
    val daylight: Boolean,
    val temperatureC: Double? = null,
    val apparentC: Double? = null,
    val dewC: Double? = null,
    val cloudPercent: Int? = null,
    val rainMm: Double? = null,
    val windKmh: Double? = null,
    val gustKmh: Double? = null,
    val windDirectionDeg: Int? = null,
    val humidityPercent: Int? = null,
    val pressureHpa: Double? = null
)

internal data class MeteogramDomain(val min: Double, val max: Double) {
    val span: Double get() = max - min
    fun fraction(value: Double): Float = ((value - min) / span).coerceIn(0.0, 1.0).toFloat()
}

internal data class MeteogramDailyRange(
    val date: LocalDate,
    val firstHour: Int,
    val hourCount: Int,
    val highC: Double?,
    val overnightLowC: Double?
)

internal enum class MeteogramKind { EVERYDAY, TEMPERATURE, HUMIDITY, PRESSURE, WIND, DAILY }
internal enum class MeteogramLayoutMode { SEPARATE, COMBINED }
private enum class MeteogramMarkerType { SUNRISE, SUNSET }
private data class MeteogramMarker(val instant: Instant, val type: MeteogramMarkerType)
private data class LegendEntry(val color: Color, val label: String, val line: Boolean = false)

// Humidity is grouped with temperature, and pressure with Everyday Weather.
// The dedicated kinds remain available for model comparison.
internal val MeteogramPresets = listOf(
    MeteogramKind.EVERYDAY, MeteogramKind.TEMPERATURE,
    MeteogramKind.WIND, MeteogramKind.DAILY
)

internal fun meteogramDomain(
    values: Sequence<Double?>,
    minimumSpan: Double,
    includeZero: Boolean = false,
    bounds: ClosedFloatingPointRange<Double>? = null
): MeteogramDomain {
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    values.forEach { value ->
        if (value != null && value.isFinite()) {
            if (value < lo) lo = value
            if (value > hi) hi = value
        }
    }
    if (!lo.isFinite()) return MeteogramDomain(bounds?.start ?: 0.0, bounds?.endInclusive ?: minimumSpan)
    if (includeZero) lo = 0.0
    val span = (hi - lo).coerceAtLeast(minimumSpan)
    if (!includeZero) {
        val middle = (lo + hi) / 2.0
        lo = middle - span * 0.56
        hi = middle + span * 0.56
    } else {
        hi = max(hi * 1.12, minimumSpan)
    }
    if (bounds != null) {
        lo = lo.coerceAtLeast(bounds.start)
        hi = hi.coerceAtMost(bounds.endInclusive)
        if (hi <= lo) hi = (lo + minimumSpan).coerceAtMost(bounds.endInclusive)
    }
    return MeteogramDomain(lo, hi)
}

internal fun meteogramDailyRanges(hours: List<MeteogramHour>, zone: ZoneId): List<MeteogramDailyRange> {
    if (hours.isEmpty()) return emptyList()
    val local = hours.map { it.instant.atZone(zone) }
    return local.indices.groupBy { local[it].toLocalDate() }.map { (date, indexes) ->
        val nextNoon = date.plusDays(1).atTime(12, 0).atZone(zone).toInstant()
        val noon = date.atTime(12, 0).atZone(zone).toInstant()
        val nightValues = hours.filter { !it.instant.isBefore(noon) && it.instant.isBefore(nextNoon) }
        val completeDay = local[indexes.first()].hour == 0 &&
            local[indexes.last()].hour == 23 && indexes.size >= 23
        val nightComplete = hours.first().instant <= noon &&
            hours.last().instant >= nextNoon.minusSeconds(3600) &&
            nightValues.size >= 22
        MeteogramDailyRange(
            date = date,
            firstHour = indexes.first(),
            hourCount = indexes.size,
            highC = if (completeDay) indexes.mapNotNull { hours[it].temperatureC?.takeIf(Double::isFinite) }.maxOrNull() else null,
            overnightLowC = if (nightComplete) nightValues.mapNotNull { it.temperatureC?.takeIf(Double::isFinite) }.minOrNull() else null
        )
    }
}

internal fun meteogramViewport(startPx: Float, widthPx: Float, hourPx: Float, count: Int): IntRange {
    if (count <= 0 || hourPx <= 0f) return IntRange.EMPTY
    return (floor(startPx / hourPx).toInt() - 2).coerceAtLeast(0)..
        (ceil((startPx + widthPx) / hourPx).toInt() + 2).coerceAtMost(count - 1)
}

internal fun buildMeteogramHours(state: GraphicForecastUiState.Loaded, selectedModel: String?): List<MeteogramHour> {
    val zone = runCatching { ZoneId.of(state.city.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
    return state.points.mapNotNull { point ->
        val instant = point.instant ?: return@mapNotNull null
        val solar = state.solarByDate[instant.atZone(zone).toLocalDate()]
        val daylight = if (solar?.sunrise != null && solar.sunset != null) {
            instant >= solar.sunrise && instant < solar.sunset
        } else point.isNight != true
        val model = selectedModel?.let { selected ->
            state.modelValuesByInstant[instant]?.firstOrNull { it.modelName == selected }
        }
        MeteogramHour(
            instant = instant,
            daylight = daylight,
            temperatureC = if (selectedModel == null) point.temperatureC else model?.temperatureC,
            apparentC = if (selectedModel == null) point.apparentTemperatureC else model?.apparentTemperatureC,
            dewC = if (selectedModel == null) point.dewPointC else model?.dewPointC,
            cloudPercent = if (selectedModel == null) point.cloudCoverPercent else model?.cloudPercent,
            rainMm = if (selectedModel == null) point.precipitationMm else model?.precipitationMm,
            windKmh = if (selectedModel == null) point.windKmh else model?.windKmh,
            gustKmh = if (selectedModel == null) point.windGustKmh else model?.windGustKmh,
            windDirectionDeg = if (selectedModel == null) point.windDirectionDeg else model?.windDirectionDeg,
            humidityPercent = if (selectedModel == null) point.humidityPercent else model?.humidityPercent,
            pressureHpa = if (selectedModel == null) point.pressureHpa else model?.pressureHpa
        )
    }
}

private fun buildMeteogramMarkers(
    hours: List<MeteogramHour>,
    solarByDate: Map<LocalDate, com.meteocompare.app.ui.graphicview.GraphicSolarWindow>,
    zone: ZoneId
): List<MeteogramMarker> {
    if (hours.isEmpty()) return emptyList()
    val firstDate = hours.first().instant.atZone(zone).toLocalDate()
    val lastDate = hours.last().instant.atZone(zone).toLocalDate()
    return solarByDate.filterKeys { it >= firstDate.minusDays(1) && it <= lastDate.plusDays(1) }
        .values
        .flatMap { window ->
            buildList {
                window.sunrise?.let { add(MeteogramMarker(it, MeteogramMarkerType.SUNRISE)) }
                window.sunset?.let { add(MeteogramMarker(it, MeteogramMarkerType.SUNSET)) }
            }
        }
        .distinctBy { it.instant to it.type }
        .sortedBy { it.instant }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeteogramScreen(
    onBack: () -> Unit,
    viewModel: GraphicForecastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshIfStale() }
    WeatherAccentTheme(condition = (state as? GraphicForecastUiState.Loaded)?.points?.firstOrNull()?.condition) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.meteogram_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            }
        ) { padding ->
            when (val current = state) {
                GraphicForecastUiState.Loading -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                is GraphicForecastUiState.Error -> Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(current.message)
                    TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.action_retry)) }
                }
                is GraphicForecastUiState.Loaded -> MeteogramContent(current, Modifier.padding(padding))
            }
        }
    }
}

@Composable
internal fun MeteogramContent(state: GraphicForecastUiState.Loaded, modifier: Modifier = Modifier) {
    val units = LocalWeatherUnits.current
    val locale = LocalConfiguration.current.locales[0]
    val clockPreference = LocalClockFormatPreference.current
    val timeFormatter = remember(locale, clockPreference) { WeatherTimeFormat.hourPadded(locale, clockPreference) }
    val zone = remember(state.city.timezone) {
        runCatching { ZoneId.of(state.city.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
    }
    val models = remember(state.modelValuesByInstant) {
        state.modelValuesByInstant.values.asSequence().flatten().map { it.modelName }.distinct().sorted().toList()
    }
    var selectedModel by rememberSaveable(state.city.id) { mutableStateOf<String?>(null) }
    val hours = remember(state.points, state.modelValuesByInstant, state.solarByDate, selectedModel) {
        buildMeteogramHours(state, selectedModel)
    }
    val markers = remember(hours, state.solarByDate, zone) { buildMeteogramMarkers(hours, state.solarByDate, zone) }
    val currentInstant = remember(state.calculatedAt) { state.calculatedAt }
    var selectedIndex by rememberSaveable(state.city.id) { mutableIntStateOf(0) }
    var scrollPx by remember(state.city.id) { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    var layoutMode by rememberSaveable(state.city.id) { mutableStateOf(MeteogramLayoutMode.SEPARATE) }
    val density = LocalDensity.current
    val hourPx = with(density) { HourWidth.toPx() }
    val scroll = rememberScrollableState { delta ->
        val maxOffset = (hours.size * hourPx - widthPx).coerceAtLeast(0f)
        val next = (scrollPx - delta).coerceIn(0f, maxOffset)
        val consumed = scrollPx - next
        scrollPx = next
        consumed
    }
    val visible by remember(hours, hourPx) {
        derivedStateOf { meteogramViewport(scrollPx, widthPx.toFloat(), hourPx, hours.size) }
    }
    val usedHours = if (visible.isEmpty()) emptyList() else hours.subList(visible.first, visible.last + 1)
    val temperatureDomain = remember(hours, visible.first, visible.last) {
        meteogramDomain(usedHours.asSequence().flatMap { sequenceOf(it.temperatureC, it.apparentC, it.dewC) }, 3.0)
    }
    val rainDomain = remember(hours, visible.first, visible.last) {
        meteogramDomain(usedHours.asSequence().map { it.rainMm }, 0.5, includeZero = true)
    }
    val windDomain = remember(hours, visible.first, visible.last) {
        meteogramDomain(usedHours.asSequence().flatMap { sequenceOf(it.windKmh, it.gustKmh) }, 5.0, includeZero = true)
    }
    val humidityDomain = remember(hours, visible.first, visible.last) {
        meteogramDomain(usedHours.asSequence().map { it.humidityPercent?.toDouble() }, 15.0, bounds = 0.0..100.0)
    }
    val pressureDomain = remember(hours, visible.first, visible.last) {
        meteogramDomain(usedHours.asSequence().map { it.pressureHpa }, 3.0)
    }
    val ranges = remember(hours, zone) { meteogramDailyRanges(hours, zone) }
    val dailyDomain = remember(ranges, hours) { meteogramHourlyDailyDomain(hours, ranges) }
    val priorTemperatures = remember(hours) { hours.associate { it.instant to it.temperatureC } }
    val onSelect: (Int) -> Unit = { selectedIndex = it.coerceIn(0, (hours.size - 1).coerceAtLeast(0)) }
    var comparing by rememberSaveable(state.city.id) { mutableStateOf(false) }
    var compareKind by rememberSaveable(state.city.id) { mutableStateOf(MeteogramKind.EVERYDAY) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(TAG_METEOGRAM_CONTENT)
    ) {
        Text(
            state.city.name,
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.meteogram_browse_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedModel == null,
                onClick = { selectedModel = null },
                label = { Text(stringResource(R.string.meteogram_consensus)) }
            )
            models.forEach { name ->
                FilterChip(
                    selected = selectedModel == name,
                    onClick = { selectedModel = name },
                    label = { Text(name, maxLines = 1) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (comparing) stringResource(R.string.meteogram_compare_title) else stringResource(R.string.meteogram_presets_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (models.isNotEmpty()) {
                FilterChip(
                    selected = comparing,
                    onClick = { comparing = !comparing },
                    label = { Text(stringResource(R.string.meteogram_compare_action)) },
                    modifier = Modifier.testTag(TAG_METEOGRAM_COMPARE)
                )
            }
        }
        if (!comparing) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = layoutMode == MeteogramLayoutMode.SEPARATE,
                    onClick = { layoutMode = MeteogramLayoutMode.SEPARATE },
                    label = { Text(stringResource(R.string.meteogram_layout_separate)) },
                    modifier = Modifier.testTag(TAG_METEOGRAM_LAYOUT)
                )
                FilterChip(
                    selected = layoutMode == MeteogramLayoutMode.COMBINED,
                    onClick = { layoutMode = MeteogramLayoutMode.COMBINED },
                    label = { Text(stringResource(R.string.meteogram_layout_combined)) },
                    modifier = Modifier.testTag(TAG_METEOGRAM_LAYOUT_COMBINED)
                )
            }
        }
        if (hours.isEmpty()) {
            Text(stringResource(R.string.meteogram_no_data), Modifier.padding(20.dp))
            return@Column
        }
        val index = selectedIndex.coerceIn(hours.indices)
        val chartAccessibility = stringResource(R.string.meteogram_a11y)
        if (comparing && models.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MeteogramKind.entries.filter { it != MeteogramKind.DAILY }.forEach { kind ->
                    FilterChip(selected = compareKind == kind, onClick = { compareKind = kind }, label = { Text(meteogramKindLabel(kind)) })
                }
            }
            val modelHoursByName = remember(state.points, state.modelValuesByInstant, state.solarByDate, models) {
                models.associateWith { name -> buildMeteogramHours(state, name) }
            }
            val compareHours = remember(modelHoursByName, visible.first, visible.last, hours.size) {
                if (visible.isEmpty()) emptyList() else modelHoursByName.values.flatMap { modelHours ->
                    if (modelHours.size == hours.size) modelHours.subList(visible.first, visible.last + 1) else emptyList()
                }
            }
            val compareDomain = remember(compareHours, compareKind) {
                meteogramDomain(
                    meteogramKindValues(compareKind, compareHours),
                    meteogramMinimumSpan(compareKind),
                    includeZero = compareKind == MeteogramKind.EVERYDAY || compareKind == MeteogramKind.WIND,
                    bounds = if (compareKind == MeteogramKind.HUMIDITY) 0.0..100.0 else null
                )
            }
            val compareWindDomain = remember(compareHours) {
                meteogramDomain(compareHours.asSequence().flatMap { sequenceOf(it.windKmh, it.gustKmh) }, 5.0, includeZero = true)
            }
            val compareRainDomain = remember(compareHours) {
                meteogramDomain(compareHours.asSequence().map { it.rainMm }, 0.5, includeZero = true)
            }
            models.forEach { name ->
                val modelHours = modelHoursByName.getValue(name)
                MeteogramChartCard(
                    kind = compareKind,
                    title = name,
                    hours = modelHours,
                    zone = zone,
                    locale = locale,
                    timeFormatter = timeFormatter,
                    units = units,
                    step = hourPx,
                    offset = scrollPx,
                    range = visible,
                    selectedIndex = index,
                    scroll = scroll,
                    onSelect = onSelect,
                    onWidthChange = { widthPx = it },
                    domain = compareDomain,
                    windDomain = compareWindDomain,
                    rainDomain = compareRainDomain,
                    humidityDomain = meteogramDomain(compareHours.asSequence().map { it.humidityPercent?.toDouble() }, 15.0, bounds = 0.0..100.0),
                    pressureDomain = meteogramDomain(compareHours.asSequence().map { it.pressureHpa }, 3.0),
                    priorTemperatures = remember(modelHours) { modelHours.associate { it.instant to it.temperatureC } },
                    dailyRanges = emptyList(),
                    accessibility = chartAccessibility,
                    markers = markers,
                    currentInstant = currentInstant
                )
            }
        } else if (layoutMode == MeteogramLayoutMode.COMBINED) {
            MeteogramCombinedCard(
                title = stringResource(R.string.meteogram_title),
                hours = hours,
                zone = zone,
                locale = locale,
                timeFormatter = timeFormatter,
                units = units,
                step = hourPx,
                offset = scrollPx,
                range = visible,
                selectedIndex = index,
                scroll = scroll,
                onSelect = onSelect,
                onWidthChange = { widthPx = it },
                temperatureDomain = temperatureDomain,
                humidityDomain = humidityDomain,
                pressureDomain = pressureDomain,
                windDomain = windDomain,
                rainDomain = rainDomain,
                priorTemperatures = priorTemperatures,
                dailyRanges = ranges,
                accessibility = chartAccessibility,
                markers = markers,
                currentInstant = currentInstant
            )
        } else {
            MeteogramPresets.forEach { kind ->
                val domain = when (kind) {
                    MeteogramKind.EVERYDAY -> rainDomain
                    MeteogramKind.TEMPERATURE -> temperatureDomain
                    MeteogramKind.DAILY -> dailyDomain
                    MeteogramKind.HUMIDITY -> humidityDomain
                    MeteogramKind.PRESSURE -> pressureDomain
                    MeteogramKind.WIND -> windDomain
                }
                MeteogramChartCard(
                    kind = kind,
                    title = meteogramKindLabel(kind),
                    hours = hours,
                    zone = zone,
                    locale = locale,
                    timeFormatter = timeFormatter,
                    units = units,
                    step = hourPx,
                    offset = scrollPx,
                    range = visible,
                    selectedIndex = index,
                    scroll = scroll,
                    onSelect = onSelect,
                    onWidthChange = { widthPx = it },
                    domain = domain,
                    windDomain = windDomain,
                    rainDomain = rainDomain,
                    humidityDomain = humidityDomain,
                    pressureDomain = pressureDomain,
                    priorTemperatures = priorTemperatures,
                    dailyRanges = ranges,
                    accessibility = chartAccessibility,
                    markers = markers,
                    currentInstant = currentInstant
                )
            }
        }
        MeteogramSelectedValues(hours[index], zone, units, locale)
    }
}

@Composable
private fun meteogramKindLabel(kind: MeteogramKind): String = when (kind) {
    MeteogramKind.EVERYDAY -> stringResource(R.string.meteogram_weather)
    MeteogramKind.TEMPERATURE -> stringResource(R.string.meteogram_temperature)
    MeteogramKind.HUMIDITY -> stringResource(R.string.meteogram_humidity)
    MeteogramKind.PRESSURE -> stringResource(R.string.meteogram_pressure)
    MeteogramKind.WIND -> stringResource(R.string.meteogram_wind)
    MeteogramKind.DAILY -> stringResource(R.string.meteogram_daily_title)
}

internal fun meteogramMinimumSpan(kind: MeteogramKind): Double = when (kind) {
    MeteogramKind.EVERYDAY -> 0.5
    MeteogramKind.TEMPERATURE, MeteogramKind.DAILY -> 3.0
    MeteogramKind.HUMIDITY -> 15.0
    MeteogramKind.PRESSURE -> 3.0
    MeteogramKind.WIND -> 5.0
}

internal fun meteogramKindValues(kind: MeteogramKind, hours: List<MeteogramHour>): Sequence<Double?> =
    hours.asSequence().flatMap { hour ->
        when (kind) {
            MeteogramKind.EVERYDAY -> sequenceOf(hour.rainMm)
            MeteogramKind.TEMPERATURE, MeteogramKind.DAILY -> sequenceOf(hour.temperatureC, hour.apparentC, hour.dewC)
            MeteogramKind.HUMIDITY -> sequenceOf(hour.humidityPercent?.toDouble())
            MeteogramKind.PRESSURE -> sequenceOf(hour.pressureHpa)
            MeteogramKind.WIND -> sequenceOf(hour.windKmh, hour.gustKmh)
        }
    }

@Composable
private fun MeteogramChartCard(
    kind: MeteogramKind,
    title: String,
    hours: List<MeteogramHour>,
    zone: ZoneId,
    locale: Locale,
    timeFormatter: DateTimeFormatter,
    units: WeatherUnits,
    step: Float,
    offset: Float,
    range: IntRange,
    selectedIndex: Int,
    scroll: androidx.compose.foundation.gestures.ScrollableState,
    onSelect: (Int) -> Unit,
    onWidthChange: (Int) -> Unit,
    domain: MeteogramDomain,
    windDomain: MeteogramDomain,
    rainDomain: MeteogramDomain,
    humidityDomain: MeteogramDomain,
    pressureDomain: MeteogramDomain,
    priorTemperatures: Map<Instant, Double?>,
    dailyRanges: List<MeteogramDailyRange>,
    accessibility: String,
    markers: List<MeteogramMarker>,
    currentInstant: Instant
) {
    val cardTag = TAG_METEOGRAM_CARD_PREFIX + kind.name.lowercase()
    val height = cardHeight(kind)
    val selected = hours.getOrNull(selectedIndex)
    val hasPressure = remember(hours, range.first, range.last) {
        range.any { index -> hours.getOrNull(index)?.pressureHpa?.isFinite() == true }
    }
    val hasHumidity = remember(hours, range.first, range.last) {
        range.any { index -> hours.getOrNull(index)?.humidityPercent?.let { it in 0..100 } == true }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(cardTag).semantics { contentDescription = accessibility },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                title,
                Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            MeteogramChartLegend(kind, units, rainDomain)
            Row(Modifier.padding(start = 8.dp, end = 5.dp, top = 4.dp)) {
                Column(
                    Modifier.weight(1f).onSizeChanged { onWidthChange(it.width) }
                        .scrollable(scroll, Orientation.Horizontal)
                ) {
                    MeteogramTimeAxis(hours, zone, locale, timeFormatter, step, offset, selectedIndex, markers, currentInstant)
                    if (kind == MeteogramKind.DAILY) {
                        MeteogramDailyStrip(hours, dailyRanges, locale, units, zone, step, offset, selectedIndex, markers, currentInstant)
                    } else {
                        MeteogramPlot(hours, height, step, offset, range, selectedIndex, meteogramKindTag(kind), onSelect) { first, last, shift, hourStep ->
                            drawChartFrame(hours, zone, first, last, shift, hourStep, markers, currentInstant, kind == MeteogramKind.EVERYDAY)
                            when (kind) {
                                MeteogramKind.EVERYDAY -> {
                                    drawCloudBars(hours, first, last, shift, hourStep)
                                    drawRainBars(hours, first, last, shift, hourStep, rainDomain)
                                    drawMetricCurve(hours, first, last, shift, hourStep, windDomain, WindColor) { it.windKmh }
                                    drawMetricCurve(hours, first, last, shift, hourStep, pressureDomain, PressureColor) { it.pressureHpa }
                                    drawWindDirectionArrows(hours, first, last, shift, hourStep)
                                }
                                MeteogramKind.TEMPERATURE -> {
                                    drawDayOverDayBands(hours, priorTemperatures, first, last, shift, hourStep, domain)
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, TemperatureColor) { it.temperatureC }
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, ApparentColor) { it.apparentC }
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, DewColor) { it.dewC }
                                    drawMetricCurve(hours, first, last, shift, hourStep, humidityDomain, HumidityColor) { it.humidityPercent?.toDouble() }
                                }
                                MeteogramKind.HUMIDITY -> drawMetricCurve(hours, first, last, shift, hourStep, domain, HumidityColor) { it.humidityPercent?.toDouble() }
                                MeteogramKind.PRESSURE -> drawMetricCurve(hours, first, last, shift, hourStep, domain, PressureColor) { it.pressureHpa }
                                MeteogramKind.WIND -> {
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, WindColor) { it.windKmh }
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, GustColor) { it.gustKmh }
                                    drawWindDirectionArrows(hours, first, last, shift, hourStep)
                                }
                                MeteogramKind.DAILY -> Unit
                            }
                        }
                    }
                }
                MeteogramValueAxis(kind, height, domain, windDomain, units,
                    if (kind == MeteogramKind.EVERYDAY || kind == MeteogramKind.TEMPERATURE) 58.dp else AxisWidth)
                when (kind) {
                    MeteogramKind.EVERYDAY -> {
                        if (hasPressure) MeteogramValueAxis(
                            MeteogramKind.PRESSURE, height, pressureDomain, windDomain, units, 60.dp)
                    }
                    MeteogramKind.TEMPERATURE -> {
                        if (hasHumidity) MeteogramValueAxis(
                            MeteogramKind.HUMIDITY, height, humidityDomain, windDomain, units, 54.dp)
                    }
                    else -> Unit
                }
            }
            if (selected != null) {
                Text(
                    meteogramSelectedCaption(kind, selected, units),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One actual graphing surface, rather than six plot sections in one card. */
@Composable
private fun MeteogramCombinedCard(
    title: String,
    hours: List<MeteogramHour>,
    zone: ZoneId,
    locale: Locale,
    timeFormatter: DateTimeFormatter,
    units: WeatherUnits,
    step: Float,
    offset: Float,
    range: IntRange,
    selectedIndex: Int,
    scroll: androidx.compose.foundation.gestures.ScrollableState,
    onSelect: (Int) -> Unit,
    onWidthChange: (Int) -> Unit,
    temperatureDomain: MeteogramDomain,
    humidityDomain: MeteogramDomain,
    pressureDomain: MeteogramDomain,
    windDomain: MeteogramDomain,
    rainDomain: MeteogramDomain,
    priorTemperatures: Map<Instant, Double?>,
    dailyRanges: List<MeteogramDailyRange>,
    accessibility: String,
    markers: List<MeteogramMarker>,
    currentInstant: Instant
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = accessibility },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(title, Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MeteogramCombinedLegend(units, rainDomain)
            Text(stringResource(R.string.meteogram_combined_scales_hint),
                Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Each colored curve has its own domain. The visible temperature scale
            // is on the right; all other ranges are fully labelled below.
            Row(Modifier.padding(start = 8.dp, end = 5.dp, top = 4.dp)) {
                Column(Modifier.weight(1f).onSizeChanged { onWidthChange(it.width) }
                    .scrollable(scroll, Orientation.Horizontal)) {
                    MeteogramTimeAxis(hours, zone, locale, timeFormatter, step,
                        offset, selectedIndex, markers, currentInstant)
                    MeteogramPlot(hours, CombinedHeight, step, offset, range, selectedIndex,
                        TAG_METEOGRAM_COMBINED, onSelect) { first, last, shift, hourStep ->
                        drawChartFrame(hours, zone, first, last, shift, hourStep,
                            markers, currentInstant, false)
                        drawCloudBars(hours, first, last, shift, hourStep, alpha = 0.22f)
                        drawRainBars(hours, first, last, shift, hourStep, rainDomain)
                        drawDayOverDayBands(hours, priorTemperatures, first, last,
                            shift, hourStep, temperatureDomain)
                        drawMetricCurve(hours, first, last, shift, hourStep,
                            temperatureDomain, TemperatureColor) { it.temperatureC }
                        drawMetricCurve(hours, first, last, shift, hourStep,
                            temperatureDomain, ApparentColor) { it.apparentC }
                        drawMetricCurve(hours, first, last, shift, hourStep,
                            temperatureDomain, DewColor) { it.dewC }
                        drawMetricCurve(hours, first, last, shift, hourStep,
                            humidityDomain, HumidityColor) { it.humidityPercent?.toDouble() }
                        drawMetricCurve(hours, first, last, shift, hourStep,
                            pressureDomain, PressureColor) { it.pressureHpa }
                        drawMetricCurve(hours, first, last, shift, hourStep,
                            windDomain, WindColor) { it.windKmh }
                        drawMetricCurve(hours, first, last, shift, hourStep,
                            windDomain, GustColor) { it.gustKmh }
                        drawDailyRangeMarkers(dailyRanges, temperatureDomain, shift, hourStep)
                        drawWindDirectionArrows(hours, first, last, shift, hourStep)
                    }
                }
                MeteogramValueAxis(MeteogramKind.TEMPERATURE,
                    CombinedHeight, temperatureDomain, windDomain, units)
            }
            val shown = if (range.isEmpty()) emptyList() else hours.subList(range.first, range.last + 1)
            MeteogramCombinedScaleLegend(units, rainDomain, windDomain,
                humidityDomain, pressureDomain,
                hasHumidity = shown.any { it.humidityPercent != null },
                hasPressure = shown.any { it.pressureHpa?.isFinite() == true })
        }
    }
}

/** The non-temperature lines in the single canvas are independently normalized.
 *  Show exact axis ranges rather than implying that pressure is in °C. */
@Composable
private fun MeteogramCombinedScaleLegend(
    units: WeatherUnits,
    rain: MeteogramDomain,
    wind: MeteogramDomain,
    humidity: MeteogramDomain,
    pressure: MeteogramDomain,
    hasHumidity: Boolean,
    hasPressure: Boolean
) {
    val entries = listOf(
        LegendEntry(RainColor,
            "${stringResource(R.string.meteogram_rain)} 0–${units.number(units.precipitation(rain.max), units.precipitationDigits)} ${units.precipitationUnit}/h"),
        LegendEntry(WindColor,
            "${stringResource(R.string.meteogram_wind)} 0–${units.number(units.wind(wind.max))} ${units.windUnit}", line = true),
        LegendEntry(HumidityColor,
            "${stringResource(R.string.meteogram_humidity)} ${if (hasHumidity) "${units.number(humidity.min)}–${units.number(humidity.max)} %" else "—"}", line = true),
        LegendEntry(PressureColor,
            "${stringResource(R.string.meteogram_pressure)} ${if (hasPressure) "${units.number(units.pressure(pressure.min), if (units.imperial) 2 else 0)}–${units.number(units.pressure(pressure.max), if (units.imperial) 2 else 0)} ${if (units.imperial) "inHg" else "hPa"}" else "—"}", line = true)
    )
    MeteogramLegend(entries)
}

/** Daily range markers live inside the combined canvas, one per local day. */
private fun DrawScope.drawDailyRangeMarkers(
    ranges: List<MeteogramDailyRange>,
    domain: MeteogramDomain,
    offset: Float,
    step: Float
) {
    val half = 5.dp.toPx()
    for (day in ranges) {
        val high = day.highC?.takeIf(Double::isFinite) ?: continue
        val low = day.overnightLowC?.takeIf(Double::isFinite) ?: continue
        val x = (day.firstHour + day.hourCount * 0.5f) * step - offset
        if (x !in -step..size.width + step) continue
        val top = (size.height - 8.dp.toPx()) * (1f - domain.fraction(high)) + 4.dp.toPx()
        val bottom = (size.height - 8.dp.toPx()) * (1f - domain.fraction(low)) + 4.dp.toPx()
        drawLine(TemperatureColor.copy(alpha = 0.48f), Offset(x, top), Offset(x, bottom), 4.dp.toPx())
        drawLine(WarmDeltaColor, Offset(x - half, top), Offset(x + half, top), 2.dp.toPx())
        drawLine(CoolDeltaColor, Offset(x - half, bottom), Offset(x + half, bottom), 2.dp.toPx())
    }
}

private fun cardHeight(kind: MeteogramKind): Dp = when (kind) {
    MeteogramKind.EVERYDAY -> WeatherHeight
    MeteogramKind.TEMPERATURE -> TemperatureHeight
    MeteogramKind.HUMIDITY -> HumidityHeight
    MeteogramKind.PRESSURE -> PressureHeight
    MeteogramKind.WIND -> WindHeight
    MeteogramKind.DAILY -> DailyHeight
}

private fun meteogramKindTag(kind: MeteogramKind): String = when (kind) {
    MeteogramKind.EVERYDAY -> TAG_METEOGRAM_WEATHER
    MeteogramKind.TEMPERATURE -> TAG_METEOGRAM_TEMPERATURE
    MeteogramKind.HUMIDITY -> TAG_METEOGRAM_HUMIDITY
    MeteogramKind.PRESSURE -> TAG_METEOGRAM_PRESSURE
    MeteogramKind.WIND -> TAG_METEOGRAM_WIND
    MeteogramKind.DAILY -> TAG_METEOGRAM_DAILY
}

@Composable
private fun meteogramSelectedCaption(kind: MeteogramKind, hour: MeteogramHour, units: WeatherUnits): String = when (kind) {
    MeteogramKind.EVERYDAY -> "${stringResource(R.string.meteogram_cloud_total)}: ${hour.cloudPercent?.let { "$it%" } ?: "—"} · ${stringResource(R.string.meteogram_rain)}: ${units.rain(hour.rainMm)} · ${stringResource(R.string.meteogram_wind)}: ${units.speed(hour.windKmh)} · ${stringResource(R.string.meteogram_pressure)}: ${hour.pressureHpa?.let { units.number(units.pressure(it), if (units.imperial) 2 else 0) } ?: "—"}"
    MeteogramKind.TEMPERATURE -> "${units.temp(hour.temperatureC)} · ${stringResource(R.string.apparent_temperature_widget_prefix)} ${units.temp(hour.apparentC)} · ${stringResource(R.string.metric_dew_point_short)} ${units.temp(hour.dewC)} · ${stringResource(R.string.meteogram_humidity)}: ${hour.humidityPercent?.let { "$it%" } ?: "—"}"
    MeteogramKind.HUMIDITY -> "${stringResource(R.string.meteogram_humidity)}: ${hour.humidityPercent?.let { "$it%" } ?: "—"}"
    MeteogramKind.PRESSURE -> "${stringResource(R.string.meteogram_pressure)}: ${hour.pressureHpa?.let { units.number(units.pressure(it), if (units.imperial) 2 else 0) } ?: "—"} ${if (units.imperial) "inHg" else "hPa"}"
    MeteogramKind.WIND -> "${stringResource(R.string.meteogram_wind)}: ${units.speed(hour.windKmh)}  ·  ${stringResource(R.string.graphic_view_gust_short)}: ${units.speed(hour.gustKmh)}"
    MeteogramKind.DAILY -> "${stringResource(R.string.meteogram_hourly_temperatures)}: ${units.temp(hour.temperatureC)} · ${stringResource(R.string.meteogram_daily_caption)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeteogramChartLegend(kind: MeteogramKind, units: WeatherUnits, rain: MeteogramDomain) {
    val entries = when (kind) {
        MeteogramKind.EVERYDAY -> listOf(
            LegendEntry(DayBackgroundColor, stringResource(R.string.meteogram_sun)),
            LegendEntry(CloudColor, stringResource(R.string.meteogram_cloud_total)),
            LegendEntry(RainColor, "${stringResource(R.string.meteogram_rain)} (0–${units.number(units.precipitation(rain.max), units.precipitationDigits)} ${units.precipitationUnit}/h)"),
            LegendEntry(WindColor, stringResource(R.string.meteogram_wind), line = true),
            LegendEntry(PressureColor, stringResource(R.string.meteogram_pressure), line = true),
            LegendEntry(SunriseColor, stringResource(R.string.meteogram_sunrise), line = true),
            LegendEntry(SunsetColor, stringResource(R.string.meteogram_sunset), line = true)
        )
        MeteogramKind.TEMPERATURE -> listOf(
            LegendEntry(TemperatureColor, stringResource(R.string.meteogram_temperature), line = true),
            LegendEntry(ApparentColor, stringResource(R.string.apparent_temperature_widget_prefix), line = true),
            LegendEntry(DewColor, stringResource(R.string.metric_dew_point_short), line = true),
            LegendEntry(HumidityColor, stringResource(R.string.meteogram_humidity), line = true),
            LegendEntry(WarmDeltaColor, stringResource(R.string.meteogram_warmer)),
            LegendEntry(CoolDeltaColor, stringResource(R.string.meteogram_colder))
        )
        MeteogramKind.HUMIDITY -> listOf(LegendEntry(HumidityColor, "%", line = true))
        MeteogramKind.PRESSURE -> listOf(LegendEntry(PressureColor, if (units.imperial) "inHg" else "hPa", line = true))
        MeteogramKind.WIND -> listOf(
            LegendEntry(WindColor, stringResource(R.string.meteogram_wind), line = true),
            LegendEntry(GustColor, stringResource(R.string.graphic_view_gust_short), line = true)
        )
        MeteogramKind.DAILY -> listOf(
            LegendEntry(TemperatureColor, stringResource(R.string.meteogram_hourly_temperatures), line = true),
            LegendEntry(WarmDeltaColor, stringResource(R.string.meteogram_daily_caption))
        )
    }
    MeteogramLegend(entries)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeteogramCombinedLegend(units: WeatherUnits, rain: MeteogramDomain) {
    MeteogramLegend(
        listOf(
            LegendEntry(DayBackgroundColor, stringResource(R.string.meteogram_sun)),
            LegendEntry(CloudColor, stringResource(R.string.meteogram_cloud_total)),
            LegendEntry(RainColor, "${stringResource(R.string.meteogram_rain)} (0–${units.number(units.precipitation(rain.max), units.precipitationDigits)} ${units.precipitationUnit}/h)"),
            LegendEntry(TemperatureColor, stringResource(R.string.meteogram_temperature), line = true),
            LegendEntry(ApparentColor, stringResource(R.string.apparent_temperature_widget_prefix), line = true),
            LegendEntry(DewColor, stringResource(R.string.metric_dew_point_short), line = true),
            LegendEntry(HumidityColor, "%", line = true),
            LegendEntry(PressureColor, if (units.imperial) "inHg" else "hPa", line = true),
            LegendEntry(WindColor, stringResource(R.string.meteogram_wind), line = true),
            LegendEntry(GustColor, stringResource(R.string.graphic_view_gust_short), line = true),
            LegendEntry(SunriseColor, stringResource(R.string.meteogram_sunrise), line = true),
            LegendEntry(SunsetColor, stringResource(R.string.meteogram_sunset), line = true)
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeteogramLegend(entries: List<LegendEntry>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        entries.forEach { entry -> MeteogramKey(entry) }
    }
}

@Composable
private fun MeteogramKey(entry: LegendEntry) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (entry.line) {
            Box(
                Modifier.width(14.dp).height(3.dp)
                    .background(entry.color, RoundedCornerShape(50))
            )
        } else {
            Box(Modifier.size(9.dp).background(entry.color, CircleShape))
        }
        Text(
            entry.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun MeteogramValueAxis(
    kind: MeteogramKind,
    height: Dp,
    domain: MeteogramDomain,
    wind: MeteogramDomain,
    units: WeatherUnits,
    axisWidth: Dp = AxisWidth
) {
    val valueDomain = if (kind == MeteogramKind.EVERYDAY) wind else domain
    val labels = when (kind) {
        MeteogramKind.TEMPERATURE, MeteogramKind.DAILY -> listOf(valueDomain.max, (valueDomain.min + valueDomain.max) / 2, valueDomain.min)
            .map { "${units.number(units.temperature(it))}${units.temperatureSuffix}" }
        MeteogramKind.WIND, MeteogramKind.EVERYDAY -> listOf(valueDomain.max, valueDomain.max / 2, 0.0)
            .map { units.number(units.wind(it)) }
        MeteogramKind.HUMIDITY -> listOf(valueDomain.max, (valueDomain.min + valueDomain.max) / 2, valueDomain.min)
            .map { "${units.number(it)}%" }
        MeteogramKind.PRESSURE -> listOf(valueDomain.max, (valueDomain.min + valueDomain.max) / 2, valueDomain.min)
            .map { units.number(units.pressure(it), if (units.imperial) 2 else 0) }
    }
    Column(Modifier.width(axisWidth).padding(start = 3.dp)) {
        Box(Modifier.height(TimeHeight), contentAlignment = Alignment.Center) {
            Text(
                when (kind) {
                    MeteogramKind.EVERYDAY, MeteogramKind.WIND -> units.windUnit
                    MeteogramKind.TEMPERATURE, MeteogramKind.DAILY -> units.temperatureUnit
                    MeteogramKind.HUMIDITY -> "%"
                    MeteogramKind.PRESSURE -> if (units.imperial) "inHg" else "hPa"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Column(
            Modifier.height(height).padding(vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            labels.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MeteogramTimeAxis(
    hours: List<MeteogramHour>,
    zone: ZoneId,
    locale: Locale,
    hourFormatter: DateTimeFormatter,
    step: Float,
    offset: Float,
    selectedIndex: Int,
    markers: List<MeteogramMarker>,
    currentInstant: Instant
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val accentInk = MaterialTheme.colorScheme.onSurface.toArgb()
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val fontSize = with(LocalDensity.current) { 11.sp.toPx() }
    val smallFontSize = with(LocalDensity.current) { 10.sp.toPx() }
    val paint = remember(ink, fontSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = fontSize } }
    val markerPaint = remember(accentInk, smallFontSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = accentInk; textSize = smallFontSize } }
    Canvas(Modifier.fillMaxWidth().height(TimeHeight).testTag(TAG_METEOGRAM_TIME)) {
        val range = meteogramViewport(offset, size.width, step, hours.size)
        if (range.isEmpty()) return@Canvas
        drawTimeGrid(hours, zone, range.first, range.last, offset, step, drawMinor = true)
        for (i in range) {
            val dateTime = hours[i].instant.atZone(zone)
            val x = (i + 0.5f) * step - offset
            if (dateTime.hour == 0 || i == 0) {
                drawContext.canvas.nativeCanvas.drawText(dateTime.format(dayFormatter), x + 4.dp.toPx(), 16.dp.toPx(), paint)
            }
            if (dateTime.hour % 3 == 0) {
                drawContext.canvas.nativeCanvas.drawText(dateTime.format(hourFormatter), x, 38.dp.toPx(), paint)
            }
        }
        drawMarkerLines(hours, markers, offset, step)
        markers.forEach { marker ->
            val x = positionForInstant(hours, marker.instant, step, offset) ?: return@forEach
            if (x in -12.dp.toPx()..size.width + 12.dp.toPx()) {
                val symbol = if (marker.type == MeteogramMarkerType.SUNRISE) "↑" else "↓"
                drawContext.canvas.nativeCanvas.drawText(symbol, x - 3.dp.toPx(), 28.dp.toPx(), markerPaint)
            }
        }
        drawCurrentTimeLine(hours, currentInstant, offset, step)
        drawSelectionLine(selectedIndex, offset, step)
    }
}

@Composable
private fun MeteogramPlot(
    hours: List<MeteogramHour>,
    height: Dp,
    step: Float,
    offset: Float,
    range: IntRange,
    selectedIndex: Int,
    tag: String,
    onSelect: (Int) -> Unit,
    drawing: DrawScope.(first: Int, last: Int, offset: Float, step: Float) -> Unit
) {
    val currentOffset by rememberUpdatedState(offset)
    Canvas(
        Modifier.fillMaxWidth().height(height).testTag(tag)
            .pointerInput(hours.size, step) {
                detectTapGestures { tap -> onSelect(floor((tap.x + currentOffset) / step).toInt()) }
            }
    ) {
        if (range.isEmpty()) return@Canvas
        drawLine(CloudColor.copy(alpha = 0.12f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
        drawing(range.first, range.last, offset, step)
        drawSelectionLine(selectedIndex, offset, step)
    }
}

private fun DrawScope.drawChartFrame(
    hours: List<MeteogramHour>,
    zone: ZoneId,
    first: Int,
    last: Int,
    offset: Float,
    step: Float,
    markers: List<MeteogramMarker>,
    currentInstant: Instant,
    everyday: Boolean
) {
    drawMeteogramBackground(hours, first, last, offset, step, everyday)
    drawTimeGrid(hours, zone, first, last, offset, step, drawMinor = true)
    drawMarkerLines(hours, markers, offset, step)
    drawCurrentTimeLine(hours, currentInstant, offset, step)
}

private fun DrawScope.drawMeteogramBackground(
    hours: List<MeteogramHour>,
    first: Int,
    last: Int,
    offset: Float,
    step: Float,
    everyday: Boolean = false
) {
    for (i in first..last) {
        val x = i * step - offset
        val color = if (hours[i].daylight) {
            if (everyday) DayBackgroundColor.copy(alpha = 0.55f) else DayBackgroundColor.copy(alpha = 0.06f)
        } else {
            if (everyday) NightBackgroundColor.copy(alpha = 0.22f) else NightBackgroundColor.copy(alpha = 0.10f)
        }
        drawRect(color, topLeft = Offset(x, 0f), size = Size(step + 0.5f, size.height))
    }
    for (row in 1..3) {
        val y = size.height * row / 4f
        drawLine(CloudColor.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y))
    }
}

private fun DrawScope.drawTimeGrid(
    hours: List<MeteogramHour>,
    zone: ZoneId,
    first: Int,
    last: Int,
    offset: Float,
    step: Float,
    drawMinor: Boolean
) {
    for (i in first..last) {
        val hour = hours[i].instant.atZone(zone).hour
        val x = i * step - offset
        when {
            hour == 0 -> drawLine(DayBoundaryColor.copy(alpha = 0.65f), Offset(x, 0f), Offset(x, size.height), 1.6.dp.toPx())
            drawMinor && hour % 6 == 0 -> drawLine(MinorBoundaryColor.copy(alpha = 0.34f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawMinor && hour % 3 == 0 -> drawLine(MinorBoundaryColor.copy(alpha = 0.18f), Offset(x, 0f), Offset(x, size.height), 0.6.dp.toPx())
        }
    }
}

/** One bar = total cloud cover. No artificial high/mid/low bands. */
private fun DrawScope.drawCloudBars(
    hours: List<MeteogramHour>, first: Int, last: Int,
    offset: Float, step: Float, alpha: Float = 0.68f
) {
    val inset = 2.dp.toPx()
    for (i in first..last) {
        val cover = hours[i].cloudPercent?.takeIf { it in 0..100 } ?: continue
        val depth = size.height * cover / 100f
        if (depth <= 0f) continue
        val x = i * step - offset + inset
        val width = (step - 2 * inset).coerceAtLeast(1.dp.toPx())
        drawRect(CloudColor.copy(alpha = alpha), Offset(x, 0f), Size(width, depth))
        drawLine(CloudColor.copy(alpha = (alpha + 0.18f).coerceAtMost(1f)),
            Offset(x, depth), Offset(x + width, depth), 1.dp.toPx())
    }
}

private fun DrawScope.drawRainBars(
    hours: List<MeteogramHour>,
    first: Int,
    last: Int,
    offset: Float,
    step: Float,
    domain: MeteogramDomain
) {
    val padding = 4.dp.toPx()
    for (i in first..last) {
        val rain = hours[i].rainMm?.takeIf { it.isFinite() && it > 0 } ?: continue
        val height = size.height * 0.92f * domain.fraction(rain)
        drawRoundRect(
            RainColor.copy(alpha = 0.88f),
            topLeft = Offset(i * step - offset + padding, size.height - height),
            size = Size((step - padding * 2).coerceAtLeast(2.dp.toPx()), height),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

private fun DrawScope.drawMetricCurve(
    hours: List<MeteogramHour>,
    first: Int,
    last: Int,
    offset: Float,
    step: Float,
    domain: MeteogramDomain,
    color: Color,
    value: (MeteogramHour) -> Double?
) {
    var path = Path()
    var previous: Offset? = null
    var segments = 0
    for (i in first..last) {
        val v = value(hours[i])?.takeIf(Double::isFinite)
        if (v == null) {
            if (segments > 0) {
                drawPath(path, color, style = Stroke(2.55.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            path = Path()
            previous = null
            segments = 0
            continue
        }
        val point = Offset(
            (i + 0.5f) * step - offset,
            (size.height - 8.dp.toPx()) * (1f - domain.fraction(v)) + 4.dp.toPx()
        )
        val previousPoint = previous
        if (previousPoint == null) {
            path.moveTo(point.x, point.y)
        } else {
            val dx = point.x - previousPoint.x
            path.cubicTo(
                previousPoint.x + dx * 0.36f,
                previousPoint.y,
                point.x - dx * 0.36f,
                point.y,
                point.x,
                point.y
            )
            segments++
        }
        previous = point
    }
    if (segments > 0) {
        drawPath(path, color, style = Stroke(2.55.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawDayOverDayBands(
    hours: List<MeteogramHour>,
    previous: Map<Instant, Double?>,
    first: Int,
    last: Int,
    offset: Float,
    step: Float,
    domain: MeteogramDomain
) {
    fun y(value: Double): Float = 4.dp.toPx() + (size.height - 8.dp.toPx()) * (1f - domain.fraction(value))
    for (i in first until last) {
        val a = hours[i].temperatureC?.takeIf(Double::isFinite) ?: continue
        val b = hours[i + 1].temperatureC?.takeIf(Double::isFinite) ?: continue
        val oldA = previous[hours[i].instant.minusSeconds(86400)]?.takeIf(Double::isFinite) ?: continue
        val oldB = previous[hours[i + 1].instant.minusSeconds(86400)]?.takeIf(Double::isFinite) ?: continue
        val xA = (i + 0.5f) * step - offset
        val xB = (i + 1.5f) * step - offset
        fun fill(x1: Float, x2: Float, cur1: Float, cur2: Float, prior1: Float, prior2: Float, warm: Boolean) {
            val p = Path().apply {
                moveTo(x1, cur1)
                lineTo(x2, cur2)
                lineTo(x2, prior2)
                lineTo(x1, prior1)
                close()
            }
            drawPath(p, (if (warm) WarmDeltaColor else CoolDeltaColor).copy(alpha = 0.24f))
        }
        val da = a - oldA
        val db = b - oldB
        if (da * db >= 0) {
            fill(xA, xB, y(a), y(b), y(oldA), y(oldB), da + db >= 0)
        } else {
            val t = (da / (da - db)).coerceIn(0.0, 1.0).toFloat()
            val xC = xA + (xB - xA) * t
            val crossover = a + (b - a) * t
            fill(xA, xC, y(a), y(crossover), y(oldA), y(crossover), da >= 0)
            fill(xC, xB, y(crossover), y(b), y(crossover), y(oldB), db >= 0)
        }
    }
}

private fun DrawScope.drawWindDirectionArrows(hours: List<MeteogramHour>, first: Int, last: Int, offset: Float, step: Float) {
    val arrowY = size.height - 12.dp.toPx()
    val arrowSize = 7.dp.toPx()
    for (i in first..last step 3) {
        val direction = hours[i].windDirectionDeg ?: continue
        val x = (i + 0.5f) * step - offset
        if (x !in -step..size.width + step) continue
        drawWindArrow(x, arrowY, arrowSize, (direction + 180f) % 360f)
    }
}

private fun DrawScope.drawWindArrow(centerX: Float, centerY: Float, sizePx: Float, degrees: Float) {
    rotate(degrees, pivot = Offset(centerX, centerY)) {
        drawLine(WindColor.copy(alpha = 0.75f), Offset(centerX, centerY + sizePx * 0.55f), Offset(centerX, centerY - sizePx * 0.6f), strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
        val tip = Path().apply {
            moveTo(centerX, centerY - sizePx)
            lineTo(centerX - sizePx * 0.42f, centerY - sizePx * 0.34f)
            lineTo(centerX + sizePx * 0.42f, centerY - sizePx * 0.34f)
            close()
        }
        drawPath(tip, WindColor.copy(alpha = 0.85f), style = Fill)
    }
}

private fun DrawScope.drawMarkerLines(hours: List<MeteogramHour>, markers: List<MeteogramMarker>, offset: Float, step: Float) {
    markers.forEach { marker ->
        val x = positionForInstant(hours, marker.instant, step, offset) ?: return@forEach
        if (x in -2.dp.toPx()..size.width + 2.dp.toPx()) {
            val color = if (marker.type == MeteogramMarkerType.SUNRISE) SunriseColor else SunsetColor
            drawLine(color.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
    }
}

private fun DrawScope.drawCurrentTimeLine(hours: List<MeteogramHour>, currentInstant: Instant, offset: Float, step: Float) {
    val x = positionForInstant(hours, currentInstant, step, offset) ?: return
    if (x in -3.dp.toPx()..size.width + 3.dp.toPx()) {
        drawLine(CurrentTimeColor.copy(alpha = 0.95f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

private fun positionForInstant(hours: List<MeteogramHour>, instant: Instant, step: Float, offset: Float): Float? {
    if (hours.isEmpty()) return null
    val first = hours.first().instant
    val hoursDelta = Duration.between(first, instant).toMinutes().toFloat() / 60f
    return (hoursDelta + 0.5f) * step - offset
}

private fun DrawScope.drawSelectionLine(selectedIndex: Int, offset: Float, step: Float) {
    val x = (selectedIndex + 0.5f) * step - offset
    if (x in 0f..size.width) {
        drawLine(SelectionColor.copy(alpha = 0.76f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.25.dp.toPx())
    }
}

internal fun meteogramDailyDomain(ranges: List<MeteogramDailyRange>): MeteogramDomain =
    meteogramDomain(ranges.asSequence().flatMap { sequenceOf(it.highC, it.overnightLowC) }, 3.0)

/** Incomplete boundary days still need a valid range for the hourly pillars. */
internal fun meteogramHourlyDailyDomain(
    hours: List<MeteogramHour>, ranges: List<MeteogramDailyRange>
): MeteogramDomain = meteogramDomain(
    hours.asSequence().map { it.temperatureC } +
        ranges.asSequence().flatMap { sequenceOf(it.highC, it.overnightLowC) },
    3.0
)

@Composable
private fun MeteogramDailyStrip(
    hours: List<MeteogramHour>,
    ranges: List<MeteogramDailyRange>,
    locale: Locale,
    units: WeatherUnits,
    zone: ZoneId,
    step: Float,
    offset: Float,
    selectedIndex: Int,
    markers: List<MeteogramMarker>,
    currentInstant: Instant
) {
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val fontSize = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember(ink, fontSize) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = fontSize
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val domain = remember(ranges, hours) { meteogramHourlyDailyDomain(hours, ranges) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    Canvas(Modifier.fillMaxWidth().height(DailyHeight).testTag(TAG_METEOGRAM_DAILY)) {
        val visible = meteogramViewport(offset, size.width, step, hours.size)
        if (visible.isEmpty()) return@Canvas
        drawTimeGrid(hours, zone, visible.first, visible.last, offset, step, drawMinor = false)
        drawMarkerLines(hours, markers, offset, step)
        drawCurrentTimeLine(hours, currentInstant, offset, step)
        val plotTop = 26.dp.toPx()
        val plotBottom = size.height - 22.dp.toPx()
        val plotSpan = plotBottom - plotTop
        // Hourly pillars ensure the graph is informative even when the center
        // of a 24-hour daily range lies outside the phone viewport.
        for (i in visible) {
            val t = hours[i].temperatureC?.takeIf(Double::isFinite) ?: continue
            val x = (i + 0.5f) * step - offset
            val y = plotTop + plotSpan * (1f - domain.fraction(t))
            drawLine(TemperatureColor.copy(alpha = 0.70f), Offset(x, plotBottom),
                Offset(x, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        val allHighs = ranges.mapNotNull { it.highC }
        val allLows = ranges.mapNotNull { it.overnightLowC }
        val warmExtreme = allHighs.maxOrNull()
        val coldExtreme = allLows.minOrNull()
        ranges.forEach { entry ->
            if (entry.firstHour + entry.hourCount <= visible.first || entry.firstHour > visible.last) return@forEach
            val x = entry.firstHour * step - offset
            val w = entry.hourCount * step
            if (x + w < 0 || x > size.width) return@forEach
            val center = x + w / 2f
            drawContext.canvas.nativeCanvas.drawText(entry.date.format(dayFormatter), center, 16.dp.toPx(), paint)
            val high = entry.highC?.takeIf(Double::isFinite)
            val low = entry.overnightLowC?.takeIf(Double::isFinite)
            if (high == null || low == null) {
                drawContext.canvas.nativeCanvas.drawText("—", center, 68.dp.toPx(), paint)
                return@forEach
            }
            val yHigh = plotTop + plotSpan * (1f - domain.fraction(high))
            val yLow = plotTop + plotSpan * (1f - domain.fraction(low))
            val isWarm = warmExtreme != null && high >= warmExtreme - domain.span * 0.10
            val isCold = coldExtreme != null && low <= coldExtreme + domain.span * 0.10
            val barTop = yHigh
            val barHeight = (yLow - yHigh).coerceAtLeast(3.dp.toPx())
            if (isWarm && isCold) {
                drawRoundRect(WarmDeltaColor.copy(alpha = 0.85f), Offset(center - 8.dp.toPx(), barTop), Size(16.dp.toPx(), barHeight / 2), CornerRadius(4.dp.toPx()))
                drawRoundRect(CoolDeltaColor.copy(alpha = 0.85f), Offset(center - 8.dp.toPx(), barTop + barHeight / 2), Size(16.dp.toPx(), barHeight / 2), CornerRadius(4.dp.toPx()))
            } else {
                val barColor = if (isWarm) WarmDeltaColor else if (isCold) CoolDeltaColor else CloudColor
                drawRoundRect(barColor.copy(alpha = 0.82f), Offset(center - 8.dp.toPx(), barTop), Size(16.dp.toPx(), barHeight), CornerRadius(5.dp.toPx()))
            }
            drawContext.canvas.nativeCanvas.drawText(units.temp(high), center - 23.dp.toPx(), (yHigh - 3.dp.toPx()).coerceAtLeast(20.dp.toPx()), paint)
            drawContext.canvas.nativeCanvas.drawText(units.temp(low), center + 23.dp.toPx(), (yLow + 13.dp.toPx()).coerceAtMost(size.height - 2.dp.toPx()), paint)
        }
        drawSelectionLine(selectedIndex, offset, step)
    }
}

@Composable
private fun MeteogramSelectedValues(hour: MeteogramHour, cityZone: ZoneId, units: WeatherUnits, locale: Locale) {
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", locale) }
    val values = listOf(
        "${stringResource(R.string.meteogram_temperature)} ${units.temp(hour.temperatureC)}",
        "${stringResource(R.string.apparent_temperature_widget_prefix)} ${units.temp(hour.apparentC)}",
        "${stringResource(R.string.metric_dew_point)} ${units.temp(hour.dewC)}",
        "${stringResource(R.string.meteogram_humidity)} ${hour.humidityPercent?.let { "$it%" } ?: "—"}",
        "${stringResource(R.string.meteogram_rain)} ${units.rain(hour.rainMm)}",
        "${stringResource(R.string.meteogram_wind)} ${units.speed(hour.windKmh)}",
        "${stringResource(R.string.meteogram_pressure)} ${hour.pressureHpa?.let { units.number(units.pressure(it), 1) } ?: "—"}"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp).testTag(TAG_METEOGRAM_SELECTED),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                stringResource(R.string.meteogram_selected, hour.instant.atZone(cityZone).format(formatter)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(values.joinToString("   ·   "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
