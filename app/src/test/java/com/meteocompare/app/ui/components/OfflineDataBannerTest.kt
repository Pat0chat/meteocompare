package com.meteocompare.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OfflineDataBannerTest {

    private val now = Instant.parse("2026-07-22T12:00:00Z")

    @Test
    fun `moins de six heures reste recent`() {
        assertEquals(
            OfflineDataAgeLevel.RECENT,
            offlineDataAgeLevel(Instant.parse("2026-07-22T06:01:00Z"), now)
        )
    }

    @Test
    fun `entre six et vingt quatre heures devient aging`() {
        assertEquals(
            OfflineDataAgeLevel.AGING,
            offlineDataAgeLevel(Instant.parse("2026-07-22T06:00:00Z"), now)
        )
    }

    @Test
    fun `vingt quatre heures ou plus devient stale`() {
        assertEquals(
            OfflineDataAgeLevel.STALE,
            offlineDataAgeLevel(Instant.parse("2026-07-21T12:00:00Z"), now)
        )
    }

    @Test
    fun `timestamp absent reste unknown`() {
        assertEquals(OfflineDataAgeLevel.UNKNOWN, offlineDataAgeLevel(null, now))
    }
}
