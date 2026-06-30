package com.meteocompare.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Renvoie la couleur de confiance adaptée au thème courant.
 *
 * Pourquoi un helper plutôt que d'appeler directement [ConfidenceHigh] etc :
 * en thème sombre, les couleurs saturées foncées (vert 0xFF2E7D32 etc.) ont
 * une luminance trop proche du `surface`/`surfaceContainerLow` du M3 dark
 * scheme — le texte/bandeau "se confond" avec le fond. La palette pastel
 * (suffixe `Dark`) garde la même teinte mais avec une luminosité bien plus
 * haute, donc lisible sur fond sombre.
 *
 * Détection du mode : on regarde la luminance du `surface` actif plutôt que
 * `isSystemInDarkTheme()`. Raison : l'utilisateur peut forcer LIGHT alors que
 * le système est en DARK (ou inversement) via les Settings de l'app — la
 * source de vérité, c'est le colorScheme réellement appliqué.
 *
 * Mappage des seuils identique partout dans l'app : ≥ 80 haute, ≥ 50 moyenne,
 * sinon basse. Centraliser ici évite que ce barème dérive entre les écrans.
 */
@Composable
@ReadOnlyComposable
fun confidenceColor(percent: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when {
        percent >= 80 -> if (dark) ConfidenceHighDark else ConfidenceHigh
        percent >= 50 -> if (dark) ConfidenceMediumDark else ConfidenceMedium
        else -> if (dark) ConfidenceLowDark else ConfidenceLow
    }
}
