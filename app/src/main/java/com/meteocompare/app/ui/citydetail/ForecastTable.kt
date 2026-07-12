package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.components.WindArrow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Style optionnel pour une cellule de valeur. Quand fourni via [ForecastTable.valueStyler],
 * permet de moduler couleur et graisse du texte en fonction de la valeur — par exemple
 * pour rendre les fortes précipitations en bleu foncé gras, ou les vents violents en orange.
 *
 * Si `null` est retourné par le styler pour une valeur donnée, on retombe sur le style
 * neutre (onSurface, FontWeight.Normal). Utile pour ne styliser que les valeurs
 * "remarquables" au-dessus d'un seuil.
 */
data class ValueStyle(
    val color: Color,
    val fontWeight: FontWeight
)

/**
 * Tableau Jour × Modèle pour une variable donnée.
 *
 * Layout :
 *   - Colonne gauche figée avec les dates (largeur 76dp)
 *   - Divider vertical
 *   - Row scrollable horizontalement avec une Column par modèle
 *
 * Chaque cellule fait 64dp de large et 36dp de haut. Les en-têtes ont un fond
 * légèrement teinté pour les distinguer.
 *
 * @param valueExtractor Fonction qui renvoie la valeur (Double?) pour un
 *   modèle et un index de jour donnés. Retourner null laisse une cellule "—".
 * @param valueFormatter Fonction de formatage (ex: `{ "${it.roundToInt()}°" }`).
 * @param valueStyler Optionnel — applique une couleur et graisse selon la valeur,
 *   pour mettre en évidence visuellement les valeurs élevées (pluie forte, vent fort).
 *   `null` (défaut) → style neutre uniforme.
 * @param directionExtractor Optionnel — pour les variables directionnelles (vent).
 *   Retourne les degrés météo (0=N, 90=E, 180=S, 270=O) ou null si la direction
 *   n'a pas de sens pour cette cellule (variable non fournie, ou vent trop faible
 *   pour que la direction soit informative). Quand fourni ET non-null, une flèche
 *   pivotée est rendue avant la valeur. Le caller filtre les vents faibles en
 *   retournant null — voir l'appel de la section wind_table.
 * @param cellWidth Largeur d'une cellule modèle. 72dp par défaut — assez large
 *   pour "0.5 mm" ou "25°" et pour loger un chip de biais sous le nom du
 *   modèle. Passer une valeur plus grande (80dp) pour la colonne vent qui
 *   affiche flèche + valeur + unité : "↗ 120 km/h" wrap à 64dp mais tient à
 *   80dp. La colonne des dates figée à gauche (76dp) reste dimensionnée
 *   séparément — pas de couplage.
 */
