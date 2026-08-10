package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Sparkline horizontal miniature qui affiche les 12 prochaines heures de
 * prévision, en superposant :
 *   - une bande de température (heatmap froid → chaud)
 *   - des marqueurs de précipitation (dots bleus sous la bande)
 *
 * ─── Design ────────────────────────────────────────────────────────────────
 * Hauteur totale : 30dp. Assez petit pour tenir en bas d'une card tout en
 * gardant assez de hauteur pour afficher la température dans chaque barre,
 * sans concurrencer les valeurs principales de la card.
 *
 * Chaque heure occupe `width / 12` — les cellules sont donc adaptatives à la
 * largeur disponible. Sur un écran classique 360dp de contenu utile
 * (screen − padding card), ça donne des cellules d'environ 26dp, largement
 * lisibles.
 *
 * ─── Encodage visuel ───────────────────────────────────────────────────────
 * **Température** : chaque heure = un rectangle de couleur, hauteur
 * proportionnelle à la temp normalisée dans la fenêtre 12h de cette card.
 * La valeur arrondie (ex. 18°) est dessinée au centre de chaque barre avec
 * un contraste automatique noir/blanc selon la couleur de fond.
 *
 * **Précipitation** : dot au bas de la cellule si la proba dépasse 30%.
 * Rayon 1.5-2.5dp selon l'intensité (plus la proba est forte, plus le dot
 * est gros). Sous 30% on ne dessine RIEN — un utilisateur lit "s'il n'y a
 * pas de dot, il ne pleut pas dans l'heure".
 *
 * ─── A11y ──────────────────────────────────────────────────────────────────
 * Le composable dessine du pixel pur (Canvas) → invisible pour TalkBack sans
 * semantic label. On fournit une description consolidée du type
 * "Prévision 12h : entre 18 et 24°C, pluie prévue à 3 heures".
 *
 * ─── Robustesse ────────────────────────────────────────────────────────────
 * Ne crashe jamais sur données partielles :
 *   - Si moins de 12 heures fournies : dessine ce qui est présent, laisse la
 *     fin vide (mieux que "extrapoler" une donnée qu'on n'a pas)
 *   - Si toutes les temps sont null : ne dessine que les dots précip et
 *     retourne — pas de division par zéro sur (max−min)
 *   - Si min == max : hauteur uniforme à ~73% + couleur uniforme au milieu de
 *     la rampe
 */
