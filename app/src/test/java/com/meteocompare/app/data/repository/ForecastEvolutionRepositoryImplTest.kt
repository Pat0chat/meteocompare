package com.meteocompare.app.data.repository

import com.meteocompare.app.core.network.ApiResult
import com.meteocompare.app.data.local.ForecastEvolutionDao
import com.meteocompare.app.data.local.ForecastEvolutionEntity
import com.meteocompare.app.data.remote.PreviousRunsApi
import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import com.meteocompare.app.domain.model.WeatherModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ForecastEvolutionRepositoryImplTest {

    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35,
        timezone = "Europe/Paris"
    )
    private val date = LocalDate.of(2026, 8, 16)
    private val now = Instant.parse("2026-08-16T10:00:00Z")

    @Test
    fun `previous runs day1 day2 day3 sont agrégés en un seul fetch`() = runTest {
        val api = mockk<PreviousRunsApi>()
        val dao = mockk<ForecastEvolutionDao>()
        coEvery { dao.getForWindow(any(), any(), any(), any()) } returns emptyList()
        coEvery { dao.latestFetchForWindow(any(), any(), any(), any()) } returns null
        coEvery { dao.replaceWindow(any(), any(), any(), any(), any()) } returns Unit
        coEvery { dao.purgeFetchedBefore(any()) } returns Unit

        coEvery {
            api.getForecastEvolution(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns response(listOf(WeatherModel.GFS, WeatherModel.ECMWF))

        val repository = ForecastEvolutionRepositoryImpl(
            api = api,
            dao = dao,
            io = Dispatchers.Unconfined,
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

        val result = repository.getPreviousForecasts(
            city = city,
            models = listOf(WeatherModel.GFS, WeatherModel.ECMWF),
            startDate = date,
            endDate = date
        )

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(18, data.samples.size) // 2 modèles × 3 variables × 3 offsets
        assertEquals(
            24.0,
            data.samples.single {
                it.model == WeatherModel.GFS &&
                    it.variable == ForecastEvolutionVariable.TEMPERATURE &&
                    it.daysAgo == 1
            }.value,
            0.0001
        )
        assertEquals(
            12.0,
            data.samples.single {
                it.model == WeatherModel.ECMWF &&
                    it.variable == ForecastEvolutionVariable.PRECIPITATION &&
                    it.daysAgo == 3
            }.value,
            0.0001
        )
        coVerify(exactly = 1) {
            api.getForecastEvolution(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    @Test
    fun `un cache partiel recent est valide et ne relance pas le reseau`() = runTest {
        val api = mockk<PreviousRunsApi>()
        val dao = mockk<ForecastEvolutionDao>()
        val fetchedAt = now.minusSeconds(60 * 60).toEpochMilli()
        val cached = ForecastEvolutionEntity(
            cityId = city.id,
            modelKey = WeatherModel.GFS.name,
            variable = ForecastEvolutionVariable.TEMPERATURE.name,
            targetDateEpochDay = date.toEpochDay(),
            daysAgo = 1,
            value = 22.0,
            fetchedAtEpochMs = fetchedAt
        )
        coEvery { dao.getForWindow(any(), any(), any(), any()) } returns listOf(cached)
        coEvery { dao.latestFetchForWindow(any(), any(), any(), any()) } returns fetchedAt

        val repository = ForecastEvolutionRepositoryImpl(
            api = api,
            dao = dao,
            io = Dispatchers.Unconfined,
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

        val result = repository.getPreviousForecasts(
            city = city,
            models = listOf(WeatherModel.GFS, WeatherModel.ECMWF),
            startDate = date,
            endDate = date
        )

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertTrue(data.fromCache)
        assertEquals(1, data.samples.size)
        coVerify(exactly = 0) {
            api.getForecastEvolution(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    private fun response(models: List<WeatherModel>): PreviousRunsResponseDto {
        val times = JsonArray((0 until 24).map { hour ->
            JsonPrimitive("2026-08-16T${hour.toString().padStart(2, '0')}:00")
        })
        val content = linkedMapOf<String, kotlinx.serialization.json.JsonElement>("time" to times)

        for (model in models) {
            for (daysAgo in 1..3) {
                content["temperature_2m_${model.apiKey}_previous_day$daysAgo"] =
                    JsonArray((1..24).map { JsonPrimitive(it.toDouble()) })
                content["precipitation_${model.apiKey}_previous_day$daysAgo"] =
                    JsonArray((1..24).map { JsonPrimitive(0.5) })
                content["wind_speed_10m_${model.apiKey}_previous_day$daysAgo"] =
                    JsonArray((1..24).map { JsonPrimitive(10.0 + it) })
            }
        }

        return PreviousRunsResponseDto(
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = requireNotNull(city.timezone),
            hourly = JsonObject(content)
        )
    }
}