@Composable
fun ForecastTable(
    forecast: CityForecast,
    valueExtractor: (DailyForecast, Int) -> Double?,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    directionExtractor: ((DailyForecast, Int) -> Int?)? = null,
    cellWidth: Dp = 72.dp,
    // ── Suivi de biais (Phase 1 UI) — même API que HourlyForecastTable ──
    // Provider optionnel qui associe un modèle à son biais pour la variable
    // affichée. Retour `null` = pas de chip (soit le repo n'a pas de données,
    // soit le biais est NOT_SIGNIFICANT — cas géré côté chip lui-même).
    // Passer non-null active un header plus haut (60dp au lieu de 40dp) pour
    // loger le chip, uniforme sur toutes les colonnes du tableau.
    modelBiasProvider: ((WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    onBiasChipClick: ((WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null,
    // Provider optionnel du nombre de samples déjà collectés par modèle. Sert
    // uniquement à alimenter la progression "N/14" affichée dans le
    // CalibratingChip quand ce modèle n'a pas encore assez de données. Retour
    // 0 (ou provider null) → chip retombe sur le "—" historique. Le count est
    // typiquement dérivé de `VariableBiasState.historyByModel[model]?.size`
    // par le parent — déjà dédupliqué par date, donc la valeur reflète bien
    // le nombre de jours effectivement observés.
    sampleCountProvider: ((WeatherModel) -> Int)? = null
) {
    // Toutes les dates couvertes par au moins un modèle, triées
    val dates = remember(forecast) {
        forecast.seriesByModel.values
            .flatMap { it.daily.dates }
            .distinct()
            .sorted()
    }
    val models = remember(forecast) {
        forecast.seriesByModel.keys.toList().sortedBy { it.ordinal }
    }

    if (dates.isEmpty() || models.isEmpty()) {
        Text(
            stringResource(R.string.no_daily_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val rowAltBg = MaterialTheme.colorScheme.surfaceContainerLow
    // Fond du jour courant. On prend `primaryContainer` avec un alpha
    // modéré : c'est la couleur de la TodaySummaryCard, donc "aujourd'hui"
    // est visuellement cohérent entre les deux vues. Alpha 0.55 :
    //   - assez soutenu pour être perçu comme un highlight distinct de
    //     l'alternance neutre `surfaceContainerLow` des lignes paires
    //   - pas trop opaque pour ne pas écraser le texte au-dessus
    val todayBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    // remember(dates) plutôt qu'un fetch par ligne — LocalDate.now() coûte
    // peu, mais on économise 7 appels + le comparaison LocalDate est claire.
    val today = remember { LocalDate.now() }

    fun bgFor(idx: Int, date: LocalDate): Color = when {
        date == today -> todayBg
        idx % 2 == 1 -> rowAltBg
        else -> Color.Transparent
    }

    // Header height dépend de la présence de chips de biais : 40dp par
    // défaut, 60dp quand un modelBiasProvider est fourni pour loger le chip
    // sous le nom du modèle. Uniforme sur toute la ligne d'en-tête pour
    // garder l'alignement de la grille.
    val headerHeight = if (modelBiasProvider != null) 60.dp else 40.dp

    Row(modifier = modifier.fillMaxWidth()) {
        // Colonne figée des dates
        Column(modifier = Modifier.width(64.dp)) {
            HeaderCell(text = "", background = headerBg, height = headerHeight, modifier = Modifier.width(64.dp))
            dates.forEachIndexed { idx, date ->
                DayLabelCell(
                    date = date,
                    background = bgFor(idx, date),
                    isToday = date == today
                )
            }
        }

        VerticalDivider(
            modifier = Modifier
                .height(headerHeight + (dates.size * 36).dp)
                .padding(vertical = 0.dp)
        )

        // Partie scrollable
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            models.forEach { model ->
                // Résolution du biais pour ce modèle (identique à la version
                // hourly). Callback mémorisé sur (model, bias) pour éviter la
                // recréation à chaque recomposition.
                //
                // ─── inBiasMode vs bias != null ────────────────────────────
                // On ne peut PAS gater l'affichage du chip sur `bias != null`,
                // sinon un modèle qui n'a pas encore accumulé 14 samples
                // (typique : AROME HD — horizon 3 jours + délai ERA5 1 jour
                // → 2 snapshots utiles par fetch au lieu de 6 → collecte
                // beaucoup plus lente que les modèles globaux) ne recevrait
                // JAMAIS le CalibratingChip pédagogique, alors qu'il en a
                // le plus besoin. Le vrai signal "on est en mode biais" est
                // la présence du `modelBiasProvider`, indépendamment de ce
                // qu'il renvoie pour un modèle donné.
                val bias = modelBiasProvider?.invoke(model)
                val inBiasMode = modelBiasProvider != null
                val sampleCount = sampleCountProvider?.invoke(model)
                val chipClick = if (bias != null && onBiasChipClick != null) {
                    remember(model, bias) { { onBiasChipClick(model, bias) } }
                } else null
                Column(modifier = Modifier.width(cellWidth)) {
                    HeaderCell(
                        text = model.displayName,
                        background = headerBg,
                        height = headerHeight,
                        modifier = Modifier.width(cellWidth),
                        bias = bias,
                        onBiasClick = chipClick,
                        showChipSlot = inBiasMode,
                        sampleCount = sampleCount
                    )
                    dates.forEachIndexed { idx, date ->
                        val value = valueAt(forecast, model, date, valueExtractor)
                        val direction = directionExtractor?.let {
                            directionAt(forecast, model, date, it)
                        }
                        ValueCell(
                            text = value?.let(valueFormatter) ?: "—",
                            style = value?.let { v -> valueStyler?.invoke(v) },
                            background = bgFor(idx, date),
                            directionDegrees = direction
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cellule d'en-tête d'une colonne (date vide ou nom de modèle). Deux modes :
 *   1. **Sans biais** (`bias == null`) : Box centrant le texte, hauteur 40dp
 *      par défaut. Comportement historique préservé, comportement identique
 *      pour la colonne de dates figée (qui n'a jamais de biais).
 *   2. **Avec biais actif** : Column, texte en haut + chip optionnel dessous
 *      si le biais est significatif. Hauteur passée par le parent (60dp).
 *
 * L'espace vide sous un nom de modèle sans chip est intentionnel : la
 * hauteur uniforme sur toute la ligne d'entête préserve l'alignement de la
 * grille, et l'absence de chip devient un signal ("ce modèle est calibré").
 */
@Composable
private fun HeaderCell(
    text: String,
    background: Color,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    bias: com.meteocompare.app.domain.model.ModelBias? = null,
    onBiasClick: (() -> Unit)? = null,
    showChipSlot: Boolean = false,
    sampleCount: Int? = null
) {
    Column(
        modifier = modifier
            .height(height)
            .background(background)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            2.dp, Alignment.CenterVertically
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        // Slot chip rendu quand le parent est en "mode biais" (showChipSlot),
        // indépendamment de la disponibilité des données pour CE modèle. Les
        // trois variantes (calibrating / significatif / calibré) ont le même
        // footprint vertical pour préserver l'alignement des noms de modèle
        // à travers les colonnes.
        //
        // sampleCount est passé au CalibratingChip pour afficher la progression
        // "N/14" au lieu du "—" laconique — nettement plus lisible pour comprendre
        // combien de jours d'observation il reste avant que le biais devienne
        // disponible. Si null, le chip retombe sur le comportement historique.
        //
        // Le fallback `onBiasClick ?: {}` gère le cas rare où bias est
        // disponible mais le parent n'a pas fourni de handler de click — le
        // chip reste visuel (info affichée) mais non interactif.
        if (showChipSlot) {
            when {
                bias == null -> CalibratingChip(sampleCount = sampleCount)
                else -> ModelBiasChip(bias = bias, onClick = onBiasClick ?: {})
            }
        }
    }
}

@Composable
private fun DayLabelCell(date: LocalDate, background: Color, isToday: Boolean = false) {
    // Locale courante (mise à jour par AppCompatDelegate.setApplicationLocales).
    // Formatter recréé via `remember(locale)` quand la locale change — sinon
    // on resterait sur le formatter French initial du process.
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d", locale)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(background)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val text = date.format(formatter).replaceFirstChar { it.uppercase() }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            // Bold + couleur primaire pour que le jour courant se repère au
            // scan visuel même quand la teinte de fond est subtile (surtout
            // en thème sombre où primaryContainer.copy(0.55) peut être ténu).
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday)
                MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ValueCell(
    text: String,
    style: ValueStyle?,
    background: Color,
    directionDegrees: Int? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        // Si direction fournie : flèche + valeur en Row côte à côte. Sinon :
        // valeur seule centrée. Pas de padding horizontal explicite entre les
        // deux — le Icon fait déjà 12dp et Row ajoute un mini gap naturel.
        if (directionDegrees != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WindArrow(directionDegrees = directionDegrees, size = 12.dp)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = style?.color ?: Color.Unspecified,
                    fontWeight = style?.fontWeight,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                // Si pas de style fourni → couleur par défaut (onSurface via MaterialTheme).
                // L'utilisation de `Color.Unspecified` indique à Text de prendre la couleur
                // depuis le LocalContentColor courant, ce qui respecte le thème.
                color = style?.color ?: Color.Unspecified,
                fontWeight = style?.fontWeight
            )
        }
    }
}

private fun valueAt(
    forecast: CityForecast,
    model: WeatherModel,
    date: LocalDate,
    extractor: (DailyForecast, Int) -> Double?
): Double? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.daily.dates.indexOf(date)
    if (idx < 0) return null
    return extractor(series.daily, idx)
}

private fun directionAt(
    forecast: CityForecast,
    model: WeatherModel,
    date: LocalDate,
    extractor: (DailyForecast, Int) -> Int?
): Int? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.daily.dates.indexOf(date)
    if (idx < 0) return null
    return extractor(series.daily, idx)
}
