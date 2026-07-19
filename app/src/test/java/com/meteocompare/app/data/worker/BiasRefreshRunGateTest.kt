package com.meteocompare.app.data.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiasRefreshRunGateTest {
    @Test
    fun `premier cycle est autorise`() {
        assertTrue(BiasRefreshRunGate.shouldRun(0L, 1_000L, BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS))
    }

    @Test
    fun `cycle recent est ignore`() {
        val now = BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS
        assertFalse(
            BiasRefreshRunGate.shouldRun(
                1L,
                now,
                BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS
            )
        )
    }

    @Test
    fun `cycle redevient autorise apres vingt heures`() {
        val last = 1_000L
        assertTrue(
            BiasRefreshRunGate.shouldRun(
                last,
                last + BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS,
                BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS
            )
        )
    }

    @Test
    fun `recul horloge autorise un cycle de securite`() {
        assertTrue(BiasRefreshRunGate.shouldRun(10_000L, 5_000L, BiasRefreshRunGate.KICKOFF_MIN_INTERVAL_MS))
    }

    @Test
    fun `refresh manuel est bloque pendant trente minutes apres un succes`() {
        val last = 1_000L
        assertFalse(
            BiasRefreshRunGate.shouldRun(
                last,
                last + BiasRefreshRunGate.MANUAL_MIN_INTERVAL_MS - 1L,
                BiasRefreshRunGate.MANUAL_MIN_INTERVAL_MS
            )
        )
        assertTrue(
            BiasRefreshRunGate.shouldRun(
                last,
                last + BiasRefreshRunGate.MANUAL_MIN_INTERVAL_MS,
                BiasRefreshRunGate.MANUAL_MIN_INTERVAL_MS
            )
        )
    }

    @Test
    fun `periodic proche du kickoff est ignore sans bloquer le cycle journalier`() {
        val last = 1_000L
        assertFalse(
            BiasRefreshRunGate.shouldRun(
                last,
                last + BiasRefreshRunGate.PERIODIC_MIN_INTERVAL_MS - 1L,
                BiasRefreshRunGate.PERIODIC_MIN_INTERVAL_MS
            )
        )
        assertTrue(
            BiasRefreshRunGate.shouldRun(
                last,
                last + BiasRefreshRunGate.PERIODIC_MIN_INTERVAL_MS,
                BiasRefreshRunGate.PERIODIC_MIN_INTERVAL_MS
            )
        )
    }

}
