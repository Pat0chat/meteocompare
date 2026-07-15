package com.meteocompare.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a small, consistent weather pictogram for Glance widgets.
 *
 * Emoji glyphs are intentionally avoided here: their shape, baseline and
 * colors vary considerably between launchers and Android vendors. Drawing the
 * icon into a bitmap keeps the widget visually stable on Pixel, One UI, MIUI
 * and third-party launchers while still requiring no bundled image assets.
 */
internal object WidgetWeatherIconRenderer {

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

        // Soft circular tile. Besides looking more deliberate than a floating
        // glyph, this keeps pale icons readable on user-selected backgrounds.
        canvas.drawCircle(f(24f), f(24f), f(22f), paint(palette.tile))

        when (condition) {
            WeatherCondition.CLEAR,
            WeatherCondition.MAINLY_CLEAR -> drawSun(canvas, scale, palette.sun)

            WeatherCondition.PARTLY_CLOUDY -> {
                drawSun(canvas, scale, palette.sun, centerX = 18f, centerY = 17f, radius = 7f)
                drawCloud(canvas, scale, palette.cloud, offsetX = 3f, offsetY = 4f)
            }

            WeatherCondition.OVERCAST -> drawCloud(canvas, scale, palette.cloud)

            WeatherCondition.FOG -> {
                drawCloud(canvas, scale, palette.cloud, offsetY = -3f)
                val fogPaint = paint(palette.precip, Paint.Style.STROKE).apply {
                    strokeWidth = f(2.2f)
                }
                canvas.drawLine(f(12f), f(34f), f(36f), f(34f), fogPaint)
                canvas.drawLine(f(16f), f(39f), f(32f), f(39f), fogPaint)
            }

            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN_SHOWERS -> {
                drawCloud(canvas, scale, palette.cloud, offsetY = -3f)
                drawRain(canvas, scale, palette.precip, light = true)
            }

            WeatherCondition.RAIN -> {
                drawCloud(canvas, scale, palette.cloudDark, offsetY = -3f)
                drawRain(canvas, scale, palette.precip, light = false)
            }

            WeatherCondition.FREEZING_RAIN -> {
                drawCloud(canvas, scale, palette.cloudDark, offsetY = -4f)
                drawRain(canvas, scale, palette.precip, light = true)
                drawSnow(canvas, scale, palette.snow, y = 40f, count = 2)
            }

            WeatherCondition.SNOW,
            WeatherCondition.SNOW_SHOWERS -> {
                drawCloud(canvas, scale, palette.cloud, offsetY = -4f)
                drawSnow(canvas, scale, palette.snow, y = 36f, count = 3)
            }

            WeatherCondition.THUNDERSTORM -> {
                drawCloud(canvas, scale, palette.cloudDark, offsetY = -4f)
                val bolt = Path().apply {
                    moveTo(f(25f), f(28f))
                    lineTo(f(20f), f(37f))
                    lineTo(f(25f), f(37f))
                    lineTo(f(22f), f(44f))
                    lineTo(f(32f), f(33f))
                    lineTo(f(27f), f(33f))
                    close()
                }
                canvas.drawPath(bolt, paint(palette.sun))
            }

            WeatherCondition.UNKNOWN,
            null -> {
                val unknownPaint = paint(palette.cloudDark, Paint.Style.STROKE).apply {
                    strokeWidth = f(2.5f)
                }
                canvas.drawCircle(f(24f), f(24f), f(10f), unknownPaint)
                canvas.drawLine(f(24f), f(16f), f(24f), f(27f), unknownPaint)
                canvas.drawCircle(f(24f), f(32f), f(1.5f), paint(palette.cloudDark))
            }
        }

