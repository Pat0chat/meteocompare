package com.meteocompare.app.domain.usecase

import com.meteocompare.app.data.remote.HistoricalForecastApi
import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.City
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Tests de [BackfillHistoricalForecastUseCase].
 *
 * Cinq axes de couverture :
 *   1. **Idempotence** — skip si des rows passées existent déjà, no-op si
 *      liste de modèles vide.
 *   2. **Fenêtre temporelle** — appel HTTP avec `start = today-30, end = today-1`
 *      (borne inclusive côté ancien, exclusive côté today).
 *   3. **Parsing multi-modèles** — chaque `variable_${apiKey}` correctement
 *      extraite et enregistrée.
 *   4. **Robustesse** — modèles absents de la réponse, valeurs null inline,
 *      listes de longueurs différentes, dates malformées.
 *   5. **Cardinal des enregistrements** — nombre de rows insérées correspond
 *      exactement à `Σ (jours × variables non-null par modèle présent)`.
 *
 * Toutes les dates ancrées sur `TODAY` fixe pour l'indépendance temporelle.
 */
class BackfillHistoricalForecastUseCaseTest {

    private lateinit var api: HistoricalForecastApi
    private lateinit var repo: BiasSampleRepository
    private lateinit var useCase: BackfillHistoricalForecastUseCase

    private val today = LocalDate.of(2024, 7, 15)
    private val paris = City(id = "1", name = "Paris", country = "FR",
        latitude = 48.85, longitude = 2.35)

    @Before
    fun setUp() {
        api = mockk()
        repo = mockk(relaxed = true)
        coEvery { repo.recordForecasts(any()) } coAnswers {
            firstArg<List<ForecastBiasRecord>>().forEach { record ->
                repo.recordForecast(
                    record.cityId, record.model, record.variable,
                    record.targetDate, record.issuedAt, record.value
                )
            }
        }
        useCase = BackfillHistoricalForecastUseCase(
            historicalApi = api,
            biasRepository = repo,
            io = Dispatchers.Unconfined
        )
    }

    // ─── Idempotence ─────────────────────────────────────────────────────

