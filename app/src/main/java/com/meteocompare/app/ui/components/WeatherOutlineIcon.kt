package com.meteocompare.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Variante outline des icônes météo, pensée pour les vues très compactes
 * (notamment la mini-timeline). Aucun aplat : uniquement des traits arrondis,
 * lisibles aussi bien en noir qu'en blanc.
 */
@Composable
fun WeatherOutlineIconDecorative(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    backdropColor: Color = Color.Transparent
) {
    if (condition == null) return
    val color = if (tint == Color.Unspecified) Color(0xFF64727C) else tint

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                // BlendMode.Clear doit s'appliquer dans une couche isolée :
                // sinon certains appareils rendent la zone effacée en noir.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .clearAndSetSemantics { }
    ) {
        val unit = min(this.size.width, this.size.height) / 100f
        val origin = Offset(
            (this.size.width - 100f * unit) / 2f,
            (this.size.height - 100f * unit) / 2f
        )
        withTransform({
            translate(left = origin.x, top = origin.y)
        }) {
            drawOutlineCondition(condition, color, backdropColor, unit)
        }
    }
}

private fun DrawScope.drawOutlineCondition(
    condition: WeatherCondition,
    color: Color,
    backdropColor: Color,
    unit: Float
) {
    val mainStroke = 5.2f * unit
    val lightStroke = 4.2f * unit

    when (condition) {
        WeatherCondition.CLEAR -> drawOutlineSun(pointO(50f, 50f, unit), 17f * unit, color, unit)

        WeatherCondition.MAINLY_CLEAR -> {
            drawOutlineSun(pointO(36f, 35f, unit), 12f * unit, color, unit)
            drawOutlineCloudMasked(pointO(59f, 61f, unit), 0.82f, color, backdropColor, unit, mainStroke)
        }

        WeatherCondition.PARTLY_CLOUDY -> {
            drawOutlineSun(pointO(33f, 34f, unit), 11f * unit, color, unit)
            drawOutlineCloudMasked(pointO(59f, 59f, unit), 0.98f, color, backdropColor, unit, mainStroke)
        }

        WeatherCondition.OVERCAST -> {
            // Le nuage avant masque réellement le nuage arrière.
            drawOutlineCloud(pointO(37f, 40f, unit), 0.70f, color, unit, lightStroke)
            drawOutlineCloudMasked(pointO(61f, 61f, unit), 1.02f, color, backdropColor, unit, mainStroke)
        }

        WeatherCondition.FOG -> {
            drawOutlineCloudMasked(pointO(50f, 39f, unit), 0.80f, color, backdropColor, unit, lightStroke)
            drawOutlineLine(22f, 63f, 78f, 63f, color, lightStroke, unit)
            drawOutlineLine(29f, 72f, 72f, 72f, color, lightStroke, unit)
            drawOutlineLine(20f, 81f, 76f, 81f, color, lightStroke, unit)
        }

        WeatherCondition.DRIZZLE -> {
            drawOutlineCloudMasked(pointO(50f, 39f, unit), 0.92f, color, backdropColor, unit, mainStroke)
            drawOutlineRain(listOf(35f, 50f, 65f), 60f, 77f, color, 3.4f * unit, unit)
        }

        WeatherCondition.RAIN -> {
            drawOutlineCloudMasked(pointO(50f, 38f, unit), 0.98f, color, backdropColor, unit, mainStroke)
            drawOutlineRain(listOf(31f, 43f, 57f, 69f), 58f, 82f, color, 4.5f * unit, unit)
        }

        WeatherCondition.RAIN_SHOWERS -> {
            drawOutlineSun(pointO(31f, 31f, unit), 8f * unit, color, unit)
            drawOutlineCloudMasked(pointO(55f, 40f, unit), 0.94f, color, backdropColor, unit, mainStroke)
            drawOutlineRain(listOf(38f, 53f, 68f), 59f, 82f, color, 4.2f * unit, unit)
        }

        WeatherCondition.FREEZING_RAIN -> {
            drawOutlineCloudMasked(pointO(50f, 37f, unit), 0.98f, color, backdropColor, unit, mainStroke)
            drawOutlineRain(listOf(34f, 50f, 66f), 57f, 76f, color, 4.0f * unit, unit)
            drawOutlineSnowflake(pointO(53f, 83f, unit), 8f * unit, color, 3.2f * unit)
        }

        WeatherCondition.SNOW -> {
            drawOutlineCloudMasked(pointO(50f, 37f, unit), 0.96f, color, backdropColor, unit, mainStroke)
            drawOutlineSnowflake(pointO(33f, 70f, unit), 6f * unit, color, 3f * unit)
            drawOutlineSnowflake(pointO(51f, 78f, unit), 6.5f * unit, color, 3f * unit)
            drawOutlineSnowflake(pointO(69f, 69f, unit), 5.5f * unit, color, 3f * unit)
        }

        WeatherCondition.SNOW_SHOWERS -> {
            drawOutlineSun(pointO(31f, 31f, unit), 8f * unit, color, unit)
            drawOutlineCloudMasked(pointO(55f, 39f, unit), 0.94f, color, backdropColor, unit, mainStroke)
            drawOutlineSnowflake(pointO(43f, 70f, unit), 5.5f * unit, color, 2.8f * unit)
            drawOutlineSnowflake(pointO(64f, 78f, unit), 6f * unit, color, 2.8f * unit)
        }

        WeatherCondition.THUNDERSTORM -> {
            drawOutlineCloudMasked(pointO(50f, 37f, unit), 1.0f, color, backdropColor, unit, mainStroke)
            drawOutlineLightning(pointO(51f, 72f, unit), color, 4.2f * unit, unit)
            drawOutlineRain(listOf(30f, 72f), 58f, 80f, color, 3.5f * unit, unit)
        }

        WeatherCondition.UNKNOWN -> {
            drawCircle(
                color = color,
                radius = 24f * unit,
                center = pointO(50f, 50f, unit),
                style = Stroke(width = mainStroke)
            )
            drawArc(
                color = color,
                startAngle = 205f,
                sweepAngle = 235f,
                useCenter = false,
                topLeft = pointO(41f, 37f, unit),
                size = androidx.compose.ui.geometry.Size(18f * unit, 18f * unit),
                style = Stroke(width = mainStroke, cap = StrokeCap.Round)
            )
            drawLine(
                color = color,
                start = pointO(50f, 54f, unit),
                end = pointO(50f, 61f, unit),
                strokeWidth = mainStroke,
                cap = StrokeCap.Round
            )
            drawCircle(color = color, radius = 2.8f * unit, center = pointO(50f, 69f, unit))
        }
    }
}

