package com.meteocompare.app.data.repository

import com.meteocompare.app.domain.repository.MarineRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineCachePolicyTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `cache is fresh strictly inside six hour window`() {
        assertTrue(MarineCachePolicy.isFresh(now, now))
        assertTrue(MarineCachePolicy.isFresh(now - MarineRepository.AVAILABILITY_CACHE_TTL_MS + 1, now))
        assertFalse(MarineCachePolicy.isFresh(now - MarineRepository.AVAILABILITY_CACHE_TTL_MS, now))
        assertFalse(MarineCachePolicy.isFresh(now - MarineRepository.AVAILABILITY_CACHE_TTL_MS - 1, now))
    }

    @Test
    fun `future dated cache is rejected instead of being fresh forever`() {
        assertFalse(MarineCachePolicy.isFresh(now + 1, now))
    }
}
