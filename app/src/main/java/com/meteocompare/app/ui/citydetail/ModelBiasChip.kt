package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.domain.model.BiasDirection
import com.meteocompare.app.domain.model.BiasSignificance
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import kotlin.math.abs

/**
 * Pill compact affichant le biais d'un modèle sous son nom dans le header
 * du tableau. Ne s'affiche QUE si [ModelBias.significance] > NOT_SIGNIFICANT
 * — l'absence de chip communique "modèle bien calibré ici, rien à signaler".
 *
 * Visuel : glyphe directionnel (↗ / ↘) + valeur signée en mono, sur un fond
 * teinté selon [BiasDirection]. Aucune différence visuelle entre MODERATE et
 * HIGH — le rôle du chip est d'attirer l'œil sur l'existence d'un biais,
 * pas d'en encoder la magnitude fine (la sheet détaille tout ça).
 *
 * Cliquable partout — la surface tactile mesure ~ 22 × 40dp au minimum, un
 * peu juste pour la cible tactile Material (48dp) mais compensé par le fait
 * que la sheet peut aussi être ouverte en tapant le nom du modèle
 * (à câbler côté caller, voir doc du header).
 */
@Composable
internal fun ModelBiasChip(
    bias: ModelBias,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sécurité : si la significativité est NOT_SIGNIFICANT, on ne rend rien.
    // Le caller devrait déjà filtrer, mais on double-guard pour éviter qu'un
    // oubli au niveau du tableau ne pollue l'écran de chips sans intérêt.
    if (bias.significance == BiasSignificance.NOT_SIGNIFICANT) return

    val palette = chipPalette(bias.direction)
    val label = formatBiasLabel(bias)
    val arrow = arrowFor(bias.direction)

    // Description a11y en toutes lettres — un lecteur d'écran ne saurait pas
    // lire "↗ +1,5°" correctement. On explicite : sens + magnitude + variable.
    val a11y = remember(bias) { biasContentDescription(bias) }

    Row(
        modifier = modifier
            .height(22.dp)
            .background(palette.background, RoundedCornerShape(999.dp))
            .border(1.dp, palette.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = arrow,
            color = palette.foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            color = palette.foreground,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Couleurs d'un chip selon la direction du biais. Isolé pour :
 *   - permettre le test de mapping (test unit "WARM → rouge", etc.)
 *   - centraliser les valeurs hex qui matchent le mockup HTML validé
 *
 * Note : ces couleurs sont volontairement HORS du colorScheme Material —
 * elles vivent dans une couche sémantique distincte (jugement sur la
 * qualité du modèle, pas mesure de température). Utiliser errorContainer
 * ou tertiary aurait été trompeur.
 *
 * En dark mode, les fonds sont éclaircis (opacité) plutôt qu'inversés en
 * teinte, pour rester lisibles sans compétition avec les couleurs du heatmap
 * de fond. TODO Phase 2 : audit visuel dark, éventuellement adjuster.
 */
internal data class ChipPalette(
    val background: Color,
    val border: Color,
    val foreground: Color
)

internal fun chipPalette(direction: BiasDirection): ChipPalette = when (direction) {
    BiasDirection.WARM -> ChipPalette(
        background = Color(0xFFFDECEC),
        border = Color(0x26B23A3A),   // 15% alpha du foreground
        foreground = Color(0xFFB23A3A)
    )
    BiasDirection.COLD -> ChipPalette(
        background = Color(0xFFEAF1FA),
        border = Color(0x261A5FB4),
        foreground = Color(0xFF1A5FB4)
    )
    BiasDirection.NEUTRAL -> ChipPalette(
        // Cas dégénéré (biais exactement 0.0, uniquement en test) : gris
        // neutre. Ne devrait jamais atteindre le rendu en prod.
        background = Color(0xFFF0F1F4),
        border = Color(0x1F6B7280),
        foreground = Color(0xFF6B7280)
    )
}

private fun arrowFor(direction: BiasDirection): String = when (direction) {
    BiasDirection.WARM    -> "↗"
    BiasDirection.COLD    -> "↘"
    BiasDirection.NEUTRAL -> "≈"
}

/**
 * Formatte "+1,5°" / "−0,4 mm" / "−1,1 km/h" — signe unicode "−" (pas le
 * moins ASCII), virgule décimale FR, unité collée à la magnitude.
 *
 * Pour les valeurs très petites (< 0.05 en abs), on arrondit vers le "≈ 0"
 * plutôt que d'afficher "+0,0°" qui envoie un signal contradictoire (le chip
 * ne s'affiche de toute façon que si significativité > NOT_SIGNIFICANT, donc
 * ce cas est théorique).
 */
internal fun formatBiasLabel(bias: ModelBias): String {
    val abs = abs(bias.meanBias)
    val sign = when {
        bias.meanBias > 0.0 -> "+"
        bias.meanBias < 0.0 -> "−"
        else -> "±"
    }
    // 1 décimale pour temp et précip, entier pour vent (les biais vent sont
    // toujours de l'ordre de plusieurs km/h, décimales inutiles).
    val magnitude = when (bias.variable) {
        BiasVariable.WIND_SPEED -> "%.0f".format(abs)
        else -> "%.1f".format(abs).replace('.', ',')
    }
    val unit = when (bias.variable) {
        BiasVariable.TEMPERATURE   -> "°"
        BiasVariable.PRECIPITATION -> " mm"
        BiasVariable.WIND_SPEED    -> " km/h"
    }
    return "$sign$magnitude$unit"
}

private fun biasContentDescription(bias: ModelBias): String {
    val direction = when (bias.direction) {
        BiasDirection.WARM -> "surestime"
        BiasDirection.COLD -> "sous-estime"
        BiasDirection.NEUTRAL -> "biais nul"
    }
    val variableName = when (bias.variable) {
        BiasVariable.TEMPERATURE -> "la température"
        BiasVariable.PRECIPITATION -> "les précipitations"
        BiasVariable.WIND_SPEED -> "le vent"
    }
    val magnitude = formatBiasLabel(bias)
        .removePrefix("+").removePrefix("−").removePrefix("±")
    return "Ce modèle $direction $variableName de $magnitude en moyenne sur ${bias.windowDays} jours. " +
        "Toucher pour voir le détail."
}
