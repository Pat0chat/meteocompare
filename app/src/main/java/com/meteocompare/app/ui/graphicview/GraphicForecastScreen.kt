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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
private val GraphicAxisWidth = 82.dp
private val TemperaturePlotHeight = 252.dp
private val RainPlotHeight = 150.dp
private val WindPlotHeight = 150.dp
private val TimeAxisHeight = 76.dp
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
            .padding(bottom = 20.dp)
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

        GraphicSelectionCard(
            point = selectedPoint,
            models = selectedModels,
            zone = zone,
            locale = locale,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )

        GraphicLegend(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            showAgreement = showAgreement,
            agreementPalette = agreementPalette
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.34f))
        ) {
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
                val chartWidth = GraphicHourWidth * state.points.size.toFloat()
                TemperaturePlot(
                    points = state.points,
                    solarByDate = state.solarByDate,
                    zone = zone,
                    domain = tempDomain,
                    selectedIndex = selectedIndex,
                    showAgreement = showAgreement,
                    agreementPalette = agreementPalette,
                    onSelectIndex = { selectedIndex = it },
                    modifier = Modifier.width(chartWidth).height(TemperaturePlotHeight).testTag(TAG_GRAPHIC_TEMPERATURE_PLOT)
                )
                RainPlot(
                    points = state.points,
                    solarByDate = state.solarByDate,
                    zone = zone,
                    domain = rainDomain,
                    selectedIndex = selectedIndex,
                    showAgreement = showAgreement,
                    agreementPalette = agreementPalette,
                    onSelectIndex = { selectedIndex = it },
                    modifier = Modifier.width(chartWidth).height(RainPlotHeight).testTag(TAG_GRAPHIC_RAIN_PLOT)
                )
                WindPlot(
                    points = state.points,
                    solarByDate = state.solarByDate,
                    zone = zone,
                    domain = windDomain,
                    selectedIndex = selectedIndex,
                    showAgreement = showAgreement,
                    agreementPalette = agreementPalette,
                    onSelectIndex = { selectedIndex = it },
                    modifier = Modifier.width(chartWidth).height(WindPlotHeight).testTag(TAG_GRAPHIC_WIND_PLOT)
                )
                GraphicTimeAxis(
                    points = state.points,
                    zone = zone,
                    locale = locale,
                    selectedIndex = selectedIndex,
                    modifier = Modifier.width(chartWidth).height(TimeAxisHeight)
                )
                GraphicVigilanceLane(
                    vigilance = state.vigilance,
                    points = state.points,
                    modifier = Modifier.width(chartWidth).height(VigilanceLaneHeight)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.graphic_view_touch_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            OpenMeteoAttribution(home = false)
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

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            period,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.graphic_view_intro),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.graphic_view_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.graphic_view_scroll_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.graphic_view_show_convergence),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.graphic_view_show_convergence_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = showAgreement, onCheckedChange = onShowAgreementChange)
            }
        }
    }
}

