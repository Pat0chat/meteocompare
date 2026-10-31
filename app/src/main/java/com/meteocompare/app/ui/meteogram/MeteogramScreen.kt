package com.meteocompare.app.ui.meteogram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
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
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.graphicview.GraphicForecastUiState
import com.meteocompare.app.ui.graphicview.GraphicForecastViewModel
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// The stacked graphs share one horizontal viewport. No 240-hour-wide Canvas is
// allocated: drawing is bounded to the visible hours, including a small overscan.
private val HourWidth = 38.dp
private val AxisWidth = 68.dp
private val WeatherHeight = 176.dp
private val TemperatureHeight = 162.dp
private val HumidityHeight = 142.dp
private val PressureHeight = 142.dp
private val WindHeight = 142.dp
private val DailyHeight = 114.dp
private val TimeHeight = 44.dp

internal const val TAG_METEOGRAM_CONTENT = "meteogram_content"
internal const val TAG_METEOGRAM_TIME = "meteogram_time"
internal const val TAG_METEOGRAM_WEATHER = "meteogram_weather"
internal const val TAG_METEOGRAM_TEMPERATURE = "meteogram_temperature"
internal const val TAG_METEOGRAM_HUMIDITY = "meteogram_humidity"
internal const val TAG_METEOGRAM_PRESSURE = "meteogram_pressure"
internal const val TAG_METEOGRAM_WIND = "meteogram_wind"
internal const val TAG_METEOGRAM_DAILY = "meteogram_daily"
internal const val TAG_METEOGRAM_SELECTED = "meteogram_selected"
internal const val TAG_METEOGRAM_CARD_PREFIX = "meteogram_card_"
internal const val TAG_METEOGRAM_COMPARE = "meteogram_compare"

private val RainColor = Color(0xFF0065AB)
private val CloudColor = Color(0xFF8C9299)
private val WindColor = Color(0xFF167D3C)
private val GustColor = Color(0xFF46A36B)
private val TemperatureColor = Color(0xFF116AA5)
private val ApparentColor = Color(0xFFA34BC3)
private val DewColor = Color(0xFF1B98AA)
private val HumidityColor = Color(0xFF168ED2)
private val PressureColor = Color(0xFF323A46)
private val WarmDeltaColor = Color(0xFFE56C45)
private val CoolDeltaColor = Color(0xFF389BCC)

/** Inputs are always canonical metric values; conversion happens only for display. */
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
    val humidityPercent: Int? = null,
    val pressureHpa: Double? = null
)

internal data class MeteogramDomain(val min: Double, val max: Double) {
    val span: Double get() = max - min
    fun fraction(value: Double): Float = ((value - min) / span).coerceIn(0.0, 1.0).toFloat()
}

/** Finite-only dynamic range, with optional zero origin for absolute quantities. */
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

internal data class MeteogramDailyRange(
    val date: LocalDate,
    val firstHour: Int,
    val hourCount: Int,
    val highC: Double?,
    val overnightLowC: Double?
)

/** Overnight low: 12:00 of this date until 12:00 of the next local day. */
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
            nightValues.size >= 22 // 23-hour DST nights are legitimate.
        MeteogramDailyRange(
            date = date,
            firstHour = indexes.first(),
            hourCount = indexes.size,
            highC = if (completeDay) indexes.mapNotNull { hours[it].temperatureC?.takeIf(Double::isFinite) }.maxOrNull() else null,
            overnightLowC = if (nightComplete) nightValues.mapNotNull {
                it.temperatureC?.takeIf(Double::isFinite)
            }.minOrNull() else null
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
            humidityPercent = if (selectedModel == null) point.humidityPercent else model?.humidityPercent,
            pressureHpa = if (selectedModel == null) point.pressureHpa else model?.pressureHpa
        )
    }
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
                GraphicForecastUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
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

/** Flowx-like, independent chart presets. Each chart owns its legend and scale. */
internal enum class MeteogramKind { EVERYDAY, TEMPERATURE, HUMIDITY, PRESSURE, WIND, DAILY }

