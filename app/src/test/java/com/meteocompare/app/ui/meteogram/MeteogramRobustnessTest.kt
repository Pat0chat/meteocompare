package com.meteocompare.app.ui.meteogram

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.graphicview.GraphicForecastUiState
import com.meteocompare.app.ui.graphicview.GraphicModelValue
import com.meteocompare.app.ui.graphicview.GraphicSolarWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Edge cases in the cached forecast -> chart adapter, independent of network and Android UI. */
class MeteogramRobustnessTest {
    private val start = Instant.parse("2026-10-28T08:00:00Z")
    private val city = City("meteogram-test", "City", country = "France",
        latitude = 48.0, longitude = 2.0, timezone = "UTC")

    private fun state(
        points: List<SimplifiedTimelinePoint>,
        models: Map<Instant, List<GraphicModelValue>> = emptyMap(),
        solar: Map<LocalDate, GraphicSolarWindow> = emptyMap(),
        city: City = this.city
    ) = GraphicForecastUiState.Loaded(city, points, solar, models, calculatedAt = start)

    private fun model(name: String, temperature: Double? = null,
        low: Int? = null, mid: Int? = null, high: Int? = null,
        humidity: Int? = null, direction: Int? = null): GraphicModelValue =
        GraphicModelValue(name, temperature, null, null, null, null, direction,
            humidityPercent = humidity, cloudLowPercent = low,
            cloudMidPercent = mid, cloudHighPercent = high)

    @Test fun missing_null_timestamps_are_skipped_and_hours_sorted_once() {
        val points = listOf(
            SimplifiedTimelinePoint(instant = start.plusSeconds(7200), temperatureC = 14.0),
            SimplifiedTimelinePoint(instant = null, temperatureC = 98.0),
            SimplifiedTimelinePoint(instant = start, temperatureC = 10.0),
            SimplifiedTimelinePoint(instant = start, temperatureC = 999.0),
            SimplifiedTimelinePoint(instant = start.plusSeconds(3600), temperatureC = 12.0)
        )
        val result = buildMeteogramHours(state(points), null)
        assertEquals(listOf(start, start.plusSeconds(3600), start.plusSeconds(7200)),
            result.map { it.instant })
        assertEquals(listOf(10.0, 12.0, 14.0), result.map { it.temperatureC })
        assertEquals(2.0f, meteogramInstantSlot(result, start.plusSeconds(5400))!!, 0.0001f)
    }

    @Test fun empty_forecast_and_non_hourly_points_have_no_chart_hours() {
        assertTrue(buildMeteogramHours(state(emptyList()), null).isEmpty())
        assertTrue(buildMeteogramHours(state(listOf(SimplifiedTimelinePoint(date = LocalDate.of(2026, 10, 28)))), null).isEmpty())
    }

    @Test fun solar_boundaries_are_half_open_and_override_night_flag() {
        val sunrise = start.plusSeconds(3600)
        val sunset = start.plusSeconds(3 * 3600)
        val points = (0..4).map { SimplifiedTimelinePoint(
            instant = start.plusSeconds(it * 3600L), isNight = true
        ) }
        val result = buildMeteogramHours(state(points, solar = mapOf(
            LocalDate.of(2026, 10, 28) to GraphicSolarWindow(sunrise, sunset)
        )), null)
        assertEquals(listOf(false, true, true, false, false), result.map { it.daylight })
    }

    @Test fun incomplete_solar_window_falls_back_to_forecast_night_flag() {
        val points = listOf(
            SimplifiedTimelinePoint(instant = start, isNight = true),
            SimplifiedTimelinePoint(instant = start.plusSeconds(3600), isNight = false)
        )
        val result = buildMeteogramHours(state(points, solar = mapOf(
            LocalDate.of(2026, 10, 28) to GraphicSolarWindow(start, null)
        )), null)
        assertFalse(result.first().daylight)
        assertTrue(result.last().daylight)
    }

    @Test fun invalid_city_timezone_does_not_crash_or_shift_solar_day() {
        val malformed = city.copy(timezone = "not/a-real-timezone")
        val result = buildMeteogramHours(state(
            listOf(SimplifiedTimelinePoint(instant = start, isNight = true)), city = malformed
        ), null)
        assertFalse(result.single().daylight)
    }

    @Test fun median_cloud_layers_exclude_invalid_and_missing_model_values() {
        val points = listOf(SimplifiedTimelinePoint(instant = start, temperatureC = 22.0))
        val rows = listOf(
            model("A", low = 10, mid = 30, high = 75),
            model("B", low = 50, mid = 60, high = null),
            model("C", low = -5, mid = 101, high = 15),
            model("D", low = null, mid = null, high = 55)
        )
        val result = buildMeteogramHours(state(points, models = mapOf(start to rows)), null).single()
        assertEquals(30, result.cloudLowPercent)
        assertEquals(45, result.cloudMidPercent)
        assertEquals(55, result.cloudHighPercent)
        assertEquals(22.0, result.temperatureC!!, 0.0)
    }

    @Test fun model_selection_preserves_missing_data_instead_of_falling_back_to_consensus() {
        val point = SimplifiedTimelinePoint(instant = start, temperatureC = 25.0,
            humidityPercent = 77, windDirectionDeg = 300)
        val loaded = state(listOf(point), models = mapOf(start to listOf(
            model("One", temperature = 7.0), model("Two", temperature = null)
        )))
        assertEquals(25.0, buildMeteogramHours(loaded, null).single().temperatureC!!, 0.0)
        assertEquals(7.0, buildMeteogramHours(loaded, "One").single().temperatureC!!, 0.0)
        val selected = buildMeteogramHours(loaded, "Two").single()
        assertNull(selected.temperatureC)
        assertNull(selected.humidityPercent)
        assertNull(selected.windDirectionDeg)
        assertNull(buildMeteogramHours(loaded, "Removed").single().temperatureC)
    }

