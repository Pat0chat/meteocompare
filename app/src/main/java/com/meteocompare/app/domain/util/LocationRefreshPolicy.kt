package com.meteocompare.app.domain.util

/** No GPS poller: decisions are evaluated only on foreground or explicit user actions. */
object LocationRefreshPolicy {
    const val FOREGROUND_INTERVAL_MS = 30 * 60_000L
    const val AUTO_RETRY_INTERVAL_MS = 2 * 60_000L
    const val TAP_DEBOUNCE_MS = 15_000L

    fun shouldAcquire(
        nowMs: Long,
        lastSuccessfulMs: Long,
        lastAttemptMs: Long,
        explicit: Boolean
    ): Boolean {
        // Monotonicity is not guaranteed across clock changes: negative elapsed
        // values should never be interpreted as "recent".
        val sinceAttempt = nowMs - lastAttemptMs
        if (lastAttemptMs > 0 && sinceAttempt >= 0 &&
            sinceAttempt < if (explicit) TAP_DEBOUNCE_MS else AUTO_RETRY_INTERVAL_MS
        ) return false
        if (explicit) return true
        val sinceFix = nowMs - lastSuccessfulMs
        return lastSuccessfulMs <= 0 || sinceFix < 0 || sinceFix >= FOREGROUND_INTERVAL_MS
    }
}
