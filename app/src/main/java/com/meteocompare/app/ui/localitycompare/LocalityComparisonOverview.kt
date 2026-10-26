package com.meteocompare.app.ui.localitycompare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.util.windCardinal
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.usecase.LocalityDayMetrics
import com.meteocompare.app.ui.components.uvIntensityColor
import com.meteocompare.app.ui.theme.confidenceColor
import java.util.Locale

private val LABEL_WIDTH = 100.dp
private val VALUE_WIDTH = 108.dp

private data class OverviewMetric(
    val kind: CompareChartMetric,
    val label: Int,
    val icon: ImageVector
)

private val overviewMetrics = listOf(
    OverviewMetric(CompareChartMetric.TEMPERATURE, R.string.compare_temperature, Icons.Default.Thermostat),
    OverviewMetric(CompareChartMetric.APPARENT, R.string.metric_apparent_temperature, Icons.Default.Thermostat),
    OverviewMetric(CompareChartMetric.PRESSURE, R.string.metric_pressure_short, Icons.Default.Speed),
    OverviewMetric(CompareChartMetric.HUMIDITY, R.string.metric_humidity, Icons.Default.WaterDrop),
    OverviewMetric(CompareChartMetric.DEW_POINT, R.string.metric_dew_point, Icons.Default.Thermostat),
    OverviewMetric(CompareChartMetric.RAIN, R.string.compare_rain, Icons.Default.WaterDrop),
    OverviewMetric(CompareChartMetric.RAIN_RISK, R.string.compare_rain_probability, Icons.Default.Opacity),
    OverviewMetric(CompareChartMetric.CLOUDS, R.string.compare_cloud_cover, Icons.Default.Cloud),
    OverviewMetric(CompareChartMetric.WIND, R.string.compare_wind, Icons.Default.Air),
    OverviewMetric(CompareChartMetric.SUNSHINE, R.string.compare_sunshine, Icons.Default.WbSunny),
    OverviewMetric(CompareChartMetric.AGREEMENT, R.string.compare_agreement, Icons.Default.CheckCircle),
    OverviewMetric(CompareChartMetric.UV, R.string.compare_uv_max, Icons.Default.LightMode)
)