        return bitmap
    }

    private fun drawSun(
        canvas: Canvas,
        scale: Float,
        color: Int,
        centerX: Float = 24f,
        centerY: Float = 24f,
        radius: Float = 9f
    ) {
        fun f(value: Float): Float = value * scale
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
            canvas.drawLine(
                f(centerX + cos(angle).toFloat() * inner),
                f(centerY + sin(angle).toFloat() * inner),
                f(centerX + cos(angle).toFloat() * outer),
                f(centerY + sin(angle).toFloat() * outer),
                rayPaint
            )
        }
        canvas.drawCircle(f(centerX), f(centerY), f(radius), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        })
    }

    private fun drawCloud(
        canvas: Canvas,
        scale: Float,
        color: Int,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        fun f(value: Float): Float = value * scale
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(f(18f + offsetX), f(25f + offsetY), f(7f), cloudPaint)
        canvas.drawCircle(f(26f + offsetX), f(21f + offsetY), f(9f), cloudPaint)
        canvas.drawCircle(f(34f + offsetX), f(26f + offsetY), f(6.5f), cloudPaint)
        canvas.drawRoundRect(
            RectF(
                f(12f + offsetX),
                f(24f + offsetY),
                f(40f + offsetX),
                f(33f + offsetY)
            ),
            f(4.5f),
            f(4.5f),
            cloudPaint
        )
    }

    private fun drawRain(canvas: Canvas, scale: Float, color: Int, light: Boolean) {
        fun f(value: Float): Float = value * scale
        val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = f(if (light) 1.8f else 2.4f)
            strokeCap = Paint.Cap.ROUND
        }
        val xs = if (light) floatArrayOf(19f, 29f) else floatArrayOf(16f, 24f, 32f)
        xs.forEachIndexed { index, x ->
            val top = if (index % 2 == 0) 35f else 37f
            canvas.drawLine(f(x), f(top), f(x - 2f), f(top + 5f), rainPaint)
        }
    }

    private fun drawSnow(canvas: Canvas, scale: Float, color: Int, y: Float, count: Int) {
        fun f(value: Float): Float = value * scale
        val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val xs = when (count) {
            2 -> floatArrayOf(20f, 30f)
            else -> floatArrayOf(16f, 24f, 32f)
        }
        xs.forEachIndexed { index, x ->
            canvas.drawCircle(f(x), f(y + (index % 2) * 2f), f(2f), snowPaint)
        }
    }

    private fun paletteFor(condition: WeatherCondition?): Palette = when (condition) {
        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR -> Palette(
            tile = Color.argb(42, 255, 183, 77),
            sun = 0xFFFFB300.toInt()
        )

        WeatherCondition.PARTLY_CLOUDY -> Palette(
            tile = Color.argb(38, 79, 195, 247),
            sun = 0xFFFFB300.toInt(),
            cloud = 0xFFF4F7FA.toInt()
        )

        WeatherCondition.OVERCAST,
        WeatherCondition.FOG -> Palette(
            tile = Color.argb(38, 120, 144, 156),
            cloud = 0xFFB0BEC5.toInt(),
            cloudDark = 0xFF607D8B.toInt(),
            precip = 0xFF90A4AE.toInt()
        )

        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN_SHOWERS,
        WeatherCondition.RAIN,
        WeatherCondition.FREEZING_RAIN -> Palette(
            tile = Color.argb(40, 25, 118, 210),
            cloud = 0xFFE7EDF1.toInt(),
            cloudDark = 0xFF607D8B.toInt(),
            precip = 0xFF42A5F5.toInt(),
            snow = 0xFFE1F5FE.toInt()
        )

        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> Palette(
            tile = Color.argb(42, 3, 169, 244),
            cloud = 0xFFF4F7FA.toInt(),
            snow = 0xFF81D4FA.toInt()
        )

        WeatherCondition.THUNDERSTORM -> Palette(
            tile = Color.argb(44, 94, 53, 177),
            sun = 0xFFFFD54F.toInt(),
            cloudDark = 0xFF546E7A.toInt()
        )

        WeatherCondition.UNKNOWN,
        null -> Palette(
            tile = Color.argb(28, 120, 144, 156),
            cloudDark = 0xFF90A4AE.toInt()
        )
    }

    private data class Palette(
        val tile: Int,
        val sun: Int = 0xFFFFB300.toInt(),
        val cloud: Int = 0xFFE7EDF1.toInt(),
        val cloudDark: Int = 0xFF607D8B.toInt(),
        val precip: Int = 0xFF42A5F5.toInt(),
        val snow: Int = 0xFF81D4FA.toInt()
    )
}
