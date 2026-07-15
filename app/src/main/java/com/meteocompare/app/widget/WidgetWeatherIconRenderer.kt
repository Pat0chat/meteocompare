package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rend un pictogramme météo stable pour les widgets Glance.
 *
 * Le disque coloré qui entourait auparavant chaque symbole a été supprimé :
 * il réduisait le contraste perçu et masquait les petits détails. Les formes
 * disposent maintenant d'un contour sombre discret, lisible aussi bien sur
 * un fond clair que sur un fond sombre ou personnalisé.
 */
internal object WidgetWeatherIconRenderer {

    private const val DEFAULT_OUTLINE = 0x7A263238

    fun render(condition: WeatherCondition?, sizePx: Int): Bitmap {
        require(sizePx > 0) { "sizePx must be > 0" }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = sizePx / 48f

        fun f(value: Float): Float = value * scale
        fun paint(color: Int, style: Paint.Style = Paint.Style.FILL): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.style = style
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        val palette = paletteFor(condition)

        when (condition) {
            WeatherCondition.CLEAR,
            WeatherCondition.MAINLY_CLEAR -> drawSun(canvas, scale, palette.sun, palette.outline)

            WeatherCondition.PARTLY_CLOUDY -> {
                drawSun(
                    canvas,
                    scale,
                    palette.sun,
                    palette.outline,
                    centerX = 18f,
                    centerY = 17f,
                    radius = 7f
                )
                drawCloud(
                    canvas,
                    scale,
                    palette.cloud,
                    palette.outline,
                    offsetX = 3f,
                    offsetY = 4f
                )
            }

            WeatherCondition.OVERCAST ->
                drawCloud(canvas, scale, palette.cloud, palette.outline)

            WeatherCondition.FOG -> {
                drawCloud(canvas, scale, palette.cloud, palette.outline, offsetY = -3f)
                drawOutlinedLines(
                    canvas = canvas,
                    scale = scale,
                    color = palette.precip,
                    outlineColor = palette.outline,
                    strokeWidth = 2.2f,
                    segments = listOf(
                        floatArrayOf(12f, 34f, 36f, 34f),
                        floatArrayOf(16f, 39f, 32f, 39f)
                    )
                )
            }

            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN_SHOWERS -> {
                drawCloud(canvas, scale, palette.cloud, palette.outline, offsetY = -3f)
                drawRain(canvas, scale, palette.precip, palette.outline, light = true)
            }

            WeatherCondition.RAIN -> {
                drawCloud(canvas, scale, palette.cloudDark, palette.outline, offsetY = -3f)
                drawRain(canvas, scale, palette.precip, palette.outline, light = false)
            }

            WeatherCondition.FREEZING_RAIN -> {
                drawCloud(canvas, scale, palette.cloudDark, palette.outline, offsetY = -4f)
                drawRain(canvas, scale, palette.precip, palette.outline, light = true)
                drawSnow(canvas, scale, palette.snow, palette.outline, y = 40f, count = 2)
            }

            WeatherCondition.SNOW,
            WeatherCondition.SNOW_SHOWERS -> {
                drawCloud(canvas, scale, palette.cloud, palette.outline, offsetY = -4f)
                drawSnow(canvas, scale, palette.snow, palette.outline, y = 36f, count = 3)
            }

            WeatherCondition.THUNDERSTORM -> {
                drawCloud(canvas, scale, palette.cloudDark, palette.outline, offsetY = -4f)
                val bolt = Path().apply {
                    moveTo(f(25f), f(28f))
                    lineTo(f(20f), f(37f))
                    lineTo(f(25f), f(37f))
                    lineTo(f(22f), f(44f))
                    lineTo(f(32f), f(33f))
                    lineTo(f(27f), f(33f))
                    close()
                }
                canvas.drawPath(
                    bolt,
                    paint(palette.outline, Paint.Style.STROKE).apply { strokeWidth = f(2.6f) }
                )
                canvas.drawPath(bolt, paint(palette.sun))
            }

            WeatherCondition.UNKNOWN,
            null -> {
                val outer = paint(palette.outline, Paint.Style.STROKE).apply {
                    strokeWidth = f(4.2f)
                }
                val inner = paint(palette.cloudDark, Paint.Style.STROKE).apply {
                    strokeWidth = f(2.4f)
                }
                canvas.drawCircle(f(24f), f(24f), f(10f), outer)
                canvas.drawCircle(f(24f), f(24f), f(10f), inner)
                canvas.drawLine(f(24f), f(16f), f(24f), f(27f), outer)
                canvas.drawLine(f(24f), f(16f), f(24f), f(27f), inner)
                canvas.drawCircle(f(24f), f(32f), f(2.3f), paint(palette.outline))
                canvas.drawCircle(f(24f), f(32f), f(1.4f), paint(palette.cloudDark))
            }
        }

        return bitmap
    }

