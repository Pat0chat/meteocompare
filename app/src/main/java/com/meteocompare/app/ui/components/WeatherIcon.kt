package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition

/**
 * Icône représentant une condition météo. Source unique de vérité pour le
 * mapping famille → icône Material — toute autre partie de l'UI doit utiliser
 * ce composable, jamais re-mapper localement, sinon on aura un soleil sur
 * l'écran liste et un nuage sur l'écran détail pour la même condition.
 *
 * @param condition Famille de temps à afficher. Si null (pas de donnée), on
 *        affiche un placeholder discret — utilisé tel quel dans la matrice
 *        Jour × Modèle où certaines cellules peuvent manquer.
 * @param size Taille de l'icône. Standard Material : 24dp en ligne, 40-48dp
 *        en hero. Pas de fillSize() automatique pour que les tableaux/cartes
 *        compactes gardent la maîtrise.
 * @param tint Couleur. `Color.Unspecified` laisse l'icône hériter du
 *        `LocalContentColor` (donc s'adapte au thème via M3 sans réglage).
 */
@Composable
fun WeatherIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified
) {
    if (condition == null) {
        // Pas de donnée → on rend rien, plutôt qu'un point d'interrogation qui
        // donnerait l'impression d'une erreur. Le caller décide d'afficher "—"
        // ou un Spacer s'il a besoin d'occuper la place.
        return
    }
    Icon(
        imageVector = condition.toIcon(),
        contentDescription = stringResource(condition.descriptionRes()),
        tint = tint,
        modifier = modifier.size(size)
    )
}

/**
 * Variante avec `contentDescription = null` — pour quand l'a11y est déjà
 * porté ailleurs (cellule de tableau dont la sémantique entière est sur la
 * Row parente, par exemple).
 */
@Composable
fun WeatherIconDecorative(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified
) {
    if (condition == null) return
    Icon(
        imageVector = condition.toIcon(),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/**
 * Couleur sémantique recommandée pour teinter l'icône. Pas obligatoire — un
 * `LocalContentColor` neutre marche aussi — mais quand on AFFICHE plusieurs
 * conditions côte à côte (matrice Jour × Modèle), une teinte sémantique aide
 * énormément à scanner du regard ("toutes les cases bleues" = tous d'accord
 * sur la pluie).
 *
 * Les couleurs collent à celles déjà utilisées dans l'app pour la cohérence :
 *   - jaune (ambre Material) pour soleil, comme `ModelEcmwf`
 *   - bleus pour pluie/bruine, comme la légende des précipitations
 *   - violets/grays pour orage et neige
 *   - gris pour nuages/brouillard, pour ne pas voler la vedette aux conditions
 *     "à signaler"
 *
 * Toutes restent assez saturées pour être lisibles dans les deux thèmes
 * (testé visuellement sur surfaceContainerHigh et primaryContainer).
 */
fun WeatherCondition.semanticTint(): Color = when (this) {
    WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> Color(0xFFFFA726) // ambre
    WeatherCondition.PARTLY_CLOUDY -> Color(0xFF9E9E9E)                         // gris moyen
    WeatherCondition.OVERCAST -> Color(0xFF757575)                              // gris foncé
    WeatherCondition.FOG -> Color(0xFFB0BEC5)                                   // gris bleuté
    WeatherCondition.DRIZZLE -> Color(0xFF4FC3F7)                               // bleu clair
    WeatherCondition.RAIN, WeatherCondition.RAIN_SHOWERS -> Color(0xFF1E88E5)   // bleu
    WeatherCondition.FREEZING_RAIN -> Color(0xFF5E35B1)                         // violet/bleu
    WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> Color(0xFF90A4AE)   // gris-bleu pâle
    WeatherCondition.THUNDERSTORM -> Color(0xFF6A1B9A)                          // violet
    WeatherCondition.UNKNOWN -> Color.Unspecified
}

// ─── Internals ──────────────────────────────────────────────────────────────

private fun WeatherCondition.toIcon(): ImageVector = when (this) {
    WeatherCondition.CLEAR -> Icons.Outlined.WbSunny
    WeatherCondition.MAINLY_CLEAR -> Icons.Outlined.WbSunny
    // FilterDrama = soleil derrière un nuage. La meilleure correspondance
    // Material disponible pour "partiellement nuageux". WbCloudy serait
    // "complètement nuageux", confusion garantie.
    WeatherCondition.PARTLY_CLOUDY -> Icons.Outlined.FilterDrama
    WeatherCondition.OVERCAST -> Icons.Outlined.WbCloudy
    WeatherCondition.FOG -> Icons.Outlined.Cloud
    WeatherCondition.DRIZZLE -> Icons.Outlined.WaterDrop
    WeatherCondition.RAIN -> Icons.Outlined.Umbrella
    WeatherCondition.FREEZING_RAIN -> Icons.Outlined.AcUnit
    WeatherCondition.SNOW -> Icons.Outlined.AcUnit
    WeatherCondition.RAIN_SHOWERS -> Icons.Outlined.Umbrella
    WeatherCondition.SNOW_SHOWERS -> Icons.Outlined.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Outlined.Thunderstorm
    WeatherCondition.UNKNOWN -> Icons.Outlined.Cloud
}

private fun WeatherCondition.descriptionRes(): Int = when (this) {
    WeatherCondition.CLEAR -> R.string.weather_clear
    WeatherCondition.MAINLY_CLEAR -> R.string.weather_mainly_clear
    WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherCondition.OVERCAST -> R.string.weather_overcast
    WeatherCondition.FOG -> R.string.weather_fog
    WeatherCondition.DRIZZLE -> R.string.weather_drizzle
    WeatherCondition.RAIN -> R.string.weather_rain
    WeatherCondition.FREEZING_RAIN -> R.string.weather_freezing_rain
    WeatherCondition.SNOW -> R.string.weather_snow
    WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
    WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
    WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
    WeatherCondition.UNKNOWN -> R.string.weather_unknown
}
