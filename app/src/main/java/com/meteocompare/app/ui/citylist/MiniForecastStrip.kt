package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.ui.components.WeatherIconDecorative
import com.meteocompare.app.ui.components.blendedHeatmapColor
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Ruban météo 12 h de la Home.
 *
 * Le fond reste une heatmap de température mais devient une bande continue,
 * sans quadrillage. Six heures sont visibles simultanément ; les six suivantes
 * restent accessibles par scroll horizontal dans le même ruban.
 *
 * Chaque colonne réutilise strictement les agrégats du moteur de consensus :
 * - heure ;
 * - icône de condition météo ;
 * - température centrale ;
 * - pluie : probabilité -> intensité du bleu, quantité -> taille du point.
 *
 * Le contraste est déterminé heure par heure à partir de la couleur thermique
 * locale afin de rester lisible en thème clair, sombre ou dynamique.
 */
@Composable
internal fun MiniForecastStrip(
    hourlyTemps: List<Double?>,
    hourlyPrecipProb: List<Int?>,
    modifier: Modifier = Modifier,
    hourlyPrecipMm: List<Double?> = emptyList(),
    hourlyConditions: List<WeatherCondition?> = emptyList(),
    startTime: LocalDateTime? = null
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val noDataColor = MaterialTheme.colorScheme.surfaceVariant
    val themeContentColor = MaterialTheme.colorScheme.onSurface
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val a11yLabel = buildA11yLabel(hourlyTemps, hourlyPrecipProb)
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val platformLocale = LocalLocale.current.platformLocale
    val hourFormatter = remember(is24, platformLocale) {
        DateTimeFormatter.ofPattern(if (is24) "H'h'" else "h a", platformLocale)
    }

    // Heatmap un peu plus vive que la 1.11.7, tout en conservant assez de
    // marge de contraste pour les libellés et les icônes météo.
    val heatStrength = if (isDarkTheme) MINI_TIMELINE_HEAT_STRENGTH_DARK else MINI_TIMELINE_HEAT_STRENGTH_LIGHT
    val cellBackgrounds = List(CELL_COUNT) { index ->
        hourlyTemps.getOrNull(index)
            ?.let(::temperatureHeatmapColor)
            ?.let { blendedHeatmapColor(surface, it, heatStrength) }
            ?: noDataColor
    }
    val cellContentColors = cellBackgrounds.map { background ->
        miniTimelineContentColor(background, themeContentColor)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(MINI_TIMELINE_HEIGHT_DP.dp)
            .then(
                if (startTime != null) Modifier.testTag(TAG_MINI_FORECAST_ANCHORS)
                else Modifier
            )
    ) {
        val cellWidth = maxWidth / MINI_TIMELINE_VISIBLE_HOURS.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(MINI_TIMELINE_CORNER_RADIUS_DP.dp))
                .testTag(TAG_MINI_FORECAST_STRIP)
                .semantics { contentDescription = a11yLabel }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
                    .testTag(TAG_MINI_FORECAST_SCROLL)
            ) {
                repeat(CELL_COUNT) { index ->
                    val contentColor = cellContentColors[index]
                    val hour = startTime?.plusHours(index.toLong())?.format(hourFormatter)
                    val isCurrentBucket = startTime != null && index == 0

                    MiniForecastHour(
                        modifier = Modifier
                            .width(cellWidth)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    miniTimelineCellGradientColors(cellBackgrounds, index)
                                )
                            ),
                        hour = hour,
                        isCurrentBucket = isCurrentBucket,
                        temperature = hourlyTemps.getOrNull(index),
                        condition = hourlyConditions.getOrNull(index),
                        rainStyle = miniTimelineRainDotStyle(
                            probabilityPercent = hourlyPrecipProb.getOrNull(index),
                            amountMm = hourlyPrecipMm.getOrNull(index),
                            isDarkTheme = isDarkTheme
                        ),
                        contentColor = contentColor,
                        index = index
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniForecastHour(
    modifier: Modifier,
    hour: String?,
    isCurrentBucket: Boolean,
    temperature: Double?,
    condition: WeatherCondition?,
    rainStyle: MiniTimelineRainDotStyle?,
    contentColor: Color,
    index: Int
) {
    val currentHourA11y = stringResource(R.string.mini_forecast_current_hour_a11y)

    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // L'heure courante n'occupe plus une ligne dédiée : une courte barre
        // intégrée au sommet de la première colonne joue le rôle d'ancre.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isCurrentBucket) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(14.dp)
                        .height(2.dp)
                        .background(
                            contentColor.copy(alpha = 0.64f),
                            RoundedCornerShape(1.dp)
                        )
                        .testTag(TAG_MINI_FORECAST_CURRENT_MARKER)
                        .semantics { contentDescription = currentHourA11y }
                )
            }
            Text(
                text = hour ?: " ",
                color = contentColor.copy(alpha = if (isCurrentBucket) 0.80f else 0.64f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    fontWeight = if (isCurrentBucket) FontWeight.SemiBold else FontWeight.Medium
                ),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .height(22.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            condition?.let {
                WeatherIconDecorative(
                    condition = it,
                    size = MINI_TIMELINE_CONDITION_ICON_DP.dp,
                    tint = contentColor.copy(alpha = 0.90f),
                    modifier = Modifier.testTag("$TAG_MINI_FORECAST_CONDITION_PREFIX$index")
                )
            }
        }

        Text(
            text = temperature?.let { "${it.roundToInt()}°" } ?: "—",
            color = contentColor,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2f
                drawLine(
                    color = contentColor.copy(alpha = 0.12f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                rainStyle?.let { style ->
                    drawCircle(
                        color = style.color,
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, y),
                        radius = style.radiusDp.dp.toPx()
                    )
                }
            }
        }
    }
}

