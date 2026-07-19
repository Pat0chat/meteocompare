package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
 * Calcule la hauteur réellement utile du bitmap à partir de la hauteur du widget.
 *
 * L'ancien rendu plafonnait la mini-prévision autour de 90–100 dp : sur un widget
 * redimensionné en hauteur, le conteneur grandissait mais pas son contenu. Ici on
 * retire le budget du bandeau courant, des paddings du widget et de l'espacement,
 * puis on laisse la heatmap grandir dans des bornes adaptées à la largeur.
 */
internal fun miniForecastChartHeightDp(
    widgetHeightDp: Float,
    headerHeightDp: Float,
    sectionGapDp: Float,
    profile: MiniForecastSizeProfile
): Int {
    val widgetPadding = forecastContainerVerticalPaddingDp(widgetHeightDp) * 2f
    val chartInnerPadding = if (widgetHeightDp < 175f) 4f else 6f
    val available = widgetHeightDp - widgetPadding - headerHeightDp - sectionGapDp - chartInnerPadding

    val minimum = when {
        widgetHeightDp < 145f -> 54f
        widgetHeightDp < 175f -> 66f
        else -> 78f
    }
    val maximum = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> 160f
        MiniForecastSizeProfile.MEDIUM_3X2 -> 190f
        MiniForecastSizeProfile.EXPANDED_4X2 -> 220f
    }

    return available.coerceIn(minimum, maximum).roundToInt()
}