@Composable
internal fun MiniForecastStrip(
    hourlyTemps: List<Double?>,
    hourlyPrecipProb: List<Int?>,
    modifier: Modifier = Modifier,
    startTime: LocalDateTime? = null
) {
    val density = LocalDensity.current
    // Palette dérivée de MaterialTheme pour respecter le thème dark/light — on
    // ne fige pas un fond blanc côté canvas. Le noneTint est utilisé pour la
    // cellule "pas de donnée" (rare, cache pré-feature).
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val precipColor = MaterialTheme.colorScheme.primary
    val a11yLabel = buildA11yLabel(hourlyTemps, hourlyPrecipProb)

    // Formatter horaire calé sur la préférence 24h du device — pas sur la locale.
    // Un utilisateur EN qui a réglé son téléphone en 24h veut voir "15h" pas
    // "3 PM". Pour les autres, "h a" donne "3 PM".
    val context = LocalContext.current
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val platformLocale = LocalLocale.current.platformLocale
    val hourFormatter = remember(is24, platformLocale) {
        // "H'h'" → "15h" / "0h"  (FR-friendly, sans zero padding)
        // "h a" → "3 PM" / "12 AM"
        val pattern = if (is24) "H'h'" else "h a"
        DateTimeFormatter.ofPattern(pattern, platformLocale)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .testTag(TAG_MINI_FORECAST_STRIP)
                .semantics { contentDescription = a11yLabel }
        ) {
            // ─── Setup ────────────────────────────────────────────────────
            val cellCount = 12
            val cellWidth = size.width / cellCount
            val cellGap = with(density) { 1.dp.toPx() }
            val barMaxHeight = size.height * 0.76f
            val dotRow = size.height * 0.92f
            val labelTextSizePx = with(density) { 8.sp.toPx() }
            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = labelTextSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }

            // Normalisation de la temp sur la fenêtre affichée. On ne prend que
            // les 12 premières valeurs (défensif : liste plus longue = on tronque,
            // liste plus courte = on rend ce qu'on a).
            val temps = hourlyTemps.take(cellCount)
            val precips = hourlyPrecipProb.take(cellCount)
            val nonNullTemps = temps.filterNotNull()
            val minT = nonNullTemps.minOrNull()
            val maxT = nonNullTemps.maxOrNull()
            val range = if (minT != null && maxT != null && maxT > minT) maxT - minT else 1.0

            // ─── Bandes de température ────────────────────────────────────
            temps.forEachIndexed { i, temp ->
                if (temp == null) return@forEachIndexed
                val normalized = if (minT != null && maxT != null && maxT > minT) {
                    (temp - minT) / range
                } else {
                    0.5 // cas dégénéré min==max → hauteur milieu
                }
                // 45% minimum : garantit assez de hauteur pour le libellé "18°".
                val barHeight = (barMaxHeight * (0.45f + 0.55f * normalized)).toFloat()
                val x = i * cellWidth
                val barTop = barMaxHeight - barHeight
                val barColor = temperatureHeatmapColor(temp)

                drawRect(
                    color = barColor,
                    topLeft = Offset(x + cellGap / 2f, barTop),
                    size = Size(cellWidth - cellGap, barHeight)
                )

                // Température centrée dans chaque barre. Le contraste est calculé
                // depuis la luminance du fond pour rester lisible en dark/light.
                val labelColor = if (barColor.luminance() > 0.52f) {
                    Color.Black.copy(alpha = 0.78f)
                } else {
                    Color.White
                }
                labelPaint.color = labelColor.toArgb()
                val metrics = labelPaint.fontMetrics
                val labelBaseline = barTop +
                        (barHeight - metrics.ascent - metrics.descent) / 2f

                drawContext.canvas.nativeCanvas.drawText(
                    "${temp.roundToInt()}°",
                    x + cellWidth / 2f,
                    labelBaseline,
                    labelPaint
                )
            }

            // ─── Dots de précipitation ────────────────────────────────────
            // Seuil 30% pour "il pleuvra" — sous ce seuil un utilisateur ne
            // prendrait pas de parapluie, donc pas d'alerte visuelle.
            precips.forEachIndexed { i, prob ->
                if (prob == null || prob < 30) return@forEachIndexed
                val x = i * cellWidth + cellWidth / 2f
                // Rayon 1.5 → 2.5dp mappé sur 30 → 100% de proba.
                val radiusDp = 1.5f + (prob - 30).coerceAtLeast(0) / 70f * 1f
                val radius = with(density) { radiusDp.dp.toPx() }
                drawCircle(
                    color = precipColor,
                    center = Offset(x, dotRow),
                    radius = radius
                )
            }
        }

        // ─── Ancres horaires ──────────────────────────────────────────────
        // Trois labels alignés sur le début, le milieu et la fin de la strip.
        // Rôle : ancrer la sparkline à des heures concrètes pour que
        // l'utilisateur puisse répondre à "à quelle heure exactement il va
        // pleuvoir ?" sans ouvrir le détail.
        //
        // On ne rend RIEN si startTime est null (cache pré-feature ou fuseau
        // ville inconnu) — plutôt que d'afficher des heures fantaisistes.
        //
        // Le milieu montre l'heure de la bar 6 (soit +6h), pas +5h ou +7h,
        // parce que c'est la borne "au milieu de la fenêtre" qui a le plus de
        // sens narratif pour l'utilisateur. Fin = +11h (dernière bar), pas +12h
        // (qui serait la première hors fenêtre).
        if (startTime != null) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().testTag(TAG_MINI_FORECAST_ANCHORS),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = startTime.format(hourFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
                Text(
                    text = startTime.plusHours(6).format(hourFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
                Text(
                    text = startTime.plusHours(11).format(hourFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun buildA11yLabel(
    temps: List<Double?>,
    precipProbs: List<Int?>
): String {
    val nonNull = temps.filterNotNull()
    val minT = nonNull.minOrNull()?.roundToInt()
    val maxT = nonNull.maxOrNull()?.roundToInt()
    val rainHours = precipProbs.count { it != null && it >= 30 }

    // 4 templates de description selon les données présentes.
    return when {
        minT == null || maxT == null -> stringResource(R.string.mini_forecast_a11y_no_data)
        rainHours == 0 -> stringResource(R.string.mini_forecast_a11y_no_rain, minT, maxT)
        else -> stringResource(R.string.mini_forecast_a11y_with_rain, minT, maxT, rainHours)
    }
}

internal const val TAG_MINI_FORECAST_STRIP = "mini_forecast_strip"
internal const val TAG_MINI_FORECAST_ANCHORS = "mini_forecast_anchors"