package com.meteocompare.app.ui.graphicview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.weatherConditionLabelRes
import com.meteocompare.app.domain.model.VigilanceColor
import com.meteocompare.app.domain.model.VigilanceForecast
import com.meteocompare.app.domain.model.VigilancePhenomenon
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.citydetail.ForecastMetric
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.citydetail.resolveCityZone
import com.meteocompare.app.ui.components.OpenMeteoAttribution
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.WindArrow
import com.meteocompare.app.ui.theme.precipitationMetricAccent
import com.meteocompare.app.ui.theme.temperatureMetricAccent
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import com.meteocompare.app.ui.theme.windMetricAccent
import com.meteocompare.app.ui.theme.WeatherAccentTheme
import com.meteocompare.app.ui.theme.confidenceColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private val GraphicHourWidth = 40.dp
private val GraphicAxisWidth = 76.dp
private val TemperaturePlotHeight = 252.dp
private val RainPlotHeight = 150.dp
private val WindPlotHeight = 150.dp
private val TimeAxisHeight = 70.dp
private val VigilanceLaneHeight = 116.dp
private val PlotTopPadding = 40.dp
private val PlotBottomPadding = 18.dp

internal const val TAG_GRAPHIC_HOUR_CELL = "graphic_hour_cell"
internal const val TAG_GRAPHIC_CONDITION_ICON = "graphic_condition_icon"
internal const val TAG_GRAPHIC_TEMPERATURE_PLOT = "graphic_temperature_plot"
internal const val TAG_GRAPHIC_RAIN_PLOT = "graphic_rain_plot"
internal const val TAG_GRAPHIC_WIND_PLOT = "graphic_wind_plot"
internal const val TAG_GRAPHIC_TEMPERATURE_TOOLTIP = "graphic_temperature_tooltip"
internal const val TAG_GRAPHIC_TEMPERATURE_TOOLTIP_VALUE = "graphic_temperature_tooltip_value"
internal const val TAG_GRAPHIC_TEMPERATURE_TOOLTIP_RANGE = "graphic_temperature_tooltip_range"
internal const val TAG_GRAPHIC_RAIN_TOOLTIP = "graphic_rain_tooltip"
internal const val TAG_GRAPHIC_RAIN_TOOLTIP_AMOUNT = "graphic_rain_tooltip_amount"
internal const val TAG_GRAPHIC_RAIN_TOOLTIP_PROBABILITY = "graphic_rain_tooltip_probability"
internal const val TAG_GRAPHIC_WIND_TOOLTIP = "graphic_wind_tooltip"
internal const val TAG_GRAPHIC_WIND_TOOLTIP_MEAN = "graphic_wind_tooltip_mean"
internal const val TAG_GRAPHIC_WIND_TOOLTIP_GUST = "graphic_wind_tooltip_gust"
internal const val TAG_GRAPHIC_WIND_TOOLTIP_DIRECTION = "graphic_wind_tooltip_direction"
internal const val TAG_GRAPHIC_WIND_DIRECTION_ARROW = "graphic_wind_direction_arrow"
internal const val TAG_GRAPHIC_AXIS_ICON = "graphic_axis_icon"
internal const val TAG_GRAPHIC_CHART_PANEL = "graphic_chart_panel"
internal const val TAG_GRAPHIC_SELECTION_HEADER = "graphic_selection_header"
internal const val TAG_GRAPHIC_RAIN_SELECTION_CONVERGENCE = "graphic_rain_selection_convergence"
internal const val TAG_GRAPHIC_LEGEND_SYMBOL = "graphic_legend_symbol"
internal const val TAG_GRAPHIC_DAY_HEADER = "graphic_day_header"

