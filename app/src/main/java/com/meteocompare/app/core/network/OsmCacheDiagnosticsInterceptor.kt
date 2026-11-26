package com.meteocompare.app.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Diagnostics DEBUG seulement, après la stratégie de cache native OkHttp.
 *
 * Intercepteur *applicatif* (pas réseau) : il voit les réponses provenant
 * uniquement du disque, du réseau, et celles revalidées par HTTP 304.
 * N'ajoute aucun header qui modifierait la politique de cache d'OSM ou RainViewer.
 * Un GET dans OkHttp n'est pas nécessairement un accès réseau ; les logs de
 * cette classe distinguent la source effective de la réponse HTTP.
 */
class OsmCacheDiagnosticsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isOsmTile = request.url.host == "tile.openstreetmap.org" &&
            request.url.encodedPath.endsWith(".png")
        val radarFrameId = radarFrameIdForDiagnostics(request.url.host, request.url.pathSegments)
        val response = chain.proceed(request)
        val networkStatus = response.networkResponse?.code
        val source = osmCacheSource(response.cacheResponse != null, networkStatus)
        if (isOsmTile) {
            Log.d(
                "MeteoCompare/OSMCache",
                "$source tile=${request.url.encodedPath.removePrefix("/")} " +
                    "status=${response.code} networkStatus=${networkStatus ?: "-"}"
            )
        }
        if (radarFrameId != null) {
            // Frame token only: do not log latitude/longitude from the radar URL.
            Log.d(
                "MeteoCompare/RadarCache",
                "$source frame=$radarFrameId status=${response.code} networkStatus=${networkStatus ?: "-"}"
            )
        }
        return response
    }
}

private val radarFrameTokenPattern = Regex("[A-Za-z0-9_-]+")

/** RainViewer frame identifier for debug logging; excludes geographic coordinates. */
internal fun radarFrameIdForDiagnostics(host: String, segments: List<String>): String? =
    segments.getOrNull(2)?.takeIf { frame ->
        host.endsWith(".rainviewer.com", ignoreCase = true) &&
            segments.getOrNull(0) == "v2" && segments.getOrNull(1) == "radar" &&
            segments.lastOrNull()?.endsWith(".png") == true &&
            frame.matches(radarFrameTokenPattern)
    }

/** Séparé du réseau Android pour tester sans serveur ni disque. */
internal fun osmCacheSource(hasCacheResponse: Boolean, networkStatus: Int?): String = when {
    hasCacheResponse && networkStatus == 304 -> "REVALIDATED_304"
    hasCacheResponse && networkStatus != null -> "REVALIDATED"
    hasCacheResponse -> "DISK_HIT"
    networkStatus != null -> "NETWORK"
    else -> "UNKNOWN"
}
