package com.meteocompare.app.ui.citydetail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meteocompare.app.ui.components.temperatureHeatmapColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronoTimelineTemperatureTest {

    @Test
    fun daily_temperature_labels_stay_on_opposite_sides_of_their_curves() {
        val domainMin = 8.0
        val domainMax = 20.0

        val highOffset = chronoDailyHighTemperatureLabelOffset(20.0, domainMin, domainMax)
        val lowOffset = chronoDailyLowTemperatureLabelOffset(8.0, domainMin, domainMax)

        assertEquals(17.dp, highOffset)
        assertEquals(103.dp, lowOffset)
        val withRange = chronoDailyHighTemperatureLabelOffset(20.0, domainMin, domainMax, withRange = true)
        assertEquals(2.dp, withRange)
        // With 2 labels above the maximum, their bottom stays above the curve (40 dp).
        assertTrue(withRange + 32.dp < 40.dp)
        assertTrue(lowOffset + 32.dp < chronoTemperatureRowHeight(DisplayMode.DAILY))
    }

    @Test
    fun daily_ranges_fit_into_compact_columns_and_do_not_widen_the_chrono_grid() {
        assertEquals(148.dp, chronoPointWidth(DisplayMode.DAILY))
        assertEquals(148.dp, chronoPointWidth(DisplayMode.DAILY, true))
        assertEquals(96.dp, chronoPointWidth(DisplayMode.HOURLY, true))
        assertEquals(112.dp, timelineColumnWidth(DisplayMode.DAILY, false))
        assertEquals(136.dp, timelineColumnWidth(DisplayMode.DAILY, true))
        assertEquals(112.dp, timelineColumnWidth(DisplayMode.HOURLY, true))
    }

    @Test
    fun compact_daily_ranges_add_less_than_one_small_row_to_the_chrono_grid() {
        val base = chronoRowHeights(DisplayMode.DAILY, false).sumOf { it.value.toDouble() }
        val expanded = chronoRowHeights(DisplayMode.DAILY, true).sumOf { it.value.toDouble() }
        assertTrue("Ranges should add less than 25 dp in total", expanded - base < 25.0)
        assertEquals(50.dp, chronoDailyApparentHeight(false))
        assertEquals(50.dp, chronoDailyApparentHeight(true))
        assertEquals(75.dp, chronoDailyApparentHeight(true, 1.5f))
        assertEquals(75.dp, chronoRowHeights(DisplayMode.DAILY, true, 1.5f)[2])
        assertEquals(
            chronoRowHeights(DisplayMode.HOURLY, false),
            chronoRowHeights(DisplayMode.HOURLY, true)
        )
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
