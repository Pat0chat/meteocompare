package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rendu bitmap de la mini-prévision 12 h utilisée dans les widgets Glance.
 *
 * L'ordre vertical est volontairement symétrique autour de l'axe temporel :
 *
 *   température
 *   heatmap de température
 *   ───────── axe horaire ─────────
 *   heatmap de précipitations
 *   probabilité de précipitations
 *
 * Cette composition rend immédiatement lisible la relation entre une heure,
 * sa température et son risque de pluie. Le bitmap est transparent : le fond
 * arrondi reste fourni par le composable Glance parent.
 */
internal object WidgetMiniForecastRenderer {

    private const val CELL_COUNT = 12

    /**
     * Rend les 12 colonnes de prévision.
     *
     * [timelineLabels] contient les trois ancres gauche, centre et droite de
     * la ligne temporelle. Quand elles sont absentes, des libellés relatifs
     * sont utilisés afin que le graphique reste autonome.
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        temps: List<Double?>,
        precips: List<Int?>,
        precipColorArgb: Int,
        textColorArgb: Int,
        timelineLabels: List<String> = emptyList()
    ): Bitmap {
        require(widthPx > 0) { "widthPx doit être > 0, reçu $widthPx" }
        require(heightPx > 0) { "heightPx doit être > 0, reçu $heightPx" }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellWidth = widthPx.toFloat() / CELL_COUNT
        val horizontalInset = (cellWidth * 0.08f).coerceAtLeast(1f)

        // Les deux heatmaps encadrent l'axe temporel, placé exactement au
        // centre du bitmap. Les valeurs sont à l'extérieur des heatmaps.
        val tempValueBaseline = heightPx * 0.18f
        val tempHeatTop = heightPx * 0.24f
        val tempHeatBottom = heightPx * 0.37f
        val timelineY = heightPx * 0.50f
        val timelineLabelBaseline = heightPx * 0.61f
        val precipHeatTop = heightPx * 0.67f
        val precipHeatBottom = heightPx * 0.80f
        val precipValueBaseline = heightPx * 0.96f

        val tempTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            textSize = heightPx * 0.18f
            isSubpixelText = true
        }
        val precipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = precipColorArgb
            textAlign = Paint.Align.CENTER
            textSize = heightPx * 0.14f
            isSubpixelText = true
        }
        val timelineTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0xA8)
            textAlign = Paint.Align.CENTER
            textSize = heightPx * 0.13f
            isSubpixelText = true
        }

        val rect = RectF()
        val radius = min(cellWidth * 0.22f, heightPx * 0.035f)

        for (index in 0 until CELL_COUNT) {
            val centerX = index * cellWidth + cellWidth / 2f
            val left = index * cellWidth + horizontalInset
            val right = (index + 1) * cellWidth - horizontalInset

            temps.getOrNull(index)?.let { temp ->
                canvas.drawText("${temp.roundToInt()}°", centerX, tempValueBaseline, tempTextPaint)
                rect.set(left, tempHeatTop, right, tempHeatBottom)
                shapePaint.color = temperatureHeatmapArgb(temp)
                canvas.drawRoundRect(rect, radius, radius, shapePaint)
            }

            val precip = precips.getOrNull(index)?.coerceIn(0, 100)
            rect.set(left, precipHeatTop, right, precipHeatBottom)
            shapePaint.color = precipitationHeatmapArgb(precip, precipColorArgb, textColorArgb)
            canvas.drawRoundRect(rect, radius, radius, shapePaint)

            if (precip != null) {
                canvas.drawText("$precip%", centerX, precipValueBaseline, precipTextPaint)
            }
        }

        drawTimeline(
            canvas = canvas,
            widthPx = widthPx,
            heightPx = heightPx,
            cellWidth = cellWidth,
            y = timelineY,
            labelBaseline = timelineLabelBaseline,
            textColorArgb = textColorArgb,
            textPaint = timelineTextPaint,
            labels = timelineLabels
        )

        return bitmap
    }

    private fun drawTimeline(
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
        cellWidth: Float,
        y: Float,
        labelBaseline: Float,
        textColorArgb: Int,
        textPaint: Paint,
        labels: List<String>
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0x58)
            strokeWidth = (heightPx * 0.014f).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
        }
        val horizontalPadding = cellWidth / 2f
        canvas.drawLine(horizontalPadding, y, widthPx - horizontalPadding, y, linePaint)

        val tickHeight = heightPx * 0.035f
        for (index in 0 until CELL_COUNT) {
            val x = index * cellWidth + cellWidth / 2f
            canvas.drawLine(x, y - tickHeight, x, y + tickHeight, linePaint)
        }

        val resolved = listOf(
            labels.getOrNull(0) ?: "+0h",
            labels.getOrNull(1) ?: "+6h",
            labels.getOrNull(2) ?: "+11h"
        )
        val edgeInset = (heightPx * 0.03f).coerceAtLeast(2f)

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(resolved[0], edgeInset, labelBaseline, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(resolved[1], widthPx / 2f, labelBaseline, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(resolved[2], widthPx - edgeInset, labelBaseline, textPaint)
    }

    /** Rampe de couleurs froid → chaud commune à l'application et au widget. */
    internal fun temperatureHeatmapArgb(temp: Double): Int {
        val stops = arrayOf(
            -10.0 to 0xFF1976D2.toInt(),
            5.0 to 0xFF4FC3F7.toInt(),
            15.0 to 0xFF81C784.toInt(),
            22.0 to 0xFFFFB74D.toInt(),
            30.0 to 0xFFEF5350.toInt(),
            40.0 to 0xFFB71C1C.toInt()
        )
        if (temp <= stops.first().first) return stops.first().second
        if (temp >= stops.last().first) return stops.last().second

        for (i in 0 until stops.size - 1) {
            val (t1, c1) = stops[i]
            val (t2, c2) = stops[i + 1]
            if (temp in t1..t2) {
                val fraction = ((temp - t1) / (t2 - t1)).toFloat()
                return lerpArgb(c1, c2, fraction)
            }
        }
        return stops.last().second
    }

    /**
     * Intensité bleue proportionnelle au risque de pluie. Une valeur nulle
     * conserve une cellule très discrète pour garder la grille alignée.
     */
    internal fun precipitationHeatmapArgb(
        probability: Int?,
        precipColorArgb: Int,
        textColorArgb: Int
    ): Int {
        if (probability == null) return withAlpha(textColorArgb, 0x12)
        val p = probability.coerceIn(0, 100)
        val alpha = 0x24 + ((0xFF - 0x24) * (p / 100f)).roundToInt()
        return withAlpha(precipColorArgb, alpha)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

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

}
