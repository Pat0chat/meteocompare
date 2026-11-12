package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val TAG_TODAY_CLOUD_LAYERS = "today_summary_cloud_layers_chart"
internal const val TAG_TODAY_CLOUD_DIALOG = "today_summary_cloud_layers_dialog"
internal const val TAG_TODAY_CLOUD_CURRENT = "today_summary_cloud_current"
internal const val TAG_TODAY_CLOUD_TIMELINE = "today_summary_cloud_timeline"

/** The card is a current-hour snapshot. Future hours and individual models live only in the dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayCloudLayersCard(
    hours: List<CloudLayerHour>,
    zone: ZoneId,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    if (hours.isEmpty()) return
    var open by remember(hours.first().hour) { mutableStateOf(false) }
    var selectedHour by remember(hours.first().hour) { mutableIntStateOf(0) }
    var showExplanation by remember(hours.first().hour) { mutableStateOf(false) }
    // The first slot is precisely the current forecast hour, even if its data are missing.
    // Never substitute the first available future hour for the current conditions.
    val current = hours.first()
    val formatter = WeatherTimeFormat.clock(locale, LocalClockFormatPreference.current)
    val names = listOf(
        stringResource(R.string.summary_cloud_layers_total),
        stringResource(R.string.summary_cloud_layers_high),
        stringResource(R.string.summary_cloud_layers_mid),
        stringResource(R.string.summary_cloud_layers_low)
    )
    val colors = listOf(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary
    )
    val currentValues = listOf(current.total, current.high, current.mid, current.low)
    val cloudAccent = MaterialTheme.colorScheme.primary

    SummaryMetricSection(tag = "today_summary_cloud_panel", modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .testTag(TAG_TODAY_CLOUD_LAYERS)
                .clickable { open = true }
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SummaryMetricGroupHeader(
                title = stringResource(R.string.summary_cloud_layers_now_title),
                icon = Icons.Outlined.Cloud,
                accent = cloudAccent,
                trailing = clock(current.hour, zone, formatter)
            )
            Column(
                modifier = Modifier.fillMaxWidth().testTag(TAG_TODAY_CLOUD_CURRENT),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                names.forEachIndexed { index, label ->
                    CloudCoverMeter(label, currentValues[index], colors[index])
                }
            }
            Text(
                text = stringResource(R.string.summary_cloud_layers_details_hint),
                style = MaterialTheme.typography.labelSmall,
                color = cloudAccent
            )
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            modifier = Modifier.testTag(TAG_TODAY_CLOUD_DIALOG),
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.summary_cloud_layers_title),
                        style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.summary_cloud_layers_timeline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CloudLayerHourlyChart(hours, names, colors, selectedHour, zone, formatter)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        hours.forEachIndexed { index, hour ->
                            val selected = selectedHour == index
                            Surface(
                                onClick = { selectedHour = index },
                                modifier = Modifier.testTag("today_cloud_hour_$index"),
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = clock(hour.hour, zone, formatter),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    hours.getOrNull(selectedHour)?.let { sample ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = clock(sample.hour, zone, formatter),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (sample.hasCloudData) {
                                    val values = listOf(sample.total, sample.high, sample.mid, sample.low)
                                    names.forEachIndexed { index, label ->
                                        CloudCoverMeter(label, values[index], colors[index])
                                    }
                                } else {
                                    Text(stringResource(R.string.summary_cloud_layers_unavailable),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.summary_cloud_layers_models),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val modelRows = sample.readings.filter(CloudLayerModelReading::hasCloudData)
                        if (modelRows.isEmpty()) {
                            Text(stringResource(R.string.summary_cloud_layers_unavailable),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        modelRows.forEach { model ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(model.model.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium)
                                    val values = listOf(model.total, model.high, model.mid, model.low)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        names.forEachIndexed { index, label ->
                                            CloudLayerChip(label, values[index], colors[index])
                                        }
                                    }
                                    model.sunshineMinutes?.let { minutes ->
                                        Text(stringResource(R.string.summary_cloud_layers_solar_minutes, minutes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    model.directNormalWm2?.let { dni ->
                                        Text(stringResource(R.string.summary_cloud_layers_dni, dni),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text(stringResource(R.string.summary_cloud_layers_not_sum),
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(
                                onClick = { showExplanation = !showExplanation },
                                modifier = Modifier.testTag("today_cloud_explanation_toggle")
                            ) {
                                Text(stringResource(R.string.summary_cloud_layers_explain))
                                Icon(
                                    imageVector = if (showExplanation) Icons.Filled.KeyboardArrowUp
                                        else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                            if (showExplanation) {
                                Text(stringResource(R.string.summary_cloud_layers_guide),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.summary_cloud_layers_veil),
                                    modifier = Modifier.testTag("today_cloud_veil_explanation"),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.summary_cloud_layers_sun_method),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.toast_dismiss))
                }
            }
        )
    }
}

/** All bars share a 0–100% scale, but layers are not additive. */
@Composable
private fun CloudCoverMeter(label: String, percent: Int?, color: Color) {
    val fraction = percent?.coerceIn(0, 100)?.div(100f) ?: 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        Canvas(
            modifier = Modifier.weight(1f).height(9.dp)
                .semantics { contentDescription = "$label: ${percent?.let { "$it %" } ?: "—"}" }
        ) {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2f))
            if (percent != null && fraction > 0f) {
                drawRoundRect(color.copy(alpha = 0.92f),
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2f))
            }
        }
        Text(
            text = percent?.let { "$it %" } ?: "—",
            modifier = Modifier.width(46.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (percent == null) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}

@Composable
private fun CloudLayerChip(label: String, percent: Int?, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = CircleShape) {
        Text(
            "$label ${percent?.let { "$it %" } ?: "—"}",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Chart labels sit immediately alongside their respective series, not in a remote legend. */
@Composable
private fun CloudLayerHourlyChart(
    hours: List<CloudLayerHour>,
    labels: List<String>,
    colors: List<Color>,
    selectedIndex: Int,
    zone: ZoneId,
    formatter: DateTimeFormatter
) {
    val series = listOf(
        hours.map { it.total }, hours.map { it.high },
        hours.map { it.mid }, hours.map { it.low }
    )
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(TAG_TODAY_CLOUD_TIMELINE),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            series.forEachIndexed { bandIndex, values ->
                val color = colors[bandIndex]
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = labels[bandIndex],
                        modifier = Modifier.width(55.dp)
                            .testTag("today_cloud_band_label_$bandIndex"),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                    Canvas(
                        modifier = Modifier.weight(1f).height(30.dp)
                            .semantics { contentDescription = labels[bandIndex] }
                    ) {
                        val cell = size.width / values.size.coerceAtLeast(1)
                        val gap = (cell * 0.12f).coerceAtMost(4.dp.toPx())
                        val barWidth = (cell - gap * 2f).coerceAtLeast(1f)
                        values.forEachIndexed { index, raw ->
                            val left = index * cell + gap
                            drawRoundRect(
                                color.copy(alpha = if (index == selectedIndex) 0.19f else 0.09f),
                                topLeft = Offset(left, 0f),
                                size = Size(barWidth, size.height),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                            raw?.let { level ->
                                val h = (size.height * level.coerceIn(0, 100) / 100f)
                                if (h > 0f) drawRoundRect(
                                    color.copy(alpha = if (index == selectedIndex) 1f else 0.76f),
                                    topLeft = Offset(left, size.height - h),
                                    size = Size(barWidth, h),
                                    cornerRadius = CornerRadius(3.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(55.dp))
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(0, hours.lastIndex / 2, hours.lastIndex).distinct().forEach { index ->
                        Text(clock(hours[index].hour, zone, formatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun clock(hour: Instant, zone: ZoneId, formatter: DateTimeFormatter): String =
    hour.atZone(zone).toLocalTime().format(formatter)
