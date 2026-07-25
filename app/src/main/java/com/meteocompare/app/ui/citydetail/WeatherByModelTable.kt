package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.model.sortedByFamily
import com.meteocompare.app.domain.usecase.DayCellExtras
import com.meteocompare.app.domain.usecase.DayConditionsRow
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import com.meteocompare.app.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Matrice Modèle × Jour des conditions météo. */
@Composable
fun WeatherByModelTable(
    rows: List<DayConditionsRow>,
    modelOrder: List<WeatherModel>,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty() || modelOrder.isEmpty()) {
        Text(
            stringResource(R.string.no_daily_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val sortedModels = remember(modelOrder) { modelOrder.sortedByFamily() }
    val palette = detailTablePalette()
    val modelWidth = 84.dp
    val dateWidth = 72.dp
    val headerHeight = 40.dp
    val rowHeight = 50.dp

    FrozenDetailTableLayout(
        modelColumnWidth = modelWidth,
        temporalColumnCount = rows.size,
        headerHeight = headerHeight,
        rowHeight = rowHeight,
        rowCount = sortedModels.size,
        palette = palette,
        modifier = modifier,
        cornerHeader = {
            HeaderCell(
                text = stringResource(R.string.detail_table_model_header),
                background = palette.frozenHeaderSurface,
                width = modelWidth,
                height = headerHeight,
                palette = palette,
                alignStart = true
            )
        },
        temporalHeaders = {
            rows.forEachIndexed { dateIndex, row ->
                val isToday = row.date == today
                HeaderCell(
                    text = formatDayLabel(row),
                    background = palette.labelRowBackground(dateIndex, isToday),
                    width = dateWidth,
                    height = headerHeight,
                    palette = palette,
                    highlighted = isToday
                )
            }
        },
        modelRows = {
            sortedModels.forEachIndexed { modelIndex, model ->
                HeaderCell(
                    text = model.displayName,
                    background = palette.labelRowBackground(modelIndex, false),
                    width = modelWidth,
                    height = rowHeight,
                    palette = palette,
                    accentColor = model.color(),
                    alignStart = true
                )
            }
        },
        temporalColumns = {
            rows.forEachIndexed { dateIndex, row ->
                val isToday = row.date == today
                Column(modifier = Modifier.width(dateWidth)) {
                    sortedModels.forEachIndexed { modelIndex, model ->
                        IconCell(
                            condition = row.byModel[model],
                            extras = row.extrasByModel[model],
                            isInferred = model in row.inferredByModel,
                            background = palette.dataRowBackground(modelIndex, isToday),
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
private fun formatDayLabel(row: DayConditionsRow): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    return row.date.format(formatter).replaceFirstChar { it.uppercase() }
}

@Composable
private fun HeaderCell(
    text: String,
    background: Color,
    width: Dp,
    height: Dp,
    palette: DetailTablePalette,
    accentColor: Color? = null,
    highlighted: Boolean = false,
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
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = when {
                highlighted -> palette.highlightedText
                accentColor != null -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IconCell(
    condition: WeatherCondition?,
    extras: DayCellExtras?,
    isInferred: Boolean,
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
        if (condition == null) {
            // Modèle sans donnée pour ce jour (typique : AROME HD ne couvre
            // que J+0 à J+2 — colonnes "vides" au-delà). On affiche un tiret
            // discret pour que la cellule reste reconnaissable comme une
            // cellule (pas comme un trou de layout).
            Text("—", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Marqueur visuel d'inférence : alpha réduit sur le contenu pour
            // signaler que cette condition vient de la médiane des peers et
            // pas de la prédiction propre du modèle. Non-invasif visuellement
            // — l'icône reste lisible et colorée — mais suffisant pour qu'un
            // utilisateur qui compare les cellules perçoive le différentiel.
            val contentModifier = if (isInferred) Modifier.alpha(0.25f) else Modifier
            Column(
                modifier = contentModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WeatherIconDecorative(
                    condition = condition,
                    size = 22.dp,
                    tint = condition.semanticTint()
                )
                val badge = weatherBadgeFor(
                    condition = condition,
                    precipProbability = extras?.precipProbabilityMax,
                    cloudCover = extras?.cloudCoverMean
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

/**
 * Légende sous le tableau — sans elle, l'utilisateur peut peiner à associer
 * "icône violette = orage" lors du premier coup d'œil. FlowRow pour gérer le
 * débordement sur petits écrans (français = libellés plus longs).
 *
 * On ne légende PAS toutes les sous-familles (FREEZING_RAIN, RAIN_SHOWERS,
 * SNOW_SHOWERS) — l'utilisateur les reconnaît au contexte si jamais elles
 * apparaissent, et la légende deviendrait illisible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeatherLegend() {
    val items = listOf(
        WeatherCondition.CLEAR,
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.OVERCAST,
        WeatherCondition.RAIN,
        WeatherCondition.SNOW,
        WeatherCondition.THUNDERSTORM
    )
    Column {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIconDecorative(
                        condition = c,
                        size = 16.dp,
                        tint = c.semanticTint()
                    )
                    Text(
                        text = stringResource(c.legendStringRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
        // Caption expliquant les badges "60%" affichés sous les icônes.
        // Sans cette note, l'utilisateur voit "60%" sous une icône soleil ou
        // nuage et hésite : probabilité de pluie ? d'ensoleillement ? de tenir
        // au sec ? Deux règles simples :
        //   - Sous une icône nuageuse/couverte → couverture nuageuse
        //   - Sous une icône pluie/neige/orage → probabilité de précipitation
        // labelSmall + onSurfaceVariant : ton discret, ne concurrence pas
        // les chips de légende juste au-dessus qui sont l'info primaire.
        Text(
            text = stringResource(R.string.weather_legend_percent_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        // Note d'inférence : couronne le marquage alpha 0.35 sur les cellules
        // dont la condition vient du peer-consensus. Sans cette note, un
        // utilisateur qui remarque une cellule "grisée" ne peut pas savoir
        // qu'il s'agit d'une inférence (il pensera à un bug d'affichage).
        // Un petit exemple visuel inline (icône + alpha) pour ancrer la
        // sémantique — plus efficace qu'un texte seul.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            WeatherIconDecorative(
                condition = WeatherCondition.PARTLY_CLOUDY,
                size = 16.dp,
                tint = WeatherCondition.PARTLY_CLOUDY.semanticTint(),
                modifier = Modifier.alpha(0.25f)
            )
            Text(
                text = stringResource(R.string.weather_legend_inferred_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun WeatherCondition.legendStringRes(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_legend_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_legend_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_legend_overcast
    WeatherCondition.RAIN -> R.string.weather_legend_rain
    WeatherCondition.SNOW -> R.string.weather_legend_snow
    WeatherCondition.THUNDERSTORM -> R.string.weather_legend_thunderstorm
    else -> R.string.weather_unknown
}