/**
 * Rendu bitmap de la mini-prévision 12 h utilisée dans les widgets Glance.
 *
 * Les douze échéances sont réparties sur deux lignes de six cellules. Chaque
 * cellule est désormais une vraie heatmap en deux zones :
 *
 *   - zone haute colorée selon la température absolue ;
 *   - zone basse bleue dont l'opacité combine probabilité et cumul de pluie.
 *
 * Les valeurs restent au premier plan avec une couleur de texte calculée pour
 * conserver le contraste. Le bitmap se redimensionne avec la hauteur du widget,
 * au lieu de rester une petite bande centrée dans un grand conteneur.
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

        val outerPadding = (heightPx * 0.022f).coerceAtLeast(2f)
        val rowGap = (heightPx * 0.042f).coerceAtLeast(3f)
        val usableHeight = heightPx - outerPadding * 2f - rowGap
        val rowHeight = usableHeight / 2f
        val columnWidth = (widthPx - outerPadding * 2f) / COLUMNS_PER_ROW
        val cellGap = (columnWidth * metrics.cellGapFraction).coerceAtLeast(1f)
        val cellRadius = min(columnWidth * 0.18f, rowHeight * 0.10f)

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = min(
                rowHeight * metrics.timeTextFraction,
                columnWidth * metrics.timeWidthLimitFraction
            ).coerceAtLeast(7f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isSubpixelText = true
        }
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = min(
                rowHeight * metrics.tempTextFraction,
                columnWidth * metrics.tempWidthLimitFraction
            ).coerceAtLeast(13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isSubpixelText = true
        }
        val precipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = min(
                rowHeight * metrics.precipTextFraction,
                columnWidth * metrics.precipWidthLimitFraction
            ).coerceAtLeast(7f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isSubpixelText = true
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(textColorArgb, 0x24)
            strokeWidth = (heightPx * 0.006f).coerceAtLeast(1f)
        }

        // Séparation discrète entre les deux tranches de six heures.
        val dividerY = outerPadding + rowHeight + rowGap / 2f
        canvas.drawLine(outerPadding, dividerY, widthPx - outerPadding, dividerY, dividerPaint)

        val rect = RectF()
        val cellRect = RectF()
        for (index in 0 until CELL_COUNT) {
            val row = index / COLUMNS_PER_ROW
            val column = index % COLUMNS_PER_ROW
            val rowTop = outerPadding + row * (rowHeight + rowGap)
            val left = outerPadding + column * columnWidth + cellGap / 2f
            val right = outerPadding + (column + 1) * columnWidth - cellGap / 2f
            val bottom = rowTop + rowHeight
            val centerX = (left + right) / 2f

            cellRect.set(left, rowTop, right, bottom)
            shapePaint.style = Paint.Style.FILL
            shapePaint.color = withAlpha(textColorArgb, if (index == 0) 0x18 else 0x0C)
            canvas.drawRoundRect(cellRect, cellRadius, cellRadius, shapePaint)

            // ─── Zone température : grande tuile colorée ─────────────────
            val tempTop = rowTop + rowHeight * 0.045f
            val tempBottom = rowTop + rowHeight * 0.645f
            val temp = temps.getOrNull(index)
            val tempColor = temp?.let(::temperatureHeatmapArgb)
                ?: withAlpha(textColorArgb, 0x18)
            val tempTileColor = if (temp == null) tempColor else withAlpha(tempColor, 0xE8)

            rect.set(left, tempTop, right, tempBottom)
            shapePaint.color = tempTileColor
            canvas.drawRoundRect(rect, cellRadius, cellRadius, shapePaint)

            val tempContentColor = if (temp == null) {
                withAlpha(textColorArgb, 0xB0)
            } else {
                heatmapContentColorArgb(tempColor)
            }
            val label = timelineLabels.getOrNull(index) ?: "+${index}h"
            timePaint.color = withAlpha(tempContentColor, 0xD8)
            tempPaint.color = tempContentColor

            val tempZoneHeight = tempBottom - tempTop
            canvas.drawText(
                label,
                centerX,
                tempTop + tempZoneHeight * 0.29f,
                timePaint
            )
            canvas.drawText(
                temp?.let { "${it.roundToInt()}°" } ?: "—",
                centerX,
                tempTop + tempZoneHeight * 0.78f,
                tempPaint
            )

            // Marqueur thermique relatif très discret : il aide à comparer les
            // heures proches sans concurrencer la couleur absolue de la heatmap.
            val tempFraction = temperatureFractions.getOrNull(index)
            if (tempFraction != null) {
                val levelWidth = (right - left) * tempFraction.coerceIn(0f, 1f)
                val levelHeight = (rowHeight * 0.018f).coerceAtLeast(1.5f)
                rect.set(
                    left,
                    tempBottom - levelHeight,
                    left + levelWidth,
                    tempBottom
                )
                shapePaint.color = withAlpha(tempContentColor, 0xA0)
                canvas.drawRoundRect(rect, levelHeight / 2f, levelHeight / 2f, shapePaint)
            }

            // ─── Zone précipitations : bande heatmap pleine largeur ──────
            val precipTop = rowTop + rowHeight * 0.705f
            val precipBottom = rowTop + rowHeight * 0.955f
            val probability = precipProbabilities.getOrNull(index)?.coerceIn(0, 100)
            val amountMm = precipAmountsMm.getOrNull(index)?.coerceAtLeast(0.0)
            val precipTileColor = precipitationHeatmapArgb(
                probability = probability,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                amountMm = amountMm
            )

            rect.set(left, precipTop, right, precipBottom)
            shapePaint.color = precipTileColor
            canvas.drawRoundRect(rect, cellRadius * 0.72f, cellRadius * 0.72f, shapePaint)

            val precipAlpha = (precipTileColor ushr 24) and 0xFF
            precipPaint.color = if (precipAlpha >= 0x78) {
                heatmapContentColorArgb(precipColorArgb)
            } else {
                withAlpha(textColorArgb, 0xD8)
            }
            val precipText = probability?.let { "$it%" } ?: "—"
            val precipFontMetrics = precipPaint.fontMetrics
            val precipBaseline = (precipTop + precipBottom) / 2f -
                (precipFontMetrics.ascent + precipFontMetrics.descent) / 2f
            canvas.drawText(precipText, centerX, precipBaseline, precipPaint)

            // Barre de saturation : probabilité + quantité. Elle reste secondaire
            // par rapport à la bande bleue, mais rend les cumuls forts visibles.
            val rainFraction = precipitationBarFraction(amountMm, probability)
            if (rainFraction != null && rainFraction > 0f) {
                val rainHeight = (rowHeight * 0.022f).coerceAtLeast(1.5f)
                val rainWidth = (right - left) * rainFraction
                rect.set(
                    left,
                    precipBottom - rainHeight,
                    left + rainWidth,
                    precipBottom
                )
                shapePaint.color = withAlpha(precipColorArgb, 0xF0)
                canvas.drawRoundRect(rect, rainHeight / 2f, rainHeight / 2f, shapePaint)
            }

            // Première échéance = maintenant : contour léger, visible sur les
            // deux couleurs sans rajouter un badge ou du texte.
            if (index == 0) {
                shapePaint.style = Paint.Style.STROKE
                shapePaint.strokeWidth = (min(columnWidth, rowHeight) * 0.025f)
                    .coerceAtLeast(1.5f)
                shapePaint.color = withAlpha(textColorArgb, 0x88)
                canvas.drawRoundRect(cellRect, cellRadius, cellRadius, shapePaint)
                shapePaint.style = Paint.Style.FILL
            }
        }

        return bitmap
    }

    /** Les 12 valeurs sont lisibles grâce à la grille 2 × 6. */
    internal fun visibleValueIndices(profile: MiniForecastSizeProfile): List<Int> =
        (0 until CELL_COUNT).toList()

    /** Longueur relative du marqueur thermique, normalisée sur la fenêtre 12 h. */
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
     * Intensité pluie combinant la quantité horaire et la probabilité.
     * La racine carrée rend les faibles cumuls visibles sans laisser un épisode
     * intense écraser toutes les autres cases.
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
            timeTextFraction = 0.105f,
            tempTextFraction = 0.225f,
            precipTextFraction = 0.105f,
            cellGapFraction = 0.08f,
            timeWidthLimitFraction = 0.24f,
            tempWidthLimitFraction = 0.43f,
            precipWidthLimitFraction = 0.22f
        )
        MiniForecastSizeProfile.MEDIUM_3X2 -> GridMetrics(
            timeTextFraction = 0.112f,
            tempTextFraction = 0.235f,
            precipTextFraction = 0.11f,
            cellGapFraction = 0.10f,
            timeWidthLimitFraction = 0.25f,
            tempWidthLimitFraction = 0.44f,
            precipWidthLimitFraction = 0.23f
        )
        MiniForecastSizeProfile.EXPANDED_4X2 -> GridMetrics(
            timeTextFraction = 0.12f,
            tempTextFraction = 0.25f,
            precipTextFraction = 0.115f,
            cellGapFraction = 0.12f,
            timeWidthLimitFraction = 0.26f,
            tempWidthLimitFraction = 0.46f,
            precipWidthLimitFraction = 0.24f
        )
    }

    private data class GridMetrics(
        val timeTextFraction: Float,
        val tempTextFraction: Float,
        val precipTextFraction: Float,
        val cellGapFraction: Float,
        val timeWidthLimitFraction: Float,
        val tempWidthLimitFraction: Float,
        val precipWidthLimitFraction: Float
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

    /** Choisit noir ou blanc pour garantir la lisibilité sur la heatmap. */
    internal fun heatmapContentColorArgb(backgroundArgb: Int): Int {
        val r = (backgroundArgb shr 16) and 0xFF
        val g = (backgroundArgb shr 8) and 0xFF
        val b = backgroundArgb and 0xFF
        val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        return if (luminance >= 0.62f) 0xFF17202A.toInt() else 0xFFFFFFFF.toInt()
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
