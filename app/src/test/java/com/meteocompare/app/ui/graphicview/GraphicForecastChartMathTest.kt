package com.meteocompare.app.ui.graphicview

import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.domain.model.ClockFormatPreference
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicForecastChartMathTest {
    @Test
    fun chart_plots_are_compact_but_keep_room_for_tooltips_and_axis_ticks() {
        assertTrue(TemperaturePlotHeight.value in 180f..200f)
        assertTrue(RainPlotHeight.value in 120f..140f)
        assertTrue(WindPlotHeight.value in 120f..140f)
        assertTrue(PressurePlotHeight.value in 120f..140f)
    }

    @Test
    fun apparentTemperatureRendersOnSameAxisWithMissingGaps() {
        val points = listOf(
            SimplifiedTimelinePoint(temperatureC = 20.0, apparentTemperatureC = 17.0),
            SimplifiedTimelinePoint(temperatureC = 21.0, apparentTemperatureC = null),
            SimplifiedTimelinePoint(temperatureC = 22.0, apparentTemperatureC = 30.0)
        )
        val data = buildGraphicRenderData(points, emptyMap(), ZoneId.of("UTC"))
        assertEquals(listOf(17.0, null, 30.0), data.apparentTemperatures)
        val domain = temperatureDomain(points)
        assertTrue(domain.min < 17.0)
        assertTrue(domain.max > 30.0)
    }

    @Test
    fun invalid_values_leave_gaps_without_mutating_canonical_points_or_chronology() {
        val start = Instant.parse("2026-09-16T00:00:00Z")
        val points = listOf(Double.NaN, Double.MAX_VALUE, -5.0, 20.0).mapIndexed { i, v ->
            SimplifiedTimelinePoint(instant = start.plusSeconds(i * 3600L), temperatureC = v,
                precipitationMm = if (i == 3) 0.05 else Double.NaN,
                windKmh = v, windGustKmh = v)
        }
        val data = buildGraphicRenderData(points, emptyMap(), ZoneId.of("UTC"))
        assertEquals(listOf(null, null, -5.0, 20.0), data.temperatures)
        assertEquals(listOf(0, 1, 2, 3), data.hours)
        assertEquals(listOf(2, 3), seriesMarkerIndices(data.gusts))
        assertEquals(0.05, data.rainAmounts[3]!!, 0.0)
        assertTrue(points[0].temperatureC!!.isNaN())
        assertEquals(Double.MAX_VALUE, points[1].temperatureC!!, 0.0)
        val domain = temperatureDomain(points)
        assertTrue(domain.min.isFinite() && domain.max.isFinite() && domain.max > domain.min)
    }


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

    @Test
    fun viewport_range_only_keeps_visible_hours_plus_small_overscan() {
        assertEquals(GraphicViewportRange(0, 11), graphicViewportRange(0, 360, 40f, 240))
        assertEquals(GraphicViewportRange(98, 111), graphicViewportRange(4_000, 360, 40f, 240))
        assertEquals(GraphicViewportRange(228, 239), graphicViewportRange(9_200, 360, 40f, 240))
    }

    @Test
    fun `time axis labels are built once and preserve local DST transitions`() {
        // In Paris the local clock jumps from 01:00 to 03:00 on 29 March 2026.
        val zone = ZoneId.of("Europe/Paris")
        val points = listOf(
            "2026-03-28T23:00:00Z",
            "2026-03-29T00:00:00Z",
            "2026-03-29T01:00:00Z",
            "2026-03-29T02:00:00Z"
        ).map { SimplifiedTimelinePoint(instant = Instant.parse(it)) }
        val labels = buildGraphicTimeAxisLabels(points, zone, Locale.FRANCE)

        assertEquals(listOf("00h", "01h", "03h", "04h"), labels.hourLabels)
        assertEquals(listOf(0, 1, 3, 4), labels.hours)
        assertEquals(listOf(GraphicDaySpan(0, 3, LocalDate.of(2026, 3, 29), 0)), labels.daySpans)
        assertTrue(labels.dayLabels[LocalDate.of(2026, 3, 29)]!!.isNotBlank())
    }

    @Test
    fun `english chart hour labels display am pm in the city timezone`() {
        val points = listOf(
            "2026-10-13T00:00:00Z",
            "2026-10-13T12:00:00Z",
            "2026-10-13T23:00:00Z"
        ).map { SimplifiedTimelinePoint(instant = Instant.parse(it)) }
        val labels = buildGraphicTimeAxisLabels(points, ZoneId.of("UTC"), Locale.UK)
        assertEquals(listOf("12 AM", "12 PM", "11 PM"), labels.hourLabels)
        assertEquals(listOf(0, 12, 23), labels.hours)
    }

    @Test
    fun `chart hour labels obey explicit clock choice regardless of language`() {
        val points = listOf(
            "2026-10-13T00:00:00Z",
            "2026-10-13T12:00:00Z",
            "2026-10-13T23:00:00Z"
        ).map { SimplifiedTimelinePoint(instant = Instant.parse(it)) }
        val zone = ZoneId.of("UTC")
        val languages = listOf(Locale.FRANCE, Locale.UK, Locale.GERMANY,
            Locale.ITALY, Locale.forLanguageTag("es-ES"))

        for (language in languages) {
            assertEquals(
                listOf("12 AM", "12 PM", "11 PM"),
                buildGraphicTimeAxisLabels(points, zone, language,
                    ClockFormatPreference.HOUR_12).hourLabels
            )
            assertEquals(
                listOf("00h", "12h", "23h"),
                buildGraphicTimeAxisLabels(points, zone, language,
                    ClockFormatPreference.HOUR_24).hourLabels
            )
        }
    }

    @Test
    fun day_spans_keep_global_indices_for_virtualized_timeline() {
        val start = LocalDate.of(2026, 9, 16)
        val dates = List(48) { index -> start.plusDays((index / 24).toLong()) }
        assertEquals(
            listOf(
                GraphicDaySpan(0, 23, start, 0),
                GraphicDaySpan(24, 47, start.plusDays(1), 1)
            ),
            graphicDaySpans(dates)
        )
    }


    @Test
    fun pressure_curve_preserves_missing_hours_and_uses_nonzero_axis() {
        val points = listOf(
            SimplifiedTimelinePoint(pressureHpa = 999.0),
            SimplifiedTimelinePoint(pressureHpa = null),
            SimplifiedTimelinePoint(pressureHpa = 1008.0)
        )
        val data = buildGraphicRenderData(points, emptyMap(), ZoneId.of("UTC"))
        assertEquals(listOf(999.0, null, 1008.0), data.pressures)
        val domain = pressureDomain(points)
        assertTrue(domain.min < 999.0)
        assertTrue(domain.max > 1008.0)
        assertTrue(domain.min > 800.0)
    }

    @Test
    fun moistureCurvesKeepMissingHoursAndShareTheExistingAxes() {
        val points = listOf(
            SimplifiedTimelinePoint(temperatureC = 25.0, humidityPercent = 70, dewPointC = 8.0),
            SimplifiedTimelinePoint(temperatureC = 26.0, humidityPercent = null, dewPointC = null),
            SimplifiedTimelinePoint(temperatureC = 27.0, humidityPercent = 65, dewPointC = 10.0)
        )
        val data = buildGraphicRenderData(points, emptyMap(), ZoneId.of("UTC"))
        assertEquals(listOf(70.0, null, 65.0), data.humidities)
        assertEquals(listOf(8.0, null, 10.0), data.dewPoints)
        val domain = temperatureDomain(points)
        assertTrue(domain.min < 8.0)
        assertTrue(domain.max > 27.0)
        // Humidity uses its own 0–100 % domain superimposed on rain, without
        // inflating the precipitation axis or creating a separate panel.
        assertTrue(RainPlotHeight.value in 120f..140f)
    }

}
