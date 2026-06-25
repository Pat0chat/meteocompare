package com.meteocompare.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.meteocompare.app.domain.model.WeatherModel

/**
 * Palette de couleurs pour différencier les modèles sur les graphes superposés.
 *
 * Choisies pour être :
 *   - Distinctes deux à deux (au-delà de 7 elles deviennent moins évidentes —
 *     c'est OK, on en aura rarement plus que 7 simultanément).
 *   - Compatibles light & dark theme (saturation modérée).
 *   - Familles de modèles regroupées par teinte : AROME/AROME HD en bleus,
 *     ARPEGE Europe/World en verts, ICON-EU/ICON en oranges.
 */
private val ModelPalette = listOf(
    Color(0xFF1565C0), // AROME_FRANCE_HD - bleu foncé
    Color(0xFF42A5F5), // AROME_FRANCE - bleu clair
    Color(0xFF2E7D32), // ARPEGE_EUROPE - vert foncé
    Color(0xFF66BB6A), // ARPEGE_WORLD - vert clair
    Color(0xFFD84315), // ICON_EU - orange foncé
    Color(0xFFFF7043), // ICON_GLOBAL - orange clair
    Color(0xFF6A1B9A), // GFS - violet
    Color(0xFFF9A825)  // ECMWF - jaune ambré
)

fun WeatherModel.color(): Color = ModelPalette[ordinal % ModelPalette.size]
