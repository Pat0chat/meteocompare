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

class ConditionStatusContractTest {
    private val day = LocalDate.parse("2026-11-13")
    private val zone = ZoneId.of("Europe/Paris")
    private fun series(times: List<Instant>, codes: List<Int?>, clouds: List<Int?> = emptyList()) = ForecastSeries(
        WeatherModel.GFS,
        HourlyForecast(times, emptyList(), emptyList(), emptyList(), codes, cloudCover = clouds),
        DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    )
    private fun times(day: LocalDate = this.day, zone: ZoneId = this.zone): List<Instant> {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return generateSequence(start) { it.plusSeconds(3600) }.takeWhile { it < end }.toList()
    }

    @Test fun `all five statuses have different deterministic visual treatments`() {
        val cases = listOf(
            ConditionStatus.RELIABLE to ConditionVisualTreatment.NORMAL,
            ConditionStatus.ESTIMATED to ConditionVisualTreatment.ESTIMATE_MARKER,
            ConditionStatus.AMBIGUOUS to ConditionVisualTreatment.ALTERNATIVES,
            ConditionStatus.INCOMPLETE to ConditionVisualTreatment.PARTIAL_MARKER,
            ConditionStatus.UNAVAILABLE to ConditionVisualTreatment.NO_DATA
        )
        for ((status, treatment) in cases) {
            val condition = if (status == ConditionStatus.UNAVAILABLE) null else WeatherCondition.CLEAR
            val d = ConditionDecision(condition, status, ConditionOrigin.NONE)
            assertEquals(status.toString(), treatment, conditionVisualTreatment(condition, d))
        }
        assertEquals(ConditionVisualTreatment.NO_DATA, conditionVisualTreatment(null, null))
        assertEquals(ConditionVisualTreatment.NORMAL, conditionVisualTreatment(WeatherCondition.RAIN, null))
    }

    @Test fun `alternatives never include unknown or duplicate conditions`() {
        val d = ConditionDecision(null, ConditionStatus.AMBIGUOUS, ConditionOrigin.MODEL_CONSENSUS,
            alternatives = listOf(WeatherCondition.RAIN, WeatherCondition.UNKNOWN, WeatherCondition.RAIN,
                WeatherCondition.CLEAR, WeatherCondition.FOG))
        assertEquals(listOf(WeatherCondition.RAIN, WeatherCondition.CLEAR), visibleConditionAlternatives(d))
        assertEquals(emptyList<WeatherCondition>(), visibleConditionAlternatives(null))
    }

    @Test fun `majority proof with a gap remains explicitly incomplete`() {
        val t = times()
        val result = ConditionDecisionEngine.periodBetween(series(t, List(t.size) { if (it < 14) 0 else null }),
            t.first(), t.last().plusSeconds(3600))
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(ConditionStatus.INCOMPLETE, result.status)
        assertEquals(true, ConditionReason.IRREVERSIBLE_MAJORITY in result.reasons)
        assertEquals(true, ConditionReason.PARTIAL_PERIOD in result.reasons)
        assertEquals(ConditionVisualTreatment.PARTIAL_MARKER,
            conditionVisualTreatment(result.condition, result))
    }

    @Test fun `complete but estimated and partly estimated are not labelled native`() {
        val t = times()
        val allEstimated = ConditionDecisionEngine.periodBetween(series(t, List(t.size) { null },
            clouds = List(t.size) { 0 }), t.first(), t.last().plusSeconds(3600))
        assertEquals(WeatherCondition.CLEAR, allEstimated.condition)
        assertEquals(ConditionStatus.ESTIMATED, allEstimated.status)
        val mixed = ConditionDecisionEngine.periodBetween(series(t, List(t.size) { if (it < 10) 0 else null },
            clouds = List(t.size) { 0 }), t.first(), t.last().plusSeconds(3600))
        assertEquals(WeatherCondition.CLEAR, mixed.condition)
        assertEquals(ConditionStatus.ESTIMATED, mixed.status)
    }

    @Test fun `unknown periods remain unavailable and explain why`() {
        val t = times()
        val result = ConditionDecisionEngine.periodBetween(series(t, List(t.size) { null }),
            t.first(), t.last().plusSeconds(3600))
        assertEquals(null, result.condition)
        assertEquals(ConditionStatus.UNAVAILABLE, result.status)
        assertEquals(ConditionVisualTreatment.NO_DATA, conditionVisualTreatment(result.condition, result))
        assertEquals(true, ConditionReason.MISSING_DATA in result.reasons)
    }

    @Test fun `period status and coverage never depend on the number of repeated timestamps`() {
        val t = times()
        val duplicated = t + List(50) { t[0] }
        val forecast = series(duplicated, List(t.size) { if (it < 14) 0 else null } + List(50) { 0 })
        val result = ConditionDecisionEngine.periodBetween(forecast, t.first(), t.last().plusSeconds(3600))
        assertEquals(ConditionStatus.INCOMPLETE, result.status)
        assertEquals(14.0 / 24.0, result.coverage)
    }

    @Test fun `native code has priority over incompatible inferred evidence`() {
        val t = times()
        val result = ConditionDecisionEngine.hourly(series(t.take(1), listOf(61), clouds = listOf(0)).hourly, 0)
        assertEquals(WeatherCondition.RAIN, result.condition)
        assertEquals(ConditionStatus.RELIABLE, result.status)
    }
}
