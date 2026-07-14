package com.meteocompare.app.ui.citylist

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.components.WeatherIconDecorative

/**
 * Version animée de [WeatherIconDecorative] pour la liste des villes.
 *
 * ─── Philosophie d'animation ───────────────────────────────────────────────
 * L'animation ajoute un signal de vie à la card sans distraire. Les amplitudes
 * ont été calibrées après feedback utilisateur : les valeurs initiales
 * (rotation 60s, ±1.5dp) étaient sous le seuil de perception passive. Les
 * valeurs actuelles restent contenues (± quelques dp, durées de 1-15s) mais
 * sont clairement perçues comme "l'icône vit".
 *
 * Chaque condition a un motif qui évoque physiquement le phénomène :
 *   - CLEAR / MAINLY_CLEAR : rotation continue à 15s/tour (rayons "scintillent")
 *   - PARTLY_CLOUDY / OVERCAST : dérive horizontale ±5dp / 3s (vent)
 *   - RAIN / DRIZZLE / RAIN_SHOWERS : rebond vertical 4dp / 0.8s (chute goutte)
 *   - SNOW / SNOW_SHOWERS : dérive X + Y de périodes différentes (flocon
 *     tourbillonnant, mouvement organique non répétitif)
 *   - THUNDERSTORM : flash d'opacité ponctuel ~4s (éclair au loin)
 *   - FREEZING_RAIN, FOG : pas d'animation — l'immobilité communique la
 *     stagnation / le danger figé
 *
 * ─── Coût perf ─────────────────────────────────────────────────────────────
 * `InfiniteTransition` génère 1 frame animation par recomposition Compose,
 * mais l'invalidation est locale au composable icône (42dp² max). Sur 4-6
 * cards visibles, on parle de ~500µs de layout + recompose par frame — sous
 * le budget d'une frame 60fps (16ms).
 */
@Composable
internal fun AnimatedWeatherIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = Color.Unspecified
) {
    if (condition == null) return

    val transition = rememberInfiniteTransition(label = "weather-icon-anim")

    val animModifier: Modifier = when (condition) {

        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR -> {
            // Rotation continue à 15s/tour = 24°/s. Clairement perçue en
            // vision périphérique. Rayons de Filled.WbSunny (8 pointes)
            // "tournent" comme les branches d'une roue solaire — évoque
            // rayonnement/scintillement plutôt que rotation mécanique.
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 15_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sun-rotation"
            )
            Modifier.rotate(angle)
        }

        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.OVERCAST -> {
            // Dérive horizontale sinusoïdale ±5dp sur 3s. Amplitude visible
            // sans "sauter aux yeux" — le nuage semble poussé par une brise.
            val dx by transition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cloud-drift"
            )
            Modifier.offset(x = dx.dp)
        }

        WeatherCondition.RAIN,
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN_SHOWERS -> {
            // Rebond vertical 4dp / 1.0s. Fréquence rapide (1.25 Hz) qui évoque
            // la chute continue des gouttes. Un utilisateur perçoit clairement
            // le mouvement sans que ça devienne agressif.
            val dy by transition.animateFloat(
                initialValue = 0f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rain-bounce"
            )
            Modifier.offset(y = dy.dp)
        }

        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> {
            // Deux animations X et Y de périodes INCOMMENSURABLES (1800 vs
            // 2200ms) → chemin non-répétitif type Lissajous. Le flocon paraît
            // tourbillonner naturellement au lieu de suivre une trajectoire
            // mécanique. C'est le seul cas d'usage à 2 animations combinées
            // — les autres conditions s'accommodent d'un seul axe.
            val dy by transition.animateFloat(
                initialValue = -2f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1_800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "snow-y"
            )
            val dx by transition.animateFloat(
                initialValue = -2f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2_200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "snow-x"
            )
            Modifier.offset(x = dx.dp, y = dy.dp)
        }

        WeatherCondition.THUNDERSTORM -> {
            // Flash d'opacité 1.0 → 0.35 → 1.0 sur 4s au total, avec la moitié
            // basse (opacity < 0.5) courte (~0.3s) pour évoquer un flash
            // d'éclair — pas une pulsation régulière type "cœur qui bat".
            // FastOutSlowInEasing avec Reverse produit un demi-cycle ~2s
            // aller-retour, l'utilisateur perçoit un "clin d'œil" sombre
            // occasionnel plutôt qu'un métronome.
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "thunder-flash"
            )
            Modifier.alpha(alpha)
        }

        // Conditions volontairement immobiles :
        //  - FREEZING_RAIN : "gel" symbolique + condition dangereuse (mieux
        //    vaut que l'utilisateur remarque la couleur alarme du halo que
        //    l'animation de l'icône)
        //  - FOG : évoque la stagnation de l'air, cohérent avec le phénomène
        //  - UNKNOWN : pas d'info sur laquelle animer
        WeatherCondition.FREEZING_RAIN,
        WeatherCondition.FOG,
        WeatherCondition.UNKNOWN -> Modifier
    }

    WeatherIconDecorative(
        condition = condition,
        modifier = modifier.then(animModifier),
        size = size,
        tint = tint
    )
}
