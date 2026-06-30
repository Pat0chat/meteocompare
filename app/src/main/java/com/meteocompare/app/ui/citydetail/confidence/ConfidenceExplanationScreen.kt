package com.meteocompare.app.ui.citydetail.confidence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.ConfidenceScore
import com.meteocompare.app.domain.model.DayConfidence
import com.meteocompare.app.domain.model.PrecipitationConfidence
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.theme.ConfidenceHigh
import com.meteocompare.app.ui.theme.ConfidenceLow
import com.meteocompare.app.ui.theme.ConfidenceMedium
import com.meteocompare.app.ui.theme.color
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// ============================================================================
//  Public screen entry
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfidenceExplanationScreen(
    onBack: () -> Unit,
    viewModel: ConfidenceExplanationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConfidenceExplanationContent(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfidenceExplanationContent(
    state: ConfidenceExplanationUiState,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.confidence_explanation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (state) {
            ConfidenceExplanationUiState.Loading -> LoadingView(padding)
            is ConfidenceExplanationUiState.Error -> ErrorView(state.message, padding)
            is ConfidenceExplanationUiState.Loaded -> LoadedView(state, padding)
        }
    }
}

@Composable
private fun LoadingView(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

// ============================================================================
//  Loaded state — l'écran principal
// ============================================================================

@Composable
private fun LoadedView(state: ConfidenceExplanationUiState.Loaded, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item("header") { HeaderCard(state) }

        // Une section "Card + légende interprétative" par variable. On itère
        // sur `variableBreakdowns` plutôt que d'avoir 4 items "en dur" — si
        // un jour on ajoute une variable (humidité, UV…) ou si une variable
        // manque pour cette date, on n'a rien à toucher ici.
        state.variableBreakdowns.forEach { breakdown ->
            item("var_${breakdown.kind}") {
                VariableSection(
                    breakdown = breakdown,
                    dayConfidence = state.dayConfidence
                )
            }
        }

        // Section éducative — c'est l'edge éditorial : on dit *pourquoi* les
        // modèles diffèrent, pas juste *qu'ils* diffèrent.
        item("why_models_diverge") { WhyModelsDivergeSection(state.contributingModels) }
    }
}

@Composable
private fun HeaderCard(state: ConfidenceExplanationUiState.Loaded) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFmt = remember(locale) {
        DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    }
    val overall = state.dayConfidence.overallPercent

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.city.shortLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = state.date.format(dateFmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            if (overall != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.confidence_overall_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    ConfidenceBadgeReadOnly(overall)
                }
                Spacer(Modifier.height(8.dp))
                // Phrase d'amorce qui contextualise le badge global. Sans elle,
                // l'utilisateur voit un % et ne sait pas comment l'interpréter.
                Text(
                    text = overallVerdict(overall),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun overallVerdict(percent: Int): String = when {
    percent >= 80 -> stringResource(R.string.confidence_verdict_high)
    percent >= 50 -> stringResource(R.string.confidence_verdict_medium)
    else -> stringResource(R.string.confidence_verdict_low)
}

// ============================================================================
//  Sections par variable
// ============================================================================

@Composable
private fun VariableSection(
    breakdown: VariableBreakdown,
    dayConfidence: DayConfidence
) {
    Column {
        SectionTitle(text = variableLabel(breakdown.kind))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Bandeau récapitulatif : que disent les modèles globalement ?
                SummaryLine(breakdown, dayConfidence)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Une ligne par modèle. On affiche le nom (avec son code couleur
                // pour qu'on retrouve la même couleur que dans le chart de
                // comparaison des températures), la résolution (info éditoriale),
                // et la valeur prédite.
                breakdown.perModel.forEach { mv ->
                    ModelValueRow(mv = mv, kind = breakdown.kind)
                    if (mv != breakdown.perModel.last()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Phrase d'interprétation finale — c'est ce qui transforme
                // "voilà les chiffres" en "voilà ce que ça veut dire".
                val interpretation = interpretationFor(breakdown, dayConfidence)
                if (interpretation != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = interpretation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(breakdown: VariableBreakdown, dayConfidence: DayConfidence) {
    // On extrait d'abord la paire (texte, percent) via un when sans @Composable
    // *inside* let/lambdas — les helpers sont appelés directement au niveau
    // body de cette fonction @Composable. Pattern important : si on mettait
    // les appels @Composable dans un `?.let { ... }`, le compilateur Compose
    // refuserait au prétexte que la lambda de `let` n'est pas elle-même
    // @Composable. Plus simple : early-return par variable.
    val tempMax = dayConfidence.tempMax
    val tempMin = dayConfidence.tempMin
    val windMax = dayConfidence.windMax
    val precip = dayConfidence.precipitation

    val text: String
    val percent: Int
    when (breakdown.kind) {
        VariableKind.TEMP_MAX -> {
            if (tempMax == null) return
            text = continuousSummary(tempMax, "°")
            percent = tempMax.percent
        }
        VariableKind.TEMP_MIN -> {
            if (tempMin == null) return
            text = continuousSummary(tempMin, "°")
            percent = tempMin.percent
        }
        VariableKind.WIND_MAX -> {
            if (windMax == null) return
            text = continuousSummary(windMax, " km/h")
            percent = windMax.percent
        }
        VariableKind.PRECIPITATION -> {
            if (precip == null) return
            text = precipitationSummary(precip)
            percent = precip.percent
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        ConfidencePillReadOnly(percent)
    }
}

@Composable
private fun continuousSummary(score: ConfidenceScore, unit: String): String {
    return if (score.spread <= 1.0) {
        stringResource(
            R.string.confidence_summary_converged,
            score.meanValue.roundToInt(),
            unit
        )
    } else {
        stringResource(
            R.string.confidence_summary_spread,
            score.minValue.roundToInt(),
            score.maxValue.roundToInt(),
            unit,
            score.spread.roundToInt()
        )
    }
}

@Composable
private fun precipitationSummary(p: PrecipitationConfidence): String = when (p) {
    is PrecipitationConfidence.NoRain -> stringResource(R.string.confidence_precip_all_dry)
    is PrecipitationConfidence.Rain -> stringResource(
        R.string.confidence_precip_all_rain,
        p.minMm.roundToInt(),
        p.maxMm.roundToInt()
    )
    is PrecipitationConfidence.Divided -> stringResource(
        R.string.confidence_precip_divided,
        p.modelsForRain,
        p.modelCount
    )
}

@Composable
private fun ModelValueRow(mv: ModelValue, kind: VariableKind) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot couleur — même teinte que dans le chart température, pour que
        // l'utilisateur fasse le lien visuel entre les deux écrans.
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(mv.model.color(), CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mv.model.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    R.string.confidence_model_resolution,
                    formatResolution(mv.model.resolutionKm)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatValue(mv.value, kind),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatValue(value: Double, kind: VariableKind): String = when (kind) {
    VariableKind.TEMP_MAX, VariableKind.TEMP_MIN -> "${value.roundToInt()}°"
    VariableKind.WIND_MAX -> "${value.roundToInt()} km/h"
    // Pluie : 1 décimale pour les faibles quantités (0.3 mm), entier au-delà.
    // Sinon 0.05 mm s'affiche en "0 mm" et perd l'info "léger humide".
    VariableKind.PRECIPITATION -> when {
        value < 0.05 -> "0 mm"
        value < 1.0 -> "%.1f mm".format(value)
        else -> "${value.roundToInt()} mm"
    }
}

/**
 * Formatte la résolution avec une décimale pour < 5 km (les modèles haute
 * résolution méritent leur précision : 1.5 km ≠ 2 km dans la perception),
 * arrondi entier au-delà (la différence entre 11 et 13 km n'est pas
 * sensorielle pour l'utilisateur).
 */
private fun formatResolution(km: Double): String =
    if (km < 5.0) "%.1f km".format(km) else "${km.roundToInt()} km"

@Composable
private fun interpretationFor(
    breakdown: VariableBreakdown,
    dayConfidence: DayConfidence
): String? {
    // On extrait d'abord les Int (data) hors @Composable, on appelle ensuite
    // le @Composable au niveau body. Aucune lambda non-@Composable n'enrobe
    // un appel @Composable.
    val percent: Int? = when (breakdown.kind) {
        VariableKind.TEMP_MAX -> dayConfidence.tempMax?.percent
        VariableKind.TEMP_MIN -> dayConfidence.tempMin?.percent
        VariableKind.WIND_MAX -> dayConfidence.windMax?.percent
        VariableKind.PRECIPITATION -> null // traité plus bas
    }
    if (percent != null) return interpretContinuousByPercent(percent)

    val precip = dayConfidence.precipitation
    if (breakdown.kind == VariableKind.PRECIPITATION && precip != null) {
        return interpretPrecip(precip)
    }
    return null
}

@Composable
private fun interpretContinuousByPercent(percent: Int): String = when {
    percent >= 80 -> stringResource(R.string.confidence_interp_high)
    percent >= 50 -> stringResource(R.string.confidence_interp_medium)
    else -> stringResource(R.string.confidence_interp_low)
}

@Composable
private fun interpretPrecip(p: PrecipitationConfidence): String = when (p) {
    is PrecipitationConfidence.NoRain -> stringResource(R.string.confidence_interp_precip_dry)
    is PrecipitationConfidence.Rain -> stringResource(
        R.string.confidence_interp_precip_rain
    )
    is PrecipitationConfidence.Divided -> stringResource(
        R.string.confidence_interp_precip_divided
    )
}

// ============================================================================
//  Section éducative — "Pourquoi les modèles diffèrent ?"
// ============================================================================

@Composable
private fun WhyModelsDivergeSection(models: List<WeatherModel>) {
    Column {
        SectionTitle(text = stringResource(R.string.confidence_section_why))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Paragraphe pédagogique : qu'est-ce que la résolution ?
                Text(
                    text = stringResource(R.string.confidence_why_resolution_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))

                // Tableau des modèles utilisés ce jour-là, du plus fin au plus
                // grossier. C'est plus impactant que de lister les 8 modèles
                // possibles — on parle de ceux qui ont VRAIMENT contribué.
                models.forEach { model ->
                    ModelResolutionRow(model)
                    if (model != models.last()) Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.confidence_why_resolution_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelResolutionRow(model: WeatherModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(model.color(), CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = model.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatResolution(model.resolutionKm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================================
//  Helpers UI
// ============================================================================

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { heading() }
    )
}

/**
 * Variante non-cliquable du badge du résumé du jour. On ne réutilise pas
 * `ConfidenceBadge` (private dans CityDetailScreen) — recopier 6 lignes est
 * plus simple que de la passer en internal et créer une dépendance UI <→ UI.
 */
@Composable
private fun ConfidenceBadgeReadOnly(percent: Int) {
    val color = when {
        percent >= 80 -> ConfidenceHigh
        percent >= 50 -> ConfidenceMedium
        else -> ConfidenceLow
    }
    Surface(
        color = color,
        modifier = Modifier.clip(MaterialTheme.shapes.small)
    ) {
        Text(
            text = stringResource(R.string.confidence_badge_percent, percent),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ConfidencePillReadOnly(percent: Int) {
    val color = when {
        percent >= 80 -> ConfidenceHigh
        percent >= 50 -> ConfidenceMedium
        else -> ConfidenceLow
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall)
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun variableLabel(kind: VariableKind): String = when (kind) {
    VariableKind.TEMP_MAX -> stringResource(R.string.var_temp_max)
    VariableKind.TEMP_MIN -> stringResource(R.string.var_temp_min)
    VariableKind.PRECIPITATION -> stringResource(R.string.var_precipitation)
    VariableKind.WIND_MAX -> stringResource(R.string.var_wind_max)
}
