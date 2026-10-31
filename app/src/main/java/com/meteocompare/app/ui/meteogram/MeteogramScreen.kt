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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
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
private val AxisWidth = 54.dp
private val WeatherHeight = 244.dp
private val TemperatureHeight = 228.dp
private val HumidityHeight = 188.dp
private val PressureHeight = 188.dp
private val WindHeight = 218.dp
private val TimeHeight = 49.dp
private val CombinedHeight = 454.dp

internal const val TAG_METEOGRAM_CONTENT = "meteogram_content"
internal const val TAG_METEOGRAM_TIME = "meteogram_time"
internal const val TAG_METEOGRAM_WEATHER = "meteogram_weather"
internal const val TAG_METEOGRAM_TEMPERATURE = "meteogram_temperature"
internal const val TAG_METEOGRAM_HUMIDITY = "meteogram_humidity"
internal const val TAG_METEOGRAM_PRESSURE = "meteogram_pressure"
internal const val TAG_METEOGRAM_WIND = "meteogram_wind"
internal const val TAG_METEOGRAM_CLOUD_RAIN_LEGEND = "meteogram_cloud_rain_legend"
internal const val TAG_METEOGRAM_SELECTED = "meteogram_selected"
internal const val TAG_METEOGRAM_CARD_SELECTED_PREFIX = "meteogram_card_selected_"
internal const val TAG_METEOGRAM_COMBINED = "meteogram_combined_plot"
internal const val TAG_METEOGRAM_ACTIVE_AXIS = "meteogram_active_axis"
internal const val TAG_METEOGRAM_SCALE_PICKER = "meteogram_scale_picker"
internal const val TAG_METEOGRAM_SCALE_OPTION_PREFIX = "meteogram_scale_option_"
internal const val TAG_METEOGRAM_LAYOUT_COMBINED = "meteogram_layout_combined"
internal const val TAG_METEOGRAM_CARD_PREFIX = "meteogram_card_"
internal const val TAG_METEOGRAM_COMPARE = "meteogram_compare"
internal const val TAG_METEOGRAM_LAYOUT = "meteogram_layout"

private val RainColor = Color(0xFF0A67B3)
private val CloudColor = Color(0xFF7D8894)
private val CloudLowColor = Color(0xFF5F6F85)
private val CloudMidColor = Color(0xFF8593A3)
private val CloudHighColor = Color(0xFFB2BFCA)
private val WindColor = Color(0xFF1C8A49)
private val GustColor = Color(0xFF7BC89A)
private val TemperatureColor = Color(0xFF1776B8)
private val ApparentColor = Color(0xFFA34BC3)
private val DewColor = Color(0xFF1B98AA)
private val HumidityColor = Color(0xFFD14E8B)
private val PressureColor = Color(0xFF6D58C7)
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
    val cloudLowPercent: Int? = null,
    val cloudMidPercent: Int? = null,
    val cloudHighPercent: Int? = null,
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

internal enum class MeteogramKind { EVERYDAY, TEMPERATURE, HUMIDITY, PRESSURE, WIND }
internal enum class MeteogramLayoutMode { SEPARATE, COMBINED }
/** The selected ruler changes, never the independent curve domains. */
internal enum class MeteogramAxisChoice { TEMPERATURE, CLOUDS, RAIN, WIND, HUMIDITY, PRESSURE }
private enum class MeteogramMarkerType { SUNRISE, SUNSET }
private data class MeteogramMarker(val instant: Instant, val type: MeteogramMarkerType)
private data class LegendEntry(
    val color: Color, val label: String, val line: Boolean = false,
    val emphasized: Boolean = false
)

