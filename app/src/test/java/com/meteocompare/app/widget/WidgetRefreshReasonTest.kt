package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshReasonTest {
    @Test
    fun `toutes les causes se decodent`() {
        WidgetRefreshReason.entries.forEach { reason ->
            assertEquals(reason, widgetRefreshReason(reason.name))
        }
    }

    @Test
    fun `cause inconnue utilise le rattrapage prudent`() {
        assertEquals(WidgetRefreshReason.CATCH_UP, widgetRefreshReason("invalid"))
        assertEquals(WidgetRefreshReason.PERIODIC, widgetRefreshReason(null, isPeriodic = true))
    }

    @Test
    fun `garde de cadence empeche le deuxieme refresh suite a configuration`() {
        val savedAt = 10 * widgetDispatchIntervalMs() + 1000
        assertEquals(false, isWidgetDispatchDue(savedAt, savedAt + 1000, force = false))
        assertEquals(true, isWidgetDispatchDue(savedAt, savedAt + 1000, force = true))
    }
}
