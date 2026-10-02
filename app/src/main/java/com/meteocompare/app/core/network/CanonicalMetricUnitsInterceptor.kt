package com.meteocompare.app.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Last network boundary: forecast, history and marine payloads must always be metric. */
class CanonicalMetricUnitsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(canonicalMetricRequest(chain.request()))
}

internal fun canonicalMetricRequest(request: Request): Request {
    val url = request.url
    val marine = url.host == "marine-api.open-meteo.com" && url.encodedPath == "/v1/marine"
    val weather = (url.host == "api.open-meteo.com" || url.host == "previous-runs-api.open-meteo.com") &&
        url.encodedPath == "/v1/forecast"
    val archive = url.host == "archive-api.open-meteo.com" && url.encodedPath == "/v1/archive"
    if (!marine && !weather && !archive) return request

    val canonical = url.newBuilder().setQueryParameter("temperature_unit", "celsius")
    if (marine) {
        canonical.setQueryParameter("length_unit", "metric")
    } else {
        canonical.setQueryParameter("wind_speed_unit", "kmh")
        canonical.setQueryParameter("precipitation_unit", "mm")
        // Remove the historical alias too, avoiding two contradictory speed options.
        canonical.removeAllQueryParameters("windspeed_unit")
    }
    return request.newBuilder().url(canonical.build()).build()
}
