package com.meteocompare.app.data.repository

import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ForecastEvolutionRecorderTest {
    private val city = City("paris", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val date = LocalDate.of(2026, 8, 18)
    private val capturedAt = Instant.parse("2026-08-16T12:34:00Z")

    @Test
    fun `records exactly the daily values fetched by the fresh forecast`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        val inserted = slot<List<ForecastEvolutionEntity>>()
        coEvery { dao.insertSnapshotBucketIfAbsent(any(), any(), capture(inserted)) } returns true
        coEvery { dao.purgeCapturedBefore(any()) } returns Unit
        val recorder = ForecastEvolutionRecorder(dao, Dispatchers.Unconfined)

        recorder.record(forecast())

        assertEquals(6, inserted.captured.size) // 2 modèles × 3 métriques × 1 jour
        assertEquals(setOf(WeatherModel.GFS.name, WeatherModel.ECMWF.name), inserted.captured.map { it.modelKey }.toSet())
        assertEquals(setOf("TEMPERATURE", "PRECIPITATION", "WIND"), inserted.captured.map { it.variable }.toSet())
        assertTrue(inserted.captured.all { it.snapshotAtEpochMs == capturedAt.toEpochMilli() })
        assertTrue(inserted.captured.all {
            it.snapshotBucket == capturedAt.toEpochMilli() / ForecastEvolutionRecorder.SNAPSHOT_BUCKET_MS
        })
        coVerify(exactly = 1) {
            dao.insertSnapshotBucketIfAbsent(
                cityId = city.id,
                snapshotBucket = capturedAt.toEpochMilli() / ForecastEvolutionRecorder.SNAPSHOT_BUCKET_MS,
                samples = any()
            )
        }
        coVerify(exactly = 1) { dao.purgeCapturedBefore(any()) }
    }



    @Test
    fun `invalid or negative daily values are never persisted as valid evolution data`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        val inserted = slot<List<ForecastEvolutionEntity>>()
        coEvery { dao.insertSnapshotBucketIfAbsent(any(), any(), capture(inserted)) } returns true
        coEvery { dao.purgeCapturedBefore(any()) } returns Unit
        val recorder = ForecastEvolutionRecorder(dao, Dispatchers.Unconfined)
        val base = forecast()
        val corrupted = base.copy(
            seriesByModel = base.seriesByModel.mapValues { (model, series) ->
                series.copy(
                    daily = series.daily.copy(
                        tempMax = listOf(if (model == WeatherModel.GFS) Double.NaN else 22.0),
                        precipitationSum = listOf(if (model == WeatherModel.GFS) -1.0 else 4.0),
                        windSpeedMax = listOf(if (model == WeatherModel.GFS) Double.POSITIVE_INFINITY else 35.0)
                    )
                )
            }
        )

        recorder.record(corrupted)

        assertEquals(3, inserted.captured.size)
        assertEquals(setOf(WeatherModel.ECMWF.name), inserted.captured.map { it.modelKey }.toSet())
        assertTrue(inserted.captured.all { it.value.isFinite() && it.value >= 0.0 })
    }

    @Test
    fun `partial last horizon day is not recorded as a comparable daily value`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        val inserted = slot<List<ForecastEvolutionEntity>>()
        coEvery { dao.insertSnapshotBucketIfAbsent(any(), any(), capture(inserted)) } returns true
        coEvery { dao.purgeCapturedBefore(any()) } returns Unit
        val recorder = ForecastEvolutionRecorder(dao, Dispatchers.Unconfined)
        val base = forecast()
        val partial = base.copy(
            seriesByModel = base.seriesByModel.mapValues { (_, series) ->
                series.copy(
                    hourly = series.hourly.copy(
                        precipitation = series.hourly.precipitation.mapIndexed { index, value ->
                            if (index >= 18) null else value
                        }
                    )
                )
            }
        )

        recorder.record(partial)

        assertEquals(4, inserted.captured.size) // T + vent pour 2 modèles ; pluie exclue
        assertTrue(inserted.captured.none { it.variable == "PRECIPITATION" })
    }

    @Test
    fun `same three hour bucket does not trigger retention work again`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        coEvery { dao.insertSnapshotBucketIfAbsent(any(), any(), any()) } returns false
        val recorder = ForecastEvolutionRecorder(dao, Dispatchers.Unconfined)

        recorder.record(forecast())

        coVerify(exactly = 1) { dao.insertSnapshotBucketIfAbsent(any(), any(), any()) }
        coVerify(exactly = 0) { dao.purgeCapturedBefore(any()) }
    }

    @Test
    fun `clearing a city removes its local evolution history`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        coEvery { dao.deleteForCity(city.id) } returns Unit
        val recorder = ForecastEvolutionRecorder(dao, Dispatchers.Unconfined)

        recorder.clearCity(city.id)

        coVerify(exactly = 1) { dao.deleteForCity(city.id) }
    }

    private fun forecast(): CityForecast {
        val series = listOf(WeatherModel.GFS, WeatherModel.ECMWF).associateWith { model ->
            ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = (0 until 24).map { hour ->
                        date.atStartOfDay(ZoneOffset.UTC).plusHours(hour.toLong()).toInstant()
                    },
                    temperature2m = List(24) { if (model == WeatherModel.GFS) 20.0 else 21.0 },
                    precipitation = List(24) { if (model == WeatherModel.GFS) 0.125 else 1.0 / 6.0 },
                    windSpeed10m = List(24) { if (model == WeatherModel.GFS) 30.0 else 35.0 }
                ),
                daily = DailyForecast(
                    dates = listOf(date),
                    tempMax = listOf(if (model == WeatherModel.GFS) 21.0 else 22.0),
                    tempMin = listOf(12.0),
                    precipitationSum = listOf(if (model == WeatherModel.GFS) 3.0 else 4.0),
                    windSpeedMax = listOf(if (model == WeatherModel.GFS) 30.0 else 35.0)
                )
            )
        }
        return CityForecast(city, series, fetchedAt = capturedAt)
    }
}
