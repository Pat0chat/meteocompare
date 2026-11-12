package com.meteocompare.app.regression

import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.SolarSkyInterpretation
import com.meteocompare.app.domain.model.WeatherCondition
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.usecase.ForecastConsensus
import com.meteocompare.app.domain.usecase.WeatherConditionConsensus
import com.meteocompare.app.domain.usecase.estimateHourlySunshineHours
import com.meteocompare.app.domain.usecase.estimatedHourlySunshineFromModels
import com.meteocompare.app.domain.util.DailyConditionProvenance
import com.meteocompare.app.domain.util.ForecastSolarWindow
import com.meteocompare.app.domain.util.resolveDailyCondition
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Crosses a fixed, reviewed golden corpus with the actual production algorithms.
 * Do NOT replace the expected outputs with values computed by the implementation.
 * Every failure identifies the exact fixture ID to reproduce the regression.
 *
 * Golden data: scripts/generate_weather_regression_corpus.py
 * Documentation: docs/weather-regression-corpus.md
 */
class WeatherRegressionCorpusTest {
    private fun records(file: String): Sequence<Map<String, String>> = sequence {
        val path = "/weather_regression/v1/$file"
        val resource = assertNotNullResource(path)
        resource.bufferedReader(Charsets.UTF_8).use { reader ->
            val header = reader.readLine()?.split('\t') ?: error("Empty corpus: $file")
            var number = 1
            while (true) {
                val line = reader.readLine() ?: break
                number += 1
                if (line.isBlank()) continue
                val columns = line.split('\t')
                require(columns.size == header.size) { "$file:$number: expected ${header.size} columns, got ${columns.size}" }
                yield(header.zip(columns).toMap())
            }
        }
    }

    private fun assertNotNullResource(path: String) =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing corpus resource $path" }

    private fun String?.intOrNull(): Int? = this?.takeUnless { it == "_" }?.toInt()
    private fun String?.doubleOrNull(): Double? = this?.takeUnless { it == "_" }?.toDouble()
    private fun String?.conditionOrNull(): WeatherCondition? =
        this?.takeUnless { it == "_" }?.let(WeatherCondition::valueOf)

    @Test fun allWmoCodesMatchReviewedMapping() {
        var count = 0
        records("wmo.tsv").forEach { row ->
            count++
            assertEquals(row.getValue("id"), WeatherCondition.valueOf(row.getValue("expected")),
                WeatherCondition.fromWmoCode(row.getValue("code").toInt()))
        }
        assertTrue("WMO fixture coverage unexpectedly shrank", count >= 110)
        assertEquals(null, WeatherCondition.fromWmoCode(null))
    }

    @Test fun exhaustiveCloudLayerAndSolarBoundaryGrid() {
        var count = 0
        records("clouds.tsv").forEach { row ->
            count++
            val id = row.getValue("id")
            val total = row.getValue("total").toInt()
            val base = WeatherCondition.fromCloudCover(total.toDouble())
            assertEquals("$id basic cloud condition", row.getValue("expected_base"), base.name)
            val veil = SolarSkyInterpretation.isVeiledSky(
                total, row["low"].intOrNull(), row["mid"].intOrNull(),
                row["high"].intOrNull(), row["sun_seconds"].doubleOrNull(),
                row["dni"].doubleOrNull())
            assertEquals("$id veil classification", row.getValue("expected_veil").toBooleanStrict(), veil)
            assertEquals("$id refined sky condition", row.getValue("expected_refined"),
                SolarSkyInterpretation.refine(base, veil)?.name)
            if (veil) {
                // WeatherCondition.fromCloudCover retains its independent raw fraction classification.
                assertEquals("$id raw fraction remains overcast", WeatherCondition.OVERCAST, base)
                for (warning in listOf(WeatherCondition.RAIN, WeatherCondition.DRIZZLE,
                    WeatherCondition.FOG, WeatherCondition.SNOW, WeatherCondition.THUNDERSTORM)) {
                    assertEquals("$id must not hide $warning", warning,
                        SolarSkyInterpretation.refine(warning, veil))
                }
            }
        }
        assertTrue("Cloud corpus coverage unexpectedly shrank", count >= 40000)
    }

