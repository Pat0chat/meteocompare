package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val WarmTempColor = Color(0xFFE53935)
private val CoolTempColor = Color(0xFF1E88E5)

/** Tableau Modèle × Jour des températures maximales et minimales. */
@Composable
fun MinMaxForecastTable(
    forecast: CityForecast,
    normals: Map<Int, DayNormals>?,
    modifier: Modifier = Modifier,
    modelBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    onBiasChipClick: ((WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null,
    sampleCountProvider: ((WeatherModel) -> Int)? = null
) {
    val dates = remember(forecast) {
        forecast.seriesByModel.values.flatMap { it.daily.dates }.distinct().sorted()
    }
    val models = remember(forecast) {
        forecast.seriesByModel.keys.toList().sortedBy { it.ordinal }
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
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val today = remember { LocalDate.now() }
    val modelRowHeight = if (modelBiasProvider != null) 60.dp else 44.dp
    val modelColumnWidth = if (modelBiasProvider != null) 122.dp else 104.dp
    val dateColumnWidth = 88.dp
    val headerHeight = 44.dp
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }

    Row(modifier = modifier.fillMaxWidth().detailTableFrame(palette)) {
        Column(modifier = Modifier.width(modelColumnWidth)) {
            HeaderCellMM(
                text = stringResource(R.string.detail_table_model_header),
                background = palette.frozenHeaderSurface,
                width = modelColumnWidth,
                height = headerHeight,
                palette = palette,
                alignStart = true
            )
            models.forEachIndexed { modelIndex, model ->
                val bias = modelBiasProvider?.invoke(model)
                val sampleCount = sampleCountProvider?.invoke(model)
                val chipClick = if (bias != null && onBiasChipClick != null) {
                    remember(model, bias) { { onBiasChipClick(model, bias) } }
                } else null
                HeaderCellMM(
                    text = model.displayName,
                    background = palette.labelRowBackground(modelIndex, false),
                    width = modelColumnWidth,
                    height = modelRowHeight,
                    palette = palette,
                    accentColor = model.color(),
                    bias = bias,
                    onBiasClick = chipClick,
                    showChipSlot = modelBiasProvider != null,
                    sampleCount = sampleCount,
                    alignStart = true
                )
            }
        }

        VerticalDivider(
            modifier = Modifier.height((headerHeight.value + modelRowHeight.value * models.size).dp),
            color = palette.frozenDivider
        )

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            dates.forEachIndexed { dateIndex, date ->
                val isToday = date == today
                Column(modifier = Modifier.width(dateColumnWidth)) {
                    HeaderCellMM(
                        text = date.format(formatter).replaceFirstChar { it.uppercase() },
                        background = palette.labelRowBackground(dateIndex, isToday),
                        width = dateColumnWidth,
                        height = headerHeight,
                        palette = palette,
                        highlighted = isToday
                    )
                    models.forEachIndexed { modelIndex, model ->
                        val (maxV, minV) = maxMinAt(forecast, model, date)
                        val normal = normals?.get(DayNormals.key(date.monthValue, date.dayOfMonth))
                        MinMaxCell(
                            tempMax = maxV,
                            tempMin = minV,
                            normal = normal,
                            neutralColor = onSurface,
                            separatorColor = onSurfaceVariant,
                            warmColor = WarmTempColor,
                            coolColor = CoolTempColor,
                            background = palette.dataRowBackground(modelIndex, isToday),
                            height = modelRowHeight,
                            palette = palette
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MinMaxForecastLegend(normalsAvailable: Boolean) {
    if (!normalsAvailable) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(color = WarmTempColor)
        Text(
            text = " " + stringResource(R.string.temp_legend_above_normal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        LegendDot(color = CoolTempColor)
        Text(
            text = " " + stringResource(R.string.temp_legend_below_normal),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .height(10.dp)
            .width(10.dp)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

@Composable
private fun HeaderCellMM(
    text: String,
    background: Color,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp = 40.dp,
    bias: com.meteocompare.app.domain.model.ModelBias? = null,
    onBiasClick: (() -> Unit)? = null,
    showChipSlot: Boolean = false,
    sampleCount: Int? = null,
    palette: DetailTablePalette,
    accentColor: Color? = null,
    highlighted: Boolean = false,
    alignStart: Boolean = false
) {
    Column(
        modifier = Modifier
            .width(width)
            .height(height)
            .detailTableCell(background, palette, accentColor)
            .padding(vertical = 4.dp, horizontal = if (alignStart) 7.dp else 2.dp),
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            2.dp, Alignment.CenterVertically
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlighted) palette.highlightedText else MaterialTheme.colorScheme.onSurface,
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        // Voir ForecastTable.HeaderCell pour la rationale — sampleCount alimente
        // la progression "N/14" du chip calibrating.
        if (showChipSlot) {
            when {
                bias == null -> CalibratingChip(sampleCount = sampleCount)
                else -> ModelBiasChip(bias = bias, onClick = onBiasClick ?: {})
            }
        }
    }
}

@Composable
private fun MinMaxCell(
    tempMax: Double?,
    tempMin: Double?,
    normal: DayNormals?,
    neutralColor: Color,
    separatorColor: Color,
    warmColor: Color,
    coolColor: Color,
    background: Color,
    height: Dp,
    palette: DetailTablePalette
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .detailTableCell(background, palette),
        contentAlignment = Alignment.Center
    ) {
        val display = remember(tempMax, tempMin, normal) {
            buildAnnotatedString {
                val maxText = tempMax?.let { "${it.roundToInt()}°" } ?: "—"
                val maxColor = colorFor(tempMax, normal?.tempMaxNormal, neutralColor, warmColor, coolColor)
                withStyle(SpanStyle(color = maxColor, fontWeight = FontWeight.Medium)) {
                    append(maxText)
                }

                withStyle(SpanStyle(color = separatorColor)) {
                    append(" / ")
                }

                val minText = tempMin?.let { "${it.roundToInt()}°" } ?: "—"
                val minColor = colorFor(tempMin, normal?.tempMinNormal, neutralColor, warmColor, coolColor)
                withStyle(SpanStyle(color = minColor)) {
                    append(minText)
                }
            }
        }
        Text(text = display, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
    }
}

/**
 * Détermine la couleur d'une valeur par rapport à sa normale.
 *
 * Seuil de ±2°C choisi pour éviter le "color noise" — un écart de 1°C est
 * dans le bruit climatique normal, pas visuellement significatif. Au-delà
 * de 2°C, c'est un événement notable (vague de chaleur ou de froid).
 */
private fun colorFor(
    value: Double?,
    normal: Double?,
    neutral: Color,
    warm: Color,
    cool: Color
): Color {
    if (value == null || normal == null) return neutral
    val delta = value - normal
    return when {
        delta > 2.0 -> warm
        delta < -2.0 -> cool
        else -> neutral
    }
}

/** Lookup helper : (max, min) pour un (model, date) donné. */
private fun maxMinAt(
    forecast: CityForecast,
    model: WeatherModel,
    date: LocalDate
): Pair<Double?, Double?> {
    val series = forecast.seriesByModel[model] ?: return null to null
    val idx = series.daily.dates.indexOf(date)
    if (idx < 0) return null to null
    return series.daily.tempMax.getOrNull(idx) to series.daily.tempMin.getOrNull(idx)
}
