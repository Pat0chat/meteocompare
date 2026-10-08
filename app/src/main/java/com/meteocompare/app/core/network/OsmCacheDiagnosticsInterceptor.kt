package com.meteocompare.app.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Diagnostics DEBUG seulement, après la stratégie de cache native OkHttp.
 *
 * Intercepteur *applicatif* (pas réseau) : il voit les réponses provenant
 * uniquement du disque, du réseau, et celles revalidées par HTTP 304.
 * N'ajoute aucun header qui modifierait la politique de cache d'OSM.
 */
class OsmCacheDiagnosticsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isOsmTile = request.url.host == "tile.openstreetmap.org" &&
            request.url.encodedPath.endsWith(".png")
        val response = chain.proceed(request)
        if (isOsmTile) {
            val networkStatus = response.networkResponse?.code
            val source = osmCacheSource(response.cacheResponse != null, networkStatus)
            Log.d(
                "MeteoCompare/OSMCache",
                "$source tile=${request.url.encodedPath.removePrefix("/")} " +
                    "status=${response.code} networkStatus=${networkStatus ?: "-"}"
            )
        }
        return response
    }
}

/** Séparé du réseau Android pour tester sans serveur ni disque. */
internal fun osmCacheSource(hasCacheResponse: Boolean, networkStatus: Int?): String = when {
    hasCacheResponse && networkStatus == 304 -> "REVALIDATED_304"
    hasCacheResponse && networkStatus != null -> "REVALIDATED"
    hasCacheResponse -> "DISK_HIT"
    networkStatus != null -> "NETWORK"
    else -> "UNKNOWN"
}
