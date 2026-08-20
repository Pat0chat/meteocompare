package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasDirection
import com.meteocompare.app.domain.model.BiasSignificance
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import kotlin.math.abs

/**
 * Badge compact affichant le biais d'un modèle sous son nom.
 *
 * Le badge reprend le langage visuel des tableaux modernes : une surface
 * tonale discrète, un rayon contenu et un repère sémantique vertical. Il ne
 * possède plus de contour complet ni de forme « pilule », ce qui évite l'effet
 * de composant rapporté dans la colonne figée.
 *
 * Les trois états gardent exactement le même encombrement :
 * - biais significatif : teinte chaude ou froide, flèche et valeur ;
 * - biais calibré : teinte neutre, coche et valeur ;
 * - calibration en cours : teinte très atténuée et progression N/14.
 */
@Composable
internal fun ModelBiasChip(
    bias: ModelBias,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCalibrated = bias.significance == BiasSignificance.NOT_SIGNIFICANT
    val palette = biasChipPalette(
        direction = if (isCalibrated) BiasDirection.NEUTRAL else bias.direction,
        pending = false
    )
    val label = formatBiasLabel(bias)
    val a11y = biasContentDescription(bias)
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier
            .height(20.dp)
            .clip(shape)
            .background(palette.background, shape)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)
            .semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(palette.indicator, RoundedCornerShape(999.dp))
        )
        Text(
            text = label,
            color = palette.foreground,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum"
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * État de calibration, dessiné avec le même badge intégré que le biais final.
 * Le repère latéral très atténué indique que l'information est encore en cours
 * de constitution sans concurrencer les états significatifs.
 */
@Composable
internal fun CalibratingChip(
    modifier: Modifier = Modifier,
    sampleCount: Int? = null
) {
    val showProgress = sampleCount != null
    val progress = sampleCount ?: 0
    val palette = biasChipPalette(BiasDirection.NEUTRAL, pending = true)
    val a11y = if (showProgress) {
        stringResource(
            R.string.bias_chip_calibrating_progress_a11y,
            progress,
            ModelBias.MIN_SAMPLES_FOR_BIAS
        )
    } else {
        stringResource(R.string.bias_chip_calibrating_content_description)
    }
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier
            .height(20.dp)
            .defaultMinSize(minWidth = 40.dp)
            .background(palette.background, shape)
            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
            .semantics { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(10.dp)
                .background(palette.indicator, RoundedCornerShape(999.dp))
        )
        Text(
            text = if (showProgress) "$progress/${ModelBias.MIN_SAMPLES_FOR_BIAS}" else "—",
            color = palette.foreground,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum"
            ),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

internal data class ChipPalette(
    val background: Color,
    val indicator: Color,
    val foreground: Color
)

/** Palette adaptative au thème clair/sombre et à Material You. */
@Composable
internal fun biasChipPalette(
    direction: BiasDirection,
    pending: Boolean
): ChipPalette {
    val scheme = MaterialTheme.colorScheme
    val accent = when (direction) {
        BiasDirection.WARM -> scheme.error
        BiasDirection.COLD -> scheme.primary
        BiasDirection.NEUTRAL -> scheme.onSurfaceVariant
    }
    val surface = scheme.surfaceContainerHigh
    val backgroundAlpha = when {
        pending -> 0.045f
        direction == BiasDirection.NEUTRAL -> 0.07f
        else -> 0.11f
    }
    val indicatorAlpha = when {
        pending -> 0.28f
        direction == BiasDirection.NEUTRAL -> 0.52f
        else -> 0.88f
    }
    val foreground = when {
        pending -> scheme.onSurfaceVariant.copy(alpha = 0.62f)
        direction == BiasDirection.NEUTRAL -> scheme.onSurfaceVariant
        else -> accent
    }

    return ChipPalette(
        background = accent.copy(alpha = backgroundAlpha).compositeOver(surface),
        indicator = accent.copy(alpha = indicatorAlpha),
        foreground = foreground
    )
}

/**
 * Formatte "+1,5°" / "−0,4mm" / "+15km/h".
 *
 * Le séparateur décimal suit `Locale.getDefault()` via `String.format` — pas
 * de `.replace('.', ',')` forcé (bug antérieur qui affichait une virgule
 * même en anglais).
 *
 * ─── Format compact "sans espace avant l'unité" ────────────────────────────
 * Le badge vit dans une première colonne volontairement compacte. Les unités
 * restent accolées à la valeur afin de préserver une lecture sur une seule ligne
 * même pour le vent (par exemple "+15km/h"). Les chiffres tabulaires assurent
 * un alignement stable sans imposer une police monospace plus large.
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
