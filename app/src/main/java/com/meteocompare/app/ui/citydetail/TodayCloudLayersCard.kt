package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    // Cloud-layer colors describe altitude, not selection or the app's accent.
    // Derive light/dark from the effective Material surface: the user can force
    // an app theme independently of the system-wide appearance.
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors = listOf(
        MaterialTheme.colorScheme.onSurfaceVariant, // Total: neutral cloud cover
        if (isDarkSurface) Color(0xFFE4B96D) else Color(0xFF956322), // High: warm gold
        if (isDarkSurface) Color(0xFF71C9D4) else Color(0xFF187487), // Mid: cool teal
        if (isDarkSurface) Color(0xFFA5B6E8) else Color(0xFF5167A9)  // Low: blue
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
                    CloudCoverMeter(
                        label, currentValues[index], colors[index],
                        valueTestTag = "today_cloud_current_value_$index"
                    )
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
        val detailScrollState = rememberScrollState()
        // The hour selector stays outside the vertical scroll region. Switching
        // hours always brings the newly selected summary and model rows into view.
        LaunchedEffect(selectedHour) { detailScrollState.scrollTo(0) }
        LaunchedEffect(showExplanation) {
            if (showExplanation) {
                // The expanded explanation changes the scroll range after layout.
                withFrameNanos { }
                withFrameNanos { }
                detailScrollState.scrollTo(detailScrollState.maxValue)
            }
        }
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f)
                    .testTag(TAG_TODAY_CLOUD_DIALOG),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(11.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.summary_cloud_layers_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(R.string.summary_cloud_layers_timeline),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { open = false }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.toast_dismiss))
                        }
                    }

                    CloudHourSelector(hours, selectedHour, zone, formatter) { selectedHour = it }

                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(detailScrollState)
                            .testTag("today_cloud_scroll_content"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CloudLayerHourlyChart(hours, names, colors, selectedHour, zone, formatter)
                        hours.getOrNull(selectedHour)?.let { sample ->
                            CloudSelectedHourSummary(sample, names, colors, zone, formatter)
                            Column(
                                modifier = Modifier.testTag("today_cloud_models_section"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.summary_cloud_layers_models),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val modelRows = sample.readings
                                if (modelRows.isEmpty()) {
                                    Text(
                                        stringResource(R.string.summary_cloud_layers_unavailable),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                modelRows.forEach { model ->
                                    CloudModelDetailCard(model, names, colors)
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(
                                    stringResource(R.string.summary_cloud_layers_not_sum),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                    Text(
                                        stringResource(R.string.summary_cloud_layers_guide),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        stringResource(R.string.summary_cloud_layers_veil),
                                        modifier = Modifier.testTag("today_cloud_veil_explanation"),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        stringResource(R.string.summary_cloud_layers_sun_method),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { open = false }) {
                            Text(stringResource(R.string.toast_dismiss))
                        }
                    }
                }
            }
        }
    }

}

/** All bars share a 0–100% scale, but layers are not additive. */
@Composable
private fun CloudCoverMeter(
    label: String,
    percent: Int?,
    color: Color,
    valueTestTag: String? = null
) {
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
            modifier = Modifier.width(46.dp).then(
                if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (percent == null) MaterialTheme.colorScheme.onSurfaceVariant else color
        )
    }
}

