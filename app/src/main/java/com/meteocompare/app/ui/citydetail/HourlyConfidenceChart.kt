package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.ui.theme.confidenceColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// ─── Constantes de layout du chart ─────────────────────────────────────────
//
// Extraites au niveau fichier pour garantir que la timeline en dessous (et
// toute future décoration alignée sur l'axe X) reste synchronisée avec
// l'intérieur du Canvas. Si on les changeait en local sans toucher la
// timeline, on retomberait sur le bug "les couleurs ne sont pas raccord
// avec les données du graphique au dessus".
private val ChartCanvasPadding = 8.dp
private val ChartLeftAxisPad = 36.dp
private val ChartRightAxisPad = 8.dp
private val ChartContentStart = ChartCanvasPadding + ChartLeftAxisPad
private val ChartContentEnd = ChartCanvasPadding + ChartRightAxisPad

// ─── Bornes du zoom ────────────────────────────────────────────────────────
//
// Le zoom est piloté par une "view window" (viewStart, viewEnd) exprimée en
// fraction du dataset total ([0, 1]). Au max zoom, la fenêtre visible fait
// MIN_VIEW_SPAN de la donnée totale — soit ~50x sur un dataset de 7 jours =
// environ 3 heures visibles. Assez pour scruter le passage d'un front sans
// perdre le sens de l'échelle globale.
private const val MIN_VIEW_SPAN = 0.02f
private const val MAX_VIEW_SPAN = 1.0f

/**
 * Graphique de bande de confiance horaire — avec pinch-to-zoom sur l'axe X.
 *
 * Visualise les prévisions de température comme une enveloppe min-max
 * autour d'une moyenne. La largeur de la bande à un instant `t` représente
 * directement le désaccord entre modèles à cet horizon.
 *
 * Lecture utilisateur :
 *   - Bande étroite → modèles d'accord, prévision fiable
 *   - Bande qui s'élargit en avançant dans le temps → divergence croissante
 *   - Ligne de mean = la "meilleure estimation" pondérée par résolution
 *
 * Interactions gestuelles :
 *   - **Pinch à 2 doigts** : zoom in/out autour du centroïde du pinch, avec
 *     pan horizontal simultané. Le geste à 1 doigt N'EST PAS intercepté →
 *     laisse la LazyColumn parente scroller normalement en vertical.
 *   - **Double-tap** : reset zoom (fenêtre = tout le dataset).
 *
 * Le hint textuel disparaît dès que l'utilisateur a zoomé au moins une fois
 * (il a découvert le geste, plus besoin d'expliquer).
 */
