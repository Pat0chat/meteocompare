package com.meteocompare.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition

/**
 * Icône météo décorative commune à toute l'application.
 *
 * Depuis le redesign Material 2026, les petites icônes utilisent exactement la
 * même famille graphique que les grandes icônes animées : formes simples,
 * extrémités arrondies, volumes plats et palette sémantique. Cela évite le
 * mélange visuel entre les anciens Material Icons génériques et les pictogrammes
 * météo propriétaires de MeteoCompare.
 */
@Composable
fun WeatherIconDecorative(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified
) {
    if (condition == null) return

    AnimatedWeatherIcon(
        condition = condition,
        modifier = modifier,
        size = size,
        animated = false,
        animateConditionChanges = false,
        tint = tint
    )
}

/**
 * Teinte sémantique utilisée lorsqu'un contexte impose un rendu monochrome
 * (heatmaps, cellules très denses, états sélectionnés). Le rendu normal reste
 * multicolore et suit [WeatherIconDefaults.palette].
 */
fun WeatherCondition.semanticTint(): Color = when (this) {
    // Le monochrome ne fonctionne bien que pour les états visuellement “purs”.
    // Pour les conditions mixtes (soleil + nuage, soleil + pluie, etc.), on
    // conserve la palette native afin d'éviter des nuages orange ou des icônes
    // ambiguës dans les tableaux compacts.
    WeatherCondition.CLEAR -> Color(0xFFFFB300)

    WeatherCondition.MAINLY_CLEAR,
    WeatherCondition.PARTLY_CLOUDY,
    WeatherCondition.RAIN_SHOWERS,
    WeatherCondition.FREEZING_RAIN,
    WeatherCondition.SNOW_SHOWERS,
    WeatherCondition.UNKNOWN -> Color.Unspecified

    WeatherCondition.OVERCAST,
    WeatherCondition.FOG -> Color(0xFF90A4AE)

    WeatherCondition.DRIZZLE -> Color(0xFF29B6F6)
    WeatherCondition.RAIN -> Color(0xFF1E88E5)
    WeatherCondition.SNOW -> Color(0xFF64B5F6)
    WeatherCondition.THUNDERSTORM -> Color(0xFF7E57C2)
}
