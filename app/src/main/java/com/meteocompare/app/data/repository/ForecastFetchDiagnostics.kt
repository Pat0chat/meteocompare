package com.meteocompare.app.data.repository

import java.util.concurrent.atomic.AtomicLong

/** Compteurs par instance de repository (jamais de coordonnées dans les logs). */
internal class ForecastFetchDiagnostics {
    private val cacheHits = AtomicLong()
    private val cacheMisses = AtomicLong()
    private val fetchStarted = AtomicLong()
    private val fetchCoalesced = AtomicLong()

    fun cacheRead(hit: Boolean) {
        if (hit) cacheHits.incrementAndGet() else cacheMisses.incrementAndGet()
    }

    fun fetchStarted() { fetchStarted.incrementAndGet() }
    fun fetchCoalesced() { fetchCoalesced.incrementAndGet() }

    fun snapshot(): ForecastFetchSnapshot = ForecastFetchSnapshot(
        cacheHit = cacheHits.get(),
        cacheMiss = cacheMisses.get(),
        fetchStarted = fetchStarted.get(),
        fetchCoalesced = fetchCoalesced.get()
    )
}

internal data class ForecastFetchSnapshot(
    val cacheHit: Long,
    val cacheMiss: Long,
    val fetchStarted: Long,
    val fetchCoalesced: Long
) {
    override fun toString(): String =
        "CACHE_HIT=$cacheHit CACHE_MISS=$cacheMiss " +
            "FETCH_STARTED=$fetchStarted FETCH_COALESCED=$fetchCoalesced"
}
