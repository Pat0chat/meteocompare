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
import java.time.Instant
import java.time.LocalDate

/**
 * Tests de [SnapshotForecastUseCase] avec un fake repo qui enregistre les
 * appels reçus. Objectif : vérifier ce qui est écrit et ce qui est skippé.
 *
 * Cas couverts :
 *   1. Snapshot complet d'un forecast valide → une row par (modèle, variable,
 *      jour) qui a une valeur non-null.
 *   2. Valeurs `null` skippées silencieusement — pas d'exception.
 *   3. Fenêtre de sanité : dates aberrantes rejetées.
 *   4. Listes désalignées (données corrompues) : modèle skippé sans crash.
 *   5. Idempotence contract : même forecast snapshotté 2x = mêmes rows
 *      (via REPLACE côté DB, testé au niveau contract avec un fake qui
 *      capture chaque appel).
 */
class SnapshotForecastUseCaseTest {

    private val fakeRepo = FakeBiasSampleRepository()
    private val useCase = SnapshotForecastUseCase(fakeRepo)

    private val today = LocalDate.of(2024, 7, 15)
    private val issuedAt = Instant.parse("2024-07-15T08:30:00Z")

    // ─── Snapshot nominal ────────────────────────────────────────────────

    @Test
    fun `snapshot records one row per variable per day per model`() = runTest {
        val forecast = buildForecast(
            models = mapOf(
                WeatherModel.GFS to dailyOf(
                    dates = listOf(today, today.plusDays(1)),
                    tempMax = listOf(25.0, 26.5),
                    precip = listOf(0.0, 1.2),
                    wind = listOf(15.0, 22.0)
                ),
                WeatherModel.ECMWF to dailyOf(
                    dates = listOf(today, today.plusDays(1)),
                    tempMax = listOf(24.5, 26.0),
                    precip = listOf(0.1, 0.9),
                    wind = listOf(14.0, 20.0)
                )
            )
        )

        useCase(forecast, issuedAt = issuedAt, today = today)

        // 2 modèles × 3 variables × 2 jours = 12 rows attendues
        assertEquals(12, fakeRepo.forecastRecords.size)
    }

    @Test
    fun `snapshot preserves each variable value correctly`() = runTest {
        val forecast = buildForecast(
            models = mapOf(
                WeatherModel.GFS to dailyOf(
                    dates = listOf(today),
                    tempMax = listOf(28.7),
                    precip = listOf(3.5),
                    wind = listOf(42.0)
                )
            )
        )
        useCase(forecast, issuedAt = issuedAt, today = today)

        val recorded = fakeRepo.forecastRecords.associate { it.variable to it.value }
        assertEquals(28.7, recorded[BiasVariable.TEMPERATURE]!!, 1e-9)
        assertEquals(3.5, recorded[BiasVariable.PRECIPITATION]!!, 1e-9)
        assertEquals(42.0, recorded[BiasVariable.WIND_SPEED]!!, 1e-9)
    }

    // ─── Robustesse : valeurs null ───────────────────────────────────────

    @Test
    fun `null values in the daily series are skipped silently`() = runTest {
        val forecast = buildForecast(
            models = mapOf(
                WeatherModel.GFS to dailyOf(
                    dates = listOf(today, today.plusDays(1)),
                    tempMax = listOf(25.0, null),      // J+1 sans temp
                    precip = listOf(null, 1.2),         // J sans precip
                    wind = listOf(15.0, 22.0)           // les deux ok
                )
            )
        )
        useCase(forecast, issuedAt = issuedAt, today = today)

        // Attendu : 2 temp - 1 null + 2 precip - 1 null + 2 wind = 4 rows
        assertEquals(4, fakeRepo.forecastRecords.size)
    }

    @Test
    fun `model with only some variables recorded when others are all null`() = runTest {
        val forecast = buildForecast(
            models = mapOf(
                WeatherModel.GFS to dailyOf(
                    dates = listOf(today),
                    tempMax = listOf(25.0),
                    precip = listOf(null),
                    wind = listOf(null)
                )
            )
        )
        useCase(forecast, issuedAt = issuedAt, today = today)

        assertEquals(1, fakeRepo.forecastRecords.size)
        assertEquals(BiasVariable.TEMPERATURE, fakeRepo.forecastRecords.first().variable)
    }

    // ─── Fenêtre de sanité ────────────────────────────────────────────────

