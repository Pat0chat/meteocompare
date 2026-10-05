package com.meteocompare.app.data.radar

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarMetadata
import java.io.IOException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class RadarRepositoryTest {
    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.8566,
        longitude = 2.3522,
        timezone = "Europe/Paris"
    )

    @Test
    fun `metadata normalization keeps only safe unique recent RainViewer frames`() {
        val frames = normalizeRadarFrames(
            listOf(
                20L to "/v2/radar/b",
                10L to "/v2/radar/a",
                20L to "/v2/radar/newer",
                null to "/v2/radar/missing",
                30L to "https://evil.test/radar.png",
                -1L to "/v2/radar/x"
            )
        )
        assertEquals(listOf(10L, 20L), frames.map { it.timeEpochSeconds })
        assertEquals("/v2/radar/newer", frames.last().path)
    }

    @Test
    fun `RainViewer host allowlist rejects lookalike hosts`() {
        assertTrue(isAllowedRainViewerHost("https://tilecache.rainviewer.com"))
        assertFalse(isAllowedRainViewerHost("http://tilecache.rainviewer.com"))
        assertFalse(isAllowedRainViewerHost("https://rainviewer.com.evil.test"))
        assertFalse(isAllowedRainViewerHost("https://rainviewer.com"))
    }

    @Test
    fun `radar image URL preserves web colour scheme options and locality precision`() {
        val url = radarImageUrl(
            RadarMetadata("https://tilecache.rainviewer.com", listOf(RadarFrame(1, "/v2/radar/test"))),
            RadarFrame(1, "/v2/radar/test"),
            city,
            7
        )
        assertEquals(
            "https://tilecache.rainviewer.com/v2/radar/test/512/7/48.85660/2.35220/2/0_1.png",
            url
        )
    }

    @Test
    fun `base map requests only cover the visible viewport like the web radar`() {
        val phone = baseTileRequests(city, zoom = 9, viewportWidth = 360, viewportHeight = 360)
        val expanded = baseTileRequests(city, zoom = 9, viewportWidth = 1200, viewportHeight = 800)

        assertEquals(6, phone.size)
        assertEquals(20, expanded.size)
        assertTrue(phone.all { it.x in 0 until (1 shl 9) && it.y in 0 until (1 shl 9) })
        assertEquals(phone.size, phone.map { it.url }.toSet().size)
        assertTrue(phone.any { it.leftFromCenter <= 0 && it.leftFromCenter + 256 >= 0 })
        assertTrue(phone.any { it.topFromCenter <= 0 && it.topFromCenter + 256 >= 0 })
        assertTrue(phone.size < 25)
    }

    @Test
    fun `radar caches are bounded for mobile memory pressure`() {
        // 512x512 ARGB images are ~1 MiB each; 256x256 OSM tiles are ~256 KiB.
        // These limits keep the theoretical pixel payload around 24 MiB.
        assertEquals(16, FRAME_CACHE_SIZE)
        assertEquals(32, TILE_CACHE_SIZE)
    }

    @Test
    fun `cancelling coroutine cancels the underlying OkHttp call`() = runTest {
        val call = HoldingCall()
        val job = launch { call.awaitBodyBytes() }
        yield()
        assertTrue(call.enqueued)

        job.cancelAndJoin()

        assertTrue(call.cancelled)
    }

    private class HoldingCall : Call {
        private val request = Request.Builder().url("https://example.test/radar.png").build()
        var enqueued = false
        var cancelled = false

        override fun request(): Request = request
        override fun execute(): Response = throw IOException("not used")
        override fun enqueue(responseCallback: Callback) {
            enqueued = true
        }
        override fun cancel() {
            cancelled = true
        }
        override fun isExecuted(): Boolean = enqueued
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout.NONE
        override fun addEventListener(eventListener: EventListener) = Unit
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = HoldingCall()
    }
}