    @Test fun dailyDominanceUsesPhysicalHoursAcrossZonesAndDst() {
        var count = 0
        records("daily.tsv").forEach { row ->
            count++
            val id = row.getValue("id")
            val date = LocalDate.parse(row.getValue("date"))
            val zone = ZoneId.of(row.getValue("timezone"))
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val size = ((end.epochSecond - start.epochSecond) / 3600).toInt()
            val codes = row.getValue("hourly_wmo").split(',').map { it.intOrNull() }
            assertEquals("$id UTC hour count", size, codes.size)

            val mode = row.getValue("mode")
            val selected = (0 until size).filter { hour ->
                when {
                    mode == "ALL" || mode == "DUP_NATIVE" -> true
                    mode.startsWith("FIRST_") -> hour < mode.substringAfter('_').toInt()
                    mode == "GAP_5" -> hour !in ((size / 2 - 2)..(size / 2 + 2))
                    else -> error("Unknown mode $mode")
                }
            }
            val indices = selected.toMutableList()
            val times = indices.map { start.plusSeconds(3600L * it) }.toMutableList()
            val hourlyCodes = indices.map { codes[it] }.toMutableList()
            val hourlyCloud = indices.map { row.getValue("cloud_cover").toInt() }.toMutableList()
            if (mode == "DUP_NATIVE") {
                // Corrupt cache: duplicate the last hour without a WMO code.
                // Native WMO must retain precedence regardless of duplicate ordering.
                times.add(0, times.last())
                hourlyCodes.add(0, null)
                hourlyCloud.add(0, row.getValue("cloud_cover").toInt())
            }
            val hourly = HourlyForecast(
                timestamps = times,
                temperature2m = List(times.size) { 12.0 },
                precipitation = List(times.size) { 0.0 },
                windSpeed10m = List(times.size) { 10.0 },
                weatherCode = hourlyCodes,
                cloudCover = hourlyCloud
            )
            val forecast = ForecastSeries(
                model = WeatherModel.GFS,
                hourly = hourly,
                daily = DailyForecast(
                    dates = listOf(date), tempMax = listOf(16.0), tempMin = listOf(8.0),
                    precipitationSum = listOf(12.0), windSpeedMax = listOf(20.0),
                    weatherCode = listOf(row["daily_wmo"].intOrNull())
                )
            )
            val resolved = forecast.resolveDailyCondition(date, zone)
            assertEquals("$id condition", row["expected"].conditionOrNull(), resolved?.condition)
            assertEquals("$id provenance", row["provenance"]?.takeUnless { it == "_" || it == "NONE" }
                ?.let(DailyConditionProvenance::valueOf), resolved?.provenance)
        }
        assertTrue("Daily corpus coverage unexpectedly shrank", count >= 8000)
    }

    @Test fun independentFamilyConsensusAndDryWetTiesMatchGoldenCases() {
        var count = 0
        records("consensus.tsv").forEach { row ->
            count++
            val models = row.getValue("models").split(',')
                .map(WeatherModel::valueOf)
            val conditions = row.getValue("conditions").split(',')
                .map(WeatherCondition::valueOf)
            assertEquals("${row.getValue("id")} model count", models.size, conditions.size)
            val entries = models.zip(conditions) { model, condition ->
                ForecastConsensus.Entry(model, condition)
            }
            val actual = WeatherConditionConsensus.resolve(
                entries = entries,
                cloudCoverPercent = row["cloud"].doubleOrNull()
            )
            assertEquals(row.getValue("id"), row["expected"].conditionOrNull(), actual.value)
            assertTrue("${row.getValue("id")} count invariant", actual.modelCount <= models.size)
        }
        assertTrue("Consensus fixture coverage unexpectedly shrank", count >= 600)
    }

