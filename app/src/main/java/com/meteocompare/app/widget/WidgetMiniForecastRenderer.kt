package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Rendu de la mini prévision 12h sous forme de [Bitmap], destinée à être
 * affichée dans un widget Glance via `ImageProvider(bitmap)`.
 *
 * ─── Pourquoi un Bitmap et pas des composables Glance ─────────────────────
 * Glance ne supporte PAS Canvas ni DrawScope — impossible de dessiner des
 * formes custom (rectangles de hauteur variable, cercles anti-aliasés). Les
 * seules primitives visuelles sont `Text`, `Image`, `Row/Column/Box` avec un
 * `background` couleur unie.
 *
 * On pourrait émuler la strip avec 12 `Box` en Row (une par heure) et jouer
 * sur le `height` de chaque Box pour figurer la temp — c'est ce que fait
 * `ConfidenceBandStrip` dans le même widget. Mais :
 *   - 12 Box avec 12 modifiers différents = arbre de composition lourd
 *   - Pas d'anti-aliasing sur les dots de précipitation (des Box carrés,
 *     visible à 2-3dp de rayon)
 *   - Le heatmap de température est un gradient : émuler avec 5-6 palettes
 *     discrètes = perte de nuance
 *
 * Le pipeline Bitmap contourne tout ça : on dessine en `android.graphics.Canvas`
 * (plein pouvoir : paint anti-alias, formes arbitraires, gradients), on
 * embarque le résultat via `Image(ImageProvider(bitmap))` dans Glance.
 *
 * ─── Coût ──────────────────────────────────────────────────────────────────
 * Un widget refresh (WorkManager, 15-60 min selon config) recrée le Bitmap
 * à chaque update. À ~500×48px en ARGB_8888, c'est ~96 KB par rendu — perdu
 * ensuite quand Glance rebuild sa RemoteViews tree. Négligeable, on ne
 * cherche pas à cacher (les temps changent chaque heure de toute façon).
 *
 * ─── Synchronisation visuelle avec la home ────────────────────────────────
 * L'encodage COULEUR + POSITION DES DOTS suit exactement la même sémantique
 * que le composable `MiniForecastStrip` de la home (heatmap froid→chaud,
 * seuil 30% pour les dots pluie, rayon 1.5→2.5dp). Un utilisateur qui a le
 * widget ET la home doit reconnaître la même lecture visuelle des deux
 * côtés.
 */
internal object WidgetMiniForecastRenderer {

    /**
     * Rend la strip dans un nouveau bitmap ARGB_8888.
     *
     * @param widthPx largeur en pixels — typiquement la largeur du widget
     *   convertie via `size.width.value * displayMetrics.density`.
     * @param heightPx hauteur en pixels — recommandé 24dp × density (soit ~72px
     *   à densité xhdpi). Les sous-éléments se calibrent en fraction de heightPx.
     * @param temps 12 températures agrégées, l'index 0 est l'heure "maintenant"
     *   (au sens de [ForecastMode.MINI_FORECAST_12H]). Les valeurs null sont
     *   sautées (bar non dessinée) — utile quand un modèle plafonne à H+6h.
     * @param precips 12 probabilités de précipitation (0-100). Un dot n'est
     *   dessiné qu'au-delà du seuil [PRECIP_THRESHOLD].
     * @param precipColorArgb couleur ARGB du dot de pluie. Fournie par le
     *   caller pour respecter le thème du widget (couleur primaire du user).
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        temps: List<Double?>,
        precips: List<Int?>,
        precipColorArgb: Int
    ): Bitmap {
        require(widthPx > 0) { "widthPx doit être > 0, reçu $widthPx" }
        require(heightPx > 0) { "heightPx doit être > 0, reçu $heightPx" }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cellCount = 12
        // On tronque à 12 valeurs — la liste peut être plus courte (données
        // partielles en fin de fenêtre) ou plus longue (défensif).
        val temps12 = temps.take(cellCount)
        val precips12 = precips.take(cellCount)

        val cellWidth = widthPx.toFloat() / cellCount
        val cellGap = heightPx * 0.04f  // ~1dp à densité normale
        val barMaxHeight = heightPx * 0.70f
        val dotRow = heightPx * 0.90f

        // ─── Normalisation temp sur la fenêtre ───────────────────────────
        val nonNullTemps = temps12.filterNotNull()
        val minT = nonNullTemps.minOrNull()
        val maxT = nonNullTemps.maxOrNull()
        val range = if (minT != null && maxT != null && maxT > minT) maxT - minT else 1.0

        // ─── Barres de température ────────────────────────────────────────
        val rect = RectF()  // réutilisé pour éviter les allocations dans la boucle
        temps12.forEachIndexed { i, temp ->
            if (temp == null) return@forEachIndexed
            val normalized = if (minT != null && maxT != null && maxT > minT) {
                (temp - minT) / range
            } else {
                0.5  // cas dégénéré min==max → hauteur milieu
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
        }

        // ─── Dots de précipitation ────────────────────────────────────────
        paint.color = precipColorArgb
        precips12.forEachIndexed { i, prob ->
            if (prob == null || prob < PRECIP_THRESHOLD) return@forEachIndexed
            val x = i * cellWidth + cellWidth / 2f
            // Rayon 1.5→2.5dp mappé sur 30→100% de proba. On exprime en fraction
            // de heightPx pour rester proportionnel : à 24dp de haut, 1.5-2.5dp
            // = 6-10% de heightPx.
            val radiusFrac = 0.06f + (prob - PRECIP_THRESHOLD)
                .coerceAtLeast(0) / 70f * 0.04f
            val radius = heightPx * radiusFrac
            canvas.drawCircle(x, dotRow, radius, paint)
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

    /** Seuil "il pleut" en dessous duquel on ne dessine pas de dot. */
    internal const val PRECIP_THRESHOLD = 30
}
