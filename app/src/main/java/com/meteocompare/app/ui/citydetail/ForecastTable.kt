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
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Style optionnel pour une cellule de valeur. Quand fourni via [ForecastTable.valueStyler],
 * permet de moduler couleur et graisse du texte en fonction de la valeur — par exemple
 * pour rendre les fortes précipitations en bleu foncé gras, ou les vents violents en orange.
 *
 * Si `null` est retourné par le styler pour une valeur donnée, on retombe sur le style
 * neutre (onSurface, FontWeight.Normal). Utile pour ne styliser que les valeurs
 * "remarquables" au-dessus d'un seuil.
 */
data class ValueStyle(
    val color: Color,
    val fontWeight: FontWeight
)

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
 * @param valueStyler Optionnel — applique une couleur et graisse selon la valeur,
 *   pour mettre en évidence visuellement les valeurs élevées (pluie forte, vent fort).
 *   `null` (défaut) → style neutre uniforme.
 */
@Composable
fun ForecastTable(
    forecast: CityForecast,
    valueExtractor: (DailyForecast, Int) -> Double?,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    valueStyler: ((Double) -> ValueStyle?)? = null
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
                            style = value?.let { v -> valueStyler?.invoke(v) },
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
    // Locale courante (mise à jour par AppCompatDelegate.setApplicationLocales).
    // Formatter recréé via `remember(locale)` quand la locale change — sinon
    // on resterait sur le formatter French initial du process.
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d", locale)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(background)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val text = date.format(formatter).replaceFirstChar { it.uppercase() }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ValueCell(text: String, style: ValueStyle?, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            // Si pas de style fourni → couleur par défaut (onSurface via MaterialTheme).
            // L'utilisation de `Color.Unspecified` indique à Text de prendre la couleur
            // depuis le LocalContentColor courant, ce qui respecte le thème.
            color = style?.color ?: Color.Unspecified,
            fontWeight = style?.fontWeight
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
