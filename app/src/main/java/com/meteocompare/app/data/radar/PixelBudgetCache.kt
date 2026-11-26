package com.meteocompare.app.data.radar

import com.meteocompare.app.domain.radar.RadarImage

/**
 * Process-local radar pixel cache bounded by *bytes*, not only by entry count.
 *
 * A radar frame is normally 512x512, but other sources may provide larger
 * images. The former entry-only LRU allowed a few oversized responses to keep
 * much more memory alive than expected. Callers can read/write from either IO
 * or the Android memory-pressure callback, so operations are synchronized.
 * Eviction only drops cache references; an image already displayed by Compose
 * remains valid until that screen releases it.
 */
internal data class CacheWriteStats(
    val evicted: Int,
    val entries: Int,
    val bytes: Long,
    val stored: Boolean
)

internal class PixelBudgetCache(
    private val maxEntries: Int,
    private val maxBytes: Long
) {
    init {
        require(maxEntries > 0)
        require(maxBytes > 0)
    }

    private val entries = LinkedHashMap<String, RadarImage>(maxEntries, 0.75f, true)
    private var retainedBytes = 0L

    @Synchronized
    operator fun get(key: String): RadarImage? = entries[key]

    @Synchronized
    fun put(key: String, image: RadarImage): CacheWriteStats {
        val bytes = image.pixelBytes()
        // Never evict useful images just to hold an oversized response.
        if (bytes > maxBytes) return CacheWriteStats(0, entries.size, retainedBytes, false)
        entries.remove(key)?.let { retainedBytes -= it.pixelBytes() }
        entries[key] = image
        retainedBytes += bytes
        var evicted = 0
        val iterator = entries.entries.iterator()
        while ((entries.size > maxEntries || retainedBytes > maxBytes) && iterator.hasNext()) {
            retainedBytes -= iterator.next().value.pixelBytes()
            iterator.remove()
            evicted++
        }
        return CacheWriteStats(evicted, entries.size, retainedBytes, true)
    }

    @Synchronized
    fun clear() {
        entries.clear()
        retainedBytes = 0L
    }

    @Synchronized
    fun sizeBytes(): Long = retainedBytes

    @Synchronized
    fun size(): Int = entries.size

    private fun RadarImage.pixelBytes(): Long = argb.size.toLong() * Int.SIZE_BYTES
}
