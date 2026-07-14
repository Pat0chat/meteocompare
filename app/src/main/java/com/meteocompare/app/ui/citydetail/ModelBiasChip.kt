package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
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
 * du tableau. Trois variantes visuelles pour communiquer trois états sémantiques
 * distincts, TOUTES avec le MÊME footprint vertical (22dp de haut) — c'est ce
 * qui garantit l'alignement des noms de modèle entre colonnes, quelle que
 * soit la maturité des données par modèle.
 *
 * ## Les trois états
 *
 * ### 1. Biais significatif — [ModelBiasChip] variant coloré
 * Palette teintée (rouge chaud / bleu froid) + flèche directionnelle + valeur
 * signée. C'est le signal d'alerte — "ce modèle a un biais systématique
 * qu'il faut connaître avant de l'interpréter". Clickable → sheet détaillée.
 *
 * ### 2. Biais calibré — [ModelBiasChip] variant neutre (même fonction)
 * Palette grise + icône plate ("−") + petite valeur signée. Le modèle a
 * accumulé assez de données pour qu'on puisse juger, et son biais est jugé
 * NON significatif. **C'est un signal positif** : ce modèle est fiable. La
 * palette neutre communique "OK, rien à signaler" sans le crier fort — on
 * ne veut pas noyer les vrais chips d'alerte au milieu de badges verts
 * partout. Clickable → sheet détaillée (sparkline plate, éducatif).
 *
 * ### 3. En cours de calibration — [CalibratingChip]
 * Composable séparé (pas de bias à afficher). Rend un placeholder discret
 * — pill vide avec un dash — même hauteur que les autres. Non-clickable :
 * il n'y a pas d'historique à montrer, la sheet serait vide.
 *
 * ## Pourquoi ne pas juste cacher les chips absents ?
 * (Version antérieure du design.) Les headers de colonnes se désalignaient
 * verticalement selon la présence du chip, ce qui rendait la comparaison
 * inter-modèles pénible visuellement. Et surtout, "pas de chip" cachait
 * un signal utile ("ce modèle est calibré, tu peux lui faire confiance")
 * derrière un état ambigu ("... ou peut-être qu'on n'a pas encore les
 * données ?").
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
    // Palette et icône dépendent du niveau de significativité :
    //   - NOT_SIGNIFICANT → toujours neutre (peu importe la direction)
    //   - sinon           → palette selon direction, flèche directionnelle
    val isCalibrated = bias.significance == BiasSignificance.NOT_SIGNIFICANT
    val palette = if (isCalibrated) {
        chipPalette(BiasDirection.NEUTRAL)
    } else {
        chipPalette(bias.direction)
    }
    val icon = if (isCalibrated) {
        // Coche "✓" plutôt qu'un dash "—" : évite le doublon visuel "— −0.1°"
        // quand la magnitude est négative (le lecteur voit deux tirets d'affilée).
        // Sémantiquement plus juste aussi : "validé/vérifié", signal positif clair.
        Icons.Filled.Check
    } else {
        arrowIconFor(bias.direction)
    }
    val label = formatBiasLabel(bias)
    val a11y = biasContentDescription(bias)

    Row(
        modifier = modifier
            .height(22.dp)
            .background(palette.background, RoundedCornerShape(999.dp))
            .border(1.dp, palette.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            // Padding H réduit 7→5dp — libère 4dp au total pour le texte.
            .padding(horizontal = 5.dp, vertical = 2.dp)
            .semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        // Spacing 3→2dp — libère 1dp supplémentaire.
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // décrit via le semantics parent
            tint = palette.foreground,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = label,
            color = palette.foreground,
            style = MaterialTheme.typography.labelSmall,
            // Monospace RETIRÉ — les glyphes proportionnels sont ~40% plus étroits
            // sur labelSmall, ce qui garantit qu'"+15km/h" tienne dans le budget
            // de 44dp du chip. L'alignement des décimales entre chips était le
            // seul argument monospace, mais chaque chip est dans SA colonne
            // (modèle) donc l'alignement inter-chip ne se voit pas.
            fontWeight = FontWeight.Medium,
            // Force le rendu sur UNE seule ligne, sans coupure au milieu du
            // texte. Si le texte dépasse tout de même (ne devrait pas après
            // les optim ci-dessus), il déborde à droite sans wrap — clippé
            // par le Column parent, mais au moins pas invisible sur une
            // deuxième ligne inatteignable (Row height=22dp).
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Placeholder discret pour un modèle dont on n'a pas encore assez de données
 * pour calculer un biais (< 14 samples exploitables). Rendu comme une pill
 * vide avec un dash centré — même hauteur (22dp) que [ModelBiasChip], donc
 * l'alignement vertical des noms de modèle en header est préservé.
 *
 * Non-clickable : ouvrir la sheet ne montrerait rien d'exploitable (pas de
 * sparkline, pas de stats). L'utilisateur qui tape dessus n'aurait qu'à
 * fermer une modale vide — mieux vaut ne pas y aller.
 *
 * La palette est encore plus atténuée que la palette NEUTRAL du chip calibré :
 * on veut que le lecteur distingue au premier coup d'œil "calibré (info
 * positive)" de "en attente (info neutre en attente)". Le `defaultMinSize`
 * assure une largeur minimale approchant celle des chips remplis pour un
 * rendu visuel plus homogène.
 */
@Composable
internal fun CalibratingChip(
    sampleCount: Int? = null,
    modifier: Modifier = Modifier
) {
    // Affichage progressif dès qu'on connaît le nombre d'échantillons collectés
    // — même si N = 0. "0/14" au jour 1 est plus informatif qu'un "—" : ça dit
    // à l'utilisateur "le système te suit, tu es à 0 collectés, il en faut 14".
    // Le "—" est réservé au cas où l'appelant ne fournit AUCUN count (null),
    // par ex. un usage futur du chip hors du pipeline biais standard.
    val showProgress = sampleCount != null
    val a11y = if (showProgress) {
        stringResource(
            R.string.bias_chip_calibrating_progress_a11y,
            sampleCount!!,
            com.meteocompare.app.domain.model.ModelBias.MIN_SAMPLES_FOR_BIAS
        )
    } else {
        stringResource(R.string.bias_chip_calibrating_content_description)
    }
    Row(
        modifier = modifier
            .height(22.dp)
            .defaultMinSize(minWidth = 40.dp)
            .background(CalibratingPalette.background, RoundedCornerShape(999.dp))
            .border(1.dp, CalibratingPalette.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (showProgress) {
                "${sampleCount!!}/${com.meteocompare.app.domain.model.ModelBias.MIN_SAMPLES_FOR_BIAS}"
            } else {
                "—"
            },
            color = CalibratingPalette.foreground,
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

/**
 * Palette du chip "en cours de calibration" — encore plus atténuée que la
 * palette NEUTRAL des chips calibrés. On veut une hiérarchie visuelle claire :
 * plein > neutre > vide/en attente.
 */
private val CalibratingPalette = ChipPalette(
    background = Color(0x0D6B7280), // ~5% alpha du gris
    border = Color(0x146B7280),     // ~8% alpha
    foreground = Color(0xFF9CA3AF)  // gris plus clair que NEUTRAL
)

private fun arrowIconFor(direction: BiasDirection): ImageVector = when (direction) {
    BiasDirection.WARM    -> Icons.Filled.ArrowUpward
    BiasDirection.COLD    -> Icons.Filled.ArrowDownward
    BiasDirection.NEUTRAL -> Icons.Filled.Remove
}

/**
 * Formatte "+1,5°" / "−0,4mm" / "+15km/h".
 *
 * Le séparateur décimal suit `Locale.getDefault()` via `String.format` — pas
 * de `.replace('.', ',')` forcé (bug antérieur qui affichait une virgule
 * même en anglais).
 *
 * ─── Format compact "sans espace avant l'unité" ────────────────────────────
 * Le chip vit dans un header de colonne de 72dp (moins padding = 68dp utiles).
 * Un chip contient icon 11dp + spacing 3dp + padding H 10dp + text. Le budget
 * text est donc ~44dp. En monospace labelSmall, chaque char = ~7dp → 6 chars
 * max. La forme "+0.5 mm" fait 7 chars → clippée à droite ("+0.5 m"). En
 * retirant l'espace → "+0.5mm" (6 chars) tient tout juste, "+15km/h" (7 chars)
 * a besoin d'une seconde optim (font proportionnelle côté chip).
 *
 * Note : ne PAS supprimer l'espace côté a11y — `biasContentDescription` utilise
 * ses propres phrases localisées ("de 1,5 millimètres en moyenne"), donc le
 * format visuel compact n'impacte pas TalkBack.
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
    // Unités accolées sans espace pour économiser 1 char de largeur — critique
    // dans le contexte de header 72dp.
    val unit = when (bias.variable) {
        BiasVariable.TEMPERATURE   -> "°"
        BiasVariable.PRECIPITATION -> "mm"
        BiasVariable.WIND_SPEED    -> "km/h"
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
