package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/** Densité visuelle du mini-forecast selon la largeur réelle du widget. */
internal enum class MiniForecastSizeProfile {
    COMPACT_2X2,
    MEDIUM_3X2,
    EXPANDED_4X2
}

/**
 * Résout le profil depuis la largeur exacte fournie par le launcher.
 *
 * Les seuils suivent ceux des layouts principaux : sous 220 dp on est dans
 * le 2×2 compact, sous 320 dp dans le 3×2, puis dans le 4×2/5×2.
 */
internal fun miniForecastProfileForWidth(widthDp: Float): MiniForecastSizeProfile = when {
    widthDp < EXTRA_LARGE_MIN_WIDTH_DP -> MiniForecastSizeProfile.COMPACT_2X2
    widthDp < MEDIUM_MAX_WIDTH_DP -> MiniForecastSizeProfile.MEDIUM_3X2
    else -> MiniForecastSizeProfile.EXPANDED_4X2
}

/**
 * Rendu bitmap de la mini-prévision 12 h utilisée dans les widgets Glance.
 *
 * L'ordre vertical est volontairement symétrique autour de l'axe temporel :
 *
 *   température
 *   heatmap de température
 *   ───────── axe horaire ─────────
 *   libellés horaires
 *   heatmap de précipitations
 *   probabilité de précipitations
 *
 * Les libellés horaires disposent désormais de leur propre bande sous la
 * ligne. Leur glyphe ne peut donc plus recouvrir l'axe temporel.
 */
internal object WidgetMiniForecastRenderer {

    private const val CELL_COUNT = 12