/** Style visuel d'un point pluie de la mini-timeline. */
internal data class MiniTimelineRainDotStyle(
    val color: Color,
    val radiusDp: Float
)

/**
 * Probabilité -> force du bleu ; quantité -> rayon.
 *
 * Le rayon suit une racine carrée pour que l'aire du disque (et non son rayon)
 * progresse approximativement avec les mm/h. Au-delà de 5 mm/h le point est
 * volontairement plafonné : la mini-timeline reste une synthèse, pas un graphe.
 */
internal fun miniTimelineRainDotStyle(
    probabilityPercent: Int?,
    amountMm: Double?,
    isDarkTheme: Boolean
): MiniTimelineRainDotStyle? {
    val probability = probabilityPercent?.coerceIn(0, 100)
    val validAmount = amountMm?.takeIf { it.isFinite() && it >= 0.0 }
    val showsRain = (probability ?: 0) >= RAIN_DOT_MIN_PROBABILITY ||
        (validAmount ?: 0.0) >= RAIN_DOT_MIN_AMOUNT_MM
    if (!showsRain) return null

    val probabilityFraction = when (probability) {
        null -> 0.5f
        else -> ((probability - RAIN_DOT_MIN_PROBABILITY).coerceAtLeast(0) /
            (100f - RAIN_DOT_MIN_PROBABILITY)).coerceIn(0f, 1f)
    }

    val lowBlue = if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF64B5F6)
    val highBlue = if (isDarkTheme) Color(0xFF2196F3) else Color(0xFF0D47A1)
    val blue = lerp(lowBlue, highBlue, probabilityFraction)
        .copy(alpha = 0.82f + 0.18f * probabilityFraction)

    val amountFraction = validAmount
        ?.let { sqrt((it.coerceAtMost(RAIN_DOT_MAX_SCALE_MM) / RAIN_DOT_MAX_SCALE_MM).toFloat()) }
        ?: 0f
    val radius = RAIN_DOT_MIN_RADIUS_DP +
        (RAIN_DOT_MAX_RADIUS_DP - RAIN_DOT_MIN_RADIUS_DP) * amountFraction

    return MiniTimelineRainDotStyle(color = blue, radiusDp = radius)
}

