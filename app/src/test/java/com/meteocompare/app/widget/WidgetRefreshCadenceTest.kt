package com.meteocompare.app.widget

import com.meteocompare.app.domain.model.RefreshInterval
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshCadenceTest {
    @Test
    fun `15 minutes conserve la cadence la plus fraiche`() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(15),
            widgetDispatchIntervalMs(RefreshInterval.MINUTES_15)
        )
    }

    @Test
    fun `30 minutes evite un rendu sur deux`() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(30),
            widgetDispatchIntervalMs(RefreshInterval.MINUTES_30)
        )
    }

    @Test
    fun `3 heures reste borne a une heure pour avancer les echeances`() {
        assertEquals(
            TimeUnit.HOURS.toMillis(1),
            widgetDispatchIntervalMs(RefreshInterval.HOURS_3)
        )
    }

    @Test
    fun `manual continue de mettre a jour les heures sans fetch automatique`() {
        assertEquals(
            TimeUnit.HOURS.toMillis(1),
            widgetDispatchIntervalMs(RefreshInterval.MANUAL)
        )
    }

    @Test
    fun `deux ticks dans le meme bucket sont regroupes`() {
        val hour = TimeUnit.HOURS.toMillis(1)
        assertFalse(
            isWidgetDispatchDue(
                lastDispatchAtMs = 10 * hour + TimeUnit.MINUTES.toMillis(5),
                nowMs = 10 * hour + TimeUnit.MINUTES.toMillis(45),
                interval = RefreshInterval.HOUR_1,
                force = false
            )
        )
    }

    @Test
    fun `passage de bucket declenche le rendu`() {
        val hour = TimeUnit.HOURS.toMillis(1)
        assertTrue(
            isWidgetDispatchDue(
                lastDispatchAtMs = 10 * hour + TimeUnit.MINUTES.toMillis(59),
                nowMs = 11 * hour + TimeUnit.MINUTES.toMillis(1),
                interval = RefreshInterval.HOUR_1,
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
                interval = RefreshInterval.HOURS_6,
                force = true
            )
        )
    }
}