internal const val GRAPHIC_TEMPERATURE_HEAT_ALPHA = 0.20f
internal const val GRAPHIC_RAIN_HEAT_ALPHA = 0.22f
internal const val GRAPHIC_WIND_HEAT_ALPHA = 0.18f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GraphicForecastScreen(
    onBack: () -> Unit,
    viewModel: GraphicForecastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIfStale()
    }

    WeatherAccentTheme(condition = (state as? GraphicForecastUiState.Loaded)?.points?.firstOrNull()?.condition) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.graphic_view_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nav_back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            when (val current = state) {
                GraphicForecastUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                is GraphicForecastUiState.Error -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(current.message, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }

                is GraphicForecastUiState.Loaded -> GraphicForecastContent(
                    state = current,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GraphicForecastContent(
    state: GraphicForecastUiState.Loaded,
    modifier: Modifier = Modifier
) {
    if (state.points.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.graphic_view_no_data))
        }
        return
    }

    val locale = LocalConfiguration.current.locales[0]
    val zone = remember(state.city.timezone) { resolveCityZone(state.city.timezone) }
    var selectedIndex by remember(state.points) { mutableIntStateOf(0) }
    var showAgreement by remember { mutableStateOf(false) }
    val selectedPoint = state.points[selectedIndex.coerceIn(state.points.indices)]
    val selectedModels = selectedPoint.instant?.let(state.modelValuesByInstant::get).orEmpty()
    val horizontalScroll = rememberScrollState()

    val tempDomain = remember(state.points) { temperatureDomain(state.points) }
    val rainDomain = remember(state.points) { positiveDomain(state.points.mapNotNull(::rainAmount), minimumMax = 1.0) }
    val windDomain = remember(state.points) {
        positiveDomain(
            state.points.flatMap { listOfNotNull(it.windKmh, it.windGustKmh) },
            minimumMax = 20.0
        )
    }

    val highAgreement = confidenceColor(90)
    val mediumAgreement = confidenceColor(65)
    val lowAgreement = confidenceColor(30)
    val agreementPalette = remember(highAgreement, mediumAgreement, lowAgreement) {
        AgreementPalette(highAgreement, mediumAgreement, lowAgreement)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        GraphicHeaderCard(
            cityName = state.city.name,
            citySubtitle = listOfNotNull(state.city.admin1, state.city.country).distinct().joinToString(" · "),
            points = state.points,
            zone = zone,
            locale = locale,
            showAgreement = showAgreement,
            onShowAgreementChange = { showAgreement = it }
        )

        GraphicChartPanel(
            point = selectedPoint,
            models = selectedModels,
            points = state.points,
            solarByDate = state.solarByDate,
            vigilance = state.vigilance,
            zone = zone,
            locale = locale,
            selectedIndex = selectedIndex,
            onSelectIndex = { selectedIndex = it },
            showAgreement = showAgreement,
            agreementPalette = agreementPalette,
            tempDomain = tempDomain,
            rainDomain = rainDomain,
            windDomain = windDomain,
            horizontalScroll = horizontalScroll
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OpenMeteoAttribution(home = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphicChartPanel(
    point: SimplifiedTimelinePoint,
    models: List<GraphicModelValue>,
    points: List<SimplifiedTimelinePoint>,
    solarByDate: Map<LocalDate, GraphicSolarWindow>,
    vigilance: VigilanceForecast?,
    zone: ZoneId,
    locale: Locale,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    showAgreement: Boolean,
    agreementPalette: AgreementPalette,
    tempDomain: PlotDomain,
    rainDomain: PlotDomain,
    windDomain: PlotDomain,
    horizontalScroll: androidx.compose.foundation.ScrollState
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(TAG_GRAPHIC_CHART_PANEL),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column {
            GraphicSelectionHeader(
                point = point,
                models = models,
                zone = zone,
                locale = locale,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(1.dp))

            GraphicLegend(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                showAgreement = showAgreement,
                agreementPalette = agreementPalette
            )

            Spacer(Modifier.height(2.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                GraphicAxisColumn(
                    tempDomain = tempDomain,
                    rainDomain = rainDomain,
                    windDomain = windDomain
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScroll)
                ) {
                    val chartWidth = GraphicHourWidth * points.size.toFloat()
                    TemperaturePlot(
                        points = points,
                        solarByDate = solarByDate,
                        zone = zone,
                        domain = tempDomain,
                        selectedIndex = selectedIndex,
                        showAgreement = showAgreement,
                        agreementPalette = agreementPalette,
                        onSelectIndex = onSelectIndex,
                        modifier = Modifier.width(chartWidth).height(TemperaturePlotHeight).testTag(TAG_GRAPHIC_TEMPERATURE_PLOT)
                    )
                    RainPlot(
                        points = points,
                        solarByDate = solarByDate,
                        zone = zone,
                        domain = rainDomain,
                        selectedIndex = selectedIndex,
                        showAgreement = showAgreement,
                        agreementPalette = agreementPalette,
                        onSelectIndex = onSelectIndex,
                        modifier = Modifier.width(chartWidth).height(RainPlotHeight).testTag(TAG_GRAPHIC_RAIN_PLOT)
                    )
                    WindPlot(
                        points = points,
                        solarByDate = solarByDate,
                        zone = zone,
                        domain = windDomain,
                        selectedIndex = selectedIndex,
                        showAgreement = showAgreement,
                        agreementPalette = agreementPalette,
                        onSelectIndex = onSelectIndex,
                        modifier = Modifier.width(chartWidth).height(WindPlotHeight).testTag(TAG_GRAPHIC_WIND_PLOT)
                    )
                    GraphicTimeAxis(
                        points = points,
                        zone = zone,
                        locale = locale,
                        selectedIndex = selectedIndex,
                        modifier = Modifier.width(chartWidth).height(TimeAxisHeight)
                    )
                    GraphicVigilanceLane(
                        vigilance = vigilance,
                        points = points,
                        modifier = Modifier.width(chartWidth).height(VigilanceLaneHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphicHeaderCard(
    cityName: String,
    citySubtitle: String,
    points: List<SimplifiedTimelinePoint>,
    zone: ZoneId,
    locale: Locale,
    showAgreement: Boolean,
    onShowAgreementChange: (Boolean) -> Unit
) {
    val firstDate = points.firstOrNull()?.instant?.atZone(zone)?.toLocalDate()
    val lastDate = points.lastOrNull()?.instant?.atZone(zone)?.toLocalDate()
    val rangeFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val period = when {
        firstDate == null || lastDate == null -> ""
        firstDate == lastDate -> rangeFormatter.format(firstDate)
        else -> "${rangeFormatter.format(firstDate)} – ${rangeFormatter.format(lastDate)}"
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cityName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (citySubtitle.isNotBlank()) {
                        Text(
                            citySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (period.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            period,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.graphic_view_intro),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.graphic_view_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.graphic_view_show_convergence),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(checked = showAgreement, onCheckedChange = onShowAgreementChange)
            }

            Text(
                stringResource(R.string.graphic_view_touch_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphicSelectionHeader(
    point: SimplifiedTimelinePoint,
    models: List<GraphicModelValue>,
    zone: ZoneId,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    val instant = point.instant
    val dateTimeFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", locale) }
    val dateLabel = instant?.atZone(zone)?.format(dateTimeFormatter).orEmpty()
    var showModels by remember(instant) { mutableStateOf(false) }
    val condition = point.condition
    val conditionLabel = if (condition != null) stringResource(weatherConditionLabelRes(condition)) else null
    val gustShort = stringResource(R.string.graphic_view_gust_short)

    Column(modifier = modifier.fillMaxWidth().testTag(TAG_GRAPHIC_SELECTION_HEADER)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    WeatherIconDecorative(point.condition, size = 25.dp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    conditionLabel ?: stringResource(R.string.graphic_view_central_forecast),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (models.isNotEmpty()) {
                TextButton(onClick = { showModels = !showModels }) {
                    Text(
                        "${stringResource(R.string.graphic_view_models_count, point.modelCount)} ${if (showModels) "▴" else "▾"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    stringResource(R.string.graphic_view_models_count, point.modelCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GraphicMetricSummaryChip(
                label = stringResource(R.string.graphic_view_temperature),
                icon = Icons.Outlined.Thermostat,
                accent = temperatureMetricAccent(),
                value = point.temperatureC?.let { "${format(it, 1)} °C" } ?: "—",
                detail = formatRange(point.temperatureMinAcrossModels, point.temperatureMaxAcrossModels, "°C", 1),
                agreement = point.consensusFor(ForecastMetric.TEMPERATURE)?.percent
            )
            GraphicMetricSummaryChip(
                label = stringResource(R.string.graphic_view_rain),
                icon = Icons.Outlined.WaterDrop,
                accent = precipitationMetricAccent(),
                value = rainAmount(point)?.let { "${format(it, 1)} mm" } ?: "—",
                detail = buildString {
                    append(formatRange(point.precipitationMinAcrossModelsMm, point.precipitationMaxAcrossModelsMm, "mm", 1))
                    point.precipitationPercent?.let { append(" · ${it}%") }
                },
                agreement = point.precipitationAmountConvergencePercent,
                agreementTestTag = TAG_GRAPHIC_RAIN_SELECTION_CONVERGENCE
            )
            GraphicMetricSummaryChip(
                label = stringResource(R.string.graphic_view_wind),
                icon = Icons.Outlined.Air,
                accent = windMetricAccent(),
                value = point.windKmh?.let { "${format(it, 0)} km/h" } ?: "—",
                detail = buildString {
                    append(formatRange(point.windMinAcrossModels, point.windMaxAcrossModels, "km/h", 0))
                    point.windGustKmh?.let { append(" · $gustShort ${format(it, 0)}") }
                    point.windDirectionDeg?.let { append(" · ${it}°") }
                },
                agreement = point.consensusFor(ForecastMetric.WIND)?.percent
            )
        }

        if (models.isNotEmpty()) {
            AnimatedVisibility(showModels) {
                Column(modifier = Modifier.padding(top = 5.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Spacer(Modifier.height(3.dp))
                    models.forEach { row -> GraphicModelRow(row) }
                }
            }
        }
    }
}

@Composable
private fun GraphicMetricSummaryChip(
    label: String,
    icon: ImageVector,
    accent: Color,
    value: String,
    detail: String,
    agreement: Int?,
    agreementTestTag: String? = null
) {
    Row(
        modifier = Modifier.padding(end = 6.dp, top = 2.dp, bottom= 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent.copy(alpha = 0.78f))
        )
        Spacer(Modifier.width(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = accent)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                agreement?.let {
                    Text(
                        "· $it%",
                        modifier = agreementTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = confidenceColor(it),
                        maxLines = 1
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (detail.isNotBlank() && detail != "—") {
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphicModelRow(row: GraphicModelValue) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.modelName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            row.temperatureC?.let { "${format(it, 1)}°" } ?: "—",
            modifier = Modifier.width(54.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            row.precipitationMm?.let { "${format(it, 1)} mm" } ?: "—",
            modifier = Modifier.width(70.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            row.windKmh?.let { "${format(it, 0)} km/h" } ?: "—",
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private enum class LegendSymbol {
    SOLID_LINE_POINT,
    DASHED_LINE_POINT,
    BAND,
    BAR,
    FILL
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphicLegend(
    modifier: Modifier = Modifier,
    showAgreement: Boolean,
    agreementPalette: AgreementPalette
) {
    val rain = precipitationMetricAccent()
    val wind = windMetricAccent()
    val temperature = temperatureMetricAccent()
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendItem(LegendSymbol.SOLID_LINE_POINT, temperature, stringResource(R.string.graphic_view_temperature))
        LegendItem(LegendSymbol.BAND, temperature.copy(alpha = 0.28f), stringResource(R.string.graphic_view_dispersion))
        LegendItem(LegendSymbol.BAR, rain, stringResource(R.string.graphic_view_rain))
        LegendItem(LegendSymbol.SOLID_LINE_POINT, wind, stringResource(R.string.graphic_view_wind))
        LegendItem(LegendSymbol.DASHED_LINE_POINT, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.graphic_view_gusts))
        LegendItem(LegendSymbol.FILL, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), stringResource(R.string.graphic_view_night))
        if (showAgreement) {
            LegendItem(LegendSymbol.FILL, agreementPalette.low, stringResource(R.string.graphic_view_divergence))
            LegendItem(LegendSymbol.FILL, agreementPalette.high, stringResource(R.string.graphic_view_strong_agreement))
        }
    }
}

@Composable
private fun LegendItem(symbol: LegendSymbol, color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(26.dp)
                .height(12.dp)
                .testTag(TAG_GRAPHIC_LEGEND_SYMBOL)
        ) {
            val centerY = size.height / 2f
            when (symbol) {
                LegendSymbol.SOLID_LINE_POINT -> {
                    drawLine(
                        color = color,
                        start = Offset(1.dp.toPx(), centerY),
                        end = Offset(size.width - 1.dp.toPx(), centerY),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = color, radius = 2.6.dp.toPx(), center = Offset(size.width / 2f, centerY))
                }
                LegendSymbol.DASHED_LINE_POINT -> {
                    drawLine(
                        color = color,
                        start = Offset(1.dp.toPx(), centerY),
                        end = Offset(size.width - 1.dp.toPx(), centerY),
                        strokeWidth = 1.8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(size.width / 2f, centerY))
                }
                LegendSymbol.BAND -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(1.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 4.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
                LegendSymbol.BAR -> {
                    val barWidth = 5.dp.toPx()
                    drawRoundRect(
                        color = color.copy(alpha = 0.78f),
                        topLeft = Offset(size.width / 2f - barWidth / 2f, 1.dp.toPx()),
                        size = Size(barWidth, size.height - 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
                LegendSymbol.FILL -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(1.dp.toPx(), 2.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 4.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GraphicAxisColumn(
    tempDomain: PlotDomain,
    rainDomain: PlotDomain,
    windDomain: PlotDomain
) {
    Column(
        modifier = Modifier
            .width(GraphicAxisWidth)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        PlotAxis(
            label = stringResource(R.string.graphic_view_temperature),
            icon = Icons.Outlined.Thermostat,
            iconTint = temperatureMetricAccent(),
            unit = "°C",
            domain = tempDomain,
            height = TemperaturePlotHeight,
            decimals = 0
        )
        PlotAxis(
            label = stringResource(R.string.graphic_view_rain),
            icon = Icons.Outlined.WaterDrop,
            iconTint = precipitationMetricAccent(),
            unit = "mm/h",
            domain = rainDomain,
            height = RainPlotHeight,
            decimals = 1
        )
        PlotAxis(
            label = stringResource(R.string.graphic_view_wind),
            icon = Icons.Outlined.Air,
            iconTint = windMetricAccent(),
            unit = "km/h",
            domain = windDomain,
            height = WindPlotHeight,
            decimals = 0
        )
        AxisLabelSlot(
            title = stringResource(R.string.graphic_view_time),
            subtitle = stringResource(R.string.graphic_view_hour_day),
            height = TimeAxisHeight
        )
        AxisLabelSlot(
            title = stringResource(R.string.graphic_view_vigilance),
            subtitle = stringResource(R.string.graphic_view_official),
            height = VigilanceLaneHeight
        )
    }
}

@Composable
private fun PlotAxis(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    unit: String,
    domain: PlotDomain,
    height: Dp,
    decimals: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(iconTint.copy(alpha = 0.035f))
            .padding(horizontal = 5.dp)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 6.dp),
            shape = RoundedCornerShape(9.dp),
            color = iconTint.copy(alpha = 0.12f),
            contentColor = iconTint
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp).testTag(TAG_GRAPHIC_AXIS_ICON),
                    tint = iconTint
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = iconTint,
                    maxLines = 1
                )
            }
        }
        domain.ticks.forEach { tick ->
            val y = valueToYDp(tick, domain, height)
            Text(
                text = format(tick, decimals),
                style = MaterialTheme.typography.labelSmall,
                color = iconTint.copy(alpha = 0.92f),
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .offset(y = y - 8.dp)
                    .align(Alignment.TopEnd)
                    .width(GraphicAxisWidth - 10.dp)
            )
        }
    }
}

@Composable
private fun AxisLabelSlot(title: String, subtitle: String, height: Dp) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TemperaturePlot(
    points: List<SimplifiedTimelinePoint>,
    solarByDate: Map<LocalDate, GraphicSolarWindow>,
    zone: ZoneId,
    domain: PlotDomain,
    selectedIndex: Int,
    showAgreement: Boolean,
    agreementPalette: AgreementPalette,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val temperature = temperatureMetricAccent()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val night = onSurface.copy(alpha = 0.055f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    val band = temperature.copy(alpha = 0.14f)
    val daylight = remember(points, solarByDate, zone) { daylightFlags(points, solarByDate, zone) }
    val heat = remember(points) { points.map { it.temperatureC?.let(::temperatureHeatmapColor) } }
    val agreement = remember(points, agreementPalette) {
        points.map { agreementPalette.colorFor(it.consensusFor(ForecastMetric.TEMPERATURE)?.percent) }
    }

    Box(modifier) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(points.size) {
                detectTapGestures { offset ->
                    onSelectIndex(indexForX(offset.x, size.width.toFloat(), points.size))
                }
            }
        ) {
            drawTimelineBackground(points, daylight, heat, agreement, showAgreement, surface, night, heatAlpha = GRAPHIC_TEMPERATURE_HEAT_ALPHA)
            drawTimelineGrid(points, zone, domain, grid)

            val upperPath = Path()
        val lower = mutableListOf<Offset>()
        var bandStarted = false
        points.forEachIndexed { index, point ->
            val min = point.temperatureMinAcrossModels
            val max = point.temperatureMaxAcrossModels
            if (min != null && max != null) {
                val x = pointCenterX(index, points.size)
                val upper = Offset(x, valueToYPx(max, domain, size.height))
                val low = Offset(x, valueToYPx(min, domain, size.height))
                if (!bandStarted) {
                    upperPath.moveTo(upper.x, upper.y)
                    bandStarted = true
                } else upperPath.lineTo(upper.x, upper.y)
                lower += low
            }
        }
        if (bandStarted && lower.isNotEmpty()) {
            lower.asReversed().forEach { upperPath.lineTo(it.x, it.y) }
            upperPath.close()
            drawPath(upperPath, color = band)
        }

        drawAgreementHalo(
            values = points.map { it.temperatureC },
            domain = domain,
            colors = agreement,
            enabled = showAgreement,
            strokeWidth = 8.dp.toPx()
        )
        drawLineSeries(
            values = points.map { it.temperatureC },
            domain = domain,
            color = temperature,
            strokeWidth = 2.4.dp.toPx(),
            pointRadius = 2.5.dp.toPx()
        )
        drawSelectedRuler(points, selectedIndex, onSurface)
            points.getOrNull(selectedIndex)?.temperatureC?.let { value ->
                drawCircle(
                    color = temperature,
                    radius = 5.dp.toPx(),
                    center = Offset(pointCenterX(selectedIndex, points.size), valueToYPx(value, domain, size.height))
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .align(Alignment.TopStart)
                .padding(top = 3.dp)
        ) {
            points.forEach { point ->
                Box(
                    modifier = Modifier
                        .width(GraphicHourWidth)
                        .height(26.dp)
                        .testTag(TAG_GRAPHIC_CONDITION_ICON),
                    contentAlignment = Alignment.Center
                ) {
                    WeatherIconDecorative(
                        condition = point.condition ?: WeatherCondition.UNKNOWN,
                        size = 19.dp
                    )
                }
            }
        }
        points.getOrNull(selectedIndex)?.let { selected ->
            val central = selected.temperatureC?.let { "${format(it, 1)} °C" } ?: "—"
            val range = formatRange(
                selected.temperatureMinAcrossModels,
                selected.temperatureMaxAcrossModels,
                "°C",
                1
            )
            TemperatureSelectionBadge(
                value = central,
                range = range.takeUnless { it == "—" },
                selectedIndex = selectedIndex,
                pointCount = points.size,
                yOffset = 34.dp
            )
        }
    }
}

@Composable
private fun RainPlot(
    points: List<SimplifiedTimelinePoint>,
    solarByDate: Map<LocalDate, GraphicSolarWindow>,
    zone: ZoneId,
    domain: PlotDomain,
    selectedIndex: Int,
    showAgreement: Boolean,
    agreementPalette: AgreementPalette,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val rain = precipitationMetricAccent()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val night = onSurface.copy(alpha = 0.055f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    val daylight = remember(points, solarByDate, zone) { daylightFlags(points, solarByDate, zone) }
    val heat = remember(points, rain) {
        points.map { point -> rain.copy(alpha = ((point.precipitationPercent ?: 0) / 100f).coerceIn(0f, 1f)) }
    }
    val agreement = remember(points, agreementPalette) {
        points.map { agreementPalette.colorFor(it.consensusFor(ForecastMetric.PRECIPITATION)?.percent) }
    }

    Box(modifier) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(points.size) {
                detectTapGestures { offset ->
                    onSelectIndex(indexForX(offset.x, size.width.toFloat(), points.size))
                }
            }
        ) {
            drawTimelineBackground(points, daylight, heat, agreement, showAgreement, surface, night, heatAlpha = GRAPHIC_RAIN_HEAT_ALPHA)
            drawTimelineGrid(points, zone, domain, grid)
            val baseline = valueToYPx(0.0, domain, size.height)
        val columnWidth = size.width / points.size
        points.forEachIndexed { index, point ->
            val amount = rainAmount(point) ?: return@forEachIndexed
            val x = index * columnWidth + columnWidth * 0.20f
            val y = valueToYPx(amount, domain, size.height)
            val height = (baseline - y).coerceAtLeast(if (amount > 0.0) 2.dp.toPx() else 0f)
            val agreementColor = agreement[index]
            val color = if (showAgreement) {
                agreementColor.copy(alpha = (agreementColor.alpha * 0.88f).coerceIn(0f, 1f))
            } else rain
            drawRoundRect(
                color = rain.copy(alpha = 0.72f),
                topLeft = Offset(x, baseline - height),
                size = Size(columnWidth * 0.60f, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            if (showAgreement && height > 1f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, baseline - height),
                    size = Size(columnWidth * 0.60f, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = 1.3.dp.toPx())
                )
            }
        }
            drawSelectedRuler(points, selectedIndex, onSurface)
        }
        points.getOrNull(selectedIndex)?.let { selected ->
            val amount = rainAmount(selected)?.let { "${format(it, 1)} mm" } ?: "—"
            RainSelectionBadge(
                amount = amount,
                probability = selected.precipitationPercent?.let { "${it}%" },
                selectedIndex = selectedIndex,
                pointCount = points.size
            )
        }
    }
}

@Composable
private fun WindPlot(
    points: List<SimplifiedTimelinePoint>,
    solarByDate: Map<LocalDate, GraphicSolarWindow>,
    zone: ZoneId,
    domain: PlotDomain,
    selectedIndex: Int,
    showAgreement: Boolean,
    agreementPalette: AgreementPalette,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val wind = windMetricAccent()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val night = onSurface.copy(alpha = 0.055f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f)
    val daylight = remember(points, solarByDate, zone) { daylightFlags(points, solarByDate, zone) }
    val heat = remember(points, wind, domain.max) {
        points.map { point ->
            val strength = (max(point.windKmh ?: 0.0, point.windGustKmh ?: 0.0) / domain.max).toFloat().coerceIn(0f, 1f)
            wind.copy(alpha = strength)
        }
    }
    val agreement = remember(points, agreementPalette) {
        points.map { agreementPalette.colorFor(it.consensusFor(ForecastMetric.WIND)?.percent) }
    }
    val gustShort = stringResource(R.string.graphic_view_gust_short)

    Box(modifier) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(points.size) {
                detectTapGestures { offset ->
                    onSelectIndex(indexForX(offset.x, size.width.toFloat(), points.size))
                }
            }
        ) {
            drawTimelineBackground(points, daylight, heat, agreement, showAgreement, surface, night, heatAlpha = GRAPHIC_WIND_HEAT_ALPHA)
            drawTimelineGrid(points, zone, domain, grid)
        drawAgreementHalo(
            values = points.map { it.windKmh },
            domain = domain,
            colors = agreement,
            enabled = showAgreement,
            strokeWidth = 7.dp.toPx()
        )
        drawLineSeries(
            values = points.map { it.windGustKmh },
            domain = domain,
            color = onSurface.copy(alpha = 0.58f),
            strokeWidth = 1.6.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            pointRadius = 1.8.dp.toPx()
        )
        drawLineSeries(
            values = points.map { it.windKmh },
            domain = domain,
            color = wind,
            strokeWidth = 2.4.dp.toPx(),
            pointRadius = 2.4.dp.toPx()
        )
        drawSelectedRuler(points, selectedIndex, onSurface)
            points.getOrNull(selectedIndex)?.windKmh?.let { value ->
                drawCircle(
                    color = wind,
                    radius = 4.5.dp.toPx(),
                    center = Offset(pointCenterX(selectedIndex, points.size), valueToYPx(value, domain, size.height))
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlotBottomPadding)
                .align(Alignment.BottomStart)
        ) {
            points.forEach { point ->
                Box(
                    modifier = Modifier
                        .width(GraphicHourWidth)
                        .height(PlotBottomPadding),
                    contentAlignment = Alignment.Center
                ) {
                    point.windDirectionDeg?.let { direction ->
                        Box(modifier = Modifier.testTag(TAG_GRAPHIC_WIND_DIRECTION_ARROW)) {
                            WindArrow(directionDegrees = direction, size = 13.dp)
                        }
                    }
                }
            }
        }
        points.getOrNull(selectedIndex)?.let { selected ->
            WindSelectionBadge(
                mean = selected.windKmh?.let { "${format(it, 0)} km/h" } ?: "—",
                gust = selected.windGustKmh?.let { "$gustShort ${format(it, 0)} km/h" },
                direction = selected.windDirectionDeg?.let { "${it}°" },
                selectedIndex = selectedIndex,
                pointCount = points.size
            )
        }
    }
}

@Composable
private fun TemperatureSelectionBadge(
    value: String,
    range: String?,
    selectedIndex: Int,
    pointCount: Int,
    yOffset: Dp = 6.dp
) {
    StructuredSelectionBadge(
        icon = Icons.Outlined.Thermostat,
        accent = temperatureMetricAccent(),
        primary = value,
        primaryTag = TAG_GRAPHIC_TEMPERATURE_TOOLTIP_VALUE,
        secondary = listOfNotNull(
            range?.let { SelectionBadgeSegment(it, TAG_GRAPHIC_TEMPERATURE_TOOLTIP_RANGE) }
        ),
        containerTag = TAG_GRAPHIC_TEMPERATURE_TOOLTIP,
        selectedIndex = selectedIndex,
        pointCount = pointCount,
        yOffset = yOffset
    )
}

@Composable
private fun RainSelectionBadge(
    amount: String,
    probability: String?,
    selectedIndex: Int,
    pointCount: Int
) {
    StructuredSelectionBadge(
        icon = Icons.Outlined.WaterDrop,
        accent = precipitationMetricAccent(),
        primary = amount,
        primaryTag = TAG_GRAPHIC_RAIN_TOOLTIP_AMOUNT,
        secondary = listOfNotNull(
            probability?.let { SelectionBadgeSegment(it, TAG_GRAPHIC_RAIN_TOOLTIP_PROBABILITY) }
        ),
        containerTag = TAG_GRAPHIC_RAIN_TOOLTIP,
        selectedIndex = selectedIndex,
        pointCount = pointCount
    )
}

@Composable
private fun WindSelectionBadge(
    mean: String,
    gust: String?,
    direction: String?,
    selectedIndex: Int,
    pointCount: Int
) {
    StructuredSelectionBadge(
        icon = Icons.Outlined.Air,
        accent = windMetricAccent(),
        primary = mean,
        primaryTag = TAG_GRAPHIC_WIND_TOOLTIP_MEAN,
        secondary = listOfNotNull(
            gust?.let { SelectionBadgeSegment(it, TAG_GRAPHIC_WIND_TOOLTIP_GUST) },
            direction?.let { SelectionBadgeSegment(it, TAG_GRAPHIC_WIND_TOOLTIP_DIRECTION) }
        ),
        containerTag = TAG_GRAPHIC_WIND_TOOLTIP,
        selectedIndex = selectedIndex,
        pointCount = pointCount
    )
}

private data class SelectionBadgeSegment(
    val text: String,
    val tag: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StructuredSelectionBadge(
    icon: ImageVector,
    accent: Color,
    primary: String,
    primaryTag: String,
    secondary: List<SelectionBadgeSegment>,
    containerTag: String,
    selectedIndex: Int,
    pointCount: Int,
    yOffset: Dp = 6.dp
) {
    val density = LocalDensity.current
    var badgeWidthPx by remember(containerTag) { mutableIntStateOf(0) }
    val hourWidthPx = with(density) { GraphicHourWidth.toPx() }
    val trackWidthPx = hourWidthPx * pointCount
    val centerPx = hourWidthPx * (selectedIndex + 0.5f)
    val xPx = selectionBadgeStartPx(
        centerPx = centerPx,
        trackWidthPx = trackWidthPx,
        badgeWidthPx = badgeWidthPx
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = yOffset)
    ) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(xPx, 0) }
                .onSizeChanged { badgeWidthPx = it.width }
                .testTag(containerTag),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            tonalElevation = 2.dp
        ) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = accent
                    )
                    Text(
                        text = primary,
                        modifier = Modifier.testTag(primaryTag),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                secondary.forEach { segment ->
                    Text(
                        text = segment.text,
                        modifier = Modifier.testTag(segment.tag),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

internal fun selectionBadgeStartPx(
    centerPx: Float,
    trackWidthPx: Float,
    badgeWidthPx: Int
): Int {
    if (badgeWidthPx <= 0) return centerPx.toInt().coerceAtLeast(0)
    val maxStart = (trackWidthPx - badgeWidthPx).coerceAtLeast(0f)
    return (centerPx - badgeWidthPx / 2f)
        .coerceIn(0f, maxStart)
        .toInt()
}

@Composable
private fun GraphicTimeAxis(
    points: List<SimplifiedTimelinePoint>,
    zone: ZoneId,
    locale: Locale,
    selectedIndex: Int,
    modifier: Modifier = Modifier
) {
    val hourFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val dates = remember(points, zone) { points.map { it.instant?.atZone(zone)?.toLocalDate() } }
    val selectedDate = dates.getOrNull(selectedIndex)
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val separator = primary.copy(alpha = 0.30f)

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.height(28.dp)) {
            var start = 0
            var dayIndex = 0
            while (start < points.size) {
                val date = dates[start]
                var end = start
                while (end + 1 < points.size && dates[end + 1] == date) end++
                val span = end - start + 1
                val isSelectedDay = date != null && date == selectedDate
                val background = when {
                    isSelectedDay -> primary.copy(alpha = 0.075f)
                    dayIndex % 2 == 0 -> MaterialTheme.colorScheme.surfaceContainerLow
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
                }
                Box(
                    modifier = Modifier
                        .width(GraphicHourWidth * span.toFloat())
                        .height(28.dp)
                        .background(background)
                        .testTag(TAG_GRAPHIC_DAY_HEADER)
                ) {
                    if (dayIndex > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(separator)
                        )
                    }
                    Text(
                        text = date?.format(dayFormatter) ?: "",
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 9.dp, end = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelectedDay) primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                start = end + 1
                dayIndex++
            }
        }

        Row(
            modifier = Modifier
                .height(42.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            points.forEachIndexed { index, point ->
                val zdt = point.instant?.atZone(zone)
                val isSelected = index == selectedIndex
                val isDayStart = index > 0 && dates[index] != dates[index - 1]
                val isMidnight = zdt?.hour == 0
                Box(
                    modifier = Modifier
                        .width(GraphicHourWidth)
                        .fillMaxSize()
                        .testTag(TAG_GRAPHIC_HOUR_CELL),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDayStart) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(separator)
                        )
                    }
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = primary.copy(alpha = 0.13f),
                            contentColor = primary
                        ) {
                            Text(
                                text = zdt?.format(hourFormatter) ?: "",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    } else {
                        Text(
                            text = zdt?.format(hourFormatter) ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMidnight) primary else onSurfaceVariant,
                            fontWeight = if (isMidnight) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .width(if (isMidnight) 2.dp else 1.dp)
                            .height(if (isMidnight) 6.dp else 4.dp)
                            .background(
                                if (isMidnight) primary.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphicVigilanceLane(
    vigilance: VigilanceForecast?,
    points: List<SimplifiedTimelinePoint>,
    modifier: Modifier = Modifier
) {
    val start = points.firstOrNull()?.instant
    val end = points.lastOrNull()?.instant?.plusSeconds(3_600)
    val alerts = remember(vigilance, start, end) {
        if (start == null || end == null) emptyList()
        else vigilance?.activeAlerts.orEmpty().mapNotNull { alert ->
            val intervals = alert.intervals.filter { interval ->
                val begin = interval.begin ?: return@filter false
                val finish = interval.end ?: return@filter false
                begin < end && finish > start
            }
            if (intervals.isEmpty()) null else alert to intervals
        }.take(3)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(bottomEnd = 14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.75f))
            .padding(vertical = 8.dp)
    ) {
        if (alerts.isEmpty() || start == null || end == null) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓", color = Color(0xFF43A047), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.graphic_view_no_vigilance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                alerts.forEach { (alert, intervals) ->
                    Box(Modifier.fillMaxWidth().height(28.dp)) {
                        intervals.forEach { interval ->
                            val begin = maxOf(start, requireNotNull(interval.begin))
                            val finish = minOf(end, requireNotNull(interval.end))
                            val totalSeconds = (end.epochSecond - start.epochSecond).coerceAtLeast(1)
                            val leftFraction = (begin.epochSecond - start.epochSecond).toFloat() / totalSeconds
                            val widthFraction = (finish.epochSecond - begin.epochSecond).toFloat() / totalSeconds
                            val left = GraphicHourWidth * points.size.toFloat() * leftFraction
                            val width = (GraphicHourWidth * points.size.toFloat() * widthFraction).coerceAtLeast(12.dp)
                            val color = vigilanceColor(interval.color)
                            Surface(
                                modifier = Modifier.offset(x = left).width(width).height(28.dp),
                                shape = RoundedCornerShape(7.dp),
                                color = color.copy(alpha = 0.20f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(vigilanceIcon(alert.phenomenon), style = MaterialTheme.typography.labelSmall)
                                    if (width > 72.dp) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            vigilancePhenomenonLabel(alert.phenomenon),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun vigilancePhenomenonLabel(phenomenon: VigilancePhenomenon): String = stringResource(
    when (phenomenon) {
        VigilancePhenomenon.WIND -> R.string.vigilance_phenomenon_wind
        VigilancePhenomenon.RAIN_FLOOD -> R.string.vigilance_phenomenon_rain_flood
        VigilancePhenomenon.THUNDERSTORMS -> R.string.vigilance_phenomenon_thunderstorms
        VigilancePhenomenon.FLOODS -> R.string.vigilance_phenomenon_floods
        VigilancePhenomenon.SNOW_ICE -> R.string.vigilance_phenomenon_snow_ice
        VigilancePhenomenon.HEATWAVE -> R.string.vigilance_phenomenon_heatwave
        VigilancePhenomenon.EXTREME_COLD -> R.string.vigilance_phenomenon_extreme_cold
        VigilancePhenomenon.AVALANCHES -> R.string.vigilance_phenomenon_avalanches
        VigilancePhenomenon.COASTAL_FLOODING -> R.string.vigilance_phenomenon_coastal_flooding
        VigilancePhenomenon.UNKNOWN -> R.string.vigilance_phenomenon_unknown
    }
)

private fun vigilanceIcon(phenomenon: VigilancePhenomenon): String = when (phenomenon) {
    VigilancePhenomenon.WIND -> "↝"
    VigilancePhenomenon.RAIN_FLOOD -> "☂"
    VigilancePhenomenon.THUNDERSTORMS -> "ϟ"
    VigilancePhenomenon.FLOODS -> "≋"
    VigilancePhenomenon.SNOW_ICE -> "❄"
    VigilancePhenomenon.HEATWAVE -> "☀"
    VigilancePhenomenon.EXTREME_COLD -> "✦"
    VigilancePhenomenon.AVALANCHES -> "▲"
    VigilancePhenomenon.COASTAL_FLOODING -> "≈"
    VigilancePhenomenon.UNKNOWN -> "!"
}

private fun vigilanceColor(color: VigilanceColor): Color = when (color) {
    VigilanceColor.GREEN -> Color(0xFF43A047)
    VigilanceColor.YELLOW -> Color(0xFFFFC928)
    VigilanceColor.ORANGE -> Color(0xFFF57C00)
    VigilanceColor.RED -> Color(0xFFD32F2F)
}

internal data class PlotDomain(
    val min: Double,
    val max: Double,
    val ticks: List<Double>
)

private data class AgreementPalette(
    val high: Color,
    val medium: Color,
    val low: Color
) {
    fun colorFor(percent: Int?): Color = when {
        percent == null -> medium.copy(alpha = 0f)
        percent >= 80 -> high
        percent >= 50 -> medium
        else -> low
    }
}

internal fun temperatureDomain(points: List<SimplifiedTimelinePoint>): PlotDomain {
    val values = points.flatMap { point ->
        listOfNotNull(point.temperatureMinAcrossModels, point.temperatureC, point.temperatureMaxAcrossModels)
    }
    if (values.isEmpty()) return PlotDomain(0.0, 30.0, listOf(0.0, 10.0, 20.0, 30.0))
    val rawMin = values.minOrNull() ?: 0.0
    val rawMax = values.maxOrNull() ?: 30.0
    val padding = max(2.0, (rawMax - rawMin) * 0.12)
    val min = floor(rawMin - padding)
    val max = ceil(rawMax + padding).coerceAtLeast(min + 1.0)
    return PlotDomain(min, max, linearTicks(min, max, 5))
}

private fun positiveDomain(values: List<Double>, minimumMax: Double): PlotDomain {
    val rawMax = max(minimumMax, values.maxOrNull() ?: minimumMax)
    val step = when {
        rawMax <= 4 -> 1.0
        rawMax <= 10 -> 2.0
        rawMax <= 25 -> 5.0
        rawMax <= 60 -> 10.0
        rawMax <= 120 -> 20.0
        else -> 50.0
    }
    val max = ceil(rawMax / step) * step
    val ticks = (0..3).map { max * it / 3.0 }
    return PlotDomain(0.0, max, ticks)
}

private fun linearTicks(min: Double, max: Double, count: Int): List<Double> =
    (0 until count).map { index -> min + (max - min) * index / (count - 1).coerceAtLeast(1) }

private fun valueToYDp(value: Double, domain: PlotDomain, height: Dp): Dp {
    val usable = height - PlotTopPadding - PlotBottomPadding
    val fraction = ((domain.max - value) / (domain.max - domain.min)).coerceIn(0.0, 1.0).toFloat()
    return PlotTopPadding + usable * fraction
}

private fun DrawScope.valueToYPx(value: Double, domain: PlotDomain, heightPx: Float): Float {
    val top = PlotTopPadding.toPx()
    val bottom = PlotBottomPadding.toPx()
    val usable = (heightPx - top - bottom).coerceAtLeast(1f)
    val fraction = ((domain.max - value) / (domain.max - domain.min)).coerceIn(0.0, 1.0).toFloat()
    return top + usable * fraction
}

private fun DrawScope.drawTimelineBackground(
    points: List<SimplifiedTimelinePoint>,
    daylight: List<Boolean>,
    heatColors: List<Color?>,
    agreementColors: List<Color>,
    showAgreement: Boolean,
    surface: Color,
    night: Color,
    heatAlpha: Float = 0.11f
) {
    if (points.isEmpty()) return
    val width = size.width / points.size
    points.indices.forEach { index ->
        val left = index * width
        drawRect(surface, topLeft = Offset(left, 0f), size = Size(width, size.height))
        if (!daylight.getOrElse(index) { true }) {
            drawRect(night, topLeft = Offset(left, 0f), size = Size(width, size.height))
        }
        heatColors.getOrNull(index)?.let { heat ->
            drawRect(
                heat.copy(alpha = (heat.alpha * heatAlpha).coerceIn(0f, 1f)),
                topLeft = Offset(left, 0f),
                size = Size(width, size.height)
            )
        }
        if (showAgreement) {
            val agreement = agreementColors.getOrElse(index) { Color.Transparent }
            drawRect(
                agreement.copy(alpha = (agreement.alpha * 0.075f).coerceIn(0f, 1f)),
                topLeft = Offset(left, 0f),
                size = Size(width, size.height)
            )
        }
    }
}

private fun DrawScope.drawTimelineGrid(
    points: List<SimplifiedTimelinePoint>,
    zone: ZoneId,
    domain: PlotDomain,
    color: Color
) {
    if (points.isEmpty()) return
    domain.ticks.forEach { tick ->
        val y = valueToYPx(tick, domain, size.height)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
    val width = size.width / points.size
    points.forEachIndexed { index, point ->
        val zdt = point.instant?.atZone(zone) ?: return@forEachIndexed
        if (index > 0 && zdt.hour == 0) {
            val x = index * width
            drawLine(color.copy(alpha = 0.9f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
        } else if (zdt.hour % 6 == 0) {
            val x = index * width
            drawLine(color.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.8.dp.toPx())
        }
    }
}

private fun DrawScope.drawAgreementHalo(
    values: List<Double?>,
    domain: PlotDomain,
    colors: List<Color>,
    enabled: Boolean,
    strokeWidth: Float
) {
    if (!enabled || values.size < 2) return
    for (index in 0 until values.lastIndex) {
        val a = values[index] ?: continue
        val b = values[index + 1] ?: continue
        val agreement = colors.getOrElse(index) { Color.Transparent }
        drawLine(
            color = agreement.copy(alpha = (agreement.alpha * 0.34f).coerceIn(0f, 1f)),
            start = Offset(pointCenterX(index, values.size), valueToYPx(a, domain, size.height)),
            end = Offset(pointCenterX(index + 1, values.size), valueToYPx(b, domain, size.height)),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawLineSeries(
    values: List<Double?>,
    domain: PlotDomain,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
    pointRadius: Float = 0f
) {
    if (values.isEmpty()) return
    for (index in 0 until values.lastIndex) {
        val a = values[index] ?: continue
        val b = values[index + 1] ?: continue
        drawLine(
            color = color,
            start = Offset(pointCenterX(index, values.size), valueToYPx(a, domain, size.height)),
            end = Offset(pointCenterX(index + 1, values.size), valueToYPx(b, domain, size.height)),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = pathEffect
        )
    }
    if (pointRadius > 0f) {
        seriesMarkerIndices(values).forEach { index ->
            val value = values[index] ?: return@forEach
            drawCircle(
                color = color,
                radius = pointRadius,
                center = Offset(pointCenterX(index, values.size), valueToYPx(value, domain, size.height))
            )
        }
    }
}

internal fun seriesMarkerIndices(values: List<Double?>): List<Int> =
    values.indices.filter { values[it] != null }

private fun DrawScope.drawSelectedRuler(
    points: List<SimplifiedTimelinePoint>,
    selectedIndex: Int,
    color: Color
) {
    if (selectedIndex !in points.indices) return
    val x = pointCenterX(selectedIndex, points.size)
    drawLine(
        color = color.copy(alpha = 0.38f),
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1.2.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
    )
}

private fun DrawScope.pointCenterX(index: Int, count: Int): Float =
    (index + 0.5f) * (size.width / count.coerceAtLeast(1))

internal fun indexForX(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    return floor((x / width) * count).toInt().coerceIn(0, count - 1)
}

internal fun daylightFlags(
    points: List<SimplifiedTimelinePoint>,
    solarByDate: Map<LocalDate, GraphicSolarWindow>,
    zone: ZoneId
): List<Boolean> = points.map { point ->
    val instant = point.instant ?: return@map true
    val date = instant.atZone(zone).toLocalDate()
    val window = solarByDate[date] ?: return@map true
    val sunrise = window.sunrise ?: return@map true
    val sunset = window.sunset ?: return@map true
    !instant.isBefore(sunrise) && instant.isBefore(sunset)
}

private fun rainAmount(point: SimplifiedTimelinePoint): Double? =
    point.precipitationMm ?: point.precipitationConditionalMm

private fun format(value: Double, decimals: Int): String =
    "%1$.${decimals}f".format(Locale.getDefault(), value)

private fun formatRange(min: Double?, max: Double?, unit: String, decimals: Int): String =
    if (min != null && max != null) "${format(min, decimals)}–${format(max, decimals)} $unit" else "—"