    @Test fun selected_model_rejects_out_of_range_cloud_humidity_and_direction() {
        val point = SimplifiedTimelinePoint(instant = start, humidityPercent = 180,
            windDirectionDeg = -90)
        val loaded = state(listOf(point), models = mapOf(start to listOf(
            model("A", low = -1, mid = 101, high = 49, humidity = -3, direction = 721)
        )))
        val consensus = buildMeteogramHours(loaded, null).single()
        assertNull(consensus.humidityPercent)
        assertNull(consensus.windDirectionDeg)
        val selected = buildMeteogramHours(loaded, "A").single()
        assertNull(selected.cloudLowPercent)
        assertNull(selected.cloudMidPercent)
        assertEquals(49, selected.cloudHighPercent)
        assertNull(selected.humidityPercent)
        assertNull(selected.windDirectionDeg)
    }

    @Test fun corrupted_cached_measurements_never_leak_into_charts() {
        val loaded = state(listOf(SimplifiedTimelinePoint(
            instant = start, temperatureC = Double.NaN,
            apparentTemperatureC = Double.POSITIVE_INFINITY,
            dewPointC = Double.NEGATIVE_INFINITY,
            precipitationMm = -0.1, windKmh = -8.0,
            windGustKmh = Double.NaN, pressureHpa = Double.NaN
        )))
        val result = buildMeteogramHours(loaded, null).single()
        assertNull(result.temperatureC)
        assertNull(result.apparentC)
        assertNull(result.dewC)
        assertNull(result.rainMm)
        assertNull(result.windKmh)
        assertNull(result.gustKmh)
        assertNull(result.pressureHpa)
        assertEquals(0.0, meteogramValidNonNegative(0.0)!!, 0.0)
        assertNull(meteogramValidMeasurement(Double.NaN))
        assertNull(meteogramValidNonNegative(-0.01))
    }

    @Test fun shared_scroll_uses_narrowest_chart_width_even_if_last_card_is_wider() {
        assertEquals(210, meteogramSharedViewportWidth(listOf(210, 245, 320)))
        assertEquals(210, meteogramSharedViewportWidth(listOf(320, 210, 245)))
        assertEquals(210, meteogramSharedViewportWidth(listOf(0, -1, 320, 210)))
        assertEquals(0, meteogramSharedViewportWidth(emptyList()))
    }

    @Test fun chart_domain_ignores_nan_infinities_and_unphysically_large_cache_numbers() {
        val range = meteogramDomain(sequenceOf(Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.MAX_VALUE, 1008.0, 1013.0), 3.0)
        assertTrue(range.min.isFinite())
        assertTrue(range.max.isFinite())
        assertTrue(range.span.isFinite())
        assertTrue(range.min <= 1008.0 && range.max >= 1013.0)
        assertTrue(range.fraction(1013.0) in 0f..1f)
        assertEquals(0.5f, MeteogramDomain(5.0, 5.0).fraction(5.0), 0.0f)
        assertEquals(0.5f, range.fraction(Double.NaN), 0.0f)
    }

    @Test fun domain_respects_physical_humidity_range_at_both_extremes() {
        val full = meteogramDomain(sequenceOf(0.0, 100.0), 15.0, bounds = 0.0..100.0)
        assertEquals(0.0, full.min, 0.0)
        assertEquals(100.0, full.max, 0.0)
        val saturated = meteogramDomain(sequenceOf(100.0), 15.0, bounds = 0.0..100.0)
        assertTrue(saturated.min < saturated.max)
        assertTrue(saturated.max <= 100.0)
    }

    @Test fun local_yesterday_matches_wall_clock_across_spring_dst() {
        val paris = ZoneId.of("Europe/Paris")
        val time = Instant.parse("2026-03-29T10:00:00Z") // 12:00 CEST
        assertEquals(Instant.parse("2026-03-28T11:00:00Z"),
            meteogramPreviousLocalDayInstant(time, paris)) // 12:00 CET
    }

    @Test fun local_yesterday_matches_wall_clock_across_autumn_dst() {
        val paris = ZoneId.of("Europe/Paris")
        val time = Instant.parse("2026-10-25T11:00:00Z") // 12:00 CET
        assertEquals(Instant.parse("2026-10-24T10:00:00Z"),
            meteogramPreviousLocalDayInstant(time, paris)) // 12:00 CEST
        assertEquals(time.minusSeconds(86400),
            meteogramPreviousLocalDayInstant(time, ZoneId.of("UTC")))
    }

    @Test fun missing_timestamps_and_sparse_horizon_clamp_solar_marker_slots() {
        val times = listOf(0L, 3L, 5L).map { MeteogramHour(start.plusSeconds(it * 3600), true) }
        assertEquals(1.0f, meteogramInstantSlot(times, start.plusSeconds(5400))!!, 0.0001f)
        assertEquals(3.5f, meteogramInstantSlot(times, start.plusSeconds(21600))!!, 0.0001f)
        assertNull(meteogramInstantSlot(times, start.plusSeconds(21601)))
        assertNull(meteogramInstantSlot(times, start.minusMillis(1)))
    }

    @Test fun percentages_cover_real_endpoints_and_drop_invalid_values() {
        assertEquals(0, meteogramValidPercent(0))
        assertEquals(100, meteogramValidPercent(100))
        assertNull(meteogramValidPercent(-1))
        assertNull(meteogramValidPercent(101))
        assertNull(meteogramValidPercent(null))
    }
}
