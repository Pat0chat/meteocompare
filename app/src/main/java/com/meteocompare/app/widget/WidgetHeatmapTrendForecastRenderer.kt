package com.meteocompare.app.widget

import androidx.core.graphics.createBitmap
import com.meteocompare.app.core.charts.metricPlotValue

import com.meteocompare.app.core.units.WeatherUnits

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
        nightFlags: List<Boolean?> = emptyList(),
        precipColorArgb: Int,
        textColorArgb: Int,
        timelineLabels: List<String> = emptyList(),
        profile: MiniForecastSizeProfile = MiniForecastSizeProfile.EXPANDED_4X2,
        units: WeatherUnits = WeatherUnits()
    ): Bitmap {
        val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outerPadding = (heightPx * 0.02f).coerceAtLeast(1.0f)
        val rowGap = (heightPx * 0.02f).coerceAtLeast(1.5f)
        val axisGap = (heightPx * 0.025f).coerceAtLeast(2f)
        val labelAreaHeight = (heightPx * 0.12f).coerceAtLeast(8f)
        val usableWidth = widthPx - outerPadding * 2f
        val columnWidth = usableWidth / CELL_COUNT
        val tempBandHeight = (
            (heightPx - outerPadding * 2f - rowGap - axisGap - labelAreaHeight) * 0.64f
        ).coerceAtLeast(heightPx * 0.55f)
        val precipBandHeight = (
            heightPx - outerPadding * 2f - rowGap - axisGap -
                labelAreaHeight - tempBandHeight
        ).coerceAtLeast(heightPx * 0.05f)
        val tempTop = outerPadding
        val tempBottom = tempTop + tempBandHeight
        val precipTop = tempBottom + rowGap
        val precipBottom = precipTop + precipBandHeight
        val axisY = precipBottom + axisGap
        val slotInset = (columnWidth * 0.03f).coerceAtLeast(0.6f)
        val anchors = WidgetHeatmapForecastRenderer.anchorIndices(profile)

        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 12f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 13f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 16f
            }.coerceAtMost(columnWidth * 0.55f)
            color = withAlpha(textColorArgb, 0xD8)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 12f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 13f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 16f
            }.coerceAtMost(columnWidth * 0.65f)
        }

        val tempValues = temps.mapNotNull(::metricPlotValue)
        val minTemp = tempValues.minOrNull() ?: 0.0
        val maxTemp = tempValues.maxOrNull() ?: 1.0
        val padded = WidgetHeatmapForecastRenderer.paddedTemperatureRange(minTemp, maxTemp)
        val plotPoints = mutableListOf<Pair<Float, Float>>()
        val curveTopRatio = WidgetHeatmapForecastRenderer.temperatureCurveTopRatio(profile)
        val temperatureLabelBaseline = tempBottom -
                tempBandHeight * WidgetHeatmapForecastRenderer.temperatureLabelBottomInsetRatio(profile)
        val curveBottomRatio = WidgetHeatmapForecastRenderer.temperatureCurveBottomRatio(
            bandHeightPx = tempBandHeight,
            labelBaselineRelativePx = temperatureLabelBaseline - tempTop,
            labelAscentPx = valuePaint.fontMetrics.ascent,
            maxPointRadiusPx = 5.4f,
            usableTopRatio = curveTopRatio
        )

        for (index in 0 until CELL_COUNT) {
            val left = outerPadding + index * columnWidth + slotInset
            val right = outerPadding + (index + 1) * columnWidth - slotInset
            val centerX = (left + right) / 2f
            val isCurrent = index == 0

            val temp = metricPlotValue(temps.getOrNull(index))
            val tempColor = temp
                ?.let(WidgetMiniForecastRenderer::temperatureHeatmapArgb)
                ?: withAlpha(textColorArgb, 0x14)
            val tempRect = RectF(left, tempTop + 4f, right, tempBottom - 4f)
            val gradient = LinearGradient(
                left,
                tempTop,
                left,
                tempBottom,
                withAlpha(tempColor, if (isCurrent) 0x5A else 0x44),
                withAlpha(tempColor, if (isCurrent) 0x20 else 0x14),
                Shader.TileMode.CLAMP
            )
            panelPaint.shader = gradient
            canvas.drawRect(tempRect, panelPaint)
            panelPaint.shader = null
            if (isCurrent) {
                panelPaint.color = withAlpha(textColorArgb, 0xA0)
                canvas.drawRect(left, tempTop, right, tempTop + 2f, panelPaint)
            }

            val tempY = temp?.let {
                WidgetHeatmapForecastRenderer.normalizedTemperatureY(
                    temperature = it,
                    minTemp = padded.first,
                    maxTemp = padded.second,
                    top = tempTop,
                    bottom = tempBottom,
                    usableTopRatio = curveTopRatio,
                    usableBottomRatio = curveBottomRatio
                )
            } ?: ((tempTop + tempBottom) / 2f)
            plotPoints += centerX to tempY

            if (index in anchors) {
                drawConditionBadge(
                    canvas = canvas,
                    condition = conditions.getOrNull(index),
                    centerX = centerX,
                    top = tempTop + tempBandHeight * 0.05f,
                    bandHeight = tempBandHeight,
                    profile = profile,
                    isNight = nightFlags.getOrNull(index) == true
                )
                valuePaint.color = withAlpha(textColorArgb, 0xEC)
                canvas.drawText(
                    temp?.let { units.temp(it) } ?: "—",
                    centerX,
                    temperatureLabelBaseline,
                    valuePaint
                )
            }

            val precipProb = precipProbabilities.getOrNull(index)?.coerceIn(0, 100)
            val precipColor = WidgetMiniForecastRenderer.precipitationHeatmapArgb(
                probability = precipProb,
                precipColorArgb = precipColorArgb,
                textColorArgb = textColorArgb,
                amountMm = precipAmountsMm.getOrNull(index)
            )
            val precipRect = RectF(left, precipTop + 2f, right, precipBottom - 2f)
            val rainAlpha = (precipColor ushr 24) and 0xFF
            val precipGradient = LinearGradient(
                left,
                precipTop,
                right,
                precipBottom,
                withAlpha(precipColor, (rainAlpha * 0.38f).toInt().coerceIn(16, 84)),
                withAlpha(precipColor, (rainAlpha * 0.70f).toInt().coerceIn(28, 132)),
                Shader.TileMode.CLAMP
            )
            panelPaint.shader = precipGradient
            canvas.drawRect(precipRect, panelPaint)
            panelPaint.shader = null
            if (index in anchors) {
                valuePaint.color = withAlpha(textColorArgb, 0xDF)
                canvas.drawText(
                    precipProb?.let { "$it%" } ?: "—",
                    centerX,
                    precipTop + precipBandHeight * 0.62f,
                    valuePaint
                )
                labelPaint.color = if (isCurrent) {
                    withAlpha(textColorArgb, 0xFF)
                } else {
                    withAlpha(textColorArgb, 0xD0)
                }
                val hourLabel = timelineLabels.getOrNull(index) ?: "+${index}h"
                val naturalSize = labelPaint.textSize
                val textWidth = labelPaint.measureText(hourLabel)
                if (textWidth > columnWidth * 0.90f && textWidth > 0f) {
                    labelPaint.textSize = (naturalSize * columnWidth * 0.90f / textWidth).coerceAtLeast(6f)
                }
                canvas.drawText(hourLabel, centerX, axisY + labelAreaHeight * 0.55f, labelPaint)
                labelPaint.textSize = naturalSize
            }
        }

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 4.0f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 4.8f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 5.6f
            }
            color = withAlpha(textColorArgb, 0x20)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = when (profile) {
                MiniForecastSizeProfile.COMPACT_2X2 -> 2.0f
                MiniForecastSizeProfile.MEDIUM_3X2 -> 2.6f
                MiniForecastSizeProfile.EXPANDED_4X2 -> 3.2f
            }
            color = withAlpha(textColorArgb, 0xEC)
        }
        val pointFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val pointRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = withAlpha(textColorArgb, 0x55)
        }
        plotPoints.zipWithNext().forEach { (a, b) ->
            canvas.drawLine(a.first, a.second, b.first, b.second, haloPaint)
            canvas.drawLine(a.first, a.second, b.first, b.second, linePaint)
        }
        plotPoints.forEachIndexed { index, point ->
            val radius = if (index == 0) 5.4f else 4.0f
            pointFillPaint.color = withAlpha(textColorArgb, 0xEC)
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
        isNight: Boolean = false
    ) {
        if (condition == null) return
        val iconSize = when (profile) {
            MiniForecastSizeProfile.COMPACT_2X2 -> (bandHeight * 0.23f)
            MiniForecastSizeProfile.MEDIUM_3X2 -> (bandHeight * 0.25f)
            MiniForecastSizeProfile.EXPANDED_4X2 -> (bandHeight * 0.26f)
        }.roundToInt().coerceAtLeast(12)

        val badgeHeight = (iconSize * 1.30f).coerceAtLeast(iconSize + 7f)
        val bitmap = WidgetWeatherIconRenderer.render(condition, iconSize, isNight = isNight)
        val iconLeft = centerX - iconSize / 2f
        val iconTop = top + (badgeHeight - iconSize) / 2f
        canvas.drawBitmap(bitmap, iconLeft, iconTop, null)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
