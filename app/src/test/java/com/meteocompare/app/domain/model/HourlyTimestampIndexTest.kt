package com.meteocompare.app.domain.model

import java.time.Instant
import java.util.LinkedList
import java.util.RandomAccess
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyTimestampIndexTest {
    private val origin = Instant.parse("2026-10-25T00:30:00Z")
    private fun series(times: List<Instant>) = HourlyForecast(times, emptyList(), emptyList(), emptyList())

    @Test fun `lookup agrees with linear search on regular irregular and unsorted snapshots`() {
        val random = Random(54)
        val regular = List(288) { origin.plusSeconds(it * 3600L) }
        val irregular = regular.filterIndexed { i, _ -> i % 4 != 0 }
        val duplicates = (irregular + irregular.take(25)).sorted()
        val inputs = listOf(emptyList(), regular.take(1), regular, irregular, duplicates,
            duplicates.shuffled(random), LinkedList(duplicates))
        for (input in inputs) {
            val forecast = series(input)
            for (hour in -2..290) {
                for (offset in listOf(0L, 30L)) {
                    val target = origin.plusSeconds(hour * 3600L + offset)
                    val expected = input.indexOf(target)
                    assertEquals(expected, forecast.indexOfTimestamp(target))
                    assertEquals(expected.takeIf { it >= 0 }, forecast.exactIndex(target))
                }
            }
        }
    }

    @Test fun `copy recomputes ordering for new timestamps`() {
        val initial = series(listOf(origin, origin.plusSeconds(3600)))
        assertEquals(0, initial.indexOfTimestamp(origin))
        val updated = initial.copy(timestamps = initial.timestamps.reversed())
        assertEquals(1, updated.indexOfTimestamp(origin))
        assertEquals(0, initial.indexOfTimestamp(origin))
    }

    @Test fun `after first validation ordered lookup uses logarithmic reads`() {
        val times = object : AbstractList<Instant>(), RandomAccess {
            override val size = 360
            var reads = 0
            override fun get(index: Int): Instant {
                reads++
                return origin.plusSeconds(index * 3600L)
            }
        }
        val forecast = series(times)
        assertEquals(359, forecast.indexOfTimestamp(origin.plusSeconds(359 * 3600L)))
        times.reads = 0
        repeat(100) {
            assertEquals(359, forecast.indexOfTimestamp(origin.plusSeconds(359 * 3600L)))
        }
        assertTrue("Expected at most 10 reads per lookup, got ${times.reads}", times.reads <= 1000)
    }
}
