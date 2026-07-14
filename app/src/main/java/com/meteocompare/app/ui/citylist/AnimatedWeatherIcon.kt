package com.meteocompare.app.ui.citylist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Icône météo multicolore et animée, dessinée directement avec Compose Canvas.
 *
 * Principes :
 * - une seule valeur animée par icône ;
 * - lecture de l'état dans la phase de dessin ;
 * - aucun déplacement de layout à chaque frame ;
 * - soleil, nuages, précipitations, brouillard et éclair animés séparément ;
 * - phases désynchronisées grâce à [animationSeed].
 *
 * L'icône est décorative : le libellé météo doit être fourni par le composable
 * parent pour éviter une annonce TalkBack redondante.
 */
@Composable
internal fun AnimatedWeatherIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animated: Boolean = true,
    animateConditionChanges: Boolean = true,
    animationSeed: Int = 0,
    motionScale: Float = 1f,
    tint: Color = Color.Unspecified,
    palette: WeatherIconPalette = WeatherIconDefaults.palette
) {
    val displayedCondition = condition ?: WeatherCondition.UNKNOWN
    val effectivePalette = if (tint == Color.Unspecified) {
        palette
    } else {
        palette.monochrome(tint)
    }

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { }
    ) {
        if (animateConditionChanges) {
            AnimatedContent(
                targetState = displayedCondition,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val enter = fadeIn(tween(220)) +
                        scaleIn(
                            initialScale = 0.82f,
                            animationSpec = tween(280)
                        )
                    val exit = fadeOut(tween(150)) +
                        scaleOut(
                            targetScale = 1.08f,
                            animationSpec = tween(180)
                        )

                    enter.togetherWith(exit)
                },
                label = "weather-condition-transition"
            ) { targetCondition ->
                WeatherIconCanvas(
                    condition = targetCondition,
                    palette = effectivePalette,
                    animated = animated,
                    animationSeed = animationSeed,
                    motionScale = motionScale.coerceIn(0f, 2f),
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            WeatherIconCanvas(
                condition = displayedCondition,
                palette = effectivePalette,
                animated = animated,
                animationSeed = animationSeed,
                motionScale = motionScale.coerceIn(0f, 2f),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun WeatherIconCanvas(
    condition: WeatherCondition,
    palette: WeatherIconPalette,
    animated: Boolean,
    animationSeed: Int,
    motionScale: Float,
    modifier: Modifier
) {
    key(condition, animationSeed, animated) {
        val cycleMillis = condition.animationCycleMillis
        val phaseFraction = positiveMod(animationSeed, cycleMillis) / cycleMillis.toFloat()
        val progressState: State<Float>? = if (animated && motionScale > 0f) {
            val transition = rememberInfiniteTransition(
                label = "weather-canvas-$condition"
            )
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = cycleMillis,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "weather-progress-$condition"
            )
        } else {
            null
        }
        val staticProgress = condition.staticPreviewProgress

        Canvas(modifier = modifier) {
            // La lecture de State est volontairement faite dans la phase de dessin.
            val progress = progressState?.let { fract(it.value + phaseFraction) }
                ?: staticProgress
            val unit = min(size.width, size.height) / 100f
            val origin = Offset(
                x = (size.width - 100f * unit) / 2f,
                y = (size.height - 100f * unit) / 2f
            )

            withTransform({
                translate(left = origin.x, top = origin.y)
            }) {
                drawWeatherCondition(
                    condition = condition,
                    palette = palette,
                    progress = progress,
                    motionScale = motionScale,
                    unit = unit
                )
            }
        }
    }
}

@Immutable
data class WeatherIconPalette(
    val sunCore: Color,
    val sunEdge: Color,
    val sunRay: Color,
    val cloudLight: Color,
    val cloudMid: Color,
    val cloudDark: Color,
    val rainLight: Color,
    val rainDark: Color,
    val snow: Color,
    val snowShadow: Color,
    val lightning: Color,
    val fog: Color,
    val alert: Color,
    val glow: Color,
    val shadow: Color
) {
    fun monochrome(tint: Color): WeatherIconPalette = copy(
        sunCore = tint,
        sunEdge = tint.copy(alpha = 0.88f),
        sunRay = tint.copy(alpha = 0.76f),
        cloudLight = tint.copy(alpha = 0.92f),
        cloudMid = tint.copy(alpha = 0.80f),
        cloudDark = tint.copy(alpha = 0.68f),
        rainLight = tint.copy(alpha = 0.84f),
        rainDark = tint.copy(alpha = 0.70f),
        snow = tint.copy(alpha = 0.96f),
        snowShadow = tint.copy(alpha = 0.56f),
        lightning = tint,
        fog = tint.copy(alpha = 0.68f),
        alert = tint,
        glow = tint.copy(alpha = 0.30f),
        shadow = tint.copy(alpha = 0.18f)
    )
}

object WeatherIconDefaults {
    val palette = WeatherIconPalette(
        sunCore = Color(0xFFFFD54F),
        sunEdge = Color(0xFFFFA726),
        sunRay = Color(0xFFFFC247),
        cloudLight = Color(0xFFF7FAFF),
        cloudMid = Color(0xFFD9E4F2),
        cloudDark = Color(0xFF9AAEC4),
        rainLight = Color(0xFF4FC3F7),
        rainDark = Color(0xFF1976D2),
        snow = Color(0xFFF5FBFF),
        snowShadow = Color(0xFF90CAF9),
        lightning = Color(0xFFFFF176),
        fog = Color(0xFFB0BEC5),
        alert = Color(0xFF80DEEA),
        glow = Color(0xFF90CAF9),
        shadow = Color(0xFF23354D)
    )
}

private val WeatherCondition.animationCycleMillis: Int
    get() = when (this) {
        WeatherCondition.CLEAR -> 12_000
        WeatherCondition.MAINLY_CLEAR -> 10_000
        WeatherCondition.PARTLY_CLOUDY -> 8_000
        WeatherCondition.OVERCAST -> 9_000
        WeatherCondition.FOG -> 8_000
        WeatherCondition.DRIZZLE -> 3_600
        WeatherCondition.RAIN -> 3_000
        WeatherCondition.RAIN_SHOWERS -> 3_400
        WeatherCondition.FREEZING_RAIN -> 4_000
        WeatherCondition.SNOW -> 5_400
        WeatherCondition.SNOW_SHOWERS -> 5_000
        WeatherCondition.THUNDERSTORM -> 6_000
        WeatherCondition.UNKNOWN -> 7_000
    }

private val WeatherCondition.staticPreviewProgress: Float
    get() = when (this) {
        WeatherCondition.CLEAR -> 0.12f
        WeatherCondition.MAINLY_CLEAR -> 0.22f
        WeatherCondition.PARTLY_CLOUDY -> 0.36f
        WeatherCondition.OVERCAST -> 0.42f
        WeatherCondition.FOG -> 0.52f
        WeatherCondition.DRIZZLE -> 0.18f
        WeatherCondition.RAIN -> 0.32f
        WeatherCondition.RAIN_SHOWERS -> 0.48f
        WeatherCondition.FREEZING_RAIN -> 0.58f
        WeatherCondition.SNOW -> 0.27f
        WeatherCondition.SNOW_SHOWERS -> 0.44f
        WeatherCondition.THUNDERSTORM -> 0.63f
        WeatherCondition.UNKNOWN -> 0.20f
    }

private fun DrawScope.drawWeatherCondition(
    condition: WeatherCondition,
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    when (condition) {
        WeatherCondition.CLEAR -> drawClear(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.MAINLY_CLEAR -> drawMainlyClear(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.PARTLY_CLOUDY -> drawPartlyCloudy(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.OVERCAST -> drawOvercast(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.FOG -> drawFog(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.DRIZZLE -> drawDrizzle(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.RAIN -> drawRain(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.RAIN_SHOWERS -> drawRainShowers(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.FREEZING_RAIN -> drawFreezingRain(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.SNOW -> drawSnow(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.SNOW_SHOWERS -> drawSnowShowers(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.THUNDERSTORM -> drawThunderstorm(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )

        WeatherCondition.UNKNOWN -> drawUnknown(
            palette = palette,
            progress = progress,
            motionScale = motionScale,
            unit = unit
        )
    }
}

private fun DrawScope.drawClear(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val breathe = 1f + 0.035f * wave(progress, cycles = 2f) * motionScale
    val rotation = 360f * progress

    drawGlow(
        center = point(50f, 50f, unit),
        radius = 40f * unit * breathe,
        color = palette.sunRay,
        alpha = 0.20f + 0.04f * wave01(progress, cycles = 2f)
    )

    drawSun(
        center = point(50f, 50f, unit),
        radius = 18f * unit * breathe,
        rayInner = 28f * unit,
        rayOuter = 39f * unit,
        rotation = rotation,
        palette = palette,
        unit = unit
    )
}

private fun DrawScope.drawMainlyClear(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val sunCenter = point(39f, 39f, unit)
    val cloudDx = wave(progress, cycles = 1f) * 1.8f * unit * motionScale
    val cloudDy = wave(progress, cycles = 2f, phase = 0.2f) * 0.6f * unit * motionScale

    drawGlow(
        center = sunCenter,
        radius = 31f * unit,
        color = palette.sunRay,
        alpha = 0.16f
    )

    drawSun(
        center = sunCenter,
        radius = 14f * unit,
        rayInner = 21f * unit,
        rayOuter = 29f * unit,
        rotation = 360f * progress,
        palette = palette,
        unit = unit
    )

    drawCloud(
        center = point(58f, 61f, unit) + Offset(cloudDx, cloudDy),
        scale = 0.86f,
        palette = palette,
        unit = unit,
        depth = 0.80f
    )
}

private fun DrawScope.drawPartlyCloudy(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val sunCenter = point(34f, 37f, unit)
    val foregroundDx = wave(progress, cycles = 1f) * 2.5f * unit * motionScale
    val foregroundDy = wave(progress, cycles = 2f, phase = 0.15f) * 0.7f * unit * motionScale

    drawGlow(
        center = sunCenter,
        radius = 27f * unit,
        color = palette.sunRay,
        alpha = 0.13f
    )
    drawSun(
        center = sunCenter,
        radius = 12f * unit,
        rayInner = 19f * unit,
        rayOuter = 26f * unit,
        rotation = 360f * progress,
        palette = palette,
        unit = unit
    )

    drawCloud(
        center = point(58f, 59f, unit) + Offset(foregroundDx, foregroundDy),
        scale = 1.02f,
        palette = palette,
        unit = unit,
        depth = 0.70f
    )
}

private fun DrawScope.drawOvercast(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val backDx = wave(progress, cycles = 1f, phase = 0.1f) * 1.4f * unit * motionScale
    val frontDx = wave(progress, cycles = 1f, phase = 0.55f) * 2.2f * unit * motionScale

    drawGlow(
        center = point(50f, 54f, unit),
        radius = 39f * unit,
        color = palette.cloudMid,
        alpha = 0.08f
    )

    drawCloud(
        center = point(42f, 47f, unit) + Offset(backDx, 0f),
        scale = 0.83f,
        palette = palette,
        unit = unit,
        depth = 0.25f,
        alpha = 0.88f
    )
    drawCloud(
        center = point(57f, 59f, unit) + Offset(frontDx, 0f),
        scale = 1.06f,
        palette = palette,
        unit = unit,
        depth = 0.86f
    )
}

private fun DrawScope.drawFog(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val cloudDx = wave(progress, cycles = 1f) * 1.2f * unit * motionScale
    drawCloud(
        center = point(50f, 42f, unit) + Offset(cloudDx, 0f),
        scale = 0.82f,
        palette = palette,
        unit = unit,
        depth = 0.38f,
        alpha = 0.82f
    )

    val fogLines = listOf(
        FogLine(y = 61f, x = 22f, width = 56f, phase = 0.00f),
        FogLine(y = 70f, x = 29f, width = 52f, phase = 0.38f),
        FogLine(y = 79f, x = 19f, width = 58f, phase = 0.72f)
    )

    fogLines.forEachIndexed { index, line ->
        val direction = if (index % 2 == 0) 1f else -1f
        val dx = wave(progress, cycles = 1f, phase = line.phase) *
            5f * unit * direction * motionScale
        drawLine(
            color = palette.fog.copy(alpha = 0.70f - index * 0.10f),
            start = point(line.x, line.y, unit) + Offset(dx, 0f),
            end = point(line.x + line.width, line.y, unit) + Offset(dx, 0f),
            strokeWidth = 5f * unit,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawDrizzle(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val cloudDx = wave(progress, cycles = 1f) * 1.4f * unit * motionScale
    drawCloud(
        center = point(50f, 42f, unit) + Offset(cloudDx, 0f),
        scale = 0.96f,
        palette = palette,
        unit = unit,
        depth = 0.66f
    )

    listOf(33f, 50f, 67f).forEachIndexed { index, x ->
        val local = fract(progress * 2f + index * 0.29f)
        val y = lerp(59f, 80f, local)
        val alpha = precipitationAlpha(local)
        val length = lerp(2.8f, 5.5f, local)
        drawLine(
            color = palette.rainLight.copy(alpha = alpha * 0.85f),
            start = point(x, y, unit),
            end = point(x - 1.5f, y + length, unit),
            strokeWidth = 2.8f * unit,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawRain(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val cloudDx = wave(progress, cycles = 1f) * 1.2f * unit * motionScale
    val cloudDy = wave(progress, cycles = 2f, phase = 0.2f) * 0.45f * unit * motionScale

    drawGlow(
        center = point(50f, 57f, unit),
        radius = 37f * unit,
        color = palette.rainLight,
        alpha = 0.08f
    )
    drawCloud(
        center = point(50f, 39f, unit) + Offset(cloudDx, cloudDy),
        scale = 1.00f,
        palette = palette,
        unit = unit,
        depth = 0.76f
    )

    drawRainDrops(
        progress = progress,
        palette = palette,
        unit = unit,
        intensity = 1f,
        motionScale = motionScale
    )
}

private fun DrawScope.drawRainShowers(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val sunCenter = point(31f, 34f, unit)
    drawSun(
        center = sunCenter,
        radius = 9f * unit,
        rayInner = 15f * unit,
        rayOuter = 21f * unit,
        rotation = 360f * progress,
        palette = palette,
        unit = unit,
        rayCount = 8
    )

    val cloudDx = wave(progress, cycles = 1f) * 2f * unit * motionScale
    drawCloud(
        center = point(54f, 42f, unit) + Offset(cloudDx, 0f),
        scale = 0.98f,
        palette = palette,
        unit = unit,
        depth = 0.73f
    )

    drawRainDrops(
        progress = progress,
        palette = palette,
        unit = unit,
        intensity = 1.18f,
        motionScale = motionScale,
        xOffset = 3f
    )
}

private fun DrawScope.drawFreezingRain(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val cloudDx = wave(progress, cycles = 1f) * 1f * unit * motionScale
    drawGlow(
        center = point(50f, 58f, unit),
        radius = 38f * unit,
        color = palette.alert,
        alpha = 0.12f
    )
    drawCloud(
        center = point(50f, 39f, unit) + Offset(cloudDx, 0f),
        scale = 1f,
        palette = palette,
        unit = unit,
        depth = 0.82f
    )

    listOf(36f, 52f, 68f).forEachIndexed { index, x ->
        val local = fract(progress * 2f + index * 0.31f)
        val y = lerp(58f, 76f, local)
        val alpha = precipitationAlpha(local)
        drawDrop(
            center = point(x, y, unit),
            size = 4.5f * unit,
            colorTop = palette.rainLight.copy(alpha = alpha),
            colorBottom = palette.rainDark.copy(alpha = alpha)
        )
    }

    val sparklePulse = 0.75f + 0.25f * wave01(progress, cycles = 2f)
    drawSnowflake(
        center = point(52f, 81f, unit),
        radius = 9f * unit * (0.92f + 0.08f * sparklePulse * motionScale),
        color = palette.alert.copy(alpha = sparklePulse),
        strokeWidth = 2.2f * unit,
        rotation = 120f * progress
    )
}

private fun DrawScope.drawSnow(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val cloudDx = wave(progress, cycles = 1f) * 1.2f * unit * motionScale
    drawCloud(
        center = point(50f, 38f, unit) + Offset(cloudDx, 0f),
        scale = 0.98f,
        palette = palette,
        unit = unit,
        depth = 0.62f
    )

    drawSnowflakes(
        progress = progress,
        palette = palette,
        unit = unit,
        motionScale = motionScale
    )
}

private fun DrawScope.drawSnowShowers(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    drawSun(
        center = point(31f, 34f, unit),
        radius = 9f * unit,
        rayInner = 15f * unit,
        rayOuter = 21f * unit,
        rotation = 360f * progress,
        palette = palette,
        unit = unit
    )

    val cloudDx = wave(progress, cycles = 1f) * 1.8f * unit * motionScale
    drawCloud(
        center = point(55f, 41f, unit) + Offset(cloudDx, 0f),
        scale = 0.98f,
        palette = palette,
        unit = unit,
        depth = 0.68f
    )

    drawSnowflakes(
        progress = progress,
        palette = palette,
        unit = unit,
        motionScale = motionScale,
        xOffset = 3f
    )
}

private fun DrawScope.drawThunderstorm(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val cloudDx = wave(progress, cycles = 1f) * 1.2f * unit * motionScale
    val flash = lightningFlash(progress)
    val shake = if (flash > 0.15f) {
        wave(progress, cycles = 34f) * 1.3f * unit * flash * motionScale
    } else {
        0f
    }

    drawGlow(
        center = point(52f, 59f, unit),
        radius = (30f + 8f * flash) * unit,
        color = palette.lightning,
        alpha = 0.05f + 0.26f * flash
    )

    drawCloud(
        center = point(50f, 38f, unit) + Offset(cloudDx + shake, 0f),
        scale = 1.03f,
        palette = palette,
        unit = unit,
        depth = 0.96f
    )

    drawLightning(
        center = point(51f, 68f, unit) + Offset(shake * 0.6f, 0f),
        scale = 1f + 0.10f * flash,
        color = palette.lightning.copy(alpha = 0.72f + 0.28f * flash),
        shadow = palette.sunEdge.copy(alpha = 0.38f + 0.25f * flash),
        unit = unit
    )

    // Quelques gouttes discrètes pour distinguer l'orage d'un simple nuage.
    listOf(31f, 72f).forEachIndexed { index, x ->
        val local = fract(progress * 2f + index * 0.43f)
        val y = lerp(59f, 80f, local)
        drawLine(
            color = palette.rainDark.copy(alpha = precipitationAlpha(local) * 0.70f),
            start = point(x, y, unit),
            end = point(x - 2f, y + 7f, unit),
            strokeWidth = 2.6f * unit,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawUnknown(
    palette: WeatherIconPalette,
    progress: Float,
    motionScale: Float,
    unit: Float
) {
    val floatY = wave(progress, cycles = 1f) * 1.2f * unit * motionScale
    val center = point(50f, 49f, unit) + Offset(0f, floatY)

    drawGlow(
        center = center,
        radius = 34f * unit,
        color = palette.cloudMid,
        alpha = 0.10f
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.cloudLight,
                palette.cloudMid
            ),
            center = center - Offset(6f * unit, 7f * unit),
            radius = 29f * unit
        ),
        radius = 24f * unit,
        center = center
    )
    drawCircle(
        color = palette.shadow.copy(alpha = 0.18f),
        radius = 24f * unit,
        center = center,
        style = Stroke(width = 2f * unit)
    )

    // Symbole d'incertitude abstrait, sans dépendance au rendu de texte.
    drawArc(
        color = palette.cloudDark,
        startAngle = 205f,
        sweepAngle = 235f,
        useCenter = false,
        topLeft = center - Offset(9f * unit, 13f * unit),
        size = Size(18f * unit, 18f * unit),
        style = Stroke(width = 5f * unit, cap = StrokeCap.Round)
    )
    drawLine(
        color = palette.cloudDark,
        start = center + Offset(0f, 4f * unit),
        end = center + Offset(0f, 10f * unit),
        strokeWidth = 5f * unit,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = palette.cloudDark,
        radius = 2.7f * unit,
        center = center + Offset(0f, 17f * unit)
    )
}

private fun DrawScope.drawSun(
    center: Offset,
    radius: Float,
    rayInner: Float,
    rayOuter: Float,
    rotation: Float,
    palette: WeatherIconPalette,
    unit: Float,
    rayCount: Int = 10
) {
    rotate(degrees = rotation, pivot = center) {
        repeat(rayCount) { index ->
            val angle = (2.0 * PI * index / rayCount).toFloat()
            val start = center + polar(rayInner, angle)
            val end = center + polar(rayOuter, angle)
            drawLine(
                color = palette.sunRay.copy(alpha = 0.86f),
                start = start,
                end = end,
                strokeWidth = 4f * unit,
                cap = StrokeCap.Round
            )
        }
    }

    drawCircle(
        color = palette.shadow.copy(alpha = 0.13f),
        radius = radius * 1.03f,
        center = center + Offset(0f, 2.1f * unit)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.sunCore,
                palette.sunEdge
            ),
            center = center - Offset(radius * 0.25f, radius * 0.28f),
            radius = radius * 1.25f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.23f),
        radius = radius * 0.22f,
        center = center - Offset(radius * 0.32f, radius * 0.34f)
    )
}

private fun DrawScope.drawCloud(
    center: Offset,
    scale: Float,
    palette: WeatherIconPalette,
    unit: Float,
    depth: Float,
    alpha: Float = 1f
) {
    val bodyWidth = 58f * unit * scale
    val bodyHeight = 24f * unit * scale
    val bodyTopLeft = Offset(
        x = center.x - bodyWidth / 2f,
        y = center.y - bodyHeight / 2f + 6f * unit * scale
    )
    val shadowOffset = Offset(0f, 3.0f * unit * scale)
    val shadowColor = palette.shadow.copy(alpha = 0.13f * alpha)

    drawCloudShape(
        center = center + shadowOffset,
        scale = scale,
        bodyTopLeft = bodyTopLeft + shadowOffset,
        bodyWidth = bodyWidth,
        bodyHeight = bodyHeight,
        brush = SolidColor(shadowColor),
        unit = unit
    )

    val light = blend(palette.cloudLight, palette.cloudMid, depth * 0.35f).copy(alpha = alpha)
    val dark = blend(palette.cloudMid, palette.cloudDark, depth * 0.72f).copy(alpha = alpha)
    val cloudBrush = Brush.linearGradient(
        colors = listOf(light, dark),
        start = center - Offset(18f * unit * scale, 20f * unit * scale),
        end = center + Offset(18f * unit * scale, 24f * unit * scale)
    )

    drawCloudShape(
        center = center,
        scale = scale,
        bodyTopLeft = bodyTopLeft,
        bodyWidth = bodyWidth,
        bodyHeight = bodyHeight,
        brush = cloudBrush,
        unit = unit
    )

    drawCircle(
        color = Color.White.copy(alpha = 0.13f * alpha),
        radius = 5.4f * unit * scale,
        center = center + Offset(-12f * unit * scale, -8f * unit * scale)
    )
}

private fun DrawScope.drawCloudShape(
    center: Offset,
    scale: Float,
    bodyTopLeft: Offset,
    bodyWidth: Float,
    bodyHeight: Float,
    brush: Brush,
    unit: Float
) {
    drawRoundRect(
        brush = brush,
        topLeft = bodyTopLeft,
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(12f * unit * scale)
    )
    drawCircle(
        brush = brush,
        radius = 14f * unit * scale,
        center = center + Offset(-15f * unit * scale, -2f * unit * scale)
    )
    drawCircle(
        brush = brush,
        radius = 18f * unit * scale,
        center = center + Offset(1f * unit * scale, -9f * unit * scale)
    )
    drawCircle(
        brush = brush,
        radius = 12.5f * unit * scale,
        center = center + Offset(18f * unit * scale, 0f)
    )
}

private fun DrawScope.drawRainDrops(
    progress: Float,
    palette: WeatherIconPalette,
    unit: Float,
    intensity: Float,
    motionScale: Float,
    xOffset: Float = 0f
) {
    val specs = listOf(
        RainSpec(x = 29f + xOffset, phase = 0.00f, speed = 2f, size = 4.6f),
        RainSpec(x = 43f + xOffset, phase = 0.24f, speed = 2f, size = 5.2f),
        RainSpec(x = 58f + xOffset, phase = 0.48f, speed = 3f, size = 4.4f),
        RainSpec(x = 72f + xOffset, phase = 0.72f, speed = 2f, size = 5.0f)
    )

    specs.forEachIndexed { index, spec ->
        val local = fract(progress * spec.speed + spec.phase)
        val y = lerp(57f, 83f, local)
        val drift = sin((local + index * 0.17f) * PI.toFloat()) *
            1.6f * unit * motionScale
        val alpha = precipitationAlpha(local)
        val stretch = lerp(0.85f, 1.28f, local) * intensity

        drawDrop(
            center = point(spec.x, y, unit) + Offset(drift, 0f),
            size = spec.size * unit * stretch,
            colorTop = palette.rainLight.copy(alpha = alpha),
            colorBottom = palette.rainDark.copy(alpha = alpha)
        )
    }
}

private fun DrawScope.drawDrop(
    center: Offset,
    size: Float,
    colorTop: Color,
    colorBottom: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        cubicTo(
            center.x + size * 0.12f,
            center.y - size * 0.52f,
            center.x + size * 0.62f,
            center.y - size * 0.08f,
            center.x + size * 0.58f,
            center.y + size * 0.34f
        )
        cubicTo(
            center.x + size * 0.54f,
            center.y + size * 0.90f,
            center.x + size * 0.22f,
            center.y + size,
            center.x,
            center.y + size
        )
        cubicTo(
            center.x - size * 0.22f,
            center.y + size,
            center.x - size * 0.54f,
            center.y + size * 0.90f,
            center.x - size * 0.58f,
            center.y + size * 0.34f
        )
        cubicTo(
            center.x - size * 0.62f,
            center.y - size * 0.08f,
            center.x - size * 0.12f,
            center.y - size * 0.52f,
            center.x,
            center.y - size
        )
        close()
    }

    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(colorTop, colorBottom),
            start = center - Offset(0f, size),
            end = center + Offset(0f, size)
        )
    )
    drawCircle(
        color = Color.White.copy(alpha = colorTop.alpha * 0.30f),
        radius = size * 0.14f,
        center = center - Offset(size * 0.18f, size * 0.24f)
    )
}

private fun DrawScope.drawSnowflakes(
    progress: Float,
    palette: WeatherIconPalette,
    unit: Float,
    motionScale: Float,
    xOffset: Float = 0f
) {
    val specs = listOf(
        SnowSpec(x = 29f + xOffset, phase = 0.00f, speed = 1f, radius = 4.4f),
        SnowSpec(x = 45f + xOffset, phase = 0.27f, speed = 1f, radius = 5.2f),
        SnowSpec(x = 61f + xOffset, phase = 0.54f, speed = 1f, radius = 4.2f),
        SnowSpec(x = 74f + xOffset, phase = 0.78f, speed = 1f, radius = 4.8f)
    )

    specs.forEachIndexed { index, spec ->
        val local = fract(progress * spec.speed + spec.phase)
        val y = lerp(57f, 84f, local)
        val sway = sin((local * 2f + index * 0.37f) * PI.toFloat()) *
            (3.5f + index * 0.4f) * unit * motionScale
        val alpha = precipitationAlpha(local)
        val rotation = (progress * 180f * (if (index % 2 == 0) 1f else -1f))

        drawCircle(
            color = palette.snowShadow.copy(alpha = alpha * 0.16f),
            radius = spec.radius * 1.55f * unit,
            center = point(spec.x, y + 1.5f, unit) + Offset(sway, 0f)
        )
        drawSnowflake(
            center = point(spec.x, y, unit) + Offset(sway, 0f),
            radius = spec.radius * unit,
            color = palette.snow.copy(alpha = alpha),
            strokeWidth = 1.7f * unit,
            rotation = rotation
        )
    }
}

private fun DrawScope.drawSnowflake(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    rotation: Float
) {
    rotate(degrees = rotation, pivot = center) {
        repeat(3) { index ->
            val angle = (index * PI / 3.0).toFloat()
            val vector = polar(radius, angle)
            drawLine(
                color = color,
                start = center - vector,
                end = center + vector,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(
        color = Color.White.copy(alpha = color.alpha * 0.70f),
        radius = strokeWidth * 0.65f,
        center = center
    )
}

private fun DrawScope.drawLightning(
    center: Offset,
    scale: Float,
    color: Color,
    shadow: Color,
    unit: Float
) {
    fun buildPath(offset: Offset): Path = Path().apply {
        moveTo(center.x - 3f * unit * scale + offset.x, center.y - 16f * unit * scale + offset.y)
        lineTo(center.x + 9f * unit * scale + offset.x, center.y - 16f * unit * scale + offset.y)
        lineTo(center.x + 2f * unit * scale + offset.x, center.y - 3f * unit * scale + offset.y)
        lineTo(center.x + 10f * unit * scale + offset.x, center.y - 3f * unit * scale + offset.y)
        lineTo(center.x - 8f * unit * scale + offset.x, center.y + 18f * unit * scale + offset.y)
        lineTo(center.x - 2f * unit * scale + offset.x, center.y + 4f * unit * scale + offset.y)
        lineTo(center.x - 11f * unit * scale + offset.x, center.y + 4f * unit * scale + offset.y)
        close()
    }

    drawPath(
        path = buildPath(Offset(1.8f * unit, 2.4f * unit)),
        color = shadow
    )
    drawPath(
        path = buildPath(Offset.Zero),
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = color.alpha), color),
            start = center - Offset(0f, 18f * unit),
            end = center + Offset(0f, 18f * unit)
        )
    )
}

private fun DrawScope.drawGlow(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    if (alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.34f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private data class RainSpec(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val size: Float
)

private data class SnowSpec(
    val x: Float,
    val phase: Float,
    val speed: Float,
    val radius: Float
)

private data class FogLine(
    val y: Float,
    val x: Float,
    val width: Float,
    val phase: Float
)

private fun wave(
    progress: Float,
    cycles: Float = 1f,
    phase: Float = 0f
): Float = sin((progress * cycles + phase) * TAU)

private fun wave01(
    progress: Float,
    cycles: Float = 1f,
    phase: Float = 0f
): Float = (wave(progress, cycles, phase) + 1f) / 2f

private fun lightningFlash(progress: Float): Float {
    val first = triangularPulse(progress, center = 0.58f, halfWidth = 0.018f)
    val second = triangularPulse(progress, center = 0.625f, halfWidth = 0.028f) * 0.72f
    val third = triangularPulse(progress, center = 0.685f, halfWidth = 0.014f) * 0.88f
    return maxOf(first, second, third).coerceIn(0f, 1f)
}

private fun triangularPulse(value: Float, center: Float, halfWidth: Float): Float {
    val distance = abs(value - center)
    return (1f - distance / halfWidth).coerceIn(0f, 1f)
}

private fun precipitationAlpha(localProgress: Float): Float {
    val fadeIn = (localProgress / 0.16f).coerceIn(0f, 1f)
    val fadeOut = ((1f - localProgress) / 0.24f).coerceIn(0f, 1f)
    return min(fadeIn, fadeOut)
}

private fun fract(value: Float): Float = value - kotlin.math.floor(value)

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun positiveMod(value: Int, modulus: Int): Int {
    if (modulus <= 0) return 0
    val result = value % modulus
    return if (result < 0) result + modulus else result
}

private fun point(x: Float, y: Float, unit: Float): Offset =
    Offset(x * unit, y * unit)

private fun polar(radius: Float, angleRadians: Float): Offset = Offset(
    x = cos(angleRadians) * radius,
    y = sin(angleRadians) * radius
)

private fun blend(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = lerp(from.red, to.red, t),
        green = lerp(from.green, to.green, t),
        blue = lerp(from.blue, to.blue, t),
        alpha = lerp(from.alpha, to.alpha, t)
    )
}

private val TAU = (2.0 * PI).toFloat()
