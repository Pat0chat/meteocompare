package com.meteocompare.app.ui.graphicview

import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicForecastChartMathTest {

    @Test
    fun line_markers_cover_every_available_hour_and_skip_missing_values() {
        assertEquals((0 until 240).toList(), seriesMarkerIndices(List(240) { it.toDouble() }))
        assertEquals(listOf(0, 2, 4), seriesMarkerIndices(listOf(1.0, null, 2.0, null, 3.0)))
    }

    @Test
    fun heatmap_layers_are_strong_enough_to_remain_visible_behind_series() {
        assertTrue(GRAPHIC_TEMPERATURE_HEAT_ALPHA >= 0.18f)
        assertTrue(GRAPHIC_RAIN_HEAT_ALPHA >= 0.18f)
        assertTrue(GRAPHIC_WIND_HEAT_ALPHA >= 0.18f)
        assertTrue(GRAPHIC_RAIN_HEAT_ALPHA >= GRAPHIC_TEMPERATURE_HEAT_ALPHA)
    }

    @Test
    fun touch_selection_maps_the_whole_chart_width_to_hour_indices() {
        assertEquals(0, indexForX(x = 0f, width = 2400f, count = 240))
        assertEquals(120, indexForX(x = 1205f, width = 2400f, count = 240))
        assertEquals(239, indexForX(x = 2399f, width = 2400f, count = 240))
        assertEquals(239, indexForX(x = 9_999f, width = 2400f, count = 240))
    }

    @Test
    fun daylight_flags_follow_sunrise_and_sunset_boundaries() {
        val date = LocalDate.of(2026, 9, 16)
        val zone = ZoneId.of("UTC")
        val sunrise = Instant.parse("2026-09-16T06:00:00Z")
        val sunset = Instant.parse("2026-09-16T18:00:00Z")
        val points = listOf(
            Instant.parse("2026-09-16T05:00:00Z"),
            sunrise,
            Instant.parse("2026-09-16T12:00:00Z"),
            sunset,
            Instant.parse("2026-09-16T19:00:00Z")
        ).map { SimplifiedTimelinePoint(instant = it) }

        assertEquals(
            listOf(false, true, true, false, false),
            daylightFlags(points, mapOf(date to GraphicSolarWindow(sunrise, sunset)), zone)
        )
    }

    @Test
    fun temperature_domain_keeps_all_model_extremes_with_padding() {
        val domain = temperatureDomain(
            listOf(
                SimplifiedTimelinePoint(
                    temperatureC = 10.0,
                    temperatureMinAcrossModels = 8.0,
                    temperatureMaxAcrossModels = 12.0
                ),
                SimplifiedTimelinePoint(
                    temperatureC = 25.0,
                    temperatureMinAcrossModels = 22.0,
                    temperatureMaxAcrossModels = 28.0
                )
            )
        )

        assertTrue(domain.min < 8.0)
        assertTrue(domain.max > 28.0)
        assertEquals(5, domain.ticks.size)
    }
    @Test
    fun adaptive_tooltip_position_uses_measured_width_and_stays_inside_track() {
        assertEquals(400, selectionBadgeStartPx(centerPx = 500f, trackWidthPx = 1_000f, badgeWidthPx = 200))
        assertEquals(0, selectionBadgeStartPx(centerPx = 50f, trackWidthPx = 1_000f, badgeWidthPx = 200))
        assertEquals(800, selectionBadgeStartPx(centerPx = 980f, trackWidthPx = 1_000f, badgeWidthPx = 200))
        assertEquals(500, selectionBadgeStartPx(centerPx = 500f, trackWidthPx = 1_000f, badgeWidthPx = 0))
    }

    @Test
    fun render_data_precomputes_long_timeline_series_once() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-09-16T00:00:00Z")
        val points = List(240) { index ->
            SimplifiedTimelinePoint(
                instant = start.plusSeconds(index * 3_600L),
                temperatureC = index.toDouble(),
                temperatureMinAcrossModels = index.toDouble() - 1.0,
                temperatureMaxAcrossModels = index.toDouble() + 1.0,
                precipitationMm = (index % 4).toDouble(),
                precipitationPercent = index % 100,
                windKmh = 10.0 + index % 8,
                windGustKmh = 20.0 + index % 10,
                windDirectionDeg = (index * 15) % 360
            )
        }

        val data = buildGraphicRenderData(points, emptyMap(), zone)

        assertEquals(240, data.temperatures.size)
        assertEquals(240, data.rainAmounts.size)
        assertEquals(240, data.winds.size)
        assertEquals(240, data.directions.size)
        assertEquals(0, data.hours.first())
        assertEquals(23, data.hours[23])
        assertTrue(data.daylight.all { it })
    }

}
