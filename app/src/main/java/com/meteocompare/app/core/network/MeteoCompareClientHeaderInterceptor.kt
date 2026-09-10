package com.meteocompare.app.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal const val METEOCOMPARE_CLIENT_HEADER_NAME = "X-MeteoCompare-Client"
internal const val METEOCOMPARE_CLIENT_HEADER_VALUE = "android"

/** Identifie systématiquement auprès du Worker les requêtes émises par Android. */
internal class MeteoCompareClientHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().withMeteoCompareClientHeader())
}

/**
 * [Request.Builder.header] remplace une éventuelle valeur existante : le Worker
 * reçoit donc une seule identité client, stable et non ambiguë.
 */
internal fun Request.withMeteoCompareClientHeader(): Request = newBuilder()
    .header(METEOCOMPARE_CLIENT_HEADER_NAME, METEOCOMPARE_CLIENT_HEADER_VALUE)
    .build()
