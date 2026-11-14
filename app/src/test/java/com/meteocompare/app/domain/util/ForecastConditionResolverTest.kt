package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cross-view daily consensus contract: timelines and engine comparison call this resolver. */
class ForecastConditionResolverTest {
    private val date = LocalDate.of(2026, 11, 13)
    private val zone = ZoneId.of("Europe/Paris")
    private val hours = generateSequence(date.atStartOfDay(zone).toInstant()) { it.plusSeconds(3600) }
        .takeWhile { it.isBefore(date.plusDays(1).atStartOfDay(zone).toInstant()) }
        .toList()

    private fun cityForecast(vararg rows: Pair<WeatherModel, List<Int?>>): CityForecast = CityForecast(
        city = City(id = "fixture", name = "Gif-sur-Yvette", country = "France",
            latitude = 48.702, longitude = 2.134, timezone = "Europe/Paris"),
        seriesByModel = rows.associate { (model, codes) ->
            model to ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = hours,
                    temperature2m = List(hours.size) { 12.0 },
                    precipitation = List(hours.size) { null },
                    windSpeed10m = List(hours.size) { null },
                    weatherCode = codes
                ),
                // Deliberately no daily entries: hourly-only dates must be resolved.
                daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            )
        }
    )

    @Test fun `one native model provides a reliable period despite missing daily axis`() {
        val result = ForecastConditionResolver.periodConsensus(
            cityForecast(WeatherModel.GFS to List(hours.size) { 0 }), date, zone)
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(ConditionStatus.RELIABLE, result.status)
    }

    @Test fun `provable majority with missing hours remains explicitly incomplete`() {
        val result = ForecastConditionResolver.periodConsensus(
            cityForecast(WeatherModel.GFS to List(hours.size) { if (it < 15) 0 else null }), date, zone)
        assertEquals(WeatherCondition.CLEAR, result.condition)
        assertEquals(ConditionStatus.INCOMPLETE, result.status)
        assertTrue(ConditionReason.PARTIAL_PERIOD in result.reasons)
        assertTrue(ConditionReason.IRREVERSIBLE_MAJORITY in result.reasons)
    }

    @Test fun `opposed native model families keep alternatives rather than inventing an icon`() {
        val result = ForecastConditionResolver.periodConsensus(
            cityForecast(
                WeatherModel.GFS to List(hours.size) { 0 },
                WeatherModel.ECMWF to List(hours.size) { 61 }
            ), date, zone)
        assertEquals(null, result.condition)
        assertEquals(ConditionStatus.AMBIGUOUS, result.status)
        assertTrue(WeatherCondition.CLEAR in result.alternatives)
        assertTrue(WeatherCondition.RAIN in result.alternatives)
    }

    @Test fun `genuinely absent conditions stay unavailable`() {
        val result = ForecastConditionResolver.periodConsensus(
            cityForecast(WeatherModel.GFS to List(hours.size) { null }), date, zone)
        assertEquals(null, result.condition)
        assertEquals(ConditionStatus.UNAVAILABLE, result.status)
    }
}