    private fun drawSun(
        canvas: Canvas,
        scale: Float,
        color: Int,
        outlineColor: Int,
        centerX: Float = 24f,
        centerY: Float = 24f,
        radius: Float = 9f
    ) {
        fun f(value: Float): Float = value * scale
        val outlineRayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = outlineColor
            style = Paint.Style.STROKE
            strokeWidth = f(4.0f)
            strokeCap = Paint.Cap.ROUND
        }
        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = f(2.2f)
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val inner = radius + 3f
            val outer = radius + 6f
            val startX = f(centerX + cos(angle).toFloat() * inner)
            val startY = f(centerY + sin(angle).toFloat() * inner)
            val endX = f(centerX + cos(angle).toFloat() * outer)
            val endY = f(centerY + sin(angle).toFloat() * outer)
            canvas.drawLine(startX, startY, endX, endY, outlineRayPaint)
            canvas.drawLine(startX, startY, endX, endY, rayPaint)
        }
        canvas.drawCircle(
            f(centerX),
            f(centerY),
            f(radius + 1.4f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = outlineColor }
        )
        canvas.drawCircle(
            f(centerX),
            f(centerY),
            f(radius),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        )
    }

    private fun drawCloud(
        canvas: Canvas,
        scale: Float,
        color: Int,
        outlineColor: Int,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        drawCloudLayer(canvas, scale, outlineColor, offsetX, offsetY, expansion = 1.5f)
        drawCloudLayer(canvas, scale, color, offsetX, offsetY, expansion = 0f)
    }

    private fun drawCloudLayer(
        canvas: Canvas,
        scale: Float,
        color: Int,
        offsetX: Float,
        offsetY: Float,
        expansion: Float
    ) {
        fun f(value: Float): Float = value * scale
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(
            f(18f + offsetX),
            f(25f + offsetY),
            f(7f + expansion),
            cloudPaint
        )
        canvas.drawCircle(
            f(26f + offsetX),
            f(21f + offsetY),
            f(9f + expansion),
            cloudPaint
        )
        canvas.drawCircle(
            f(34f + offsetX),
            f(26f + offsetY),
            f(6.5f + expansion),
            cloudPaint
        )
        canvas.drawRoundRect(
            RectF(
                f(12f + offsetX - expansion),
                f(24f + offsetY - expansion),
                f(40f + offsetX + expansion),
                f(33f + offsetY + expansion)
            ),
            f(4.5f + expansion),
            f(4.5f + expansion),
            cloudPaint
        )
    }

    private fun drawRain(
        canvas: Canvas,
        scale: Float,
        color: Int,
        outlineColor: Int,
        light: Boolean
    ) {
        val xs = if (light) floatArrayOf(19f, 29f) else floatArrayOf(16f, 24f, 32f)
        val segments = xs.mapIndexed { index, x ->
            val top = if (index % 2 == 0) 35f else 37f
            floatArrayOf(x, top, x - 2f, top + 5f)
        }
        drawOutlinedLines(
            canvas = canvas,
            scale = scale,
            color = color,
            outlineColor = outlineColor,
            strokeWidth = if (light) 1.8f else 2.4f,
            segments = segments
        )
    }

    private fun drawOutlinedLines(
        canvas: Canvas,
        scale: Float,
        color: Int,
        outlineColor: Int,
        strokeWidth: Float,
        segments: List<FloatArray>
    ) {
        fun f(value: Float): Float = value * scale
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = outlineColor
            style = Paint.Style.STROKE
            this.strokeWidth = f(strokeWidth + 1.8f)
            strokeCap = Paint.Cap.ROUND
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = f(strokeWidth)
            strokeCap = Paint.Cap.ROUND
        }
        segments.forEach { segment ->
            canvas.drawLine(f(segment[0]), f(segment[1]), f(segment[2]), f(segment[3]), outlinePaint)
            canvas.drawLine(f(segment[0]), f(segment[1]), f(segment[2]), f(segment[3]), linePaint)
        }
    }

    private fun drawSnow(
        canvas: Canvas,
        scale: Float,
        color: Int,
        outlineColor: Int,
        y: Float,
        count: Int
    ) {
        fun f(value: Float): Float = value * scale
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = outlineColor }
        val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val xs = when (count) {
            2 -> floatArrayOf(20f, 30f)
            else -> floatArrayOf(16f, 24f, 32f)
        }
        xs.forEachIndexed { index, x ->
            val cy = y + (index % 2) * 2f
            canvas.drawCircle(f(x), f(cy), f(3f), outlinePaint)
            canvas.drawCircle(f(x), f(cy), f(1.9f), snowPaint)
        }
    }

    private fun paletteFor(condition: WeatherCondition?): Palette = when (condition) {
        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR -> Palette(
            sun = 0xFFFFB300.toInt()
        )

        WeatherCondition.PARTLY_CLOUDY -> Palette(
            sun = 0xFFFFB300.toInt(),
            cloud = 0xFFF4F7FA.toInt()
        )

        WeatherCondition.OVERCAST,
        WeatherCondition.FOG -> Palette(
            cloud = 0xFFB0BEC5.toInt(),
            cloudDark = 0xFF607D8B.toInt(),
            precip = 0xFF90A4AE.toInt()
        )

        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN_SHOWERS,
        WeatherCondition.RAIN,
        WeatherCondition.FREEZING_RAIN -> Palette(
            cloud = 0xFFE7EDF1.toInt(),
            cloudDark = 0xFF607D8B.toInt(),
            precip = 0xFF42A5F5.toInt(),
            snow = 0xFFE1F5FE.toInt()
        )

        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> Palette(
            cloud = 0xFFF4F7FA.toInt(),
            snow = 0xFF81D4FA.toInt()
        )

        WeatherCondition.THUNDERSTORM -> Palette(
            sun = 0xFFFFD54F.toInt(),
            cloudDark = 0xFF546E7A.toInt()
        )

        WeatherCondition.UNKNOWN,
        null -> Palette(
            cloudDark = 0xFF90A4AE.toInt()
        )
    }

    private data class Palette(
        val sun: Int = 0xFFFFB300.toInt(),
        val cloud: Int = 0xFFE7EDF1.toInt(),
        val cloudDark: Int = 0xFF607D8B.toInt(),
        val precip: Int = 0xFF42A5F5.toInt(),
        val snow: Int = 0xFF81D4FA.toInt(),
        val outline: Int = DEFAULT_OUTLINE
    )
}
