package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.sortedByFamily
import com.meteocompare.app.ui.components.WindArrow
import com.meteocompare.app.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Style optionnel appliqué au texte d'une cellule de valeur. */
data class ValueStyle(
    val color: Color,
    val fontWeight: FontWeight
)

/**
 * Tableau Modèle × Jour pour une variable quotidienne.
 *
 * Les modèles occupent désormais les lignes dans une colonne figée. Les dates
 * sont disposées en colonnes et défilent horizontalement. Cette orientation
 * facilite la lecture d'un modèle dans le temps et garde son nom visible.
 */
@Composable
fun ForecastTable(
    forecast: CityForecast,
    valueExtractor: (DailyForecast, Int) -> Double?,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    directionExtractor: ((DailyForecast, Int) -> Int?)? = null,
    cellWidth: Dp = 72.dp,
    modelBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    onBiasChipClick: ((WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null,
    sampleCountProvider: ((WeatherModel) -> Int)? = null
) {
    val dates = remember(forecast) {
        forecast.seriesByModel.values.flatMap { it.daily.dates }.distinct().sorted()
    }
    val models = remember(forecast) {
        forecast.seriesByModel.keys.sortedByFamily()
    }

    if (dates.isEmpty() || models.isEmpty()) {
        Text(
            stringResource(R.string.no_daily_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val palette = detailTablePalette()
    val today = remember { LocalDate.now() }
    val modelRowHeight = if (modelBiasProvider != null) 56.dp else 40.dp
    val modelColumnWidth = if (modelBiasProvider != null) 94.dp else 84.dp
    val temporalHeaderHeight = 40.dp
    val locale = LocalConfiguration.current.locales[0]
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }

    FrozenDetailTableLayout(
        modelColumnWidth = modelColumnWidth,
        temporalColumnWidth = cellWidth,
        temporalColumnCount = dates.size,
        headerHeight = temporalHeaderHeight,
        rowHeight = modelRowHeight,
        rowCount = models.size,
        palette = palette,
        modifier = modifier,
        cornerHeader = {
            CornerHeaderCell(
                text = stringResource(R.string.detail_table_model_header),
                width = modelColumnWidth,
                height = temporalHeaderHeight,
                background = palette.frozenHeaderSurface,
                palette = palette
            )
        },
        temporalHeaders = {
            dates.forEachIndexed { dateIndex, date ->
                val isToday = date == today
                TemporalHeaderCell(
                    text = date.format(dayFormatter).replaceFirstChar { it.uppercase() },
                    width = cellWidth,
                    height = temporalHeaderHeight,
                    background = palette.labelRowBackground(dateIndex, isToday),
                    highlighted = isToday,
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
                ModelRowHeaderCell(
                    model = model,
                    width = modelColumnWidth,
                    height = modelRowHeight,
                    background = palette.labelRowBackground(modelIndex, false),
                    palette = palette,
                    bias = bias,
                    onBiasClick = chipClick,
                    showChipSlot = modelBiasProvider != null,
                    sampleCount = sampleCount
                )
            }
        },
        temporalColumns = {
            dates.forEachIndexed { dateIndex, date ->
                val isToday = date == today
                Column(modifier = Modifier.width(cellWidth)) {
                    models.forEachIndexed { modelIndex, model ->
                        val value = valueAt(forecast, model, date, valueExtractor)
                        val direction = directionExtractor?.let {
                            directionAt(forecast, model, date, it)
                        }
                        ValueCell(
                            text = value?.let(valueFormatter) ?: "—",
                            style = value?.let { valueStyler?.invoke(it) },
                            background = palette.dataRowBackground(modelIndex, isToday),
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
private fun CornerHeaderCell(
    text: String,
    width: Dp,
    height: Dp,
    background: Color,
    palette: DetailTablePalette
) {
    Box(
        modifier = Modifier.width(width).height(height)
            .detailTableCell(background, palette).padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun ModelRowHeaderCell(
    model: WeatherModel,
    width: Dp,
    height: Dp,
    background: Color,
    palette: DetailTablePalette,
    bias: com.meteocompare.app.domain.model.ModelBias?,
    onBiasClick: (() -> Unit)?,
    showChipSlot: Boolean,
    sampleCount: Int?
) {
    Column(
        modifier = Modifier.width(width).height(height)
            .detailTableCell(background, palette, model.color())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            2.dp, Alignment.CenterVertically
        )
    ) {
        Text(
            text = model.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TemporalHeaderCell(
    text: String,
    width: Dp,
    height: Dp,
    background: Color,
    highlighted: Boolean,
    palette: DetailTablePalette
) {
    Box(
        modifier = Modifier.width(width).height(height)
            .detailTableCell(background, palette).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (highlighted) palette.highlightedText else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun ValueCell(
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
                WindArrow(directionDegrees = directionDegrees, size = 12.dp)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = style?.color ?: Color.Unspecified,
                    fontWeight = style?.fontWeight,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = style?.color ?: Color.Unspecified,
                fontWeight = style?.fontWeight
            )
        }
    }
}

private fun valueAt(
    forecast: CityForecast,
    model: WeatherModel,
    date: LocalDate,
    extractor: (DailyForecast, Int) -> Double?
): Double? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.daily.dates.indexOf(date)
    if (idx < 0) return null
    return extractor(series.daily, idx)
}

private fun directionAt(
    forecast: CityForecast,
    model: WeatherModel,
    date: LocalDate,
    extractor: (DailyForecast, Int) -> Int?
): Int? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.daily.dates.indexOf(date)
    if (idx < 0) return null
    return extractor(series.daily, idx)
}
