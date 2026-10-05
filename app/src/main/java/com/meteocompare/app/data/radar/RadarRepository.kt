package com.meteocompare.app.data.radar

import android.graphics.BitmapFactory
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarBaseTile
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarImage
import com.meteocompare.app.domain.radar.RadarMetadata
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.tan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private const val RADAR_METADATA_URL = "https://api.rainviewer.com/public/weather-maps.json"
private const val OSM_TILE_URL = "https://tile.openstreetmap.org"
private const val RADAR_COLOR_SCHEME = 2
private const val RADAR_OPTIONS = "0_1"
private const val METADATA_TTL_MS = 5 * 60_000L
private const val FRAME_CACHE_SIZE = 32
private const val TILE_CACHE_SIZE = 80

interface RadarRepository {
    suspend fun metadata(forceRefresh: Boolean = false): RadarMetadata
    suspend fun radarImage(metadata: RadarMetadata, frame: RadarFrame, city: City, zoom: Int): RadarImage
    suspend fun baseTiles(city: City, zoom: Int, radius: Int = 2): List<RadarBaseTile>
}

@Serializable
private data class RainViewerPayload(
    val host: String? = null,
    val generated: Long? = null,
    val radar: RainViewerRadar? = null
)

@Serializable
private data class RainViewerRadar(val past: List<RainViewerFrame> = emptyList())

@Serializable
private data class RainViewerFrame(val time: Long? = null, val path: String? = null)

internal fun normalizeRadarFrames(frames: List<Pair<Long?, String?>>, limit: Int = 13): List<RadarFrame> {
    val validPath = Regex("^/v2/radar/[A-Za-z0-9_-]+$")
    return frames.mapNotNull { (time, path) ->
        val cleanPath = path.orEmpty()
        if (time == null || time <= 0 || !validPath.matches(cleanPath)) null else RadarFrame(time, cleanPath)
    }.associateBy(RadarFrame::timeEpochSeconds)
        .values
        .sortedBy(RadarFrame::timeEpochSeconds)
        .takeLast(max(1, limit))
}

internal fun isAllowedRainViewerHost(host: String): Boolean =
    Regex("^https://[a-z0-9.-]+\\.rainviewer\\.com$", RegexOption.IGNORE_CASE).matches(host)

internal fun radarImageUrl(metadata: RadarMetadata, frame: RadarFrame, city: City, zoom: Int): String =
    "${metadata.host}${frame.path}/512/$zoom/${"%.5f".format(java.util.Locale.US, city.latitude)}/${"%.5f".format(java.util.Locale.US, city.longitude)}/$RADAR_COLOR_SCHEME/$RADAR_OPTIONS.png"

internal data class MercatorPoint(val x: Double, val y: Double)

internal fun projectWebMercator(latitude: Double, longitude: Double, zoom: Int): MercatorPoint {
    val n = (1 shl zoom) * 256.0
    val lat = latitude.coerceIn(-85.05112878, 85.05112878)
    val rad = Math.toRadians(lat)
    return MercatorPoint(
        x = (longitude + 180) / 360 * n,
        y = (1 - asinh(tan(rad)) / Math.PI) / 2 * n
    )
}

internal data class BaseTileRequest(
    val x: Int,
    val y: Int,
    val leftFromCenter: Double,
    val topFromCenter: Double,
    val url: String
)

internal fun baseTileRequests(city: City, zoom: Int, radius: Int = 2): List<BaseTileRequest> {
    val center = projectWebMercator(city.latitude, city.longitude, zoom)
    val centerTileX = floor(center.x / 256).toInt()
    val centerTileY = floor(center.y / 256).toInt()
    val tileCount = 1 shl zoom
    val safeRadius = radius.coerceIn(1, 3)
    val rows = mutableListOf<BaseTileRequest>()
    for (dy in -safeRadius..safeRadius) for (dx in -safeRadius..safeRadius) {
        val rawX = centerTileX + dx
        val y = centerTileY + dy
        if (y !in 0 until tileCount) continue
        val x = ((rawX % tileCount) + tileCount) % tileCount
        rows += BaseTileRequest(
            x = x,
            y = y,
            leftFromCenter = rawX * 256.0 - center.x,
            topFromCenter = y * 256.0 - center.y,
            url = "$OSM_TILE_URL/$zoom/$x/$y.png"
        )
    }
    return rows
}

@Singleton
class RainViewerRadarRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : RadarRepository {
    private val metadataMutex = Mutex()
    private var cachedMetadata: RadarMetadata? = null
    private var cachedMetadataAt = 0L

    private val frameCache = object : LinkedHashMap<String, RadarImage>(FRAME_CACHE_SIZE, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RadarImage>?): Boolean = size > FRAME_CACHE_SIZE
    }
    private val tileCache = object : LinkedHashMap<String, RadarImage>(TILE_CACHE_SIZE, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RadarImage>?): Boolean = size > TILE_CACHE_SIZE
    }
    private val cacheMutex = Mutex()

    override suspend fun metadata(forceRefresh: Boolean): RadarMetadata = metadataMutex.withLock {
        val now = System.currentTimeMillis()
        cachedMetadata?.takeIf { !forceRefresh && now - cachedMetadataAt < METADATA_TTL_MS }?.let { return@withLock it }
        val body = getBytes(RADAR_METADATA_URL).decodeToString()
        val payload = json.decodeFromString<RainViewerPayload>(body)
        val host = payload.host.orEmpty()
        if (!isAllowedRainViewerHost(host)) throw IOException("Invalid RainViewer host")
        val frames = normalizeRadarFrames(payload.radar?.past.orEmpty().map { it.time to it.path })
        if (frames.isEmpty()) throw IOException("No radar frames")
        RadarMetadata(host, frames, payload.generated).also {
            cachedMetadata = it
            cachedMetadataAt = now
        }
    }

    override suspend fun radarImage(metadata: RadarMetadata, frame: RadarFrame, city: City, zoom: Int): RadarImage {
        val url = radarImageUrl(metadata, frame, city, zoom)
        cacheMutex.withLock { frameCache[url] }?.let { return it }
        val image = decodeImage(getBytes(url))
        cacheMutex.withLock { frameCache[url] = image }
        return image
    }

    override suspend fun baseTiles(city: City, zoom: Int, radius: Int): List<RadarBaseTile> = coroutineScope {
        baseTileRequests(city, zoom, radius).map { tile ->
            async {
                val cached = cacheMutex.withLock { tileCache[tile.url] }
                val image = cached ?: runCatching { decodeImage(getBytes(tile.url)) }
                    .getOrNull()
                    ?.also { cacheMutex.withLock { tileCache[tile.url] = it } }
                image?.let { RadarBaseTile(tile.leftFromCenter, tile.topFromCenter, it) }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun getBytes(url: String): ByteArray = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MeteoCompare-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body.bytes()
        }
    }

    private suspend fun decodeImage(bytes: ByteArray): RadarImage = withContext(ioDispatcher) {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("Invalid image")
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            RadarImage(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }
}
