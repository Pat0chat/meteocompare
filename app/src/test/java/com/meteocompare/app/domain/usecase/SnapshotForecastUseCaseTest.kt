package com.meteocompare.app.domain.usecase

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.CityForecast
import com.meteocompare.app.domain.model.DailyForecast
import com.meteocompare.app.domain.model.ForecastSeries
import com.meteocompare.app.domain.model.HourlyForecast
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SnapshotForecastUseCaseTest {

    private val today = LocalDate.of(2024, 7, 15)
    private val issuedAt = Instant.parse("2024-07-15T08:30:00Z")

    @Test
    fun `snapshot records only tomorrow for every model`() = runTest {
        val repo = FakeBiasSampleRepository()
        val useCase = SnapshotForecastUseCase(repo)
        val forecast = buildForecast(
            mapOf(
                WeatherModel.GFS to dailyOf(
                    listOf(today, today.plusDays(1), today.plusDays(2)),
                    listOf(25.0, 26.0, 27.0),
                    listOf(0.0, 1.0, 2.0),
                    listOf(15.0, 20.0, 25.0)
                ),
                WeatherModel.ECMWF to dailyOf(
                    listOf(today.plusDays(1)),
                    listOf(25.5), listOf(0.5), listOf(18.0)
                )
            )
        )

        useCase(forecast, issuedAt = issuedAt, today = today)

        assertEquals(6, repo.forecastRecords.size)
        assertTrue(repo.forecastRecords.all { it.targetDate == today.plusDays(1) })
    }

    @Test
    fun `tomorrow values are recorded independently when lists are partial`() = runTest {
        val repo = FakeBiasSampleRepository()
        val useCase = SnapshotForecastUseCase(repo)
        val forecast = buildForecast(
            mapOf(
                WeatherModel.GFS to dailyOf(
                    listOf(today, today.plusDays(1)),
                    listOf(25.0),
                    listOf(0.0, 1.2),
                    listOf(15.0, 22.0)
                )
            )
        )

        useCase(forecast, issuedAt = issuedAt, today = today)

        assertEquals(2, repo.forecastRecords.size)
        assertEquals(
            setOf(BiasVariable.PRECIPITATION, BiasVariable.WIND_SPEED),
            repo.forecastRecords.map { it.variable }.toSet()
        )
    }

    @Test
    fun `forecast without tomorrow records nothing`() = runTest {
        val repo = FakeBiasSampleRepository()
        val useCase = SnapshotForecastUseCase(repo)
        val forecast = buildForecast(
            mapOf(
                WeatherModel.GFS to dailyOf(
                    listOf(today, today.plusDays(2)),
                    listOf(25.0, 27.0), listOf(0.0, 2.0), listOf(15.0, 25.0)
                )
            )
        )

        useCase(forecast, issuedAt = issuedAt, today = today)

        assertTrue(repo.forecastRecords.isEmpty())
    }

    @Test
    fun `issue key is normalized to city local day`() = runTest {
        val instant = Instant.parse("2026-07-23T23:30:00Z")
        val localToday = LocalDate.of(2026, 7, 24)
        val repo = FakeBiasSampleRepository()
        val useCase = SnapshotForecastUseCase(repo, Clock.fixed(instant, ZoneOffset.UTC))
        val city = City(
            id = "kiritimati",
            name = "Kiritimati",
            latitude = 1.87,
            longitude = -157.43,
            country = "Kiribati",
            timezone = "Pacific/Kiritimati"
        )
        val forecast = buildForecast(
            mapOf(
                WeatherModel.GFS to dailyOf(
                    listOf(localToday.plusDays(1)),
                    listOf(28.0), listOf(0.0), listOf(12.0)
                )
            ),
            city = city
        )

        useCase(forecast)

        val expectedMarker = localToday
            .atStartOfDay(java.time.ZoneId.of(city.timezone))
            .toInstant()
        assertEquals(3, repo.forecastRecords.size)
        assertTrue(repo.forecastRecords.all { it.targetDate == localToday.plusDays(1) })
        assertTrue(repo.forecastRecords.all { it.issuedAt == expectedMarker })
    }

    @Test
    fun `refreshes on same local day use the same persistence key`() = runTest {
        val repo = FakeBiasSampleRepository()
        val useCase = SnapshotForecastUseCase(repo)
        val forecast = buildForecast(
            mapOf(
                WeatherModel.GFS to dailyOf(
                    listOf(today.plusDays(1)),
                    listOf(26.0), listOf(1.0), listOf(20.0)
                )
            )
        )

        useCase(forecast, issuedAt = issuedAt, today = today)
        useCase(forecast, issuedAt = issuedAt.plusSeconds(6 * 3600), today = today)

        val first = repo.forecastRecords.take(3)
        val second = repo.forecastRecords.drop(3)
        assertEquals(first, second)
    }

    @Test
    fun `empty forecast records nothing`() = runTest {
        val repo = FakeBiasSampleRepository()
        SnapshotForecastUseCase(repo)(buildForecast(emptyMap()), issuedAt, today)
        assertTrue(repo.forecastRecords.isEmpty())
    }

    private fun buildForecast(
        models: Map<WeatherModel, DailyForecast>,
        city: City = City(
            id = "paris",
            name = "Paris",
            latitude = 48.85,
            longitude = 2.35,
            country = "France",
            timezone = "Europe/Paris"
        )
    ): CityForecast = CityForecast(
        city = city,
        seriesByModel = models.mapValues { (model, daily) ->
            ForecastSeries(
                model = model,
                hourly = HourlyForecast(
                    timestamps = emptyList(),
                    temperature2m = emptyList(),
                    precipitation = emptyList(),
                    windSpeed10m = emptyList()
                ),
                daily = daily
            )
        }
    )

    private fun dailyOf(
        dates: List<LocalDate>,
        tempMax: List<Double?>,
        precip: List<Double?>,
        wind: List<Double?>
    ) = DailyForecast(
        dates = dates,
        tempMax = tempMax,
        tempMin = List(dates.size) { null },
        precipitationSum = precip,
        windSpeedMax = wind
    )

    private class FakeBiasSampleRepository : BiasSampleRepository {
        data class ForecastRecord(
            val cityId: String,
            val model: WeatherModel,
            val variable: BiasVariable,
            val targetDate: LocalDate,
            val issuedAt: Instant,
            val value: Double
        )

        val forecastRecords = mutableListOf<ForecastRecord>()

        override suspend fun recordForecast(
            cityId: String,
            model: WeatherModel,
            variable: BiasVariable,
            targetDate: LocalDate,
            issuedAt: Instant,
            value: Double
        ) {
            forecastRecords += ForecastRecord(
                cityId, model, variable, targetDate, issuedAt, value
            )
        }

        override fun observeSamples(
            cityId: String,
            model: WeatherModel,
            variable: BiasVariable,
            asOf: LocalDate,
            timezone: String?,
            windowDays: Int
        ): Flow<List<BiasSample>> = flowOf(emptyList())

        override suspend fun recordObservation(
            cityId: String,
            variable: BiasVariable,
            targetDate: LocalDate,
            value: Double
        ) = error("Not expected")

        override suspend fun earliestMissingReferenceDate(
            cityId: String,
            upToDate: LocalDate
        ): LocalDate? = null

        override suspend fun purgeOlderThan(beforeDate: LocalDate) = error("Not expected")
    }
}
