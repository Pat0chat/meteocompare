package com.meteocompare.app.domain.util

/** One-shot foreground acquisition only; raw fixes must never be persisted. */
object LocationFixPolicy {
    // A recent platform fix (including one produced by Maps) is safe to reuse.
    const val CACHED_FIX_MAX_AGE_MS = 3 * 60_000L
    // A manual refresh is a request for a NEW fix, not a 3-minute-old cell.
    const val EXPLICIT_CACHED_FIX_MAX_AGE_MS = 15_000L
    const val EXPLICIT_TIMEOUT_MS = 30_000L
    const val AUTOMATIC_TIMEOUT_MS = 12_000L
    const val SAME_CELL_REUSE_WINDOW_MS = 15_000L

    fun isRecentCachedFix(nowElapsedNanos: Long, fixElapsedNanos: Long, explicit: Boolean = false): Boolean {
        if (fixElapsedNanos <= 0L || nowElapsedNanos < fixElapsedNanos) return false
        val ageLimit = if (explicit) EXPLICIT_CACHED_FIX_MAX_AGE_MS else CACHED_FIX_MAX_AGE_MS
        return (nowElapsedNanos - fixElapsedNanos) / 1_000_000L <= ageLimit
    }

    /** Never write a fix after opt-out or after its original accuracy grant expired. */
    fun mayCommitFix(
        generationAtStart: Long,
        currentGeneration: Long,
        anyPermissionNow: Boolean,
        fineAtStart: Boolean,
        fineNow: Boolean
    ): Boolean = generationAtStart == currentGeneration && anyPermissionNow &&
        (!fineAtStart || fineNow)

    /** A backwards wall-clock change must not make an old grid cell look freshly updated. */
    fun isStoredCellStale(nowMs: Long, savedAtMs: Long): Boolean =
        savedAtMs <= 0L || savedAtMs > nowMs || nowMs - savedAtMs > 12 * 60 * 60_000L

    /** A widget tap just after a successful foreground fix needn't reacquire the location. */
    fun canReuseFreshSuccess(nowMs: Long, lastSuccessfulMs: Long): Boolean =
        lastSuccessfulMs > 0L && nowMs >= lastSuccessfulMs &&
            nowMs - lastSuccessfulMs < SAME_CELL_REUSE_WINDOW_MS
}
