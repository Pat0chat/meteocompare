package com.meteocompare.app.data.worker

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Empêche le kickoff one-shot, reprogrammé à chaque création de process, de
 * refaire un cycle historique complet alors qu'un cycle vient de réussir.
 *
 * Le periodic quotidien reste la source de vérité. Cette petite préférence
 * n'est lue que depuis le thread du Worker, jamais sur le thread principal.
 */
internal object BiasRefreshRunGate {
    private const val PREFS_NAME = "meteocompare_background_refresh"
    private const val LAST_SUCCESS_KEY = "bias_last_success_at"

    internal val KICKOFF_MIN_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(20)
    internal val PERIODIC_MIN_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(6)
    internal val MANUAL_MIN_INTERVAL_MS: Long = TimeUnit.MINUTES.toMillis(30)

    fun shouldRun(
        context: Context,
        minIntervalMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val lastSuccess = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(LAST_SUCCESS_KEY, 0L)
        return shouldRun(lastSuccess, nowMs, minIntervalMs)
    }

    fun markSuccess(context: Context, nowMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_SUCCESS_KEY, nowMs)
            .apply()
    }

    internal fun shouldRun(
        lastSuccessAtMs: Long,
        nowMs: Long,
        minIntervalMs: Long
    ): Boolean {
        if (lastSuccessAtMs <= 0L || nowMs < lastSuccessAtMs) return true
        return nowMs - lastSuccessAtMs >= minIntervalMs
    }
}
