package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Densité visuelle du mini-forecast selon la largeur réelle du widget. */
internal enum class MiniForecastSizeProfile {
    COMPACT_2X2,
    MEDIUM_3X2,
    EXPANDED_4X2
}

/** Résout le profil depuis la largeur exacte fournie par le launcher. */
internal fun miniForecastProfileForWidth(widthDp: Float): MiniForecastSizeProfile = when {
    widthDp < EXTRA_LARGE_MIN_WIDTH_DP -> MiniForecastSizeProfile.COMPACT_2X2
    widthDp < MEDIUM_MAX_WIDTH_DP -> MiniForecastSizeProfile.MEDIUM_3X2
    else -> MiniForecastSizeProfile.EXPANDED_4X2
}

/**
 * Rendu bitmap de la mini-prévision 12 h utilisée dans les widgets Glance.
 *
 * Les températures et les précipitations ne sont plus représentées par des
 * cellules de hauteur fixe. Chaque heure devient une barre verticale :
 *
 *   valeur température
 *   barres température (hauteur relative sur les 12 h)
 *   ───────── axe horaire ─────────
 *   libellés horaires
 *   barres pluie (quantité + probabilité)
 *   probabilité de pluie
 *
 * La couleur reste une heatmap : température pour la partie haute, opacité
 * bleue pilotée par le risque et la quantité de pluie pour la partie basse.
 */
internal object WidgetMiniForecastRenderer {

    private const val CELL_COUNT = 12
    private const val MIN_VISIBLE_BAR_FRACTION = 0.14f
    private const val MIN_TEMPERATURE_RANGE_C = 6.0
    private const val HEAVY_HOURLY_RAIN_MM = 4.0

    fun render(
        widthPx: Int,
        heightPx: Int,
        temps: List<Double?>,
        precipProbabilities: List<Int?>,
        precipAmountsMm: List<Double?> = emptyList(),
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

        val tempValueBaseline = heightPx * 0.15f
        val tempBarAreaTop = heightPx * 0.19f
        val tempBarBottom = heightPx * 0.40f
        val timelineY = heightPx * 0.48f
        val timelineLabelBaseline = heightPx * 0.59f
        val precipBarTop = heightPx * 0.64f
        val precipBarAreaBottom = heightPx * 0.86f
        val precipValueBaseline = heightPx * 0.98f

        val tempTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            textSize = heightPx * metrics.tempTextFraction
            isSubpixelText = true
        }
        val precipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
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

        val temperatureFractions = temperatureBarFractions(temps)
        val rect = RectF()
        val radius = min(cellWidth * 0.24f, heightPx * 0.035f)
        val tempAreaHeight = tempBarBottom - tempBarAreaTop
        val rainAreaHeight = precipBarAreaBottom - precipBarTop

