package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.DayConditionsRow
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import java.time.format.DateTimeFormatter

/**
 * Matrice Jour × Modèle des conditions météo.
 *
 * Layout calqué sur [ForecastTable] pour cohérence visuelle :
 *   - Colonne gauche figée avec les dates (76dp)
 *   - VerticalDivider
 *   - Row scrollable horizontalement avec une Column par modèle (largeur 64dp)
 *
 * Chaque cellule contient l'icône colorée. Le scan horizontal d'une ligne =
 * "que prédisent les différents modèles pour ce jour ?" — un alignement
 * parfait des icônes signale accord, une icône différente saute aux yeux.
 *
 * Différence vs ForecastTable : pas de [ValueStyle] ni de formatter — on a
 * un type fixe (icône), pas une valeur numérique à styler. Tenter de fusionner
 * les deux composants nous aurait imposé un generic <T> ou un sealed sur
 * "Value | Icon", ce qui aurait alourdi le call-site pour pas grand-chose.
 *
 * @param rows Une entrée par date, dans l'ordre chronologique.
 * @param modelOrder Liste des modèles à afficher en colonnes, dans l'ordre.
 *   On accepte un ordre externe (typiquement `forecast.availableModels`,
 *   trié par résolution) plutôt qu'un tri interne, pour ne pas dupliquer
 *   la décision ailleurs si on la veut différente plus tard.
 */
@Composable
fun WeatherByModelTable(
    rows: List<DayConditionsRow>,
    modelOrder: List<WeatherModel>,
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

    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val rowAltBg = MaterialTheme.colorScheme.surfaceContainerLow

    Row(modifier = modifier.fillMaxWidth()) {
        // Colonne figée : dates
        Column(modifier = Modifier.width(76.dp)) {
            HeaderCell(text = "", background = headerBg, modifier = Modifier.width(76.dp))
            rows.forEachIndexed { idx, row ->
                DayLabelCell(
                    text = formatDayLabel(row),
                    background = if (idx % 2 == 1) rowAltBg else Color.Transparent
                )
            }
        }

        VerticalDivider(
            modifier = Modifier.height((40 + rows.size * 44).dp)
        )

        // Partie scrollable : une colonne par modèle
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            modelOrder.forEach { model ->
                Column(modifier = Modifier.width(64.dp)) {
                    HeaderCell(
                        text = model.displayName,
                        background = headerBg,
                        modifier = Modifier.width(64.dp)
                    )
                    rows.forEachIndexed { idx, row ->
                        IconCell(
                            condition = row.byModel[model],
                            background = if (idx % 2 == 1) rowAltBg else Color.Transparent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatDayLabel(row: DayConditionsRow): String {
    val locale = LocalConfiguration.current.locales[0]
    // remember(locale) sinon un changement de langue ne rafraîchirait pas le
    // format ("EEE d" est sensible à la locale).
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d", locale)
    }
    return row.date.format(formatter).replaceFirstChar { it.uppercase() }
}

@Composable
private fun HeaderCell(text: String, background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(background)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DayLabelCell(text: String, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(background)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IconCell(condition: WeatherCondition?, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (condition != null) {
            WeatherIconDecorative(
                condition = condition,
                size = 24.dp,
                tint = condition.semanticTint()
            )
        } else {
            // Modèle sans donnée pour ce jour (typique : AROME HD ne couvre
            // que J+0 à J+2 — colonnes "vides" au-delà). On affiche un tiret
            // discret pour que la cellule reste reconnaissable comme une
            // cellule (pas comme un trou de layout).
            Text("—", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