    @Test
    fun `skip si liste de modèles vide — aucun appel API`() = runTest {
        val n = useCase(paris, emptyList(), today)
        assertEquals(0, n)
        coVerify(exactly = 0) {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `skip si countPastForecastDays atteint le seuil`() = runTest {
        coEvery { repo.countPastForecastDays(paris.id, any(), today) } returns 5

        val n = useCase(paris, listOf(WeatherModel.GFS), today)

        assertEquals(0, n)
        coVerify(exactly = 0) {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `procède si countPastForecastDays juste sous le seuil`() = runTest {
        coEvery { repo.countPastForecastDays(paris.id, any(), today) } returns 4
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns emptyResponse()

        useCase(paris, listOf(WeatherModel.GFS), today)

        coVerify(exactly = 1) {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `un modele nouvellement active est backfille sans refaire les modeles deja couverts`() = runTest {
        coEvery {
            repo.countPastForecastDays(paris.id, WeatherModel.GFS, today)
        } returns 5
        coEvery {
            repo.countPastForecastDays(paris.id, WeatherModel.ICON_EU, today)
        } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns responseWithMultiple(
            dates = listOf("2024-06-15"),
            perModel = mapOf("icon_eu" to Triple(21.0, 0.5, 12.0))
        )

        val recorded = useCase(
            paris,
            listOf(WeatherModel.GFS, WeatherModel.ICON_EU),
            today
        )

        assertEquals(3, recorded)
        coVerify {
            api.getHistoricalForecast(
                any(), any(), models = "icon_eu",
                any(), any(), any(), any(), any(), any(), any()
            )
        }
        coVerify(exactly = 0) {
            repo.recordForecast(
                paris.id, WeatherModel.GFS, any(), any(), any(), any()
            )
        }
    }

    // ─── Fenêtre temporelle ──────────────────────────────────────────────

    @Test
    fun `fenêtre — start = today-30 jusqu'à end = today-1 (inclusive)`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns emptyResponse()

        useCase(paris, listOf(WeatherModel.GFS), today)

        coVerify {
            api.getHistoricalForecast(
                latitude = paris.latitude,
                longitude = paris.longitude,
                models = "gfs_seamless",
                startDate = "2024-06-15",  // today - 30
                endDate = "2024-07-14",    // today - 1
                daily = any(),
                timezone = any(),
                windSpeedUnit = any(),
                temperatureUnit = any(),
                precipitationUnit = any()
            )
        }
    }

    @Test
    fun `models query concatène les apiKey triés dans l'ordre fourni`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns emptyResponse()

        useCase(paris, listOf(WeatherModel.ICON_EU, WeatherModel.GFS), today)

        // Ordre préservé (le use case ne trie pas — c'est la responsabilité
        // du caller si un ordre canonique est souhaité).
        coVerify {
            api.getHistoricalForecast(
                any(), any(),
                models = "icon_eu,gfs_seamless",
                any(), any(), any(), any(), any(), any(), any()
            )
        }
    }

    // ─── Parsing + enregistrement ────────────────────────────────────────

    @Test
    fun `enregistre un sample par (modèle, variable, date) présente`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns responseWithGfs(
            dates = listOf("2024-06-15", "2024-06-16"),
            tempMax = listOf(22.5, 23.0),
            precipSum = listOf(0.0, 1.5),
            windMax = listOf(12.0, 15.0)
        )

        val recorded = useCase(paris, listOf(WeatherModel.GFS), today)

        // 2 dates × 3 variables = 6 rows attendues
        assertEquals(6, recorded)

        // Vérifie qu'on a bien appelé recordForecast pour chaque paire
        coVerify(exactly = 2) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.TEMPERATURE,
                any(), any(), any())
        }
        coVerify(exactly = 2) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.PRECIPITATION,
                any(), any(), any())
        }
        coVerify(exactly = 2) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.WIND_SPEED,
                any(), any(), any())
        }
    }

    @Test
    fun `parse plusieurs modèles indépendamment`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns responseWithMultiple(
            dates = listOf("2024-06-15"),
            perModel = mapOf(
                "gfs_seamless" to Triple(20.0, 0.0, 10.0),
                "icon_eu" to Triple(21.0, 0.5, 12.0)
            )
        )

        val recorded = useCase(paris, listOf(WeatherModel.GFS, WeatherModel.ICON_EU), today)

        // 1 date × 3 vars × 2 modèles = 6 rows
        assertEquals(6, recorded)
        coVerify(exactly = 1) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.TEMPERATURE, any(), any(), any())
        }
        coVerify(exactly = 1) {
            repo.recordForecast(paris.id, WeatherModel.ICON_EU, BiasVariable.TEMPERATURE, any(), any(), any())
        }
    }

    // ─── Robustesse ──────────────────────────────────────────────────────

    @Test
    fun `modèle absent de la réponse ne fait pas planter les autres`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns responseWithGfs(  // seul GFS présent
            dates = listOf("2024-06-15"),
            tempMax = listOf(22.0),
            precipSum = listOf(0.0),
            windMax = listOf(10.0)
        )

        // On demande GFS + AROME (AROME absent de la réponse)
        val recorded = useCase(paris,
            listOf(WeatherModel.GFS, WeatherModel.AROME_FRANCE_HD), today)

        // Seul GFS produit des rows (3), AROME est silencieusement skippé
        assertEquals(3, recorded)
        coVerify(exactly = 0) {
            repo.recordForecast(any(), WeatherModel.AROME_FRANCE_HD, any(), any(), any(), any())
        }
    }

    @Test
    fun `valeurs null inline dans le tableau ne font pas planter`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns BatchedForecastResponseDto(
            latitude = paris.latitude,
            longitude = paris.longitude,
            timezone = "Europe/Paris",
            daily = buildJsonObject {
                put("time", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("2024-06-15"))
                    add(kotlinx.serialization.json.JsonPrimitive("2024-06-16"))
                })
                put("temperature_2m_max_gfs_seamless", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(22.0))
                    add(kotlinx.serialization.json.JsonNull) // null au jour 2
                })
                put("precipitation_sum_gfs_seamless", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(0.0))
                    add(kotlinx.serialization.json.JsonPrimitive(1.0))
                })
                put("wind_speed_10m_max_gfs_seamless", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(10.0))
                    add(kotlinx.serialization.json.JsonPrimitive(12.0))
                })
            }
        )

        val recorded = useCase(paris, listOf(WeatherModel.GFS), today)

        // 6 slots au total - 1 null température = 5 rows
        assertEquals(5, recorded)
        coVerify(exactly = 1) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.TEMPERATURE,
                any(), any(), any())
        }
    }

    @Test
    fun `réponse sans daily object retourne 0`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns BatchedForecastResponseDto(
            latitude = paris.latitude,
            longitude = paris.longitude,
            timezone = "Europe/Paris",
            daily = null
        )

        val recorded = useCase(paris, listOf(WeatherModel.GFS), today)

        assertEquals(0, recorded)
        coVerify(exactly = 0) {
            repo.recordForecast(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `date malformée dans time skippée, les autres continuent`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns responseWithGfs(
            dates = listOf("2024-06-15", "not-a-date", "2024-06-17"),
            tempMax = listOf(22.0, 23.0, 24.0),
            precipSum = listOf(0.0, 0.0, 0.0),
            windMax = listOf(10.0, 11.0, 12.0)
        )

        val recorded = useCase(paris, listOf(WeatherModel.GFS), today)

        // 2 dates parseables × 3 variables = 6 rows (celle du milieu skippée)
        assertEquals(6, recorded)
    }

    @Test
    fun `longueurs différentes conservent les valeurs disponibles de chaque variable`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns responseWithGfs(
            dates = listOf("2024-06-15", "2024-06-16", "2024-06-17"),
            tempMax = listOf(22.0, 23.0),           // 2 seulement
            precipSum = listOf(0.0, 0.0, 0.0),      // 3
            windMax = listOf(10.0, 11.0, 12.0)      // 3
        )

        val recorded = useCase(paris, listOf(WeatherModel.GFS), today)

        // Température : 2 valeurs, pluie : 3, vent : 3 → 8 rows.
        assertEquals(8, recorded)
    }


    @Test
    fun `variable absente ne supprime pas les autres séries valides`() = runTest {
        coEvery { repo.countPastForecastDays(any(), any(), any()) } returns 0
        coEvery {
            api.getHistoricalForecast(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns BatchedForecastResponseDto(
            latitude = paris.latitude,
            longitude = paris.longitude,
            timezone = "Europe/Paris",
            daily = buildJsonObject {
                put("time", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("2024-06-15"))
                    add(kotlinx.serialization.json.JsonPrimitive("2024-06-16"))
                })
                put("temperature_2m_max_gfs_seamless", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(22.0))
                    add(kotlinx.serialization.json.JsonPrimitive(23.0))
                })
                // precipitation_sum volontairement absente
                put("wind_speed_10m_max_gfs_seamless", buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive(10.0))
                    add(kotlinx.serialization.json.JsonPrimitive(12.0))
                })
            }
        )

        val recorded = useCase(paris, listOf(WeatherModel.GFS), today)

        assertEquals(4, recorded)
        coVerify(exactly = 2) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.TEMPERATURE,
                any(), any(), any())
        }
        coVerify(exactly = 0) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.PRECIPITATION,
                any(), any(), any())
        }
        coVerify(exactly = 2) {
            repo.recordForecast(paris.id, WeatherModel.GFS, BiasVariable.WIND_SPEED,
                any(), any(), any())
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun emptyResponse() = BatchedForecastResponseDto(
        latitude = paris.latitude,
        longitude = paris.longitude,
        timezone = "Europe/Paris",
        daily = buildJsonObject {
            put("time", buildJsonArray { })
        }
    )

    private fun responseWithGfs(
        dates: List<String>,
        tempMax: List<Double>,
        precipSum: List<Double>,
        windMax: List<Double>
    ) = BatchedForecastResponseDto(
        latitude = paris.latitude,
        longitude = paris.longitude,
        timezone = "Europe/Paris",
        daily = buildDaily(mapOf(
            "gfs_seamless" to Triple(tempMax, precipSum, windMax)
        ), dates)
    )

    private fun responseWithMultiple(
        dates: List<String>,
        perModel: Map<String, Triple<Double, Double, Double>>  // apiKey → (tempMax, precipSum, windMax)
    ) = BatchedForecastResponseDto(
        latitude = paris.latitude,
        longitude = paris.longitude,
        timezone = "Europe/Paris",
        daily = buildDaily(
            perModel.mapValues { (_, v) ->
                Triple(
                    dates.map { v.first },
                    dates.map { v.second },
                    dates.map { v.third }
                )
            },
            dates
        )
    )

    private fun buildDaily(
        perApiKey: Map<String, Triple<List<Double>, List<Double>, List<Double>>>,
        dates: List<String>
    ): JsonObject = buildJsonObject {
        put("time", buildJsonArray {
            dates.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
        })
        perApiKey.forEach { (apiKey, values) ->
            val (temps, precips, winds) = values
            put("temperature_2m_max_$apiKey", buildJsonArray {
                temps.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("precipitation_sum_$apiKey", buildJsonArray {
                precips.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("wind_speed_10m_max_$apiKey", buildJsonArray {
                winds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        }
    }
}