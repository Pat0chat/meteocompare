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
 * Rendu météo pour Glance, aligné sur la famille d'icônes Compose 2026.
 *
 * Principes : aplats francs, géométrie simple, extrémités arrondies,
 * contour tonal discret et meilleure définition à petite taille.
 */
internal object WidgetWeatherIconRenderer {

    fun render(condition: WeatherCondition?, sizePx: Int): Bitmap {
        require(sizePx > 0) { "sizePx must be > 0" }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = sizePx / 48f
        val palette = paletteFor(condition)

        when (condition) {
            WeatherCondition.CLEAR -> drawSun(canvas, scale, palette, 24f, 24f, 9f)
            WeatherCondition.MAINLY_CLEAR -> {
                drawSun(canvas, scale, palette, 18f, 18f, 6.9f)
                drawCloud(canvas, scale, palette, 3f, 4f, 0.90f)
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                drawSun(canvas, scale, palette, 17f, 17f, 6.6f)
                drawCloud(canvas, scale, palette, 4f, 5f, 0.98f)
            }
            WeatherCondition.OVERCAST -> {
                drawCloud(canvas, scale, palette, -9f, -4f, 0.74f, depth = 0.08f, alpha = 1f)
                drawCloud(canvas, scale, palette, 6f, 5f, 1.08f, depth = 0.98f, alpha = 1f)
            }
            WeatherCondition.FOG -> {
                drawCloud(canvas, scale, palette, 0f, -4f, 0.90f, depth = 0.44f, alpha = 0.88f)
                drawLines(canvas, scale, palette.fog, listOf(
                    floatArrayOf(11f, 34f, 37f, 34f),
                    floatArrayOf(16f, 39.5f, 34f, 39.5f),
                    floatArrayOf(13f, 44.5f, 35f, 44.5f)
                ), 2.5f)
            }
            WeatherCondition.DRIZZLE -> {
                drawCloud(canvas, scale, palette, 0f, -4f, 0.95f, depth = 0.70f)
                drawRain(canvas, scale, palette, light = true, fine = true)
            }
            WeatherCondition.RAIN -> {
                drawCloud(canvas, scale, palette.copy(cloudDark = 0xFF708390.toInt()), 0f, -4f, 1f, depth = 0.82f)
                drawRain(canvas, scale, palette, light = false, fine = false)
            }
            WeatherCondition.RAIN_SHOWERS -> {
                drawSun(canvas, scale, palette, 16f, 15f, 5.8f)
                drawCloud(canvas, scale, palette, 3f, -2f, 0.96f, depth = 0.74f)
                drawRain(canvas, scale, palette, light = true, xShift = 2f, fine = false)
            }
            WeatherCondition.FREEZING_RAIN -> {
                drawCloud(canvas, scale, palette.copy(cloudDark = 0xFF708390.toInt()), 0f, -5f, 1f, depth = 0.84f)
                drawRain(canvas, scale, palette, light = true, fine = false)
                drawSnowflake(canvas, scale, 26f, 40f, 3.7f, palette.freeze)
            }
            WeatherCondition.SNOW -> {
                drawCloud(canvas, scale, palette, 0f, -5f, 0.96f, depth = 0.66f)
                drawSnow(canvas, scale, palette, showers = false)
            }
            WeatherCondition.SNOW_SHOWERS -> {
                drawSun(canvas, scale, palette, 16f, 15f, 5.8f)
                drawCloud(canvas, scale, palette, 3f, -3f, 0.96f, depth = 0.70f)
                drawSnow(canvas, scale, palette, showers = true)
            }
            WeatherCondition.THUNDERSTORM -> {
                drawCloud(canvas, scale, palette.copy(cloud = 0xFFB9C6D0.toInt(), cloudDark = 0xFF60717D.toInt()), 0f, -5f, 1f, depth = 0.98f)
                drawBolt(canvas, scale, palette)
                drawLines(canvas, scale, palette.rain, listOf(
                    floatArrayOf(18f, 34f, 16f, 40f),
                    floatArrayOf(32f, 34f, 30f, 40f)
                ), 2.2f)
            }
            WeatherCondition.UNKNOWN, null -> drawUnknown(canvas, scale, palette.outline)
        }

        return bitmap
    }

