package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.BiasDirection
import com.meteocompare.app.domain.model.BiasSignificance
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel

/**
 * Bottom sheet Material 3 qui présente le détail d'un biais modèle × variable.
 *
 * Phase 1 UI (cette itération) : titre "sec" centré sur la donnée, grille de
 * stats, texte explicatif. Sparkline 30j en placeholder — remplacé Phase 1.5.
 *
 * Toutes les chaînes utilisateur passent par stringResource — la sheet est
 * pleinement localisée FR/EN. Formatage numérique via `String.format` sans
 * `.replace('.', ',')` forcé, donc le séparateur décimal suit la locale du
 * device (virgule en FR, point en EN).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelBiasDetailSheet(
    selection: BiasSelection?,
    onDismiss: () -> Unit
) {
    if (selection == null) return

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp)
        ) {
            SheetEyebrow(selection.model, selection.bias.variable, selection.bias.windowDays)
            Spacer(Modifier.height(6.dp))
            SheetTitle(selection.bias)
            Spacer(Modifier.height(14.dp))
            BiasSparkline(
                forecast = selection.dailyForecast,
                observation = selection.dailyObservation,
                direction = selection.bias.direction,
                yDomainMin = selection.yDomainMin,
                yDomainMax = selection.yDomainMax
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatsGrid(selection.bias)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            Explainer(selection)
        }
    }
}

/** Paire modèle + biais que la sheet affiche. Immutable → stable pour Compose. */
@androidx.compose.runtime.Immutable
internal data class BiasSelection(
    val model: WeatherModel,
    val bias: ModelBias,
    // ── Données pour le sparkline ──
    // Populated côté caller à l'ouverture de la sheet. Chronologique, index 0
    // = J−(size−1), dernier index = aujourd'hui. Même taille des deux séries.
    val dailyForecast: List<Double>,
    val dailyObservation: List<Double>,
    // Bornes Y communes à tous les modèles de la même variable (calculées sur
    // l'union des séries) — permet la comparaison visuelle inter-modèles.
    val yDomainMin: Double,
    val yDomainMax: Double
)

