package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.meteocompare.app.domain.model.BiasDirection
import com.meteocompare.app.domain.model.BiasSignificance
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel

/**
 * Bottom sheet Material 3 qui présente le détail d'un biais modèle × variable.
 *
 * Cette itération (Phase 1 UI) inclut : titre "sec" centré sur la donnée,
 * grille de statistiques, texte explicatif. La zone du sparkline (30 jours
 * forecast vs observation) est réservée mais rendue en placeholder — sera
 * complétée à l'itération suivante.
 *
 * Rappel des trois choix produit qui pilotent cette UI (validés) :
 *   1. Titre "sec direct qui se focalise sur la donnée" — pas d'éditorial.
 *   2. Un biais PAR variable, dans le contexte de son tableau propre.
 *   3. Brut par défaut dans les tableaux — la sheet ne propose pas de bascule
 *      "afficher les valeurs corrigées" (jamais dans le MVP).
 *
 * @param selection modèle + biais à afficher, ou `null` pour ne pas afficher
 *   la sheet. Passer `null` équivaut à ne pas monter le composant.
 * @param onDismiss callback quand l'utilisateur ferme la sheet (drag down,
 *   scrim tap, ou système).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelBiasDetailSheet(
    selection: BiasSelection?,
    onDismiss: () -> Unit
) {
    if (selection == null) return

    // sheetState créé en interne pour ne pas exposer le type SheetState
    // (experimental Material3) dans la signature publique de la fonction —
    // le compilateur remonte l'opt-in au call-site autrement. Aucune raison
    // produit de laisser le caller customiser l'état pour l'instant ; on
    // rétablira le paramètre le jour où on en aura besoin, avec un @OptIn
    // explicite côté caller à ce moment-là.
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
            SheetEyebrow(selection.model, selection.bias.variable)
            Spacer(Modifier.height(6.dp))
            SheetTitle(selection.bias)
            Spacer(Modifier.height(14.dp))
            SparklinePlaceholder(selection.bias.direction)
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
    val bias: ModelBias
)

@Composable
private fun SheetEyebrow(model: WeatherModel, variable: BiasVariable) {
    val variableLabel = when (variable) {
        BiasVariable.TEMPERATURE -> "Biais température"
        BiasVariable.PRECIPITATION -> "Biais précipitations"
        BiasVariable.WIND_SPEED -> "Biais vent"
    }
    Text(
        text = "${model.displayName}  ·  $variableLabel  ·  30 jours",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

/**
 * Titre "sec direct centré sur la donnée" — décision produit validée.
 * Rendu en annotatedString pour teinter le nombre (WARM = rouge, COLD = bleu)
 * cohérent avec le chip — l'œil relie immédiatement la sheet à ce sur quoi
 * elle a été tapée.
 */
@Composable
private fun SheetTitle(bias: ModelBias) {
    val palette = chipPalette(bias.direction)
    val magnitude = formatBiasLabel(bias)

    val referenceWord = when (bias.direction) {
        BiasDirection.WARM -> "par rapport à l'observation."
        BiasDirection.COLD -> "par rapport à l'observation."
        BiasDirection.NEUTRAL -> "par rapport à l'observation."
    }

    val annotated = buildAnnotatedString {
        append("Écart moyen ")
        withStyle(
            SpanStyle(
                color = palette.foreground,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        ) { append(magnitude) }
        append(" $referenceWord")
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.headlineSmall,
        lineHeight = MaterialTheme.typography.headlineSmall.lineHeight
    )
}

/**
 * Placeholder pour le sparkline 30j — réservé à l'itération suivante.
 *
 * Rendu : rectangle arrondi tinté selon la direction (rouge léger pour WARM,
 * bleu léger pour COLD) avec la mention "Graphique 30 jours" au centre. C'est
 * volontairement discret — on garde la structure de la sheet en place sans
 * prétendre à un rendu final. En Phase 1.5, on remplace ce composable par
 * `BiasSparkline(bias, dailyForecast, dailyObservation, animate = true)`.
 */
@Composable
private fun SparklinePlaceholder(direction: BiasDirection) {
    val palette = chipPalette(direction)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(palette.background, RoundedCornerShape(8.dp)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = "Graphique 30 jours — à venir",
            style = MaterialTheme.typography.labelMedium,
            color = palette.foreground,
            fontWeight = FontWeight.Medium
        )
    }
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
                label = "Écart moyen",
                value = formatBiasLabel(bias),
                emphasize = true,
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "Écart-type",
                value = "%.1f".format(bias.stdDev).replace('.', ',') + unitFor(bias.variable),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            StatCell(
                label = "Jours de données",
                value = "${bias.sampleSize} / ${bias.windowDays}",
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "Significativité",
                value = when (bias.significance) {
                    BiasSignificance.HIGH -> "Élevée"
                    BiasSignificance.MODERATE -> "Modérée"
                    BiasSignificance.NOT_SIGNIFICANT -> "Faible"
                },
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
 * Texte explicatif du "pourquoi physique" du biais. Rendu court volontairement
 * — cette info est un bonus pédagogique, pas la donnée principale (qui est
 * dans le titre et la grille).
 *
 * Phase 1 : texte générique par direction. Phase 2 (Repository de biais réel),
 * le texte pourra être adapté par modèle si utile — mais probablement
 * over-engineering, un texte générique reste utile.
 */
@Composable
private fun Explainer(selection: BiasSelection) {
    val text = remember(selection) { buildExplainerText(selection) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    )
}

private fun buildExplainerText(selection: BiasSelection): String {
    val name = selection.model.displayName
    val variable = when (selection.bias.variable) {
        BiasVariable.TEMPERATURE -> "la température"
        BiasVariable.PRECIPITATION -> "les précipitations"
        BiasVariable.WIND_SPEED -> "le vent"
    }
    val (verb, hint) = when (selection.bias.direction) {
        BiasDirection.WARM -> "surestime" to "Soustraire mentalement l'écart aux prévisions donne une lecture plus proche du réel."
        BiasDirection.COLD -> "sous-estime" to "Ajouter mentalement l'écart aux prévisions donne une lecture plus proche du réel."
        BiasDirection.NEUTRAL -> "reste calibré" to "Rien à corriger sur cette variable."
    }
    val stability = when (selection.bias.significance) {
        BiasSignificance.HIGH -> "Le biais est consistant sur les 30 derniers jours (écart-type faible par rapport à la magnitude) — le comportement observé n'est pas dû au hasard."
        BiasSignificance.MODERATE -> "Le biais est visible mais modéré — utile à garder en tête sur les décisions serrées."
        BiasSignificance.NOT_SIGNIFICANT -> "Écart proche du bruit journalier — pas d'action nécessaire."
    }
    return "$name $verb systématiquement $variable à cet endroit sur les ${selection.bias.windowDays} derniers jours. " +
            "$hint\n\n$stability"
}