    /**
     * Rend les 12 colonnes de prévision en adaptant la densité des textes à
     * la taille du widget. Les heatmaps conservent toujours les 12 cellules :
     * seul le nombre de valeurs imprimées est réduit sur les petites tailles.
     */
    fun render(
        widthPx: Int,
        heightPx: Int,
        temps: List<Double?>,
        precips: List<Int?>,
        precipColorArgb: Int,
        textColorArgb: Int,
        timelineLabels: List<String> = emptyList(),
        profile: MiniForecastSizeProfile = MiniForecastSizeProfile.EXPANDED_4X2
    ): Bitmap {
        require(widthPx > 0) { "widthPx doit être > 0, reçu $widthPx" }
        require(heightPx > 0) { "heightPx doit être > 0, reçu $heightPx" }

        val metrics = metricsFor(profile)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellWidth = widthPx.toFloat() / CELL_COUNT
        val horizontalInset = (cellWidth * metrics.cellInsetFraction).coerceAtLeast(1f)

        val tempValueBaseline = heightPx * 0.17f
        val tempHeatTop = heightPx * 0.23f
        val tempHeatBottom = heightPx * 0.35f
        val timelineY = heightPx * 0.46f
        val timelineLabelBaseline = heightPx * 0.62f
        val precipHeatTop = heightPx * 0.69f
        val precipHeatBottom = heightPx * 0.81f
        val precipValueBaseline = heightPx * 0.96f

        val tempTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            textSize = heightPx * metrics.tempTextFraction
            isSubpixelText = true
        }
        val precipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = precipColorArgb
            textAlign = Paint.Align.CENTER
            textSize = heightPx * metrics.precipTextFraction
            isSubpixelText = true
        }
        val timelineTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0xB8)
            textAlign = Paint.Align.CENTER
            textSize = heightPx * metrics.timelineTextFraction
            isSubpixelText = true
        }

        val rect = RectF()
        val radius = min(cellWidth * 0.22f, heightPx * 0.032f)

        for (index in 0 until CELL_COUNT) {
            val centerX = index * cellWidth + cellWidth / 2f
            val left = index * cellWidth + horizontalInset
            val right = (index + 1) * cellWidth - horizontalInset

            temps.getOrNull(index)?.let { temp ->
                if (shouldDrawValue(index, metrics)) {
                    canvas.drawText("${temp.roundToInt()}°", centerX, tempValueBaseline, tempTextPaint)
                }
                rect.set(left, tempHeatTop, right, tempHeatBottom)
                shapePaint.color = temperatureHeatmapArgb(temp)
                canvas.drawRoundRect(rect, radius, radius, shapePaint)
            }

            val precip = precips.getOrNull(index)?.coerceIn(0, 100)
            rect.set(left, precipHeatTop, right, precipHeatBottom)
            shapePaint.color = precipitationHeatmapArgb(precip, precipColorArgb, textColorArgb)
            canvas.drawRoundRect(rect, radius, radius, shapePaint)

            if (precip != null && shouldDrawValue(index, metrics)) {
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
            labels = timelineLabels,
            showCenterLabel = metrics.showCenterTimelineLabel
        )

        return bitmap
    }

    internal fun visibleValueIndices(profile: MiniForecastSizeProfile): List<Int> {
        val metrics = metricsFor(profile)
        return (0 until CELL_COUNT).filter { shouldDrawValue(it, metrics) }
    }

    private fun shouldDrawValue(index: Int, metrics: RenderMetrics): Boolean =
        index % metrics.valueStep == 0 || (metrics.includeLastValue && index == CELL_COUNT - 1)

    private fun drawTimeline(
        canvas: Canvas,
        widthPx: Int,
        heightPx: Int,
        cellWidth: Float,
        y: Float,
        labelBaseline: Float,
        textColorArgb: Int,
        textPaint: Paint,
        labels: List<String>,
        showCenterLabel: Boolean
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0x68)
            strokeWidth = (heightPx * 0.012f).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
        }
        val horizontalPadding = cellWidth / 2f
        canvas.drawLine(horizontalPadding, y, widthPx - horizontalPadding, y, linePaint)

        val tickHeight = heightPx * 0.025f
        for (index in 0 until CELL_COUNT) {
            val x = index * cellWidth + cellWidth / 2f
            canvas.drawLine(x, y - tickHeight, x, y + tickHeight, linePaint)
        }

        val resolved = listOf(
            labels.getOrNull(0) ?: "+0h",
            labels.getOrNull(1) ?: "+6h",
            labels.getOrNull(2) ?: "+11h"
        )
        val edgeInset = (heightPx * 0.035f).coerceAtLeast(2f)

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(resolved[0], edgeInset, labelBaseline, textPaint)
        if (showCenterLabel) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(resolved[1], widthPx / 2f, labelBaseline, textPaint)
        }
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(resolved[2], widthPx - edgeInset, labelBaseline, textPaint)
    }

    private fun metricsFor(profile: MiniForecastSizeProfile): RenderMetrics = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> RenderMetrics(
            valueStep = 3,
            tempTextFraction = 0.105f,
            precipTextFraction = 0.085f,
            timelineTextFraction = 0.082f,
            cellInsetFraction = 0.12f,
            showCenterTimelineLabel = false,
            includeLastValue = true
        )

        MiniForecastSizeProfile.MEDIUM_3X2 -> RenderMetrics(
            valueStep = 2,
            tempTextFraction = 0.12f,
            precipTextFraction = 0.095f,
            timelineTextFraction = 0.09f,
            cellInsetFraction = 0.10f,
            showCenterTimelineLabel = true,
            includeLastValue = false
        )

        MiniForecastSizeProfile.EXPANDED_4X2 -> RenderMetrics(
            valueStep = 1,
            tempTextFraction = 0.14f,
            precipTextFraction = 0.11f,
            timelineTextFraction = 0.10f,
            cellInsetFraction = 0.08f,
            showCenterTimelineLabel = true,
            includeLastValue = false
        )
    }

    private data class RenderMetrics(
        val valueStep: Int,
        val tempTextFraction: Float,
        val precipTextFraction: Float,
        val timelineTextFraction: Float,
        val cellInsetFraction: Float,
        val showCenterTimelineLabel: Boolean,
        val includeLastValue: Boolean
    )

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
