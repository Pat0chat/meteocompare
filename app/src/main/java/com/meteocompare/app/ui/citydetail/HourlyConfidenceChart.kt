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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.meteocompare.app.domain.model.DayNormals
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
private val ChartCanvasPadding = 8.dp
private val ChartLeftAxisPad = 40.dp
private val ChartRightAxisPad = 8.dp
private val ChartContentStart = ChartCanvasPadding + ChartLeftAxisPad
private val ChartContentEnd = ChartCanvasPadding + ChartRightAxisPad
/**
 * Hauteur du canevas de dessin. Bumpée à 300 dp (vs 260 précédemment) pour
 * donner plus d'amplitude verticale à la bande — utile pour distinguer les
 * traits pointillés "normale 10 ans" (max + min en T°) qui sont proches à
 * l'échelle du chart et qui pouvaient se confondre visuellement.
 */
private val ChartCanvasHeight = 300.dp

// ─── Bornes du zoom ────────────────────────────────────────────────────────
private const val MIN_VIEW_SPAN = 0.02f
private const val MAX_VIEW_SPAN = 1.0f

/**
 * Composant unique de bande de confiance avec sélecteur à 3 états.
 *
 * Encapsule le SegmentedButton (Température / Précipitations / Vent) et rend
 * le chart correspondant en dessous. C'est la wrapper à utiliser depuis
 * l'écran détail — il gère l'état de sélection en interne (rememberSaveable
 * pour survivre à la rotation).
 *
 * L'appelant fournit les 3 séries de bandes (précalculées côté ViewModel pour
 * que la transition entre métriques soit instantanée). Chaque série peut être
 * vide ; le chart affichera son placeholder "pas assez de données" localement.
 */
@Composable
fun ConfidenceBandSection(
    tempBands: List<HourlyConfidenceBand>,
    precipBands: List<HourlyConfidenceBand>,
    windBands: List<HourlyConfidenceBand>,
    timezone: String?,
    normals: Map<Int, DayNormals>?,
    modifier: Modifier = Modifier
) {
    var metric by rememberSaveable(stateSaver = ConfidenceMetric.Saver) {
        mutableStateOf(ConfidenceMetric.TEMPERATURE)
    }

    Column(modifier = modifier) {
        ConfidenceMetricSelector(
            selected = metric,
            onSelect = { metric = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val bands = when (metric) {
            ConfidenceMetric.TEMPERATURE -> tempBands
            ConfidenceMetric.PRECIPITATION -> precipBands
            ConfidenceMetric.WIND -> windBands
        }
        HourlyConfidenceChart(
            bands = bands,
            metric = metric,
            timezone = timezone,
            normals = normals
        )
    }
}

/**
 * Sélecteur segmenté à 3 états — Température / Précipitations / Vent.
 *
 * Rendu comme un SingleChoiceSegmentedButtonRow M3, cohérent avec les autres
 * segmented pickers de l'app (theme, langue, mode display). L'icône est laissée
 * vide : les labels sont assez explicites, et forcer une icône par métrique
 * bruiterait le composant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfidenceMetricSelector(
    selected: ConfidenceMetric,
    onSelect: (ConfidenceMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        ConfidenceMetric.TEMPERATURE to stringResource(R.string.metric_temperature),
        ConfidenceMetric.PRECIPITATION to stringResource(R.string.metric_precipitation),
        ConfidenceMetric.WIND to stringResource(R.string.metric_wind)
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { idx, (metric, label) ->
            SegmentedButton(
                selected = selected == metric,
                onClick = { onSelect(metric) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                icon = { /* labels seuls, plus lisible sans icône avec 3 items */ }
            ) {
                Text(label)
            }
        }
    }
}

/**
 * Graphique de bande de confiance horaire — supporte 3 métriques (température,
 * précipitation, vent) et un overlay optionnel de normales 10 ans.
 *
 * Interactions :
 *   - Pinch à 2 doigts : zoom horizontal (le 1 doigt reste passthrough pour
 *     laisser la LazyColumn scroller).
 *   - Double-tap : reset zoom.
 *
 * Overlay normales :
 *   - Température : 2 traits pointillés (min et max 10 ans) qui varient jour
 *     par jour — rendus comme step-function le long de l'axe X.
 *   - Précipitations : 1 trait pointillé (précipitation moyenne journalière).
 *   - Vent : 1 trait pointillé (vent moyen journalier).
 *   Les normales manquantes (nullables sur [DayNormals]) sont simplement skipées.
 */
