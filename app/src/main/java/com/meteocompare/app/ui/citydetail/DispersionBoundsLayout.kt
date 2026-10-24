package com.meteocompare.app.ui.citydetail

import kotlin.math.roundToInt

/**
 * Coordonnées anti-collision des étiquettes min/max sous une frise.
 *
 * Quand deux valeurs sont presque identiques, on décale les libellés en paire
 * au lieu de leur laisser la même abscisse. Pour les écrans très étroits ou
 * les grandes polices, la valeur max passe sur une seconde ligne.
 */
internal data class DispersionBoundsPlacement(
    val minX: Int,
    val maxX: Int,
    val maxOnSecondLine: Boolean
)

internal fun dispersionBoundsPlacement(
    width: Int,
    minLabelWidth: Int,
    maxLabelWidth: Int,
    minCenter: Float,
    maxCenter: Float,
    gap: Int
): DispersionBoundsPlacement {
    val available = width.coerceAtLeast(0)
    val leftWidth = minLabelWidth.coerceAtLeast(0)
    val rightWidth = maxLabelWidth.coerceAtLeast(0)
    val spacing = gap.coerceAtLeast(0)

    fun clampedStart(centerX: Float, labelWidth: Int): Int =
        (centerX - labelWidth / 2f).roundToInt()
            .coerceIn(0, (available - labelWidth).coerceAtLeast(0))

    val desiredMin = clampedStart(minCenter, leftWidth)
    val desiredMax = clampedStart(maxCenter, rightWidth)
    if (leftWidth.toLong() + rightWidth + spacing > available) {
        return DispersionBoundsPlacement(desiredMin, desiredMax, maxOnSecondLine = true)
    }
    if (desiredMin + leftWidth + spacing <= desiredMax) {
        return DispersionBoundsPlacement(desiredMin, desiredMax, maxOnSecondLine = false)
    }

    // Répartit symétriquement les libellés autour de leur position médiane,
    // puis les translate si la paire arrive au bord de la frise.
    val pairWidth = leftWidth + rightWidth + spacing
    val pairStart = ((minCenter + maxCenter) / 2f - pairWidth / 2f)
        .roundToInt().coerceIn(0, available - pairWidth)
    return DispersionBoundsPlacement(
        minX = pairStart,
        maxX = pairStart + leftWidth + spacing,
        maxOnSecondLine = false
    )
}
