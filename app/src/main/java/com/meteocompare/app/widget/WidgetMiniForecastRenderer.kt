package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rendu de la mini prévision 12h sous forme de [Bitmap], destinée à être
 * affichée dans un widget Glance via `ImageProvider(bitmap)`.
 *
 * ─── Pourquoi un Bitmap et pas des composables Glance ─────────────────────
 * Glance ne supporte PAS Canvas ni DrawScope — impossible de dessiner des
 * formes custom (rectangles de hauteur variable, cercles anti-aliasés, texte
 * positionné pixel-perfect sous chaque barre). Les primitives visuelles Glance
 * sont `Text`, `Image`, `Row/Column/Box` avec `background` couleur unie.
 *
 * ─── Contenu du bitmap ─────────────────────────────────────────────────────
 * Quatre zones verticales dans le bitmap (heightPx recommandé : ~65dp × density) :
 *   1. Bandes de température (heatmap froid→chaud), zone 0-35% de heightPx
 *   2. Dots de précipitation quand proba >= 30% — y ~ 45% de heightPx
 *   3. Label de précipitation (%) sous le dot — baseline y ~ 67%, couleur precip
 *   4. Label de température (°C) au bas de la cellule — baseline y ~ 93%, couleur texte
 */
internal object WidgetMiniForecastRenderer {