    private fun drawSun(canvas: Canvas, scale: Float, palette: Palette, cx: Float, cy: Float, radius: Float) {
        fun f(v: Float) = v * scale
        val ray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.sun
            style = Paint.Style.STROKE
            strokeWidth = f(2.7f)
            strokeCap = Paint.Cap.ROUND
        }
        repeat(8) { i ->
            val a = Math.toRadians((i * 45).toDouble())
            val inner = radius + 3.1f
            val outer = radius + 6.1f
            canvas.drawLine(
                f(cx + cos(a).toFloat() * inner), f(cy + sin(a).toFloat() * inner),
                f(cx + cos(a).toFloat() * outer), f(cy + sin(a).toFloat() * outer), ray
            )
        }
        canvas.drawCircle(f(cx), f(cy), f(radius + 1.4f), fill(palette.sunEdgeAlpha))
        canvas.drawCircle(f(cx), f(cy), f(radius), fill(palette.sun))
        canvas.drawCircle(f(cx), f(cy), f(radius), stroke(palette.sunEdge, 1.5f * scale))
    }

    private fun drawCloud(
        canvas: Canvas,
        scale: Float,
        palette: Palette,
        offsetX: Float,
        offsetY: Float,
        factor: Float,
        depth: Float = 0.70f,
        alpha: Float = 1f
    ) {
        drawCloudLayer(canvas, scale, palette.outlineAlpha(alpha), offsetX, offsetY, factor * 1.075f)
        drawCloudLayer(canvas, scale, palette.cloudForDepth(depth, alpha), offsetX, offsetY, factor)
        drawCloudHighlight(canvas, scale, palette.cloudHighlight(alpha), 24f + offsetX, 24f + offsetY, factor)
        drawCloudUnderside(canvas, scale, palette.cloudUnderside(depth, alpha), 24f + offsetX, 24f + offsetY, factor)
    }

    private fun drawCloudLayer(
        canvas: Canvas,
        scale: Float,
        color: Int,
        offsetX: Float,
        offsetY: Float,
        factor: Float
    ) {
        fun f(v: Float) = v * scale
        val p = fill(color)
        val cx = 24f + offsetX
        val cy = 24f + offsetY
        canvas.drawCircle(f(cx - 7f * factor), f(cy + 1f * factor), f(6.8f * factor), p)
        canvas.drawCircle(f(cx + 1f * factor), f(cy - 3.2f * factor), f(9.0f * factor), p)
        canvas.drawCircle(f(cx + 9f * factor), f(cy + 1.8f * factor), f(6.2f * factor), p)
        canvas.drawRoundRect(
            RectF(f(cx - 13f * factor), f(cy + 0.5f * factor), f(cx + 15f * factor), f(cy + 9f * factor)),
            f(5f * factor), f(5f * factor), p
        )
    }

    private fun drawCloudHighlight(canvas: Canvas, scale: Float, color: Int, cx: Float, cy: Float, factor: Float) {
        fun f(v: Float) = v * scale
        canvas.drawCircle(f(cx - 7f * factor), f(cy - 7f * factor), f(5.2f * factor), fill(color))
    }

    private fun drawCloudUnderside(canvas: Canvas, scale: Float, color: Int, cx: Float, cy: Float, factor: Float) {
        fun f(v: Float) = v * scale
        canvas.drawRoundRect(
            RectF(f(cx - 8.5f * factor), f(cy + 7.2f * factor), f(cx + 10.5f * factor), f(cy + 10.2f * factor)),
            f(2.4f * factor), f(2.4f * factor), fill(color)
        )
    }

    private fun drawRain(
        canvas: Canvas,
        scale: Float,
        palette: Palette,
        light: Boolean,
        xShift: Float = 0f,
        fine: Boolean = false
    ) {
        val xs = if (light) floatArrayOf(18f, 29f) else floatArrayOf(15f, 24f, 33f)
        xs.forEachIndexed { i, x ->
            val y = 36f + (i % 2) * 1.4f
            val trail = if (fine) 5.6f else 7.8f
            val width = if (fine) 1.9f else if (light) 2.2f else 2.5f
            val lead = if (fine) palette.rainLight else adjustAlpha(palette.rainLight, 0.92f)
            val tail = if (fine) adjustAlpha(palette.rainLight, 0.62f) else adjustAlpha(palette.rainLight, 0.56f)
            drawLines(canvas, scale, tail, listOf(
                floatArrayOf(x + xShift - 1.9f, y - trail, x + xShift, y)
            ), width + 0.7f)
            drawLines(canvas, scale, lead, listOf(
                floatArrayOf(x + xShift - 1.6f, y - trail + 0.6f, x + xShift, y)
            ), width)
        }
    }

    private fun drawDrop(canvas: Canvas, scale: Float, cx: Float, cy: Float, size: Float, topColor: Int, bottomColor: Int) {
        fun f(v: Float) = v * scale
        val path = Path().apply {
            moveTo(f(cx), f(cy - size))
            cubicTo(f(cx + size * 0.12f), f(cy - size * 0.52f), f(cx + size * 0.62f), f(cy - size * 0.08f), f(cx + size * 0.58f), f(cy + size * 0.34f))
            cubicTo(f(cx + size * 0.54f), f(cy + size * 0.90f), f(cx + size * 0.22f), f(cy + size), f(cx), f(cy + size))
            cubicTo(f(cx - size * 0.22f), f(cy + size), f(cx - size * 0.54f), f(cy + size * 0.90f), f(cx - size * 0.58f), f(cy + size * 0.34f))
            cubicTo(f(cx - size * 0.62f), f(cy - size * 0.08f), f(cx - size * 0.12f), f(cy - size * 0.52f), f(cx), f(cy - size))
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                f(cx), f(cy - size), f(cx), f(cy + size),
                topColor, bottomColor, android.graphics.Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke(adjustAlpha(bottomColor, 0.28f), 0.75f * scale))
    }

    private fun drawLines(canvas: Canvas, scale: Float, color: Int, segments: List<FloatArray>, width: Float) {
        fun f(v: Float) = v * scale
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = f(width)
            strokeCap = Paint.Cap.ROUND
        }
        segments.forEach { s -> canvas.drawLine(f(s[0]), f(s[1]), f(s[2]), f(s[3]), p) }
    }

    private fun drawSnow(canvas: Canvas, scale: Float, palette: Palette, showers: Boolean) {
        val xs = if (showers) floatArrayOf(19f, 29f) else floatArrayOf(16f, 24f, 32f)
        xs.forEachIndexed { i, x ->
            drawSnowflake(
                canvas,
                scale,
                x + if (showers) 2f else 0f,
                37f + (i % 2) * 3f,
                2.7f,
                palette.snow
            )
        }
    }

    private fun drawSnowflake(canvas: Canvas, scale: Float, cx: Float, cy: Float, radius: Float, color: Int) {
        fun f(v: Float) = v * scale
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = f(1.7f)
            strokeCap = Paint.Cap.ROUND
        }
        repeat(3) { i ->
            val a = Math.toRadians((i * 60).toDouble())
            val dx = cos(a).toFloat() * radius
            val dy = sin(a).toFloat() * radius
            canvas.drawLine(f(cx - dx), f(cy - dy), f(cx + dx), f(cy + dy), p)

            val bx = cos(a).toFloat() * radius * 0.42f
            val by = sin(a).toFloat() * radius * 0.42f
            val left = a + 0.55
            val right = a - 0.55
            canvas.drawLine(f(cx + bx), f(cy + by), f(cx + bx - cos(left).toFloat() * radius * 0.18f), f(cy + by - sin(left).toFloat() * radius * 0.18f), p)
            canvas.drawLine(f(cx + bx), f(cy + by), f(cx + bx - cos(right).toFloat() * radius * 0.18f), f(cy + by - sin(right).toFloat() * radius * 0.18f), p)
        }
        canvas.drawCircle(f(cx), f(cy), f(0.9f), fill(0xCCFFFFFF.toInt()))
    }

    private fun drawBolt(canvas: Canvas, scale: Float, palette: Palette) {
        fun f(v: Float) = v * scale
        val path = Path().apply {
            moveTo(f(25f), f(28f)); lineTo(f(20f), f(36f)); lineTo(f(25f), f(36f))
            lineTo(f(22f), f(44f)); lineTo(f(33f), f(33f)); lineTo(f(27f), f(33f)); close()
        }
        canvas.drawPath(path, stroke(adjustAlpha(palette.sunEdge, 0.42f), 1.2f * scale))
        canvas.drawPath(path, fill(palette.lightning))
    }

    private fun drawUnknown(canvas: Canvas, scale: Float, color: Int) {
        fun f(v: Float) = v * scale
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = f(2.4f)
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(f(24f), f(24f), f(10f), p)
        canvas.drawLine(f(24f), f(17f), f(24f), f(26f), p)
        canvas.drawCircle(f(24f), f(31f), f(1.4f), fill(color))
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (((color ushr 24) and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        val fa = (from ushr 24) and 0xFF
        val fr = (from ushr 16) and 0xFF
        val fg = (from ushr 8) and 0xFF
        val fb = from and 0xFF
        val ta = (to ushr 24) and 0xFF
        val tr = (to ushr 16) and 0xFF
        val tg = (to ushr 8) and 0xFF
        val tb = to and 0xFF
        fun lerp(a: Int, b: Int) = (a + ((b - a) * t)).toInt().coerceIn(0, 255)
        return (lerp(fa, ta) shl 24) or (lerp(fr, tr) shl 16) or (lerp(fg, tg) shl 8) or lerp(fb, tb)
    }

    private fun paletteFor(condition: WeatherCondition?): Palette = when (condition) {
        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR,
        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.RAIN_SHOWERS,
        WeatherCondition.SNOW_SHOWERS -> Palette()

        WeatherCondition.OVERCAST,
        WeatherCondition.FOG -> Palette(
            cloud = 0xFFDCE6EE.toInt(), cloudDark = 0xFF708390.toInt(), fog = 0xFF9AAAB5.toInt()
        )

        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN,
        WeatherCondition.FREEZING_RAIN -> Palette(
            cloud = 0xFFE5EDF4.toInt(), cloudDark = 0xFF708390.toInt()
        )

        WeatherCondition.SNOW -> Palette(cloud = 0xFFF2F7FB.toInt(), snow = 0xFFF6FBFF.toInt())
        WeatherCondition.THUNDERSTORM -> Palette(cloud = 0xFFB9C6D0.toInt(), cloudDark = 0xFF60717D.toInt())
        WeatherCondition.UNKNOWN, null -> Palette()
    }

    private data class Palette(
        val sun: Int = 0xFFFFCA28.toInt(),
        val sunEdge: Int = 0xFFFFB300.toInt(),
        val cloud: Int = 0xFFF8FAFC.toInt(),
        val cloudDark: Int = 0xFF708390.toInt(),
        val rain: Int = 0xFF1976D2.toInt(),
        val rainLight: Int = 0xFF6EC6FF.toInt(),
        val snow: Int = 0xFFF6FBFF.toInt(),
        val freeze: Int = 0xFF5E5CE6.toInt(),
        val lightning: Int = 0xFFFFC107.toInt(),
        val fog: Int = 0xFF9AAAB5.toInt(),
        val outline: Int = 0xFF708390.toInt()
    ) {
        val sunEdgeAlpha: Int get() = WidgetWeatherIconRenderer.adjustAlpha(sunEdge, 0.18f)
        fun outlineAlpha(alpha: Float): Int = WidgetWeatherIconRenderer.adjustAlpha(WidgetWeatherIconRenderer.blend(cloudDark, cloud, 0.14f), 0.78f * alpha)
        fun cloudForDepth(depth: Float, alpha: Float): Int = WidgetWeatherIconRenderer.adjustAlpha(WidgetWeatherIconRenderer.blend(cloud, 0xFFDCE6EE.toInt(), depth * 0.52f), alpha)
        fun cloudHighlight(alpha: Float): Int = WidgetWeatherIconRenderer.adjustAlpha(WidgetWeatherIconRenderer.blend(cloud, 0xFFFFFFFF.toInt(), 0.24f), 0.14f * alpha)
        fun cloudUnderside(depth: Float, alpha: Float): Int = WidgetWeatherIconRenderer.adjustAlpha(WidgetWeatherIconRenderer.blend(cloud, cloudDark, 0.15f + depth * 0.09f), 0.32f * alpha)
    }
}
