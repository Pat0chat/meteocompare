package com.meteocompare.app.widget

/**
 * A widget's colour is stored as ARGB, but alpha is configured independently
 * by the background opacity control. HEX input therefore always creates an
 * opaque #RRGGBB colour; existing ARGB values are kept unchanged until edited.
 */
internal fun parseWidgetHexColor(input: String): Int? {
    val digits = input.trim().removePrefix("#")
    if (digits.length != 6 || !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return null
    }
    return 0xFF000000.toInt() or digits.toInt(16)
}

internal fun formatWidgetHexColor(argb: Int): String =
    "#${(argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')}"