@Composable
fun HourlyConfidenceChart(
    bands: List<HourlyConfidenceBand>,
    metric: ConfidenceMetric = ConfidenceMetric.TEMPERATURE,
    timezone: String?,
    normals: Map<Int, DayNormals>? = null,
    modifier: Modifier = Modifier
) {
    if (bands.size < 2) {
        Box(modifier = modifier.height(ChartCanvasHeight), contentAlignment = Alignment.Center) {
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

    val confidenceHighColor = confidenceColor(80)
    val confidenceMediumColor = confidenceColor(50)
    val confidenceLowColor = confidenceColor(0)
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val bandFillAlpha = if (isDarkTheme) 0.40f else 0.28f
    val timelineStripAlpha = if (isDarkTheme) 0.85f else 0.7f

    // Palette dédiée aux traits pointillés "normale 10 ans" — sémantique
    // per-métrique (rouge = chaud, bleu = froid/humide, jaune = vent). Les
    // couleurs sont sélectionnées par [normalsPalette] pour rester lisibles
    // sur les deux thèmes (variantes brighter en dark mode).
    val normalsPalette = normalsPalette(isDarkTheme)

    // Bornes calculées
    val firstTs = bands.first().timestamp
    val lastTs = bands.last().timestamp
    val totalSeconds = Duration.between(firstTs, lastTs).seconds.coerceAtLeast(1L)

    // ─── Bornes Y — intègrent les normales quand présentes ────────────────
    // Mémorisées avec `remember(bands, metric, normals, zone)` : le calcul
    // itère toutes les bandes (~168 items) + tous les jours-de-l'année
    // couverts, chaque recomposition (theme swap, zoom, resize) refaisait
    // ~500 additions inutilement. La clé de dépendance capture précisément
    // les inputs qui changent la valeur, rien de plus.
    val (yMin, yMax) = remember(bands, metric, normals, zone) {
        // Pour la précipitation, le min est toujours 0 — on force la borne
        // basse à 0 pour que la bande touche le sol (visuellement plus
        // naturel : pas de pluie = ligne à 0). Idem pour le vent (jamais
        // négatif).
        val forceZeroMin = metric == ConfidenceMetric.PRECIPITATION ||
            metric == ConfidenceMetric.WIND

        val allValues = ArrayList<Double>(bands.size * 2 + 16)
        bands.forEach {
            allValues += it.minValue
            allValues += it.maxValue
        }
        // Sinon un jour très pluvieux/venteux dans les normales sortirait de
        // la fenêtre visible ; on étend les bornes pour garder les traits
        // pointillés à l'écran.
        if (normals != null) {
            val datesInRange = bands
                .map { it.timestamp.atZone(zone).toLocalDate() }
                .distinct()
            datesInRange.forEach { date ->
                normals[DayNormals.key(date.monthValue, date.dayOfMonth)]?.let { n ->
                    when (metric) {
                        ConfidenceMetric.TEMPERATURE -> {
                            allValues += n.tempMinNormal
                            allValues += n.tempMaxNormal
                        }
                        ConfidenceMetric.PRECIPITATION ->
                            n.precipMeanNormal?.let { allValues += it }
                        ConfidenceMetric.WIND ->
                            n.windMeanNormal?.let { allValues += it }
                    }
                }
            }
        }
        val rawMin = allValues.min()
        val rawMax = allValues.max()
        val yMinComputed = if (forceZeroMin) 0f else floor(rawMin).toFloat() - 1f
        val yMaxComputed = ceil(rawMax).toFloat() + 1f
        yMinComputed to yMaxComputed
    }

    // ─── État de zoom ──────────────────────────────────────────────────────
    var viewStart by rememberSaveable { mutableFloatStateOf(0f) }
    var viewEnd by rememberSaveable { mutableFloatStateOf(1f) }
    val isZoomed = (viewEnd - viewStart) < 0.999f

    // Description sémantique
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val a11yBase = remember(bands, context, metric) {
        // Note : le formatter reste calé sur la température, ce qui n'est pas
        // idéal pour precip/wind mais c'est un incrément futur — pour l'instant
        // TalkBack lit les valeurs numériques, l'utilisateur devine la métrique
        // via le label du SegmentedButton du dessus. Correct pour le MVP.
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
        // Header explicatif
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.chart_confidence_band_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                .height(ChartCanvasHeight)
                .padding(ChartCanvasPadding)
                .pointerInput(bands, totalSeconds) {
                    val leftPadPx = ChartLeftAxisPad.toPx()
                    val rightPadPx = ChartRightAxisPad.toPx()
                    val chartLeftPx = leftPadPx
                    val chartWPx = (size.width - leftPadPx - rightPadPx)
                        .coerceAtLeast(1f)

                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size < 2) continue

                            val curCentroid = pressed
                                .fold(Offset.Zero) { acc, c -> acc + c.position } /
                                pressed.size.toFloat()
                            val prevCentroid = pressed
                                .fold(Offset.Zero) { acc, c -> acc + c.previousPosition } /
                                pressed.size.toFloat()
                            val pan = curCentroid - prevCentroid

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

                            val centroidXInChart = curCentroid.x - chartLeftPx
                            val centroidFracInView = (centroidXInChart / chartWPx)
                                .coerceIn(0f, 1f)
                            val worldCentroid = viewStart + centroidFracInView * curSpan
                            var newStart = worldCentroid - centroidFracInView * newSpan

                            val panFrac = -pan.x / chartWPx * newSpan
                            newStart += panFrac

                            newStart = newStart.coerceIn(0f, 1f - newSpan)

                            viewStart = newStart
                            viewEnd = newStart + newSpan

                            pressed.forEach { it.consume() }
                        }
                    }
                }
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

            // ─── Grille Y + labels valeurs ────────────────────────────────
            val yTicks = 4
            for (i in 0..yTicks) {
                val y = chartBottom - (i.toFloat() / yTicks) * chartH
                drawLine(
                    color = gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1f
                )
                val axisValue = yMin + (yMax - yMin) * i / yTicks
                // Format spécifique par métrique — précip en 1 décimale sous 1 mm,
                // vent et température en entier (précision non signifiante en dessous).
                val label = when (metric) {
                    ConfidenceMetric.TEMPERATURE -> "${axisValue.roundToInt()}°"
                    ConfidenceMetric.PRECIPITATION -> {
                        if (axisValue < 1f) "%.1f".format(axisValue)
                        else "${axisValue.roundToInt()}"
                    }
                    ConfidenceMetric.WIND -> "${axisValue.roundToInt()}"
                }
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

            // ─── Traits pointillés "normale 10 ans" ──────────────────────
            // Rendus AVANT la ligne moyenne pour que celle-ci reste au-dessus
            // (l'œil identifie mean = "notre estimation la plus probable" ;
            // les normales sont un contexte historique).
            if (normals != null) {
                drawNormalsOverlay(
                    bands = bands,
                    metric = metric,
                    normals = normals,
                    zone = zone,
                    xFor = ::xFor,
                    yFor = ::yFor,
                    chartLeft = chartLeft,
                    chartRight = chartRight,
                    palette = normalsPalette
                )
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

        // Légende compacte des normales — n'apparaît que si le graphe
        // trace effectivement quelque chose (pour ne pas mentir à
        // l'utilisateur en promettant une donnée absente du cache).
        val hasNormals = normals != null && hasNormalsForMetric(bands, metric, normals, zone)
        if (hasNormals) {
            NormalsLegend(metric = metric, palette = normalsPalette)
        }

        ConfidenceTimeline(bands = bands, stripAlpha = timelineStripAlpha)
    }
}

/**
 * Overlay des normales 10 ans en step-function le long de l'axe X.
 *
 * Les normales sont journalières mais l'axe est horaire → chaque jour rendu
 * comme un segment horizontal (constant sur les 24 h) qui saute à la valeur
 * suivante à minuit local. C'est visuellement plus honnête qu'une
 * interpolation linéaire entre jours, qui ferait croire à une variation
 * intra-journalière alors que c'est purement du day-of-year.
 *
 * ─── Codage couleur sémantique ─────────────────────────────────────────
 *  - TEMPERATURE : ROUGE pour la max journalière (référence "chaud"), BLEU
 *    pour la min journalière (référence "froid"). Convention grand public
 *    ("bleu = froid, rouge = chaud") — pas besoin de légende pour deviner.
 *  - PRECIPITATION : BLEU (même palette que la min température — l'eau est
 *    bleue partout dans la culture visuelle météo).
 *  - WIND : JAUNE/AMBRE — assigné par contraste avec les deux autres (bleu
 *    et rouge sont pris), et parce que les cartes vent utilisent souvent le
 *    jaune pour les vitesses moyennes-élevées (au-dessus du fond bleu).
 *
 * Les couleurs sont piochées dans [NormalsPalette], calibrée pour rester
 * lisible en dark ET light theme (variantes brighter en dark). Les dashes
 * varient aussi (long pour max, court pour min en T°) — redondance
 * visuelle bienvenue pour daltoniens rouge-vert.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNormalsOverlay(
    bands: List<HourlyConfidenceBand>,
    metric: ConfidenceMetric,
    normals: Map<Int, DayNormals>,
    zone: ZoneId,
    xFor: (Instant) -> Float,
    yFor: (Double) -> Float,
    chartLeft: Float,
    chartRight: Float,
    palette: NormalsPalette
) {
    val strokeWidth = 1.5.dp.toPx()
    val dashLong = PathEffect.dashPathEffect(
        floatArrayOf(8.dp.toPx(), 4.dp.toPx()), 0f
    )
    val dashShort = PathEffect.dashPathEffect(
        floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f
    )

    // Découpe la série en runs de même date. `bands` est déjà trié par
    // timestamp, on peut donc balayer linéairement.
    var runStart = 0
    for (i in bands.indices) {
        val date = bands[i].timestamp.atZone(zone).toLocalDate()
        val nextDate = bands.getOrNull(i + 1)?.timestamp?.atZone(zone)?.toLocalDate()
        val endOfRun = (nextDate == null || nextDate != date)
        if (endOfRun) {
            val startX = xFor(bands[runStart].timestamp).coerceAtLeast(chartLeft)
            val endX = xFor(bands[i].timestamp).coerceAtMost(chartRight)
            if (endX > startX) {
                val normal = normals[DayNormals.key(date.monthValue, date.dayOfMonth)]
                if (normal != null) {
                    when (metric) {
                        ConfidenceMetric.TEMPERATURE -> {
                            val yMax = yFor(normal.tempMaxNormal)
                            drawLine(
                                color = palette.tempMax,
                                start = Offset(startX, yMax),
                                end = Offset(endX, yMax),
                                strokeWidth = strokeWidth,
                                pathEffect = dashLong
                            )
                            val yMin = yFor(normal.tempMinNormal)
                            drawLine(
                                color = palette.tempMin,
                                start = Offset(startX, yMin),
                                end = Offset(endX, yMin),
                                strokeWidth = strokeWidth,
                                pathEffect = dashShort
                            )
                        }
                        ConfidenceMetric.PRECIPITATION -> {
                            normal.precipMeanNormal?.let { p ->
                                val y = yFor(p)
                                drawLine(
                                    color = palette.precip,
                                    start = Offset(startX, y),
                                    end = Offset(endX, y),
                                    strokeWidth = strokeWidth,
                                    pathEffect = dashLong
                                )
                            }
                        }
                        ConfidenceMetric.WIND -> {
                            normal.windMeanNormal?.let { w ->
                                val y = yFor(w)
                                drawLine(
                                    color = palette.wind,
                                    start = Offset(startX, y),
                                    end = Offset(endX, y),
                                    strokeWidth = strokeWidth,
                                    pathEffect = dashLong
                                )
                            }
                        }
                    }
                }
            }
            runStart = i + 1
        }
    }
}

/**
 * Vérifie qu'au moins un jour du chart a une valeur de normale exploitable
 * pour la métrique demandée. Sert à ne pas afficher la légende quand aucune
 * ligne pointillée n'est en fait rendue (cas cache pré-feature).
 */
private fun hasNormalsForMetric(
    bands: List<HourlyConfidenceBand>,
    metric: ConfidenceMetric,
    normals: Map<Int, DayNormals>,
    zone: ZoneId
): Boolean {
    val dates = bands.map { it.timestamp.atZone(zone).toLocalDate() }.distinct()
    return dates.any { date ->
        val n = normals[DayNormals.key(date.monthValue, date.dayOfMonth)] ?: return@any false
        when (metric) {
            ConfidenceMetric.TEMPERATURE -> true // toujours présent si la normale existe
            ConfidenceMetric.PRECIPITATION -> n.precipMeanNormal != null
            ConfidenceMetric.WIND -> n.windMeanNormal != null
        }
    }
}

/**
 * Légende compacte des normales 10 ans — un pastille + label par trait rendu.
 * On la rend seulement quand des normales sont effectivement affichées
 * (voir [hasNormalsForMetric]).
 */
/**
 * Palette de couleurs des traits pointillés "normale 10 ans" du chart.
 *
 * Assignation sémantique par métrique — voir le docblock de
 * [drawNormalsOverlay] pour la justification :
 *   - `tempMax` = rouge (chaud)
 *   - `tempMin` = bleu (froid)
 *   - `precip`  = bleu (eau)
 *   - `wind`    = jaune/ambre (contraste)
 *
 * Fabriquée par [normalsPalette] qui prend en compte le thème (dark/light)
 * pour garder un contraste suffisant sur les deux fonds.
 */
private data class NormalsPalette(
    val tempMax: Color,
    val tempMin: Color,
    val precip: Color,
    val wind: Color
)

/**
 * Palette calibrée par thème.
 *
 *   - Light : teintes 600-800 des palettes Material — assez saturées pour
 *     ressortir sur un fond clair (surface Material light).
 *   - Dark : teintes 300-400 — versions plus claires pour garder le
 *     contraste sur fond sombre. Sinon un rouge 700 sur du gris #121212
 *     deviendrait indistinguable.
 *
 * Les valeurs bleu (tempMin et precip) sont VOLONTAIREMENT identiques dans
 * chaque thème — c'est cohérent avec la spec ("min = bleu, précip = bleu")
 * et évite d'introduire deux teintes bleues qui se confondraient à l'écran.
 */
@Composable
private fun normalsPalette(isDarkTheme: Boolean): NormalsPalette = remember(isDarkTheme) {
    if (isDarkTheme) {
        NormalsPalette(
            tempMax = Color(0xFFEF5350),  // Red 400 — visible sur fond sombre
            tempMin = Color(0xFF42A5F5),  // Blue 400
            precip = Color(0xFF42A5F5),   // Blue 400 (identique tempMin)
            wind = Color(0xFFFFCA28)      // Amber 400 — bien distinct du rouge/bleu
        )
    } else {
        NormalsPalette(
            tempMax = Color(0xFFD32F2F),  // Red 700 — bon contraste sur fond clair
            tempMin = Color(0xFF1976D2),  // Blue 700
            precip = Color(0xFF1976D2),   // Blue 700
            wind = Color(0xFFF57C00)      // Orange 700 — sur fond clair l'ambre pur
            // manque de contraste ; on choisit un orange plus profond, encore
            // visuellement du côté "jaune-orange" (respecte la spec "vent = jaune").
        )
    }
}

/**
 * Légende compacte des normales 10 ans — un tiret coloré + label par trait
 * effectivement rendu :
 *   - TEMPERATURE : deux lignes (max rouge + min bleu)
 *   - PRECIPITATION / WIND : une seule ligne
 *
 * Rendue uniquement quand des normales sont effectivement affichées (voir
 * [hasNormalsForMetric]) — évite de mentir à l'utilisateur en promettant
 * une donnée absente du cache pré-feature.
 */
@Composable
private fun NormalsLegend(
    metric: ConfidenceMetric,
    palette: NormalsPalette
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        when (metric) {
            ConfidenceMetric.TEMPERATURE -> {
                NormalsLegendRow(
                    color = palette.tempMax,
                    label = stringResource(R.string.chart_normals_legend_temp_max)
                )
                NormalsLegendRow(
                    color = palette.tempMin,
                    label = stringResource(R.string.chart_normals_legend_temp_min)
                )
            }
            ConfidenceMetric.PRECIPITATION -> {
                NormalsLegendRow(
                    color = palette.precip,
                    label = stringResource(R.string.chart_normals_legend_precip)
                )
            }
            ConfidenceMetric.WIND -> {
                NormalsLegendRow(
                    color = palette.wind,
                    label = stringResource(R.string.chart_normals_legend_wind)
                )
            }
        }
    }
}

/** Une ligne de la légende : petit tiret pointillé de couleur + label. */
@Composable
private fun NormalsLegendRow(color: Color, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .padding(end = 8.dp)
                .width(18.dp)
                .height(2.dp)
        ) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f
                )
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Petite barre sous le graphique qui résume l'évolution de la confiance.
 *
 * On échantillonne 24 points (un par heure de la journée en moyenne pour 7j)
 * et on les colore selon le niveau de confiance.
 */
@Composable
private fun ConfidenceTimeline(bands: List<HourlyConfidenceBand>, stripAlpha: Float) {
    val timeline = remember(bands) {
        if (bands.size <= 24) bands
        else {
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
