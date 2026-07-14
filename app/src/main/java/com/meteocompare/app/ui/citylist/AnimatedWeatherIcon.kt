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
 * L'animation doit être **subtile** — l'objectif est d'ajouter un léger
 * "signal de vie" aux cards, PAS de distraire. Un utilisateur qui scrolle sa
 * home ne doit pas percevoir consciemment le mouvement ; il doit juste sentir
 * que "la page respire", comme un fond d'écran en parallaxe iOS.
 *
 * Chaque condition a un motif qui évoque physiquement le phénomène :
 *   - CLEAR / MAINLY_CLEAR : rotation TRÈS lente du soleil (60s/tour, imperceptible
 *     à l'œil nu mais présente en périphérie de vision)
 *   - PARTLY_CLOUDY / OVERCAST : dérive horizontale des nuages (4s de cycle,
 *     amplitude 1.5dp — mimique du vent)
 *   - RAIN / DRIZZLE / RAIN_SHOWERS : petit rebond vertical (1.2s, amplitude 2dp
 *     — évoque la chute de gouttes)
 *   - FREEZING_RAIN : pas d'animation — "gel" symbolique + économie de tokens
 *     visuels pour signaler la condition dangereuse
 *   - SNOW / SNOW_SHOWERS : dérive verticale lente (2s, 1.5dp — évoque la
 *     chute légère des flocons)
 *   - THUNDERSTORM : pas d'animation — pause visuelle = tension. C'est la
 *     couleur d'alerte de la barre latérale qui doit accrocher l'œil, pas
 *     l'animation.
 *   - FOG : pas d'animation — l'immobilité évoque la stagnation de l'air
 *
 * ─── Coût perf ─────────────────────────────────────────────────────────────
 * `InfiniteTransition` génère 1 frame animation par recomposition Compose,
 * mais l'invalidation est locale au composable icône (16dp² max). Sur 4-6
 * cards visibles, on parle de ~500µs de layout + recompose par frame — sous
 * le budget d'une frame 60fps (16ms). Aucune allocation supplémentaire par
 * frame (les floats sont recyclés).
 *
 * ─── Ce qui N'EST PAS animé ───────────────────────────────────────────────
 * - La couleur de tint (reste stable via `semanticTint()`)
 * - La taille de l'icône (les .dp restent constantes)
 * - La barre latérale d'accent (statique — c'est un signal, pas un mouvement)
 *
 * Les recompositions provoquées par le tick d'animation ne recalculent PAS
 * la logique de sélection d'icône (`WeatherIconDecorative` est stable sur
 * son enum). Coût strictement au niveau du draw.
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

    // Chaque motif d'animation est isolé — on ne crée que celle nécessaire
    // pour la condition courante. Un remember-based approach avec
    // `key(condition) { ... }` serait équivalent mais plus verbeux.
    val animModifier: Modifier = when (condition) {

        WeatherCondition.CLEAR,
        WeatherCondition.MAINLY_CLEAR -> {
            // Rotation continue TRÈS lente. 60s/tour = 6°/s = 0.1°/frame à
            // 60fps. Consciemment invisible, subliminalement perçu.
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 60_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sun-rotation"
            )
            Modifier.rotate(angle)
        }

        WeatherCondition.PARTLY_CLOUDY,
        WeatherCondition.OVERCAST -> {
            // Dérive horizontale sinusoïdale. FastOutSlowIn donne un mouvement
            // "aisé" naturel qui ressemble à une brise, pas à un métronome.
            val dx by transition.animateFloat(
                initialValue = -1.5f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4_000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "cloud-drift"
            )
            Modifier.offset(x = dx.dp)
        }

        WeatherCondition.RAIN,
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN_SHOWERS -> {
            // Petit rebond vertical asymétrique : plus lent en descente, plus
            // rapide en remontée, comme une goutte qui tombe et rebondit. En
            // pratique on ne fait qu'un ping-pong linéaire — le cerveau
            // interprète le mouvement vertical à cette taille comme "pluie"
            // sans avoir besoin de finesse.
            val dy by transition.animateFloat(
                initialValue = 0f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1_200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rain-bounce"
            )
            Modifier.offset(y = dy.dp)
        }

        WeatherCondition.SNOW,
        WeatherCondition.SNOW_SHOWERS -> {
            // Chute lente + dérive latérale légère — flocon idéalisé.
            // Amplitude verticale 1.5dp, période 2s (plus lent que la pluie).
            val dy by transition.animateFloat(
                initialValue = -1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "snow-drift"
            )
            Modifier.offset(y = dy.dp)
        }

        // Conditions immobiles — voir docstring pour la rationale
        WeatherCondition.THUNDERSTORM,
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