@Composable
private fun SheetEyebrow(model: WeatherModel, variable: BiasVariable, windowDays: Int) {
    val variableLabel = stringResource(sheetVariableLabelResId(variable))
    Text(
        text = stringResource(
            R.string.bias_sheet_eyebrow,
            model.displayName,
            variableLabel,
            windowDays
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

/**
 * Titre "sec direct centré sur la donnée" — décision produit validée.
 * La magnitude teintée selon la direction relie visuellement la sheet au chip
 * qui a été tapé. Prefix + suffix en stringResource pour permettre la
 * localisation FR/EN sans casser la mise en couleur du milieu.
 */
@Composable
private fun SheetTitle(bias: ModelBias) {
    // Palette alignée sur celle du chip qui vient d'être tapé : neutre (gris)
    // pour un modèle calibré, teintée warm/cold sinon. Sans cette symétrie,
    // taper un chip gris ouvrirait une sheet dont le titre est teinté rouge
    // ou bleu — dissonance visuelle, l'utilisateur croirait tout à coup que
    // le modèle est problématique.
    val isCalibrated = bias.significance == BiasSignificance.NOT_SIGNIFICANT
    val palette = if (isCalibrated) {
        biasChipPalette(BiasDirection.NEUTRAL, pending = false)
    } else {
        biasChipPalette(bias.direction, pending = false)
    }
    val magnitude = formatBiasLabel(bias)
    val prefix = stringResource(R.string.bias_sheet_title_prefix)
    val suffix = stringResource(R.string.bias_sheet_title_suffix)

    val annotated = buildAnnotatedString {
        append(prefix)
        append(" ")
        withStyle(
            SpanStyle(
                color = palette.foreground,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        ) { append(magnitude) }
        append(" ")
        append(suffix)
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.headlineSmall,
        lineHeight = MaterialTheme.typography.headlineSmall.lineHeight
    )
}

/** Grille 2×2 des statistiques principales, style "instrument de mesure". */
@Composable
private fun StatsGrid(bias: ModelBias) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatCell(
                label = stringResource(R.string.bias_stat_mean),
                value = formatBiasLabel(bias),
                emphasize = true,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = stringResource(R.string.bias_stat_stddev),
                value = "%.1f".format(bias.stdDev) + unitFor(bias.variable),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatCell(
                label = stringResource(R.string.bias_stat_sample_size),
                value = stringResource(
                    R.string.bias_stat_sample_size_value,
                    bias.sampleSize,
                    bias.windowDays
                ),
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = stringResource(R.string.bias_stat_significance),
                value = stringResource(significanceLabelResId(bias.significance)),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun unitFor(v: BiasVariable): String = when (v) {
    BiasVariable.TEMPERATURE -> "°"
    BiasVariable.PRECIPITATION -> " mm"
    BiasVariable.WIND_SPEED -> " km/h"
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Texte explicatif du "pourquoi" du biais.
 *
 * Deux chemins distincts :
 *
 * - **Biais significatif** — chemin historique : 2 paragraphes (opener +
 *   hint, puis stability). L'opener utilise le verbe directionnel
 *   ("surestime" / "sous-estime") pour dire ce que le modèle fait de mal
 *   et le hint dit comment le corriger mentalement.
 *
 * - **Calibré (NOT_SIGNIFICANT)** — chemin dédié : une seule phrase positive.
 *   L'ancien texte reprenait le verbe directionnel ("surestime") alors que
 *   le modèle est en fait calibré — mensonger et anxiogène pour rien.
 *   Le nouveau message assume que le petit écart existe (le titre le montre
 *   déjà chiffré) et communique le message important : c'est du bruit,
 *   aucune correction à faire.
 */
@Composable
private fun Explainer(selection: BiasSelection) {
    val name = selection.model.displayName

    if (selection.bias.significance == BiasSignificance.NOT_SIGNIFICANT) {
        val variable = stringResource(sheetExplainerVariableResId(selection.bias.variable))
        Text(
            text = stringResource(
                R.string.bias_explainer_calibrated,
                name, variable, selection.bias.windowDays
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
        )
        return
    }

    val verb = stringResource(explainerVerbResId(selection.bias.direction))
    val variable = stringResource(sheetExplainerVariableResId(selection.bias.variable))
    val opener = stringResource(
        R.string.bias_explainer_opener,
        name, verb, variable, selection.bias.windowDays
    )
    val stability = stringResource(explainerStabilityResId(selection.bias.significance))

    Text(
        text = "$opener $stability",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    )
}

// ─── Res id resolvers (pas @Composable, appelables depuis remember blocks) ──

private fun sheetVariableLabelResId(v: BiasVariable): Int = when (v) {
    BiasVariable.TEMPERATURE   -> R.string.bias_sheet_variable_temperature
    BiasVariable.PRECIPITATION -> R.string.bias_sheet_variable_precipitation
    BiasVariable.WIND_SPEED    -> R.string.bias_sheet_variable_wind_speed
}

private fun sheetExplainerVariableResId(v: BiasVariable): Int = when (v) {
    BiasVariable.TEMPERATURE   -> R.string.bias_variable_temperature
    BiasVariable.PRECIPITATION -> R.string.bias_variable_precipitation
    BiasVariable.WIND_SPEED    -> R.string.bias_variable_wind_speed
}

private fun significanceLabelResId(s: BiasSignificance): Int = when (s) {
    BiasSignificance.HIGH             -> R.string.bias_significance_high
    BiasSignificance.MODERATE         -> R.string.bias_significance_moderate
    BiasSignificance.NOT_SIGNIFICANT  -> R.string.bias_significance_not_significant
}

private fun explainerVerbResId(d: BiasDirection): Int = when (d) {
    BiasDirection.WARM    -> R.string.bias_verb_overestimates
    BiasDirection.COLD    -> R.string.bias_verb_underestimates
    BiasDirection.NEUTRAL -> R.string.bias_verb_neutral
}

private fun explainerStabilityResId(s: BiasSignificance): Int = when (s) {
    BiasSignificance.HIGH             -> R.string.bias_stability_high
    BiasSignificance.MODERATE         -> R.string.bias_stability_moderate
    BiasSignificance.NOT_SIGNIFICANT  -> R.string.bias_stability_not_significant
}
