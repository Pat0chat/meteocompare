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
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.ui.components.WindArrow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

/**
 * Tableau Heure × Modèle pour une variable donnée.
 *
 * Pendant hourly du [ForecastTable] daily. Layout identique dans l'esprit :
 *   - Colonne gauche figée avec les labels d'heure (largeur 84dp pour "Lun. 14h")
 *   - VerticalDivider
 *   - Row scrollable horizontalement avec une Column par modèle
 *
 * Différences vs [ForecastTable] :
 *   - Jusqu'à 24 lignes au lieu de 7 → la colonne label est plus large pour
 *     accueillir le préfixe du jour (visible seulement aux changements de jour
 *     pour économiser la place et clarifier les transitions)
 *   - Highlight de l'heure courante (au lieu de "aujourd'hui")
 *   - Filtre l'horizon via `computeHourlyHorizon(timezone)` — on n'affiche
 *     que de "maintenant" à la fin de demain
 *
 * @param valueExtractor Fonction qui renvoie la valeur (Double?) pour un
 *   modèle et un index d'heure donnés.
 * @param valueFormatter Formatage (ex: `{ "${it.roundToInt()}°" }`).
 * @param valueStyler Optionnel — applique une couleur et graisse au TEXTE
 *   selon la valeur. Mutuellement exclusif avec [heatmapStyler] : si les
 *   deux sont fournis, [heatmapStyler] gagne (sa couleur de contenu écrase
 *   celle du valueStyler pour préserver la lisibilité sur fond coloré).
 * @param heatmapStyler Optionnel — colore le FOND de la cellule selon la
 *   valeur, transformant le tableau en carte thermique (heatmap). C'est le
 *   mode "vue d'ensemble" du tableau hourly : l'œil repère instantanément
 *   les zones "chaudes" (température élevée, forte pluie, vent fort) sans
 *   avoir à lire les valeurs numériques. Le [HeatmapCellStyle.contentColor]
 *   est calculé automatiquement (noir/blanc via [contrastingContentColor])
 *   pour rester lisible quelle que soit la couleur de fond. Quand la lambda
 *   retourne null pour une valeur donnée (ex: sec ou calme), on retombe sur
 *   le fond de rangée alternée classique — utile pour ne signaler que les
 *   valeurs remarquables sans "saturer" la vue.
 * @param directionExtractor Optionnel — pour les variables directionnelles.
 *   Retourne les degrés météo (0=N, 90=E, 180=S, 270=O) ou null si non
 *   applicable (variable absente, ou vent trop faible pour être informatif).
 *   Symétrique du `directionExtractor` de [ForecastTable] (version daily).
 * @param cellWidth Largeur d'une cellule modèle. 72dp par défaut — assez large
 *   pour "0.5 mm" ou "22°" et pour loger un chip de biais sous le nom du
 *   modèle. Passer une valeur plus grande pour la colonne vent qui affiche
 *   flèche + valeur + unité "km/h" (76dp donne suffisamment de marge pour
 *   "↗ 120 km/h"). La colonne label figée à gauche (84dp) reste dimensionnée
 *   séparément.
 */
