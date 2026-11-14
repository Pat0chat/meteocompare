package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Deterministic edge cases; no network, no calibration dressed up as a confidence score. */
class ConditionDecisionEngineTest {
    private val utc = ZoneId.of("UTC")
    private val day = LocalDate.parse("2026-11-13")

    private fun source(
        timestamps: List<Instant>,
        wmo: List<Int?> = emptyList(),
        cloud: List<Int?> = emptyList(),
        precip: List<Double?> = emptyList(),
        temp: List<Double?> = emptyList(),
        dailyWmo: Int? = null
    ) = ForecastSeries(
        WeatherModel.GFS,
        HourlyForecast(timestamps, temp, precip, emptyList(), wmo, cloudCover = cloud),
        DailyForecast(listOf(day), emptyList(), emptyList(), emptyList(), emptyList(),
            weatherCode = listOf(dailyWmo))
    )

    private fun hours(date: LocalDate, zone: ZoneId): List<Instant> {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return generateSequence(start) { it.plusSeconds(3600) }.takeWhile { it < end }.toList()
    }

    @Test fun `WMO takes precedence over contradictory rainfall totals`() {
        val x = source(listOf(Instant.EPOCH), listOf(0), precip = listOf(20.0), temp = listOf(-2.0))
        val result = ConditionDecisionEngine.hourly(x.hourly, 0)
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(ConditionStatus.RELIABLE, result.status)
        assertEquals(ConditionOrigin.HOURLY_WMO, result.origin)
    }

    @Test fun `warm rain with missing WMO is explicitly estimated`() {
        val x = source(listOf(Instant.EPOCH), precip = listOf(2.0), temp = listOf(12.0))
        val result = ConditionDecisionEngine.hourly(x.hourly, 0)
        assertEquals(WeatherCondition.RAIN_SHOWERS, result.condition)
        assertEquals(ConditionStatus.ESTIMATED, result.status)
        assertEquals(true, ConditionReason.PRECEDING_HOUR_PRECIPITATION in result.reasons)
    }

    @Test fun `weak probability does not turn an inferred rain scenario into the main icon`() {
        val timestamp = listOf(Instant.EPOCH)
        val hourly = HourlyForecast(
            timestamps = timestamp, temperature2m = listOf(14.0), precipitation = listOf(0.4),
            windSpeed10m = emptyList(), precipitationProbability = listOf(20)
        )
        val result = ConditionDecisionEngine.hourly(hourly, 0)
        assertEquals(null, result.condition)
        assertEquals(ConditionStatus.UNAVAILABLE, result.status)
        val withCloud = hourly.copy(cloudCover = listOf(0))
        assertEquals(WeatherCondition.CLEAR, ConditionDecisionEngine.hourly(withCloud, 0).condition)
        val withNative = hourly.copy(weatherCode = listOf(61))
        assertEquals(WeatherCondition.RAIN, ConditionDecisionEngine.hourly(withNative, 0).condition)
    }

    @Test fun `near freezing 2m temperature alone cannot prove snow or rain`() {
        val x = source(listOf(Instant.EPOCH), precip = listOf(2.0), temp = listOf(-1.0), cloud = listOf(5))
        val result = ConditionDecisionEngine.hourly(x.hourly, 0)
        assertEquals(null, result.condition)
        assertEquals(ConditionStatus.AMBIGUOUS, result.status)
        assertEquals(true, WeatherCondition.SNOW in result.alternatives)
    }

    @Test fun `invalid negative precipitation cannot create a wet event`() {
        val x = source(listOf(Instant.EPOCH), precip = listOf(-2.0), cloud = listOf(25))
        val result = ConditionDecisionEngine.hourly(x.hourly, 0)
        assertEquals(WeatherCondition.MAINLY_CLEAR, result.condition)
        assertEquals(true, ConditionReason.INVALID_PHYSICAL_VALUE in result.reasons)
    }

    @Test fun `unrecognised native WMO is marked and cloud fallback is used`() {
        val x = source(listOf(Instant.EPOCH), wmo = listOf(999), cloud = listOf(100))
        val result = ConditionDecisionEngine.hourly(x.hourly, 0)
        assertEquals(WeatherCondition.OVERCAST, result.condition)
        assertEquals(true, ConditionReason.INVALID_WMO in result.reasons)
    }

    @Test fun `partial cloud layers can only prove fully covered sky`() {
        val t = listOf(Instant.EPOCH)
        val x = source(t).copy(hourly = HourlyForecast(t, emptyList(), emptyList(), emptyList(),
            cloudCoverLow = listOf(95), cloudCoverMid = emptyList(), cloudCoverHigh = listOf(10)))
        val result = ConditionDecisionEngine.hourly(x.hourly, 0)
        assertEquals(WeatherCondition.OVERCAST, result.condition)
        assertEquals(true, ConditionReason.PARTIAL_CLOUD_LAYERS in result.reasons)
    }