@Composable
fun HourlyConfidenceChart(
    bands: List<HourlyConfidenceBand>,
    timezone: String?,
    modifier: Modifier = Modifier
) {
    if (bands.size < 2) {
        Box(modifier = modifier.height(260.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.chart_not_enough_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val zone = remember(timezone) { ZoneId.of(timezone ?: "UTC") }
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = onSurface, fontSize = 10.sp)

    // Trois teintes de confiance pré-résolues pour le thème courant. Le Canvas
    // étant un DrawScope (non @Composable), il ne peut pas appeler
    // confidenceColor() lui-même — on les capture par closure ici.
    val confidenceHighColor = confidenceColor(80)
    val confidenceMediumColor = confidenceColor(50)
    val confidenceLowColor = confidenceColor(0)
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bandFillAlpha = if (isDarkTheme) 0.40f else 0.28f
    val timelineStripAlpha = if (isDarkTheme) 0.85f else 0.7f

    // Bornes calculées
    val firstTs = bands.first().timestamp
    val lastTs = bands.last().timestamp
    val totalSeconds = Duration.between(firstTs, lastTs).seconds.coerceAtLeast(1L)

    val allValues = bands.flatMap { listOf(it.minValue, it.maxValue) }
    val yMin = floor(allValues.min()).toFloat() - 1f
    val yMax = ceil(allValues.max()).toFloat() + 1f

    // ─── État de zoom ──────────────────────────────────────────────────────
    // viewStart / viewEnd expriment la fenêtre visible en FRACTION du dataset
    // (0..1). Défaut = (0, 1) = tout visible. On utilise rememberSaveable pour
    // survivre à la rotation — le user aime rarement voir son zoom reset après
    // avoir tourné son téléphone.
    //
    // remember(bands) — recalcul si la référence bands change (nouveau fetch).
    // On ne saveable/remember pas sur bands.size ou hash : on veut juste que
    // ça se remette à jour quand la donnée change, pas nécessairement reset le
    // zoom (qui reste défini sur la fenêtre proportionnelle).
    var viewStart by rememberSaveable { mutableFloatStateOf(0f) }
    var viewEnd by rememberSaveable { mutableFloatStateOf(1f) }
    val isZoomed = (viewEnd - viewStart) < 0.999f

    // Description sémantique pour les lecteurs d'écran. Quand on est zoomé,
    // on préfixe pour signaler l'état et rappeler comment reset (accessibilité).
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val a11yBase = remember(bands, context) {
        com.meteocompare.app.ui.accessibility.A11yFormatter
            .hourlyChartDescription(context, bands)
    }
    val a11yZoomedPrefix = stringResource(R.string.chart_zoom_a11y_zoomed)
    val a11yDescription = if (isZoomed) "$a11yZoomedPrefix. $a11yBase" else a11yBase

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = a11yDescription
            }
            .padding(bottom = 12.dp)
    ) {
        // ─── Header explicatif ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.chart_confidence_band_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.chart_confidence_band_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Hint gestuel — affiché tant que l'utilisateur n'a pas zoomé.
            // Une fois qu'il a fait le geste, il connaît, on masque le hint
            // pour rendre l'UI au calme. S'il reset au double-tap, il re-devient
            // "unzoomed" et le hint réapparaît — c'est OK, la deuxième fois
            // c'est un rappel pas gênant.
            if (!isZoomed) {
                Text(
                    text = stringResource(R.string.chart_zoom_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(ChartCanvasPadding)
                // ─── Pinch/pan à 2 doigts ────────────────────────────────
                // On gère les événements manuellement pour ne CONSOMMER que
                // sur multi-touch. detectTransformGestures{} de Compose
                // consommerait aussi les drags à 1 doigt et bloquerait le
                // scroll vertical de la LazyColumn parente. Ici, si 1 seul
                // doigt est down, on ne consomme rien → la LazyColumn scroll.
                //
                // Reclé sur (bands, totalSeconds) pour que le handler retrouve
                // les bonnes constantes quand la donnée change.
                .pointerInput(bands, totalSeconds) {
                    val leftPadPx = ChartLeftAxisPad.toPx()
                    val rightPadPx = ChartRightAxisPad.toPx()
                    val chartLeftPx = leftPadPx
                    val chartWPx = (size.width - leftPadPx - rightPadPx)
                        .coerceAtLeast(1f)

                    awaitEachGesture {
                        // requireUnconsumed=false → on ne demande PAS un down
                        // vierge, sinon un tap-and-hold qui viendrait juste
                        // après un double-tap serait raté. On veut voir tout
                        // ce qui arrive et décider nous-mêmes.
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size < 2) continue  // 1 doigt : passthrough

                            // ─── Multi-touch : zoom + pan ────────────────
                            val curCentroid = pressed
                                .fold(Offset.Zero) { acc, c -> acc + c.position } /
                                pressed.size.toFloat()
                            val prevCentroid = pressed
                                .fold(Offset.Zero) { acc, c -> acc + c.previousPosition } /
                                pressed.size.toFloat()
                            val pan = curCentroid - prevCentroid

                            // Distance moyenne au centroïde — proxy pour la
                            // "taille" du pinch. Utiliser la MOYENNE plutôt
                            // que le MAX rend le zoom plus stable avec 3+ doigts.
                            val curSpread = pressed
                                .map { (it.position - curCentroid).getDistance() }
                                .average().toFloat().coerceAtLeast(1f)
                            val prevSpread = pressed
                                .map { (it.previousPosition - prevCentroid).getDistance() }
                                .average().toFloat().coerceAtLeast(1f)
                            val zoomFactor = curSpread / prevSpread

                            val curSpan = viewEnd - viewStart
                            val newSpan = (curSpan / zoomFactor)
                                .coerceIn(MIN_VIEW_SPAN, MAX_VIEW_SPAN)

                            // Zoom AUTOUR du centroïde : le point sous le doigt
                            // reste fixe dans l'espace données. C'est le geste
                            // attendu (comme sur Google Maps).
                            val centroidXInChart = curCentroid.x - chartLeftPx
                            val centroidFracInView = (centroidXInChart / chartWPx)
                                .coerceIn(0f, 1f)
                            val worldCentroid = viewStart + centroidFracInView * curSpan
                            var newStart = worldCentroid - centroidFracInView * newSpan

                            // Pan horizontal — ajouté APRÈS le zoom (l'utilisateur
                            // peut pincer et faire glisser en un seul geste).
                            val panFrac = -pan.x / chartWPx * newSpan
                            newStart += panFrac

                            // Clamp aux bornes du dataset
                            newStart = newStart.coerceIn(0f, 1f - newSpan)

                            viewStart = newStart
                            viewEnd = newStart + newSpan

                            // Consommer sinon la LazyColumn essaierait aussi
                            // d'attraper le pan et on aurait un scroll parasite.
                            pressed.forEach { it.consume() }
                        }
                    }
                }
                // ─── Double-tap pour reset ───────────────────────────────
                // Dans son propre pointerInput block, séparé du pinch. Compose
                // route les events aux DEUX blocks en parallèle — pas de conflit :
                // le double-tap requiert 1 doigt down+up rapide, le pinch requiert
                // 2 doigts down. Ils s'excluent naturellement.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            viewStart = 0f
                            viewEnd = 1f
                        }
                    )
                }
        ) {
            val leftPad = ChartLeftAxisPad.toPx()
            val rightPad = ChartRightAxisPad.toPx()
            val topPad = 8.dp.toPx()
            val bottomPad = 28.dp.toPx()
            val chartLeft = leftPad
            val chartTop = topPad
            val chartRight = size.width - rightPad
            val chartBottom = size.height - bottomPad
            val chartW = chartRight - chartLeft
            val chartH = chartBottom - chartTop

            // Fenêtre visible en secondes. C'est cette fenêtre qu'on mappe sur
            // la largeur du chart — les bandes hors fenêtre finiront en dehors
            // du rectangle du Canvas (clip automatique par Compose).
            val visibleStartSec = viewStart * totalSeconds
            val visibleEndSec = viewEnd * totalSeconds
            val visibleSpanSec = (visibleEndSec - visibleStartSec).coerceAtLeast(1f)

            fun xFor(ts: Instant): Float {
                val tsSec = Duration.between(firstTs, ts).seconds.toFloat()
                val fracInView = (tsSec - visibleStartSec) / visibleSpanSec
                return chartLeft + fracInView * chartW
            }

            fun yFor(value: Double): Float {
                return chartBottom - ((value.toFloat() - yMin) / (yMax - yMin)) * chartH
            }

            // ─── Grille Y + labels température ────────────────────────────
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

            // ─── Repères verticaux + labels aux changements de jour ──────
            // On skippe les labels dont le x est hors [chartLeft, chartRight] —
            // sinon en zoomant sur un seul jour, on aurait des labels de jours
            // adjacents dépassant à gauche/droite du chart, chevauchant les
            // labels de température.
            var currentDate: LocalDate? = null
            bands.forEach { band ->
                val localDate = band.timestamp.atZone(zone).toLocalDate()
                if (localDate != currentDate) {
                    val x = xFor(band.timestamp)
                    if (currentDate != null && x in chartLeft..chartRight) {
                        drawLine(
                            color = gridColor.copy(alpha = 0.6f),
                            start = Offset(x, chartTop),
                            end = Offset(x, chartBottom),
                            strokeWidth = 1f
                        )
                    }
                    val label = localDate.dayOfWeek
                        .getDisplayName(JavaTextStyle.SHORT, locale)
                        .replace(".", "")
                    val measured = textMeasurer.measure(label, labelStyle)
                    val labelX = x + 4.dp.toPx()
                    // Ne dessine le texte du jour que s'il est visible ET s'il
                    // tient dans la zone chart (on lui donne son largeur pour
                    // qu'un label proche de chartRight ne dépasse pas non plus).
                    if (labelX in chartLeft..(chartRight - measured.size.width)) {
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = labelX,
                                y = chartBottom + 6.dp.toPx()
                            )
                        )
                    }
                    currentDate = localDate
                }
            }

            // ─── Bande SEGMENTÉE colorée par confiance locale ────────────
            // Au lieu d'un seul Path uniformément teinté, on découpe la bande
            // en quadrilatères entre points consécutifs. Chaque segment prend
            // sa couleur de la moyenne des deux endpoints — résultat : la
            // bande "rougit" naturellement là où les modèles divergent.
            //
            // Les segments hors visible-range sont dessinés quand même — le
            // Canvas clippe automatiquement à ses bounds, coût = 0 en visuel,
            // et éviter d'itérer conditionnellement garde le code simple.
            bands.zipWithNext().forEach { (a, b) ->
                val xa = xFor(a.timestamp)
                val xb = xFor(b.timestamp)
                val maxYa = yFor(a.maxValue)
                val maxYb = yFor(b.maxValue)
                val minYa = yFor(a.minValue)
                val minYb = yFor(b.minValue)

                val avgPercent = (a.percent + b.percent) / 2
                val segmentColor = when {
                    avgPercent >= 80 -> confidenceHighColor
                    avgPercent >= 50 -> confidenceMediumColor
                    else -> confidenceLowColor
                }.copy(alpha = bandFillAlpha)

                val segmentPath = Path().apply {
                    moveTo(xa, maxYa)
                    lineTo(xb, maxYb)
                    lineTo(xb, minYb)
                    lineTo(xa, minYa)
                    close()
                }
                drawPath(path = segmentPath, color = segmentColor)
            }

            // ─── Ligne moyenne pondérée ──────────────────────────────────
            val meanPath = Path().apply {
                bands.forEachIndexed { i, b ->
                    val x = xFor(b.timestamp)
                    val y = yFor(b.meanValue)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(
                path = meanPath,
                color = primary,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Strip et caption restent alignés sur la donnée FULL (pas zoomée) —
        // ils servent de vue d'ensemble. Cela permet au user de garder la
        // perception du "à quel horizon est-on" globalement, même quand le
        // chart est zoomé. Idem pour le caption "Confiance maintenant / à J+N"
        // qui donne les bornes DU DATASET, pas de la fenêtre visible.
        ConfidenceTimeline(bands = bands, stripAlpha = timelineStripAlpha)
    }
}

/**
 * Petite barre sous le graphique qui résume l'évolution de la confiance.
 *
 * On échantillonne 24 points (un par heure de la journée en moyenne pour 7j)
 * et on les colore selon le niveau de confiance. Donne un aperçu instantané
 * de "ça se gâte à partir de quand".
 *
 * `stripAlpha` est passé par le caller : en thème sombre on pousse un peu
 * plus la saturation parce que les couleurs pastel à 70% d'alpha étaient
 * trop ténues — l'utilisateur ne voyait plus le dégradé de confiance.
 */
@Composable
private fun ConfidenceTimeline(bands: List<HourlyConfidenceBand>, stripAlpha: Float) {
    val timeline = remember(bands) {
        if (bands.size <= 24) bands
        else {
            // On échantillonne 24 points équidistants
            val step = bands.size / 24
            bands.filterIndexed { idx, _ -> idx % step == 0 }.take(24)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChartContentStart,
                end = ChartContentEnd,
                top = 4.dp,
                bottom = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        timeline.forEach { band ->
            val color = confidenceColor(band.percent)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(color.copy(alpha = stripAlpha))
            )
        }
    }

    // Caption avec les bornes de confidence
    val firstPercent = bands.first().percent
    val lastBand = bands.last()
    val lastPercent = lastBand.percent
    val daysAhead = Duration.between(bands.first().timestamp, lastBand.timestamp).toDays()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.chart_confidence_now, firstPercent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.chart_confidence_ahead, daysAhead, lastPercent),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = confidenceColor(lastPercent)
        )
    }
}