// Humidity is grouped with temperature, and pressure with Everyday Weather.
// The dedicated kinds remain available for model comparison.
internal val MeteogramPresets = listOf(
    MeteogramKind.EVERYDAY,
    MeteogramKind.TEMPERATURE,
    MeteogramKind.WIND
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

/** Meteorological wind direction is where the wind COMES FROM, not where it travels.
 *  Western compass points use O (ouest/oeste/ovest) in FR/ES/IT and W elsewhere. */
internal fun meteogramWindCardinal(degrees: Int?, language: String): String? {
    val direction = degrees?.takeIf { it in 0..360 } ?: return null
    val west = language.lowercase(Locale.ROOT) in setOf("fr", "es", "it")
    val sectors = if (west) {
        listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    } else {
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    }
    return sectors[((direction % 360 + 22) / 45) % 8]
}

/** Same day/night backdrop in EVERY chart, independently of the meteorogram preset. */
internal fun meteogramDayNightAlpha(daylight: Boolean): Float = if (daylight) 0.16f else 0.12f

internal fun meteogramViewport(startPx: Float, widthPx: Float, hourPx: Float, count: Int): IntRange {
    if (count <= 0 || hourPx <= 0f) return IntRange.EMPTY
    val left = (floor(startPx / hourPx).toInt() - 2).coerceIn(0, count - 1)
    val right = (ceil((startPx + widthPx.coerceAtLeast(0f)) / hourPx).toInt() + 2)
        .coerceIn(left, count - 1)
    return left..right
}

private fun centralPercent(values: List<Int?>): Int? = values.mapNotNull { it?.takeIf { v -> v in 0..100 } }
    .sorted()
    .let { sorted ->
        when {
            sorted.isEmpty() -> null
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0).toInt()
        }
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
        val rows = state.modelValuesByInstant[instant].orEmpty()
        MeteogramHour(
            instant = instant,
            daylight = daylight,
            temperatureC = if (selectedModel == null) point.temperatureC else model?.temperatureC,
            apparentC = if (selectedModel == null) point.apparentTemperatureC else model?.apparentTemperatureC,
            dewC = if (selectedModel == null) point.dewPointC else model?.dewPointC,
            cloudLowPercent = if (selectedModel == null) centralPercent(rows.map { it.cloudLowPercent }) else model?.cloudLowPercent,
            cloudMidPercent = if (selectedModel == null) centralPercent(rows.map { it.cloudMidPercent }) else model?.cloudMidPercent,
            cloudHighPercent = if (selectedModel == null) centralPercent(rows.map { it.cloudHighPercent }) else model?.cloudHighPercent,
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
    // When a refresh reduces the forecast horizon, avoid retaining an offscreen
    // position from the previous timeline. Each Canvas computes its own viewport.
    LaunchedEffect(hours.size, hourPx, widthPx) {
        scrollPx = scrollPx.coerceIn(0f, (hours.size * hourPx - widthPx).coerceAtLeast(0f))
    }
    val temperatureDomain = remember(hours) {
        meteogramDomain(hours.asSequence().flatMap { sequenceOf(it.temperatureC, it.apparentC, it.dewC) }, 3.0)
    }
    val rainDomain = remember(hours) {
        meteogramDomain(hours.asSequence().map { it.rainMm }, 0.5, includeZero = true)
    }
    val windDomain = remember(hours) {
        meteogramDomain(hours.asSequence().flatMap { sequenceOf(it.windKmh, it.gustKmh) }, 5.0, includeZero = true)
    }
    val humidityDomain = remember(hours) {
        meteogramDomain(hours.asSequence().map { it.humidityPercent?.toDouble() }, 15.0, bounds = 0.0..100.0)
    }
    val pressureDomain = remember(hours) {
        meteogramDomain(hours.asSequence().map { it.pressureHpa }, 3.0)
    }
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
                MeteogramKind.entries.forEach { kind ->
                    FilterChip(selected = compareKind == kind, onClick = { compareKind = kind }, label = { Text(meteogramKindLabel(kind)) })
                }
            }
            val modelHoursByName = remember(state.points, state.modelValuesByInstant, state.solarByDate, models) {
                models.associateWith { name -> buildMeteogramHours(state, name) }
            }
            // Stable shared domains across the entire forecast: scroll never rescales
            // comparison panels, and the model curves remain directly comparable.
            val compareHours = remember(modelHoursByName) {
                modelHoursByName.values.flatten()
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
            val compareHumidityDomain = remember(compareHours) {
                meteogramDomain(compareHours.asSequence().map { it.humidityPercent?.toDouble() }, 15.0, bounds = 0.0..100.0)
            }
            val comparePressureDomain = remember(compareHours) {
                meteogramDomain(compareHours.asSequence().map { it.pressureHpa }, 3.0)
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
                    selectedIndex = index,
                    scroll = scroll,
                    onSelect = onSelect,
                    onWidthChange = { widthPx = it },
                    domain = compareDomain,
                    rainDomain = compareRainDomain,
                    humidityDomain = compareHumidityDomain,
                    pressureDomain = comparePressureDomain,
                    priorTemperatures = remember(modelHours) { modelHours.associate { it.instant to it.temperatureC } },
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
                accessibility = chartAccessibility,
                markers = markers,
                currentInstant = currentInstant
            )
        } else {
            MeteogramPresets.forEach { kind ->
                val domain = when (kind) {
                    MeteogramKind.EVERYDAY -> rainDomain
                    MeteogramKind.TEMPERATURE -> temperatureDomain
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
                    selectedIndex = index,
                    scroll = scroll,
                    onSelect = onSelect,
                    onWidthChange = { widthPx = it },
                    domain = domain,
                    rainDomain = rainDomain,
                    humidityDomain = humidityDomain,
                    pressureDomain = pressureDomain,
                    priorTemperatures = priorTemperatures,
                    accessibility = chartAccessibility,
                    markers = markers,
                    currentInstant = currentInstant
                )
            }
        }
        // Separate/compare cards already show the selected hour next to each graph.
        // Only the single combined chart needs the full metric overview below it.
        if (!comparing && layoutMode == MeteogramLayoutMode.COMBINED) {
            MeteogramSelectedValues(hours[index], zone, units, locale)
        }
    }
}

@Composable
private fun meteogramKindLabel(kind: MeteogramKind): String = when (kind) {
    MeteogramKind.EVERYDAY -> stringResource(R.string.meteogram_weather)
    MeteogramKind.TEMPERATURE -> stringResource(R.string.meteogram_temperature)
    MeteogramKind.HUMIDITY -> stringResource(R.string.meteogram_humidity)
    MeteogramKind.PRESSURE -> stringResource(R.string.meteogram_pressure)
    MeteogramKind.WIND -> stringResource(R.string.meteogram_wind)
}

internal fun meteogramMinimumSpan(kind: MeteogramKind): Double = when (kind) {
    MeteogramKind.EVERYDAY -> 0.5
    MeteogramKind.TEMPERATURE -> 3.0
    MeteogramKind.HUMIDITY -> 15.0
    MeteogramKind.PRESSURE -> 3.0
    MeteogramKind.WIND -> 5.0
}

