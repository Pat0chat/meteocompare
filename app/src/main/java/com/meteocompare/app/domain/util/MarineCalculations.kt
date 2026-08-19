package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.TideEvent
import com.meteocompare.app.domain.model.TideEventType
import com.meteocompare.app.domain.model.TideRange
import kotlin.math.abs

fun MarineForecast.nearestMarineIndex(nowEpochMs: Long = System.currentTimeMillis()): Int {
    if (hourly.timestampEpochMs.isEmpty()) return -1
    var best = -1
    var delta = Long.MAX_VALUE
    hourly.timestampEpochMs.forEachIndexed { index, ms ->
        if (ms != null) {
            val d = abs(ms - nowEpochMs)
            if (d < delta) {
                delta = d
                best = index
            }
        }
    }
    return best
}

fun MarineForecast.detectTideEvents(
    hours: Int = 72,
    minGapHours: Int = 3,
    nowEpochMs: Long = System.currentTimeMillis()
): List<TideEvent> {
    val epochs = hourly.timestampEpochMs
    val values = hourly.seaLevelHeightMsl
    val count = minOf(hourly.timestamps.size, epochs.size, values.size)
    if (count < 3) return emptyList()
    val end = nowEpochMs + hours * 3_600_000L
    val candidates = mutableListOf<TideEvent>()
    for (i in 1 until count - 1) {
        val ms = epochs[i] ?: continue
        if (ms < nowEpochMs - 3_600_000L || ms > end) continue
        val previous = values[i - 1] ?: continue
        val current = values[i] ?: continue
        val next = values[i + 1] ?: continue
        val high = current >= previous && current > next
        val low = current <= previous && current < next
        if (high || low) {
            candidates += TideEvent(
                timestamp = hourly.timestamps[i],
                epochMs = ms,
                value = current,
                type = if (high) TideEventType.HIGH else TideEventType.LOW
            )
        }
    }

    val out = mutableListOf<TideEvent>()
    val gap = minGapHours * 3_600_000L
    for (event in candidates) {
        val previous = out.lastOrNull()
        if (previous != null && event.type == previous.type && event.epochMs - previous.epochMs < gap) {
            val better = if (event.type == TideEventType.HIGH) event.value > previous.value else event.value < previous.value
            if (better) out[out.lastIndex] = event
        } else {
            out += event
        }
    }
    return out
}

fun MarineForecast.tideRangeNext24h(nowEpochMs: Long = System.currentTimeMillis()): TideRange? {
    val values = hourly.seaLevelHeightMsl
    val epochs = hourly.timestampEpochMs
    val count = minOf(values.size, epochs.size)
    val selected = buildList {
        for (i in 0 until count) {
            val ms = epochs[i] ?: continue
            val value = values[i] ?: continue
            if (ms >= nowEpochMs && ms < nowEpochMs + 24 * 3_600_000L) add(value)
        }
    }
    if (selected.isEmpty()) return null
    val min = selected.min()
    val max = selected.max()
    return TideRange(min = min, max = max, range = max - min)
}
