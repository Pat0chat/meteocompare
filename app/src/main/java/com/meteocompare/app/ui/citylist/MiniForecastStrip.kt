package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Mini-timeline 12 h de la Home, alignée sur les agrégats du moteur de consensus.
 *
 * Chaque cellule horaire affiche désormais quatre informations sans refaire de
 * calcul météo dans l'UI :
 * - heure (sur quelques ancres seulement pour ne pas surcharger la bande) ;
 * - icône de la condition météo de consensus ;
 * - température centrale ;
 * - point de pluie : la probabilité pilote l'intensité du bleu, tandis que la
 *   quantité centrale en mm/h pilote la taille du point.
 *
 * La couleur du texte et des icônes monochromes est choisie cellule par cellule.
 * On privilégie `colorScheme.onSurface` (donc la couleur de contenu du thème)
 * lorsqu'elle atteint AA sur le fond thermique ; sinon on bascule vers noir ou
 * blanc, selon le meilleur contraste WCAG. Une cellule très claire peut donc
 * utiliser du texte sombre même en thème sombre, et inversement.
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
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val noDataColor = MaterialTheme.colorScheme.surfaceVariant
    val separatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    val themeContentColor = MaterialTheme.colorScheme.onSurface
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val a11yLabel = buildA11yLabel(hourlyTemps, hourlyPrecipProb)

    val context = LocalContext.current
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val platformLocale = LocalLocale.current.platformLocale
    val hourFormatter = remember(is24, platformLocale) {
        DateTimeFormatter.ofPattern(if (is24) "H'h'" else "h a", platformLocale)
    }

    val heatStrength = if (surface.luminance() < 0.5f) 0.88f else 0.82f
    val cellBackgrounds = List(CELL_COUNT) { index ->
        hourlyTemps.getOrNull(index)
            ?.let(::temperatureHeatmapColor)
            ?.let { blendedHeatmapColor(surface, it, heatStrength) }
            ?: noDataColor
    }
    val cellContentColors = cellBackgrounds.map { background ->
        miniTimelineContentColor(background, themeContentColor)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(
                if (startTime != null) Modifier.testTag(TAG_MINI_FORECAST_ANCHORS)
                else Modifier
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .testTag(TAG_MINI_FORECAST_STRIP)
                .semantics { contentDescription = a11yLabel }
        ) {
            val cellWidth = size.width / CELL_COUNT
            val hourTextSizePx = with(density) { 6.5.sp.toPx() }
            val temperatureTextSizePx = with(density) { 8.5.sp.toPx() }
            val precipY = size.height - with(density) { 5.dp.toPx() }

            val hourPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = hourTextSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.NORMAL
                )
            }
            val temperaturePaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = temperatureTextSizePx
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            }

            repeat(CELL_COUNT) { index ->
                val temp = hourlyTemps.getOrNull(index)
                val background = cellBackgrounds[index]
                val contentColor = cellContentColors[index]
                val x = index * cellWidth

                drawRect(
                    color = background,
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(cellWidth, size.height)
                )

                if (index > 0) {
                    drawLine(
                        color = separatorColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = with(density) { 0.5.dp.toPx() }
                    )
                }

                // Les ancres restent discrètes : début, +4 h, +7 h, +11 h.
                if (startTime != null && index in TIME_ANCHOR_INDICES) {
                    hourPaint.color = contentColor.copy(alpha = 0.82f).toArgb()
                    val hourMetrics = hourPaint.fontMetrics
                    val hourCenterY = with(density) { 7.dp.toPx() }
                    val hourBaseline = hourCenterY -
                        (hourMetrics.ascent + hourMetrics.descent) / 2f

                    drawContext.canvas.nativeCanvas.drawText(
                        startTime.plusHours(index.toLong()).format(hourFormatter),
                        x + cellWidth / 2f,
                        hourBaseline,
                        hourPaint
                    )
                }

                if (temp != null) {
                    temperaturePaint.color = contentColor.toArgb()
                    val metrics = temperaturePaint.fontMetrics
                    val centerY = with(density) { 34.dp.toPx() }
                    val baseline = centerY - (metrics.ascent + metrics.descent) / 2f

                    drawContext.canvas.nativeCanvas.drawText(
                        "${temp.roundToInt()}°",
                        x + cellWidth / 2f,
                        baseline,
                        temperaturePaint
                    )
                }

                miniTimelineRainDotStyle(
                    probabilityPercent = hourlyPrecipProb.getOrNull(index),
                    amountMm = hourlyPrecipMm.getOrNull(index),
                    isDarkTheme = isDarkTheme
                )?.let { style ->
                    drawCircle(
                        color = style.color,
                        center = Offset(x + cellWidth / 2f, precipY),
                        radius = with(density) { style.radiusDp.dp.toPx() }
                    )
                }
            }
        }

        // Les icônes sont des composables (et non des glyphes dessinés à la main)
        // pour réutiliser la même correspondance WeatherCondition -> icône que le
        // reste de l'application. Elles sont décoratives : la sémantique consolidée
        // demeure portée par la mini-timeline.
        Row(Modifier.fillMaxSize()) {
            repeat(CELL_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    hourlyConditions.getOrNull(index)?.let { condition ->
                        WeatherIconDecorative(
                            condition = condition,
                            size = 14.dp,
                            tint = cellContentColors[index],
                            modifier = Modifier
                                .offset(y = 12.dp)
                                .testTag("$TAG_MINI_FORECAST_CONDITION_PREFIX$index")
                        )
                    }
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
private const val RAIN_DOT_MIN_PROBABILITY = 30
private const val RAIN_DOT_MIN_AMOUNT_MM = 0.05
private const val RAIN_DOT_MAX_SCALE_MM = 5.0
private const val RAIN_DOT_MIN_RADIUS_DP = 1.25f
private const val RAIN_DOT_MAX_RADIUS_DP = 3.65f
private const val MIN_TEXT_CONTRAST_RATIO = 4.5f
private val TIME_ANCHOR_INDICES = setOf(0, 4, 7, 11)

internal const val TAG_MINI_FORECAST_STRIP = "mini_forecast_strip"
internal const val TAG_MINI_FORECAST_ANCHORS = "mini_forecast_anchors"
internal const val TAG_MINI_FORECAST_CONDITION_PREFIX = "mini_forecast_condition_"