internal fun meteogramKindValues(kind: MeteogramKind, hours: List<MeteogramHour>): Sequence<Double?> =
    hours.asSequence().flatMap { hour ->
        when (kind) {
            MeteogramKind.EVERYDAY -> sequenceOf(hour.rainMm)
            MeteogramKind.TEMPERATURE -> sequenceOf(hour.temperatureC, hour.apparentC, hour.dewC)
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
    selectedIndex: Int,
    scroll: androidx.compose.foundation.gestures.ScrollableState,
    onSelect: (Int) -> Unit,
    onWidthChange: (Int) -> Unit,
    domain: MeteogramDomain,
    rainDomain: MeteogramDomain,
    humidityDomain: MeteogramDomain,
    pressureDomain: MeteogramDomain,
    priorTemperatures: Map<Instant, Double?>,
    accessibility: String,
    markers: List<MeteogramMarker>,
    currentInstant: Instant
) {
    val cardTag = TAG_METEOGRAM_CARD_PREFIX + kind.name.lowercase()
    val height = cardHeight(kind)
    val selected = hours.getOrNull(selectedIndex)
    // Keep axis widths stable during horizontal scrolling, even when a series
    // has temporary missing values in the currently visible hours.
    val hasPressure = remember(hours) { hours.any { it.pressureHpa?.isFinite() == true } }
    val hasHumidity = remember(hours) { hours.any { it.humidityPercent?.let { pct -> pct in 0..100 } == true } }
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
            if (kind == MeteogramKind.EVERYDAY) {
                MeteogramCloudRainLegend(units)
                MeteogramCloudLayersHint(hours)
            } else {
                MeteogramChartLegend(kind, units)
            }
            MeteogramGraphFrame {
                Column(
                    Modifier.weight(1f).onSizeChanged { onWidthChange(it.width) }
                        .scrollable(scroll, Orientation.Horizontal)
                ) {
                    MeteogramTimeAxis(hours, zone, locale, timeFormatter, step, offset, selectedIndex, markers, currentInstant)
                    MeteogramPlot(hours, height, step, offset, selectedIndex, meteogramKindTag(kind),
                        onSelect, markers, currentInstant) { first, last, shift, hourStep ->
                        drawChartFrame(hours, zone, first, last, shift, hourStep)
                        when (kind) {
                            MeteogramKind.EVERYDAY -> {
                                drawCloudLayerBands(hours, first, last, shift, hourStep)
                                drawRainBars(hours, first, last, shift, hourStep, rainDomain)
                                drawMetricCurve(hours, first, last, shift, hourStep, pressureDomain, PressureColor) { it.pressureHpa }
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
                                drawWindDirectionArrows(hours, first, last, shift, hourStep,
                                    cardinalLanguage = locale.language)
                            }
                        }
                    }
                }
                MeteogramValueAxis(kind, height, domain, units,
                    if (kind == MeteogramKind.EVERYDAY || kind == MeteogramKind.TEMPERATURE) 50.dp else AxisWidth)
                when (kind) {
                    MeteogramKind.EVERYDAY -> {
                        if (hasPressure) MeteogramValueAxis(
                            MeteogramKind.PRESSURE, height, pressureDomain, units, 50.dp)
                    }
                    MeteogramKind.TEMPERATURE -> {
                        if (hasHumidity) MeteogramValueAxis(
                            MeteogramKind.HUMIDITY, height, humidityDomain, units, 50.dp)
                    }
                    else -> Unit
                }
            }
            if (selected != null) {
                MeteogramCardSelectedValues(kind, selected, zone, timeFormatter, units, locale)
            }
        }
    }
}

/** Shared full-width timeline backing for separate, compare and combined modes.
 *  The plot and ruler headers stay on the same vertical baseline.
 */
@Composable
private fun MeteogramGraphFrame(content: @Composable RowScope.() -> Unit) {
    val timeStrip = MaterialTheme.colorScheme.surfaceContainerHighest
    val timeOutline = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).drawBehind {
        val bottom = TimeHeight.toPx()
        drawRect(timeStrip.copy(alpha = 0.75f), size = Size(size.width, bottom))
        drawLine(timeOutline.copy(alpha = 0.65f), Offset(0f, bottom), Offset(size.width, bottom))
    }, content = content)
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
    accessibility: String,
    markers: List<MeteogramMarker>,
    currentInstant: Instant
) {
    var axisChoice by rememberSaveable { mutableStateOf(MeteogramAxisChoice.TEMPERATURE) }
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
            MeteogramCombinedLegend(units)
            MeteogramCloudLayersHint(hours)
            MeteogramScalePicker(axisChoice) { axisChoice = it }
            MeteogramGraphFrame {
                Column(Modifier.weight(1f).onSizeChanged { onWidthChange(it.width) }
                    .scrollable(scroll, Orientation.Horizontal)) {
                    MeteogramTimeAxis(hours, zone, locale, timeFormatter, step,
                        offset, selectedIndex, markers, currentInstant)
                    MeteogramPlot(hours, CombinedHeight, step, offset, selectedIndex,
                        TAG_METEOGRAM_COMBINED, onSelect, markers, currentInstant) { first, last, shift, hourStep ->
                        drawChartFrame(hours, zone, first, last, shift, hourStep)
                        drawCloudLayerBands(hours, first, last, shift, hourStep, topFraction = 0.38f, alpha = 0.68f)
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
                        drawWindDirectionArrows(hours, first, last, shift, hourStep)
                    }
                }
                MeteogramCombinedSelectedAxis(
                    axisChoice, units, temperatureDomain, rainDomain,
                    windDomain, humidityDomain, pressureDomain
                )
            }
        }
    }
}

@Composable
private fun MeteogramCloudLayersHint(hours: List<MeteogramHour>) {
    val available = remember(hours) {
        hours.any { it.cloudLowPercent != null || it.cloudMidPercent != null || it.cloudHighPercent != null }
    }
    // No redundant description when the three layers are already visible.
    // Keep the warning for older caches/models without layer data.
    if (!available) {
        Text(
            stringResource(R.string.meteogram_cloud_layers_unavailable),
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A compact choice of the ONE ruler rendered alongside the shared Canvas.
 *  All plotted series keep their own domain regardless of the selected ruler.
 *  Cloud cover is special: three separate 0–100% rulers occupy the three cloud lanes. */
@Composable
private fun meteogramChoiceName(choice: MeteogramAxisChoice): String = when (choice) {
    MeteogramAxisChoice.TEMPERATURE -> stringResource(R.string.meteogram_temperature)
    MeteogramAxisChoice.CLOUDS -> stringResource(R.string.meteogram_cloud_picker)
    MeteogramAxisChoice.RAIN -> stringResource(R.string.meteogram_rain)
    MeteogramAxisChoice.WIND -> stringResource(R.string.meteogram_wind)
    MeteogramAxisChoice.HUMIDITY -> stringResource(R.string.meteogram_humidity)
    MeteogramAxisChoice.PRESSURE -> stringResource(R.string.meteogram_pressure)
}

private fun meteogramChoiceColor(choice: MeteogramAxisChoice): Color = when (choice) {
    MeteogramAxisChoice.TEMPERATURE -> TemperatureColor
    MeteogramAxisChoice.CLOUDS -> CloudMidColor
    MeteogramAxisChoice.RAIN -> RainColor
    MeteogramAxisChoice.WIND -> WindColor
    MeteogramAxisChoice.HUMIDITY -> HumidityColor
    MeteogramAxisChoice.PRESSURE -> PressureColor
}

@Composable
private fun MeteogramScalePicker(
    selected: MeteogramAxisChoice,
    onSelect: (MeteogramAxisChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.meteogram_choose_scale),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag(TAG_METEOGRAM_SCALE_PICKER)
            ) {
                Box(Modifier.size(9.dp).background(meteogramChoiceColor(selected), CircleShape))
                Text("  ${meteogramChoiceName(selected)}  ▾", fontWeight = FontWeight.SemiBold)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MeteogramAxisChoice.entries.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(9.dp).background(meteogramChoiceColor(choice), CircleShape))
                                Text(meteogramChoiceName(choice))
                            }
                        },
                        onClick = { onSelect(choice); expanded = false },
                        modifier = Modifier.testTag(TAG_METEOGRAM_SCALE_OPTION_PREFIX + choice.name.lowercase())
                    )
                }
            }
        }
    }
}