/**
 * Petit gradient local utilisé par chaque colonne de la heatmap. Les couleurs
 * des bords sont les moyennes avec les colonnes voisines : le bord droit d'une
 * heure est donc exactement le bord gauche de la suivante. On obtient un ruban
 * visuellement continu tout en gardant une couleur centrale fidèle à l'heure.
 */
internal fun miniTimelineCellGradientColors(
    cellBackgrounds: List<Color>,
    index: Int
): List<Color> {
    if (cellBackgrounds.isEmpty()) return listOf(Color.Transparent, Color.Transparent)
    val safeIndex = index.coerceIn(0, cellBackgrounds.lastIndex)
    val current = cellBackgrounds[safeIndex]
    val previous = cellBackgrounds.getOrNull(safeIndex - 1) ?: current
    val next = cellBackgrounds.getOrNull(safeIndex + 1) ?: current
    return listOf(
        lerp(previous, current, 0.5f),
        current,
        lerp(current, next, 0.5f)
    )
}

/**
 * Couleur de contenu sensible au thème ET au fond réel de la cellule.
 *
 * Le rôle Material `onSurface` est privilégié s'il atteint le ratio AA 4.5:1.
 * Sur un fond thermique qui rend ce rôle illisible, noir/blanc prend le relais
 * avec le meilleur contraste. Cette stratégie est robuste avec les thèmes
 * dynamique, clair et sombre.
 */
internal fun miniTimelineContentColor(
    background: Color,
    themeContentColor: Color
): Color {
    if (contrastRatio(themeContentColor, background) >= MIN_TEXT_CONTRAST_RATIO) {
        return themeContentColor
    }
    return if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }
}

internal fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = max(foreground.luminance(), background.luminance())
    val darker = kotlin.math.min(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

@Composable
private fun buildA11yLabel(
    temps: List<Double?>,
    precipProbs: List<Int?>
): String {
    val nonNull = temps.filterNotNull()
    val minT = nonNull.minOrNull()?.roundToInt()
    val maxT = nonNull.maxOrNull()?.roundToInt()
    val rainHours = precipProbs.count { it != null && it >= RAIN_DOT_MIN_PROBABILITY }

    return when {
        minT == null || maxT == null -> stringResource(R.string.mini_forecast_a11y_no_data)
        rainHours == 0 -> stringResource(R.string.mini_forecast_a11y_no_rain, minT, maxT)
        else -> stringResource(R.string.mini_forecast_a11y_with_rain, minT, maxT, rainHours)
    }
}

private const val CELL_COUNT = 12
internal const val MINI_TIMELINE_VISIBLE_HOURS = 6
internal const val MINI_TIMELINE_HEIGHT_DP = 70
internal const val MINI_TIMELINE_CORNER_RADIUS_DP = 10
internal const val MINI_TIMELINE_CONDITION_ICON_DP = 16
internal const val MINI_TIMELINE_HEAT_STRENGTH_LIGHT = 0.78f
internal const val MINI_TIMELINE_HEAT_STRENGTH_DARK = 0.82f
private const val RAIN_DOT_MIN_PROBABILITY = 30
private const val RAIN_DOT_MIN_AMOUNT_MM = 0.05
private const val RAIN_DOT_MAX_SCALE_MM = 5.0
private const val RAIN_DOT_MIN_RADIUS_DP = 1.25f
private const val RAIN_DOT_MAX_RADIUS_DP = 3.65f
private const val MIN_TEXT_CONTRAST_RATIO = 4.5f

internal const val TAG_MINI_FORECAST_STRIP = "mini_forecast_strip"
internal const val TAG_MINI_FORECAST_SCROLL = "mini_forecast_scroll"
internal const val TAG_MINI_FORECAST_ANCHORS = "mini_forecast_anchors"
internal const val TAG_MINI_FORECAST_CURRENT_MARKER = "mini_forecast_current_marker"
internal const val TAG_MINI_FORECAST_CONDITION_PREFIX = "mini_forecast_condition_"
