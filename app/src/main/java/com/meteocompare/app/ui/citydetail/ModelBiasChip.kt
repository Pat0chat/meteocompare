package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
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
 * Visuel : icône directionnelle (Material) + valeur signée en mono, sur un
 * fond teinté selon [BiasDirection]. Aucune différence visuelle entre
 * MODERATE et HIGH — le rôle du chip est d'attirer l'œil sur l'existence
 * d'un biais, pas d'en encoder la magnitude fine (la sheet détaille tout ça).
 *
 * Pourquoi Icon plutôt qu'un glyphe unicode ↗/↘ : les glyphes textuels ont un
 * placement vertical variable selon la police (au-dessus de la baseline des
 * chiffres dans la plupart des fonts), ce qui décale visuellement la flèche
 * du nombre. Les Icons sont rendus dans une boîte carrée avec contenu centré
 * — alignement vertical parfait garanti quelle que soit la police système.
 */
@Composable
internal fun ModelBiasChip(
    bias: ModelBias,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sécurité : si la significativité est NOT_SIGNIFICANT, on ne rend rien.
    if (bias.significance == BiasSignificance.NOT_SIGNIFICANT) return

    val palette = chipPalette(bias.direction)
    val label = formatBiasLabel(bias)
    val a11y = biasContentDescription(bias)

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
        Icon(
            imageVector = arrowIconFor(bias.direction),
            contentDescription = null, // décrit via le semantics parent
            tint = palette.foreground,
            modifier = Modifier.size(11.dp)
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
 * Couleurs d'un chip selon la direction du biais.
 *
 * Ces couleurs sont volontairement HORS du colorScheme Material — elles vivent
 * dans une couche sémantique distincte (jugement sur la qualité du modèle,
 * pas mesure de température).
 */
internal data class ChipPalette(
    val background: Color,
    val border: Color,
    val foreground: Color
)

internal fun chipPalette(direction: BiasDirection): ChipPalette = when (direction) {
    BiasDirection.WARM -> ChipPalette(
        background = Color(0xFFFDECEC),
        border = Color(0x26B23A3A),
        foreground = Color(0xFFB23A3A)
    )
    BiasDirection.COLD -> ChipPalette(
        background = Color(0xFFEAF1FA),
        border = Color(0x261A5FB4),
        foreground = Color(0xFF1A5FB4)
    )
    BiasDirection.NEUTRAL -> ChipPalette(
        background = Color(0xFFF0F1F4),
        border = Color(0x1F6B7280),
        foreground = Color(0xFF6B7280)
    )
}

private fun arrowIconFor(direction: BiasDirection): ImageVector = when (direction) {
    BiasDirection.WARM    -> Icons.Filled.ArrowUpward
    BiasDirection.COLD    -> Icons.Filled.ArrowDownward
    BiasDirection.NEUTRAL -> Icons.Filled.Remove
}

/**
 * Formatte "+1,5°" / "−0,4 mm" / "+15 km/h".
 *
 * Le séparateur décimal suit `Locale.getDefault()` via `String.format` — pas
 * de `.replace('.', ',')` forcé (bug antérieur qui affichait une virgule
 * même en anglais).
 */
internal fun formatBiasLabel(bias: ModelBias): String {
    val abs = abs(bias.meanBias)
    val sign = when {
        bias.meanBias > 0.0 -> "+"
        bias.meanBias < 0.0 -> "−"
        else -> "±"
    }
    // 1 décimale pour temp et précip, entier pour vent (biais vent toujours
    // ordre de plusieurs km/h, décimales inutiles).
    val magnitude = when (bias.variable) {
        BiasVariable.WIND_SPEED -> "%.0f".format(abs)
        else                    -> "%.1f".format(abs) // locale-aware
    }
    val unit = when (bias.variable) {
        BiasVariable.TEMPERATURE   -> "°"
        BiasVariable.PRECIPITATION -> " mm"
        BiasVariable.WIND_SPEED    -> " km/h"
    }
    return "$sign$magnitude$unit"
}

/**
 * Description a11y — construite via stringResource pour rester localisée.
 * Un lecteur d'écran doit prononcer "Ce modèle surestime la température de
 * 1,5° en moyenne sur 30 jours." en français, "This model overestimates
 * temperature by 1.5° on average over 30 days." en anglais.
 */
@Composable
private fun biasContentDescription(bias: ModelBias): String {
    val verb = stringResource(
        when (bias.direction) {
            BiasDirection.WARM    -> R.string.bias_verb_overestimates
            BiasDirection.COLD    -> R.string.bias_verb_underestimates
            BiasDirection.NEUTRAL -> R.string.bias_verb_neutral
        }
    )
    val variable = stringResource(
        when (bias.variable) {
            BiasVariable.TEMPERATURE   -> R.string.bias_variable_temperature
            BiasVariable.PRECIPITATION -> R.string.bias_variable_precipitation
            BiasVariable.WIND_SPEED    -> R.string.bias_variable_wind_speed
        }
    )
    val magnitude = formatBiasLabel(bias)
        .removePrefix("+").removePrefix("−").removePrefix("±")
    return stringResource(
        R.string.bias_chip_content_description,
        verb,
        variable,
        magnitude,
        bias.windowDays
    )
}