/** A fixed, accessible time rail: it does not disappear when model details are scrolled. */
@Composable
private fun CloudHourSelector(
    hours: List<CloudLayerHour>,
    selectedIndex: Int,
    zone: ZoneId,
    formatter: DateTimeFormatter,
    onSelected: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("today_cloud_hour_selector")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                stringResource(R.string.summary_cloud_layers_select_hour),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                hours.forEachIndexed { index, hour ->
                    val selected = index == selectedIndex
                    Surface(
                        onClick = { onSelected(index) },
                        modifier = Modifier.testTag("today_cloud_hour_$index")
                            .semantics { this.selected = selected },
                        shape = RoundedCornerShape(11.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            popupHourLabel(hours, index, zone, formatter),
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/** Each independent layer has its own compact 9-hour plot, with a common 0–100% scale. */
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.summary_cloud_layers_timeline),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        popupHourLabel(hours, selectedIndex, zone, formatter),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            series.forEachIndexed { bandIndex, values ->
                val color = colors[bandIndex]
                val selected = values.getOrNull(selectedIndex)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = color.copy(alpha = 0.17f)
                        ) {
                            Box(modifier = Modifier.padding(5.dp)) {
                                Box(
                                    Modifier.width(7.dp).height(7.dp)
                                        .background(color, CircleShape)
                                )
                            }
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            labels[bandIndex],
                            modifier = Modifier.weight(1f).testTag("today_cloud_band_label_$bandIndex"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            selected?.let { "$it %" } ?: "—",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selected != null) color else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val guide = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    val selectedBackdrop = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                    Canvas(
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                            .testTag("today_cloud_band_chart_$bandIndex").semantics {
                            contentDescription = "${labels[bandIndex]}: " +
                                values.joinToString(", ") { it?.let { value -> "$value %" } ?: "—" }
                        }
                    ) {
                        val cell = size.width / values.size.coerceAtLeast(1)
                        val gap = (cell * 0.16f).coerceAtMost(5.dp.toPx())
                        val barWidth = (cell - gap * 2).coerceAtLeast(1f)
                        drawLine(guide, Offset(0f, size.height / 2f),
                            Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
                        values.forEachIndexed { index, raw ->
                            val left = index * cell + gap
                            if (index == selectedIndex) {
                                drawRoundRect(
                                    selectedBackdrop,
                                    topLeft = Offset(index * cell, 0f),
                                    size = Size(cell, size.height),
                                    cornerRadius = CornerRadius(6.dp.toPx())
                                )
                            }
                            raw?.let { level ->
                                val barHeight = size.height * level.coerceIn(0, 100) / 100f
                                if (barHeight > 0f) {
                                    drawRoundRect(
                                        color.copy(alpha = if (index == selectedIndex) 1f else 0.63f),
                                        topLeft = Offset(left, size.height - barHeight),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(4.dp.toPx())
                                    )
                                }
                            } ?: run {
                                // Missing data is not a zero-height measured cloud cover.
                                drawLine(
                                    guide,
                                    Offset(left, size.height - 2.dp.toPx()),
                                    Offset(left + barWidth, size.height - 2.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, hours.lastIndex / 2, hours.lastIndex).distinct().forEach { index ->
                    Text(
                        popupHourLabel(hours, index, zone, formatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudSelectedHourSummary(
    sample: CloudLayerHour,
    labels: List<String>,
    colors: List<Color>,
    zone: ZoneId,
    formatter: DateTimeFormatter
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("today_cloud_selected_summary"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.summary_cloud_layers_central),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    clock(sample.hour, zone, formatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sample.hasCloudData) {
                listOf(sample.total, sample.high, sample.mid, sample.low)
                    .forEachIndexed { index, value ->
                        CloudCoverMeter(
                            labels[index], value, colors[index],
                            valueTestTag = "today_cloud_selected_value_$index"
                        )
                    }
            } else {
                Text(
                    stringResource(R.string.summary_cloud_layers_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudModelDetailCard(
    model: CloudLayerModelReading,
    labels: List<String>,
    colors: List<Color>
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("today_cloud_model_${model.model.name}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    model.model.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        "${labels[0]} ${model.total?.let { "$it %" } ?: "—"}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(model.high, model.mid, model.low).forEachIndexed { index, value ->
                    CloudLayerModelTile(
                        label = labels[index + 1],
                        percent = value,
                        color = colors[index + 1],
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (model.sunshineMinutes != null || model.directNormalWm2 != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    model.sunshineMinutes?.let { minutes ->
                        SolarCloudChip(stringResource(R.string.summary_cloud_layers_solar_minutes, minutes))
                    }
                    model.directNormalWm2?.let { dni ->
                        SolarCloudChip(stringResource(R.string.summary_cloud_layers_dni, dni))
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudLayerModelTile(
    label: String,
    percent: Int?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                percent?.let { "$it %" } ?: "—",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Canvas(modifier = Modifier.fillMaxWidth().height(5.dp)) {
                drawRoundRect(color.copy(alpha = 0.18f), cornerRadius = CornerRadius(size.height / 2))
                percent?.let { level ->
                    val width = size.width * level.coerceIn(0, 100) / 100f
                    if (width > 0f) drawRoundRect(
                        color,
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2)
                    )
                }
            }
        }
    }
}

@Composable
private fun SolarCloudChip(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            Icons.Outlined.WbSunny,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.width(14.dp).height(14.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun clock(hour: Instant, zone: ZoneId, formatter: DateTimeFormatter): String =
    hour.atZone(zone).toLocalTime().format(formatter)

/** Distinguish the repeated local hour when a nine-hour window crosses autumn DST. */
private fun popupHourLabel(
    hours: List<CloudLayerHour>, index: Int, zone: ZoneId, formatter: DateTimeFormatter
): String {
    val instant = hours[index].hour
    val local = clock(instant, zone, formatter)
    return if (hours.count { clock(it.hour, zone, formatter) == local } > 1) {
        "$local ${instant.atZone(zone).offset.id}"
    } else local
}
