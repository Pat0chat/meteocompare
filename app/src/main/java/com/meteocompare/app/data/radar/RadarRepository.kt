package com.meteocompare.app.data.radar

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import com.meteocompare.app.BuildConfig
import com.meteocompare.app.di.IoDispatcher
import com.meteocompare.app.di.RadarOkHttp
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.radar.RadarBaseTile
import com.meteocompare.app.domain.radar.RadarFrame
import com.meteocompare.app.domain.radar.RadarImage
import com.meteocompare.app.domain.radar.RadarMetadata
import android.util.Log
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.tan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val RADAR_METADATA_URL = "https://api.rainviewer.com/public/weather-maps.json"
private const val OSM_TILE_URL = "https://tile.openstreetmap.org"
private const val RADAR_COLOR_SCHEME = 2
private const val RADAR_OPTIONS = "0_1"
private const val METADATA_TTL_MS = 5 * 60_000L
internal const val FRAME_CACHE_SIZE = 16
internal const val TILE_CACHE_SIZE = 32
internal const val FRAME_CACHE_BYTES = 16L * 1024L * 1024L
internal const val TILE_CACHE_BYTES = 4L * 1024L * 1024L
private const val DOWNLOAD_LOCK_STRIPES = 32

interface RadarRepository {
    suspend fun metadata(forceRefresh: Boolean = false): RadarMetadata
    suspend fun radarImage(metadata: RadarMetadata, frame: RadarFrame, city: City, zoom: Int): RadarImage
    suspend fun baseTiles(
        city: City,
        zoom: Int,
        viewportWidth: Int = 512,
        viewportHeight: Int = 360
    ): List<RadarBaseTile>
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

internal fun baseTileRequests(
    city: City,
    zoom: Int,
    viewportWidth: Int = 512,
    viewportHeight: Int = 360
): List<BaseTileRequest> {
    val width = max(280, viewportWidth).toDouble()
    val height = max(180, viewportHeight).toDouble()
    val center = projectWebMercator(city.latitude, city.longitude, zoom)
    val left = center.x - width / 2
    val top = center.y - height / 2
    val startX = floor(left / 256).toInt()
    val endX = floor((left + width) / 256).toInt()
    val tileCount = 1 shl zoom
    val startY = max(0, floor(top / 256).toInt())
    val endY = minOf(tileCount - 1, floor((top + height) / 256).toInt())
    val rows = mutableListOf<BaseTileRequest>()
    for (y in startY..endY) for (rawX in startX..endX) {
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


internal suspend fun Call.awaitBodyBytes(urlForError: String = request().url.toString()): ByteArray =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        // Cancellation can happen while a response is queued. Avoid
                        // reading/decompressing a body that the screen no longer needs.
                        if (!continuation.isActive) return
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code} for $urlForError")
                        val bytes = it.body.bytes()
                        if (continuation.isActive) continuation.resume(bytes)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

/** The UI_HIDDEN callback is lifecycle information, not a low-memory signal. */
@Suppress("DEPRECATION")
internal fun shouldClearRadarCaches(level: Int): Boolean =
    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

@Singleton
class RainViewerRadarRepository @Inject constructor(
    // L'instance OkHttp du radar est créée à la première requête SUR Dispatchers.IO.
    // Injecter directement OkHttpClient initialisait context.cacheDir sur le main
    // thread lors de la création du ViewModel (StrictMode DiskReadViolation).
    @param:RadarOkHttp private val client: Lazy<OkHttpClient>,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationContext private val context: Context
) : RadarRepository {
    private val metadataMutex = Mutex()
    private var cachedMetadata: RadarMetadata? = null
    private var cachedMetadataAt = 0L

    // Up to 13 RainViewer frames of 512x512 ARGB pixels (~13 MiB) must fit in
    // memory to avoid re-decoding on every playback loop. The byte budget and
    // entry count still protect against unexpected image dimensions.
    private val frameCache = PixelBudgetCache(FRAME_CACHE_SIZE, FRAME_CACHE_BYTES)
    private val tileCache = PixelBudgetCache(TILE_CACHE_SIZE, TILE_CACHE_BYTES)

    // This singleton lives as long as the app process. Register with the
    // Application context only, never an Activity. Dropping our references is
    // safe even while a ViewModel is drawing an image it already received.
    @Suppress("DEPRECATION")
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        // Android has deprecated this callback, but ComponentCallbacks2 still
        // invokes it on supported devices; retain the memory-pressure handling.
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = clearImageCaches("low_memory")
        // Android has deprecated this callback, but ComponentCallbacks2 still
        // invokes it on supported devices; retain the memory-pressure handling.
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onTrimMemory(level: Int) {
            // UI_HIDDEN (20) is not itself memory pressure. Preserve frames for
            // a quick return to the radar; clear when Android really needs RAM.
            if (shouldClearRadarCaches(level)) clearImageCaches("trim_$level")
        }
    }

    init {
        context.registerComponentCallbacks(memoryCallbacks)
    }

    private fun clearImageCaches(reason: String) {
        frameCache.clear()
        tileCache.clear()
        if (BuildConfig.DEBUG) Log.d("MeteoCompare/RadarCache", "CLEARED reason=$reason")
    }

    private val downloadLocks = Array(DOWNLOAD_LOCK_STRIPES) { Mutex() }
    // Keep OSM downloads/PNG decodes from saturating CPU and sockets during
    // large/full-screen viewports. Weather radar frames are not throttled here.
    private val tileDownloadLimiter = Semaphore(4)

    private fun downloadLock(url: String): Mutex =
        downloadLocks[(url.hashCode() and Int.MAX_VALUE) % downloadLocks.size]

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
        // Only log the opaque frame token, never the locality coordinates in the URL.
        val frameId = frame.path.substringAfterLast('/')
        frameCache[url]?.let {
            if (BuildConfig.DEBUG) Log.d("MeteoCompare/RadarCache", "MEMORY_HIT frame=$frameId")
            return it
        }

        // A display refresh and the nowcast analysis can ask for the same frame
        // simultaneously. Serialize identical URL work to avoid duplicate decodes.
        return downloadLock(url).withLock download@{
            frameCache[url]?.let {
                if (BuildConfig.DEBUG) Log.d("MeteoCompare/RadarCache", "MEMORY_HIT frame=$frameId coalesced=true")
                return@download it
            }
            val image = decodeImage(getBytes(url))
            val stats = frameCache.put(url, image)
            if (BuildConfig.DEBUG) {
                Log.d("MeteoCompare/RadarCache", "DECODE frame=$frameId " +
                    "dimensions=${image.width}x${image.height} " +
                    "entries=${stats.entries} retainedBytes=${stats.bytes}")
                if (stats.evicted > 0) {
                    Log.d("MeteoCompare/RadarCache", "EVICTION frame=$frameId " +
                        "count=${stats.evicted} retainedBytes=${stats.bytes}")
                }
                if (!stats.stored) Log.d("MeteoCompare/RadarCache", "NOT_CACHED frame=$frameId oversized=true")
            }
            image
        }
    }

