package com.meteocompare.app.ui.citylist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.meteocompare.app.ui.components.blendedHeatmapColor
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Mini heatmap 12 h de la Home, volontairement alignée sur le langage visuel
 * de la timeline détaillée : une bande thermique continue, de hauteur fixe,
 * avec chaque heure représentée par une cellule pleine plutôt que par une
 * barre de hauteur variable.
 *
 * Les informations restent strictement les mêmes :
 * - température arrondie pour chacune des 12 heures ;
 * - marqueur de pluie dès 30 % (taille croissante avec la probabilité) ;
 * - trois ancres horaires (début, +6 h, +11 h) ;
 * - description TalkBack consolidée.
 *
 * Les heures sont maintenant intégrées dans la bande. La heatmap fait 38 dp,
 * mais la ligne d’ancres séparée a disparu : la hauteur totale est donc plus faible.
 */
@Composable
internal fun MiniForecastStrip(
    hourlyTemps: List<Double?>,
    hourlyPrecipProb: List<Int?>,
    modifier: Modifier = Modifier,
    startTime: LocalDateTime? = null
) {
    val density = LocalDensity.current
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val noDataColor = MaterialTheme.colorScheme.surfaceVariant
    val separatorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    val precipColor = MaterialTheme.colorScheme.primary
    val a11yLabel = buildA11yLabel(hourlyTemps, hourlyPrecipProb)

    val context = LocalContext.current
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val platformLocale = LocalLocale.current.platformLocale
    val hourFormatter = remember(is24, platformLocale) {
        DateTimeFormatter.ofPattern(if (is24) "H'h'" else "h a", platformLocale)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
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
            val cellCount = 12
            val cellWidth = size.width / cellCount

            // La timeline Details utilisait 0.54f : agréable dans une grande carte,
            // mais trop pâle sur cette bande très compacte. Ici on conserve le même
            // nuancier thermique en laissant nettement plus de couleur visible.
            val heatStrength = if (surface.luminance() < 0.5f) 0.88f else 0.82f

            val hourTextSizePx = with(density) { 6.5.sp.toPx() }
            val temperatureTextSizePx = with(density) { 8.5.sp.toPx() }
            val precipY = size.height - with(density) { 6.dp.toPx() }

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

            repeat(cellCount) { index ->
                val temp = hourlyTemps.getOrNull(index)
                val rawColor = temp?.let(::temperatureHeatmapColor)
                val background = rawColor?.let {
                    blendedHeatmapColor(surface, it, heatStrength)
                } ?: noDataColor

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

                val contentColor = if (background.luminance() > 0.54f) {
                    Color.Black.copy(alpha = 0.88f)
                } else {
                    Color.White.copy(alpha = 0.96f)
                }

                // Les trois ancres horaires auparavant placées sous la heatmap sont
                // maintenant intégrées dans la bande : début, +6 h et dernière heure.
                if (startTime != null && index in TIME_ANCHOR_INDICES) {
                    hourPaint.color = contentColor.copy(alpha = 0.76f).toArgb()
                    val hourMetrics = hourPaint.fontMetrics
                    val hourCenterY = with(density) { 8.dp.toPx() }
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
                    val centerY = with(density) { 20.dp.toPx() }
                    val baseline = centerY - (metrics.ascent + metrics.descent) / 2f

                    drawContext.canvas.nativeCanvas.drawText(
                        "${temp.roundToInt()}°",
                        x + cellWidth / 2f,
                        baseline,
                        temperaturePaint
                    )
                }

                val probability = hourlyPrecipProb.getOrNull(index)
                if (probability != null && probability >= 30) {
                    val fraction = ((probability.coerceIn(30, 100) - 30) / 70f)
                        .coerceIn(0f, 1f)
                    val radius = with(density) {
                        (1.5f + 1.0f * fraction).dp.toPx()
                    }
                    drawCircle(
                        color = precipColor.copy(alpha = 0.72f + 0.28f * fraction),
                        center = Offset(x + cellWidth / 2f, precipY),
                        radius = radius
                    )
                }
            }
        }
    }
}

@Composable
private fun buildA11yLabel(
    temps: List<Double?>,
    precipProbs: List<Int?>
): String {
    val nonNull = temps.filterNotNull()
    val minT = nonNull.minOrNull()?.roundToInt()
    val maxT = nonNull.maxOrNull()?.roundToInt()
    val rainHours = precipProbs.count { it != null && it >= 30 }

    return when {
        minT == null || maxT == null -> stringResource(R.string.mini_forecast_a11y_no_data)
        rainHours == 0 -> stringResource(R.string.mini_forecast_a11y_no_rain, minT, maxT)
        else -> stringResource(R.string.mini_forecast_a11y_with_rain, minT, maxT, rainHours)
    }
}

private val TIME_ANCHOR_INDICES = setOf(0, 4, 7, 11)

internal const val TAG_MINI_FORECAST_STRIP = "mini_forecast_strip"
internal const val TAG_MINI_FORECAST_ANCHORS = "mini_forecast_anchors"
