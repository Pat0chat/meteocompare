package com.meteocompare.app.ui.citydetail

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineSelectionTest {

    @Test
    fun `hourly timeline uses stable three hour intervals`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = List(24) { index ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(index * 3_600L))
        }

        val selected = selectRegularTimelinePoints(points)

        assertEquals(8, selected.size)
        assertEquals(
            listOf(0L, 3L, 6L, 9L, 12L, 15L, 18L, 21L),
            selected.map { (it.instant!!.epochSecond - start.epochSecond) / 3_600L }
        )
    }

    @Test
    fun `events no longer alter the regular grid`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = List(24) { index ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(index * 3_600L))
        }
        val eventPoint = points[13]

        val selected = selectRegularTimelinePoints(points)

        assertFalse(eventPoint in selected)
        assertEquals(points[12], selected[4])
        assertEquals(points[15], selected[5])
    }

    @Test
    fun `missing exact slot produces an empty regular placeholder`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = listOf(0L, 3L, 6L, 11L, 12L, 15L, 18L, 21L).map { hour ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(hour * 3_600L))
        }

        val selected = selectRegularTimelinePoints(points)

        assertTrue(points[3] !in selected)
        assertEquals(listOf(0L, 3L, 6L, 9L, 12L, 15L, 18L, 21L), selected.map {
            (it.instant!!.epochSecond - start.epochSecond) / 3_600L
        })
        assertEquals(null, selected[3].temperatureC)
    }

    @Test
    fun `daily timeline remains chronological and capped`() {
        val points = (0..9).map { day ->
            SimplifiedTimelinePoint(date = java.time.LocalDate.of(2026, 7, 23).plusDays(day.toLong()))
        }

        assertEquals(points.take(8), selectRegularTimelinePoints(points))
    }
}