internal val MeteogramPresets = MeteogramKind.entries.toList()

@Composable
internal fun MeteogramContent(state: GraphicForecastUiState.Loaded, modifier: Modifier = Modifier) {
    val units = LocalWeatherUnits.current
    val locale = LocalConfiguration.current.locales[0]
    val clockPreference = LocalClockFormatPreference.current
    val timeFormatter = remember(locale, clockPreference) {
        WeatherTimeFormat.hourPadded(locale, clockPreference)
    }
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
    var selectedIndex by rememberSaveable(state.city.id) { mutableIntStateOf(0) }
    var scrollPx by remember(state.city.id) { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
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
    val dailyDomain = remember(ranges) { meteogramDailyDomain(ranges) }
    val priorTemperatures = remember(hours) { hours.associate { it.instant to it.temperatureC } }
    val onSelect: (Int) -> Unit = { selectedIndex = it.coerceIn(0, (hours.size - 1).coerceAtLeast(0)) }
    var comparing by rememberSaveable(state.city.id) { mutableStateOf(false) }
    var compareKind by rememberSaveable(state.city.id) { mutableStateOf(MeteogramKind.EVERYDAY) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(TAG_METEOGRAM_CONTENT)) {
        Text(state.city.name, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.meteogram_browse_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = selectedModel == null, onClick = { selectedModel = null },
                label = { Text(stringResource(R.string.meteogram_consensus)) })
            models.forEach { name ->
                FilterChip(selected = selectedModel == name, onClick = { selectedModel = name },
                    label = { Text(name, maxLines = 1) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (comparing) stringResource(R.string.meteogram_compare_title)
                 else stringResource(R.string.meteogram_presets_title),
                 style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (models.isNotEmpty()) {
                FilterChip(selected = comparing, onClick = { comparing = !comparing },
                    label = { Text(stringResource(R.string.meteogram_compare_action)) },
                    modifier = Modifier.testTag(TAG_METEOGRAM_COMPARE))
            }
        }
        if (hours.isEmpty()) {
            Text(stringResource(R.string.meteogram_no_data), Modifier.padding(20.dp))
            return@Column
        }
        val index = selectedIndex.coerceIn(hours.indices)
        val chartAccessibility = stringResource(R.string.meteogram_a11y)
        if (comparing && models.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // Compare one graph type across all sources, as in Flowx's Compare Mode.
                MeteogramPresets.filter { it != MeteogramKind.DAILY }.forEach { kind ->
                    FilterChip(selected = compareKind == kind, onClick = { compareKind = kind },
                        label = { Text(meteogramKindLabel(kind)) })
                }
            }
            // All sources use identical scales so vertical differences are comparable.
            val modelHoursByName = remember(state.points, state.modelValuesByInstant, state.solarByDate, models) {
                models.associateWith { name -> buildMeteogramHours(state, name) }
            }
            val compareHours = remember(modelHoursByName, visible.first, visible.last) {
                if (visible.isEmpty()) emptyList() else modelHoursByName.values.flatMap { modelHours ->
                    if (modelHours.size == hours.size) modelHours.subList(visible.first, visible.last + 1)
                    else emptyList()
                }
            }
            val compareDomain = remember(compareHours, compareKind) {
                meteogramDomain(meteogramKindValues(compareKind, compareHours),
                    meteogramMinimumSpan(compareKind),
                    includeZero = compareKind == MeteogramKind.EVERYDAY || compareKind == MeteogramKind.WIND,
                    bounds = if (compareKind == MeteogramKind.HUMIDITY) 0.0..100.0 else null)
            }
            val compareWindDomain = remember(compareHours) {
                meteogramDomain(compareHours.asSequence().flatMap { sequenceOf(it.windKmh, it.gustKmh) },
                    5.0, includeZero = true)
            }
            val compareRainDomain = remember(compareHours) {
                meteogramDomain(compareHours.asSequence().map { it.rainMm }, 0.5, includeZero = true)
            }
            models.forEach { name ->
                val modelHours = modelHoursByName.getValue(name)
                MeteogramChartCard(
                    kind = compareKind, title = name, hours = modelHours,
                    zone = zone, locale = locale, timeFormatter = timeFormatter,
                    units = units, step = hourPx, offset = scrollPx, range = visible,
                    selectedIndex = index, scroll = scroll, onSelect = onSelect,
                    onWidthChange = { widthPx = it }, domain = compareDomain,
                    windDomain = compareWindDomain,
                    rainDomain = compareRainDomain,
                    priorTemperatures = remember(modelHours) { modelHours.associate { it.instant to it.temperatureC } },
                    dailyRanges = emptyList(), accessibility = chartAccessibility
                )
            }
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
                    kind = kind, title = meteogramKindLabel(kind), hours = hours,
                    zone = zone, locale = locale, timeFormatter = timeFormatter,
                    units = units, step = hourPx, offset = scrollPx, range = visible,
                    selectedIndex = index, scroll = scroll, onSelect = onSelect,
                    onWidthChange = { widthPx = it }, domain = domain,
                    windDomain = windDomain, rainDomain = rainDomain,
                    priorTemperatures = priorTemperatures,
                    dailyRanges = ranges, accessibility = chartAccessibility
                )
            }
        }
        MeteogramSelectedValues(hours[index], zone, units, locale)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { OpenMeteoAttribution(home = false) }
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
    hours.asSequence().flatMap { hour -> when (kind) {
        MeteogramKind.EVERYDAY -> sequenceOf(hour.rainMm)
        MeteogramKind.TEMPERATURE, MeteogramKind.DAILY -> sequenceOf(hour.temperatureC, hour.apparentC, hour.dewC)
        MeteogramKind.HUMIDITY -> sequenceOf(hour.humidityPercent?.toDouble())
        MeteogramKind.PRESSURE -> sequenceOf(hour.pressureHpa)
        MeteogramKind.WIND -> sequenceOf(hour.windKmh, hour.gustKmh)
    } }

@Composable
private fun MeteogramChartCard(
    kind: MeteogramKind, title: String, hours: List<MeteogramHour>, zone: ZoneId,
    locale: Locale, timeFormatter: DateTimeFormatter, units: WeatherUnits,
    step: Float, offset: Float, range: IntRange, selectedIndex: Int,
    scroll: androidx.compose.foundation.gestures.ScrollableState, onSelect: (Int) -> Unit,
    onWidthChange: (Int) -> Unit, domain: MeteogramDomain,
    windDomain: MeteogramDomain, rainDomain: MeteogramDomain,
    priorTemperatures: Map<Instant, Double?>,
    dailyRanges: List<MeteogramDailyRange>, accessibility: String
) {
    val cardTag = TAG_METEOGRAM_CARD_PREFIX + kind.name.lowercase()
    val height = when (kind) {
        MeteogramKind.EVERYDAY -> WeatherHeight
        MeteogramKind.TEMPERATURE -> TemperatureHeight
        MeteogramKind.HUMIDITY -> HumidityHeight
        MeteogramKind.PRESSURE -> PressureHeight
        MeteogramKind.WIND -> WindHeight
        MeteogramKind.DAILY -> DailyHeight
    }
    val selected = hours.getOrNull(selectedIndex)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag(cardTag).semantics { contentDescription = accessibility },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(title, Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            MeteogramChartLegend(kind, units, rainDomain)
            Row(Modifier.padding(start = 8.dp, end = 5.dp, top = 4.dp)) {
                Column(Modifier.weight(1f).onSizeChanged { onWidthChange(it.width) }
                    .scrollable(scroll, Orientation.Horizontal)) {
                    MeteogramTimeAxis(hours, zone, locale, timeFormatter, step, offset, selectedIndex)
                    if (kind == MeteogramKind.DAILY) {
                        MeteogramDailyStrip(hours, dailyRanges, locale, units, step, offset, selectedIndex)
                    } else {
                        MeteogramPlot(hours, height, step, offset, range, selectedIndex,
                            meteogramKindTag(kind), onSelect) { first, last, shift, hourStep ->
                            drawMeteogramBackground(hours, first, last, shift, hourStep, kind == MeteogramKind.EVERYDAY)
                            when (kind) {
                                MeteogramKind.EVERYDAY -> {
                                    drawCloudBars(hours, first, last, shift, hourStep)
                                    drawRainBars(hours, first, last, shift, hourStep, rainDomain)
                                    drawMetricCurve(hours, first, last, shift, hourStep, windDomain, WindColor) { it.windKmh }
                                }
                                MeteogramKind.TEMPERATURE -> {
                                    drawDayOverDayBands(hours, priorTemperatures, first, last, shift, hourStep, domain)
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, TemperatureColor) { it.temperatureC }
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, ApparentColor) { it.apparentC }
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, DewColor) { it.dewC }
                                }
                                MeteogramKind.HUMIDITY -> drawMetricCurve(hours, first, last, shift, hourStep,
                                    domain, HumidityColor) { it.humidityPercent?.toDouble() }
                                MeteogramKind.PRESSURE -> drawMetricCurve(hours, first, last, shift, hourStep,
                                    domain, PressureColor) { it.pressureHpa }
                                MeteogramKind.WIND -> {
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, WindColor) { it.windKmh }
                                    drawMetricCurve(hours, first, last, shift, hourStep, domain, GustColor) { it.gustKmh }
                                }
                                MeteogramKind.DAILY -> Unit
                            }
                        }
                    }
                }
                MeteogramValueAxis(kind, height, domain, windDomain, units)
            }
            if (selected != null) {
                Text(meteogramSelectedCaption(kind, selected, units),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
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
    MeteogramKind.EVERYDAY -> "${stringResource(R.string.meteogram_cloud)}: ${hour.cloudPercent?.let { "$it%" } ?: "—"}  ·  ${stringResource(R.string.meteogram_rain)}: ${units.rain(hour.rainMm)}  ·  ${stringResource(R.string.meteogram_wind)}: ${units.speed(hour.windKmh)}"
    MeteogramKind.TEMPERATURE -> "${units.temp(hour.temperatureC)}  ·  ${stringResource(R.string.apparent_temperature_widget_prefix)} ${units.temp(hour.apparentC)}  ·  ${stringResource(R.string.metric_dew_point_short)} ${units.temp(hour.dewC)}"
    MeteogramKind.HUMIDITY -> "${stringResource(R.string.meteogram_humidity)}: ${hour.humidityPercent?.let { "$it%" } ?: "—"}"
    MeteogramKind.PRESSURE -> "${stringResource(R.string.meteogram_pressure)}: ${hour.pressureHpa?.let { units.number(units.pressure(it), if (units.imperial) 2 else 0) } ?: "—"} ${if (units.imperial) "inHg" else "hPa"}"
    MeteogramKind.WIND -> "${stringResource(R.string.meteogram_wind)}: ${units.speed(hour.windKmh)}  ·  ${stringResource(R.string.graphic_view_gust_short)}: ${units.speed(hour.gustKmh)}"
    MeteogramKind.DAILY -> stringResource(R.string.meteogram_daily_caption)
}

@Composable
private fun MeteogramChartLegend(kind: MeteogramKind, units: WeatherUnits,
                                 rain: MeteogramDomain) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (kind) {
            MeteogramKind.EVERYDAY -> {
                MeteogramKey(Color(0xFFFFC44B), stringResource(R.string.meteogram_sun))
                MeteogramKey(CloudColor, stringResource(R.string.meteogram_cloud))
                MeteogramKey(RainColor, "${stringResource(R.string.meteogram_rain)} (0–${units.number(units.precipitation(rain.max), units.precipitationDigits)} ${units.precipitationUnit}/h)")
                MeteogramKey(WindColor, "${stringResource(R.string.meteogram_wind)} (${units.windUnit})")
            }
            MeteogramKind.TEMPERATURE -> {
                MeteogramKey(TemperatureColor, stringResource(R.string.meteogram_temperature))
                MeteogramKey(ApparentColor, stringResource(R.string.apparent_temperature_widget_prefix))
                MeteogramKey(DewColor, stringResource(R.string.metric_dew_point_short))
                MeteogramKey(WarmDeltaColor, stringResource(R.string.meteogram_warmer))
                MeteogramKey(CoolDeltaColor, stringResource(R.string.meteogram_colder))
            }
            MeteogramKind.HUMIDITY -> MeteogramKey(HumidityColor, "%")
            MeteogramKind.PRESSURE -> MeteogramKey(PressureColor, if (units.imperial) "inHg" else "hPa")
            MeteogramKind.WIND -> {
                MeteogramKey(WindColor, stringResource(R.string.meteogram_wind))
                MeteogramKey(GustColor, stringResource(R.string.graphic_view_gust_short))
                Text(units.windUnit, style = MaterialTheme.typography.labelSmall)
            }
            MeteogramKind.DAILY -> MeteogramKey(TemperatureColor, stringResource(R.string.meteogram_daily_caption))
        }
    }
}

