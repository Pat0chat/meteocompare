package com.meteocompare.app.ui.citydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.semanticTint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

/**
 * Matrice Heure × Modèle des conditions météo horaires.
 *
 * Pendant hourly du [WeatherByModelTable]. Layout identique dans l'esprit :
 * colonne d'heures figée à gauche + une colonne par modèle scrollables. Le
 * scan horizontal d'une ligne = "à cette heure, que prédisent les modèles ?"
 * — un alignement des icônes signale accord, une divergence saute aux yeux.
 *
 * Différences vs [WeatherByModelTable] :
 *   - Jusqu'à 24 lignes au lieu de 7 (une par heure sur la fin du jour)
 *   - Colonne label plus large pour préfixe jour aux transitions
 *   - Conditions calculées ici depuis la série hourly de chaque modèle
 *     (via weather_code ou fallback précipitation) — pas de use case dédié
 *     dans la ViewModel, la logique reste locale à la vue
 */
@Composable
fun HourlyWeatherByModelTable(
    forecast: CityForecast,
    modifier: Modifier = Modifier
) {
    val zone = remember(forecast.city.timezone) {
        runCatching { ZoneId.of(forecast.city.timezone ?: "UTC") }
            .getOrDefault(ZoneId.of("UTC"))
    }

    val (startHour, endExclusive) = remember(forecast) {
        computeHourlyHorizon(forecast.city.timezone)
    }

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

    // Pré-calcul des conditions + extras Heure × Modèle. Un data holder local
    // au fichier (HourCellData) pour ne pas multiplier les maps parallèles et
    // garder l'accès dans les cellules trivial.
    val cellsByTimestamp: Map<Instant, Map<WeatherModel, HourCellData>> =
        remember(forecast, timestamps) {
            // Pré-calcul : médiane inter-modèles de cloud_cover par timestamp.
            // Utilisé comme 3e fallback dans la boucle ci-dessous — dérive
            // une condition sans-précip quand un modèle n'expose ni
            // weather_code ni précip exploitable (AROME HD sur heure sèche).
            //
            // Médiane robuste aux outliers vs moyenne — pertinent quand
            // 5+ modèles contribuent.
            val medianCloudByTs: Map<Instant, Double> = buildMap {
                for (ts in timestamps) {
                    val values = forecast.seriesByModel.mapNotNull { (_, series) ->
                        val i = series.hourly.timestamps.indexOf(ts)
                        if (i < 0) null else series.hourly.cloudCover.getOrNull(i)
                    }
                    if (values.isNotEmpty()) {
                        val sorted = values.sorted()
                        val median = if (sorted.size % 2 == 1) {
                            sorted[sorted.size / 2].toDouble()
                        } else {
                            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
                        }
                        put(ts, median)
                    }
                }
            }

            timestamps.associateWith { ts ->
                forecast.seriesByModel.mapNotNull { (model, series) ->
                    val idx = series.hourly.timestamps.indexOf(ts)
                    if (idx < 0) return@mapNotNull null
                    val code = series.hourly.weatherCode.getOrNull(idx)
                    // Même logique qu'en daily : (1) weather_code natif,
                    // (2) fallback empirique via précipitation, (3) fallback
                    // ultime peer-consensus cloud_cover — signalé par
                    // isInferred=true pour que l'UI affiche un marqueur.
                    var isInferred = false
                    var condition = WeatherCondition.fromWmoCode(code)
                        ?: WeatherCondition.inferFromPrecipAndTemp(
                            precipMm = series.hourly.precipitation.getOrNull(idx),
                            tempMinC = series.hourly.temperature2m.getOrNull(idx)
                        )
                    if (condition == null) {
                        val medianCloud = medianCloudByTs[ts]
                        if (medianCloud != null) {
                            condition = WeatherCondition.fromCloudCover(medianCloud)
                            isInferred = true
                        }
                    }
                    if (condition == null) return@mapNotNull null
                    val precipProb = series.hourly.precipitationProbability.getOrNull(idx)
                    val cloudCover = series.hourly.cloudCover.getOrNull(idx)
                    model to HourCellData(
                        condition = condition,
                        precipProbability = precipProb,
                        cloudCover = cloudCover,
                        isInferred = isInferred
                    )
                }.toMap()
            }
        }

    // Si aucune donnée d'aucun modèle sur toute la fenêtre : message plutôt
    // qu'un tableau vide qui donnerait l'impression d'un bug de rendu.
    if (timestamps.isEmpty() || models.isEmpty() ||
        cellsByTimestamp.values.all { it.isEmpty() }) {
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
    val currentHourBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val currentHourInstant = timestamps.first()

    fun bgFor(idx: Int, ts: Instant): Color = when {
        ts == currentHourInstant -> currentHourBg
        idx % 2 == 1 -> rowAltBg
        else -> Color.Transparent
    }

    val locale = LocalConfiguration.current.locales[0]
    val hourFmt = remember(locale) { DateTimeFormatter.ofPattern("HH'h'", locale) }

    Row(modifier = modifier.fillMaxWidth()) {
        // Colonne figée : labels d'heure. 84dp comme HourlyForecastTable pour
        // uniformité visuelle entre les tables hourly.
        Column(modifier = Modifier.width(84.dp)) {
            HourHeaderCellBlank(background = headerBg)
            var previousDate: LocalDate? = null
            timestamps.forEachIndexed { idx, ts ->
                val local = ts.atZone(zone)
                val date = local.toLocalDate()
                val showDayPrefix = date != previousDate
                HourLabelCellWeather(
                    hourText = local.format(hourFmt),
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
            modifier = Modifier.height((40 + timestamps.size * 44).dp)
        )

        // Partie scrollable : une colonne par modèle. Cellule 44dp — plus haute
        // que la précédente version (36dp) pour accommoder l'icône 20dp + un
        // badge extra (probabilité de pluie ou couverture nuageuse) sur une
        // ligne en dessous. Reste dense vs les tables daily (52dp) car ici on
        // a beaucoup de lignes (jusqu'à 24) et il faut ménager le scroll.
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            models.forEach { model ->
                Column(modifier = Modifier.width(60.dp)) {
                    ModelHeaderCell(text = model.displayName, background = headerBg)
                    timestamps.forEachIndexed { idx, ts ->
                        HourIconCell(
                            cell = cellsByTimestamp[ts]?.get(model),
                            background = bgFor(idx, ts)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourHeaderCellBlank(background: Color) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(40.dp)
            .background(background)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) { }
}

@Composable
private fun ModelHeaderCell(text: String, background: Color) {
    Box(
        modifier = Modifier
            .width(60.dp)
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

@Composable
private fun HourLabelCellWeather(
    hourText: String,
    dayPrefix: String?,
    background: Color,
    isCurrentHour: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(background)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (dayPrefix != null) {
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

/**
 * Données affichées dans une cellule Heure × Modèle du tableau du temps
 * horaire. Analogue à [com.meteocompare.app.domain.usecase.DayCellExtras]
 * mais tenues LOCALES au composant : ce type n'a pas d'utilité en dehors
 * du rendu ; l'ajouter au domaine créerait un couplage inutile.
 */
private data class HourCellData(
    val condition: WeatherCondition,
    val precipProbability: Int?,
    val cloudCover: Int?,
    /**
     * `true` si la [condition] provient du 3e fallback (médiane peer-consensus
     * de cloud_cover), et pas de la prédiction propre du modèle. L'UI utilise
     * ce flag pour appliquer un alpha réduit — voir [HourIconCell].
     * Défaut à false : les paths (1) weather_code natif et (2) fallback
     * précipitation restent des prédictions propres du modèle.
     */
    val isInferred: Boolean = false
)

@Composable
private fun HourIconCell(cell: HourCellData?, background: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        if (cell == null) {
            // Modèle sans donnée pour cette heure (typique : AROME HD ne
            // couvre que J+0 à J+2, colonnes vides au-delà). Tiret discret
            // pour que la cellule reste reconnaissable comme cellule.
            Text(
                "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Marqueur visuel d'inférence : alpha réduit sur le contenu quand
            // la condition vient de la médiane des peers (pas la prédiction
            // propre du modèle). Voir [HourCellData.isInferred].
            val contentModifier = if (cell.isInferred) Modifier.alpha(0.45f) else Modifier
            Column(
                modifier = contentModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WeatherIconDecorative(
                    condition = cell.condition,
                    size = 20.dp,
                    tint = cell.condition.semanticTint()
                )
                val badge = weatherBadgeFor(
                    condition = cell.condition,
                    precipProbability = cell.precipProbability,
                    cloudCover = cell.cloudCover
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