@Composable
private fun meteogramAxisShortLabel(choice: MeteogramAxisChoice): String = when (choice) {
    MeteogramAxisChoice.TEMPERATURE -> stringResource(R.string.meteogram_axis_short_temperature)
    MeteogramAxisChoice.CLOUDS -> stringResource(R.string.meteogram_axis_short_clouds)
    MeteogramAxisChoice.RAIN -> stringResource(R.string.meteogram_axis_short_rain)
    MeteogramAxisChoice.WIND -> stringResource(R.string.meteogram_axis_short_wind)
    MeteogramAxisChoice.HUMIDITY -> stringResource(R.string.meteogram_axis_short_humidity)
    MeteogramAxisChoice.PRESSURE -> stringResource(R.string.meteogram_axis_short_pressure)
}

@Composable
private fun MeteogramCombinedSelectedAxis(
    choice: MeteogramAxisChoice,
    units: WeatherUnits,
    temperature: MeteogramDomain,
    rain: MeteogramDomain,
    wind: MeteogramDomain,
    humidity: MeteogramDomain,
    pressure: MeteogramDomain
) {
    val domain = when (choice) {
        MeteogramAxisChoice.TEMPERATURE -> temperature
        MeteogramAxisChoice.CLOUDS -> MeteogramDomain(0.0, 100.0)
        MeteogramAxisChoice.RAIN -> rain
        MeteogramAxisChoice.WIND -> wind
        MeteogramAxisChoice.HUMIDITY -> humidity
        MeteogramAxisChoice.PRESSURE -> pressure
    }
    val unit = when (choice) {
        MeteogramAxisChoice.TEMPERATURE -> units.temperatureUnit
        MeteogramAxisChoice.CLOUDS, MeteogramAxisChoice.HUMIDITY -> "%"
        MeteogramAxisChoice.RAIN -> "${units.precipitationUnit}/h"
        MeteogramAxisChoice.WIND -> units.windUnit
        MeteogramAxisChoice.PRESSURE -> if (units.imperial) "inHg" else "hPa"
    }
    fun label(value: Double): String = when (choice) {
        MeteogramAxisChoice.TEMPERATURE -> units.number(units.temperature(value))
        MeteogramAxisChoice.CLOUDS, MeteogramAxisChoice.HUMIDITY -> units.number(value)
        MeteogramAxisChoice.RAIN -> units.number(units.precipitation(value), units.precipitationDigits)
        MeteogramAxisChoice.WIND -> units.number(units.wind(value))
        MeteogramAxisChoice.PRESSURE -> units.number(units.pressure(value), if (units.imperial) 2 else 0)
    }
    val values = listOf(domain.max, (domain.min + domain.max) / 2.0, domain.min).map(::label)
    // Abbreviations follow the active app language (Wind = W in English,
    // Vent = V in French, etc.), independently of the selected weather units.
    val axisShortTitle = meteogramAxisShortLabel(choice)
    Box(Modifier.testTag(TAG_METEOGRAM_ACTIVE_AXIS)
        .semantics { contentDescription = choice.name.lowercase() }) {
        MeteogramAxisRuler(
            title = axisShortTitle, unit = unit,
            color = meteogramChoiceColor(choice), labels = values,
            height = CombinedHeight, axisWidth = AxisWidth,
            cloudLayers = choice == MeteogramAxisChoice.CLOUDS
        )
    }
}

private fun cardHeight(kind: MeteogramKind): Dp = when (kind) {
    MeteogramKind.EVERYDAY -> WeatherHeight
    MeteogramKind.TEMPERATURE -> TemperatureHeight
    MeteogramKind.HUMIDITY -> HumidityHeight
    MeteogramKind.PRESSURE -> PressureHeight
    MeteogramKind.WIND -> WindHeight
}

private fun meteogramKindTag(kind: MeteogramKind): String = when (kind) {
    MeteogramKind.EVERYDAY -> TAG_METEOGRAM_WEATHER
    MeteogramKind.TEMPERATURE -> TAG_METEOGRAM_TEMPERATURE
    MeteogramKind.HUMIDITY -> TAG_METEOGRAM_HUMIDITY
    MeteogramKind.PRESSURE -> TAG_METEOGRAM_PRESSURE
    MeteogramKind.WIND -> TAG_METEOGRAM_WIND
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeteogramChartLegend(kind: MeteogramKind, units: WeatherUnits) {
    val entries = when (kind) {
        MeteogramKind.EVERYDAY -> emptyList() // Displayed by MeteogramCloudRainLegend.
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
            LegendEntry(GustColor, stringResource(R.string.graphic_view_gust_short), line = true),
            LegendEntry(WindColor, stringResource(R.string.meteogram_wind_origin))
        )

    }
    MeteogramLegend(entries)
}

/** The cloud / rain card highlights the three cloud strata and rain separately.
 *  Unlike the crowded generic legend, these keys never disappear into solar or wind metadata. */