    @Test fun hourlySunshineRespectsExplicitDurationMissingDataAndDaylight() {
        val origin = Instant.parse("2026-08-24T00:00:00Z")
        val solarWindow = ForecastSolarWindow(origin.plusSeconds(21600), origin.plusSeconds(64800))
        var count = 0
        records("sunshine.tsv").forEach { row ->
            count++
            val id = row.getValue("id")
            val result = estimateHourlySunshineHours(
                instant = origin.plusSeconds(row.getValue("utc_hour").toLong() * 3600),
                cloudCoverPercent = row["cloud"].intOrNull(), solarWindow = solarWindow,
                lowCloudPercent = row["low"].intOrNull(),
                midCloudPercent = row["mid"].intOrNull(),
                highCloudPercent = row["high"].intOrNull(),
                sunshineSeconds = row["sun_seconds"].doubleOrNull(),
                directNormalWm2 = row["dni"].doubleOrNull()
            )
            val expected = row["expected_hours"].doubleOrNull()
            if (expected == null) assertEquals("$id should be unknown, not zero", null, result)
            else {
                assertNotNull("$id expected $expected", result)
                assertEquals(id, expected, result!!, 1e-7)
                assertTrue("$id must fit a physical hour", result in 0.0..1.0)
            }
        }
        assertTrue("Solar corpus coverage unexpectedly shrank", count >= 2800)
    }

    @Test fun sunshineConsensusRequiresIndependentModelEvidenceUnderOvercastSky() {
        // A fully artificial single hour. Solar duration is recorded at the END
        // of the hour, following the Open-Meteo interval convention.
        val start = Instant.parse("2026-08-24T12:00:00Z")
        val end = start.plusSeconds(3600)
        val window = ForecastSolarWindow(start, end)
        val city = City(
            id = "corpus", name = "Synthetic", admin1 = null, country = "FR",
            latitude = 48.0, longitude = 2.0, timezone = "UTC"
        )
        var count = 0
        records("sunshine_consensus.tsv").forEach { row ->
            count++
            val id = row.getValue("id")
            val models = row.getValue("models").split(',').map(WeatherModel::valueOf)
            val clouds = row.getValue("clouds").split(',').map(String::toInt)
            val seconds = row.getValue("seconds").split(',').map { it.doubleOrNull() }
            val forecasts = models.indices.associate { i ->
                val model = models[i]
                model to ForecastSeries(
                    model = model,
                    hourly = HourlyForecast(
                        timestamps = listOf(start, end),
                        temperature2m = listOf(12.0, 12.0),
                        precipitation = listOf(0.0, 0.0),
                        windSpeed10m = listOf(3.0, 3.0),
                        cloudCover = listOf(clouds[i], clouds[i]),
                        sunshineDuration = listOf(null, seconds[i])
                    ),
                    daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
                )
            }
            val forecast = CityForecast(city = city, seriesByModel = forecasts)
            val result = estimatedHourlySunshineFromModels(forecast, start, window, cloudCoverFallback = 95)
            val expected = row["expected_hours"].doubleOrNull()
            if (expected == null) assertEquals("$id unknown stays unknown", null, result)
            else {
                assertNotNull("$id expected solar support", result)
                assertEquals(id, expected, result!!, 1e-7)
            }
        }
        assertTrue("Missing independent-family sunshine scenarios", count >= 5_000)
    }

    @Test fun weatherWarningsCannotBeErasedByVeiledSky() {
        for (warning in listOf(
            WeatherCondition.RAIN, WeatherCondition.RAIN_SHOWERS, WeatherCondition.DRIZZLE,
            WeatherCondition.FOG, WeatherCondition.SNOW, WeatherCondition.SNOW_SHOWERS,
            WeatherCondition.FREEZING_RAIN, WeatherCondition.THUNDERSTORM
        )) {
            assertEquals(warning, SolarSkyInterpretation.refine(warning, true))
            assertFalse(warning.isSky)
        }
    }
}
