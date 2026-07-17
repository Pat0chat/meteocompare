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
 * Les douze échéances sont réparties sur deux lignes de six cellules. Cette
 * grille double la largeur utile de chaque heure par rapport à l'ancienne
 * bande unique et exploite la hauteur des formats ×2 :
 *
 *   heure
 *   température forte
 *   accent thermique coloré
 *   risque de pluie + indicateur d'intensité
 *
 * La première échéance est légèrement mise en avant, sans ajouter de bordure
 * lourde. Les couleurs restent les mêmes que dans l'application.
 */
internal object WidgetMiniForecastRenderer {

    private const val CELL_COUNT = 12
    private const val COLUMNS_PER_ROW = 6
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

        val metrics = gridMetricsFor(profile)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val temperatureFractions = temperatureBarFractions(temps)

        val outerPadding = (heightPx * 0.025f).coerceAtLeast(2f)
        val rowGap = (heightPx * 0.045f).coerceAtLeast(3f)
        val usableHeight = heightPx - outerPadding * 2f - rowGap
        val rowHeight = usableHeight / 2f
        val columnWidth = (widthPx - outerPadding * 2f) / COLUMNS_PER_ROW
        val cellGap = (columnWidth * metrics.cellGapFraction).coerceAtLeast(1f)
        val cellRadius = min(columnWidth * 0.20f, rowHeight * 0.12f)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0xA8)
            textAlign = Paint.Align.CENTER
            textSize = rowHeight * metrics.timeTextFraction
            isSubpixelText = true
        }
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColorArgb
            textAlign = Paint.Align.CENTER
            textSize = rowHeight * metrics.tempTextFraction
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            isSubpixelText = true
        }
        val precipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0xC8)
            textAlign = Paint.Align.CENTER
            textSize = rowHeight * metrics.precipTextFraction
            isSubpixelText = true
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0x20)
            strokeWidth = (heightPx * 0.006f).coerceAtLeast(1f)
        }

        // Séparation très discrète entre les deux tranches de six heures.
        val dividerY = outerPadding + rowHeight + rowGap / 2f
        canvas.drawLine(outerPadding, dividerY, widthPx - outerPadding, dividerY, dividerPaint)

        val rect = RectF()
        for (index in 0 until CELL_COUNT) {
            val row = index / COLUMNS_PER_ROW
            val column = index % COLUMNS_PER_ROW
            val rowTop = outerPadding + row * (rowHeight + rowGap)
            val left = outerPadding + column * columnWidth + cellGap / 2f
            val right = outerPadding + (column + 1) * columnWidth - cellGap / 2f
            val bottom = rowTop + rowHeight
            val centerX = (left + right) / 2f

            // Carte horaire sobre ; la première échéance est légèrement mise en avant.
            /*rect.set(left, rowTop, right, bottom)
            shapePaint.color = withAlpha(textColorArgb, if (index == 0) 0x18 else 0x0C)
            canvas.drawRoundRect(rect, cellRadius, cellRadius, shapePaint)*/

            val label = timelineLabels.getOrNull(index) ?: "+${index}h"
            canvas.drawText(label, centerX, rowTop + rowHeight * 0.20f, timePaint)

            val temp = temps.getOrNull(index)
            if (temp != null) {
                canvas.drawText(
                    "${temp.roundToInt()}°",
                    centerX,
                    rowTop + rowHeight * 0.51f,
                    tempPaint
                )

                // Accent thermique horizontal : couleur = température,
                // longueur = position relative dans la fenêtre 12 h.
                val fraction = temperatureFractions.getOrNull(index)
                    ?: MIN_VISIBLE_BAR_FRACTION
                val accentMaxWidth = (right - left) * 0.70f
                val accentWidth = accentMaxWidth * (0.45f + 0.55f * fraction)
                val accentHeight = (rowHeight * 0.055f).coerceAtLeast(2f)
                rect.set(
                    centerX - accentWidth / 2f,
                    rowTop + rowHeight * 0.59f,
                    centerX + accentWidth / 2f,
                    rowTop + rowHeight * 0.59f + accentHeight
                )
                shapePaint.color = temperatureHeatmapArgb(temp)
                canvas.drawRoundRect(rect, accentHeight / 2f, accentHeight / 2f, shapePaint)
            }

            val probability = precipProbabilities.getOrNull(index)?.coerceIn(0, 100)
            val amountMm = precipAmountsMm.getOrNull(index)?.coerceAtLeast(0.0)
            if (probability != null) {
                val dotRadius = (rowHeight * 0.035f).coerceAtLeast(1.5f)
                val text = "$probability%"
                val textWidth = precipPaint.measureText(text)
                val contentWidth = dotRadius * 2.6f + textWidth
                val dotX = centerX - contentWidth / 2f + dotRadius
                val precipBaseline = rowTop + rowHeight * 0.84f
                shapePaint.color = precipitationHeatmapArgb(
                    probability = probability,
                    precipColorArgb = precipColorArgb,
                    textColorArgb = textColorArgb,
                    amountMm = amountMm
                )
                canvas.drawCircle(dotX, precipBaseline - dotRadius, dotRadius, shapePaint)
                precipPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(text, dotX + dotRadius * 1.6f, precipBaseline, precipPaint)
                precipPaint.textAlign = Paint.Align.CENTER
            }

            // Trait pluie inférieur : intensité combinée quantité/probabilité.
            val rainFraction = precipitationBarFraction(amountMm, probability)
            if (rainFraction != null && rainFraction > 0f) {
                val trackWidth = (right - left) * 0.72f
                val rainWidth = trackWidth * rainFraction
                val rainHeight = (rowHeight * 0.035f).coerceAtLeast(1.5f)
                rect.set(
                    centerX - rainWidth / 2f,
                    bottom - rowHeight * 0.08f,
                    centerX + rainWidth / 2f,
                    bottom - rowHeight * 0.08f + rainHeight
                )
                shapePaint.color = precipitationHeatmapArgb(
                    probability = probability,
                    precipColorArgb = precipColorArgb,
                    textColorArgb = textColorArgb,
                    amountMm = amountMm
                )
                canvas.drawRoundRect(rect, rainHeight / 2f, rainHeight / 2f, shapePaint)
            }
        }

        return bitmap
    }

    /** Les 12 valeurs sont lisibles grâce à la grille 2 × 6. */
    internal fun visibleValueIndices(profile: MiniForecastSizeProfile): List<Int> =
        (0 until CELL_COUNT).toList()

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

    private fun gridMetricsFor(profile: MiniForecastSizeProfile): GridMetrics = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> GridMetrics(
            timeTextFraction = 0.125f,
            tempTextFraction = 0.245f,
            precipTextFraction = 0.115f,
            cellGapFraction = 0.10f
        )
        MiniForecastSizeProfile.MEDIUM_3X2 -> GridMetrics(
            timeTextFraction = 0.13f,
            tempTextFraction = 0.255f,
            precipTextFraction = 0.12f,
            cellGapFraction = 0.12f
        )
        MiniForecastSizeProfile.EXPANDED_4X2 -> GridMetrics(
            timeTextFraction = 0.135f,
            tempTextFraction = 0.27f,
            precipTextFraction = 0.125f,
            cellGapFraction = 0.14f
        )
    }

    private data class GridMetrics(
        val timeTextFraction: Float,
        val tempTextFraction: Float,
        val precipTextFraction: Float,
        val cellGapFraction: Float
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
