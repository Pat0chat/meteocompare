package com.meteocompare.app.ui.components

import java.time.Instant
import com.meteocompare.app.domain.model.isForecastStale
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

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
    fun `home alert shares stale threshold with offline banner`() {
        val boundary = now.minusSeconds(86_400)
        assertFalse(isForecastStale(boundary.plusMillis(1), now))
        assertTrue(isForecastStale(boundary, now))
        assertTrue(isForecastStale(boundary.minusMillis(1), now))
        assertFalse(isForecastStale(null, now))
        assertFalse(isForecastStale(now.plusSeconds(60), now))
        listOf(boundary.plusMillis(1), boundary, boundary.minusMillis(1), null).forEach { fetchedAt ->
            assertEquals(offlineDataAgeLevel(fetchedAt, now) == OfflineDataAgeLevel.STALE,
                isForecastStale(fetchedAt, now))
        }
    }

    @Test
    fun `timestamp absent reste unknown`() {
        assertEquals(OfflineDataAgeLevel.UNKNOWN, offlineDataAgeLevel(null, now))
    }
}
