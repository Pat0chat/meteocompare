package com.meteocompare.app.data.radar

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `base map requests form a centered five by five tile grid`() {
        val requests = baseTileRequests(city, zoom = 9, radius = 2)
        assertEquals(25, requests.size)
        assertTrue(requests.all { it.x in 0 until (1 shl 9) && it.y in 0 until (1 shl 9) })
        assertTrue(requests.map { it.url }.toSet().size == requests.size)
        assertTrue(requests.any { it.leftFromCenter <= 0 && it.leftFromCenter + 256 >= 0 })
        assertTrue(requests.any { it.topFromCenter <= 0 && it.topFromCenter + 256 >= 0 })
    }
}