@Composable
private fun MeteogramKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MeteogramValueAxis(kind: MeteogramKind, height: Dp,
                              domain: MeteogramDomain, wind: MeteogramDomain, units: WeatherUnits) {
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
    Column(Modifier.width(AxisWidth).padding(start = 3.dp)) {
        Box(Modifier.height(TimeHeight), contentAlignment = Alignment.Center) {
            Text(when (kind) {
                MeteogramKind.EVERYDAY, MeteogramKind.WIND -> units.windUnit
                MeteogramKind.TEMPERATURE, MeteogramKind.DAILY -> units.temperatureUnit
                MeteogramKind.HUMIDITY -> "%"
                MeteogramKind.PRESSURE -> if (units.imperial) "inHg" else "hPa"
            }, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(Modifier.height(height).padding(vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            labels.forEach { label -> Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

@Composable
private fun MeteogramTimeAxis(
    hours: List<MeteogramHour>, zone: ZoneId, locale: Locale, hourFormatter: DateTimeFormatter,
    step: Float, offset: Float, selectedIndex: Int
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val fontSize = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember(ink, fontSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        textSize = fontSize
    } }
    Canvas(Modifier.fillMaxWidth().height(TimeHeight).testTag(TAG_METEOGRAM_TIME)) {
        val range = meteogramViewport(offset, size.width, step, hours.size)
        if (range.isEmpty()) return@Canvas
        for (i in range) {
            val dateTime = hours[i].instant.atZone(zone)
            val x = (i + 0.5f) * step - offset
            if (dateTime.hour == 0 || i == 0) {
                drawLine(CloudColor.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(dateTime.format(dayFormatter), x + 4.dp.toPx(), 16.dp.toPx(), paint)
            }
            if (dateTime.hour % 3 == 0) {
                drawContext.canvas.nativeCanvas.drawText(dateTime.format(hourFormatter),
                    x, 36.dp.toPx(), paint)
            }
        }
        drawSelectionLine(selectedIndex, offset, step)
    }
}

@Composable
private fun MeteogramPlot(
    hours: List<MeteogramHour>, height: Dp, step: Float, offset: Float,
    range: IntRange, selectedIndex: Int, tag: String,
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
        drawLine(CloudColor.copy(alpha = 0.15f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
        drawing(range.first, range.last, offset, step)
        drawSelectionLine(selectedIndex, offset, step)
    }
}

private fun DrawScope.drawMeteogramBackground(
    hours: List<MeteogramHour>, first: Int, last: Int, offset: Float, step: Float, everyday: Boolean = false
) {
    for (i in first..last) {
        val x = i * step - offset
        drawRect(
            if (everyday && hours[i].cloudPercent == null) Color(0xFF42516A).copy(alpha = 0.07f)
            else if (hours[i].daylight) Color(0xFFFFC247).copy(alpha = if (everyday) 0.83f else 0.04f)
            else Color(0xFF42516A).copy(alpha = if (everyday) 0.22f else 0.06f),
            topLeft = Offset(x, 0f), size = Size(step + 0.5f, size.height)
        )
    }
    for (row in 1..3) {
        val y = size.height * row / 4
        drawLine(CloudColor.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y))
    }
}

private fun DrawScope.drawCloudBars(
    hours: List<MeteogramHour>, first: Int, last: Int, offset: Float, step: Float
) {
    // Cloud cover hangs down from the upper edge, leaving the sunny yellow visible.
    var path = Path()
    var runStart: Int? = null
    var lastCloudIndex = -1
    fun finishRun() {
        val start = runStart ?: return
        val end = lastCloudIndex
        path.lineTo((end + 1f) * step - offset, 0f)
        path.lineTo(start * step - offset, 0f)
        path.close()
        drawPath(path, CloudColor.copy(alpha = 0.88f))
        path = Path()
        runStart = null
    }
    for (i in first..last) {
        val cloud = hours[i].cloudPercent?.coerceIn(0, 100)
        if (cloud == null) { finishRun(); continue }
        if (runStart == null) {
            runStart = i
            path.moveTo(i * step - offset, 0f)
            path.lineTo(i * step - offset, size.height * cloud / 100f)
        }
        path.lineTo((i + 0.5f) * step - offset, size.height * cloud / 100f)
        lastCloudIndex = i
    }
    finishRun()
}

private fun DrawScope.drawRainBars(
    hours: List<MeteogramHour>, first: Int, last: Int, offset: Float, step: Float,
    domain: MeteogramDomain
) {
    for (i in first..last) {
        val rain = hours[i].rainMm?.takeIf { it.isFinite() && it > 0 } ?: continue
        val height = size.height * 0.92f * domain.fraction(rain)
        drawRect(RainColor.copy(alpha = 0.9f),
            Offset(i * step - offset, size.height - height),
            Size(step + 0.5f, height))
    }
}

private fun DrawScope.drawMetricCurve(
    hours: List<MeteogramHour>, first: Int, last: Int,
    offset: Float, step: Float, domain: MeteogramDomain, color: Color,
    value: (MeteogramHour) -> Double?
) {
    var path = Path()
    var previous: Offset? = null
    var segments = 0
    for (i in first..last) {
        val v = value(hours[i])?.takeIf(Double::isFinite)
        if (v == null) {
            if (segments > 0) drawPath(path, color, style = Stroke(2.35.dp.toPx(),
                cap = StrokeCap.Round, join = StrokeJoin.Round))
            path = Path()
            previous = null
            segments = 0
            continue
        }
        val point = Offset((i + 0.5f) * step - offset,
            (size.height - 8.dp.toPx()) * (1f - domain.fraction(v)) + 4.dp.toPx())
        val previousPoint = previous
        if (previousPoint == null) path.moveTo(point.x, point.y)
        else {
            val dx = point.x - previousPoint.x
            // Both control-point y-values equal their endpoints: no invented peaks.
            path.cubicTo(previousPoint.x + dx * 0.36f, previousPoint.y,
                point.x - dx * 0.36f, point.y, point.x, point.y)
            segments++
        }
        previous = point
    }
    if (segments > 0) drawPath(path, color, style = Stroke(2.35.dp.toPx(),
        cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawDayOverDayBands(
    hours: List<MeteogramHour>, previous: Map<Instant, Double?>, first: Int, last: Int,
    offset: Float, step: Float, domain: MeteogramDomain
) {
    fun y(value: Double): Float = 4.dp.toPx() +
        (size.height - 8.dp.toPx()) * (1f - domain.fraction(value))
    // Fill between current and the exact 24-hour-earlier series, as in Flowx.
    // Split at sign changes so red never represents colder and blue never warmer.
    for (i in first until last) {
        val a = hours[i].temperatureC?.takeIf(Double::isFinite) ?: continue
        val b = hours[i + 1].temperatureC?.takeIf(Double::isFinite) ?: continue
        val oldA = previous[hours[i].instant.minusSeconds(86400)]?.takeIf(Double::isFinite) ?: continue
        val oldB = previous[hours[i + 1].instant.minusSeconds(86400)]?.takeIf(Double::isFinite) ?: continue
        val xA = (i + 0.5f) * step - offset
        val xB = (i + 1.5f) * step - offset
        fun fill(x1: Float, x2: Float, cur1: Float, cur2: Float,
                 prior1: Float, prior2: Float, warm: Boolean) {
            val p = Path().apply {
                moveTo(x1, cur1); lineTo(x2, cur2); lineTo(x2, prior2)
                lineTo(x1, prior1); close()
            }
            drawPath(p, (if (warm) WarmDeltaColor else CoolDeltaColor).copy(alpha = 0.25f))
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

private fun DrawScope.drawSelectionLine(selectedIndex: Int, offset: Float, step: Float) {
    val x = (selectedIndex + 0.5f) * step - offset
    if (x in 0f..size.width) {
        drawLine(Color(0xFFCF6471).copy(alpha = 0.78f), Offset(x, 0f),
            Offset(x, size.height), strokeWidth = 1.25.dp.toPx())
    }
}

/** Range bars are comparable across days on one common temperature scale. */
internal fun meteogramDailyDomain(ranges: List<MeteogramDailyRange>): MeteogramDomain =
    meteogramDomain(ranges.asSequence().flatMap { sequenceOf(it.highC, it.overnightLowC) }, 3.0)

@Composable
private fun MeteogramDailyStrip(
    hours: List<MeteogramHour>, ranges: List<MeteogramDailyRange>,
    locale: Locale, units: WeatherUnits, step: Float, offset: Float, selectedIndex: Int
) {
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val fontSize = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember(ink, fontSize) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; textSize = fontSize; textAlign = android.graphics.Paint.Align.CENTER
    } }
    val domain = remember(ranges) { meteogramDailyDomain(ranges) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    Canvas(Modifier.fillMaxWidth().height(DailyHeight).testTag(TAG_METEOGRAM_DAILY)) {
        val visible = meteogramViewport(offset, size.width, step, hours.size)
        if (visible.isEmpty()) return@Canvas
        val plotTop = 26.dp.toPx()
        val plotBottom = size.height - 22.dp.toPx()
        val plotSpan = plotBottom - plotTop
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
            drawLine(CloudColor.copy(alpha = 0.16f), Offset(x, 0f), Offset(x, size.height))
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
                // Both extremes: a warm upper half and a cold lower half.
                drawRoundRect(WarmDeltaColor.copy(alpha = 0.85f),
                    topLeft = Offset(center - 8.dp.toPx(), barTop),
                    size = Size(16.dp.toPx(), barHeight / 2), cornerRadius = CornerRadius(4.dp.toPx()))
                drawRoundRect(CoolDeltaColor.copy(alpha = 0.85f),
                    topLeft = Offset(center - 8.dp.toPx(), barTop + barHeight / 2),
                    size = Size(16.dp.toPx(), barHeight / 2), cornerRadius = CornerRadius(4.dp.toPx()))
            } else {
                val barColor = if (isWarm) WarmDeltaColor else if (isCold) CoolDeltaColor else CloudColor
                drawRoundRect(barColor.copy(alpha = 0.82f),
                    topLeft = Offset(center - 8.dp.toPx(), barTop),
                    size = Size(16.dp.toPx(), barHeight), cornerRadius = CornerRadius(5.dp.toPx()))
            }
            drawContext.canvas.nativeCanvas.drawText(units.temp(high), center - 23.dp.toPx(),
                (yHigh - 3.dp.toPx()).coerceAtLeast(20.dp.toPx()), paint)
            drawContext.canvas.nativeCanvas.drawText(units.temp(low), center + 23.dp.toPx(),
                (yLow + 13.dp.toPx()).coerceAtMost(size.height - 2.dp.toPx()), paint)
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
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.meteogram_selected, hour.instant.atZone(cityZone).format(formatter)),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(values.joinToString("   ·   "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
