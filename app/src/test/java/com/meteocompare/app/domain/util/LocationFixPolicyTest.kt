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

    @Test fun `manual refresh refuses old platform position while automatic may use it`() {
        assertTrue(LocationFixPolicy.isRecentCachedFix(now, now - 10_000_000_000L, explicit = true))
        assertFalse(LocationFixPolicy.isRecentCachedFix(now, now - 40_000_000_000L, explicit = true))
        assertTrue(LocationFixPolicy.isRecentCachedFix(now, now - 40_000_000_000L, explicit = false))
        assertFalse(LocationFixPolicy.isRecentCachedFix(now, now + 1L, explicit = true))
    }

    @Test fun `disabled or revoked location is never persisted after a slow fix`() {
        assertTrue(LocationFixPolicy.mayCommitFix(1, 1, true, true, true))
        assertTrue(LocationFixPolicy.mayCommitFix(1, 1, true, false, false))
        assertFalse(LocationFixPolicy.mayCommitFix(1, 2, true, true, true))
        assertFalse(LocationFixPolicy.mayCommitFix(1, 1, false, true, false))
        // COARSE remains authorized but a one-time FINE grant used for GPS expired.
        assertFalse(LocationFixPolicy.mayCommitFix(1, 1, true, true, false))
    }

    @Test fun `clock rollback cannot keep an old location looking fresh forever`() {
        assertFalse(LocationFixPolicy.isStoredCellStale(1_000_000L, 950_000L))
        assertTrue(LocationFixPolicy.isStoredCellStale(1_000_000L, 1_000_001L))
        assertTrue(LocationFixPolicy.isStoredCellStale(1_000_000L, 0L))
        assertTrue(LocationFixPolicy.isStoredCellStale(50_000_000L, 1_000_000L))
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
