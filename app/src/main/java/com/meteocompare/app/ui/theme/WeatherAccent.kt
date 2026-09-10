package com.meteocompare.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.meteocompare.app.domain.model.WeatherCondition
import kotlin.math.max
import kotlin.math.min

/**
 * Palette meteo partagee par les cartes et par les accents d'interface.
 *
 * Les teintes brutes restent semantiques : une condition donnee garde la meme
 * famille de couleur, meme lorsque Material You fournit le theme de base. Le
 * theme d'accent ci-dessous adapte uniquement leur luminosite quand la couleur
 * porte du texte ou une icone, afin de conserver un contraste accessible.
 */
internal object WeatherAccent {

    /** Couleur decorative brute, avec un neutre pour les donnees inconnues. */
    fun of(condition: WeatherCondition?, isDark: Boolean): Color =
        activeOrNull(condition, isDark)
            ?: if (isDark) NeutralDark else NeutralLight

    /**
     * Accent significatif pour l'interface. Une absence de donnee ne doit pas
     * repeindre l'application en gris : le theme Material de base est conserve.
     */
    fun activeOrNull(condition: WeatherCondition?, isDark: Boolean): Color? = when (condition) {
        null,
        WeatherCondition.UNKNOWN -> null

        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR -> if (isDark) SunnyDark else SunnyLight

        WeatherCondition.PARTLY_CLOUDY ->
            if (isDark) PartlyCloudyDark else PartlyCloudyLight

        WeatherCondition.OVERCAST -> if (isDark) OvercastDark else OvercastLight
        WeatherCondition.FOG -> if (isDark) FogDark else FogLight

        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN,
        WeatherCondition.RAIN_SHOWERS -> if (isDark) RainDark else RainLight

        WeatherCondition.FREEZING_RAIN ->
            if (isDark) FreezingRainDark else FreezingRainLight

        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> if (isDark) SnowDark else SnowLight

        WeatherCondition.THUNDERSTORM ->
            if (isDark) ThunderstormDark else ThunderstormLight
    }

    // Beau temps — ambre chaud.
    private val SunnyLight = Color(0xFFFFA726)
    private val SunnyDark = Color(0xFFFFB74D)

    // Eclaircies — beige dore, entre soleil et ciel couvert.
    private val PartlyCloudyLight = Color(0xFFBCAAA4)
    private val PartlyCloudyDark = Color(0xFFA1887F)

    // Couvert et brouillard — tons froids/neutres.
    private val OvercastLight = Color(0xFF78909C)
    private val OvercastDark = Color(0xFF90A4AE)
    private val FogLight = Color(0xFF9E9E9E)
    private val FogDark = Color(0xFFBDBDBD)

    // Precipitations.
    private val RainLight = Color(0xFF3F51B5)
    private val RainDark = Color(0xFF5C6BC0)
    private val FreezingRainLight = Color(0xFF00838F)
    private val FreezingRainDark = Color(0xFF00ACC1)
    private val SnowLight = Color(0xFF81D4FA)
    private val SnowDark = Color(0xFFB3E5FC)

    // L'orage reste volontairement distinct du soleil et des pluies ordinaires.
    private val ThunderstormLight = Color(0xFFB71C1C)
    private val ThunderstormDark = Color(0xFFF44336)

    private val NeutralLight = Color(0xFFBDBDBD)
    private val NeutralDark = Color(0xFF757575)
}

private const val WEATHER_ACCENT_ANIMATION_MS = 420
private const val TEXT_CONTRAST_RATIO = 4.5f
private const val ICON_CONTRAST_RATIO = 3f

/**
 * Applique la meteo de la ville au vocabulaire d'accent Material 3.
 *
 * Seuls les roles primaires changent. Les couleurs error/secondary/tertiary et
 * les accents metriques restent intacts, ce qui preserve leur signification.
 * Le retour au theme de base (chargement, cache ancien, condition inconnue) est
 * anime de la meme maniere qu'un changement de condition.
 */
