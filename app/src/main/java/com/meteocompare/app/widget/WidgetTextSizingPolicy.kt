package com.meteocompare.app.widget

/**
 * Never inflate fonts in tiny cells simply because the user chose a large scale.
 * The launcher is allowed to report dimensions different from the screen grid.
 * Normal mode keeps a consistent 100%; Auto grows gradually only with room.
 */
internal fun widgetTextScale(
    widthDp: Float,
    heightDp: Float,
    auto: Boolean,
    requestedPct: Int
): Float {
    val requested = if (auto) when {
        widthDp >= 360f && heightDp >= 180f -> 120
        widthDp >= 240f && heightDp >= 140f -> 112
        widthDp >= 300f && heightDp >= 75f -> 108
        else -> 100
    } else requestedPct.coerceIn(
        85, 140
    )
    val cap = when {
        widthDp < 115f || heightDp < 65f -> 110
        widthDp < 220f || heightDp < 100f -> 118
        widthDp < 320f -> 125
        else -> 140
    }
    return requested.coerceAtMost(cap) / 100f
}