    override suspend fun baseTiles(
        city: City,
        zoom: Int,
        viewportWidth: Int,
        viewportHeight: Int
    ): List<RadarBaseTile> = coroutineScope {
        baseTileRequests(city, zoom, viewportWidth, viewportHeight).map { tile ->
            async {
                try {
                    tileCache[tile.url]?.let { cached ->
                        if (BuildConfig.DEBUG) {
                            Log.d("MeteoCompare/OSMCache", "MEMORY_HIT tile=${tile.url.removePrefix("$OSM_TILE_URL/")}")
                        }
                        return@async RadarBaseTile(tile.leftFromCenter, tile.topFromCenter, cached)
                    }
                    val image = tileDownloadLimiter.withPermit {
                        downloadLock(tile.url).withLock {
                            tileCache[tile.url] ?: decodeImage(getBytes(tile.url)).also { decoded ->
                                tileCache.put(tile.url, decoded)
                            }
                        }
                    }
                    RadarBaseTile(tile.leftFromCenter, tile.topFromCenter, image)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun getBytes(url: String): ByteArray = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MeteoCompare-Android/${BuildConfig.VERSION_NAME} (+https://github.com/Pat0chat/MeteoCompare)")
            .build()
        client.get().newCall(request).awaitBodyBytes(url)
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
