package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutClassifierTest {

    @Test
    fun `current conditions stay visible by default on every widget layout`() {
        WidgetLayoutKind.entries.forEach { kind ->
            assertEquals(true, shouldDisplayCurrentConditions(kind, true))
        }
    }

    @Test
    fun `turning off current conditions only removes the separate tall header`() {
        WidgetLayoutKind.entries.forEach { kind ->
            val hasSeparateHeader = kind == WidgetLayoutKind.COMPACT_TALL ||
                kind == WidgetLayoutKind.EXTRA_LARGE
            assertEquals(!hasSeparateHeader, shouldDisplayCurrentConditions(kind, false))
        }
        assertEquals(false, shouldDisplayCurrentConditions(classifyWidgetLayout(160f, 160f), false))
        assertEquals(false, shouldDisplayCurrentConditions(classifyWidgetLayout(420f, 170f), false))
        assertEquals(true, shouldDisplayCurrentConditions(classifyWidgetLayout(420f, 90f), false))
    }

    @Test
    fun `one by one stays tiny`() {
        assertEquals(WidgetLayoutKind.TINY, classifyWidgetLayout(72f, 72f))
    }

    @Test
    fun `two by one stays small`() {
        assertEquals(WidgetLayoutKind.SMALL, classifyWidgetLayout(180f, 78f))
    }

    @Test
    fun `two by two uses dedicated compact tall layout`() {
        assertEquals(WidgetLayoutKind.COMPACT_TALL, classifyWidgetLayout(160f, 160f))
    }

    @Test
    fun `very narrow tall resize does not impersonate a square widget`() {
        assertEquals(WidgetLayoutKind.SMALL, classifyWidgetLayout(90f, 170f))
    }

    @Test
    fun `three by one uses medium layout`() {
        assertEquals(WidgetLayoutKind.MEDIUM, classifyWidgetLayout(250f, 85f))
    }

    @Test
    fun `four by one uses large layout`() {
        assertEquals(WidgetLayoutKind.LARGE, classifyWidgetLayout(340f, 90f))
    }

    @Test
    fun `five by one uses wide layout`() {
        assertEquals(WidgetLayoutKind.WIDE, classifyWidgetLayout(420f, 90f))
    }

    @Test
    fun `wide two row widget prefers extra large over wide`() {
        assertEquals(WidgetLayoutKind.EXTRA_LARGE, classifyWidgetLayout(420f, 170f))
    }

    @Test
    fun `three by two keeps four forecast cards`() {
        assertEquals(4, extendedForecastItemCount(319f))
    }

    @Test
    fun `four and five by two show five forecast cards`() {
        assertEquals(5, extendedForecastItemCount(320f))
        assertEquals(5, extendedForecastItemCount(420f))
    }
    @Test
    fun `narrow pills select readable summaries rather than clipped charts`() {
        assertEquals(
            WidgetLayoutKind.TINY,
            classifyWidgetLayoutForShape(170f, 150f, WidgetCornerStyle.PILL, 40f, 14f)
        )
        assertEquals(
            WidgetLayoutKind.COMPACT_TALL,
            classifyWidgetLayoutForShape(280f, 150f, WidgetCornerStyle.PILL, 35f, 14f)
        )
        assertEquals(
            WidgetLayoutKind.EXTRA_LARGE,
            classifyWidgetLayoutForShape(430f, 170f, WidgetCornerStyle.PILL, 40f, 14f)
        )
    }

    @Test
    fun `non-pill shapes keep their size-based forecast modes`() {
        for (shape in listOf(WidgetCornerStyle.SQUARE, WidgetCornerStyle.SOFT,
            WidgetCornerStyle.ROUNDED, WidgetCornerStyle.CUSTOM)) {
            assertEquals(
                classifyWidgetLayout(320f, 170f),
                classifyWidgetLayoutForShape(320f, 170f, shape, 40f, 14f)
            )
        }
    }
    @Test
    fun `nova height changes retain the lower forecast as room becomes available`() {
        assertEquals(WidgetLayoutKind.LARGE, classifyWidgetLayout(340f, 119f))
        assertEquals(WidgetLayoutKind.EXTRA_LARGE, classifyWidgetLayout(340f, 120f))
        assertEquals(WidgetLayoutKind.EXTRA_LARGE, classifyWidgetLayout(340f, 160f))
        assertEquals(WidgetLayoutKind.SMALL, classifyWidgetLayout(175f, 119f))
        assertEquals(WidgetLayoutKind.COMPACT_TALL, classifyWidgetLayout(175f, 120f))
    }

}
