package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
 * @param valueStyler Optionnel — applique une couleur et graisse selon la valeur.
 * @param directionExtractor Optionnel — pour les variables directionnelles.
 *   Retourne les degrés météo (0=N, 90=E, 180=S, 270=O) ou null si non
 *   applicable (variable absente, ou vent trop faible pour être informatif).
 *   Symétrique du `directionExtractor` de [ForecastTable] (version daily).
 * @param cellWidth Largeur d'une cellule modèle. 60dp par défaut — assez large
 *   pour "0.5" ou "22°" sans unité. Passer une valeur plus grande pour la
 *   colonne vent qui affiche flèche + valeur + unité "km/h" (76dp donne
 *   suffisamment de marge pour "↗ 120 km/h"). La colonne label figée à gauche
 *   (84dp) reste dimensionnée séparément.
 */
@Composable
fun HourlyForecastTable(
    forecast: CityForecast,
    valueExtractor: (HourlyForecast, Int) -> Double?,
    valueFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    valueStyler: ((Double) -> ValueStyle?)? = null,
    directionExtractor: ((HourlyForecast, Int) -> Int?)? = null,
    cellWidth: Dp = 60.dp
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

    Row(modifier = modifier.fillMaxWidth()) {
        // Colonne figée : labels d'heure. 84dp accommode "Lun. 14h" en français
        // (préfixe jour visible aux changements de jour uniquement).
        Column(modifier = Modifier.width(84.dp)) {
            HourHeaderCell(background = headerBg)
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
            modifier = Modifier.height((40 + timestamps.size * 32).dp)
        )

        // Partie scrollable : une colonne par modèle. Cellules à `cellWidth`
        // (défaut 60dp) — plus étroit que le tableau daily (64dp) pour compenser
        // la colonne label plus large. Aération verticale (32 vs 36) pour tenir
        // compte du nombre de lignes plus élevé. Le caller peut passer une
        // valeur plus grande pour les colonnes vent (unité "km/h" + flèche).
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            models.forEach { model ->
                Column(modifier = Modifier.width(cellWidth)) {
                    HourModelHeaderCell(
                        text = model.displayName,
                        background = headerBg,
                        width = cellWidth
                    )
                    timestamps.forEachIndexed { idx, ts ->
                        val value = valueAt(forecast, model, ts, valueExtractor)
                        val direction = directionExtractor?.let {
                            directionAt(forecast, model, ts, it)
                        }
                        HourValueCell(
                            text = value?.let(valueFormatter) ?: "—",
                            style = value?.let { v -> valueStyler?.invoke(v) },
                            background = bgFor(idx, ts),
                            directionDegrees = direction
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourHeaderCell(background: Color) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(40.dp)
            .background(background)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Header vide (le sens de la colonne est évident : ce sont des heures)
    }
}

@Composable
private fun HourModelHeaderCell(text: String, background: Color, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(40.dp)
            .background(background)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
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
                // Flèche minuscule (10dp, plus petit que la version daily 12dp)
                // pour tenir dans la cellule 60dp × 32dp très dense de la table
                // horaire. Le sens : downwind, cohérent avec le tableau daily.
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(10.dp)
                        .rotate(((directionDegrees + 180) % 360).toFloat())
                )
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

// ─── Stylers hourly-spécifiques ────────────────────────────────────────────
//
// Les seuils daily de CityDetailScreen ne se transposent pas tels quels à
// l'horaire :
//   - Précipitations : 5 mm sur UN jour = pluie modérée ; 5 mm en UNE heure =
//     pluie forte (voire orage). Il faut recalibrer.
//   - Vent : la variable est déjà une vitesse instantanée (km/h), pas un cumul.
//     Les seuils daily calibrés sur "vent max de la journée" restent proches
//     de ce qu'on lit dans l'horaire — on peut réutiliser les mêmes.

/**
 * Style de la cellule en fonction des précipitations horaires en mm/h.
 *
 *   - < 0.05 mm/h : null (neutre)
 *   - 0.05–0.5    : bleu clair, Normal        (bruine)
 *   - 0.5–2       : bleu, Medium              (pluie modérée)
 *   - 2–5         : bleu foncé, SemiBold      (pluie forte)
 *   - > 5         : bleu très foncé, Bold     (pluie très forte / orage)
 *
 *  Seuils environ 3-5× plus serrés que le daily : 5 mm/h en UNE heure c'est
 *  déjà de la pluie très forte (échelle Météo-France : > 7,6 mm/h = vigilance).
 */
internal fun hourlyPrecipitationStyle(mm: Double): ValueStyle? = when {
    mm < 0.05 -> null
    mm < 0.5  -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFF4FC3F7), fontWeight = FontWeight.Normal)
    mm < 2.0  -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFF1E88E5), fontWeight = FontWeight.Medium)
    mm < 5.0  -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
    else      -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFF0D47A1), fontWeight = FontWeight.Bold)
}

/**
 * Style de la cellule en fonction du vent instantané en km/h.
 *
 * Mêmes seuils que le daily : la variable a la même unité (km/h) et la même
 * grille d'interprétation Beaufort. La différence "vent max quotidien" vs
 * "vent instantané" ne justifie pas de re-calibrer les 4 paliers.
 */
internal fun hourlyWindStyle(kmh: Double): ValueStyle? = when {
    kmh < 20.0 -> null
    kmh < 40.0 -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFFFFB74D), fontWeight = FontWeight.Normal)
    kmh < 60.0 -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFFFB8C00), fontWeight = FontWeight.Medium)
    kmh < 80.0 -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFFE64A19), fontWeight = FontWeight.SemiBold)
    else       -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFFC62828), fontWeight = FontWeight.Bold)
}

/**
 * Style de la cellule en fonction de la température horaire en °C.
 *
 * Contrairement au tableau min/max daily, on n'a pas de normales horaires
 * (les normales sont par jour). On applique un dégradé sémantique large :
 *
 *   - ≥ 30°  : rouge, SemiBold        (canicule)
 *   - 20-30° : orange clair, Medium   (chaud)
 *   - 5-20°  : null (neutre)          (tempéré, pas de signal)
 *   - 0-5°   : bleu clair, Medium     (frais)
 *   - < 0°   : bleu, SemiBold         (gel)
 *
 *  Ces couleurs sont indépendantes des couleurs "chaud/froid vs normale"
 *  du tableau daily (qui sont relatives). Ici c'est absolu — 25° reste chaud
 *  qu'on soit en été ou dans une vague de chaleur hivernale.
 */
internal fun hourlyTemperatureStyle(celsius: Double): ValueStyle? = when {
    celsius >= 30.0 -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFFE53935), fontWeight = FontWeight.SemiBold)
    celsius >= 20.0 -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFFFF7043), fontWeight = FontWeight.Medium)
    celsius > 5.0   -> null
    celsius >= 0.0  -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFF4FC3F7), fontWeight = FontWeight.Medium)
    else            -> ValueStyle(color = androidx.compose.ui.graphics.Color(0xFF1E88E5), fontWeight = FontWeight.SemiBold)
}
