package com.meteocompare.app.ui.components

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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
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
        sunCore = tint.copy(alpha = 0.94f),
        sunEdge = tint.copy(alpha = 0.72f),
        sunRay = tint.copy(alpha = 0.86f),
        // Contraste volontairement plus fort en monochrome pour que les icônes
        // compactes (notamment overcast) restent lisibles en noir ou en blanc.
        cloudLight = tint.copy(alpha = 0.98f),
        cloudMid = tint.copy(alpha = 0.74f),
        cloudDark = tint.copy(alpha = 0.46f),
        rainLight = tint.copy(alpha = 0.92f),
        rainDark = tint.copy(alpha = 0.66f),
        snow = tint.copy(alpha = 0.98f),
        snowShadow = tint.copy(alpha = 0.58f),
        lightning = tint.copy(alpha = 0.96f),
        fog = tint.copy(alpha = 0.72f),
        alert = tint.copy(alpha = 0.92f),
        glow = tint.copy(alpha = 0.10f),
        shadow = tint.copy(alpha = 0.22f)
    )
}

object WeatherIconDefaults {
    /**
     * Palette 2026 : couleurs franches mais moins « glossy », proches des
     * rôles tonaux Material 3. Les pictogrammes restent volontairement
     * multicolores pour être identifiables instantanément à petite taille.
     */
    val palette = WeatherIconPalette(
        sunCore = Color(0xFFFFCA28),
        sunEdge = Color(0xFFFFB300),
        sunRay = Color(0xFFFFB300),
        cloudLight = Color(0xFFF8FAFC),
        cloudMid = Color(0xFFDCE6EE),
        cloudDark = Color(0xFF708390),
        rainLight = Color(0xFF6EC6FF),
        rainDark = Color(0xFF1976D2),
        snow = Color(0xFFF6FBFF),
        snowShadow = Color(0xFF90CAF9),
        lightning = Color(0xFFFFC107),
        fog = Color(0xFF9AAAB5),
        alert = Color(0xFF5E5CE6),
        glow = Color(0x141976D2),
        shadow = Color(0xFF52616B)
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
    val backDx = wave(progress, cycles = 1f, phase = 0.10f) * 1.3f * unit * motionScale
    val backDy = wave(progress, cycles = 2f, phase = 0.24f) * 0.30f * unit * motionScale
    val frontDx = wave(progress, cycles = 1f, phase = 0.58f) * 1.7f * unit * motionScale
    val frontDy = wave(progress, cycles = 2f, phase = 0.14f) * 0.42f * unit * motionScale

    // Les deux nuages restent opaques : la séparation se fait par le ton,
    // l'échelle et le décalage, pas par une semi-transparence qui paraît étrange.
    drawCloud(
        center = point(37f, 43f, unit) + Offset(backDx, backDy),
        scale = 0.74f,
        palette = palette,
        unit = unit,
        depth = 0.08f,
        alpha = 1f
    )
    drawCloud(
        center = point(61f, 60f, unit) + Offset(frontDx, frontDy),
        scale = 1.12f,
        palette = palette,
        unit = unit,
        depth = 0.98f,
        alpha = 1f
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
            start = snap(point(line.x, line.y, unit) + Offset(dx, 0f)),
            end = snap(point(line.x + line.width, line.y, unit) + Offset(dx, 0f)),
            strokeWidth = snappedStroke(5f * unit),
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

    drawRainStreaks(
        progress = progress,
        palette = palette,
        unit = unit,
        motionScale = motionScale,
        intensity = 0.78f,
        xPositions = listOf(34f, 50f, 66f),
        speeds = listOf(1.55f, 1.70f, 1.60f),
        phases = listOf(0.00f, 0.28f, 0.56f),
        startY = 59f,
        endY = 81f,
        lengthRange = 5.5f to 8.0f,
        strokeRange = 1.8f to 2.1f,
        slant = 1.8f
    )
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

    drawRainStreaks(
        progress = progress,
        palette = palette,
        unit = unit,
        motionScale = motionScale,
        intensity = 0.92f,
        xPositions = listOf(36f, 52f, 68f),
        speeds = listOf(1.85f, 2.15f, 1.95f),
        phases = listOf(0.00f, 0.31f, 0.62f),
        startY = 57f,
        endY = 76f,
        lengthRange = 6.5f to 10.5f,
        strokeRange = 2.0f to 2.5f,
        slant = 2.1f
    )

    val sparklePulse = 0.75f + 0.25f * wave01(progress, cycles = 2f)
    drawSnowflake(
        center = point(52f, 81f, unit),
        radius = 9f * unit * (0.92f + 0.08f * sparklePulse * motionScale),
        color = palette.alert.copy(alpha = sparklePulse),
        strokeWidth = snappedStroke(2.2f * unit),
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
            start = snap(point(x, y, unit)),
            end = snap(point(x - 2f, y + 7f, unit)),
            strokeWidth = snappedStroke(2.6f * unit),
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
        style = Stroke(width = snappedStroke(5f * unit), cap = StrokeCap.Round)
    )
    drawLine(
        color = palette.cloudDark,
        start = snap(center + Offset(0f, 4f * unit)),
        end = snap(center + Offset(0f, 10f * unit)),
        strokeWidth = snappedStroke(5f * unit),
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
    rayCount: Int = 8
) {
    // Direction Material 2026 : géométrie simple, rayons courts et ronds,
    // mais avec un contour tonal discret pour une meilleure lisibilité.
    rotate(degrees = rotation, pivot = center) {
        repeat(rayCount) { index ->
            val angle = (2.0 * PI * index / rayCount).toFloat()
            drawLine(
                color = palette.sunRay,
                start = snap(center + polar(rayInner, angle)),
                end = snap(center + polar(rayOuter, angle)),
                strokeWidth = snappedStroke(4.6f * unit),
                cap = StrokeCap.Round
            )
        }
    }

    drawCircle(
        color = palette.sunEdge.copy(alpha = 0.18f),
        radius = radius + 2.2f * unit,
        center = snap(center)
    )
    drawCircle(
        color = palette.sunCore,
        radius = radius,
        center = snap(center)
    )
    drawCircle(
        color = palette.sunEdge.copy(alpha = 0.42f),
        radius = radius,
        center = center,
        style = Stroke(width = snappedStroke(2.0f * unit))
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

    // Contours tonals plus nets pour améliorer la définition à petite taille,
    // tout en conservant un langage plat et moderne.
    val outline = blend(palette.cloudDark, palette.cloudMid, 0.22f)
        .copy(alpha = 0.70f * alpha)
    val fill = blend(palette.cloudLight, palette.cloudMid, depth * 0.52f)
        .copy(alpha = alpha)
    val highlight = blend(palette.cloudLight, Color.White, 0.28f)
        .copy(alpha = 0.14f * alpha)
    val underside = blend(fill, palette.cloudDark, 0.10f)
        .copy(alpha = 0.26f * alpha)

    drawCloudShape(
        center = center,
        scale = scale * 1.075f,
        bodyTopLeft = Offset(
            x = center.x - bodyWidth * 1.075f / 2f,
            y = center.y - bodyHeight * 1.075f / 2f + 6f * unit * scale * 1.075f
        ),
        bodyWidth = bodyWidth * 1.075f,
        bodyHeight = bodyHeight * 1.075f,
        brush = SolidColor(outline),
        unit = unit
    )
    drawCloudShape(
        center = center,
        scale = scale,
        bodyTopLeft = bodyTopLeft,
        bodyWidth = bodyWidth,
        bodyHeight = bodyHeight,
        brush = SolidColor(fill),
        unit = unit
    )

    drawRoundRect(
        color = underside,
        topLeft = bodyTopLeft + Offset(4.5f * unit * scale, 10f * unit * scale),
        size = Size(bodyWidth - 9f * unit * scale, bodyHeight * 0.32f),
        cornerRadius = CornerRadius(9f * unit * scale)
    )
    drawCircle(
        color = highlight,
        radius = 8.5f * unit * scale,
        center = center + Offset(-8f * unit * scale, -9f * unit * scale)
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
        center = snap(center + Offset(-15f * unit * scale, -2f * unit * scale))
    )
    drawCircle(
        brush = brush,
        radius = 18f * unit * scale,
        center = snap(center + Offset(1f * unit * scale, -9f * unit * scale))
    )
    drawCircle(
        brush = brush,
        radius = 12.5f * unit * scale,
        center = snap(center + Offset(18f * unit * scale, 0f))
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
    drawRainStreaks(
        progress = progress,
        palette = palette,
        unit = unit,
        motionScale = motionScale,
        intensity = intensity,
        xPositions = listOf(29f + xOffset, 43f + xOffset, 58f + xOffset, 72f + xOffset),
        speeds = listOf(1.95f, 2.20f, 2.55f, 2.10f),
        phases = listOf(0.00f, 0.23f, 0.47f, 0.71f),
        startY = 56f,
        endY = 84f,
        lengthRange = 7.0f to 12.5f,
        strokeRange = 2.1f to 2.8f,
        slant = 2.4f
    )
}

private fun DrawScope.drawRainStreaks(
    progress: Float,
    palette: WeatherIconPalette,
    unit: Float,
    motionScale: Float,
    intensity: Float,
    xPositions: List<Float>,
    speeds: List<Float>,
    phases: List<Float>,
    startY: Float,
    endY: Float,
    lengthRange: Pair<Float, Float>,
    strokeRange: Pair<Float, Float>,
    slant: Float
) {
    xPositions.forEachIndexed { index, x ->
        val speed = speeds.getOrElse(index) { speeds.last() }
        val phase = phases.getOrElse(index) { 0f }
        val local = fract(progress * speed + phase)
        val y = lerp(startY, endY, local)
        val alpha = precipitationAlpha(local)
        val drift = sin((local + index * 0.17f) * PI.toFloat()) *
            1.2f * unit * motionScale
        val length = lerp(lengthRange.first, lengthRange.second, local) * intensity
        val stroke = snappedStroke(lerp(strokeRange.first, strokeRange.second, local) * unit)
        val start = point(x, y, unit) + Offset(drift - slant * unit, -length * unit)
        val end = point(x, y, unit) + Offset(drift, 0f)

        drawLine(
            color = palette.rainLight.copy(alpha = alpha * 0.55f),
            start = snap(start),
            end = snap(end),
            strokeWidth = snappedStroke(stroke * 1.22f),
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.rainDark.copy(alpha = alpha),
            start = snap(start + Offset(0.15f * unit, 0.35f * unit)),
            end = snap(end),
            strokeWidth = stroke,
            cap = StrokeCap.Round
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
    drawPath(
        path = path,
        color = blend(colorBottom, Color.Black.copy(alpha = colorBottom.alpha), 0.12f),
        style = Stroke(width = snappedStroke(size * 0.18f), join = StrokeJoin.Round)
    )
    drawCircle(
        color = Color.White.copy(alpha = colorTop.alpha * 0.20f),
        radius = size * 0.11f,
        center = snap(center - Offset(size * 0.18f, size * 0.24f))
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
            strokeWidth = snappedStroke(1.8f * unit),
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
                start = snap(center - vector),
                end = snap(center + vector),
                strokeWidth = snappedStroke(strokeWidth),
                cap = StrokeCap.Round
            )
            val branch = polar(radius * 0.42f, angle)
            val branchLeft = polar(radius * 0.18f, angle + 0.55f)
            val branchRight = polar(radius * 0.18f, angle - 0.55f)
            drawLine(
                color = color.copy(alpha = color.alpha * 0.92f),
                start = snap(center + branch),
                end = snap(center + branch - branchLeft),
                strokeWidth = snappedStroke(strokeWidth * 0.78f),
                cap = StrokeCap.Round
            )
            drawLine(
                color = color.copy(alpha = color.alpha * 0.92f),
                start = snap(center + branch),
                end = snap(center + branch - branchRight),
                strokeWidth = snappedStroke(strokeWidth * 0.78f),
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(
        color = Color.White.copy(alpha = color.alpha * 0.70f),
        radius = strokeWidth * 0.62f,
        center = snap(center)
    )
}

private fun DrawScope.drawLightning(
    center: Offset,
    scale: Float,
    color: Color,
    shadow: Color,
    unit: Float
) {
    val path = Path().apply {
        moveTo(center.x - 3f * unit * scale, center.y - 16f * unit * scale)
        lineTo(center.x + 9f * unit * scale, center.y - 16f * unit * scale)
        lineTo(center.x + 2f * unit * scale, center.y - 3f * unit * scale)
        lineTo(center.x + 10f * unit * scale, center.y - 3f * unit * scale)
        lineTo(center.x - 8f * unit * scale, center.y + 18f * unit * scale)
        lineTo(center.x - 2f * unit * scale, center.y + 4f * unit * scale)
        lineTo(center.x - 11f * unit * scale, center.y + 4f * unit * scale)
        close()
    }
    drawPath(
        path = path,
        color = shadow,
        style = Stroke(width = 2.2f * unit * scale, join = StrokeJoin.Round)
    )
    drawPath(path = path, color = color)
}

private fun DrawScope.drawGlow(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    // Désactivé : même un halo très discret autour du soleil pouvait être
    // perçu comme un rond disgracieux englobant les rayons.
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

private fun fract(value: Float): Float = value - floor(value)

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

private fun DrawScope.snap(value: Float): Float =
    (value * 2f).roundToInt() / 2f

private fun DrawScope.snap(offset: Offset): Offset = Offset(
    x = snap(offset.x),
    y = snap(offset.y)
)

private fun DrawScope.snappedStroke(width: Float): Float =
    maxOf(1f, (width * 2f).roundToInt() / 2f)

private val TAU = (2.0 * PI).toFloat()
