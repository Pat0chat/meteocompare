package com.meteocompare.app.core.network

import android.os.SystemClock
import android.util.Log
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Garde-fou de diagnostic pour les émulateurs/appareils dont l'horloge système
 * dérive fortement de l'heure HTTP annoncée par Open-Meteo.
 *
 * Ce composant n'est installé que dans les builds DEBUG (voir NetworkModule).
 * Il ne modifie jamais la réponse, les timestamps météo ni l'horloge Android :
 * il se contente d'émettre un warning Logcat lorsqu'un écart > 30 min est
 * détecté. Un cooldown évite qu'un refresh multi-API ne répète le même warning.
 */
internal class OpenMeteoClockDebugInterceptor(
    private val deviceNowMillis: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val warningLogger: (String) -> Unit = { message -> Log.w(TAG, message) }
) : Interceptor {

    private val lastWarningElapsedMillis = AtomicLong(-WARNING_COOLDOWN_MS)

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val host = response.request.url.host
        if (!isOpenMeteoHost(host)) return response

        // Préférer le header de la réponse réseau réelle. Le fallback garde le
        // diagnostic fonctionnel si OkHttp ne renseigne pas networkResponse.
        val serverDateHeader = response.networkResponse?.header("Date")
            ?: response.header("Date")
            ?: return response

        val warning = buildClockSkewWarning(
            serverDateHeader = serverDateHeader,
            deviceNowMillis = deviceNowMillis()
        ) ?: return response

        if (shouldLogWarning()) {
            warningLogger(warning)
        }
        return response
    }

    private fun shouldLogWarning(): Boolean {
        val now = elapsedRealtimeMillis()
        while (true) {
            val previous = lastWarningElapsedMillis.get()
            if (now - previous < WARNING_COOLDOWN_MS) return false
            if (lastWarningElapsedMillis.compareAndSet(previous, now)) return true
        }
    }

    companion object {
        private const val TAG = "MeteoCompare/Clock"
        internal const val CLOCK_SKEW_THRESHOLD_MS = 30L * 60L * 1_000L
        private const val WARNING_COOLDOWN_MS = 5L * 60L * 1_000L

        internal fun isOpenMeteoHost(host: String): Boolean =
            host == "open-meteo.com" || host.endsWith(".open-meteo.com")

        /**
         * Retourne le message de diagnostic si l'écart dépasse le seuil.
         * `difference = device - server` : un appareil en retard est négatif.
         */
        internal fun buildClockSkewWarning(
            serverDateHeader: String,
            deviceNowMillis: Long
        ): String? {
            val serverInstant = parseHttpDate(serverDateHeader) ?: return null
            val differenceMillis = deviceNowMillis - serverInstant.toEpochMilli()
            if (abs(differenceMillis) <= CLOCK_SKEW_THRESHOLD_MS) return null

            return buildString {
                append("Device clock differs from server by ")
                append(formatSignedDuration(differenceMillis))
                append(".\nForecast presentation may be incorrect.")
            }
        }

        internal fun parseHttpDate(value: String): Instant? = try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }

        internal fun formatSignedDuration(durationMillis: Long): String {
            val sign = if (durationMillis < 0L) "-" else "+"
            val totalMinutes = abs(durationMillis) / 60_000L
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return when {
                hours > 0L && minutes > 0L -> "$sign${hours}h${minutes}m"
                hours > 0L -> "$sign${hours}h"
                else -> "$sign${minutes}m"
            }
        }
    }
}
