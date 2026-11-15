package com.meteocompare.app.ui.citylist

import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat

import com.meteocompare.app.core.units.WeatherUnits
import com.meteocompare.app.core.units.LocalWeatherUnits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteocompare.app.ui.components.formatSunshineDuration
import com.meteocompare.app.ui.components.localizedSunshineDurationFormats
import com.meteocompare.app.R
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.util.ConditionDecision
import com.meteocompare.app.ui.components.ConditionStatusIcon
import com.meteocompare.app.ui.components.blendedHeatmapColor
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Prévision compacte 24 h de la Home.
 *
 * Le composant est désormais une surface neutre, cohérente avec les tuiles
 * `MetricToday`. La heatmap reste volontairement présente uniquement derrière
 * la température : la couleur a ainsi un sens unique et immédiat.
 *
 * Six heures sont visibles simultanément, les 18 suivantes restant
 * accessibles par défilement horizontal. Chaque colonne réutilise strictement les
 * agrégats du moteur de consensus : condition, température, probabilité et
 * quantité de pluie et ensoleillement estimé (en minutes si disponible).
 */
@Composable
internal fun MiniForecastStrip(
    hourlyTemps: List<Double?>,
    hourlyPrecipProb: List<Int?>,
    modifier: Modifier = Modifier,
    hourlyPrecipMm: List<Double?> = emptyList(),
    hourlyConditions: List<WeatherCondition?> = emptyList(),
    conditionDiagnostics: List<ConditionDecision> = emptyList(),
    startTime: LocalDateTime? = null,
    startInstant: Instant? = null,
    timezone: ZoneId? = null,
    nightFlags: List<Boolean?> = emptyList(),
    hourlySunshineHours: List<Double?> = emptyList()
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f)
    val themeContentColor = MaterialTheme.colorScheme.onSurface
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val a11yLabel = buildA11yLabel(hourlyTemps, hourlyPrecipProb, hourlySunshineHours)
    val scrollState = rememberScrollState()
    // Keep the original height when forecasts contain no valid sunshine data.
    val hasSunshine = hourlySunshineHours.take(MINI_TIMELINE_HOUR_COUNT)
        .any { validMiniTimelineSunshineHours(it) != null }
    val sunshineFormats = localizedSunshineDurationFormats()
    val sunshineLabel = stringResource(R.string.compare_sunshine)
    val stripHeight = MINI_TIMELINE_HEIGHT_DP +
        (if (hasSunshine) MINI_TIMELINE_SUNSHINE_EXTRA_HEIGHT_DP else 0)
    val units = LocalWeatherUnits.current

    val platformLocale = LocalLocale.current.platformLocale
    val clockPreference = LocalClockFormatPreference.current
    val hourFormatter = remember(platformLocale, clockPreference) { WeatherTimeFormat.hour(platformLocale, clockPreference) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight.dp)
            .then(
                if (startTime != null || (startInstant != null && timezone != null)) Modifier.testTag(TAG_MINI_FORECAST_ANCHORS)
                else Modifier
            )
    ) {
        val cellWidth = maxWidth / MINI_TIMELINE_VISIBLE_HOURS.toFloat()

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_MINI_FORECAST_STRIP)
                .semantics { contentDescription = a11yLabel },
            shape = RoundedCornerShape(MINI_TIMELINE_CORNER_RADIUS_DP.dp),
            color = containerColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(scrollState)
                    .testTag(TAG_MINI_FORECAST_SCROLL)
            ) {
                repeat(MINI_TIMELINE_HOUR_COUNT) { index ->
                    val hour = if (startInstant != null && timezone != null) {
                        startInstant.plusSeconds(index * 3_600L).atZone(timezone).format(hourFormatter)
                    } else {
                        startTime?.plusHours(index.toLong())?.format(hourFormatter)
                    }
                    val isCurrentBucket = (startTime != null || startInstant != null) && index == 0
                    val temperature = hourlyTemps.getOrNull(index)
                    val heatBackground = miniTimelineTemperatureBackground(
                        temperature = temperature,
                        surface = MaterialTheme.colorScheme.surfaceContainerHigh,
                        isDarkTheme = isDarkTheme
                    )
                    val temperatureContent = miniTimelineContentColor(
                        background = heatBackground,
                        themeContentColor = themeContentColor
                    )

                    MiniForecastHour(
                        modifier = Modifier
                            .width(cellWidth)
                            .fillMaxHeight(),
                        hour = hour,
                        isCurrentBucket = isCurrentBucket,
                        temperature = temperature,
                        temperatureBackground = heatBackground,
                        temperatureContentColor = temperatureContent,
                        condition = hourlyConditions.getOrNull(index),
                        conditionDiagnostic = conditionDiagnostics.getOrNull(index),
                        isNight = nightFlags.getOrNull(index) == true,
                        rainStyle = miniTimelineRainDotStyle(
                            probabilityPercent = hourlyPrecipProb.getOrNull(index),
                            amountMm = hourlyPrecipMm.getOrNull(index),
                            isDarkTheme = isDarkTheme
                        ),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        sunshineText = validMiniTimelineSunshineHours(hourlySunshineHours.getOrNull(index))
                            ?.let { formatSunshineDuration(it, hourly = true, units, platformLocale, sunshineFormats) },
                        sunshineLabel = sunshineLabel,
                        showSunshine = hasSunshine,
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
    temperatureBackground: Color,
    temperatureContentColor: Color,
    condition: WeatherCondition?,
    conditionDiagnostic: ConditionDecision? = null,
    isNight: Boolean = false,
    rainStyle: MiniTimelineRainDotStyle?,
    contentColor: Color,
    sunshineText: String?,
    sunshineLabel: String,
    showSunshine: Boolean,
    index: Int,
    units: WeatherUnits = LocalWeatherUnits.current
) {
    val currentHourA11y = stringResource(R.string.mini_forecast_current_hour_a11y)

    Column(
        modifier = modifier.padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(13.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isCurrentBucket) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(10.dp)
                        .height(1.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(1.dp)
                        )
                        .testTag(TAG_MINI_FORECAST_CURRENT_MARKER)
                        .semantics { contentDescription = currentHourA11y }
                )
            }
            Text(
                text = hour ?: " ",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isCurrentBucket) 0.70f else 0.60f
                ),
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
                .height(30.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ConditionStatusIcon(
                condition = condition,
                decision = conditionDiagnostic,
                size = MINI_TIMELINE_CONDITION_ICON_DP.dp,
                isNight = isNight,
                modifier = Modifier.testTag("$TAG_MINI_FORECAST_CONDITION_PREFIX$index")
            )
        }

        Box(
            modifier = Modifier
                .height(18.dp)
                .width(MINI_TIMELINE_TEMPERATURE_CAPSULE_WIDTH_DP.dp)
                .background(
                    temperatureBackground,
                    RoundedCornerShape(MINI_TIMELINE_TEMPERATURE_CAPSULE_RADIUS_DP.dp)
                )
                .testTag("$TAG_MINI_FORECAST_TEMPERATURE_PREFIX$index"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = temperature?.let { units.temp(it) } ?: "—",
                color = temperatureContentColor,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2f
                drawLine(
                    color = contentColor.copy(alpha = 0.08f),
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

        if (showSunshine) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MINI_TIMELINE_SUNSHINE_ROW_HEIGHT_DP.dp),
                contentAlignment = Alignment.Center
            ) {
                sunshineText?.let { minutes ->
                    Row(
                        modifier = Modifier
                            .testTag("$TAG_MINI_FORECAST_SUNSHINE_PREFIX$index")
                            .semantics {
                                contentDescription = listOfNotNull(hour, "$sunshineLabel: $minutes").joinToString(", ")
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WbSunny,
                            contentDescription = null,
                            modifier = Modifier.width(10.dp).height(10.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = minutes,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Keep bad/missing values blank instead of misrepresenting them as zero minutes. */
internal fun validMiniTimelineSunshineHours(value: Double?): Double? =
    value?.takeIf { it.isFinite() && it in 0.0..1.0 }

/** Couleur thermique confinée à la capsule de température. */
internal fun miniTimelineTemperatureBackground(
    temperature: Double?,
    surface: Color,
    isDarkTheme: Boolean
): Color {
    if (temperature == null) return surface
    val strength = if (isDarkTheme) {
        MINI_TIMELINE_HEAT_STRENGTH_DARK
    } else {
        MINI_TIMELINE_HEAT_STRENGTH_LIGHT
    }
    return blendedHeatmapColor(surface, temperatureHeatmapColor(temperature), strength)
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
 * Couleur de contenu sensible au thème ET au fond réel de la capsule thermique.
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
    precipProbs: List<Int?>,
    sunshineHours: List<Double?>
): String {
    val nonNull = temps.filterNotNull().filter(Double::isFinite)
    val minT = nonNull.minOrNull()
    val maxT = nonNull.maxOrNull()
    val rainHours = precipProbs.count { it != null && it >= RAIN_DOT_MIN_PROBABILITY }

    return when {
        (minT == null || maxT == null) && sunshineHours.take(MINI_TIMELINE_HOUR_COUNT)
            .any { validMiniTimelineSunshineHours(it) != null } -> stringResource(R.string.compare_sunshine)
        minT == null || maxT == null -> stringResource(R.string.mini_forecast_a11y_no_data)
        rainHours == 0 -> stringResource(R.string.mini_forecast_a11y_no_rain, minT, maxT)
        else -> stringResource(R.string.mini_forecast_a11y_with_rain, minT, maxT, rainHours)
    }
}

internal const val MINI_TIMELINE_HOUR_COUNT = 24
internal const val MINI_TIMELINE_VISIBLE_HOURS = 6
internal const val MINI_TIMELINE_HEIGHT_DP = 84
internal const val MINI_TIMELINE_SUNSHINE_ROW_HEIGHT_DP = 15
internal const val MINI_TIMELINE_SUNSHINE_EXTRA_HEIGHT_DP = 19
internal const val MINI_TIMELINE_CORNER_RADIUS_DP = 10
internal const val MINI_TIMELINE_CONDITION_ICON_DP = 27
internal const val MINI_TIMELINE_TEMPERATURE_CAPSULE_WIDTH_DP = 40
internal const val MINI_TIMELINE_TEMPERATURE_CAPSULE_RADIUS_DP = 7
internal const val MINI_TIMELINE_HEAT_STRENGTH_LIGHT = 0.88f
internal const val MINI_TIMELINE_HEAT_STRENGTH_DARK = 0.90f
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
internal const val TAG_MINI_FORECAST_TEMPERATURE_PREFIX = "mini_forecast_temperature_"
internal const val TAG_MINI_FORECAST_SUNSHINE_PREFIX = "mini_forecast_sunshine_"
