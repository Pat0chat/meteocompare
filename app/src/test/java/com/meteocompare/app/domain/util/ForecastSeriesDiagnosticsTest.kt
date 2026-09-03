package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ForecastSeriesDiagnosticsTest {
    private val start = Instant.parse("2026-09-02T00:00:00Z")

    @Test
    fun `detecte une longue sequence interne de valeurs manquantes`() {
        val series = series(
            timestamps = List(8) { start.plusSeconds(it * 3_600L) },
            temperatures = listOf(15.0, 16.0, null, null, null, 18.0, 19.0, 20.0)
        )

        val diagnostic = ForecastSeriesDiagnostics.analyze(series)

        assertTrue(diagnostic.hasLongInternalMissingSequence)
        val run = diagnostic.internalMissingRuns.single { it.variable == ForecastSeriesDiagnostics.Variable.TEMPERATURE }
        assertEquals(3, run.lengthHours)
        assertEquals(start.plusSeconds(2 * 3_600L), run.startInstant)
    }

    @Test
    fun `ignore les nulls de fin dhorizon qui ne sont pas des trous internes`() {
        val series = series(
            timestamps = List(8) { start.plusSeconds(it * 3_600L) },
            temperatures = listOf(15.0, 16.0, 17.0, 18.0, null, null, null, null)
        )

        val diagnostic = ForecastSeriesDiagnostics.analyze(series)

        assertFalse(diagnostic.internalMissingRuns.any {
            it.variable == ForecastSeriesDiagnostics.Variable.TEMPERATURE
        })
    }

    @Test
    fun `detecte les timestamps horaires absents au milieu dune serie`() {
        val timestamps = listOf(
            start,
            start.plusSeconds(3_600L),
            start.plusSeconds(5 * 3_600L),
            start.plusSeconds(6 * 3_600L)
        )
        val series = series(timestamps, List(timestamps.size) { 15.0 })

        val diagnostic = ForecastSeriesDiagnostics.analyze(series)

        assertTrue(diagnostic.hasLongInternalMissingSequence)
        assertEquals(3, diagnostic.timestampGaps.single().missingHours)
        assertEquals(3, diagnostic.longestInternalMissingSequenceHours)
    }

    private fun series(
        timestamps: List<Instant>,
        temperatures: List<Double?>
    ) = ForecastSeries(
        model = WeatherModel.GFS,
        hourly = HourlyForecast(
            timestamps = timestamps,
            temperature2m = temperatures,
            precipitation = List(timestamps.size) { 0.0 },
            windSpeed10m = List(timestamps.size) { 10.0 },
            weatherCode = List(timestamps.size) { 0 },
            precipitationProbability = List(timestamps.size) { 0 },
            cloudCover = List(timestamps.size) { 10 },
            windGusts10m = List(timestamps.size) { 15.0 },
            windDirection10m = List(timestamps.size) { 270 }
        ),
        daily = DailyForecast(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    )
}