private fun DrawScope.drawOutlineSun(
    center: Offset,
    radius: Float,
    color: Color,
    unit: Float
) {
    val stroke = 4.4f * unit
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = stroke)
    )
    repeat(8) { index ->
        val angle = (2.0 * PI * index / 8.0).toFloat()
        drawLine(
            color = color,
            start = center + polarO(radius + 6f * unit, angle),
            end = center + polarO(radius + 12f * unit, angle),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawOutlineCloud(
    center: Offset,
    scale: Float,
    color: Color,
    unit: Float,
    strokeWidth: Float
) {
    val path = outlineCloudPath(center, scale, unit)
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawOutlineCloudMasked(
    center: Offset,
    scale: Float,
    color: Color,
    backdropColor: Color,
    unit: Float,
    strokeWidth: Float
) {
    val path = outlineCloudPath(center, scale, unit)
    // Vrai masquage : on efface ce qui a déjà été dessiné derrière le nuage
    // de premier plan, au lieu de repeindre par-dessus avec une couleur qui
    // pourrait rester translucide selon le fond.
    drawPath(
        path = path,
        color = Color.Transparent,
        blendMode = BlendMode.Clear
    )
    drawPath(
        path = path,
        color = Color.Transparent,
        style = Stroke(
            width = strokeWidth + 3.4f * unit,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = BlendMode.Clear
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun outlineCloudPath(
    center: Offset,
    scale: Float,
    unit: Float
): Path {
    val sx = unit * scale
    return Path().apply {
        moveTo(center.x - 28f * sx, center.y + 10f * sx)
        cubicTo(
            center.x - 30f * sx, center.y - 2f * sx,
            center.x - 23f * sx, center.y - 11f * sx,
            center.x - 13f * sx, center.y - 12f * sx
        )
        cubicTo(
            center.x - 9f * sx, center.y - 25f * sx,
            center.x + 3f * sx, center.y - 29f * sx,
            center.x + 13f * sx, center.y - 21f * sx
        )
        cubicTo(
            center.x + 19f * sx, center.y - 17f * sx,
            center.x + 21f * sx, center.y - 10f * sx,
            center.x + 21f * sx, center.y - 5f * sx
        )
        cubicTo(
            center.x + 33f * sx, center.y - 5f * sx,
            center.x + 38f * sx, center.y + 4f * sx,
            center.x + 34f * sx, center.y + 13f * sx
        )
        cubicTo(
            center.x + 32f * sx, center.y + 19f * sx,
            center.x + 25f * sx, center.y + 22f * sx,
            center.x + 17f * sx, center.y + 22f * sx
        )
        lineTo(center.x - 17f * sx, center.y + 22f * sx)
        cubicTo(
            center.x - 24f * sx, center.y + 22f * sx,
            center.x - 28f * sx, center.y + 18f * sx,
            center.x - 28f * sx, center.y + 10f * sx
        )
        close()
    }
}

private fun DrawScope.drawOutlineRain(
    xs: List<Float>,
    startY: Float,
    endY: Float,
    color: Color,
    strokeWidth: Float,
    unit: Float
) {
    xs.forEachIndexed { index, x ->
        val offset = if (index % 2 == 0) 0f else 3f
        drawLine(
            color = color,
            start = pointO(x + 2f, startY + offset, unit),
            end = pointO(x - 2f, endY + offset, unit),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawOutlineSnowflake(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float
) {
    repeat(3) { index ->
        val angle = (index * PI / 3.0).toFloat()
        val v = polarO(radius, angle)
        drawLine(
            color = color,
            start = center - v,
            end = center + v,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawOutlineLightning(
    center: Offset,
    color: Color,
    strokeWidth: Float,
    unit: Float
) {
    val path = Path().apply {
        moveTo(center.x - 4f * unit, center.y - 14f * unit)
        lineTo(center.x + 7f * unit, center.y - 14f * unit)
        lineTo(center.x + 1f * unit, center.y - 3f * unit)
        lineTo(center.x + 8f * unit, center.y - 3f * unit)
        lineTo(center.x - 8f * unit, center.y + 16f * unit)
        lineTo(center.x - 2f * unit, center.y + 4f * unit)
        lineTo(center.x - 9f * unit, center.y + 4f * unit)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawOutlineLine(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    color: Color,
    strokeWidth: Float,
    unit: Float
) {
    drawLine(
        color = color,
        start = pointO(x1, y1, unit),
        end = pointO(x2, y2, unit),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun pointO(x: Float, y: Float, unit: Float) = Offset(x * unit, y * unit)

private fun polarO(radius: Float, angle: Float) = Offset(
    x = cos(angle) * radius,
    y = sin(angle) * radius
)
