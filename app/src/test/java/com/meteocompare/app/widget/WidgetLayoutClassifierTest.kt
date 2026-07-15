package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutClassifierTest {

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
}
