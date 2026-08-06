package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Variante plus graphique du widget 12 h : deux bandes heatmap synchronisées
 * avec un tracé de tendance température et des repères horaires allégés.
 */
internal object WidgetHeatmapForecastRenderer {

    private const val CELL_COUNT = 12

    fun render(
        widthPx: Int,
        heightPx: Int,
        temps: List<Double?>,
        precipProbabilities: List<Int?>,
        precipAmountsMm: List<Double?> = emptyList(),
        conditions: List<WeatherCondition?> = emptyList(),
        precipColorArgb: Int,
        textColorArgb: Int,
        timelineLabels: List<String> = emptyList(),
        profile: MiniForecastSizeProfile = MiniForecastSizeProfile.EXPANDED_4X2
    ): Bitmap {
        require(widthPx > 0) { "widthPx doit être > 0, reçu $widthPx" }
        require(heightPx > 0) { "heightPx doit être > 0, reçu $heightPx" }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outerPadding = (heightPx * 0.02f).coerceAtLeast(1f)
        val rowGap = (heightPx * 0.02f).coerceAtLeast(1.5f)
        val axisGap = (heightPx * 0.025f).coerceAtLeast(2f)
        val labelAreaHeight = (heightPx * 0.12f).coerceAtLeast(8f)
        val usableWidth = widthPx - outerPadding * 2f
        val columnWidth = usableWidth / CELL_COUNT
        val tempBandHeight = ((heightPx - outerPadding * 2f - rowGap - axisGap - labelAreaHeight) * 0.64f).coerceAtLeast(heightPx * 0.55f)
        val precipBandHeight = (heightPx - outerPadding * 2f - rowGap - axisGap - labelAreaHeight - tempBandHeight).coerceAtLeast(heightPx * 0.05f)
        val tempTop = outerPadding
        val tempBottom = tempTop + tempBandHeight
        val precipTop = tempBottom + rowGap
        val precipBottom = precipTop + precipBandHeight
        val axisY = precipBottom + axisGap

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(textColorArgb, 0x10)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 12f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 13f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 16f
            }.coerceAtMost(columnWidth * 0.55f)
            color = withAlpha(textColorArgb, 0xD8)
            isSubpixelText = true
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 12f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 13f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 16f
            }.coerceAtMost(columnWidth * 0.65f)
            isSubpixelText = true
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 2.0f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 2.6f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 3.2f
            }
            color = withAlpha(textColorArgb, 0xE0)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val cardRect = RectF()
        val tempValues = temps.filterNotNull()
        val minTemp = tempValues.minOrNull() ?: 0.0
        val maxTemp = tempValues.maxOrNull() ?: 1.0
        val padded = paddedTemperatureRange(minTemp, maxTemp)
        val anchorIndices = anchorIndices(profile)
        val slotInset = (columnWidth * 0.07f).coerceAtLeast(1f)
        val plotPoints = mutableListOf<Pair<Float, Float>>()
        val curveTopRatio = temperatureCurveTopRatio(profile)
        val temperatureLabelBaseline = tempBottom - tempBandHeight * temperatureLabelBottomInsetRatio(profile)
        val curveBottomRatio = temperatureCurveBottomRatio(
            bandHeightPx = tempBandHeight,
            labelBaselineRelativePx = temperatureLabelBaseline - tempTop,
            labelAscentPx = valuePaint.fontMetrics.ascent,
            maxPointRadiusPx = 4.6f,
            usableTopRatio = curveTopRatio
        )

        for (index in 0 until CELL_COUNT) {
            val left = outerPadding + index * columnWidth + slotInset
            val right = outerPadding + (index + 1) * columnWidth - slotInset
            val centerX = (left + right) / 2f
            val isCurrent = index == 0
            val columnRect = RectF(left, tempTop + 4f, right, tempBottom - 4f)
            val temp = temps.getOrNull(index)
            val tempColor = temp?.let(WidgetMiniForecastRenderer::temperatureHeatmapArgb) ?: withAlpha(textColorArgb, 0x14)
            val tileColor = if (temp == null) tempColor else withAlpha(tempColor, if (isCurrent) 0xEC else 0xD8)
            panelPaint.color = tileColor
            canvas.drawRoundRect(columnRect, 10f, 10f, panelPaint)

            if (isCurrent) {
                panelPaint.style = Paint.Style.STROKE
                panelPaint.color = withAlpha(textColorArgb, 0x5E)
                canvas.drawRoundRect(columnRect, 10f, 10f, panelPaint)
                panelPaint.style = Paint.Style.FILL
            }

            val tempY = temp?.let {
                normalizedTemperatureY(
                    temperature = it,
                    minTemp = padded.first,
                    maxTemp = padded.second,
                    top = tempTop,
                    bottom = tempBottom,
                    usableTopRatio = curveTopRatio,
                    usableBottomRatio = curveBottomRatio
                )
            }
                ?: ((tempTop + tempBottom) / 2f)
            plotPoints += centerX to tempY

            if (index in anchorIndices) {
                val contentColor = if (temp == null) withAlpha(textColorArgb, 0xD8)
                else WidgetMiniForecastRenderer.heatmapContentColorArgb(tempColor)
                drawConditionBadge(
                    canvas = canvas,
                    condition = conditions.getOrNull(index),
                    centerX = centerX,
                    top = tempTop + tempBandHeight * 0.05f,
                    bandHeight = tempBandHeight,
                    profile = profile
                )
                valuePaint.color = contentColor
                val tempLabel = temp?.let { "${it.roundToInt()}°" } ?: "—"
                canvas.drawText(
                    tempLabel,
                    centerX,
                    temperatureLabelBaseline,
                    valuePaint
                )
            }

            // Bande pluie
            val precipProb = precipProbabilities.getOrNull(index)?.coerceIn(0, 100)
            val amount = precipAmountsMm.getOrNull(index)?.coerceAtLeast(0.0)
            val precipColor = WidgetMiniForecastRenderer.precipitationHeatmapArgb(
                probability = precipProb,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                amountMm = amount
            )
            cardRect.set(left, precipTop + 2f, right, precipBottom - 2f)
            panelPaint.color = precipColor
            canvas.drawRoundRect(cardRect, 8f, 8f, panelPaint)
            if (index in anchorIndices) {
                val alpha = (precipColor ushr 24) and 0xFF
                valuePaint.color = if (alpha >= 0x72) {
                    WidgetMiniForecastRenderer.heatmapContentColorArgb(precipColorArgb)
                } else {
                    withAlpha(textColorArgb, 0xD8)
                }
                val probText = precipProb?.let { "$it%" } ?: "—"
                val baseline = precipTop + precipBandHeight * 0.62f
                canvas.drawText(probText, centerX, baseline, valuePaint)
            }

            // Repères horaires allégés
            if (index in anchorIndices) {
                labelPaint.color = if (isCurrent) withAlpha(textColorArgb, 0xFF) else withAlpha(textColorArgb, 0xD0)
                val hourLabel = timelineLabels.getOrNull(index) ?: "+${index}h"
                canvas.drawText(hourLabel, centerX, axisY + labelAreaHeight * 0.55f, labelPaint)
            }
        }

        // Tracé de tendance température au-dessus des tuiles.
        plotPoints.zipWithNext().forEach { (a, b) ->
            canvas.drawLine(a.first, a.second, b.first, b.second, linePaint)
        }
        plotPoints.forEachIndexed { index, point ->
            val temp = temps.getOrNull(index)
            pointPaint.color = if (temp != null) {
                val base = WidgetMiniForecastRenderer.temperatureHeatmapArgb(temp)
                WidgetMiniForecastRenderer.heatmapContentColorArgb(base)
            } else {
                withAlpha(textColorArgb, 0x80)
            }
            val radius = if (index == 0) 4.6f else 3.2f
            canvas.drawCircle(point.first, point.second, radius, pointPaint)
        }

        return bitmap
    }

    internal fun anchorIndices(profile: MiniForecastSizeProfile): List<Int> = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> listOf(0, 3, 6, 9, 11)
        MiniForecastSizeProfile.MEDIUM_3X2 -> listOf(0, 2, 4, 6, 8, 10, 11)
        MiniForecastSizeProfile.EXPANDED_4X2 -> (0 until CELL_COUNT).toList()
    }

    internal fun paddedTemperatureRange(minTemp: Double, maxTemp: Double): Pair<Double, Double> {
        if (minTemp == maxTemp) return (minTemp - 2.0) to (maxTemp + 2.0)
        val span = (maxTemp - minTemp).coerceAtLeast(2.0)
        val padding = max(1.5, span * 0.18)
        return (minTemp - padding) to (maxTemp + padding)
    }

    internal fun normalizedTemperatureY(
        temperature: Double,
        minTemp: Double,
        maxTemp: Double,
        top: Float,
        bottom: Float,
        usableTopRatio: Float = 0.14f,
        usableBottomRatio: Float = 0.22f
    ): Float {
        val clamped = ((temperature - minTemp) / (maxTemp - minTemp)).coerceIn(0.0, 1.0)
        val height = bottom - top
        val usableTop = top + height * usableTopRatio
        // Protection supplémentaire pour les très petits widgets : même si les
        // réserves haut/bas se croisent après arrondi, la courbe garde au moins
        // un pixel de hauteur et ne descend jamais dans la zone des valeurs.
        val requestedBottom = bottom - height * usableBottomRatio
        val usableBottom = requestedBottom.coerceAtLeast(usableTop + 1f)
        return usableBottom - ((usableBottom - usableTop) * clamped).toFloat()
    }

    internal fun temperatureCurveTopRatio(profile: MiniForecastSizeProfile): Float = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> 0.42f
        MiniForecastSizeProfile.MEDIUM_3X2 -> 0.40f
        MiniForecastSizeProfile.EXPANDED_4X2 -> 0.37f
    }

    internal fun temperatureLabelBottomInsetRatio(
        profile: MiniForecastSizeProfile
    ): Float = when (profile) {
        MiniForecastSizeProfile.COMPACT_2X2 -> 0.08f
        MiniForecastSizeProfile.MEDIUM_3X2 -> 0.09f
        MiniForecastSizeProfile.EXPANDED_4X2 -> 0.10f
    }

    /**
     * Réserve dynamiquement la zone basse du texte de température.
     *
     * La borne inférieure de la courbe est placée au-dessus du haut réel des
     * glyphes, avec une marge qui inclut le rayon maximal des points. Le clamp
     * conserve malgré tout une hauteur minimale de courbe sur les widgets très
     * compacts.
     */
    internal fun temperatureCurveBottomRatio(
        bandHeightPx: Float,
        labelBaselineRelativePx: Float,
        labelAscentPx: Float,
        maxPointRadiusPx: Float,
        usableTopRatio: Float
    ): Float {
        if (bandHeightPx <= 0f) return 0.30f
        val labelTopPx = labelBaselineRelativePx + labelAscentPx
        val visualGapPx = maxOf(2f, bandHeightPx * 0.035f)
        val requestedCurveBottomPx = labelTopPx - maxPointRadiusPx - visualGapPx
        val minimumCurveHeightPx = maxOf(1f, bandHeightPx * 0.10f)
        val minimumBottomPx = bandHeightPx * usableTopRatio + minimumCurveHeightPx
        val safeCurveBottomPx = requestedCurveBottomPx.coerceAtLeast(minimumBottomPx)
        val ratio = 1f - safeCurveBottomPx / bandHeightPx
        val maximumRatio = (1f - usableTopRatio - 0.10f).coerceAtLeast(0.12f)
        return ratio.coerceIn(0.22f, maximumRatio)
    }

    private fun drawConditionBadge(
        canvas: Canvas,
        condition: WeatherCondition?,
        centerX: Float,
        top: Float,
        bandHeight: Float,
        profile: MiniForecastSizeProfile
    ) {
        if (condition == null) return
        val iconSize = when (profile) {
            MiniForecastSizeProfile.COMPACT_2X2 -> (bandHeight * 0.23f)
            MiniForecastSizeProfile.MEDIUM_3X2 -> (bandHeight * 0.25f)
            MiniForecastSizeProfile.EXPANDED_4X2 -> (bandHeight * 0.26f)
        }.roundToInt().coerceAtLeast(12)

        val badgeHeight = (iconSize * 1.28f).coerceAtLeast(iconSize + 6f)
        val bitmap = WidgetWeatherIconRenderer.render(condition, iconSize)
        val iconLeft = centerX - iconSize / 2f
        val iconTop = top + (badgeHeight - iconSize) / 2f
        canvas.drawBitmap(bitmap, iconLeft, iconTop, null)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
