package com.meteocompare.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholder animé pour les loadings — donne un effet skeleton/shimmer en
 * faisant glisser un gradient horizontal.
 *
 * Préféré à un simple `CircularProgressIndicator` parce que :
 *   - Donne au user un avant-goût de la forme finale (height/width)
 *   - Réduit la perception de latence (effet "ça arrive")
 *   - Plus moderne visuellement
 *
 * Implémenté avec un `infiniteRepeatable` sur la position d'un gradient
 * linéaire. Pas de lib externe nécessaire.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // L'animation déplace le gradient de -300dp à 1200dp sur 1.2s, en boucle.
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )

    val baseColor = MaterialTheme.colorScheme.onSurface
    val shimmerColors = listOf(
        baseColor.copy(alpha = 0.08f),
        baseColor.copy(alpha = 0.18f),
        baseColor.copy(alpha = 0.08f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 0f)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush)
    )
}
