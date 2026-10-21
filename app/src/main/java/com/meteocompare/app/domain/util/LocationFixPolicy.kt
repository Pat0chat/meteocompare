package com.meteocompare.app.domain.util

/** One-shot foreground acquisition only; raw fixes must never be persisted. */
object LocationFixPolicy {
    // A recent platform fix (including one produced by Maps) is safe to reuse.
    const val CACHED_FIX_MAX_AGE_MS = 3 * 60_000L
    const val EXPLICIT_TIMEOUT_MS = 30_000L
    const val AUTOMATIC_TIMEOUT_MS = 12_000L
    const val SAME_CELL_REUSE_WINDOW_MS = 15_000L

    fun isRecentCachedFix(nowElapsedNanos: Long, fixElapsedNanos: Long): Boolean {
        if (fixElapsedNanos <= 0L || nowElapsedNanos < fixElapsedNanos) return false
        return (nowElapsedNanos - fixElapsedNanos) / 1_000_000L <= CACHED_FIX_MAX_AGE_MS
    }

    /** A widget tap just after a successful foreground fix needn't reacquire the location. */
    fun canReuseFreshSuccess(nowMs: Long, lastSuccessfulMs: Long): Boolean =
        lastSuccessfulMs > 0L && nowMs >= lastSuccessfulMs &&
            nowMs - lastSuccessfulMs < SAME_CELL_REUSE_WINDOW_MS
}
