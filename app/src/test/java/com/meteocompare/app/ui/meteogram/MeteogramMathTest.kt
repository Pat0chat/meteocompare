package com.meteocompare.app.ui.meteogram

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.ui.citydetail.SimplifiedTimelinePoint
import com.meteocompare.app.ui.graphicview.GraphicForecastUiState
import com.meteocompare.app.ui.graphicview.GraphicModelValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteogramMathTest {
    @Test fun viewport_bounds_limit_work_to_visible_hours() {
        assertTrue(meteogramViewport(0f, 400f, 38f, 0).isEmpty())
        assertEquals(0, meteogramViewport(0f, 400f, 38f, 240).first)
        val range = meteogramViewport(1000f, 350f, 38f, 240)
        assertTrue(range.first >= 23 && range.last <= 38)
        assertEquals(239, meteogramViewport(9000f, 300f, 38f, 240).last)
    }

    @Test fun domains_ignore_missing_data_and_keep_precipitation_at_zero() {
        val temperature = meteogramDomain(sequenceOf(null, 14.0, 15.0, Double.NaN), 3.0)
        assertTrue(temperature.min < 14.0)
        assertTrue(temperature.max > 15.0)
        assertEquals(0.0, meteogramDomain(sequenceOf(null, 0.1, 1.5), 0.5, true).min, 0.0)
        val humidity = meteogramDomain(sequenceOf(70.0, 75.0, 80.0), 15.0, bounds = 0.0..100.0)
        assertTrue(humidity.min > 0.0 && humidity.max < 100.0)
        val empty = meteogramDomain(sequenceOf(null, Double.NaN), 4.0)
        assertEquals(0.0, empty.min, 0.0)
        assertEquals(4.0, empty.max, 0.0)
    }

    @Test fun overnight_range_respects_daylight_saving_and_missing_last_day() {
        val zone = ZoneId.of("Europe/Paris")
        val start = LocalDate.parse("2026-10-24").atStartOfDay(zone).toInstant()
        val end = LocalDate.parse("2026-10-27").atStartOfDay(zone).toInstant()
        val count = ((end.epochSecond - start.epochSecond) / 3600).toInt()
        val hours = List(count) { i ->
            MeteogramHour(start.plusSeconds(i * 3600L), daylight = false, temperatureC = 12.0 + i % 6)
        }
        val ranges = meteogramDailyRanges(hours, zone)
        assertEquals(listOf(24, 25, 24), ranges.map(MeteogramDailyRange::hourCount))
        assertTrue(ranges[0].overnightLowC != null)
        assertTrue(ranges[1].overnightLowC != null)
        assertNull("Last incomplete overnight must be absent", ranges[2].overnightLowC)
    }

    @Test fun separate_presets_have_distinct_scales_and_daily_range_domain() {
        assertEquals(6, MeteogramPresets.size)
        assertEquals(MeteogramKind.entries.toList(), MeteogramPresets)
        assertEquals(0.5, meteogramMinimumSpan(MeteogramKind.EVERYDAY), 0.0)
        assertEquals(15.0, meteogramMinimumSpan(MeteogramKind.HUMIDITY), 0.0)
        val range = meteogramDailyDomain(listOf(
            MeteogramDailyRange(LocalDate.parse("2026-10-28"), 0, 24, 24.0, 8.0),
            MeteogramDailyRange(LocalDate.parse("2026-10-29"), 24, 24, 17.0, 4.0)
        ))
        assertTrue(range.min < 4.0 && range.max > 24.0)
    }

    @Test fun compare_presets_do_not_mix_rain_with_wind_or_humidity() {
        val hours = listOf(MeteogramHour(Instant.parse("2026-10-28T10:00:00Z"),
            daylight = true, rainMm = 0.2, windKmh = 40.0, humidityPercent = 90))
        assertEquals(listOf(0.2), meteogramKindValues(MeteogramKind.EVERYDAY, hours).toList())
        assertEquals(listOf(90.0), meteogramKindValues(MeteogramKind.HUMIDITY, hours).toList())
        assertEquals(listOf(40.0, null), meteogramKindValues(MeteogramKind.WIND, hours).toList())
    }

    @Test fun selecting_a_model_never_falls_back_to_another_models_values() {
        val now = Instant.parse("2026-10-28T12:00:00Z")
        val state = GraphicForecastUiState.Loaded(
            city = City("m", "City", country = "France", latitude = 48.0,
                longitude = 2.0, timezone = "UTC"),
            points = listOf(SimplifiedTimelinePoint(instant = now, temperatureC = 22.0,
                cloudCoverPercent = 50, pressureHpa = 1015.0, humidityPercent = 80)),
            solarByDate = emptyMap(),
            modelValuesByInstant = mapOf(now to listOf(
                GraphicModelValue("Model A", temperatureC = 18.0, precipitationMm = null,
                    precipitationProbabilityPercent = null, windKmh = null, windGustKmh = null,
                    windDirectionDeg = null, cloudPercent = null, pressureHpa = null)
            )),
            calculatedAt = now
        )
        val consensus = buildMeteogramHours(state, null).single()
        assertEquals(22.0, consensus.temperatureC!!, 0.0)
        val model = buildMeteogramHours(state, "Model A").single()
        assertEquals(18.0, model.temperatureC!!, 0.0)
        assertNull(model.cloudPercent)
        assertNull(model.pressureHpa)
        assertNull(model.humidityPercent)
        val unknown = buildMeteogramHours(state, "Model B").single()
        assertNull(unknown.temperatureC)
        assertNull(unknown.cloudPercent)
        assertFalse(unknown.daylight != consensus.daylight)
    }
}
