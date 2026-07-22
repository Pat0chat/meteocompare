package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.sortedByFamily
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.theme.color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

/** Matrice Modèle × Heure des conditions météo horaires. */
@Composable
fun HourlyWeatherByModelTable(
    forecast: CityForecast,
    modifier: Modifier = Modifier
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

    val cellsByTimestamp: Map<Instant, Map<WeatherModel, HourCellData>> =
        remember(forecast, timestamps) {
            val medianCloudByTs: Map<Instant, Double> = buildMap {
                for (ts in timestamps) {
                    val values = forecast.seriesByModel.mapNotNull { (_, series) ->
                        val i = series.hourly.timestamps.indexOf(ts)
                        if (i < 0) null else series.hourly.cloudCover.getOrNull(i)
                    }
                    if (values.isNotEmpty()) {
                        val sorted = values.sorted()
                        put(
                            ts,
                            if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble()
                            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                        )
                    }
                }
            }

            timestamps.associateWith { ts ->
                forecast.seriesByModel.mapNotNull { (model, series) ->
                    val idx = series.hourly.timestamps.indexOf(ts)
                    if (idx < 0) return@mapNotNull null
                    var inferred = false
                    var condition = WeatherCondition.fromWmoCode(series.hourly.weatherCode.getOrNull(idx))
                        ?: WeatherCondition.inferFromPrecipAndTemp(
                            precipMm = series.hourly.precipitation.getOrNull(idx),
                            tempMinC = series.hourly.temperature2m.getOrNull(idx)
                        )
                    if (condition == null) {
                        medianCloudByTs[ts]?.let {
                            condition = WeatherCondition.fromCloudCover(it)
                            inferred = true
                        }
                    }
                    if (condition == null) return@mapNotNull null
                    model to HourCellData(
                        condition = condition,
                        precipProbability = series.hourly.precipitationProbability.getOrNull(idx),
                        cloudCover = series.hourly.cloudCover.getOrNull(idx),
                        isInferred = inferred
                    )
                }.toMap()
            }
        }

    if (timestamps.isEmpty() || models.isEmpty() || cellsByTimestamp.values.all { it.isEmpty() }) {
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
    val modelWidth = 84.dp
    val timeWidth = 68.dp
    val headerHeight = 44.dp
    val rowHeight = 46.dp
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
        temporalColumnWidth = timeWidth,
        temporalColumnCount = timestamps.size,
        headerHeight = headerHeight,
        rowHeight = rowHeight,
        rowCount = models.size,
        palette = palette,
        modifier = modifier,
        cornerHeader = {
            HeaderCell(
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
                    width = timeWidth,
                    height = headerHeight,
                    background = palette.labelRowBackground(timeIndex, isCurrent),
                    highlighted = isCurrent,
                    palette = palette
                )
            }
        },
        modelRows = {
            models.forEachIndexed { modelIndex, model ->
                HeaderCell(
                    text = model.displayName,
                    width = modelWidth,
                    height = rowHeight,
                    background = palette.labelRowBackground(modelIndex, false),
                    palette = palette,
                    accentColor = model.color(),
                    alignStart = true
                )
            }
        },
        temporalColumns = {
            timestamps.forEachIndexed { timeIndex, ts ->
                val isCurrent = ts == currentHour
                Column(modifier = Modifier.width(timeWidth)) {
                    models.forEachIndexed { modelIndex, model ->
                        HourIconCell(
                            cell = cellsByTimestamp[ts]?.get(model),
                            background = palette.dataRowBackground(modelIndex, isCurrent),
                            height = rowHeight,
                            palette = palette
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun HeaderCell(
    text: String,
    width: Dp,
    height: Dp,
    background: Color,
    palette: DetailTablePalette,
    accentColor: Color? = null,
    alignStart: Boolean = false
) {
    Box(
        modifier = Modifier.width(width).height(height)
            .detailTableCell(background, palette, accentColor)
            .padding(horizontal = if (alignStart) 6.dp else 4.dp),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (accentColor != null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
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
        verticalArrangement = Arrangement.Center
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

private data class HourCellData(
    val condition: WeatherCondition,
    val precipProbability: Int?,
    val cloudCover: Int?,
    /**
     * `true` si la [condition] provient du 3e fallback (médiane peer-consensus
     * de cloud_cover), et pas de la prédiction propre du modèle. L'UI utilise
     * ce flag pour appliquer un alpha réduit — voir [HourIconCell].
     * Défaut à false : les paths (1) weather_code natif et (2) fallback
     * précipitation restent des prédictions propres du modèle.
     */
    val isInferred: Boolean = false
)

@Composable
private fun HourIconCell(
    cell: HourCellData?,
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
        if (cell == null) {
            // Modèle sans donnée pour cette heure (typique : AROME HD ne
            // couvre que J+0 à J+2, colonnes vides au-delà). Tiret discret
            // pour que la cellule reste reconnaissable comme cellule.
            Text(
                "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Marqueur visuel d'inférence : alpha réduit sur le contenu quand
            // la condition vient de la médiane des peers (pas la prédiction
            // propre du modèle). Voir [HourCellData.isInferred].
            val contentModifier = if (cell.isInferred) Modifier.alpha(0.20f) else Modifier
            Column(
                modifier = contentModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WeatherIconDecorative(
                    condition = cell.condition,
                    size = 20.dp,
                    tint = cell.condition.semanticTint()
                )
                val badge = weatherBadgeFor(
                    condition = cell.condition,
                    precipProbability = cell.precipProbability,
                    cloudCover = cell.cloudCover
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
