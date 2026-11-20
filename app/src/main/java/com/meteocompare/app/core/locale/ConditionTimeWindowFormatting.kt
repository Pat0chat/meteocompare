package com.meteocompare.app.core.locale

import com.meteocompare.app.domain.util.ConditionTimeWindow
import com.meteocompare.app.domain.util.ConditionWindowKind
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** UTC instants are formatted in the destination city's zone. A missing city zone
 * is explicitly shown as UTC rather than silently using the device's timezone.
 * Midnight crossings and changes of DST offset are always displayed unambiguously.
 */
internal fun formatConditionTimeWindow(
    window: ConditionTimeWindow,
    locale: Locale,
    clock: DateTimeFormatter
): String {
    val zone = window.zone ?: ZoneOffset.UTC
    val start = window.start.atZone(zone)
    val end = window.endExclusive.atZone(zone)
    val date = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    val timeLabel = when {
        window.kind == ConditionWindowKind.HOURLY ||
            window.kind == ConditionWindowKind.HOURLY_MIXED ->
            "${start.format(date)} · ${start.format(clock)}"
        start.toLocalDate() == end.toLocalDate() ->
            "${start.format(date)} · ${start.format(clock)} – ${end.format(clock)}"
        else -> "${start.format(date)} ${start.format(clock)} → ${end.format(date)} ${end.format(clock)}"
    }
    val zoneLabel = if (window.zone == null) "UTC" else {
        val firstOffset = start.offset.id.let { if (it == "Z") "+00:00" else it }
        val lastOffset = end.offset.id.let { if (it == "Z") "+00:00" else it }
        if (window.kind != ConditionWindowKind.HOURLY &&
            window.kind != ConditionWindowKind.HOURLY_MIXED && firstOffset != lastOffset)
            "UTC$firstOffset → UTC$lastOffset"
        else start.format(DateTimeFormatter.ofPattern("z", locale))
    }
    return "$timeLabel · $zoneLabel"
}
