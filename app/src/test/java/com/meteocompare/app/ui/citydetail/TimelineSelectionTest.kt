package com.meteocompare.app.ui.citydetail

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineSelectionTest {

    @Test
    fun `hourly timeline exposes every hour of the 24 hour window`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = List(24) { index ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(index * 3_600L))
        }

        val selected = selectRegularTimelinePoints(points)

        assertEquals(24, selected.size)
        assertEquals((0L..23L).toList(), selected.map {
            (it.instant!!.epochSecond - start.epochSecond) / 3_600L
        })
        selected.forEachIndexed { index, point -> assertSame(points[index], point) }
    }

    @Test
    fun `an insight target keeps its exact hourly card in the displayed timeline`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = List(24) { index ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(index * 3_600L))
        }
        val target = points[13]

        val selected = selectRegularTimelinePoints(points)

        assertSame(target, selected[13])
    }

    @Test
    fun `missing exact hourly slot produces an empty regular placeholder`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = (0L..23L)
            .filterNot { it == 9L }
            .map { hour -> SimplifiedTimelinePoint(instant = start.plusSeconds(hour * 3_600L)) }

        val selected = selectRegularTimelinePoints(points)

        assertEquals(24, selected.size)
        assertEquals((0L..23L).toList(), selected.map {
            (it.instant!!.epochSecond - start.epochSecond) / 3_600L
        })
        assertEquals(null, selected[9].temperatureC)
        assertTrue(selected[9] !in points)
    }

    @Test
    fun `daily timeline remains chronological and capped`() {
        val points = (0..9).map { day ->
            SimplifiedTimelinePoint(date = java.time.LocalDate.of(2026, 7, 23).plusDays(day.toLong()))
        }

        assertEquals(points.take(8), selectRegularTimelinePoints(points))
    }
}
