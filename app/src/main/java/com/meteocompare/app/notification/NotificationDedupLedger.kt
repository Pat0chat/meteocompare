package com.meteocompare.app.notification

import android.content.Context
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant

/**
 * Registre des notifications déjà présentées, pour qu'un même événement
 * (même ville, même jour cible, même nature) ne soit notifié qu'une fois,
 * même si le worker s'exécute plusieurs fois ou est relancé par WorkManager.
 *
 * Chaque entrée est `"<dedupKey>@<epochMillis>"`. Les entrées plus anciennes
 * que [RETENTION] sont purgées à chaque écriture : les clés portent une date
 * cible proche, elles deviennent donc inutiles au bout de quelques jours.
 *
 * La logique est pure (ensembles immuables) pour être testée sans Android ;
 * [NotificationDedupStore] ne fait que la persister.
 */
internal object NotificationDedupLedger {

    val RETENTION: Duration = Duration.ofDays(4)
    private const val SEPARATOR = '@'

    fun contains(entries: Set<String>, key: String): Boolean =
        entries.any { entry -> keyOf(entry) == key }

    fun record(entries: Set<String>, key: String, now: Instant): Set<String> =
        prune(entries, now)
            .filterNot { entry -> keyOf(entry) == key }
            .toSet() + "$key$SEPARATOR${now.toEpochMilli()}"

    fun prune(entries: Set<String>, now: Instant): Set<String> {
        val cutoff = now.minus(RETENTION).toEpochMilli()
        return entries.filterTo(LinkedHashSet()) { entry ->
            val recordedAt = entry.substringAfterLast(SEPARATOR, missingDelimiterValue = "")
                .toLongOrNull()
            recordedAt != null && recordedAt >= cutoff
        }
    }

    private fun keyOf(entry: String): String = entry.substringBeforeLast(SEPARATOR)
}

/** Persistance SharedPreferences du [NotificationDedupLedger]. */
internal class NotificationDedupStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun alreadyNotified(key: String): Boolean =
        NotificationDedupLedger.contains(entries(), key)

    fun markNotified(key: String, now: Instant) {
        prefs.edit {
            putStringSet(ENTRIES_KEY, NotificationDedupLedger.record(entries(), key, now))
        }
    }

    private fun entries(): Set<String> = prefs.getStringSet(ENTRIES_KEY, null).orEmpty()

    private companion object {
        const val PREFS_NAME = "meteocompare_notifications"
        const val ENTRIES_KEY = "notified_events"
    }
}
