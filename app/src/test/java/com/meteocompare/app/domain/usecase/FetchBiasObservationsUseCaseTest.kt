package com.meteocompare.app.domain.usecase

import com.meteocompare.app.data.remote.ClimateArchiveApi
import com.meteocompare.app.data.remote.dto.ArchiveDailyDto
import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ObservationBiasRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class FetchBiasObservationsUseCaseTest {

    private lateinit var api: ClimateArchiveApi
    private lateinit var repository: BiasSampleRepository
    private lateinit var useCase: FetchBiasObservationsUseCase

    private val city = City(
        id = "1",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35
    )
    private val today = LocalDate.of(2026, 7, 15)

    @Before
    fun setUp() {
        api = mockk()
        repository = mockk(relaxed = true)
        coEvery { repository.recordObservations(any()) } coAnswers {
            firstArg<List<ObservationBiasRecord>>().forEach { record ->
                repository.recordObservation(
                    record.cityId, record.variable, record.targetDate, record.value
                )
            }
        }
        useCase = FetchBiasObservationsUseCase(
            archiveApi = api,
            biasRepository = repository,
            io = Dispatchers.Unconfined
        )
        coEvery { repository.latestObservationDate(any(), any()) } returns null
    }

    @Test
    fun `une série optionnelle absente ne bloque pas les autres observations`() = runTest {
        coEvery {
            api.archive(any(), any(), any(), any(), any(), any(), any(), any())
        } returns ArchiveResponseDto(
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2026-07-13", "2026-07-14"),
                tempMax = listOf(25.0, 26.0),
                tempMin = listOf(15.0, 16.0),
                precipSum = null,
                windSpeedMax = listOf(20.0, 22.0)
            )
        )

        val recordedDays = useCase(city, today)

        assertEquals(2, recordedDays)
        coVerify(exactly = 2) {
            repository.recordObservation(city.id, BiasVariable.TEMPERATURE, any(), any())
        }
        coVerify(exactly = 0) {
            repository.recordObservation(city.id, BiasVariable.PRECIPITATION, any(), any())
        }
        coVerify(exactly = 2) {
            repository.recordObservation(city.id, BiasVariable.WIND_SPEED, any(), any())
        }
    }

    @Test
    fun `listes partielles sont traitées indépendamment`() = runTest {
        coEvery {
            api.archive(any(), any(), any(), any(), any(), any(), any(), any())
        } returns ArchiveResponseDto(
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = "Europe/Paris",
            daily = ArchiveDailyDto(
                time = listOf("2026-07-12", "2026-07-13", "2026-07-14"),
                tempMax = listOf(24.0),
                tempMin = emptyList(),
                precipSum = listOf(0.0, 1.0, 2.0),
                windSpeedMax = listOf(18.0, 19.0)
            )
        )

        val recordedDays = useCase(city, today)

        assertEquals(3, recordedDays)
        coVerify(exactly = 1) {
            repository.recordObservation(city.id, BiasVariable.TEMPERATURE, any(), any())
        }
        coVerify(exactly = 3) {
            repository.recordObservation(city.id, BiasVariable.PRECIPITATION, any(), any())
        }
        coVerify(exactly = 2) {
            repository.recordObservation(city.id, BiasVariable.WIND_SPEED, any(), any())
        }
    }
}
