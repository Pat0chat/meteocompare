package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronoTimelineTemperatureTest {

    @Test
    fun daily_temperature_series_keep_distinct_min_and_max_curves() {
        val points = listOf(
            SimplifiedTimelinePoint(tempMinC = 8.0, tempMaxC = 17.0),
            SimplifiedTimelinePoint(tempMinC = 10.0, tempMaxC = 21.0),
            SimplifiedTimelinePoint(tempMinC = null, tempMaxC = 19.0)
        )

        val (mins, maxs) = chronoDailyTemperatureSeries(points)

        assertEquals(listOf(8.0, 10.0, null), mins)
        assertEquals(listOf(17.0, 21.0, 19.0), maxs)
    }
    @Test
    fun daily_temperature_labels_stay_on_opposite_sides_of_their_curves() {
        val domainMin = 8.0
        val domainMax = 20.0

        val highOffset = chronoDailyHighTemperatureLabelOffset(20.0, domainMin, domainMax)
        val lowOffset = chronoDailyLowTemperatureLabelOffset(8.0, domainMin, domainMax)

        assertEquals(7.dp, highOffset)
        assertEquals(99.dp, lowOffset)
    }

    @Test
    fun daily_temperature_curves_use_the_same_heatmap_palette_as_hourly() {
        val fallback = Color.Magenta
        val values = listOf(-3.0, 8.0, 24.0, null)

        val colors = chronoTemperatureHeatmapColors(values, fallback)

        assertEquals(temperatureHeatmapColor(-3.0), colors[0])
        assertEquals(temperatureHeatmapColor(8.0), colors[1])
        assertEquals(temperatureHeatmapColor(24.0), colors[2])
        assertEquals(fallback, colors[3])
    }

    @Test
    fun hourly_apparent_temperature_keeps_gaps_and_daily_mode_does_not_invent_values() {
        val points = listOf(
            SimplifiedTimelinePoint(temperatureC = 21.0, apparentTemperatureC = 16.0),
            SimplifiedTimelinePoint(temperatureC = 22.0, apparentTemperatureC = null),
            SimplifiedTimelinePoint(temperatureC = 23.0, apparentTemperatureC = Double.NaN),
            SimplifiedTimelinePoint(temperatureC = 24.0, apparentTemperatureC = 31.0)
        )
        assertEquals(listOf(16.0, null, null, 31.0), chronoApparentTemperatureSeries(points, DisplayMode.HOURLY))
        assertTrue(chronoApparentTemperatureSeries(points, DisplayMode.DAILY).isEmpty())
    }

    @Test
    fun hourly_chart_domain_includes_felt_temperature_but_daily_range_is_unchanged() {
        val hourly = chronoTemperaturePlotBounds(
            temperatures = listOf(20.0, 21.0),
            apparent = listOf(12.0, 31.0),
            minimums = emptyList(), maximums = emptyList(), mode = DisplayMode.HOURLY
        )
        assertTrue(hourly.min <= 12.0)
        assertTrue(hourly.max >= 31.0)
        val daily = chronoTemperaturePlotBounds(
            temperatures = listOf(20.0), apparent = listOf(-40.0),
            minimums = listOf(8.0), maximums = listOf(23.0), mode = DisplayMode.DAILY
        )
        assertTrue(daily.min > -40.0)
        assertTrue(daily.max >= 23.0)
    }

    @Test
    fun temperature_curve_geometry_matches_hourly_design() {
        assertEquals(3.dp, CHRONO_TEMP_LINE_WIDTH)
        assertEquals(5.2.dp, CHRONO_TEMP_POINT_HALO_RADIUS)
        assertEquals(3.4.dp, CHRONO_TEMP_POINT_RADIUS)
    }

}
