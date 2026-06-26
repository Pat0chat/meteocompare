package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.ui.theme.ConfidenceHigh
import com.meteocompare.app.ui.theme.ConfidenceLow
import com.meteocompare.app.ui.theme.ConfidenceMedium
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Graphique de bande de confiance horaire.
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
 * Implémentation Canvas :
 *   - Path fermé `(upper-edge L→R, lower-edge R→L)` pour la bande
 *   - Path simple pour la ligne mean
 *   - Repères verticaux aux changements de date locale
 */
@Composable
fun HourlyConfidenceChart(
    bands: List<HourlyConfidenceBand>,
    timezone: String?,
    modifier: Modifier = Modifier
) {
    if (bands.size < 2) {
        Box(modifier = modifier.height(220.dp), contentAlignment = Alignment.Center) {
            Text(
                "Pas assez de données pour calculer une bande",
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

    // Bornes calculées
    val firstTs = bands.first().timestamp
    val lastTs = bands.last().timestamp
    val totalSeconds = Duration.between(firstTs, lastTs).seconds.coerceAtLeast(1L)

    val allValues = bands.flatMap { listOf(it.minValue, it.maxValue) }
    val yMin = floor(allValues.min()).toFloat() - 1f
    val yMax = ceil(allValues.max()).toFloat() + 1f

    // Description sémantique pour les lecteurs d'écran : TalkBack ne voit
    // pas le Canvas. On consolide les infos clés en une phrase lisible.
    val a11yDescription = remember(bands) {
        com.meteocompare.app.ui.accessibility.A11yFormatter
            .hourlyChartDescription(bands)
    }

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = a11yDescription
        }
    ) {
        // ─── Header explicatif ─────────────────────────────────────────
        // Sans cette intro, le chart est cryptique pour un nouvel utilisateur.
        // 2 lignes max pour rester compact tout en donnant la clé de lecture.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Plage des prévisions inter-modèles",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Plus la bande s'élargit, plus les modèles divergent. La couleur indique le niveau de confiance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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

            fun xFor(ts: Instant): Float {
                val frac = Duration.between(firstTs, ts).seconds.toFloat() / totalSeconds
                return chartLeft + frac * chartW
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
            var currentDate: LocalDate? = null
            bands.forEach { band ->
                val localDate = band.timestamp.atZone(zone).toLocalDate()
                if (localDate != currentDate) {
                    val x = xFor(band.timestamp)
                    if (currentDate != null) {
                        drawLine(
                            color = gridColor.copy(alpha = 0.6f),
                            start = Offset(x, chartTop),
                            end = Offset(x, chartBottom),
                            strokeWidth = 1f
                        )
                    }
                    val label = localDate.dayOfWeek
                        .getDisplayName(JavaTextStyle.SHORT, Locale.FRENCH)
                        .replace(".", "")
                    val measured = textMeasurer.measure(label, labelStyle)
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(
                            x = x + 4.dp.toPx(),
                            y = chartBottom + 6.dp.toPx()
                        )
                    )
                    currentDate = localDate
                }
            }

            // ─── Bande SEGMENTÉE colorée par confiance locale ────────────
            // Au lieu d'un seul Path uniformément teinté, on découpe la bande
            // en quadrilatères entre points consécutifs. Chaque segment prend
            // sa couleur de la moyenne des deux endpoints — résultat : la
            // bande "rougit" naturellement là où les modèles divergent, donne
            // visuellement la même information que le timeline strip mais
            // SUR le chart lui-même, pas en dessous.
            bands.zipWithNext().forEach { (a, b) ->
                val xa = xFor(a.timestamp)
                val xb = xFor(b.timestamp)
                val maxYa = yFor(a.maxValue)
                val maxYb = yFor(b.maxValue)
                val minYa = yFor(a.minValue)
                val minYb = yFor(b.minValue)

                val avgPercent = (a.percent + b.percent) / 2
                val segmentColor = when {
                    avgPercent >= 80 -> ConfidenceHigh
                    avgPercent >= 50 -> ConfidenceMedium
                    else -> ConfidenceLow
                }.copy(alpha = 0.28f)

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

        ConfidenceTimeline(bands = bands)
    }
}

/**
 * Petite barre sous le graphique qui résume l'évolution de la confiance.
 *
 * On échantillonne 24 points (un par heure de la journée en moyenne pour 7j)
 * et on les colore selon le niveau de confiance. Donne un aperçu instantané
 * de "ça se gâte à partir de quand".
 */
@Composable
private fun ConfidenceTimeline(bands: List<HourlyConfidenceBand>) {
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        timeline.forEach { band ->
            val color = when {
                band.percent >= 80 -> ConfidenceHigh
                band.percent >= 50 -> ConfidenceMedium
                else -> ConfidenceLow
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(color.copy(alpha = 0.7f))
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
            text = "Confiance maintenant : $firstPercent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "à J+$daysAhead : $lastPercent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                lastPercent >= 80 -> ConfidenceHigh
                lastPercent >= 50 -> ConfidenceMedium
                else -> ConfidenceLow
            }
        )
    }
}
