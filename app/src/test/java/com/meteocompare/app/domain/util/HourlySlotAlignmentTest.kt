package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The source timestamps accumulation at its END; display slots start at H. */
class HourlySlotAlignmentTest {
    private val h = Instant.parse("2026-10-25T00:00:00Z")

    private fun hourly(
        timestamps: List<Instant>,
        rain: List<Double?>,
        probability: List<Int?>,
        codes: List<Int?> = List(timestamps.size) { null },
        clouds: List<Int?> = List(timestamps.size) { null }
    ) = HourlyForecast(
        timestamps = timestamps,
        temperature2m = List(timestamps.size) { 15.0 },
        precipitation = rain,
        precipitationProbability = probability,
        windSpeed10m = List(timestamps.size) { 10.0 },
        weatherCode = codes,
        cloudCover = clouds
    )

    @Test fun `rain at H belongs to past but H plus one to displayed future`() {
        val source = hourly(
            listOf(h, h.plusSeconds(3600)),
            rain = listOf(10.0, 2.0), probability = listOf(100, 80),
            clouds = listOf(10, 10)
        )
        val slot = HourlySampling.precipitationForSlot(source, 0)
        assertEquals(2.0, slot.amountMm!!, 0.0)
        assertEquals(80, slot.probabilityPercent)
        assertEquals(WeatherCondition.RAIN_SHOWERS, ConditionDecisionEngine.upcomingHour(source, 0).condition)
        assertEquals(ConditionWindowKind.HOURLY, ConditionDecisionEngine.upcomingHour(source, 0).timeWindow?.kind)
        assertTrue(ConditionReason.UPCOMING_HOUR_PRECIPITATION in
            ConditionDecisionEngine.upcomingHour(source, 0).reasons)
        assertEquals(ConditionWindowKind.HOURLY_PRECEDING, ConditionDecisionEngine.hourly(source, 0).timeWindow?.kind)
    }

    @Test fun `missing H plus one never reuses H even with very heavy rain`() {
        val source = hourly(listOf(h), rain = listOf(20.0), probability = listOf(100))
        assertNull(HourlySampling.precipitationForSlot(source, 0).amountMm)
        assertNull(HourlySampling.precipitationForSlot(source, 0).probabilityPercent)
        assertNull(ConditionDecisionEngine.upcomingHour(source, 0).condition)
    }

    @Test fun `gap is not shifted by index even when next row exists`() {
        val source = hourly(
            listOf(h, h.plusSeconds(7200)), rain = listOf(20.0, 2.0), probability = listOf(100, 80)
        )
        assertNull(HourlySampling.nextHourIndex(source, 0))
        assertNull(HourlySampling.precipitationForSlot(source, 0).amountMm)
        assertNull(ConditionDecisionEngine.upcomingHour(source, 0).condition)
    }

    @Test fun `both local 02 hours on autumn DST change use real instants`() {
        // 02:00 in Europe/Paris occurs twice, but the UTC instants are distinct.
        val source = hourly(
            listOf(h, h.plusSeconds(3600), h.plusSeconds(7200)),
            rain = listOf(15.0, 1.0, 3.0), probability = listOf(100, 20, 90)
        )
        assertEquals(1, HourlySampling.nextHourIndex(source, 0))
        assertEquals(2, HourlySampling.nextHourIndex(source, 1))
        assertEquals(20, HourlySampling.precipitationForSlot(source, 0).probabilityPercent)
        assertEquals(90, HourlySampling.precipitationForSlot(source, 1).probabilityPercent)
    }

    @Test fun `half hour offset source timestamps stay exact`() {
        val start = Instant.parse("2026-11-19T06:30:00Z")
        val source = hourly(
            listOf(start, start.plusSeconds(3600)),
            rain = listOf(10.0, 0.3), probability = listOf(99, 40),
            codes = listOf(0, 0)
        )
        assertEquals(40, HourlySampling.precipitationForSlot(source, 0).probabilityPercent)
        assertEquals(WeatherCondition.CLEAR, ConditionDecisionEngine.upcomingHour(source, 0).condition)
    }
}
