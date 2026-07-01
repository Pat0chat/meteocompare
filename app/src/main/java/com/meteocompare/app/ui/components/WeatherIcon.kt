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
import androidx.compose.material3.LocalContentColor
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
 * @param tint Couleur. `Color.Unspecified` déclenche l'utilisation de
 *        `LocalContentColor.current` — le contrat "hérite du thème" qu'on
 *        veut pour les familles neutres (nuageux, couvert, brouillard).
 *
 * ⚠ Piège Compose contourné ici : `Icon(tint = Color.Unspecified)` NE fait
 * PAS hériter de LocalContentColor. Ça signifie "aucun ColorFilter appliqué",
 * donc l'icône est rendue dans sa couleur native — noir pur pour les vecteurs
 * outlined. En thème sombre, ça donnait des nuages noirs sur fond noir dans
 * les tableaux. Le default value `LocalContentColor.current` du paramètre
 * `tint` d'Icon ne se déclenche QUE quand l'argument est OMIS. Passer
 * explicitement Unspecified court-circuite ce défaut. On résout donc à la
 * main ici avant de déléguer.
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
    val resolvedTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Icon(
        imageVector = condition.toIcon(),
        contentDescription = stringResource(condition.descriptionRes()),
        tint = resolvedTint,
        modifier = modifier.size(size)
    )
}

/**
 * Variante avec `contentDescription = null` — pour quand l'a11y est déjà
 * porté ailleurs (cellule de tableau dont la sémantique entière est sur la
 * Row parente, par exemple).
 *
 * Applique la même résolution `Unspecified → LocalContentColor` que
 * [WeatherIcon] pour la même raison — voir sa doc pour le détail.
 */
@Composable
fun WeatherIconDecorative(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified
) {
    if (condition == null) return
    val resolvedTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Icon(
        imageVector = condition.toIcon(),
        contentDescription = null,
        tint = resolvedTint,
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
 *   - **familles nuageuses neutres** (PARTLY_CLOUDY, OVERCAST, FOG) →
 *     `Color.Unspecified`, ce qui laisse `Icon` récupérer `LocalContentColor`
 *     du contexte. Rationale : Material You peut produire un `primaryContainer`
 *     grisâtre selon le fond d'écran ; des icônes teintées en gris pur
 *     (0xFF9E9E9E, 0xFF757575) devenaient alors quasi invisibles sur ce fond.
 *     En s'appuyant sur `onPrimaryContainer` (fourni via `LocalContentColor`
 *     par les composants Material 3 comme Card), la lisibilité est garantie
 *     par le contrat M3 quel que soit le thème dynamique.
 *
 * Toutes les couleurs restent assez saturées pour être lisibles dans les deux
 * thèmes (testé visuellement sur surfaceContainerHigh et primaryContainer).
 */
fun WeatherCondition.semanticTint(): Color = when (this) {
    WeatherCondition.CLEAR, WeatherCondition.MAINLY_CLEAR -> Color(0xFFFFA726) // ambre
    WeatherCondition.PARTLY_CLOUDY -> Color.Unspecified                        // hérite du contexte
    WeatherCondition.OVERCAST -> Color.Unspecified                             // hérite du contexte
    WeatherCondition.FOG -> Color.Unspecified                                  // hérite du contexte
    WeatherCondition.DRIZZLE -> Color(0xFF4FC3F7)                              // bleu clair
    WeatherCondition.RAIN, WeatherCondition.RAIN_SHOWERS -> Color(0xFF1E88E5)  // bleu
    WeatherCondition.FREEZING_RAIN -> Color(0xFF5E35B1)                        // violet/bleu
    WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS -> Color(0xFF90A4AE)  // gris-bleu pâle
    WeatherCondition.THUNDERSTORM -> Color(0xFF6A1B9A)                         // violet
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
