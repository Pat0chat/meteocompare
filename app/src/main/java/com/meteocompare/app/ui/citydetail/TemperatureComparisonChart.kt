package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DayNormals
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Graphique de comparaison des températures max sur plusieurs modèles.
 *
 * Implémenté en Canvas plutôt qu'avec Vico parce que :
 *   - Le besoin est simple (lignes superposées + points + axes), pas de besoin
 *     d'interactivité, de zoom ou d'animations.
 *   - Vico 2.x a une API qui évolue trop vite — un Canvas direct est immune
 *     aux breaking changes.
 *   - Contrôle total sur le styling (couleurs par modèle, axes M3-themed).
 *
 * Si on a besoin plus tard d'interactivité (tap pour voir la valeur) ou de
 * zoom, on swappera vers Vico — le contrat (CityForecast → graphique) ne change pas.
 */
@Composable
fun TemperatureComparisonChart(
    forecast: CityForecast,
    normals: Map<Int, DayNormals>? = null,
    modifier: Modifier = Modifier
) {
    // Extraction de la donnée chart : on prend tempMax, et on convertit les dates
    // en day-offset par rapport à la première date trouvée (today).
    val series = remember(forecast) { extractTemperatureSeries(forecast) }
    if (series.isEmpty()) {
        Box(modifier = modifier.height(220.dp), contentAlignment = Alignment.Center) {
            Text("Aucune donnée à afficher", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val normalLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = onSurface, fontSize = 10.sp)

    val today = series.first().points.first().date
    val maxDay = series.flatMap { it.points }.maxOf {
        ChronoUnit.DAYS.between(today, it.date).toInt()
    }.coerceAtLeast(1)

    // Calcule la séquence des normales pour les jours couverts par le chart.
    // Ces points seront tracés en pointillés. Le `mapNotNull` skip les jours
    // sans donnée (cas rare : début d'année avec Feb 29).
    val normalsForChart = remember(normals, today, maxDay) {
        if (normals == null) emptyList<Triple<Int, Double, Double>>()
        else (0..maxDay).mapNotNull { dayOffset ->
            val date = today.plusDays(dayOffset.toLong())
            val key = DayNormals.key(date.monthValue, date.dayOfMonth)
            normals[key]?.let { Triple(dayOffset, it.tempMaxNormal, it.tempMinNormal) }
        }
    }

    // Inclut les normales dans le calcul de l'échelle Y pour éviter qu'elles
    // sortent du chart. yMin descend pour englober la min normale.
    val allValues = buildList {
        addAll(series.flatMap { it.points }.map { it.temperature })
        normalsForChart.forEach { (_, max, min) -> add(max); add(min) }
    }
    val yMin = floor(allValues.min()).toFloat() - 1f
    val yMax = ceil(allValues.max()).toFloat() + 1f

    val a11yDescription = remember(series, normalsForChart) {
        com.meteocompare.app.ui.accessibility.A11yFormatter
            .multiModelChartDescription(
                modelCount = series.size,
                daysCovered = maxDay + 1
            ) + if (normalsForChart.isNotEmpty()) " Normales climatiques 10 ans affichées en pointillés." else ""
    }

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = a11yDescription
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(8.dp)
        ) {
            val leftPad = 36.dp.toPx()
            val rightPad = 8.dp.toPx()
            val topPad = 8.dp.toPx()
            val bottomPad = 28.dp.toPx()

            val chartLeft = leftPad
            val chartTop = topPad
            val chartRight = size.width - rightPad
            val chartBottom = size.height - bottomPad
            val chartW = chartRight - chartLeft
            val chartH = chartBottom - chartTop

            // ─── Grille Y + labels ───────────────────────────────────────
            val yTicks = 4
            for (i in 0..yTicks) {
                val y = chartBottom - (i.toFloat() / yTicks) * chartH
                drawLine(
                    color = gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1f
                )
                val tempValue = yMin + (yMax - yMin) * i / yTicks
                val label = "${tempValue.roundToInt()}°"
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = chartLeft - measured.size.width - 4.dp.toPx(),
                        y = y - measured.size.height / 2f
                    )
                )
            }

            // ─── Labels X (jours) ────────────────────────────────────────
            for (day in 0..maxDay) {
                val x = chartLeft + (day.toFloat() / maxDay) * chartW
                val date = today.plusDays(day.toLong())
                val label = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.FRENCH)
                    .replace(".", "")
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = x - measured.size.width / 2f,
                        y = chartBottom + 6.dp.toPx()
                    )
                )
            }

            // ─── Pointillés des normales (max et min) ───────────────────
            // Dessinés AVANT les lignes des modèles pour qu'ils restent en
            // arrière-plan. Pattern (8 ON, 6 OFF) en pixels pour un look
            // distinctement "dashed" sans être trop fragmenté.
            if (normalsForChart.isNotEmpty()) {
                val dashPattern = floatArrayOf(8f, 6f)
                val dashStroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(dashPattern, 0f)
                )

                // Max normales — légèrement plus foncé pour cohérence visuelle
                // avec les températures max (plus "visibles" sur le chart).
                val maxNormalPath = Path()
                normalsForChart.forEachIndexed { i, (dayOffset, max, _) ->
                    val x = chartLeft + (dayOffset.toFloat() / maxDay) * chartW
                    val y = chartBottom - ((max.toFloat() - yMin) / (yMax - yMin)) * chartH
                    if (i == 0) maxNormalPath.moveTo(x, y) else maxNormalPath.lineTo(x, y)
                }
                drawPath(
                    path = maxNormalPath,
                    color = normalLineColor.copy(alpha = 0.75f),
                    style = dashStroke
                )

                // Min normales
                val minNormalPath = Path()
                normalsForChart.forEachIndexed { i, (dayOffset, _, min) ->
                    val x = chartLeft + (dayOffset.toFloat() / maxDay) * chartW
                    val y = chartBottom - ((min.toFloat() - yMin) / (yMax - yMin)) * chartH
                    if (i == 0) minNormalPath.moveTo(x, y) else minNormalPath.lineTo(x, y)
                }
                drawPath(
                    path = minNormalPath,
                    color = normalLineColor.copy(alpha = 0.5f),
                    style = dashStroke
                )
            }

            // ─── Lignes (une par modèle) ────────────────────────────────
            series.forEach { modelSeries ->
                val path = Path()
                modelSeries.points.forEachIndexed { i, point ->
                    val dayOffset = ChronoUnit.DAYS.between(today, point.date).toFloat()
                    val x = chartLeft + (dayOffset / maxDay) * chartW
                    // point.temperature est un Double (domain). Compose Canvas
                    // travaille en Float — conversion explicite pour éviter le
                    // mismatch sur Path.moveTo/lineTo et Offset.
                    val y = chartBottom - ((point.temperature.toFloat() - yMin) / (yMax - yMin)) * chartH
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = modelSeries.color,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Points
                modelSeries.points.forEach { point ->
                    val dayOffset = ChronoUnit.DAYS.between(today, point.date).toFloat()
                    val x = chartLeft + (dayOffset / maxDay) * chartW
                    val y = chartBottom - ((point.temperature.toFloat() - yMin) / (yMax - yMin)) * chartH
                    drawCircle(
                        color = modelSeries.color,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        // Légende des modèles
        ModelLegend(series.map { it.model to it.color })

        // Indicateur "Normales 10 ans" — n'apparaît que si les normales sont
        // chargées et affichées sur le chart. Conservé en dehors de ModelLegend
        // pour ne pas dépendre du wrapping FlowRow des modèles.
        if (normalsForChart.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(
                    modifier = Modifier
                        .height(2.dp)
                        .width(20.dp)
                ) {
                    drawLine(
                        color = normalLineColor.copy(alpha = 0.75f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )
                }
                Text(
                    text = "  Normales 10 ans (max / min)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelLegend(items: List<Pair<WeatherModel, Color>>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (model, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = color,
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                ) {}
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

// ─── Data preparation ───────────────────────────────────────────────────────

private data class TemperatureSeries(
    val model: WeatherModel,
    val color: Color,
    val points: List<TempPoint>
)

private data class TempPoint(val date: LocalDate, val temperature: Double)

private fun extractTemperatureSeries(forecast: CityForecast): List<TemperatureSeries> {
    return forecast.seriesByModel.toList()
        .sortedBy { it.first.ordinal }
        .mapNotNull { (model, series) ->
            val points = series.daily.dates.zip(series.daily.tempMax)
                .mapNotNull { (date, temp) -> temp?.let { TempPoint(date, it) } }
            if (points.isEmpty()) null
            else TemperatureSeries(model, model.color(), points)
        }
}