    @Test fun `partial cloud layers must not invent clear skies`() {
        val t = listOf(Instant.EPOCH)
        val x = source(t).copy(hourly = HourlyForecast(t, emptyList(), emptyList(), emptyList(),
            cloudCoverLow = listOf(5), cloudCoverMid = emptyList(), cloudCoverHigh = listOf(10)))
        assertEquals(null, ConditionDecisionEngine.hourly(x.hourly, 0).condition)
    }

    @Test fun `a daily leaf with 16 of 24 hours is proven despite eight missing hours`() {
        val t = hours(day, utc)
        val x = source(t, wmo = List(24) { if (it < 16) 0 else null }, dailyWmo = 95)
        val result = ConditionDecisionEngine.period(x, day, utc)
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(ConditionOrigin.HOURLY_DOMINANT, result.origin)
        assertEquals(true, ConditionReason.IRREVERSIBLE_MAJORITY in result.reasons)
    }

    @Test fun `short severe storm is tracked separately from dominant clear sky`() {
        val t = hours(day, utc)
        val x = source(t, wmo = List(24) { if (it == 14) 95 else 0 }, dailyWmo = 95)
        val result = ConditionDecisionEngine.period(x, day, utc)
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(WeatherCondition.THUNDERSTORM, result.notablePhenomenon)
    }

    @Test fun `twelve hours dry twelve hours wet remain ambiguous`() {
        val t = hours(day, utc)
        val x = source(t, wmo = List(24) { if (it < 12) 0 else 61 })
        val result = ConditionDecisionEngine.period(x, day, utc)
        assertEquals(null, result.condition)
        assertEquals(ConditionStatus.AMBIGUOUS, result.status)
        assertEquals(true, ConditionReason.NO_DOMINANT_CONDITION in result.reasons)
    }

    @Test fun `absent daily date does not hide a demonstrable hourly majority`() {
        val t = hours(day, utc)
        val x = source(t, wmo = List(24) { 0 }).copy(daily = DailyForecast(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        assertEquals(WeatherCondition.CLEAR, ConditionDecisionEngine.period(x, day, utc).condition)
    }

    @Test fun `missing next sunrise is explained instead of inventing a night`() {
        val x = source(hours(day, utc), wmo = List(24) { 0 })
        val solar = ForecastSolarWindow(Instant.parse("2026-11-13T07:00:00Z"),
            Instant.parse("2026-11-13T17:00:00Z"))
        val result = ConditionDecisionEngine.period(x, day, utc, DailyConditionPeriod.NIGHT, solar)
        assertEquals(ConditionStatus.UNAVAILABLE, result.status)
        assertEquals(true, ConditionReason.MISSING_FOLLOWING_SUNRISE in result.reasons)
    }

    @Test fun `DST spring actual 23 hours are used rather than a nominal 24`() {
        val d = LocalDate.parse("2026-03-29")
        val zone = ZoneId.of("Europe/Paris")
        val t = hours(d, zone)
        assertEquals(23, t.size)
        val x = source(t, wmo = List(23) { if (it < 12) 0 else null })
        val result = ConditionDecisionEngine.period(x, d, zone)
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(true, ConditionReason.IRREVERSIBLE_MAJORITY in result.reasons)
    }

    @Test fun `DST autumn actual 25 hours are used rather than a nominal 24`() {
        val d = LocalDate.parse("2026-10-25")
        val zone = ZoneId.of("Europe/Paris")
        val t = hours(d, zone)
        assertEquals(25, t.size)
        val x = source(t, wmo = List(25) { if (it < 12) 0 else null })
        val result = ConditionDecisionEngine.period(x, d, zone)
        assertEquals(null, result.condition)
    }

    @Test fun `duplicates at one instant do not multiply the majority`() {
        val t = hours(day, utc)
        val x = source(t + List(25) { t[0] }, wmo = List(24) { if (it < 12) 0 else 61 } + List(25) { 0 })
        val result = ConditionDecisionEngine.period(x, day, utc)
        assertEquals(null, result.condition)
        assertEquals(1.0, result.coverage)
    }

    @Test fun `daily severe maximum fallback is tagged as maximum not dominance`() {
        val x = source(emptyList(), dailyWmo = 95)
        val result = ConditionDecisionEngine.period(x, day, utc)
        assertEquals(WeatherCondition.THUNDERSTORM, result.condition)
        assertEquals(ConditionOrigin.DAILY_WMO_MAXIMUM, result.origin)
        assertEquals(true, ConditionReason.DAILY_WMO_IS_MAXIMUM in result.reasons)
    }

    @Test fun `incomplete night with definite majority uses duration not daily severe maximum`() {
        val start = Instant.parse("2026-11-13T17:30:00Z")
        val end = Instant.parse("2026-11-14T07:30:00Z")
        val t = (0..13).map { Instant.parse("2026-11-13T17:00:00Z").plusSeconds(it * 3600L) }
        val x = source(t, wmo = List(14) { if (it < 11) 0 else null }, dailyWmo = 95)
        val result = ConditionDecisionEngine.periodBetween(x, start, end)
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(true, result.coverage > 0.5)
    }
}
