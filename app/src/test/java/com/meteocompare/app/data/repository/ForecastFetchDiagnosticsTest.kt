package com.meteocompare.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastFetchDiagnosticsTest {
    @Test
    fun `compteurs commencent a zero et suivent les evenements reels`() {
        val counters = ForecastFetchDiagnostics()
        assertEquals(ForecastFetchSnapshot(0, 0, 0, 0), counters.snapshot())
        counters.cacheRead(true)
        counters.cacheRead(false)
        counters.cacheRead(true)
        counters.fetchStarted()
        counters.fetchCoalesced()
        counters.fetchCoalesced()
        val snapshot = counters.snapshot()
        assertEquals(ForecastFetchSnapshot(2, 1, 1, 2), snapshot)
        assertTrue(snapshot.toString().contains("CACHE_HIT=2"))
        assertTrue(snapshot.toString().contains("CACHE_MISS=1"))
        assertTrue(snapshot.toString().contains("FETCH_STARTED=1"))
        assertTrue(snapshot.toString().contains("FETCH_COALESCED=2"))
    }
}