@Composable
private fun MeteogramCloudRainLegend(units: WeatherUnits) {
    Column(Modifier.fillMaxWidth().testTag(TAG_METEOGRAM_CLOUD_RAIN_LEGEND)) {
        Text(
            "${stringResource(R.string.meteogram_cloud_picker)} (%)",
            Modifier.padding(start = 12.dp, end = 12.dp, top = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        MeteogramLegend(listOf(
            LegendEntry(CloudLowColor, stringResource(R.string.meteogram_cloud_low)),
            LegendEntry(CloudMidColor, stringResource(R.string.meteogram_cloud_mid)),
            LegendEntry(CloudHighColor, stringResource(R.string.meteogram_cloud_high))
        ))
        MeteogramLegend(listOf(
            LegendEntry(RainColor, "${stringResource(R.string.meteogram_rain)} (${units.precipitationUnit}/h)", emphasized = true),
            LegendEntry(PressureColor, stringResource(R.string.meteogram_pressure), line = true),
            LegendEntry(DayBackgroundColor, stringResource(R.string.meteogram_sun)),
            LegendEntry(SunriseColor, stringResource(R.string.meteogram_sunrise), line = true),
            LegendEntry(SunsetColor, stringResource(R.string.meteogram_sunset), line = true)
        ))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeteogramCombinedLegend(units: WeatherUnits) {
    MeteogramLegend(
        listOf(
            LegendEntry(DayBackgroundColor, stringResource(R.string.meteogram_sun)),
            LegendEntry(CloudLowColor, stringResource(R.string.meteogram_cloud_low)),
            LegendEntry(CloudMidColor, stringResource(R.string.meteogram_cloud_mid)),
            LegendEntry(CloudHighColor, stringResource(R.string.meteogram_cloud_high)),
            LegendEntry(RainColor, "${stringResource(R.string.meteogram_rain)} (${units.precipitationUnit}/h)"),
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
            Box(Modifier.width(14.dp).height(3.dp)
                .background(entry.color, RoundedCornerShape(50)))
        } else {
            Box(Modifier.size(if (entry.emphasized) 12.dp else 9.dp)
                .background(entry.color, CircleShape))
        }
        Text(
            entry.label,
            style = if (entry.emphasized) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = if (entry.emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (entry.emphasized) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

private fun meteogramAxisColor(kind: MeteogramKind): Color = when (kind) {
    MeteogramKind.EVERYDAY -> RainColor
    MeteogramKind.WIND -> WindColor
    MeteogramKind.TEMPERATURE -> TemperatureColor
    MeteogramKind.HUMIDITY -> HumidityColor
    MeteogramKind.PRESSURE -> PressureColor
}

@Composable
private fun MeteogramValueAxis(
    kind: MeteogramKind,
    height: Dp,
    domain: MeteogramDomain,
    units: WeatherUnits,
    axisWidth: Dp = AxisWidth
) {
    val valueDomain = domain
    val values = listOf(valueDomain.max, (valueDomain.min + valueDomain.max) / 2, valueDomain.min)
    val labels = when (kind) {
        MeteogramKind.TEMPERATURE -> values.map { units.number(units.temperature(it)) }
        MeteogramKind.WIND -> values.map { units.number(units.wind(it)) }
        MeteogramKind.EVERYDAY -> values.map { units.number(units.precipitation(it), units.precipitationDigits) }
        MeteogramKind.HUMIDITY -> values.map { units.number(it) }
        MeteogramKind.PRESSURE -> values.map { units.number(units.pressure(it), if (units.imperial) 2 else 0) }
    }
    val unitLabel = when (kind) {
        MeteogramKind.EVERYDAY -> "${units.precipitationUnit}/h"
        MeteogramKind.WIND -> units.windUnit
        MeteogramKind.TEMPERATURE -> units.temperatureUnit
        MeteogramKind.HUMIDITY -> "%"
        MeteogramKind.PRESSURE -> if (units.imperial) "inHg" else "hPa"
    }
    val shortName = when (kind) {
        MeteogramKind.EVERYDAY -> meteogramAxisShortLabel(MeteogramAxisChoice.RAIN)
        MeteogramKind.WIND -> meteogramAxisShortLabel(MeteogramAxisChoice.WIND)
        MeteogramKind.TEMPERATURE -> meteogramAxisShortLabel(MeteogramAxisChoice.TEMPERATURE)
        MeteogramKind.HUMIDITY -> meteogramAxisShortLabel(MeteogramAxisChoice.HUMIDITY)
        MeteogramKind.PRESSURE -> meteogramAxisShortLabel(MeteogramAxisChoice.PRESSURE)
    }
    MeteogramAxisRuler(
        title = shortName,
        unit = unitLabel,
        color = meteogramAxisColor(kind),
        labels = labels,
        height = height,
        axisWidth = axisWidth
    )
}

/** A narrow scale strip: unit only once, numeric ticks high contrast.
 *  The cloud bands each have their own percentage coordinate within the top
 *  38% of the combined plot; a full-height 0..100 ruler would be misleading. */
@Composable
private fun MeteogramAxisRuler(
    title: String,
    unit: String,
    color: Color,
    labels: List<String>,
    height: Dp,
    axisWidth: Dp,
    cloudLayers: Boolean = false
) {
    // Keep the compact ruler, but restore a translucent metric-colored surface.
    // The neutral opaque gray introduced in the previous patch reduced visual
    // association with the curve, particularly for wind vs. pressure.
    val rulerShape = RoundedCornerShape(7.dp)
    Column(
        Modifier.width(axisWidth).padding(start = 2.dp)
            .background(color.copy(alpha = 0.105f), rulerShape)
            .border(1.dp, color.copy(alpha = 0.38f), rulerShape)
            .padding(horizontal = 3.dp)
    ) {
        Column(
            Modifier.height(TimeHeight).fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(5.dp).background(color, CircleShape))
                Text(unit, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1)
            }
        }
        if (cloudLayers) {
            val laneHeight = height * (0.38f / 3f)
            listOf(CloudHighColor, CloudMidColor, CloudLowColor).forEach { layerColor ->
                Column(Modifier.height(laneHeight).fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(Modifier.size(4.dp).background(layerColor, CircleShape))
                        Text("100", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text("0", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(height * 0.62f))
        } else {
            Column(Modifier.height(height).fillMaxWidth().padding(vertical = 3.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End) {
                labels.forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
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
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val accentInk = MaterialTheme.colorScheme.onSurface.toArgb()
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val clockPreference = LocalClockFormatPreference.current
    val solarClockFormatter = remember(locale, clockPreference) {
        WeatherTimeFormat.clock(locale, clockPreference)
    }
    val fontSize = with(LocalDensity.current) { 11.sp.toPx() }
    val smallFontSize = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember(ink, fontSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = fontSize } }
    val markerPaint = remember(accentInk, smallFontSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = accentInk; textSize = smallFontSize } }
    Canvas(
        Modifier.fillMaxWidth().height(TimeHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            .testTag(TAG_METEOGRAM_TIME)
    ) {
        val range = meteogramViewport(offset, size.width, step, hours.size)
        if (range.isEmpty()) return@Canvas
        drawTimeGrid(hours, zone, range.first, range.last, offset, step, drawMinor = true)
        for (i in range) {
            val dateTime = hours[i].instant.atZone(zone)
            val x = (i + 0.5f) * step - offset
            if (dateTime.hour == 0 || i == 0) {
                val label = dateTime.format(dayFormatter)
                // Keep text fully inside the rectangular timeline viewport.
                val labelX = x + 4.dp.toPx()
                if (labelX >= 7.dp.toPx() && labelX + paint.measureText(label) <= size.width - 7.dp.toPx()) {
                    drawContext.canvas.nativeCanvas.drawText(label, labelX, 13.dp.toPx(), paint)
                }
            }
            if (dateTime.hour % 3 == 0) {
                val label = dateTime.format(hourFormatter)
                val halfWidth = paint.measureText(label) / 2f
                if (x - halfWidth >= 7.dp.toPx() && x + halfWidth <= size.width - 7.dp.toPx()) {
                    drawContext.canvas.nativeCanvas.drawText(label, x - halfWidth,
                        size.height - 3.dp.toPx(), paint)
                }
            }
        }
        drawMarkerLines(hours, markers, offset, step)
        // Named time badges instead of barely visible sunrise/sunset hairlines.
        // Markers remain tied to their actual instant even across DST changes.
        var lastBadgeRight = -Float.MAX_VALUE
        markers.forEach { marker ->
            val x = positionForInstant(hours, marker.instant, step, offset) ?: return@forEach
            if (x < -8.dp.toPx() || x > size.width + 8.dp.toPx()) return@forEach
            val accent = if (marker.type == MeteogramMarkerType.SUNRISE) SunriseColor else SunsetColor
            val symbol = if (marker.type == MeteogramMarkerType.SUNRISE) "↑" else "↓"
            // Sunrise/sunset are minute-precision events, unlike the hour-only timeline ticks.
            val label = "$symbol ${marker.instant.atZone(zone).format(solarClockFormatter)}"
            val padX = 8.dp.toPx()
            val width = markerPaint.measureText(label) + 2f * padX
            val margin = 3.dp.toPx()
            if (width > size.width - 2f * margin) return@forEach
            val left = (x - width / 2f).coerceIn(margin, size.width - width - margin)
            if (left < lastBadgeRight + 4.dp.toPx()) return@forEach
            val top = 15.dp.toPx()
            val badgeHeight = 19.dp.toPx()
            drawRoundRect(accent.copy(alpha = 0.22f), Offset(left, top),
                Size(width, badgeHeight), CornerRadius(5.dp.toPx()))
            drawRoundRect(accent.copy(alpha = 0.94f), Offset(left, top),
                Size(width, badgeHeight), CornerRadius(5.dp.toPx()),
                style = Stroke(1.2.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(label, left + padX,
                top + 13.dp.toPx(), markerPaint)
            lastBadgeRight = left + width
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
    selectedIndex: Int,
    tag: String,
    onSelect: (Int) -> Unit,
    markers: List<MeteogramMarker>,
    currentInstant: Instant,
    drawing: DrawScope.(first: Int, last: Int, offset: Float, step: Float) -> Unit
) {
    val currentOffset by rememberUpdatedState(offset)
    Canvas(
        Modifier.fillMaxWidth().height(height).testTag(tag)
            .pointerInput(hours.size, step) {
                detectTapGestures { tap -> onSelect(floor((tap.x + currentOffset) / step).toInt()) }
            }
    ) {
        val range = meteogramViewport(offset, size.width, step, hours.size)
        if (range.isEmpty()) return@Canvas
        drawLine(CloudColor.copy(alpha = 0.12f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
        drawing(range.first, range.last, offset, step)
        // Solar/now lines must stay above rain bars, clouds and metric curves.
        drawMarkerLines(hours, markers, offset, step)
        drawCurrentTimeLine(hours, currentInstant, offset, step)
        drawSelectionLine(selectedIndex, offset, step)
    }
}

private fun DrawScope.drawChartFrame(
    hours: List<MeteogramHour>,
    zone: ZoneId,
    first: Int,
    last: Int,
    offset: Float,
    step: Float
) {
    drawMeteogramBackground(hours, first, last, offset, step)
    drawTimeGrid(hours, zone, first, last, offset, step, drawMinor = true)
}

private fun DrawScope.drawMeteogramBackground(
    hours: List<MeteogramHour>,
    first: Int,
    last: Int,
    offset: Float,
    step: Float
) {
    for (i in first..last) {
        val x = i * step - offset
        val color = (if (hours[i].daylight) DayBackgroundColor else NightBackgroundColor)
            .copy(alpha = meteogramDayNightAlpha(hours[i].daylight))
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
            hour == 0 -> drawLine(DayBoundaryColor.copy(alpha = 0.92f), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            drawMinor && hour % 6 == 0 -> drawLine(MinorBoundaryColor.copy(alpha = 0.52f), Offset(x, 0f), Offset(x, size.height), 1.15.dp.toPx())
            drawMinor && hour % 3 == 0 -> drawLine(MinorBoundaryColor.copy(alpha = 0.30f), Offset(x, 0f), Offset(x, size.height), 0.75.dp.toPx())
        }
    }
}

/** Three real cloud layers shown as continuous altitude bands, not independent bars.
 *  Within each altitude band, the shaded depth corresponds to cloud cover %;
 *  a missing layer breaks its curve rather than becoming an invented zero.
 *  No total-cover contour is drawn: the three real layers are the informative series. */
private fun DrawScope.drawCloudLayerBands(
    hours: List<MeteogramHour>,
    first: Int,
    last: Int,
    offset: Float,
    step: Float,
    topFraction: Float = 0.52f,
    alpha: Float = 0.74f
) {
    val totalHeight = size.height * topFraction
    val laneHeight = totalHeight / 3f
    val colors = listOf(CloudHighColor, CloudMidColor, CloudLowColor)
    fun layer(hour: MeteogramHour, lane: Int): Int? = when (lane) {
        0 -> hour.cloudHighPercent
        1 -> hour.cloudMidPercent
        else -> hour.cloudLowPercent
    }?.takeIf { it in 0..100 }

    for (lane in 0..2) {
        val baseline = (lane + 1) * laneHeight
        val color = colors[lane]
        drawRect(color.copy(alpha = 0.045f), Offset(0f, lane * laneHeight), Size(size.width, laneHeight))
        drawLine(color.copy(alpha = 0.30f), Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
        var fill = Path()
        var edge = Path()
        var firstX = 0f
        var prev: Offset? = null
        var points = 0
        fun finish() {
            val lastPoint = prev ?: return
            if (points > 1) {
                fill.lineTo(lastPoint.x, baseline)
                fill.lineTo(firstX, baseline)
                fill.close()
                drawPath(fill, color.copy(alpha = alpha * 0.65f))
                drawPath(edge, color.copy(alpha = 0.96f),
                    style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else if (lastPoint.y < baseline) {
                drawLine(color.copy(alpha = alpha), Offset(lastPoint.x, baseline), lastPoint,
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            fill = Path()
            edge = Path()
            prev = null
            points = 0
        }
        for (i in first..last) {
            val cover = layer(hours[i], lane)
            if (cover == null) {
                finish()
                continue
            }
            val point = Offset((i + 0.5f) * step - offset,
                baseline - laneHeight * cover / 100f)
            val prior = prev
            if (prior == null) {
                firstX = point.x
                fill.moveTo(point.x, baseline)
                fill.lineTo(point.x, point.y)
                edge.moveTo(point.x, point.y)
            } else {
                val dx = point.x - prior.x
                // Horizontal end tangents keep every segment within its measured
                // endpoint heights (no artificial maxima or minima).
                fill.cubicTo(prior.x + dx * 0.35f, prior.y,
                    point.x - dx * 0.35f, point.y, point.x, point.y)
                edge.cubicTo(prior.x + dx * 0.35f, prior.y,
                    point.x - dx * 0.35f, point.y, point.x, point.y)
            }
            prev = point
            points++
        }
        finish()
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
    fun flush() {
        // A single isolated observation is still valid data: render a dot,
        // not an invisible curve with zero line segments.
        if (segments > 0) {
            drawPath(path, color, style = Stroke(2.55.dp.toPx(),
                cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            previous?.let { drawCircle(color, radius = 2.6.dp.toPx(), center = it) }
        }
        path = Path()
        previous = null
        segments = 0
    }
    for (i in first..last) {
        val v = value(hours[i])?.takeIf(Double::isFinite)
        if (v == null) {
            flush()
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
    flush()
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

private fun DrawScope.drawWindDirectionArrows(
    hours: List<MeteogramHour>, first: Int, last: Int, offset: Float, step: Float,
    cardinalLanguage: String? = null
) {
    val arrowY = size.height - (if (cardinalLanguage != null) 30.dp.toPx() else 12.dp.toPx())
    val arrowSize = 7.dp.toPx()
    val paint = if (cardinalLanguage != null) android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 10.sp.toPx()
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    } else null
    // Anchor to absolute forecast-hour indexes so arrows don't jump on scroll.
    val start = first + (3 - first % 3) % 3
    for (i in start..last step 3) {
        val direction = hours[i].windDirectionDeg ?: continue
        val x = (i + 0.5f) * step - offset
        if (x !in -step..size.width + step) continue
        // Meteorological arrows point towards the direction of origin.
        drawWindArrow(x, arrowY, arrowSize, direction.toFloat())
        if (cardinalLanguage != null && paint != null) {
            val label = meteogramWindCardinal(direction, cardinalLanguage) ?: continue
            val badgeWidth = (if (label.length > 1) 24.dp else 18.dp).toPx()
            val badgeHeight = 15.dp.toPx()
            val badgeTop = size.height - badgeHeight - 2.dp.toPx()
            drawRoundRect(WindColor.copy(alpha = 0.95f),
                topLeft = Offset(x - badgeWidth / 2f, badgeTop),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = CornerRadius(4.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(label, x, badgeTop + 11.dp.toPx(), paint)
        }
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
        if (x in -5.dp.toPx()..size.width + 5.dp.toPx()) {
            val color = if (marker.type == MeteogramMarkerType.SUNRISE) SunriseColor else SunsetColor
            // Soft halo plus a sharp, foreground marker visible over dense clouds/rain.
            drawLine(color.copy(alpha = 0.20f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 9.dp.toPx())
            drawLine(color.copy(alpha = 0.96f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.7.dp.toPx())
            drawCircle(color, radius = 3.6.dp.toPx(), center = Offset(x, 5.dp.toPx()))
        }
    }
}

private fun DrawScope.drawCurrentTimeLine(hours: List<MeteogramHour>, currentInstant: Instant, offset: Float, step: Float) {
    val x = positionForInstant(hours, currentInstant, step, offset) ?: return
    if (x in -3.dp.toPx()..size.width + 3.dp.toPx()) {
        drawLine(CurrentTimeColor.copy(alpha = 0.95f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
    }
}

/** Fractional slot for a solar event on an hourly timeline.
 *  Forecasts can contain gaps: elapsed hours must not be used as a list index.
 *  Interpolating between the two actual timestamps keeps the event aligned
 *  with the plotted hours, including DST and missing model hours.
 */
internal fun meteogramInstantSlot(hours: List<MeteogramHour>, instant: Instant): Float? {
    if (hours.isEmpty() || instant < hours.first().instant ||
        instant > hours.last().instant.plusSeconds(3600)) return null
    val index = hours.binarySearch { it.instant.compareTo(instant) }
    if (index >= 0) return index + 0.5f
    val next = -index - 1
    if (next == 0) return null
    val preceding = hours[next - 1].instant
    val following = if (next < hours.size) hours[next].instant else preceding.plusSeconds(3600)
    val spanMillis = Duration.between(preceding, following).toMillis()
    if (spanMillis <= 0L) return null
    val progress = Duration.between(preceding, instant).toMillis().toDouble() / spanMillis
    return next - 0.5f + progress.toFloat()
}

private fun positionForInstant(hours: List<MeteogramHour>, instant: Instant, step: Float, offset: Float): Float? =
    meteogramInstantSlot(hours, instant)?.let { it * step - offset }

private fun DrawScope.drawSelectionLine(selectedIndex: Int, offset: Float, step: Float) {
    val x = (selectedIndex + 0.5f) * step - offset
    if (x in 0f..size.width) {
        drawLine(SelectionColor.copy(alpha = 0.76f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.25.dp.toPx())
    }
}

/** The selected hour belongs to its chart; no duplicate static min/max range badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeteogramCardSelectedValues(
    kind: MeteogramKind,
    hour: MeteogramHour,
    zone: ZoneId,
    clockFormatter: DateTimeFormatter,
    units: WeatherUnits,
    locale: Locale
) {
    val metrics = when (kind) {
        MeteogramKind.EVERYDAY -> listOf(
            Triple(CloudHighColor, stringResource(R.string.meteogram_cloud_high), hour.cloudHighPercent?.let { "$it%" } ?: "—"),
            Triple(CloudMidColor, stringResource(R.string.meteogram_cloud_mid), hour.cloudMidPercent?.let { "$it%" } ?: "—"),
            Triple(CloudLowColor, stringResource(R.string.meteogram_cloud_low), hour.cloudLowPercent?.let { "$it%" } ?: "—"),
            Triple(RainColor, stringResource(R.string.meteogram_rain), units.rain(hour.rainMm)),
            Triple(PressureColor, stringResource(R.string.meteogram_pressure),
                hour.pressureHpa?.let { "${units.number(units.pressure(it), if (units.imperial) 2 else 0)} ${if (units.imperial) "inHg" else "hPa"}" } ?: "—")
        )
        MeteogramKind.TEMPERATURE -> listOf(
            Triple(TemperatureColor, stringResource(R.string.meteogram_temperature), units.temp(hour.temperatureC)),
            Triple(ApparentColor, stringResource(R.string.apparent_temperature_widget_prefix), units.temp(hour.apparentC)),
            Triple(DewColor, stringResource(R.string.metric_dew_point_short), units.temp(hour.dewC)),
            Triple(HumidityColor, stringResource(R.string.meteogram_humidity), hour.humidityPercent?.let { "$it%" } ?: "—")
        )
        MeteogramKind.WIND -> listOf(
            Triple(WindColor, stringResource(R.string.meteogram_wind), units.speed(hour.windKmh)),
            Triple(GustColor, stringResource(R.string.graphic_view_gust_short), units.speed(hour.gustKmh)),
            Triple(WindColor, stringResource(R.string.meteogram_wind_origin),
                meteogramWindCardinal(hour.windDirectionDeg, locale.language)?.let { cardinal ->
                    "$cardinal · ${hour.windDirectionDeg}°"
                } ?: "—")
        )
        MeteogramKind.HUMIDITY -> listOf(
            Triple(HumidityColor, stringResource(R.string.meteogram_humidity), hour.humidityPercent?.let { "$it%" } ?: "—")
        )
        MeteogramKind.PRESSURE -> listOf(
            Triple(PressureColor, stringResource(R.string.meteogram_pressure),
                hour.pressureHpa?.let { "${units.number(units.pressure(it), if (units.imperial) 2 else 0)} ${if (units.imperial) "inHg" else "hPa"}" } ?: "—")
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag(TAG_METEOGRAM_CARD_SELECTED_PREFIX + kind.name.lowercase()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.meteogram_selected, hour.instant.atZone(zone).format(clockFormatter)),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            metrics.forEach { (color, label, value) -> MeteogramValuePill(label, value, color) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeteogramSelectedValues(hour: MeteogramHour, cityZone: ZoneId, units: WeatherUnits, locale: Locale) {
    val clockPreference = LocalClockFormatPreference.current
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val clockFormatter = remember(locale, clockPreference) { WeatherTimeFormat.clock(locale, clockPreference) }
    val selectedTime = hour.instant.atZone(cityZone)
    val values = listOf(
        Triple(TemperatureColor, stringResource(R.string.meteogram_temperature), units.temp(hour.temperatureC)),
        Triple(ApparentColor, stringResource(R.string.apparent_temperature_widget_prefix), units.temp(hour.apparentC)),
        Triple(DewColor, stringResource(R.string.metric_dew_point), units.temp(hour.dewC)),
        Triple(HumidityColor, stringResource(R.string.meteogram_humidity), hour.humidityPercent?.let { "$it%" } ?: "—"),
        Triple(RainColor, stringResource(R.string.meteogram_rain), units.rain(hour.rainMm)),
        Triple(WindColor, stringResource(R.string.meteogram_wind), units.speed(hour.windKmh)),
        Triple(GustColor, stringResource(R.string.graphic_view_gust_short), units.speed(hour.gustKmh)),
        Triple(PressureColor, stringResource(R.string.meteogram_pressure),
            hour.pressureHpa?.let { "${units.number(units.pressure(it), if (units.imperial) 2 else 0)} ${if (units.imperial) "inHg" else "hPa"}" } ?: "—"),
        Triple(CloudLowColor, stringResource(R.string.meteogram_cloud_low), hour.cloudLowPercent?.let { "$it%" } ?: "—"),
        Triple(CloudMidColor, stringResource(R.string.meteogram_cloud_mid), hour.cloudMidPercent?.let { "$it%" } ?: "—"),
        Triple(CloudHighColor, stringResource(R.string.meteogram_cloud_high), hour.cloudHighPercent?.let { "$it%" } ?: "—")
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp).testTag(TAG_METEOGRAM_SELECTED),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.meteogram_selected,
                    "${selectedTime.format(dateFormatter)} · ${selectedTime.format(clockFormatter)}"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                values.forEach { (color, label, value) ->
                    MeteogramValuePill(label = label, value = value, color = color)
                }
            }
        }
    }
}

@Composable
private fun MeteogramValuePill(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.09f),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(width = 4.dp, height = 24.dp).background(color, RoundedCornerShape(999.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
        }
    }
}