    /**
     * Rend la strip complète (bandes + dots + labels temp + labels precip).
     *
     * @param widthPx largeur en pixels du bitmap.
     * @param heightPx hauteur totale — recommandé 60dp * density (~120px xhdpi).
     *   Historiquement 24dp puis 40dp, bumpé à 60dp pour éviter que les 4 zones
     *   (barres, dots, label precip, label temp) ne s'écrasent verticalement.
     *   Un heightPx trop petit rendra les labels illisibles ou overlappera les
     *   dots ; un heightPx trop grand grille le budget d'affichage du widget
     *   2-row (~140dp max sur 3x2).
     * @param temps 12 températures agrégées, index 0 = "maintenant".
     * @param precips 12 probabilités (0-100).
     * @param precipColorArgb couleur ARGB du dot ET du label de précipitation.
     * @param textColorArgb couleur ARGB du label de température. Fourni par
     *   le caller pour respecter le thème du widget (dark on light, light on
     *   dark). Typiquement `onContainer` du GlanceTheme.
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        temps: List<Double?>,
        precips: List<Int?>,
        precipColorArgb: Int,
        textColorArgb: Int
    ): Bitmap {
        require(widthPx > 0) { "widthPx doit être > 0, reçu $widthPx" }
        require(heightPx > 0) { "heightPx doit être > 0, reçu $heightPx" }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cellCount = 12
        val temps12 = temps.take(cellCount)
        val precips12 = precips.take(cellCount)

        val cellWidth = widthPx.toFloat() / cellCount
        val cellGap = heightPx * 0.02f
        // Nouveau layout à 4 zones bien espacées (vs. l'ancien à 2 zones tassées) :
        //   - Bars : 0 → 35% (bottom des barres à 21dp sur 60dp)
        //   - Dot centre : 45%     (à 27dp — 3.6dp au-dessus des barres, radius incl.)
        //   - Label precip baseline : 67%  (à 40.2dp — 4dp sous le dot bottom)
        //   - Label temp baseline : 93%    (à 55.8dp — 5.7dp sous le precip descender)
        // Chaque zone a ~4-5dp de respiration par rapport à la suivante à 60dp.
        val barMaxHeight = heightPx * 0.35f
        val dotRow = heightPx * 0.45f
        val precipLabelBaseline = heightPx * 0.67f
        val tempLabelBaseline = heightPx * 0.93f

        // Text paint dédiés — configurés une fois, réutilisés. Anti-alias est
        // critique sur texte petit (~10-14px) pour la lisibilité.
        val tempTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            // Fraction 0.19 → à heightPx=60dp*density on obtient ~11.4sp, une
            // taille de texte confortable pour un widget. Historiquement 0.22
            // à 40dp (~8.8sp) — plus petit ET plus tassé. Bump absolu de ~30%.
            textSize = heightPx * 0.19f
            isSubpixelText = true
        }
        val precipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = precipColorArgb
            textAlign = Paint.Align.CENTER
            // Fraction 0.15 → ~9sp à heightPx=60dp*density. Précip label reste
            // légèrement plus petit que temp car il n'apparaît que par
            // intermittence (~30% des cellules) et véhicule une info secondaire.
            textSize = heightPx * 0.15f
            isSubpixelText = true
        }

        // ─── Normalisation temp sur la fenêtre ───────────────────────────
        val nonNullTemps = temps12.filterNotNull()
        val minT = nonNullTemps.minOrNull()
        val maxT = nonNullTemps.maxOrNull()
        val range = if (minT != null && maxT != null && maxT > minT) maxT - minT else 1.0

        val rect = RectF()

        // Fine baseline under the bars. It gives the chart a stable visual
        // rhythm without competing with the temperature colors.
        paint.color = (textColorArgb and 0x00FFFFFF) or 0x18000000
        paint.strokeWidth = (heightPx * 0.012f).coerceAtLeast(1f)
        canvas.drawLine(0f, barMaxHeight, widthPx.toFloat(), barMaxHeight, paint)

        temps12.forEachIndexed { i, temp ->
            val cellCenterX = i * cellWidth + cellWidth / 2f

            // ─── Barre de température ─────────────────────────────────────
            if (temp != null) {
                val normalized = if (minT != null && maxT != null && maxT > minT) {
                    (temp - minT) / range
                } else {
                    0.5
                }
                val barHeight = barMaxHeight * (0.30f + 0.70f * normalized.toFloat())
                val x = i * cellWidth
                rect.set(
                    x + cellGap / 2f,
                    barMaxHeight - barHeight,
                    x + cellWidth - cellGap / 2f,
                    barMaxHeight
                )
                paint.color = temperatureHeatmapArgb(temp)
                val radius = min(cellWidth * 0.24f, heightPx * 0.035f)
                canvas.drawRoundRect(rect, radius, radius, paint)

                // Label temp : entier arrondi + "°" suffixé. Format compact
                // "22°" plutôt que "22°C" — l'unité est implicite dans un
                // contexte météo widget, le "°" seul suffit.
                val tempLabel = "${temp.roundToInt()}°"
                canvas.drawText(tempLabel, cellCenterX, tempLabelBaseline, tempTextPaint)
            }

            // ─── Dot + label de précipitation (proba >= 30%) ─────────────
            val prob = precips12.getOrNull(i)
            if (prob != null && prob >= PRECIP_THRESHOLD) {
                paint.color = precipColorArgb
                // Rayon proportionnel à la proba : ~4% de heightPx à 30%,
                // jusqu'à ~7% à 100%. Assez petit pour rester lisible même
                // quand plusieurs dots consécutifs sont affichés.
                val radiusFrac = 0.04f + (prob - PRECIP_THRESHOLD)
                    .coerceAtLeast(0) / 70f * 0.03f
                val radius = heightPx * radiusFrac
                canvas.drawCircle(cellCenterX, dotRow, radius, paint)

                // Label precip : entier + "%". Positionné sous le dot dans
                // la même cellule verticale.
                canvas.drawText("$prob%", cellCenterX, precipLabelBaseline, precipTextPaint)
            }
        }

        return bitmap
    }

    /**
     * Rampe de couleurs ARGB pour la température, calibrée pour couvrir la
     * plage courante en climat tempéré européen (-10°C → 40°C). Interpolation
     * linéaire entre 6 arrêts.
     *
     * Les VALEURS des stops sont synchronisées manuellement avec
     * `MiniForecastStrip.temperatureHeatmapColor` — si l'un change, l'autre
     * doit suivre pour préserver la cohérence visuelle app + widget.
     */
    internal fun temperatureHeatmapArgb(temp: Double): Int {
        val stops = arrayOf(
            -10.0 to 0xFF1976D2.toInt(),  // bleu profond
            5.0 to 0xFF4FC3F7.toInt(),    // cyan clair
            15.0 to 0xFF81C784.toInt(),   // vert doux
            22.0 to 0xFFFFB74D.toInt(),   // orange chaud
            30.0 to 0xFFEF5350.toInt(),   // rouge saturé
            40.0 to 0xFFB71C1C.toInt()    // rouge sombre (caniculaire)
        )
        if (temp <= stops.first().first) return stops.first().second
        if (temp >= stops.last().first) return stops.last().second

        for (i in 0 until stops.size - 1) {
            val (t1, c1) = stops[i]
            val (t2, c2) = stops[i + 1]
            if (temp in t1..t2) {
                val f = ((temp - t1) / (t2 - t1)).toFloat()
                return lerpArgb(c1, c2, f)
            }
        }
        return stops.last().second
    }

    private fun lerpArgb(a: Int, b: Int, f: Float): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * f).toInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * f).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Seuil "il pleut" en dessous duquel on ne dessine pas de dot / label. */
    internal const val PRECIP_THRESHOLD = 30
}