/** Comparable cells share ONE row and one horizontal scroll position on narrow devices. */
@Composable
internal fun LocalityComparisonOverview(
    cities: List<City>,
    days: Map<String, LocalityDayMetrics?>,
    forecasts: Map<String, CompareForecastState>,
    units: WeatherUnits,
    locale: Locale,
    selectedMetric: CompareChartMetric,
    onMetricSelected: (CompareChartMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < .5f
    val sharedHorizontalScroll = rememberScrollState()
    Surface(modifier = modifier.fillMaxWidth().testTag("compare_overview"),
        shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerLow) {
        Column(modifier = Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(sharedHorizontalScroll)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(LABEL_WIDTH).padding(start = 5.dp)) {
                    Text(stringResource(R.string.compare_metric_heading), style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.compare_overview_tap_hint), style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant)
                }
                cities.forEachIndexed { index, city ->
                    val day = days[city.id]
                    Column(
                        modifier = Modifier.width(VALUE_WIDTH).testTag("compare_column_${city.id}")
                            .padding(horizontal = 5.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(city.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            color = listOf(colors.primary, colors.tertiary, colors.secondary)[index % 3],
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(units.temp(day?.maxC, locale = locale), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = colors.onSurface,
                            modifier = Modifier.testTag("compare_main_temperature_${city.id}"))
                        if (forecasts[city.id] is CompareForecastState.Failed) {
                            Text(stringResource(R.string.compare_unavailable),
                                style = MaterialTheme.typography.labelSmall, color = colors.error,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            overviewMetrics.forEach { spec ->
                val tone = when (spec.kind) {
                    CompareChartMetric.RAIN, CompareChartMetric.RAIN_RISK -> if (dark) Color(0xFF91CAFF) else Color(0xFF286AA4)
                    CompareChartMetric.UV, CompareChartMetric.SUNSHINE -> colors.tertiary
                    else -> colors.primary
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (selectedMetric == spec.kind) colors.primaryContainer.copy(alpha = .48f) else Color.Transparent)
                        .horizontalScroll(sharedHorizontalScroll)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.width(LABEL_WIDTH).heightIn(min = 40.dp)
                            .clickable { onMetricSelected(spec.kind) }
                            .testTag("compare_overview_metric_${spec.kind.name.lowercase()}")
                            .padding(start = 4.dp, end = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(spec.icon, contentDescription = null, tint = tone, modifier = Modifier.size(15.dp))
                        Text(stringResource(spec.label), style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant, maxLines = 2)
                    }
                    cities.forEach { city ->
                        val day = days[city.id]
                        val value = day?.let { displayDayMetric(spec.kind, it, units, locale) } ?: "—"
                        val percent = day?.let {
                            when (spec.kind) {
                                CompareChartMetric.RAIN_RISK -> it.rainProbabilityPercent
                                CompareChartMetric.CLOUDS -> it.cloudCoverPercent
                                CompareChartMetric.AGREEMENT -> it.agreementPercent
                                else -> null
                            }
                        }
                        val valueTone = when (spec.kind) {
                            CompareChartMetric.UV -> day?.uvMax?.takeIf { it.isFinite() && it >= 0.0 }
                                ?.let { uvIntensityColor(it, dark) } ?: colors.onSurfaceVariant
                            CompareChartMetric.AGREEMENT -> day?.agreementPercent?.let { confidenceColor(it) }
                                ?: colors.onSurfaceVariant
                            else -> colors.onSurface
                        }
                        Column(
                            modifier = Modifier.width(VALUE_WIDTH).padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, color = valueTone,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag(when(spec.kind) {
                                    CompareChartMetric.RAIN_RISK -> "compare_rain_probability_${city.id}"
                                    CompareChartMetric.CLOUDS -> "compare_cloud_cover_${city.id}"
                                    CompareChartMetric.UV -> "compare_uv_${city.id}"
                                    else -> "compare_value_${spec.kind.name.lowercase()}_${city.id}"
                                })
                            )
                            if (percent != null && percent in 0..100) {
                                LinearProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.width(80.dp).height(3.dp).testTag(when(spec.kind) {
                                        CompareChartMetric.RAIN_RISK -> "compare_rain_bar_${city.id}"
                                        CompareChartMetric.CLOUDS -> "compare_cloud_bar_${city.id}"
                                        else -> "compare_agreement_bar_${city.id}"
                                    }),
                                    color = if (spec.kind == CompareChartMetric.AGREEMENT) valueTone else tone,
                                    trackColor = colors.surfaceContainerHighest,
                                    drawStopIndicator = {}
                                )
                            }
                        }
                    }
                }
            }
            if (cities.size == 2) {
                val first = days[cities[0].id]
                val second = days[cities[1].id]
                val gap = comparisonMaxTemperatureDifference(first?.maxC, second?.maxC)
                if (gap != null && gap >= 2.0) {
                    val warmer = if ((first?.maxC ?: 0.0) > (second?.maxC ?: 0.0)) cities[0] else cities[1]
                    val cooler = if (warmer.id == cities[0].id) cities[1] else cities[0]
                    Text(
                        stringResource(
                            R.string.compare_temperature_difference,
                            warmer.name,
                            units.number(units.temperatureDelta(gap), 0, locale) + units.temperatureUnit,
                            cooler.name
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp).testTag("compare_highlight")
                    )
                }
                val rainGap = comparisonMaxTemperatureDifference(first?.precipitationMm, second?.precipitationMm)
                if (rainGap != null && rainGap >= 1.0) {
                    val drier = if ((first?.precipitationMm ?: 0.0) < (second?.precipitationMm ?: 0.0))
                        cities[0] else cities[1]
                    val wetter = if (drier.id == cities[0].id) cities[1] else cities[0]
                    Text(
                        stringResource(R.string.compare_rain_difference, drier.name, units.rain(rainGap, locale), wetter.name),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                            .testTag("compare_rain_highlight")
                    )
                }
            }
        }
    }
}

/** Unit-independent pure comparison: never rank when either forecast is absent. */
internal fun comparisonMaxTemperatureDifference(a: Double?, b: Double?): Double? {
    if (a == null || b == null || !a.isFinite() || !b.isFinite()) return null
    return kotlin.math.abs(a - b)
}

internal fun displayDayMetric(
    metric: CompareChartMetric,
    day: LocalityDayMetrics,
    units: WeatherUnits,
    locale: Locale
): String = when (metric) {
    CompareChartMetric.TEMPERATURE -> "${units.temp(day.minC, locale = locale)} / ${units.temp(day.maxC, locale = locale)}"
    CompareChartMetric.APPARENT -> "${units.temp(day.apparentMinC, locale = locale)} / ${units.temp(day.apparentMaxC, locale = locale)}"
    CompareChartMetric.HUMIDITY -> day.humidityPercent?.let { "$it %" } ?: "—"
    CompareChartMetric.DEW_POINT -> units.temp(day.dewPointC, locale = locale)
    CompareChartMetric.PRESSURE -> units.format(day.pressureHpa, com.meteocompare.app.core.units.WeatherUnit.PRESSURE, if (units.imperial) 2 else 0, locale)
    CompareChartMetric.RAIN -> units.rain(day.precipitationMm, locale)
    CompareChartMetric.RAIN_RISK -> day.rainProbabilityPercent?.let { "$it %" } ?: "—"
    CompareChartMetric.CLOUDS -> day.cloudCoverPercent?.let { "$it %" } ?: "—"
    CompareChartMetric.WIND -> units.speed(day.maxWindKmh, locale = locale).let { speed ->
        if (day.maxWindKmh == null) speed else
            windCardinal(day.windDirectionDeg, locale)?.let { "$speed · $it" } ?: speed
    }
    CompareChartMetric.SUNSHINE -> day.estimatedSunHours?.let { "${units.number(it, 1, locale)} h" } ?: "—"
    CompareChartMetric.AGREEMENT -> day.agreementPercent?.let { "$it %" } ?: "—"
    CompareChartMetric.UV -> day.uvMax?.takeIf { it.isFinite() && it >= 0.0 }?.let { units.number(it, 1, locale) } ?: "—"
}

/** Third level remains collapsed until requested, while chart selection always shows one metric's exact values. */
@Composable
internal fun LocalityHourDetails(
    cities: List<HourChartCity>,
    selectedHour: Int,
    hourLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    units: WeatherUnits,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val sharedHorizontalScroll = rememberScrollState()
    // The disclosure state is exposed on the Surface itself. A tag on the inner
    // Column can be hidden by Compose's merged semantics tree on Android tests.
    // Keep the stable component tag on the inner layout for diagnostics.
    Surface(
        modifier = modifier.fillMaxWidth().testTag(
            if (expanded) "compare_hour_details_expanded" else "compare_hour_details_collapsed"
        ),
        color = colors.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("compare_hour_details")
        ) {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().testTag("compare_toggle_details")) {
                Text(stringResource(if (expanded) R.string.compare_hide_details else R.string.compare_show_details, hourLabel))
            }
            if (expanded) {
                // A single grid, not three independent city cards: identical metrics stay aligned.
                Row(Modifier.horizontalScroll(sharedHorizontalScroll), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.compare_metric_heading), modifier = Modifier.width(LABEL_WIDTH),
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    cities.forEach { city ->
                        Text(city.name, modifier = Modifier.width(VALUE_WIDTH), maxLines = 1,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center)
                    }
                }
                CompareChartMetric.entries.forEach { metric ->
                    Row(
                        modifier = Modifier.horizontalScroll(sharedHorizontalScroll).padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(metric.labelRes), modifier = Modifier.width(LABEL_WIDTH),
                            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        cities.forEach { city ->
                            Text(metric.displayValue(city.hours.getOrNull(selectedHour), units, locale),
                                modifier = Modifier.width(VALUE_WIDTH).testTag("compare_detail_${metric.name.lowercase()}_${city.id}"),
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LocalityComparisonExplanations(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            TextButton(onClick = onToggle, modifier = Modifier.testTag("compare_toggle_methodology")) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.compare_methodology))
            }
            if (expanded) {
                listOf(
                    R.string.compare_rain_cloud_disclaimer,
                    R.string.compare_hour_rain_cloud_disclaimer,
                    R.string.compare_sunshine_disclaimer,
                    R.string.compare_agreement_disclaimer,
                    R.string.compare_hour_agreement_disclaimer,
                    R.string.compare_uv_disclaimer
                ).forEach { label ->
                    Text(stringResource(label), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}
