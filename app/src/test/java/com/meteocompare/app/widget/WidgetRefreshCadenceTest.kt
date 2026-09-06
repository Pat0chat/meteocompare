package com.meteocompare.app.widget

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshCadenceTest {

    @Test
    fun `rendu widget conserve toujours une cadence de quinze minutes`() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(15),
            widgetDispatchIntervalMs()
        )
    }

    @Test
    fun `deux passages dans le meme bucket sont regroupes`() {
        val minute = TimeUnit.MINUTES.toMillis(1)
        assertFalse(
            isWidgetDispatchDue(
                lastDispatchAtMs = 10 * minute + 1_000L,
                nowMs = 14 * minute + 59_000L,
                force = false
            )
        )
    }

    @Test
    fun `nouveau bucket de quinze minutes declenche le rendu cache only`() {
        val minute = TimeUnit.MINUTES.toMillis(1)
        assertTrue(
            isWidgetDispatchDue(
                lastDispatchAtMs = 14 * minute + 59_000L,
                nowMs = 15 * minute + 1_000L,
                force = false
            )
        )
    }

    @Test
    fun `refresh force ignore le bucket`() {
        assertTrue(
            isWidgetDispatchDue(
                lastDispatchAtMs = 1_000L,
                nowMs = 2_000L,
                force = true
            )
        )
    }

    @Test
    fun `recul dhorloge force une reconstruction de securite`() {
        assertTrue(
            isWidgetDispatchDue(
                lastDispatchAtMs = 10_000L,
                nowMs = 5_000L,
                force = false
            )
        )
    }
}
