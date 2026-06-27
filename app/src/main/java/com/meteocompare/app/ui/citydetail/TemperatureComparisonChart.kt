package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
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
    // Extraction de la donnée chart : tempMax + tempMin, convertis les dates en
    // day-offset par rapport à la première date trouvée (today).
    val allSeries = remember(forecast) { extractTemperatureSeries(forecast) }
    if (allSeries.isEmpty()) {
        Box(modifier = modifier.height(220.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chart_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Set des modèles actuellement MASQUÉS (vide par défaut = tous visibles).
    // Utiliser "masqué" plutôt que "visible" simplifie le défaut : on commence
    // toujours par tout afficher, et le state ne contient que les exceptions.
    // `remember` plutôt que `rememberSaveable` car la sélection est purement
    // visuelle / éphémère — pas critique de survivre au process death, le user
    // peut re-toggler en 2 secondes.
    var hiddenModels by remember(forecast) {
        mutableStateOf(emptySet<WeatherModel>())
    }

    // Modèles actuellement visibles (filtered). Si vide, l'utilisateur a tout
    // désactivé dans la légende — on affichera un état vide au lieu d'essayer
    // de dessiner un chart sans données. La ModelLegend reste affichée pour
    // qu'il puisse re-cliquer un modèle et revenir à un état avec données.
    val visibleSeries = allSeries.filter { it.model !in hiddenModels }

    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val normalLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = onSurface, fontSize = 10.sp)

    // Couleurs des normales : max en orange foncé (chaud), min en bleu foncé
    // (froid). Permet de distinguer instantanément les deux sans regarder la
    // légende. Hors Canvas DrawScope pour être utilisables aussi dans la légende.
    val warmNormalColor = androidx.compose.ui.graphics.Color(0xFFD84315) // deep orange 800
    val coolNormalColor = androidx.compose.ui.graphics.Color(0xFF0277BD) // light blue 800

    // Strings extraits via stringResource AVANT le Modifier.semantics car
    // semantics est une lambda Modifier (pas @Composable), elle ne peut pas
    // appeler stringResource directement.
    val emptyA11y = stringResource(R.string.chart_a11y_no_model)
    val emptyMessage = stringResource(R.string.chart_no_model_selected)
    val context = LocalContext.current
    // A11y description du chart non-vide : précalculée hors semantics (Context).
    val populatedA11y = if (visibleSeries.isNotEmpty()) {
        com.meteocompare.app.ui.accessibility.A11yFormatter
            .multiModelChartDescription(context, modelCount = visibleSeries.size, daysCovered = 7)
    } else ""

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = if (visibleSeries.isEmpty()) emptyA11y else populatedA11y
            }
            .padding(bottom = 12.dp)
    ) {
        if (visibleSeries.isEmpty()) {
            // État vide : tous les modèles ont été désactivés via la légende.
            // On garde la même hauteur que le Canvas normal (270dp) pour que
            // le layout ne saute pas brutalement quand l'utilisateur re-active
            // un modèle. La ModelLegend juste en dessous reste cliquable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Computations utilisées uniquement pour le rendu — déplacées ici
            // pour ne pas tenter de calculer .first()/.maxOf() sur visibleSeries
            // vide (cause du crash précédent).
            val today = visibleSeries.first().points.first().date
            val maxDay = visibleSeries.flatMap { it.points }.maxOf {
                ChronoUnit.DAYS.between(today, it.date).toInt()
            }.coerceAtLeast(1)

            val normalsForChart = if (normals == null) emptyList<Triple<Int, Double, Double>>()
            else (0..maxDay).mapNotNull { dayOffset ->
                val date = today.plusDays(dayOffset.toLong())
                val key = DayNormals.key(date.monthValue, date.dayOfMonth)
                normals[key]?.let { Triple(dayOffset, it.tempMaxNormal, it.tempMinNormal) }
            }

            // Échelle Y : inclut min ET max des modèles visibles + normales.
            // Si l'utilisateur cache un modèle aux valeurs extrêmes, l'axe se
            // ré-ajuste, donnant plus de précision visuelle sur les modèles restants.
            val allValues = buildList {
                visibleSeries.flatMap { it.points }.forEach { p ->
                    add(p.max)
                    p.min?.let { add(it) }
                }
                normalsForChart.forEach { (_, max, min) -> add(max); add(min) }
            }
            val yMin = floor(allValues.min()).toFloat() - 1f
            val yMax = ceil(allValues.max()).toFloat() + 1f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
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
                    width = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(dashPattern, 0f)
                )

                val maxNormalPath = Path()
                normalsForChart.forEachIndexed { i, (dayOffset, max, _) ->
                    val x = chartLeft + (dayOffset.toFloat() / maxDay) * chartW
                    val y = chartBottom - ((max.toFloat() - yMin) / (yMax - yMin)) * chartH
                    if (i == 0) maxNormalPath.moveTo(x, y) else maxNormalPath.lineTo(x, y)
                }
                drawPath(
                    path = maxNormalPath,
                    color = warmNormalColor,
                    style = dashStroke
                )

                val minNormalPath = Path()
                normalsForChart.forEachIndexed { i, (dayOffset, _, min) ->
                    val x = chartLeft + (dayOffset.toFloat() / maxDay) * chartW
                    val y = chartBottom - ((min.toFloat() - yMin) / (yMax - yMin)) * chartH
                    if (i == 0) minNormalPath.moveTo(x, y) else minNormalPath.lineTo(x, y)
                }
                drawPath(
                    path = minNormalPath,
                    color = coolNormalColor,
                    style = dashStroke
                )
            }

            // ─── Par modèle : enveloppe translucide (min→max) + ligne max ─
            //
            // Pourquoi enveloppe plutôt qu'une 2e ligne (min) :
            //   - 2 lignes × 5 modèles + 2 normales = 12 traits qui se croisent.
            //     L'œil n'arrive plus à suivre un modèle individuel.
            //   - Enveloppe = zone fillée à 13% alpha. Recule visuellement (par
            //     opposition aux lignes vives), donc les 5 max restent les
            //     éléments saillants. Là où les modèles s'accordent sur la plage
            //     diurne, les enveloppes se superposent en un nuage homogène
            //     (signal correct). Là où ils divergent, on voit l'écart latéral.
            //   - Pas de perte d'info : pour lire un min précis, le tableau
            //     min/max plus bas est plus efficace. Le chart sert à comparer
            //     les modèles visuellement, l'enveloppe fait ça mieux que 2 lignes.
            //
            // Pour chaque modèle on construit un polygone fermé :
            //   forward le long du max → backward le long du min → close()
            visibleSeries.forEach { modelSeries ->
                // ─── Enveloppe min/max (filled polygon) ──────────────────
                val pointsWithMin = modelSeries.points.filter { it.min != null }
                if (pointsWithMin.size >= 2) {
                    val envelope = Path()
                    // Forward le long du max
                    pointsWithMin.forEachIndexed { i, point ->
                        val dayOffset = ChronoUnit.DAYS.between(today, point.date).toFloat()
                        val x = chartLeft + (dayOffset / maxDay) * chartW
                        val y = chartBottom - ((point.max.toFloat() - yMin) / (yMax - yMin)) * chartH
                        if (i == 0) envelope.moveTo(x, y) else envelope.lineTo(x, y)
                    }
                    // Backward le long du min pour fermer la zone
                    pointsWithMin.asReversed().forEach { point ->
                        val dayOffset = ChronoUnit.DAYS.between(today, point.date).toFloat()
                        val x = chartLeft + (dayOffset / maxDay) * chartW
                        val y = chartBottom - ((point.min!!.toFloat() - yMin) / (yMax - yMin)) * chartH
                        envelope.lineTo(x, y)
                    }
                    envelope.close()
                    drawPath(
                        path = envelope,
                        color = modelSeries.color.copy(alpha = 0.13f)
                    )
                }

                // ─── Ligne max (élément focal) ───────────────────────────
                val maxPath = Path()
                modelSeries.points.forEachIndexed { i, point ->
                    val dayOffset = ChronoUnit.DAYS.between(today, point.date).toFloat()
                    val x = chartLeft + (dayOffset / maxDay) * chartW
                    val y = chartBottom - ((point.max.toFloat() - yMin) / (yMax - yMin)) * chartH
                    if (i == 0) maxPath.moveTo(x, y) else maxPath.lineTo(x, y)
                }
                drawPath(
                    path = maxPath,
                    color = modelSeries.color,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Cercles sur les points max (focal points)
                modelSeries.points.forEach { point ->
                    val dayOffset = ChronoUnit.DAYS.between(today, point.date).toFloat()
                    val x = chartLeft + (dayOffset / maxDay) * chartW
                    val y = chartBottom - ((point.max.toFloat() - yMin) / (yMax - yMin)) * chartH
                    drawCircle(
                        color = modelSeries.color,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

            // Légende des éléments additionnels — normales + min/max + hint.
            // Reste DANS le else parce qu'elle utilise normalsForChart (scopé
            // au else) et qu'elle n'aurait aucun sens en état vide.
            if (normalsForChart.isNotEmpty() || visibleSeries.any { it.points.any { p -> p.min != null } }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    if (normalsForChart.isNotEmpty()) {
                        LegendDashedRow(
                            color = warmNormalColor,
                            label = stringResource(R.string.chart_legend_normal_max)
                        )
                        LegendDashedRow(
                            color = coolNormalColor,
                            label = stringResource(R.string.chart_legend_normal_min)
                        )
                    }
                    if (visibleSeries.any { it.points.any { p -> p.min != null } }) {
                        Text(
                            text = stringResource(R.string.chart_legend_envelope),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // Hint discret pour expliquer le toggle
                    Text(
                        text = stringResource(R.string.chart_legend_toggle_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        // ─── Fin du if/else (vide vs Canvas) ─────────────────────────────

        // ModelLegend toujours visible — c'est le seul moyen pour l'utilisateur
        // de ré-activer un modèle quand il les a tous désactivés. Si elle était
        // dans le else, l'état vide serait sans issue.
        ModelLegend(
            items = allSeries.map { Triple(it.model, it.color, it.model !in hiddenModels) },
            onToggle = { model ->
                hiddenModels = if (model in hiddenModels) hiddenModels - model
                               else hiddenModels + model
            }
        )
    }
}

/**
 * Petite ligne pointillée + label, utilisée dans la légende du chart pour les
 * normales. Centralisée pour éviter de dupliquer le bloc Canvas+Text.
 */
@Composable
private fun LegendDashedRow(
    color: androidx.compose.ui.graphics.Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Canvas(
            modifier = Modifier
                .height(2.dp)
                .width(20.dp)
        ) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )
        }
        Text(
            text = "  $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelLegend(
    items: List<Triple<WeatherModel, Color, Boolean>>,
    onToggle: (WeatherModel) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (model, color, visible) ->
            // Chip cliquable. Quand masqué, on baisse l'alpha à 0.35 pour
            // signaler visuellement l'état "hidden" tout en restant lisible
            // pour que l'utilisateur sache où re-cliquer pour réactiver.
            val alpha = if (visible) 1f else 0.35f
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .clickable { onToggle(model) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Surface(
                    color = color.copy(alpha = alpha),
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                ) {}
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    textDecoration = if (visible) null
                                     else androidx.compose.ui.text.style.TextDecoration.LineThrough,
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

private data class TempPoint(
    val date: LocalDate,
    val max: Double,
    val min: Double?
)

private fun extractTemperatureSeries(forecast: CityForecast): List<TemperatureSeries> {
    return forecast.seriesByModel.toList()
        .sortedBy { it.first.ordinal }
        .mapNotNull { (model, series) ->
            val dates = series.daily.dates
            val maxes = series.daily.tempMax
            val mins = series.daily.tempMin
            val points = dates.indices.mapNotNull { i ->
                val max = maxes.getOrNull(i) ?: return@mapNotNull null
                val min = mins.getOrNull(i)  // peut être null sans bloquer la série
                TempPoint(date = dates[i], max = max, min = min)
            }
            if (points.isEmpty()) null
            else TemperatureSeries(model, model.color(), points)
        }
}
