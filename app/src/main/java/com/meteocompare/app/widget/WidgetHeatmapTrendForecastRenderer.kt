package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.roundToInt

/**
 * Variante premium du rendu 12 h : icônes météo, courbe de tendance plus
 * marquée, halo léger et contrastes un peu plus “dashboard”.
 */
internal object WidgetHeatmapTrendForecastRenderer {

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
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outerPadding = (heightPx * 0.05f).coerceAtLeast(4f)
        val rowGap = (heightPx * 0.07f).coerceAtLeast(5f)
        val axisGap = (heightPx * 0.045f).coerceAtLeast(3f)
        val labelAreaHeight = (heightPx * 0.16f).coerceAtLeast(11f)
        val usableWidth = widthPx - outerPadding * 2f
        val columnWidth = usableWidth / CELL_COUNT
        val tempBandHeight = ((heightPx - outerPadding * 2f - rowGap - axisGap - labelAreaHeight) * 0.60f).coerceAtLeast(heightPx * 0.30f)
        val precipBandHeight = (heightPx - outerPadding * 2f - rowGap - axisGap - labelAreaHeight - tempBandHeight).coerceAtLeast(heightPx * 0.13f)
        val tempTop = outerPadding
        val tempBottom = tempTop + tempBandHeight
        val precipTop = tempBottom + rowGap
        val precipBottom = precipTop + precipBandHeight
        val axisY = precipBottom + axisGap
        val slotInset = (columnWidth * 0.08f).coerceAtLeast(1f)
        val anchors = WidgetHeatmapForecastRenderer.anchorIndices(profile)

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = withAlpha(textColorArgb, 0x18)
            strokeWidth = (heightPx * 0.004f).coerceAtLeast(1f)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 9f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 10f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 11f
            }.coerceAtMost(columnWidth * 0.42f)
            color = withAlpha(textColorArgb, 0xD8)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 10f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 11f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 12f
            }.coerceAtMost(columnWidth * 0.46f)
        }

        val surfaceRect = RectF(outerPadding * 0.25f, tempTop, widthPx - outerPadding * 0.25f, precipBottom)
        panelPaint.color = withAlpha(textColorArgb, 0x0F)
        canvas.drawRoundRect(surfaceRect, 16f, 16f, panelPaint)

        val tempValues = temps.filterNotNull()
        val minTemp = tempValues.minOrNull() ?: 0.0
        val maxTemp = tempValues.maxOrNull() ?: 1.0
        val padded = WidgetHeatmapForecastRenderer.paddedTemperatureRange(minTemp, maxTemp)
        val plotPoints = mutableListOf<Pair<Float, Float>>()

        for (index in 0 until CELL_COUNT) {
            val left = outerPadding + index * columnWidth + slotInset
            val right = outerPadding + (index + 1) * columnWidth - slotInset
            val centerX = (left + right) / 2f
            val isCurrent = index == 0

            val temp = temps.getOrNull(index)
            val tempColor = temp?.let(WidgetMiniForecastRenderer::temperatureHeatmapArgb) ?: withAlpha(textColorArgb, 0x14)
            val tempRect = RectF(left, tempTop + 4f, right, tempBottom - 4f)
            val gradient = LinearGradient(left, tempTop, left, tempBottom, withAlpha(tempColor, if (isCurrent) 0xF2 else 0xE4), withAlpha(tempColor, if (isCurrent) 0xC8 else 0xB0), Shader.TileMode.CLAMP)
            panelPaint.shader = gradient
            canvas.drawRoundRect(tempRect, 11f, 11f, panelPaint)
            panelPaint.shader = null
            if (isCurrent) {
                panelPaint.style = Paint.Style.STROKE
                panelPaint.color = withAlpha(textColorArgb, 0x5E)
                panelPaint.strokeWidth = 2.4f
                canvas.drawRoundRect(tempRect, 11f, 11f, panelPaint)
                panelPaint.style = Paint.Style.FILL
            }

            val tempY = temp?.let {
                WidgetHeatmapForecastRenderer.normalizedTemperatureY(it, padded.first, padded.second, tempTop, tempBottom)
            } ?: ((tempTop + tempBottom) / 2f)
            plotPoints += centerX to tempY

            if (index in anchors) {
                drawConditionBadge(
                    canvas = canvas,
                    condition = conditions.getOrNull(index),
                    centerX = centerX,
                    top = tempTop + tempBandHeight * 0.03f,
                    bandHeight = tempBandHeight,
                    profile = profile,
                    textColorArgb = textColorArgb,
                    accentColorArgb = tempColor,
                    isCurrent = isCurrent
                )
                val contentColor = if (temp == null) withAlpha(textColorArgb, 0xD8) else WidgetMiniForecastRenderer.heatmapContentColorArgb(tempColor)
                valuePaint.color = contentColor
                canvas.drawText(temp?.let { "${it.roundToInt()}°" } ?: "—", centerX, tempBottom - tempBandHeight * 0.13f, valuePaint)
            }

            val precipProb = precipProbabilities.getOrNull(index)?.coerceIn(0, 100)
            val precipColor = WidgetMiniForecastRenderer.precipitationHeatmapArgb(precipProb, precipColorArgb, textColorArgb, precipAmountsMm.getOrNull(index))
            val precipRect = RectF(left, precipTop + 2f, right, precipBottom - 2f)
            val precipGradient = LinearGradient(left, precipTop, right, precipBottom, withAlpha(precipColor, 0xD0), withAlpha(precipColor, 0xF2), Shader.TileMode.CLAMP)
            panelPaint.shader = precipGradient
            canvas.drawRoundRect(precipRect, 8f, 8f, panelPaint)
            panelPaint.shader = null
            if (index in anchors) {
                valuePaint.color = if (((precipColor ushr 24) and 0xFF) >= 0x72) WidgetMiniForecastRenderer.heatmapContentColorArgb(precipColorArgb) else withAlpha(textColorArgb, 0xD8)
                canvas.drawText(precipProb?.let { "$it%" } ?: "—", centerX, precipTop + precipBandHeight * 0.62f, valuePaint)
                labelPaint.color = if (isCurrent) withAlpha(textColorArgb, 0xFF) else withAlpha(textColorArgb, 0xD0)
                val hourLabel = timelineLabels.getOrNull(index) ?: "+${index}h"
                canvas.drawText(hourLabel, centerX, axisY + labelAreaHeight * 0.55f, labelPaint)
            }

            if (index < CELL_COUNT - 1) {
                val x = outerPadding + (index + 1) * columnWidth
                canvas.drawLine(x, tempTop + 6f, x, precipBottom - 4f, gridPaint)
            }
        }

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 5.8f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 6.6f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 7.2f
            }
            color = withAlpha(0xFFFFFFFF.toInt(), 0x40)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 3.0f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 3.6f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 4.2f
            }
            color = 0xFFFFFFFF.toInt()
        }
        val pointFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val pointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = withAlpha(textColorArgb, 0x55)
        }
        plotPoints.zipWithNext().forEach { (a, b) ->
            canvas.drawLine(a.first, a.second, b.first, b.second, haloPaint)
            canvas.drawLine(a.first, a.second, b.first, b.second, linePaint)
        }
        plotPoints.forEachIndexed { index, point ->
            val radius = if (index == 0) 5.4f else 4.0f
            pointFillPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(point.first, point.second, radius + 1.4f, pointRingPaint)
            canvas.drawCircle(point.first, point.second, radius, pointFillPaint)
        }

        return bitmap
    }

    private fun drawConditionBadge(
        canvas: Canvas,
        condition: WeatherCondition?,
        centerX: Float,
        top: Float,
        bandHeight: Float,
        profile: MiniForecastSizeProfile,
        textColorArgb: Int,
        accentColorArgb: Int,
        isCurrent: Boolean
    ) {
        if (condition == null) return
        val iconSize = when (profile) {
            MiniForecastSizeProfile.COMPACT_2X2 -> (bandHeight * 0.19f)
            MiniForecastSizeProfile.MEDIUM_3X2 -> (bandHeight * 0.21f)
            MiniForecastSizeProfile.EXPANDED_4X2 -> (bandHeight * 0.22f)
        }.roundToInt().coerceAtLeast(12)
        val badgeWidth = (iconSize * 1.72f).coerceAtLeast(iconSize + 10f)
        val badgeHeight = (iconSize * 1.30f).coerceAtLeast(iconSize + 7f)
        val left = centerX - badgeWidth / 2f
        val rect = RectF(left, top, left + badgeWidth, top + badgeHeight)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isCurrent) withAlpha(accentColorArgb, 0x32) else withAlpha(textColorArgb, 0x18)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isCurrent) withAlpha(0xFFFFFFFF.toInt(), 0xE2) else withAlpha(0xFFFFFFFF.toInt(), 0xCA)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = if (isCurrent) 2.4f else 1.8f
            color = if (isCurrent) withAlpha(accentColorArgb, 0x90) else withAlpha(textColorArgb, 0x3D)
        }
        val glowRect = RectF(rect.left - 1.5f, rect.top - 1.5f, rect.right + 1.5f, rect.bottom + 1.5f)
        canvas.drawRoundRect(glowRect, badgeHeight / 2f, badgeHeight / 2f, glowPaint)
        canvas.drawRoundRect(rect, badgeHeight / 2f, badgeHeight / 2f, fillPaint)
        canvas.drawRoundRect(rect, badgeHeight / 2f, badgeHeight / 2f, strokePaint)

        val bitmap = WidgetWeatherIconRenderer.render(condition, iconSize)
        val iconLeft = centerX - iconSize / 2f
        val iconTop = top + (badgeHeight - iconSize) / 2f
        canvas.drawBitmap(bitmap, iconLeft, iconTop, null)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