    @Test
    fun `dates outside sanity window are rejected`() = runTest {
        val forecast = buildForecast(
            models = mapOf(
                WeatherModel.GFS to dailyOf(
                    dates = listOf(
                        today.minusDays(100), // hors fenêtre (< -35)
                        today,                 // dans la fenêtre
                        today.plusDays(20)     // hors fenêtre (> +10)
                    ),
                    tempMax = listOf(25.0, 26.0, 27.0),
                    precip = listOf(1.0, 2.0, 3.0),
                    wind = listOf(15.0, 20.0, 25.0)
                )
            )
        )
        useCase(forecast, issuedAt = issuedAt, today = today)

        // Seul le jour dans la fenêtre est enregistré → 3 variables = 3 rows
        assertEquals(3, fakeRepo.forecastRecords.size)
        assertTrue(fakeRepo.forecastRecords.all { it.targetDate == today })
    }

    // ─── Robustesse : listes désalignées ─────────────────────────────────

    @Test
    fun `misaligned lists skip the model without crashing`() = runTest {
        val forecast = buildForecast(
            models = mapOf(
                WeatherModel.GFS to DailyForecast(
                    dates = listOf(today, today.plusDays(1)),
                    tempMax = listOf(25.0), // taille 1, dates taille 2 → désaligné
                    tempMin = listOf(15.0),
                    precipitationSum = listOf(1.0, 2.0),
                    windSpeedMax = listOf(15.0, 20.0)
                ),
                WeatherModel.ECMWF to dailyOf(
                    dates = listOf(today),
                    tempMax = listOf(24.0),
                    precip = listOf(0.5),
                    wind = listOf(14.0)
                )
            )
        )
        useCase(forecast, issuedAt = issuedAt, today = today)

        // GFS entièrement skippé (misaligné), ECMWF ok → 3 rows
        assertEquals(3, fakeRepo.forecastRecords.size)
        assertTrue(fakeRepo.forecastRecords.all { it.model == WeatherModel.ECMWF })
    }

    // ─── Contract d'idempotence ───────────────────────────────────────────

    @Test
    fun `snapshotting the same forecast twice records the same set of rows`() = runTest {
        val forecast = buildForecast(
            models = mapOf(
                WeatherModel.GFS to dailyOf(
                    dates = listOf(today),
                    tempMax = listOf(25.0),
                    precip = listOf(1.0),
                    wind = listOf(15.0)
                )
            )
        )
        useCase(forecast, issuedAt = issuedAt, today = today)
        val first = fakeRepo.forecastRecords.toList()
        useCase(forecast, issuedAt = issuedAt, today = today)

        assertEquals(
            "REPLACE côté DB rendra le résultat identique — au niveau contract " +
                    "on doit voir le double d'appels, mais mêmes cibles.",
            first.size * 2, fakeRepo.forecastRecords.size
        )
        val secondHalf = fakeRepo.forecastRecords.drop(first.size)
        assertEquals(first, secondHalf)
    }

    // ─── Cas dégénérés ────────────────────────────────────────────────────

    @Test
    fun `empty forecast records nothing`() = runTest {
        val forecast = buildForecast(models = emptyMap())
        useCase(forecast, issuedAt = issuedAt, today = today)
        assertTrue(fakeRepo.forecastRecords.isEmpty())
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private fun buildForecast(models: Map<WeatherModel, DailyForecast>): CityForecast {
        return CityForecast(
            city = City(
                id = "paris",
                name = "Paris",
                latitude = 48.85,
                longitude = 2.35,
                country = "France"
            ),
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
    }

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

    /**
     * Fake in-memory qui capture chaque `recordForecast` en ordre chronologique
     * d'appel. Les autres méthodes (observations, purge, observe) ne sont pas
     * utilisées par ce use case — elles jettent pour signaler un mauvais appel.
     */
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
            forecastRecords += ForecastRecord(cityId, model, variable, targetDate, issuedAt, value)
        }

        override fun observeSamples(
            cityId: String,
            model: WeatherModel,
            variable: BiasVariable,
            windowDays: Int
        ): Flow<List<BiasSample>> = flowOf(emptyList())

        override suspend fun recordObservation(
            cityId: String, variable: BiasVariable, targetDate: LocalDate, value: Double
        ) = error("Not expected in SnapshotForecastUseCase tests")

        override suspend fun latestObservationDate(
            cityId: String, variable: BiasVariable
        ) = error("Not expected in SnapshotForecastUseCase tests")

        override suspend fun countPastForecastSamples(
            cityId: String, beforeDate: LocalDate
        ) = error("Not expected in SnapshotForecastUseCase tests")

        override suspend fun purgeOlderThan(beforeDate: LocalDate) =
            error("Not expected in SnapshotForecastUseCase tests")
    }
}