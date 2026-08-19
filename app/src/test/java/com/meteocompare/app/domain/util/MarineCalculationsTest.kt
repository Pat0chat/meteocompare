package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.MarineDaily
import com.meteocompare.app.domain.model.MarineForecast
import com.meteocompare.app.domain.model.MarineGrid
import com.meteocompare.app.domain.model.MarineHourly
import com.meteocompare.app.domain.model.TideEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineCalculationsTest {
    private val base = 1_800_000_000_000L

    @Test
    fun `detecte les pleines et basses mers sans exiger une journee complete`() {
        val forecast = marine(
            values = listOf(0.0, 0.8, 0.2, -0.6, -0.1, 0.9, 0.3),
            start = base - 3_600_000L
        )
        val events = forecast.detectTideEvents(hours = 12, minGapHours = 1, nowEpochMs = base)
        assertEquals(3, events.size)
        assertEquals(TideEventType.HIGH, events[0].type)
        assertEquals(TideEventType.LOW, events[1].type)
        assertEquals(TideEventType.HIGH, events[2].type)
    }

    @Test
    fun `marnage 24h ignore les points manquants`() {
        val forecast = marine(
            values = listOf(null, -0.4, 0.1, 0.7, null, -0.2),
            start = base
        )
        val range = forecast.tideRangeNext24h(base)
        requireNotNull(range)
        assertEquals(-0.4, range.min, 0.0001)
        assertEquals(0.7, range.max, 0.0001)
        assertEquals(1.1, range.range, 0.0001)
    }

    @Test
    fun `index marin le plus proche utilise les epochs et non le fuseau du telephone`() {
        val forecast = marine(values = listOf(0.0, 1.0, 2.0), start = base)
        assertEquals(1, forecast.nearestMarineIndex(base + 3_500_000L))
    }

    private fun marine(values: List<Double?>, start: Long): MarineForecast {
        val epochs = values.indices.map { start + it * 3_600_000L }
        return MarineForecast(
            fetchedAtEpochMs = base,
            timezone = "Europe/Paris",
            grid = MarineGrid(43.0, 5.0, 2.0),
            hourly = MarineHourly(
                timestamps = values.indices.map { "2027-01-15T%02d:00".format(it) },
                timestampEpochMs = epochs,
                waveHeight = values.map { 1.0 },
                seaLevelHeightMsl = values
            ),
            daily = MarineDaily(),
            usablePoints = values.size,
            coastal = true
        )
    }
}
