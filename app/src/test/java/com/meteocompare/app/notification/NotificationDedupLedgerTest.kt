package com.meteocompare.app.notification

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDedupLedgerTest {

    private val now = Instant.parse("2026-09-28T05:00:00Z")

    @Test
    fun `un evenement enregistre n'est plus notifie`() {
        val entries = NotificationDedupLedger.record(emptySet(), "divergence|paris|2026-09-29", now)

        assertTrue(NotificationDedupLedger.contains(entries, "divergence|paris|2026-09-29"))
        assertFalse(NotificationDedupLedger.contains(entries, "divergence|paris|2026-09-30"))
        assertFalse(NotificationDedupLedger.contains(entries, "divergence|lyon|2026-09-29"))
    }

    @Test
    fun `reenregistrer une cle ne la duplique pas`() {
        val first = NotificationDedupLedger.record(emptySet(), "daily|paris|2026-09-28", now)
        val second = NotificationDedupLedger.record(first, "daily|paris|2026-09-28", now.plusSeconds(60))

        assertEquals(1, second.size)
    }

    @Test
    fun `les entrees anciennes sont purgees a l'ecriture`() {
        val old = NotificationDedupLedger.record(
            emptySet(),
            "change|paris|2026-09-20|PRECIPITATION|INCREASING",
            now.minus(NotificationDedupLedger.RETENTION).minus(Duration.ofMinutes(1))
        )

        val updated = NotificationDedupLedger.record(old, "daily|paris|2026-09-28", now)

        assertEquals(setOf("daily|paris|2026-09-28@${now.toEpochMilli()}"), updated)
    }

    @Test
    fun `une entree illisible est ignoree`() {
        val pruned = NotificationDedupLedger.prune(setOf("garbage", "daily|paris@abc"), now)

        assertTrue(pruned.isEmpty())
    }
}
