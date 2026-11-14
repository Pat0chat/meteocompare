package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** Fixed-seed property tests: order, missing coverage, DST and model lineage. */
class ConditionEnginePropertyTest {
    private fun forecast(t: List<Instant>, codes: List<Int?>) = ForecastSeries(
        WeatherModel.GFS,
        HourlyForecast(t, emptyList(), emptyList(), emptyList(), codes),
        DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    )

    @Test fun `majority proof survives arbitrary unknown gaps and native WMO contradictions`() {
        val random = Random(1221)
        for (zoneName in listOf("UTC", "Europe/Paris", "America/New_York", "Asia/Tokyo")) {
            val zone = ZoneId.of(zoneName)
            for (date in listOf(LocalDate.parse("2026-03-29"), LocalDate.parse("2026-10-25"),
                LocalDate.parse("2026-11-13"))) {
                val start = date.atStartOfDay(zone).toInstant()
                val end = date.plusDays(1).atStartOfDay(zone).toInstant()
                val timestamps = generateSequence(start) { it.plusSeconds(3600) }
                    .takeWhile { it < end }.toList()
                val threshold = timestamps.size / 2 + 1
                repeat(40) {
                    val included = timestamps.indices.shuffled(random).take(threshold).toSet()
                    val forecast = forecast(timestamps,
                        timestamps.indices.map { i -> if (i in included) 0 else null })
                    val decided = ConditionDecisionEngine.periodBetween(forecast, start, end)
                    assertEquals(WeatherCondition.CLEAR, decided.condition)
                    assertEquals(true, ConditionReason.IRREVERSIBLE_MAJORITY in decided.reasons)
                    assertEquals(true, decided.coverage >= threshold.toDouble() / timestamps.size)
                }
            }
        }
    }

    @Test fun `permuting hours and repeating same UTC instant never changes a complete day`() {
        val date = LocalDate.parse("2026-10-25")
        val zone = ZoneId.of("Europe/Paris")
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timestamps = generateSequence(start) { it.plusSeconds(3600) }.takeWhile { it < end }.toList()
        val codes = timestamps.indices.map { if (it < 18) 0 else 61 }
        val original = ConditionDecisionEngine.periodBetween(forecast(timestamps, codes), start, end)
        assertEquals(WeatherCondition.CLEAR, original.condition)
        repeat(50) { seed ->
            val shuffled = timestamps.zip(codes).shuffled(Random(seed))
            val repeated = shuffled + (0..10).map { timestamps[0] to 0 }
            val result = ConditionDecisionEngine.periodBetween(
                forecast(repeated.map { it.first }, repeated.map { it.second }), start, end)
            assertEquals(original.condition, result.condition)
            assertEquals(original.coverage, result.coverage)
        }
    }

    @Test fun `permuting distinct models never changes family balanced consensus`() {
        val entries = listOf(
            ForecastConsensus.Entry(WeatherModel.ICON_EU, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ICON_GLOBAL, WeatherCondition.RAIN),
            ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.GFS, WeatherCondition.CLEAR),
            ForecastConsensus.Entry(WeatherModel.UKMO_GLOBAL, WeatherCondition.CLEAR)
        )
        val expected = WeatherConditionConsensus.resolve(entries, null)
        repeat(50) { seed ->
            val other = WeatherConditionConsensus.resolve(entries.shuffled(Random(seed)), null)
            assertEquals(expected.value, other.value)
            assertEquals(expected.familyCount, other.familyCount)
        }
    }

    @Test fun `same model derived inputs never become artificially independent votes`() {
        val vote = WeatherConditionConsensus.resolveAggregate(
            nativeEntries = listOf(ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.RAIN)),
            temperatureCentralC = 20.0, precipitationCentralMm = 5.0, cloudCoverPercent = 2.0,
            supportModels = listOf(WeatherModel.ECMWF, WeatherModel.ICON_EU),
            derivedEntries = listOf(ForecastConsensus.Entry(WeatherModel.ECMWF, WeatherCondition.CLEAR))
        )
        assertEquals(WeatherCondition.RAIN, vote.vote.value)
        assertEquals(1, vote.vote.familyCount)
    }

    @Test fun `no same model weather observations never produce a fabricated category`() {
        val x = WeatherConditionConsensus.resolveAggregate(
            nativeEntries = emptyList(), temperatureCentralC = -3.0,
            precipitationCentralMm = 3.0, cloudCoverPercent = 95.0,
            supportModels = listOf(WeatherModel.ECMWF, WeatherModel.GFS)
        )
        assertEquals(null, x.vote.value)
        assertEquals(WeatherConditionConsensus.AggregateSource.NONE, x.source)
    }
}