@Composable
fun HourlyForecastTable(
    forecast: CityForecast,
    valueExtractor: (HourlyForecast, Int) -> Double?,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    heatmapStyler: ((Double) -> HeatmapCellStyle?)? = null,
    directionExtractor: ((HourlyForecast, Int) -> Int?)? = null,
    cellWidth: Dp = 72.dp,
    // ── Suivi de biais (Phase 1 UI) ──
    // Provider optionnel qui associe un modèle à son biais pour la variable
    // affichée. Retour `null` = pas de chip (soit le repo n'a pas de données,
    // soit le biais est NOT_SIGNIFICANT — cas géré côté chip lui-même).
    // Passer non-null active un header plus haut (60dp au lieu de 40dp) pour
    // loger le chip, uniforme sur toutes les colonnes du tableau.
    modelBiasProvider: ((com.meteocompare.app.domain.model.WeatherModel) -> com.meteocompare.app.domain.model.ModelBias?)? = null,
    onBiasChipClick: ((com.meteocompare.app.domain.model.WeatherModel, com.meteocompare.app.domain.model.ModelBias) -> Unit)? = null
) {
    // Fuseau de la ville — sert au filtrage de l'horizon ET au formatage des
    // labels d'heure. Fallback UTC silencieux si timezone invalide, pour ne
    // jamais crasher sur une valeur mal formée renvoyée par l'API.
    val zone = remember(forecast.city.timezone) {
        runCatching { ZoneId.of(forecast.city.timezone ?: "UTC") }
            .getOrDefault(ZoneId.of("UTC"))
    }

    // Fenêtre horaire calculée une fois par forecast — remember(forecast) plutôt
    // que remember{} sans key sinon un refresh renverrait la fenêtre initiale.
    // Note : Instant.now() est recalculé à chaque appel du remember, ce qui
    // colle bien avec "toujours ancrer sur maintenant lors d'un refresh".
    val (startHour, endExclusive) = remember(forecast) {
        computeHourlyHorizon(forecast.city.timezone)
    }

    // Tous les timestamps couverts par au moins un modèle, filtrés et triés.
    // La map "index par timestamp par modèle" est le lookup principal ensuite —
    // O(1) par cellule, vs indexOf qui serait O(n) et donnerait 5×48×48 = 11k
    // comparaisons pour un rendu.
    val timestamps = remember(forecast, startHour, endExclusive) {
        forecast.seriesByModel.values
            .flatMap { it.hourly.timestamps }
            .distinct()
            .sorted()
            .filter { it >= startHour && it < endExclusive }
    }
    val models = remember(forecast) {
        forecast.seriesByModel.keys.toList().sortedBy { it.ordinal }
    }

    if (timestamps.isEmpty() || models.isEmpty()) {
        Text(
            stringResource(R.string.no_hourly_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    val headerBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val rowAltBg = MaterialTheme.colorScheme.surfaceContainerLow
    // Highlight de l'heure courante — cohérent avec le highlight "aujourd'hui"
    // du ForecastTable daily. Alpha 0.55 sur primaryContainer.
    val currentHourBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)

    // "Heure courante" = premier timestamp de la fenêtre (calé sur l'heure
    // pleine dans le fuseau). Comparé par identité d'Instant pour éviter
    // toute erreur de rounding.
    val currentHourInstant = timestamps.first()

    fun bgFor(idx: Int, ts: Instant): Color = when {
        ts == currentHourInstant -> currentHourBg
        idx % 2 == 1 -> rowAltBg
        else -> Color.Transparent
    }

    val locale = LocalConfiguration.current.locales[0]

    // Formatter d'heure au niveau composable — recréé si la locale change
    // (changement de langue). "HH'h'" en français donne "14h", en anglais on
    // adapte au format 12h/24h ambiant (mais Open-Meteo renvoie en 24h natif,
    // on garde HHh partout pour la compacité — 5 caractères max).
    val hourFmt = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }

    // Header height dépend de la présence de chips de biais : 40dp par
    // défaut, 60dp quand un modelBiasProvider est fourni pour loger le chip
    // sous le nom du modèle. La colonne LABEL fixe (gauche) et la colonne
    // MODEL (droite) doivent rester alignées visuellement → même hauteur des
    // deux côtés, uniforme sur toutes les colonnes du tableau.
    val headerHeight = if (modelBiasProvider != null) 60.dp else 40.dp

    Row(modifier = modifier.fillMaxWidth()) {
        // Colonne figée : labels d'heure. 84dp accommode "Lun. 14h" en français
        // (préfixe jour visible aux changements de jour uniquement).
        Column(modifier = Modifier.width(84.dp)) {
            HourHeaderCell(background = headerBg, height = headerHeight)
            var previousDate: java.time.LocalDate? = null
            timestamps.forEachIndexed { idx, ts ->
                val local = ts.atZone(zone)
                val date = local.toLocalDate()
                val showDayPrefix = date != previousDate
                HourLabelCell(
                    hourText = local.format(hourFmt),
                    // Préfixe jour uniquement à la première heure d'un nouveau
                    // jour → l'œil identifie visuellement les transitions sans
                    // que chaque ligne soit surchargée.
                    dayPrefix = if (showDayPrefix) {
                        date.dayOfWeek
                            .getDisplayName(JavaTextStyle.SHORT, locale)
                            .replace(".", "")
                    } else null,
                    background = bgFor(idx, ts),
                    isCurrentHour = ts == currentHourInstant
                )
                previousDate = date
            }
        }

        VerticalDivider(
            modifier = Modifier.height(headerHeight + (timestamps.size * 32).dp)
        )

        // Partie scrollable : une colonne par modèle. Cellules à `cellWidth`
        // (défaut 60dp) — plus étroit que le tableau daily (64dp) pour compenser
        // la colonne label plus large. Aération verticale (32 vs 36) pour tenir
        // compte du nombre de lignes plus élevé. Le caller peut passer une
        // valeur plus grande pour les colonnes vent (unité "km/h" + flèche).
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            models.forEach { model ->
                // Résolution du biais pour ce modèle. remember(bias) plus bas
                // dans HourModelHeaderCell évite la recomposition inutile —
                // ici on lit une fois par render du tableau, ce qui reste
                // O(nb_modèles) et cheap (le provider consulte typiquement
                // une Map pré-calculée dans le ViewModel).
                val bias = modelBiasProvider?.invoke(model)
                // Callback stable : capture model + bias explicitement pour
                // éviter la recréation à chaque recomposition (Compose ne
                // peut inférer la stabilité d'une lambda qui capture bias?).
                val chipClick = if (bias != null && onBiasChipClick != null) {
                    remember(model, bias) { { onBiasChipClick(model, bias) } }
                } else null
                Column(modifier = Modifier.width(cellWidth)) {
                    HourModelHeaderCell(
                        text = model.displayName,
                        background = headerBg,
                        width = cellWidth,
                        height = headerHeight,
                        bias = bias,
                        onBiasClick = chipClick
                    )
                    timestamps.forEachIndexed { idx, ts ->
                        val value = valueAt(forecast, model, ts, valueExtractor)
                        val direction = directionExtractor?.let {
                            directionAt(forecast, model, ts, it)
                        }
                        // Résolution du fond de la cellule VALEUR :
                        //   - Si un heatmap est actif ET renvoie une couleur pour
                        //     cette valeur → cette couleur écrase le fond de rangée
                        //     (y compris le highlight "heure courante" qui reste
                        //     visible sur la colonne label figée à gauche).
                        //   - Sinon → fond de rangée classique (alternance + highlight
                        //     de l'heure courante) via [bgFor].
                        // L'heure courante reste identifiable via la colonne label,
                        // toujours mise en évidence indépendamment du heatmap.
                        val heatmap = value?.let { v -> heatmapStyler?.invoke(v) }
                        val cellBackground = heatmap?.background ?: bgFor(idx, ts)
                        // Style texte : priorité au heatmap.contentColor (contrasté
                        // avec son fond) ; à défaut, on retombe sur le valueStyler
                        // classique s'il est fourni ; sinon couleur par défaut.
                        val textStyle = when {
                            heatmap != null -> ValueStyle(
                                color = heatmap.contentColor,
                                fontWeight = FontWeight.Medium
                            )
                            else -> value?.let { v -> valueStyler?.invoke(v) }
                        }
                        HourValueCell(
                            text = value?.let(valueFormatter) ?: "—",
                            style = textStyle,
                            background = cellBackground,
                            directionDegrees = direction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourHeaderCell(background: Color, height: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(height)
            .background(background)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Header vide (le sens de la colonne est évident : ce sont des heures)
    }
}

/**
 * Cellule d'en-tête d'une colonne modèle. Deux modes de rendu :
 *
 *   1. **Sans biais** (`bias == null` ET `onBiasClick == null`) : hauteur 40dp,
 *      juste le nom du modèle centré (comportement historique, inchangé).
 *   2. **Avec biais actif** : hauteur passée par le parent (60dp typiquement),
 *      nom en haut + éventuel chip en bas. Si `bias.significance` est
 *      NOT_SIGNIFICANT, le chip n'est pas rendu — l'espace reste vide et
 *      communique "modèle bien calibré ici" par l'absence.
 *
 * L'espace vide sous un nom de modèle sans chip est intentionnel : la
 * hauteur uniforme sur toute la ligne d'entête préserve l'alignement de la
 * grille, et l'absence de chip devient un signal (cf. mockup validé).
 */
@Composable
private fun HourModelHeaderCell(
    text: String,
    background: Color,
    width: Dp,
    height: Dp = 40.dp,
    bias: com.meteocompare.app.domain.model.ModelBias? = null,
    onBiasClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .width(width)
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
            // Garde-fou : si un futur modèle a un nom plus long que ne le
            // permet cellWidth, on préfère l'ellipsis à un wrap qui casserait
            // l'alignement vertical de la grille du tableau.
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        // Chip uniquement si (a) le mode "avec biais" est actif — height > 40 —
        // ET (b) le biais existe ET est significatif. Sinon slot vide.
        if (bias != null &&
            bias.significance != com.meteocompare.app.domain.model.BiasSignificance.NOT_SIGNIFICANT &&
            onBiasClick != null
        ) {
            ModelBiasChip(bias = bias, onClick = onBiasClick)
        }
    }
}

/**
 * Cellule de label d'heure — soit "14h" seul (par défaut), soit "Lun. 14h" à
 * la première heure d'un nouveau jour. Le préfixe jour apparaît sur son propre
 * niveau visuel (plus petit, semibold, teinté onSurfaceVariant) au-dessus de
 * l'heure elle-même, pour que le scan vertical reste axé sur "quelle heure".
 */
@Composable
private fun HourLabelCell(
    hourText: String,
    dayPrefix: String?,
    background: Color,
    isCurrentHour: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(background)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (dayPrefix != null) {
            // Deux lignes empilées : jour au-dessus (labelSmall), heure en dessous
            // (bodySmall). Column plutôt qu'AnnotatedString pour un contrôle
            // taille/couleur indépendant par ligne — les AnnotatedStrings ne
            // permettent pas de faire varier la taille de font par span sans
            // manipulations sp.value complexes.
            Column {
                Text(
                    text = dayPrefix,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentHour)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = hourText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentHour)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Text(
                text = hourText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentHour)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HourValueCell(
    text: String,
    style: ValueStyle?,
    background: Color,
    directionDegrees: Int? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (directionDegrees != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Flèche 10dp (plus petite que la version daily 12dp) pour
                // tenir dans la cellule 60dp × 32dp très dense de la table
                // horaire. Le sens : downwind, cohérent avec le tableau daily.
                WindArrow(directionDegrees = directionDegrees, size = 10.dp)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = style?.color ?: Color.Unspecified,
                    fontWeight = style?.fontWeight,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = style?.color ?: Color.Unspecified,
                fontWeight = style?.fontWeight
            )
        }
    }
}

// ─── Value lookup ──────────────────────────────────────────────────────────

/**
 * Trouve la valeur d'un modèle pour un timestamp donné.
 *
 * On matche par égalité stricte d'Instant. Open-Meteo renvoie ses timestamps
 * calés à l'heure pleine UTC, donc tous les modèles devraient partager les
 * MÊMES Instants pour les mêmes heures — pas besoin de recherche floue.
 *
 * Retourne null si le modèle n'a pas cette heure (typique : AROME HD s'arrête
 * à J+2 alors que GFS va à J+16 → les colonnes AROME HD auront "—" au-delà).
 */
private fun valueAt(
    forecast: CityForecast,
    model: com.meteocompare.app.domain.model.WeatherModel,
    timestamp: Instant,
    extractor: (HourlyForecast, Int) -> Double?
): Double? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.hourly.timestamps.indexOf(timestamp)
    if (idx < 0) return null
    return extractor(series.hourly, idx)
}

/** Version Int? de [valueAt] pour les champs directionnels (degrés météo). */
private fun directionAt(
    forecast: CityForecast,
    model: com.meteocompare.app.domain.model.WeatherModel,
    timestamp: Instant,
    extractor: (HourlyForecast, Int) -> Int?
): Int? {
    val series = forecast.seriesByModel[model] ?: return null
    val idx = series.hourly.timestamps.indexOf(timestamp)
    if (idx < 0) return null
    return extractor(series.hourly, idx)
}