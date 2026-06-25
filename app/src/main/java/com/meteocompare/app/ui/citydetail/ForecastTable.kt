package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tableau Jour × Modèle pour une variable donnée.
 *
 * Layout :
 *   - Colonne gauche figée avec les dates (largeur 76dp)
 *   - Divider vertical
 *   - Row scrollable horizontalement avec une Column par modèle
 *
 * Chaque cellule fait 64dp de large et 36dp de haut. Les en-têtes ont un fond
 * légèrement teinté pour les distinguer.
 *
 * @param valueExtractor Fonction qui renvoie la valeur (Double?) pour un
 *   modèle et un index de jour donnés. Retourner null laisse une cellule "—".
 * @param valueFormatter Fonction de formatage (ex: `{ "${it.roundToInt()}°" }`).
 */
@Composable
fun ForecastTable(
    forecast: CityForecast,
    valueExtractor: (DailyForecast, Int) -> Double?,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier
) {
    // Toutes les dates couvertes par au moins un modèle, triées
    val dates = remember(forecast) {
        forecast.seriesByModel.values
            .flatMap { it.daily.dates }
            .distinct()
            .sorted()
    }
    val models = remember(forecast) {
        forecast.seriesByModel.keys.toList().sortedBy { it.ordinal }
    }

    if (dates.isEmpty() || models.isEmpty()) {
        Text(
            "Pas de données journalières",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val rowAltBg = MaterialTheme.colorScheme.surfaceContainerLow

    Row(modifier = modifier.fillMaxWidth()) {
        // Colonne figée des dates
        Column(modifier = Modifier.width(76.dp)) {
            HeaderCell(text = "", background = headerBg, modifier = Modifier.width(76.dp))
            dates.forEachIndexed { idx, date ->
                DayLabelCell(
                    date = date,
                    background = if (idx % 2 == 1) rowAltBg else Color.Transparent
                )
            }
        }

        VerticalDivider(
            modifier = Modifier
                .height((40 + dates.size * 36).dp)
                .padding(vertical = 0.dp)
        )

        // Partie scrollable
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            models.forEach { model ->
                Column(modifier = Modifier.width(64.dp)) {
                    HeaderCell(
                        text = model.displayName,
                        background = headerBg,
                        modifier = Modifier.width(64.dp)
                    )
                    dates.forEachIndexed { idx, date ->
                        val value = valueAt(forecast, model, date, valueExtractor)
                        ValueCell(
                            text = value?.let(valueFormatter) ?: "—",
                            background = if (idx % 2 == 1) rowAltBg else Color.Transparent
                        )
                    }
                }
            }
        }
    }
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
private fun DayLabelCell(date: LocalDate, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(background)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val text = date.format(DAY_LABEL_FMT).replaceFirstChar { it.uppercase() }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ValueCell(text: String, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
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

private val DAY_LABEL_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)
