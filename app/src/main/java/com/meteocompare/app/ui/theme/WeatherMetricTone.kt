package com.meteocompare.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Accent sémantique partagé pour la température dans les contrôles et visuels. */
@Composable
@ReadOnlyComposable
fun temperatureMetricAccent(): Color = MaterialTheme.colorScheme.error

/**
 * Bleu météo plus franc que le `primary` Material You.
 *
 * Un accent fixe par luminosité de thème évite que certaines palettes
 * dynamiques rendent la pluie gris-bleu ou trop pâle, tout en conservant un
 * contraste correct en thème clair et sombre.
 */
@Composable
@ReadOnlyComposable
fun precipitationMetricAccent(): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) Color(0xFF42A5F5) else Color(0xFF1565C0)
}

/** Accent partagé du vent, dérivé du tertiaire du thème courant. */
@Composable
@ReadOnlyComposable
fun windMetricAccent(): Color = MaterialTheme.colorScheme.tertiary