        for (index in 0 until CELL_COUNT) {
            val centerX = index * cellWidth + cellWidth / 2f
            val left = index * cellWidth + horizontalInset
            val right = (index + 1) * cellWidth - horizontalInset

            temps.getOrNull(index)?.let { temp ->
                if (shouldDrawValue(index, metrics)) {
                    canvas.drawText("${temp.roundToInt()}°", centerX, tempValueBaseline, tempTextPaint)
                }
                val fraction = temperatureFractions.getOrNull(index) ?: MIN_VISIBLE_BAR_FRACTION
                val top = tempBarBottom - tempAreaHeight * fraction
                rect.set(left, top, right, tempBarBottom)
                shapePaint.color = temperatureHeatmapArgb(temp)
                canvas.drawRoundRect(rect, radius, radius, shapePaint)
            }

            val probability = precipProbabilities.getOrNull(index)?.coerceIn(0, 100)
            val amountMm = precipAmountsMm.getOrNull(index)?.coerceAtLeast(0.0)
            val rainFraction = precipitationBarFraction(amountMm, probability)
            if (rainFraction != null && rainFraction > 0f) {
                val bottom = precipBarTop + rainAreaHeight * rainFraction
                rect.set(left, precipBarTop, right, bottom)
                shapePaint.color = precipitationHeatmapArgb(
                    probability = probability,
                    precipColorArgb = precipColorArgb,
                    textColorArgb = textColorArgb,
                    amountMm = amountMm
                )
                canvas.drawRoundRect(rect, radius, radius, shapePaint)
            }

            if (probability != null && shouldDrawValue(index, metrics)) {
                canvas.drawText("$probability%", centerX, precipValueBaseline, precipTextPaint)
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

    /** Hauteur relative des barres température, normalisée sur la fenêtre 12 h. */
    internal fun temperatureBarFractions(temps: List<Double?>): List<Float?> {
        val values = temps.filterNotNull()
        if (values.isEmpty()) return List(temps.size) { null }

        val rawMin = values.minOrNull() ?: return List(temps.size) { null }
        val rawMax = values.maxOrNull() ?: return List(temps.size) { null }
        val center = (rawMin + rawMax) / 2.0
        val span = max(rawMax - rawMin, MIN_TEMPERATURE_RANGE_C)
        val minValue = center - span / 2.0

        return temps.map { temp ->
            temp?.let {
                val normalized = ((it - minValue) / span).toFloat().coerceIn(0f, 1f)
                MIN_VISIBLE_BAR_FRACTION + normalized * (1f - MIN_VISIBLE_BAR_FRACTION)
            }
        }
    }

    /**
     * Hauteur pluie combinant la quantité horaire et la probabilité.
     * La racine carrée rend les faibles cumuls visibles sans laisser un épisode
     * intense écraser toutes les autres barres.
     */
    internal fun precipitationBarFraction(amountMm: Double?, probability: Int?): Float? {
        if (amountMm == null && probability == null) return null
        val amountScore = amountMm
            ?.coerceAtLeast(0.0)
            ?.div(HEAVY_HOURLY_RAIN_MM)
            ?.coerceIn(0.0, 1.0)
            ?.let(::sqrt)
            ?.toFloat()
            ?: 0f
        val probabilityScore = probability?.coerceIn(0, 100)?.div(100f) ?: 0f
        val combined = amountScore * 0.65f + probabilityScore * 0.35f
        if (combined <= 0f) return 0f
        return (MIN_VISIBLE_BAR_FRACTION + combined * (1f - MIN_VISIBLE_BAR_FRACTION))
            .coerceIn(MIN_VISIBLE_BAR_FRACTION, 1f)
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
            strokeWidth = (heightPx * 0.010f).coerceAtLeast(1f)
            strokeCap = Paint.Cap.ROUND
        }
        val horizontalPadding = cellWidth / 2f
        canvas.drawLine(horizontalPadding, y, widthPx - horizontalPadding, y, linePaint)

        val tickHeight = heightPx * 0.018f
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
            tempTextFraction = 0.095f,
            precipTextFraction = 0.078f,
            timelineTextFraction = 0.078f,
            cellInsetFraction = 0.16f,
            showCenterTimelineLabel = false,
            includeLastValue = true
        )

        MiniForecastSizeProfile.MEDIUM_3X2 -> RenderMetrics(
            valueStep = 2,
            tempTextFraction = 0.105f,
            precipTextFraction = 0.085f,
            timelineTextFraction = 0.082f,
            cellInsetFraction = 0.13f,
            showCenterTimelineLabel = true,
            includeLastValue = false
        )

        MiniForecastSizeProfile.EXPANDED_4X2 -> RenderMetrics(
            valueStep = 1,
            tempTextFraction = 0.12f,
            precipTextFraction = 0.095f,
            timelineTextFraction = 0.09f,
            cellInsetFraction = 0.10f,
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

    /** Intensité bleue proportionnelle au risque et au cumul de pluie. */
    internal fun precipitationHeatmapArgb(
        probability: Int?,
        precipColorArgb: Int,
        textColorArgb: Int,
        amountMm: Double? = null
    ): Int {
        if (probability == null && amountMm == null) return withAlpha(textColorArgb, 0x12)
        val probabilityScore = probability?.coerceIn(0, 100)?.div(100f) ?: 0f
        val amountScore = amountMm
            ?.coerceAtLeast(0.0)
            ?.div(HEAVY_HOURLY_RAIN_MM)
            ?.coerceIn(0.0, 1.0)
            ?.let(::sqrt)
            ?.toFloat()
            ?: 0f
        val intensity = max(probabilityScore * 0.75f, amountScore)
        val alpha = 0x30 + ((0xFF - 0x30) * intensity).roundToInt()
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
