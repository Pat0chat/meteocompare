package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.sortedByFamily
import com.meteocompare.app.ui.components.WindArrow
import com.meteocompare.app.ui.theme.color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

/** Tableau Modèle × Heure pour une variable horaire. */
@Composable
fun HourlyForecastTable(
    forecast: CityForecast,
    valueExtractor: (HourlyForecast, Int) -> Double?,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    heatmapStyler: ((Double) -> HeatmapCellStyle?)? = null,
    directionExtractor: ((HourlyForecast, Int) -> Int?)? = null,
    cellWidth: Dp = 72.dp,
    modelBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    onBiasChipClick: ((WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null,
    sampleCountProvider: ((WeatherModel) -> Int)? = null
) {
    val zone = remember(forecast.city.timezone) {
        runCatching { ZoneId.of(forecast.city.timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
    }
    val (startHour, endExclusive) = remember(forecast) {
        computeHourlyHorizon(forecast.city.timezone)
    }
    val timestamps = remember(forecast, startHour, endExclusive) {
        forecast.seriesByModel.values.flatMap { it.hourly.timestamps }.distinct().sorted()
            .filter { it >= startHour && it < endExclusive }
    }
    val models = remember(forecast) {
        forecast.seriesByModel.keys.sortedByFamily()
    }

    if (timestamps.isEmpty() || models.isEmpty()) {
        Text(
            stringResource(R.string.no_hourly_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val palette = detailTablePalette()
    val currentHour = timestamps.first()
    val locale = LocalConfiguration.current.locales[0]
    val hourFmt = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }
    val headerHeight = 44.dp
    val modelRowHeight = if (modelBiasProvider != null) 56.dp else 38.dp
    val modelWidth = if (modelBiasProvider != null) 94.dp else 84.dp

    val dayPrefixes = remember(timestamps, zone, locale) {
        var previous: java.time.LocalDate? = null
        timestamps.associateWith { ts ->
            val date = ts.atZone(zone).toLocalDate()
            val prefix = if (date != previous) {
                date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale).replace(".", "")
            } else null
            previous = date
            prefix
        }
    }

    FrozenDetailTableLayout(
        modelColumnWidth = modelWidth,
        temporalColumnWidth = cellWidth,
        temporalColumnCount = timestamps.size,
        headerHeight = headerHeight,
        rowHeight = modelRowHeight,
        rowCount = models.size,
        palette = palette,
        modifier = modifier,
        cornerHeader = {
            HourHeaderCell(
                model = null,
                text = stringResource(R.string.detail_table_model_header),
                width = modelWidth,
                height = headerHeight,
                background = palette.frozenHeaderSurface,
                palette = palette,
                alignStart = true
            )
        },
        temporalHeaders = {
            timestamps.forEachIndexed { timeIndex, ts ->
                val isCurrent = ts == currentHour
                val local = ts.atZone(zone)
                TimeHeaderCell(
                    hourText = local.format(hourFmt),
                    dayPrefix = dayPrefixes[ts],
                    width = cellWidth,
                    height = headerHeight,
                    background = palette.labelRowBackground(timeIndex, isCurrent),
                    highlighted = isCurrent,
                    palette = palette
                )
            }
        },
        modelRows = {
            models.forEachIndexed { modelIndex, model ->
                val bias = modelBiasProvider?.invoke(model)
                val sampleCount = sampleCountProvider?.invoke(model)
                val chipClick = if (bias != null && onBiasChipClick != null) {
                    remember(model, bias) { { onBiasChipClick(model, bias) } }
                } else null
                HourHeaderCell(
                    model = model,
                    text = model.displayName,
                    width = modelWidth,
                    height = modelRowHeight,
                    background = palette.labelRowBackground(modelIndex, false),
                    palette = palette,
                    bias = bias,
                    onBiasClick = chipClick,
                    showChipSlot = modelBiasProvider != null,
                    sampleCount = sampleCount,
                    alignStart = true
                )
            }
        },
        temporalColumns = {
            timestamps.forEachIndexed { timeIndex, ts ->
                val isCurrent = ts == currentHour
                Column(modifier = Modifier.width(cellWidth)) {
                    models.forEachIndexed { modelIndex, model ->
                        val value = valueAt(forecast, model, ts, valueExtractor)
                        val direction = directionExtractor?.let {
                            directionAt(forecast, model, ts, it)
                        }
                        val heatmap = value?.let { heatmapStyler?.invoke(it) }
                        val heatmapBackground = heatmap?.background?.let {
                            palette.modernHeatmapBackground(it)
                        }
                        val background = heatmapBackground
                            ?: palette.dataRowBackground(modelIndex, isCurrent)
                        val textStyle = when {
                            heatmapBackground != null -> ValueStyle(
                                color = contrastingContentColor(heatmapBackground),
                                fontWeight = FontWeight.Medium
                            )
                            else -> value?.let { valueStyler?.invoke(it) }
                        }
                        HourValueCell(
                            text = value?.let(valueFormatter) ?: "—",
                            style = textStyle,
                            background = background,
                            directionDegrees = direction,
                            height = modelRowHeight,
                            palette = palette
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun HourHeaderCell(
    model: WeatherModel?,
    text: String,
    width: Dp,
    height: Dp,
    background: Color,
    palette: DetailTablePalette,
    bias: com.meteocompare.app.domain.model.ModelBias? = null,
    onBiasClick: (() -> Unit)? = null,
    showChipSlot: Boolean = false,
    sampleCount: Int? = null,
    alignStart: Boolean = false
) {
    Column(
        modifier = Modifier.width(width).height(height)
            .detailTableCell(background, palette, model?.color())
            .padding(horizontal = if (alignStart) 6.dp else 3.dp, vertical = 4.dp),
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            2.dp, Alignment.CenterVertically
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (model != null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (showChipSlot) {
            if (bias == null) CalibratingChip(sampleCount = sampleCount)
            else ModelBiasChip(bias = bias, onClick = onBiasClick ?: {})
        }
    }
}

@Composable
private fun TimeHeaderCell(
    hourText: String,
    dayPrefix: String?,
    width: Dp,
    height: Dp,
    background: Color,
    highlighted: Boolean,
    palette: DetailTablePalette
) {
    Column(
        modifier = Modifier.width(width).height(height)
            .detailTableCell(background, palette).padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        if (dayPrefix != null) {
            Text(
                text = dayPrefix,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) palette.highlightedText else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = hourText,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (highlighted) palette.highlightedText else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun HourValueCell(
    text: String,
    style: ValueStyle?,
    background: Color,
    directionDegrees: Int? = null,
    height: Dp,
    palette: DetailTablePalette
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(height).detailTableCell(background, palette),
        contentAlignment = Alignment.Center
    ) {
        if (directionDegrees != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WindArrow(directionDegrees = directionDegrees, size = 10.dp)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = style?.color ?: Color.Unspecified,
                    fontWeight = style?.fontWeight,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = style?.color ?: Color.Unspecified,
                fontWeight = style?.fontWeight
            )
        }
    }
}

private fun valueAt(
    forecast: CityForecast,
    model: com.meteocompare.app.domain.model.WeatherModel,
    timestamp: Instant,
    extractor: (HourlyForecast, Int) -> Double?
): Double? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.hourly.timestamps.indexOf(timestamp)
    if (idx < 0) return null
    return extractor(series.hourly, idx)
}

/** Version Int? de [valueAt] pour les champs directionnels (degrés météo). */
private fun directionAt(
    forecast: CityForecast,
    model: com.meteocompare.app.domain.model.WeatherModel,
    timestamp: Instant,
    extractor: (HourlyForecast, Int) -> Int?
): Int? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.hourly.timestamps.indexOf(timestamp)
    if (idx < 0) return null
    return extractor(series.hourly, idx)
}

// ─── Stylers hourly-spécifiques ────────────────────────────────────────────
//
//  Les stylers *texte* hourly (température / précipitation / vent) ont été
//  déplacés vers [HourlyHeatmap.kt] sous la forme de stylers *heatmap* qui
//  colorient le fond de la cellule au lieu du texte. Ce refactor a été fait
//  quand les tableaux hourly sont passés en mode heatmap (bien plus lisible
//  pour la matrice 24×5 qu'un texte simplement teinté).
//
//  Les seuils meteo et la palette n'ont PAS changé — voir
//  [hourlyTemperatureHeatmap], [hourlyPrecipitationHeatmap] et
//  [hourlyWindHeatmap]. Si on veut à nouveau un styler texte (ex : pour un
//  écran daily hourly-like futur), il suffit de wrapper les fonctions heatmap
//  et de mapper `background` sur `ValueStyle.color`.
