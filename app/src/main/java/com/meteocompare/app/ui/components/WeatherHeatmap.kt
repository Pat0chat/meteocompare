package com.meteocompare.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Palette thermique partagée entre la heatmap compacte de la Home et les
 * visualisations de détail. Garder cette fonction commune évite qu'une même
 * température change de couleur selon l'écran.
 */
internal fun temperatureHeatmapColor(temp: Double): Color {
    val stops = listOf(
        -10.0 to Color(0xFF1976D2),
        5.0 to Color(0xFF4FC3F7),
        15.0 to Color(0xFF81C784),
        22.0 to Color(0xFFFFB74D),
        30.0 to Color(0xFFEF5350),
        40.0 to Color(0xFFB71C1C)
    )
    if (temp <= stops.first().first) return stops.first().second
    if (temp >= stops.last().first) return stops.last().second

    for (index in 0 until stops.lastIndex) {
        val (t1, c1) = stops[index]
        val (t2, c2) = stops[index + 1]
        if (temp in t1..t2) {
            val fraction = ((temp - t1) / (t2 - t1)).toFloat()
            return Color(
                red = interpolateChannel(c1.red, c2.red, fraction),
                green = interpolateChannel(c1.green, c2.green, fraction),
                blue = interpolateChannel(c1.blue, c2.blue, fraction),
                alpha = 1f
            )
        }
    }
    return stops.last().second
}

/** Fond thermique volontairement adouci pour rester intégré aux surfaces M3. */
internal fun blendedHeatmapColor(surface: Color, source: Color, strength: Float): Color {
    val amount = strength.coerceIn(0f, 1f)
    return Color(
        red = interpolateChannel(surface.red, source.red, amount),
        green = interpolateChannel(surface.green, source.green, amount),
        blue = interpolateChannel(surface.blue, source.blue, amount),
        alpha = 1f
    )
}

private fun interpolateChannel(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
