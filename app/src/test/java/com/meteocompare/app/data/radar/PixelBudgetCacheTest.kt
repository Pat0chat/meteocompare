package com.meteocompare.app.data.radar

import com.meteocompare.app.domain.radar.RadarImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelBudgetCacheTest {
    private fun image(pixels: Int) = RadarImage(pixels, 1, IntArray(pixels))

    @Test fun `evicts least recently used image when byte budget is reached`() {
        val cache = PixelBudgetCache(maxEntries = 5, maxBytes = 12)
        val a = image(1)
        val b = image(1)
        cache.put("a", a)
        cache.put("b", b)
        assertSame(a, cache["a"]) // Most recent is now a.
        cache.put("c", image(2)) // 8 bytes; one 4-byte entry must go.
        assertNull(cache["b"])
        assertSame(a, cache["a"])
        assertEquals(12L, cache.sizeBytes())
    }

    @Test fun `replacing an entry updates byte accounting`() {
        val cache = PixelBudgetCache(maxEntries = 3, maxBytes = 20)
        cache.put("a", image(2))
        cache.put("a", image(3))
        assertEquals(12L, cache.sizeBytes())
        assertEquals(1, cache.size())
    }

    @Test fun `oversized images are not retained or allowed to evict useful entries`() {
        val cache = PixelBudgetCache(maxEntries = 4, maxBytes = 12)
        val small = image(2)
        cache.put("small", small)
        cache.put("huge", image(4))
        assertNull(cache["huge"])
        assertSame(small, cache["small"])
        assertEquals(8L, cache.sizeBytes())
    }

    @Test fun `oversized replacement preserves the previous cached image and accounting`() {
        val cache = PixelBudgetCache(maxEntries = 3, maxBytes = 12)
        val previous = image(2)
        cache.put("frame", previous)
        val stats = cache.put("frame", image(4))
        assertTrue(!stats.stored)
        assertEquals(0, stats.evicted)
        assertEquals(1, stats.entries)
        assertEquals(8L, stats.bytes)
        assertSame(previous, cache["frame"])
    }

    @Test fun `entry bound still applies for small images`() {
        val cache = PixelBudgetCache(maxEntries = 2, maxBytes = 100)
        cache.put("a", image(1))
        cache.put("b", image(1))
        cache.put("c", image(1))
        assertNull(cache["a"])
        assertEquals(2, cache.size())
    }

    @Test fun `clearing cache releases strong references and accounts zero bytes`() {
        val cache = PixelBudgetCache(maxEntries = 2, maxBytes = 100)
        cache.put("a", image(1))
        cache.clear()
        assertNull(cache["a"])
        assertEquals(0L, cache.sizeBytes())
        assertEquals(0, cache.size())
    }

    @Test fun `all thirteen RainViewer frames survive repeated playback without redecoding`() {
        // At 512x512 ARGB, one frame occupies 1 MiB. The old 8-MiB budget
        // evicted early frames before the next playback loop.
        val cache = PixelBudgetCache(FRAME_CACHE_SIZE, FRAME_CACHE_BYTES)
        val frames = (0 until 13).map { index ->
            "frame_$index" to RadarImage(512, 512, IntArray(512 * 512))
        }
        frames.forEach { (key, image) ->
            assertEquals(0, cache.put(key, image).evicted)
        }
        assertEquals(13, cache.size())
        assertEquals(13L * 1024 * 1024, cache.sizeBytes())
        // A second and third playback must hit the same objects rather than
        // triggering another fetch/decode; no eviction has happened.
        repeat(2) {
            frames.forEach { (key, image) -> assertSame(image, cache[key]) }
        }
    }

    @Test fun `seventeenth radar frame evicts only the LRU under 16 MiB budget`() {
        val cache = PixelBudgetCache(FRAME_CACHE_SIZE, FRAME_CACHE_BYTES)
        val pixels = RadarImage(512, 512, IntArray(512 * 512))
        repeat(16) { cache.put("frame_$it", pixels) }
        val stats = cache.put("new_frame", pixels)
        assertEquals(1, stats.evicted)
        assertTrue(stats.stored)
        assertEquals(16, stats.entries)
        assertEquals(FRAME_CACHE_BYTES, stats.bytes)
        assertNull(cache["frame_0"])
        assertSame(pixels, cache["new_frame"])
    }

    @Test fun `cache write stats report oversize images accurately`() {
        val cache = PixelBudgetCache(3, 8)
        val stats = cache.put("oversized", image(3))
        assertTrue(!stats.stored)
        assertEquals(0, stats.evicted)
        assertEquals(0, stats.entries)
        assertEquals(0L, stats.bytes)
    }

    @Test fun `simultaneous reads and writes do not break the budget`() {
        val cache = PixelBudgetCache(maxEntries = 4, maxBytes = 64)
        val threads = (0 until 4).map { worker ->
            Thread {
                repeat(1000) { index ->
                    cache.put("${worker}_${index % 8}", image(3))
                    cache["${worker}_${index % 8}"]
                }
            }.apply { start() }
        }
        threads.forEach { it.join() }
        assertTrue(cache.sizeBytes() <= 64L)
        assertTrue(cache.size() <= 4)
    }
}
