package com.meteocompare.app.domain.usecase

import com.meteocompare.app.data.remote.PreviousRunsApi
import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BootstrapBiasHistoryUseCaseTest {

    private lateinit var api: PreviousRunsApi
    private lateinit var repository: BiasSampleRepository
    private lateinit var useCase: BootstrapBiasHistoryUseCase

    private val today = LocalDate.of(2026, 8, 4)
    private val city = City(
        id = "paris",
        name = "Paris",
        country = "France",
        latitude = 48.85,
        longitude = 2.35,
        timezone = "Europe/Paris"
    )

    @Before
    fun setUp() {
        api = mockk()
        repository = mockk(relaxed = true)
        useCase = BootstrapBiasHistoryUseCase(
            api = api,
            biasRepository = repository,
            io = Dispatchers.Unconfined
        )
    }

    @Test
    fun `bootstrap agrège quatorze jours horaires en variables quotidiennes J plus 1`() = runTest {
        val records = slot<List<ForecastBiasRecord>>()
        coEvery { repository.recordForecasts(capture(records)) } returns Unit
        coEvery { api.getPreviousDayOne(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            response(
                model = WeatherModel.GFS,
                days = 14,
                temperature = { _, hour -> hour.toDouble() },
                precipitation = { _, _ -> 0.5 },
                wind = { _, hour -> 20.0 + hour }
            )

        val result = useCase(city, listOf(WeatherModel.GFS), today, requestedDays = 14)

        assertEquals(14, result.coveredDays)
        assertEquals(1, result.coveredModels)
        assertEquals(42, result.forecastRecords)
        assertEquals(42, records.captured.size)
        assertEquals(14, result.sampleCount(WeatherModel.GFS, BiasVariable.TEMPERATURE))
        assertEquals(14, result.sampleCount(WeatherModel.GFS, BiasVariable.PRECIPITATION))
        assertEquals(14, result.sampleCount(WeatherModel.GFS, BiasVariable.WIND_SPEED))
        assertEquals(1, result.forecastReadyModels(BiasVariable.TEMPERATURE))

        val lastTarget = today.minusDays(1)
        assertEquals(
            23.0,
            records.captured.single {
                it.targetDate == lastTarget && it.variable == BiasVariable.TEMPERATURE
            }.value,
            0.0001
        )
        assertEquals(
            12.0,
            records.captured.single {
                it.targetDate == lastTarget && it.variable == BiasVariable.PRECIPITATION
            }.value,
            0.0001
        )
        assertEquals(
            43.0,
            records.captured.single {
                it.targetDate == lastTarget && it.variable == BiasVariable.WIND_SPEED
            }.value,
            0.0001
        )
        records.captured.forEach { record ->
            assertEquals(
                record.targetDate.minusDays(1),
                record.issuedAt.atZone(ZoneId.of(city.timezone)).toLocalDate()
            )
        }
    }



    @Test
    fun `bootstrap conserve des profils distincts par lead day et ne fabrique pas les horizons absents`() = runTest {
        val model = WeatherModel.GFS
        val target = today.minusDays(1)
        val records = slot<List<ForecastBiasRecord>>()
        coEvery { repository.recordForecasts(capture(records)) } returns Unit
        val hourly = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        hourly["time"] = JsonArray(dayTimes(target))
        listOf(1, 3).forEach { lead ->
            hourly["temperature_2m_previous_day${lead}_${model.apiKey}"] = values(24) { 10.0 * lead + it }
            hourly["precipitation_previous_day${lead}_${model.apiKey}"] = values(24) { lead.toDouble() }
            hourly["wind_speed_10m_previous_day${lead}_${model.apiKey}"] = values(24) { 20.0 * lead + it }
        }
        coEvery {
            api.getPreviousDayOne(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns PreviousRunsResponseDto(city.latitude, city.longitude, city.timezone!!, JsonObject(hourly))

        val result = useCase(city, listOf(model), today, requestedDays = 1)

        assertEquals(6, result.forecastRecords)
        assertEquals(1, result.sampleCount(model, BiasVariable.TEMPERATURE, leadDay = 1))
        assertEquals(1, result.sampleCount(model, BiasVariable.TEMPERATURE, leadDay = 3))
        assertEquals(0, result.sampleCount(model, BiasVariable.TEMPERATURE, leadDay = 2))
        assertEquals(setOf(1, 3), records.captured.map { it.leadDay }.toSet())
        assertEquals(6, records.captured.size)
        records.captured.filter { it.leadDay == 1 }.forEach { record ->
            assertEquals(target.minusDays(1), record.issuedAt.atZone(ZoneId.of(city.timezone)).toLocalDate())
        }
        records.captured.filter { it.leadDay == 3 }.forEach { record ->
            assertEquals(target.minusDays(3), record.issuedAt.atZone(ZoneId.of(city.timezone)).toLocalDate())
        }
    }

    @Test
    fun `bootstrap demande trois semaines par défaut et conserve quatorze jours valides`() = runTest {
        val records = slot<List<ForecastBiasRecord>>()
        coEvery { repository.recordForecasts(capture(records)) } returns Unit
        val response = response(
            model = WeatherModel.GFS,
            days = 21,
            temperature = { day, hour -> if (day < 7) null else hour.toDouble() },
            precipitation = { day, _ -> if (day < 7) null else 0.5 },
            wind = { day, hour -> if (day < 7) null else 20.0 + hour }
        )
        coEvery {
            api.getPreviousDayOne(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns response

        val result = useCase(city, listOf(WeatherModel.GFS), today)

        assertEquals(21, result.requestedDays)
        assertEquals(14, result.coveredDays)
        assertEquals(42, result.forecastRecords)
        assertEquals(14, result.sampleCount(WeatherModel.GFS, BiasVariable.TEMPERATURE))
        coVerify(exactly = 1) {
            api.getPreviousDayOne(
                any(), any(), any(), any(), city.timezone!!,
                "2026-07-14", "2026-08-03", any(), any(), any()
            )
        }
    }

    @Test
    fun `bootstrap ECMWF HRES utilise la nouvelle source et accepte le suffixe placé avant previous day`() = runTest {
        val model = WeatherModel.ECMWF
        val modelsParam = slot<String>()
        val hourly = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        hourly["time"] = JsonArray(dayTimes(today.minusDays(1)))
        hourly["temperature_2m_${model.apiKey}_previous_day1"] = values(24) { 18.0 + it }
        hourly["precipitation_${model.apiKey}_previous_day1"] = values(24) { 0.0 }
        hourly["wind_speed_10m_${model.apiKey}_previous_day1"] = values(24) { 10.0 + it }
        coEvery {
            api.getPreviousDayOne(
                any(), any(), capture(modelsParam), any(), any(), any(), any(), any(), any(), any()
            )
        } returns PreviousRunsResponseDto(city.latitude, city.longitude, city.timezone!!, JsonObject(hourly))

        val result = useCase(city, listOf(model), today, requestedDays = 1)

        assertEquals("ecmwf_ifs", modelsParam.captured)
        assertEquals(1, result.coveredDays)
        assertEquals(3, result.forecastRecords)
        coVerify(exactly = 1) { repository.recordForecasts(match { it.size == 3 }) }
    }

    @Test
    fun `valeurs negatives de pluie et vent sont ignorees au lieu detre corrigees`() = runTest {
        val records = slot<List<ForecastBiasRecord>>()
        coEvery { repository.recordForecasts(capture(records)) } returns Unit
        coEvery { api.getPreviousDayOne(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            response(
                model = WeatherModel.GFS,
                days = 1,
                temperature = { _, hour -> 10.0 + hour },
                precipitation = { _, _ -> -1.0 },
                wind = { _, _ -> -5.0 }
            )

        val result = useCase(city, listOf(WeatherModel.GFS), today, requestedDays = 1)

        assertEquals(1, result.forecastRecords)
        assertEquals(BiasVariable.TEMPERATURE, records.captured.single().variable)
        assertEquals(0, result.sampleCount(WeatherModel.GFS, BiasVariable.PRECIPITATION))
        assertEquals(0, result.sampleCount(WeatherModel.GFS, BiasVariable.WIND_SPEED))
    }

    @Test
    fun `timeline tronquee a 18 heures est rejetee comme journee incomplete`() = runTest {
        val model = WeatherModel.GFS
        val date = today.minusDays(1)
        val hourly = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        hourly["time"] = JsonArray((0 until 18).map { hour ->
            JsonPrimitive("${date}T${hour.toString().padStart(2, '0')}:00")
        })
        hourly["temperature_2m_previous_day1_${model.apiKey}"] = values(18) { 15.0 }
        hourly["precipitation_previous_day1_${model.apiKey}"] = values(18) { 1.0 }
        hourly["wind_speed_10m_previous_day1_${model.apiKey}"] = values(18) { 20.0 }
        coEvery { api.getPreviousDayOne(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            PreviousRunsResponseDto(city.latitude, city.longitude, city.timezone!!, JsonObject(hourly))

        val result = useCase(city, listOf(model), today, requestedDays = 1)

        assertEquals(0, result.coveredDays)
        assertEquals(0, result.forecastRecords)
        coVerify(exactly = 1) { repository.recordForecasts(emptyList()) }
    }

    @Test
    fun `journee civile DST de 23 heures reste acceptee si toutes les heures sont presentes`() = runTest {
        val model = WeatherModel.GFS
        val date = today.minusDays(1)
        val hourly = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        hourly["time"] = JsonArray((0 until 23).map { hour ->
            JsonPrimitive("${date}T${hour.toString().padStart(2, '0')}:00")
        })
        hourly["temperature_2m_previous_day1_${model.apiKey}"] = values(23) { it.toDouble() }
        hourly["precipitation_previous_day1_${model.apiKey}"] = values(23) { 1.0 }
        hourly["wind_speed_10m_previous_day1_${model.apiKey}"] = values(23) { 20.0 + it }
        coEvery { api.getPreviousDayOne(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            PreviousRunsResponseDto(city.latitude, city.longitude, city.timezone!!, JsonObject(hourly))

        val result = useCase(city, listOf(model), today, requestedDays = 1)

        assertEquals(1, result.coveredDays)
        assertEquals(3, result.forecastRecords)
    }

    @Test
    fun `jour 18 heures sur 24 est ignore sans fabriquer un cumul partiel`() = runTest {
        coEvery { api.getPreviousDayOne(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            response(
                model = WeatherModel.GFS,
                days = 1,
                temperature = { _, hour -> if (hour < 18) 15.0 else null },
                precipitation = { _, hour -> if (hour < 18) 1.0 else null },
                wind = { _, hour -> if (hour < 18) 20.0 else null }
            )

        val result = useCase(city, listOf(WeatherModel.GFS), today, requestedDays = 1)

        assertEquals(0, result.coveredDays)
        assertEquals(0, result.forecastRecords)
        coVerify(exactly = 1) { repository.recordForecasts(emptyList()) }
    }

    private fun response(
        model: WeatherModel,
        days: Int,
        temperature: (day: Int, hour: Int) -> Double?,
        precipitation: (day: Int, hour: Int) -> Double?,
        wind: (day: Int, hour: Int) -> Double?
    ): PreviousRunsResponseDto {
        val firstDate = today.minusDays(days.toLong())
        val times = mutableListOf<JsonPrimitive>()
        val temperatures = mutableListOf<kotlinx.serialization.json.JsonElement>()
        val precipitationValues = mutableListOf<kotlinx.serialization.json.JsonElement>()
        val winds = mutableListOf<kotlinx.serialization.json.JsonElement>()
        repeat(days) { day ->
            val date = firstDate.plusDays(day.toLong())
            repeat(24) { hour ->
                times += JsonPrimitive("${date}T${hour.toString().padStart(2, '0')}:00")
                temperatures += temperature(day, hour).jsonValue()
                precipitationValues += precipitation(day, hour).jsonValue()
                winds += wind(day, hour).jsonValue()
            }
        }
        return PreviousRunsResponseDto(
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = city.timezone!!,
            hourly = JsonObject(
                mapOf(
                    "time" to JsonArray(times),
                    "temperature_2m_previous_day1_${model.apiKey}" to JsonArray(temperatures),
                    "precipitation_previous_day1_${model.apiKey}" to JsonArray(precipitationValues),
                    "wind_speed_10m_previous_day1_${model.apiKey}" to JsonArray(winds)
                )
            )
        )
    }

    private fun dayTimes(date: LocalDate): List<JsonPrimitive> =
        (0 until 24).map { hour ->
            JsonPrimitive("${date}T${hour.toString().padStart(2, '0')}:00")
        }

    private fun values(size: Int, value: (Int) -> Double): JsonArray =
        JsonArray((0 until size).map { JsonPrimitive(value(it)) })

    private fun Double?.jsonValue(): kotlinx.serialization.json.JsonElement =
        this?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull
}
