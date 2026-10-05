package com.meteocompare.app.testutil

import com.meteocompare.app.data.radar.RadarRepository
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarBaseTile
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarImage
import com.meteocompare.app.domain.radar.RadarMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeRadarRepository @Inject constructor() : RadarRepository {
    private val frames = List(7) { index ->
        RadarFrame(
            timeEpochSeconds = 1_700_000_000L + index * 600L,
            path = "/v2/radar/test-$index"
        )
    }
    private val transparentImage = RadarImage(512, 512, IntArray(512 * 512))

    val metadataForceFlags = mutableListOf<Boolean>()
    val radarZooms = mutableListOf<Int>()
    val baseZooms = mutableListOf<Int>()

    override suspend fun metadata(forceRefresh: Boolean): RadarMetadata {
        metadataForceFlags += forceRefresh
        return RadarMetadata(
            host = "https://tilecache.rainviewer.com",
            past = frames,
            generatedEpochSeconds = frames.last().timeEpochSeconds
        )
    }

    override suspend fun radarImage(
        metadata: RadarMetadata,
        frame: RadarFrame,
        city: City,
        zoom: Int
    ): RadarImage {
        radarZooms += zoom
        return transparentImage
    }

    override suspend fun baseTiles(city: City, zoom: Int, viewportWidth: Int, viewportHeight: Int): List<RadarBaseTile> {
        baseZooms += zoom
        return emptyList()
    }

    fun reset() {
        metadataForceFlags.clear()
        radarZooms.clear()
        baseZooms.clear()
    }
}
