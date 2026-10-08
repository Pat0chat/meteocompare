package com.meteocompare.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class OsmCacheDiagnosticsInterceptorTest {
    @Test
    fun `fresh or still valid OSM response is served from disk`() {
        assertEquals("DISK_HIT", osmCacheSource(hasCacheResponse = true, networkStatus = null))
    }

    @Test
    fun `expired ETag validated by 304 is labelled revalidated`() {
        assertEquals("REVALIDATED_304", osmCacheSource(hasCacheResponse = true, networkStatus = 304))
    }

    @Test
    fun `uncached tile is served from the network`() {
        assertEquals("NETWORK", osmCacheSource(hasCacheResponse = false, networkStatus = 200))
    }

    @Test
    fun `non 304 cache and network responses are not incorrectly called disk hits`() {
        assertEquals("REVALIDATED", osmCacheSource(hasCacheResponse = true, networkStatus = 200))
    }
}
