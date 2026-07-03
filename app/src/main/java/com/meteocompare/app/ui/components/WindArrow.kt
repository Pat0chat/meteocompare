package com.meteocompare.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Petite flèche indiquant la direction du vent — pointe DANS le sens où
 * le vent SOUFFLE (downwind), pas d'où il vient.
 *
 * Convention météo :
 *   - `directionDegrees` = 0 → vent VENANT du Nord → soufflant VERS le Sud
 *     → flèche pointe vers le bas → rotation 180° (Icons.Filled.ArrowUpward
 *     pointe vers le haut par défaut).
 *   - Formule : `rotation = (directionDegrees + 180) % 360`
 *
 * Le choix "downwind" est plus intuitif pour un utilisateur casual (la
 * flèche indique "où va le vent"). Les usages spécialisés (voile, vol)
 * préfèrent l'orientation "upwind" — non couvert ici, on assume le grand
 * public.
 *
 * Utilisé par les tableaux vent daily (12dp) et hourly (10dp). Extrait ici
 * plutôt que dupliqué inline pour garantir que si la convention change
 * (ex : passer à "upwind"), le fix se fait à un seul endroit.
 *
 * @param directionDegrees direction météo d'origine du vent, en degrés (0-360).
 * @param size taille du glyphe. 12dp pour les tableaux daily, 10dp pour hourly
 *   (plus dense, cellule 32dp de haut, il faut économiser la place verticale).
 */
@Composable
internal fun WindArrow(directionDegrees: Int, size: Dp = 12.dp) {
    Icon(
        imageVector = Icons.Filled.ArrowUpward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(size)
            .rotate(((directionDegrees + 180) % 360).toFloat())
    )
}
