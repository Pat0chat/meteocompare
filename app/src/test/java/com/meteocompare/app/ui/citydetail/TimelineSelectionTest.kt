package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineSelectionTest {

    @Test
    fun `event selection keeps rain transitions wind peak temperature extrema and disagreement`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = List(24) { index ->
            SimplifiedTimelinePoint(
                instant = start.plusSeconds(index * 3600L),
                temperatureC = when (index) {
                    5 -> 11.0
                    14 -> 31.0
                    else -> 18.0 + index / 3.0
                },
                precipitationPercent = if (index in 7..10) 75 else 10,
                precipitationSource = PrecipitationSignalSource.MODEL_PROBABILITY,
                precipitationModelCount = 3,
                windKmh = if (index == 16) 55.0 else 12.0,
                condition = if (index in 7..10) WeatherCondition.RAIN else WeatherCondition.CLEAR,
                modelCount = 3,
                temperatureModelCount = 3,
                windModelCount = 3,
                hasMultiModelEvidence = true,
                divergenceReasons = if (index == 9) {
                    setOf(DivergenceReason.PRECIPITATION)
                } else {
                    emptySet()
                }
            )
        }

        val selected = selectTimelinePoints(points)

        assertTrue(selected.size <= 8)
        assertTrue(points[7] in selected) // début de pluie
        assertTrue(points[11] in selected) // fin de pluie
        assertTrue(points[16] in selected) // pic de vent
        assertTrue(points[5] in selected) // minimum de température
        assertTrue(points[14] in selected) // maximum de température
        assertTrue(points[9] in selected) // désaccord
        assertEquals(points.first(), selected.first())
        assertEquals(points.last(), selected.last())
    }

    @Test
    fun `required insight point is retained even when regular sampling would skip it`() {
        val start = Instant.parse("2026-07-23T10:00:00Z")
        val points = List(24) { index ->
            SimplifiedTimelinePoint(
                instant = start.plusSeconds(index * 3600L),
                temperatureC = 20.0,
                modelCount = 3,
                temperatureModelCount = 3,
                hasMultiModelEvidence = true
            )
        }
        val required = points[13]

        val selected = selectTimelinePoints(points, requiredPoints = listOf(required))

        assertTrue(required in selected)
        assertTrue(selected.size <= 8)
    }
}