@Composable
private fun GraphicSelectionCard(
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
    val conditionLabel = if (condition != null) {
        stringResource(weatherConditionLabelRes(condition))
    } else null
    val gustShort = stringResource(R.string.graphic_view_gust_short)
    val tempAgreement = point.consensusFor(ForecastMetric.TEMPERATURE)?.percent
    val rainAgreement = point.consensusFor(ForecastMetric.PRECIPITATION)?.percent
    val windAgreement = point.consensusFor(ForecastMetric.WIND)?.percent

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIconDecorative(point.condition, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(dateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        conditionLabel ?: stringResource(R.string.graphic_view_central_forecast),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.graphic_view_models_count, point.modelCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            Spacer(Modifier.height(6.dp))

            GraphicMetricDetailRow(
                label = stringResource(R.string.graphic_view_temperature),
                value = point.temperatureC?.let { "${format(it, 1)} °C" } ?: "—",
                range = formatRange(point.temperatureMinAcrossModels, point.temperatureMaxAcrossModels, "°C", 1),
                agreement = tempAgreement
            )
            GraphicMetricDetailRow(
                label = stringResource(R.string.graphic_view_rain),
                value = rainAmount(point)?.let { "${format(it, 1)} mm" } ?: "—",
                range = buildString {
                    append(formatRange(point.precipitationMinAcrossModelsMm, point.precipitationMaxAcrossModelsMm, "mm", 1))
                    point.precipitationPercent?.let { append(" · ${it}%") }
                },
                agreement = rainAgreement
            )
            GraphicMetricDetailRow(
                label = stringResource(R.string.graphic_view_wind),
                value = point.windKmh?.let { "${format(it, 0)} km/h" } ?: "—",
                range = buildString {
                    append(formatRange(point.windMinAcrossModels, point.windMaxAcrossModels, "km/h", 0))
                    point.windGustKmh?.let { append(" · $gustShort ${format(it, 0)}") }
                    point.windDirectionDeg?.let { append(" · ${it}°") }
                },
                agreement = windAgreement
            )

            if (models.isNotEmpty()) {
                TextButton(onClick = { showModels = !showModels }) {
                    Text(
                        stringResource(
                            if (showModels) R.string.graphic_view_hide_models
                            else R.string.graphic_view_show_models
                        )
                    )
                }
                AnimatedVisibility(showModels) {
                    Column {
                        HorizontalDivider()
                        Spacer(Modifier.height(6.dp))
                        models.forEach { row -> GraphicModelRow(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphicMetricDetailRow(
    label: String,
    value: String,
    range: String,
    agreement: Int?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.graphic_view_model_range_value, range),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (agreement != null) {
            val color = confidenceColor(agreement)
            Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
                Text(
                    "$agreement%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
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
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LegendChip(temperature, stringResource(R.string.graphic_view_temperature))
        LegendChip(temperature.copy(alpha = 0.20f), stringResource(R.string.graphic_view_dispersion))
        LegendChip(rain, stringResource(R.string.graphic_view_rain))
        LegendChip(wind, stringResource(R.string.graphic_view_wind))
        LegendChip(MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.graphic_view_gusts))
        LegendChip(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), stringResource(R.string.graphic_view_night))
        if (showAgreement) {
            LegendChip(agreementPalette.low, stringResource(R.string.graphic_view_divergence))
            LegendChip(agreementPalette.high, stringResource(R.string.graphic_view_strong_agreement))
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        domain.ticks.forEach { tick ->
            val y = valueToYDp(tick, domain, height)
            Text(
                text = format(tick, decimals),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val badgeWidth = 224.dp
    val trackWidth = GraphicHourWidth * pointCount.toFloat()
    val center = GraphicHourWidth * (selectedIndex + 0.5f)
    val maxX = (trackWidth - badgeWidth).coerceAtLeast(0.dp)
    val x = (center - badgeWidth * 0.5f).coerceIn(0.dp, maxX)
    Surface(
        modifier = Modifier
            .offset(x = x, y = yOffset)
            .width(badgeWidth)
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
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.height(34.dp)) {
            points.forEachIndexed { index, point ->
                val zdt = point.instant?.atZone(zone)
                Box(
                    modifier = Modifier
                        .width(GraphicHourWidth)
                        .fillMaxSize()
                        .testTag(TAG_GRAPHIC_HOUR_CELL)
                        .background(
                            if (index == selectedIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        zdt?.format(hourFormatter) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (index == selectedIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(Modifier.height(42.dp)) {
            var start = 0
            while (start < points.size) {
                val date = dates[start]
                var end = start
                while (end + 1 < points.size && dates[end + 1] == date) end++
                val span = end - start + 1
                Surface(
                    modifier = Modifier.width(GraphicHourWidth * span.toFloat()).height(42.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            date?.format(dayFormatter) ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
                start = end + 1
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
