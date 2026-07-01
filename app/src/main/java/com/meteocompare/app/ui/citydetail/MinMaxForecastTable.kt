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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt

// Couleurs chaud / froid. Choisies pour rester lisibles en thèmes clair ET
// sombre. Top-level pour être partagées entre [MinMaxForecastTable] (utilisées
// dans les cellules) et [MinMaxForecastLegend] (chips de légende), sans avoir
// à les ré-instancier dans chaque composable.
private val WarmTempColor = Color(0xFFE53935)  // red 600
private val CoolTempColor = Color(0xFF1E88E5)  // blue 600

/**
 * Tableau fusionné des températures max/min, avec coloration en fonction
 * des normales climatiques.
 *
 * Affichage par cellule : "MAX° / MIN°" — chaque valeur colorée individuellement :
 *   - Rouge si > normale + 2°C (chaud par rapport au climat 10 ans)
 *   - Bleu si < normale − 2°C (froid)
 *   - Neutre (onSurface) sinon
 *
 * Si les normales ne sont pas encore chargées (null), affichage neutre uniquement
 * — l'app reste fonctionnelle, la coloration apparaît dès que les données
 * historiques sont fetchées en background.
 *
 * **Note d'architecture** : la légende N'EST PAS dans ce composable. Pour matcher
 * le pattern des autres tableaux (cf. ForecastSection dans CityDetailScreen),
 * le table-only ici se rend dans une Card, et la légende [MinMaxForecastLegend]
 * se rend SÉPARÉMENT en dessous de la Card.
 */
@Composable
fun MinMaxForecastTable(
    forecast: CityForecast,
    normals: Map<Int, DayNormals>?,
    modifier: Modifier = Modifier
) {
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
            stringResource(R.string.no_daily_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val rowAltBg = MaterialTheme.colorScheme.surfaceContainerLow
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    // Highlight du jour courant — cohérent avec ForecastTable et
    // WeatherByModelTable. Voir ForecastTable pour la rationale de l'alpha.
    val todayBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val today = remember { LocalDate.now() }
    fun bgFor(idx: Int, date: LocalDate): Color = when {
        date == today -> todayBg
        idx % 2 == 1 -> rowAltBg
        else -> Color.Transparent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Colonne figée des dates — largeur 96dp (plus large que la table
            // simple pour accomoder "Mer. 1" en jours longs)
            Column(modifier = Modifier.width(96.dp)) {
                HeaderCellMM(text = "Jour", background = headerBg, width = 96.dp)
                dates.forEachIndexed { idx, date ->
                    DayLabelCellMM(
                        date = date,
                        background = bgFor(idx, date),
                        isToday = date == today
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height((40 + dates.size * 40).dp))

            // Partie scrollable : une colonne par modèle, plus large (88dp) car
            // chaque cellule contient deux valeurs séparées par "/".
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                models.forEach { model ->
                    Column(modifier = Modifier.width(88.dp)) {
                        HeaderCellMM(
                            text = model.displayName,
                            background = headerBg,
                            width = 88.dp
                        )
                        dates.forEachIndexed { idx, date ->
                            val (maxV, minV) = maxMinAt(forecast, model, date)
                            val normalForDay = normals?.get(
                                DayNormals.key(date.monthValue, date.dayOfMonth)
                            )
                            MinMaxCell(
                                tempMax = maxV,
                                tempMin = minV,
                                normal = normalForDay,
                                neutralColor = onSurface,
                                separatorColor = onSurfaceVariant,
                                warmColor = WarmTempColor,
                                coolColor = CoolTempColor,
                                background = bgFor(idx, date)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Légende du tableau min/max — chips colorées expliquant la coloration des
 * cellules. Composable séparé (et non inclus dans [MinMaxForecastTable]) pour
 * matcher le pattern des autres tableaux : la Card englobe juste le tableau,
 * la légende est rendue en dessous.
 *
 * Conditionnelle dans son rendu : ne rend rien si normales == null (pas de
 * coloration à expliquer si les données historiques ne sont pas chargées).
 */
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
private fun HeaderCellMM(text: String, background: Color, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
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
private fun DayLabelCellMM(date: LocalDate, background: Color, isToday: Boolean = false) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d", locale)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(background)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val text = date.format(formatter).replaceFirstChar { it.uppercase() }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday)
                MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
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
    background: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(background),
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
        Text(text = display, style = MaterialTheme.typography.bodyMedium)
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
