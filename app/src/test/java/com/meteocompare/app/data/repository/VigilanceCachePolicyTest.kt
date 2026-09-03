package com.meteocompare.app.data.repository

import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VigilanceCachePolicyTest {

    @Test
    fun `un cache date dans le futur nest jamais frais ni utilisable`() {
        val futureAge = Duration.ofMinutes(-1)

        assertFalse(VigilanceCachePolicy.isFresh(futureAge))
        assertFalse(VigilanceCachePolicy.isUsableFallback(futureAge))
    }

    @Test
    fun `un cache de moins dune heure est frais`() {
        assertTrue(VigilanceCachePolicy.isFresh(Duration.ofMinutes(59)))
        assertTrue(VigilanceCachePolicy.isFresh(Duration.ofHours(1)))
    }

    @Test
    fun `un cache de deux heures sert uniquement de secours`() {
        val age = Duration.ofHours(2)

        assertFalse(VigilanceCachePolicy.isFresh(age))
        assertTrue(VigilanceCachePolicy.isUsableFallback(age))
    }

    @Test
    fun `un cache de plus de six heures est refuse`() {
        assertFalse(VigilanceCachePolicy.isUsableFallback(Duration.ofHours(6).plusSeconds(1)))
    }
}
