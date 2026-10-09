package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetColorHexTest {
    @Test fun accepts_six_digit_hex_with_or_without_hash_and_any_case() {
        assertEquals(0xFF123ABC.toInt(), parseWidgetHexColor("#123AbC"))
        assertEquals(0xFF123ABC.toInt(), parseWidgetHexColor("123abc"))
        assertEquals(0xFF00FF01.toInt(), parseWidgetHexColor(" #00fF01 "))
    }

    @Test fun rejects_invalid_incomplete_or_argb_hex() {
        listOf("", "#", "#12", "#12345", "#12GGFF", "#1234567", "#FF123456", "red")
            .forEach { assertNull("unexpected valid HEX: $it", parseWidgetHexColor(it)) }
    }

    @Test fun formatter_uses_six_rgb_digits_and_does_not_expose_alpha() {
        assertEquals("#123456", formatWidgetHexColor(0x80123456.toInt()))
        assertEquals("#000000", formatWidgetHexColor(0xFF000000.toInt()))
        assertEquals("#FFFFFF", formatWidgetHexColor(0xFFFFFFFF.toInt()))
    }

    @Test fun hex_roundtrip_preserves_exact_rgb_without_hsv_rounding() {
        listOf(0xFF123456.toInt(), 0xFFAC0043.toInt(), 0xFF00A080.toInt()).forEach { argb ->
            assertEquals(argb, parseWidgetHexColor(formatWidgetHexColor(argb)))
        }
    }
}