@Composable
internal fun WeatherAccentTheme(
    condition: WeatherCondition?,
    content: @Composable () -> Unit
) {
    val base = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val isDark = base.surface.luminance() < 0.5f
    val rawAccent = WeatherAccent.activeOrNull(condition, isDark)
    val hasWeatherAccent = rawAccent != null

    val targetPrimary = rawAccent?.let { readableAccent(it, base.surface) } ?: base.primary
    val targetPrimaryContainer = rawAccent?.let {
        weatherAccentContainer(
            accent = it,
            surface = base.surface,
            isDark = isDark
        )
    } ?: base.primaryContainer
    val targetOnPrimary = if (hasWeatherAccent) {
        highestContrastContent(targetPrimary)
    } else {
        base.onPrimary
    }
    val targetOnPrimaryContainer = rawAccent?.let {
        readableAccent(targetPrimary, targetPrimaryContainer)
    } ?: base.onPrimaryContainer
    val targetInversePrimary = rawAccent?.let {
        readableAccent(it, base.inverseSurface, minimumRatio = ICON_CONTRAST_RATIO)
    } ?: base.inversePrimary
    val targetSurfaceTint = if (hasWeatherAccent) targetPrimary else base.surfaceTint

    val primary by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(WEATHER_ACCENT_ANIMATION_MS),
        label = "weather-accent-primary"
    )
    val onPrimary by animateColorAsState(
        targetValue = targetOnPrimary,
        animationSpec = tween(WEATHER_ACCENT_ANIMATION_MS),
        label = "weather-accent-on-primary"
    )
    val primaryContainer by animateColorAsState(
        targetValue = targetPrimaryContainer,
        animationSpec = tween(WEATHER_ACCENT_ANIMATION_MS),
        label = "weather-accent-primary-container"
    )
    val onPrimaryContainer by animateColorAsState(
        targetValue = targetOnPrimaryContainer,
        animationSpec = tween(WEATHER_ACCENT_ANIMATION_MS),
        label = "weather-accent-on-primary-container"
    )
    val inversePrimary by animateColorAsState(
        targetValue = targetInversePrimary,
        animationSpec = tween(WEATHER_ACCENT_ANIMATION_MS),
        label = "weather-accent-inverse-primary"
    )
    val surfaceTint by animateColorAsState(
        targetValue = targetSurfaceTint,
        animationSpec = tween(WEATHER_ACCENT_ANIMATION_MS),
        label = "weather-accent-surface-tint"
    )

    MaterialTheme(
        colorScheme = base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            surfaceTint = surfaceTint
        ),
        typography = typography,
        shapes = shapes,
        content = content
    )
}

/** Teinte legere utilisee par les FAB, selections et cartes mises en avant. */
internal fun weatherAccentContainer(
    accent: Color,
    surface: Color,
    isDark: Boolean
): Color = lerp(surface, accent, if (isDark) 0.26f else 0.18f)

/**
 * Conserve autant que possible la teinte d'origine, puis la rapproche du noir
 * ou du blanc jusqu'a atteindre le contraste demande sur [background].
 */
internal fun readableAccent(
    accent: Color,
    background: Color,
    minimumRatio: Float = TEXT_CONTRAST_RATIO
): Color {
    val opaqueAccent = accent.compositeOver(background)
    if (accentContrastRatio(opaqueAccent, background) >= minimumRatio) return opaqueAccent

    val target = highestContrastContent(background)
    for (step in 1..100) {
        val candidate = lerp(opaqueAccent, target, step / 100f)
        if (accentContrastRatio(candidate, background) >= minimumRatio) return candidate
    }
    return target
}

/** Noir ou blanc, selon celui qui contraste le mieux avec [background]. */
internal fun highestContrastContent(background: Color): Color =
    if (accentContrastRatio(Color.Black, background) >=
        accentContrastRatio(Color.White, background)
    ) Color.Black else Color.White

internal fun accentContrastRatio(foreground: Color, background: Color): Float {
    val opaqueForeground = foreground.compositeOver(background)
    val lighter = max(opaqueForeground.luminance(), background.luminance())
    val darker = min(opaqueForeground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}
