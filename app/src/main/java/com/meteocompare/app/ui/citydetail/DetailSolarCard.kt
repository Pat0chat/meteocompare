package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.core.locale.LocalClockFormatPreference
import com.meteocompare.app.core.locale.WeatherTimeFormat

import com.meteocompare.app.ui.components.UvIndexGauge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meteocompare.app.R
import com.meteocompare.app.core.units.weatherStringResource as stringResource
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.util.forecastSolarWindow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

/** Instants absolus : la durée reste juste lors des changements d'heure (DST). */
internal data class DetailSolarWindow(val sunrise: Instant?, val sunset: Instant?)

/** Même priorité que la carte Home : heures Open-Meteo, sinon calcul NOAA local. */
internal fun detailSolarWindow(forecast: CityForecast, date: LocalDate): DetailSolarWindow {
    val window = forecastSolarWindow(forecast, date)
    return DetailSolarWindow(window.sunrise, window.sunset)
}

/** null en dehors de la journée solaire ; évite un soleil dessiné pendant la nuit. */
internal fun solarProgress(window: DetailSolarWindow, now: Instant): Float? {
    val sunrise = window.sunrise ?: return null
    val sunset = window.sunset ?: return null
    if (now.isBefore(sunrise) || !now.isBefore(sunset)) return null
    val duration = Duration.between(sunrise, sunset).toMillis().takeIf { it > 0 } ?: return null
    return (Duration.between(sunrise, now).toMillis().toFloat() / duration).coerceIn(0f, 1f)
}

/** Carte solaire condensée, avec couleurs dynamiques du thème MeteoCompare. */
@Composable
internal fun DetailSolarCard(
    window: DetailSolarWindow,
    timezone: ZoneId,
    date: LocalDate,
    now: Instant,
    modifier: Modifier = Modifier,
    uvIndex: Double? = null
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = WeatherTimeFormat.clock(locale, LocalClockFormatPreference.current)
    val sunriseTime = window.sunrise?.atZone(timezone)?.toLocalTime()
    val sunsetTime = window.sunset?.atZone(timezone)?.toLocalTime()
    val minutesOfDaylight = if (window.sunrise != null && window.sunset != null &&
        window.sunset.isAfter(window.sunrise)) {
        Duration.between(window.sunrise, window.sunset).toMinutes()
    } else null
    val progress = if (now.atZone(timezone).toLocalDate() == date) {
        solarProgress(window, now)
    } else null
    val sunriseColor = MaterialTheme.colorScheme.primary
    val sunsetColor = MaterialTheme.colorScheme.tertiary
    val midpointColor = MaterialTheme.colorScheme.secondary
    val horizonColor = MaterialTheme.colorScheme.outlineVariant

    // Même structure que Température / Précipitations / Vent : aucune carte
    // imbriquée, la section est posée directement sur TodaySummaryCard.
    SummaryMetricSection(tag = TAG_DETAIL_SOLAR_CARD, modifier = modifier) {
        SummaryMetricGroupHeader(
            title = stringResource(R.string.detail_solar_title),
            icon = Icons.Filled.WbSunny,
            accent = sunriseColor,
            trailing = minutesOfDaylight?.let {
                stringResource(R.string.detail_solar_daylight, it / 60, it % 60)
            }
        )

        Spacer(Modifier.height(3.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp).testTag(TAG_DETAIL_SOLAR_CURVE)) {
            val startX = 12.dp.toPx()
            val endX = max(startX, size.width - 12.dp.toPx())
            val baseline = size.height - 5.dp.toPx()
            val controlY = 0f
            drawLine(
                color = horizonColor,
                start = Offset(startX, baseline),
                end = Offset(endX, baseline),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3.dp.toPx(), 5.dp.toPx())
                )
            )
            if (window.sunrise != null && window.sunset != null &&
                window.sunset.isAfter(window.sunrise)) {
                val distance = endX - startX
                val path = Path().apply {
                    moveTo(startX, baseline)
                    cubicTo(
                        startX + distance * .25f, controlY,
                        endX - distance * .25f, controlY,
                        endX, baseline
                    )
                }
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        listOf(sunriseColor, midpointColor, sunsetColor)
                    ),
                    style = Stroke(width = 2.7.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(sunriseColor, radius = 3.dp.toPx(), center = Offset(startX, baseline))
                drawCircle(sunsetColor, radius = 3.dp.toPx(), center = Offset(endX, baseline))
                if (progress != null) {
                    val t = progress
                    val complement = 1f - t
                    val x = complement * complement * complement * startX +
                        3f * complement * complement * t * (startX + distance * .25f) +
                        3f * complement * t * t * (endX - distance * .25f) +
                        t * t * t * endX
                    val y = (complement * complement * complement + t * t * t) * baseline +
                        3f * complement * t * controlY
                    val dot = lerp(sunriseColor, sunsetColor, t)
                    drawCircle(dot.copy(alpha = .22f), 9.dp.toPx(), Offset(x, y))
                    drawCircle(dot, 5.5.dp.toPx(), Offset(x, y))
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        // Les deux extrémités suivent exactement les points de la courbe
        // (x = 12 dp et x = largeur - 12 dp), et non deux colonnes 50/50.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            SolarTimeValue(
                icon = Icons.Filled.WbSunny,
                label = stringResource(R.string.detail_solar_sunrise),
                value = sunriseTime?.format(formatter) ?: "—",
                tint = sunriseColor,
                iconAtEnd = false
            )
            SolarTimeValue(
                icon = Icons.Filled.Bedtime,
                label = stringResource(R.string.detail_solar_sunset),
                value = sunsetTime?.format(formatter) ?: "—",
                tint = sunsetColor,
                iconAtEnd = true
            )
        }

        // L'indice UV fait partie de la même section que la trajectoire solaire,
        // sous les heures de lever/coucher et sans nouvelle carte ni séparateur.
        if (uvIndex != null && uvIndex.isFinite() && uvIndex >= 0.0) {
            Spacer(Modifier.height(10.dp))
            UvIndexGauge(value = uvIndex, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SolarTimeValue(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
    iconAtEnd: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Marge de 5 dp + demi-icône de 7 dp = point situé à 12 dp du bord.
        if (!iconAtEnd) {
            Spacer(Modifier.width(5.dp))
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp).testTag(TAG_DETAIL_SOLAR_SUNRISE_ICON))
            Spacer(Modifier.width(5.dp))
        }
        Column(horizontalAlignment = if (iconAtEnd) Alignment.End else Alignment.Start) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        if (iconAtEnd) {
            Spacer(Modifier.width(5.dp))
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp).testTag(TAG_DETAIL_SOLAR_SUNSET_ICON))
            Spacer(Modifier.width(5.dp))
        }
    }
}

internal const val TAG_DETAIL_SOLAR_CARD = "detail_solar_card"
internal const val TAG_DETAIL_SOLAR_CURVE = "detail_solar_curve"
internal const val TAG_DETAIL_SOLAR_SUNRISE_ICON = "detail_solar_sunrise_icon"
internal const val TAG_DETAIL_SOLAR_SUNSET_ICON = "detail_solar_sunset_icon"
