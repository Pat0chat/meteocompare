package com.meteocompare.app.data.repository

import com.meteocompare.app.data.local.BiasSampleDao
import com.meteocompare.app.data.local.ObservationSampleEntity
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.WeatherModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BiasSampleRepositoryImplTest {

    @Test
    fun `past sample count uses the same enum key as forecast storage`() = runTest {
        val dao = mockk<BiasSampleDao>()
        val date = LocalDate.of(2026, 7, 24)
        coEvery {
            dao.countPastForecastDays("paris", WeatherModel.GFS.name, date.toEpochDay())
        } returns 7
        val repository = repository(dao)

        val count = repository.countPastForecastDays("paris", WeatherModel.GFS, date)

        assertEquals(7, count)
        coVerify(exactly = 1) {
            dao.countPastForecastDays("paris", WeatherModel.GFS.name, date.toEpochDay())
        }
    }

    @Test
    fun `sample observation uses the explicit asOf window`() = runTest {
        val dao = mockk<BiasSampleDao>()
        val asOf = LocalDate.of(2026, 7, 24)
        every {
            dao.observeJoinedSamples(
                cityId = "paris",
                modelKey = WeatherModel.GFS.name,
                variable = BiasVariable.TEMPERATURE.name,
                startEpochDay = asOf.minusDays(30).toEpochDay(),
                endEpochDay = asOf.toEpochDay()
            )
        } returns flowOf(emptyList())
        val repository = repository(dao)

        repository.observeSamples(
            cityId = "paris",
            model = WeatherModel.GFS,
            variable = BiasVariable.TEMPERATURE,
            asOf = asOf,
            windowDays = 30
        ).first()
    }

    @Test
    fun `single observation write uses the injected clock`() = runTest {
        val dao = mockk<BiasSampleDao>(relaxed = true)
        val instant = Instant.parse("2026-07-24T08:00:00Z")
        val repository = BiasSampleRepositoryImpl(
            dao = dao,
            io = Dispatchers.Unconfined,
            clock = Clock.fixed(instant, ZoneOffset.UTC)
        )
        val date = LocalDate.of(2026, 7, 23)

        repository.recordObservation(
            cityId = "paris",
            variable = BiasVariable.WIND_SPEED,
            targetDate = date,
            value = 18.0
        )

        coVerify(exactly = 1) {
            dao.insertObservation(
                ObservationSampleEntity(
                    cityId = "paris",
                    variable = BiasVariable.WIND_SPEED.name,
                    targetDateEpochDay = date.toEpochDay(),
                    value = 18.0,
                    fetchedAtEpochMs = instant.toEpochMilli()
                )
            )
        }
    }

    private fun repository(dao: BiasSampleDao): BiasSampleRepositoryImpl =
        BiasSampleRepositoryImpl(dao, Dispatchers.Unconfined, Clock.systemUTC())
}
