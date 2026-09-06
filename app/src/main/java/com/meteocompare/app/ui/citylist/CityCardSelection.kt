package com.meteocompare.app.ui.citylist

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.SemanticsPropertyKey

/** Contrat visuel commun aux cartes téléphone et tablette. */
internal enum class CityCardVisualEmphasis {
    /** Carte Home normale : aucune notion de sélection sur téléphone. */
    STANDARD,

    /** Carte active : surface légèrement teintée par son accent météo. */
    WEATHER_COLORED,

    /** Carte tablette inactive : palette neutralisée et contenu atténué. */
    DEEMPHASIZED
}

internal data class CityCardSelectionVisuals(
    val emphasis: CityCardVisualEmphasis,
    val targetAlpha: Float,
    val usesWeatherTint: Boolean
)

internal const val CITY_CARD_DEEMPHASIZED_ALPHA = 0.55f
internal const val CITY_CARD_SELECTED_TINT_LIGHT = 0.18f
internal const val CITY_CARD_SELECTED_TINT_DARK = 0.22f

private val DeemphasizedContainerLight = Color(0xFFE0E0E0)
private val DeemphasizedContainerDark = Color(0xFF303030)
private val DeemphasizedAccentLight = Color(0xFF9E9E9E)
private val DeemphasizedAccentDark = Color(0xFF757575)

internal val CityCardVisualEmphasisKey =
    SemanticsPropertyKey<CityCardVisualEmphasis>("CityCardVisualEmphasis")

internal val CityCardTargetAlphaKey =
    SemanticsPropertyKey<Int>("CityCardTargetAlphaPercent")

internal fun cityCardSelectionVisuals(
    selectionEnabled: Boolean,
    isSelected: Boolean
): CityCardSelectionVisuals = when {
    !selectionEnabled -> CityCardSelectionVisuals(
        emphasis = CityCardVisualEmphasis.STANDARD,
        targetAlpha = 1f,
        usesWeatherTint = false
    )

    isSelected -> CityCardSelectionVisuals(
        emphasis = CityCardVisualEmphasis.WEATHER_COLORED,
        targetAlpha = 1f,
        usesWeatherTint = true
    )

    else -> CityCardSelectionVisuals(
        emphasis = CityCardVisualEmphasis.DEEMPHASIZED,
        targetAlpha = CITY_CARD_DEEMPHASIZED_ALPHA,
        usesWeatherTint = false
    )
}

internal fun weatherTintedCardColor(
    baseColor: Color,
    weatherAccent: Color,
    isDark: Boolean
): Color = lerp(
    start = baseColor,
    stop = weatherAccent,
    fraction = if (isDark) CITY_CARD_SELECTED_TINT_DARK else CITY_CARD_SELECTED_TINT_LIGHT
)

internal fun deemphasizedCardContainerColor(isDark: Boolean): Color =
    if (isDark) DeemphasizedContainerDark else DeemphasizedContainerLight

internal fun deemphasizedCardAccentColor(isDark: Boolean): Color =
    if (isDark) DeemphasizedAccentDark else DeemphasizedAccentLight
