package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
 * Trois couches verticales dans le bitmap (heightPx recommandé : ~40dp × density) :
 *   1. Bandes de température (heatmap froid→chaud), hauteur variable — zone
 *      0-50% de heightPx
 *   2. Dots de précipitation quand proba >= 30% — y ~ 58% de heightPx
 *   3. Deux rangées de labels textuels :
 *      - Valeur de précipitation (%) sous le dot — y ~ 76%, couleur precip
 *      - Valeur de température (°C) au bas de la cellule — y ~ 97%, couleur texte
 *
 * ─── Densité et scaling ────────────────────────────────────────────────────
 * Toutes les dimensions internes (rayons, tailles de texte, gaps) sont
 * exprimées en FRACTION de heightPx — le renderer est density-agnostic. Le
 * caller passe heightPx = targetDp * displayMetrics.density et le résultat
 * est net à toutes les densités sans code de conversion supplémentaire.
 *
 * ─── Cohérence avec le composable home ────────────────────────────────────
 * Même sémantique d'encodage que `MiniForecastStrip` de la home (heatmap
 * palette 6-stops, seuil pluie 30%, dots proportionnels à la proba). Les
 * VALEURS de la palette sont dupliquées manuellement dans les deux fichiers
 * — synchro à maintenir à la main si l'une évolue.
 */
internal object WidgetMiniForecastRenderer {

    /**
     * Rend la strip complète (bandes + dots + labels temp + labels precip).
     *
     * @param widthPx largeur en pixels du bitmap.
     * @param heightPx hauteur totale — recommandé 40dp * density (~80px xhdpi).
     *   Historiquement 24dp mais bumpé pour loger les 2 rangées de labels
     *   textuels sous les barres et les dots. Un heightPx trop petit rendra
     *   les labels illisibles ; un heightPx trop grand grille le budget
     *   d'affichage du widget 2-row.
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
        val barMaxHeight = heightPx * 0.50f
        val dotRow = heightPx * 0.58f
        val precipLabelBaseline = heightPx * 0.76f
        val tempLabelBaseline = heightPx * 0.97f

        // Text paint dédiés — configurés une fois, réutilisés. Anti-alias est
        // critique sur texte petit (~10-14px) pour la lisibilité.
        val tempTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            textSize = heightPx * 0.28f
            isSubpixelText = true
        }
        val precipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = precipColorArgb
            textAlign = Paint.Align.CENTER
            textSize = heightPx * 0.22f
            isSubpixelText = true
        }

        // ─── Normalisation temp sur la fenêtre ───────────────────────────
        val nonNullTemps = temps12.filterNotNull()
        val minT = nonNullTemps.minOrNull()
        val maxT = nonNullTemps.maxOrNull()
        val range = if (minT != null && maxT != null && maxT > minT) maxT - minT else 1.0

        val rect = RectF()

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
                canvas.drawRect(rect, paint)

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
     * ─── Pourquoi une copie de la version Compose ─────────────────────────
     * Le composable de la home utilise `androidx.compose.ui.graphics.Color`
     * (wrapper d'un Long). Ici on retourne un `Int` ARGB brut compatible avec
     * `android.graphics.Paint.color`. Convertir entre les deux nécessiterait
     * d'importer Compose dans le widget renderer, ce qui casserait la
     * pureté JVM (utile pour les tests unitaires : on peut tester cette
     * fonction sans Android runtime).
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

    /**
     * Interpolation ARGB canal par canal. Ignore l'alpha des inputs et
     * force alpha = 255 dans le résultat — les stops sont opaques par
     * construction, on ne veut pas de translucidité involontaire.
     */
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
