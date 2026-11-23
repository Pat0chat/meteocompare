package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetResponsiveSizesTest {
    @Test fun `the lightweight insight widget retains a small bounded responsive catalog`() {
        assertTrue(insightWidgetResponsivePresets.isNotEmpty())
        assertEquals(insightWidgetResponsivePresets.size, insightWidgetResponsivePresets.distinct().size)
        assertTrue(insightWidgetResponsivePresets.all { it.widthDp > 0 && it.heightDp > 0 })
        assertEquals(6, insightWidgetResponsivePresets.size)
    }

    @Test fun `weather layouts respond to exact launcher size rather than a limited preset`() {
        val examples = listOf(
            Triple(78 to 84, WidgetLayoutKind.TINY, "1x1"),
            Triple(166 to 80, WidgetLayoutKind.SMALL, "2x1"),
            Triple(258 to 85, WidgetLayoutKind.MEDIUM, "3x1"),
            Triple(340 to 80, WidgetLayoutKind.LARGE, "4x1"),
            Triple(410 to 88, WidgetLayoutKind.WIDE, "5x1"),
            Triple(175 to 138, WidgetLayoutKind.COMPACT_TALL, "2x2"),
            Triple(260 to 140, WidgetLayoutKind.EXTRA_LARGE, "3x2"),
            Triple(345 to 145, WidgetLayoutKind.EXTRA_LARGE, "4x2"),
            Triple(410 to 150, WidgetLayoutKind.EXTRA_LARGE, "5x2")
        )
        examples.forEach { (available, expected, label) ->
            assertEquals("Wrong layout family for $label", expected,
                classifyWidgetLayout(available.first.toFloat(), available.second.toFloat()))
        }
    }

    @Test fun `raising widget height keeps real host width and height`() {
        for (width in listOf(45, 70, 115, 175, 215, 305, 380, 430)) {
            for (height in 40..320 step 10) {
                val previous = classifyWidgetLayout(width.toFloat(), height.toFloat())
                val next = classifyWidgetLayout(width.toFloat(), (height + 10).toFloat())
                // Taller widgets can switch to a tall layout but never to
                // another horizontal-only family once expanded.
                if (previous == WidgetLayoutKind.EXTRA_LARGE) {
                    assertEquals(WidgetLayoutKind.EXTRA_LARGE, next)
                }
                if (previous == WidgetLayoutKind.COMPACT_TALL) {
                    assertEquals(WidgetLayoutKind.COMPACT_TALL, next)
                }
            }
        }
    }
}
