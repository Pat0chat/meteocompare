package com.meteocompare.app.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFixPolicyTest {
    private val now = 900_000_000_000L

    @Test fun `recent cached fix can be reused after Maps warmed system provider`() {
        assertTrue(LocationFixPolicy.isRecentCachedFix(now, now - 5_000_000_000L))
        assertTrue(LocationFixPolicy.isRecentCachedFix(now, now - 179_000_000_000L))
    }

    @Test fun `old or invalid cached fixes are never mistaken for current position`() {
        assertFalse(LocationFixPolicy.isRecentCachedFix(now, now - 181_000_000_000L))
        assertFalse(LocationFixPolicy.isRecentCachedFix(now, 0L))
        assertFalse(LocationFixPolicy.isRecentCachedFix(now, now + 1_000_000_000L))
    }

    @Test fun `widget can reuse fresh successful cell without another sensor request`() {
        assertTrue(LocationFixPolicy.canReuseFreshSuccess(2_000_000L, 1_990_000L))
        assertFalse(LocationFixPolicy.canReuseFreshSuccess(2_000_000L, 1_984_999L))
        assertFalse(LocationFixPolicy.canReuseFreshSuccess(2_000_000L, 0L))
        assertFalse(LocationFixPolicy.canReuseFreshSuccess(2_000_000L, 2_000_001L))
    }

    @Test fun `manual request waits for cold location but auto is bounded`() {
        assertTrue(LocationFixPolicy.EXPLICIT_TIMEOUT_MS > LocationFixPolicy.AUTOMATIC_TIMEOUT_MS)
        assertTrue(LocationFixPolicy.EXPLICIT_TIMEOUT_MS <= 30_000L)
        assertTrue(LocationFixPolicy.CACHED_FIX_MAX_AGE_MS <= 5 * 60_000L)
    }
}
