package com.meteocompare.app.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRefreshPolicyTest {
    private val now = 2_000_000L
    @Test fun `foreground within 30 minutes never reacquires GPS`() {
        assertFalse(LocationRefreshPolicy.shouldAcquire(now, now - 15 * 60_000L, 0, false))
        assertFalse(LocationRefreshPolicy.shouldAcquire(now, now - 29 * 60_000L, 0, false))
    }

    @Test fun `foreground after 30 minutes can reacquire once`() {
        assertTrue(LocationRefreshPolicy.shouldAcquire(now, now - 30 * 60_000L, 0, false))
    }

    @Test fun `failures are throttled on frequent activity restarts`() {
        assertFalse(LocationRefreshPolicy.shouldAcquire(now, 0, now - 20_000L, false))
        assertTrue(LocationRefreshPolicy.shouldAcquire(now, 0, now - 121_000L, false))
    }

    @Test fun `explicit refresh bypasses automatic freshness rule`() {
        assertTrue(LocationRefreshPolicy.shouldAcquire(now, now - 5_000L, 0, true))
        assertFalse(LocationRefreshPolicy.shouldAcquire(now, now - 5_000L, now - 3_000L, true))
        assertTrue(LocationRefreshPolicy.shouldAcquire(now, now - 5_000L, now - 15_000L, true))
    }

    @Test fun `clock correction cannot permanently lock location`() {
        assertTrue(LocationRefreshPolicy.shouldAcquire(now, now + 100_000L, 0, false))
        assertTrue(LocationRefreshPolicy.shouldAcquire(now, 0, now + 100_000L, false))
    }

    @Test fun `never used when location is disabled`() {
        // No implicit opt-in: the controller checks store.enabled before this policy.
        assertFalse(LocationRefreshPolicy.shouldAcquire(now, now - 1_000L, 0, false))
    }
}
