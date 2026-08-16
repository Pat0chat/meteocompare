package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ForecastEvolutionRepositoryImplTest {
    private val city = City("paris", "Paris", country = "France", latitude = 48.85, longitude = 2.35)
    private val date = LocalDate.of(2026, 8, 18)
    private val reference = Instant.parse("2026-08-16T12:00:00Z")

    @Test
    fun `selects the nearest local snapshots around 24 48 and 72 hours`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        val rows = buildList {
            addAll(snapshot(reference.minusSeconds(25 * 3600), WeatherModel.GFS, 8.0))
            addAll(snapshot(reference.minusSeconds(25 * 3600), WeatherModel.ECMWF, 9.0))
            addAll(snapshot(reference.minusSeconds(47 * 3600), WeatherModel.GFS, 6.0))
            addAll(snapshot(reference.minusSeconds(47 * 3600), WeatherModel.ECMWF, 7.0))
            addAll(snapshot(reference.minusSeconds(73 * 3600), WeatherModel.GFS, 4.0))
            addAll(snapshot(reference.minusSeconds(73 * 3600), WeatherModel.ECMWF, 5.0))
        }
        coEvery { dao.getHistoryWindow(any(), any(), any(), any(), any(), any()) } returns rows
        coEvery { dao.oldestSnapshotAt(any(), any()) } returns reference.minusSeconds(73 * 3600).toEpochMilli()

        val result = repository(dao).getPreviousForecasts(
            city = city,
            models = listOf(WeatherModel.GFS, WeatherModel.ECMWF),
            startDate = date,
            endDate = date,
            referenceAt = reference
        )

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(setOf(1, 2, 3), data.samples.map { it.daysAgo }.toSet())
        assertEquals(setOf(25), data.samples.filter { it.daysAgo == 1 }.map { it.ageHours }.toSet())
        assertEquals(setOf(47), data.samples.filter { it.daysAgo == 2 }.map { it.ageHours }.toSet())
        assertEquals(setOf(73), data.samples.filter { it.daysAgo == 3 }.map { it.ageHours }.toSet())
        assertEquals(reference.minusSeconds(73 * 3600), data.oldestSnapshotAt)
    }

    @Test
    fun `snapshot outside tolerance is not presented as a 24 hour comparison`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        // 14 h old: 10 h away from the H-24 target, beyond the ±8 h tolerance.
        coEvery { dao.getHistoryWindow(any(), any(), any(), any(), any(), any()) } returns
            snapshot(reference.minusSeconds(14 * 3600), WeatherModel.GFS, 8.0)
        coEvery { dao.oldestSnapshotAt(any(), any()) } returns reference.minusSeconds(14 * 3600).toEpochMilli()

        val result = repository(dao).getPreviousForecasts(
            city, listOf(WeatherModel.GFS), date, date, reference
        ) as ApiResult.Success

        assertTrue(result.data.samples.isEmpty())
    }

    @Test
    fun `one bucket is never reused for two target ages`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        val rows = snapshot(reference.minusSeconds(32 * 3600), WeatherModel.GFS, 8.0)
        coEvery { dao.getHistoryWindow(any(), any(), any(), any(), any(), any()) } returns rows
        coEvery { dao.oldestSnapshotAt(any(), any()) } returns rows.first().snapshotAtEpochMs

        val result = repository(dao).getPreviousForecasts(
            city, listOf(WeatherModel.GFS), date, date, reference
        ) as ApiResult.Success

        // H-32 is exactly at the tolerance edge for H-24 and 16 h away from H-48.
        assertEquals(setOf(1), result.data.samples.map { it.daysAgo }.toSet())
    }

    @Test
    fun `equal time distance prefers snapshot with more comparable models`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        val h23 = reference.minusSeconds(23 * 3600)
        val h25 = reference.minusSeconds(25 * 3600)
        val rows = buildList {
            addAll(snapshot(h23, WeatherModel.GFS, 7.0))
            addAll(snapshot(h25, WeatherModel.GFS, 8.0))
            addAll(snapshot(h25, WeatherModel.ECMWF, 9.0))
        }
        coEvery { dao.getHistoryWindow(any(), any(), any(), any(), any(), any()) } returns rows
        coEvery { dao.oldestSnapshotAt(any(), any()) } returns h25.toEpochMilli()

        val result = repository(dao).getPreviousForecasts(
            city, listOf(WeatherModel.GFS, WeatherModel.ECMWF), date, date, reference
        ) as ApiResult.Success

        val h24Samples = result.data.samples.filter { it.daysAgo == 1 }
        assertEquals(setOf(WeatherModel.GFS, WeatherModel.ECMWF), h24Samples.map { it.model }.toSet())
        assertEquals(setOf(25), h24Samples.map { it.ageHours }.toSet())
    }

    @Test
    fun `room error stays secondary and returns an ApiResult error`() = runTest {
        val dao = mockk<ForecastEvolutionDao>()
        coEvery { dao.getHistoryWindow(any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("db unavailable")

        val result = repository(dao).getPreviousForecasts(
            city, listOf(WeatherModel.GFS), date, date, reference
        )

        assertTrue(result is ApiResult.Error)
        coVerify(exactly = 1) { dao.getHistoryWindow(any(), any(), any(), any(), any(), any()) }
    }

    private fun repository(dao: ForecastEvolutionDao) = ForecastEvolutionRepositoryImpl(
        dao = dao,
        io = Dispatchers.Unconfined
    )

    private fun snapshot(
        capturedAt: Instant,
        model: WeatherModel,
        precipitation: Double
    ): List<ForecastEvolutionEntity> {
        val bucket = capturedAt.toEpochMilli() / ForecastEvolutionRecorder.SNAPSHOT_BUCKET_MS
        return listOf(
            entity(capturedAt, bucket, model, ForecastEvolutionVariable.TEMPERATURE, 20.0),
            entity(capturedAt, bucket, model, ForecastEvolutionVariable.PRECIPITATION, precipitation),
            entity(capturedAt, bucket, model, ForecastEvolutionVariable.WIND, 30.0)
        )
    }

    private fun entity(
        capturedAt: Instant,
        bucket: Long,
        model: WeatherModel,
        variable: ForecastEvolutionVariable,
        value: Double
    ) = ForecastEvolutionEntity(
        cityId = city.id,
        modelKey = model.name,
        variable = variable.name,
        targetDateEpochDay = date.toEpochDay(),
        snapshotBucket = bucket,
        snapshotAtEpochMs = capturedAt.toEpochMilli(),
        value = value
    )
}
