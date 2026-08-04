package com.meteocompare.app.ui.citydetail

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meteocompare.app.data.local.MeteoCompareDatabase
import com.meteocompare.app.data.repository.BiasSampleRepositoryImpl
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.WeatherModel
import com.meteocompare.app.domain.repository.ForecastBiasRecord
import com.meteocompare.app.domain.repository.ObservationBiasRecord
import com.meteocompare.app.domain.usecase.ComputeBiasUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/** Vérifie Room → biais → classements → données des pages par variable. */
class HistoricalBiasPipelineIntegrationTest {

    private lateinit var database: MeteoCompareDatabase
    private lateinit var repository: BiasSampleRepositoryImpl
    private val computeBias = ComputeBiasUseCase()
    private val asOf = LocalDate.of(2026, 8, 4)
    private val zone = ZoneId.of("Europe/Paris")
    private val models = listOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_GLOBAL)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeteoCompareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BiasSampleRepositoryImpl(
            dao = database.biasSampleDao(),
            io = Dispatchers.Unconfined,
            clock = Clock.fixed(Instant.parse("2026-08-04T12:00:00Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun bootstrap_data_feeds_bias_rankings_and_variable_pages() = runTest {
        seedFourteenComparableDays()

        val temperature = stateFor(BiasVariable.TEMPERATURE)
        val precipitation = stateFor(BiasVariable.PRECIPITATION)
        val wind = stateFor(BiasVariable.WIND_SPEED)
        val state = BiasScreenState(temperature, precipitation, wind)

        models.forEach { model ->
            assertEquals(14, temperature.historyByModel.getValue(model).size)
            assertEquals(14, precipitation.historyByModel.getValue(model).size)
            assertEquals(14, wind.historyByModel.getValue(model).size)
            assertNotNull(temperature.biasByModel[model])
            assertNotNull(precipitation.biasByModel[model])
            assertNotNull(wind.biasByModel[model])
        }

        assertEquals(0.5, temperature.biasByModel.getValue(WeatherModel.ECMWF)!!.meanBias, 0.0001)
        assertEquals(0.2, precipitation.biasByModel.getValue(WeatherModel.GFS)!!.meanBias, 0.0001)
        assertEquals(1.0, wind.biasByModel.getValue(WeatherModel.ICON_GLOBAL)!!.meanBias, 0.0001)

        val rankings = buildLocalModelRankings(state)
        assertEquals(WeatherModel.ECMWF, rankings.temperature.winner?.model)
        assertEquals(WeatherModel.GFS, rankings.precipitation.winner?.model)
        assertEquals(WeatherModel.ICON_GLOBAL, rankings.wind.winner?.model)
        listOf(rankings.temperature, rankings.precipitation, rankings.wind).forEach { ranking ->
            assertEquals(3, ranking.entries.size)
            ranking.entries.forEach { assertEquals(14, it.reliability.sampleSize) }
        }

        assertPage(buildBiasSelection(WeatherModel.ECMWF, BiasVariable.TEMPERATURE, temperature))
        assertPage(buildBiasSelection(WeatherModel.GFS, BiasVariable.PRECIPITATION, precipitation))
        assertPage(buildBiasSelection(WeatherModel.ICON_GLOBAL, BiasVariable.WIND_SPEED, wind))
    }


    @Test
    fun room_flow_reemits_after_bootstrap_insertions() = runTest {
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeSamples(
                cityId = CITY_ID,
                model = WeatherModel.GFS,
                variable = BiasVariable.TEMPERATURE,
                asOf = asOf,
                timezone = zone.id,
                windowDays = 30
            ).first { samples -> samples.size == 14 }
        }

        seedFourteenComparableDays()

        assertEquals(14, observed.await().size)
    }

    private suspend fun stateFor(variable: BiasVariable): VariableBiasState {
        val historyByModel = models.associateWith { model ->
            repository.observeSamples(
                cityId = CITY_ID,
                model = model,
                variable = variable,
                asOf = asOf,
                timezone = zone.id,
                windowDays = 30
            ).first()
        }
        val biasByModel = historyByModel.mapValues { (_, samples) ->
            computeBias(variable, samples, asOf, windowDays = 30)
        }
        val values = historyByModel.values.flatten().flatMap { listOf(it.forecast, it.observation) }
        return VariableBiasState(
            biasByModel = biasByModel,
            historyByModel = historyByModel,
            yDomainMin = values.minOrNull()?.minus(1.0),
            yDomainMax = values.maxOrNull()?.plus(1.0)
        )
    }

    private suspend fun seedFourteenComparableDays() {
        val forecasts = mutableListOf<ForecastBiasRecord>()
        val observations = mutableListOf<ObservationBiasRecord>()
        repeat(14) { index ->
            val date = asOf.minusDays((14 - index).toLong())
            val issuedAt = date.minusDays(1).atStartOfDay(zone).toInstant()
            val observedTemperature = 18.0 + (index % 4)
            val observedRain = if (index % 3 == 0) 4.0 else 0.0
            val observedWind = 20.0 + (index % 5)

            observations += ObservationBiasRecord(
                CITY_ID, BiasVariable.TEMPERATURE, date, observedTemperature, FETCHED_AT
            )
            observations += ObservationBiasRecord(
                CITY_ID, BiasVariable.PRECIPITATION, date, observedRain, FETCHED_AT
            )
            observations += ObservationBiasRecord(
                CITY_ID, BiasVariable.WIND_SPEED, date, observedWind, FETCHED_AT
            )

            models.forEach { model ->
                forecasts += ForecastBiasRecord(
                    CITY_ID, model, BiasVariable.TEMPERATURE, date, issuedAt,
                    observedTemperature + temperatureError(model)
                )
                forecasts += ForecastBiasRecord(
                    CITY_ID, model, BiasVariable.PRECIPITATION, date, issuedAt,
                    observedRain + precipitationError(model)
                )
                forecasts += ForecastBiasRecord(
                    CITY_ID, model, BiasVariable.WIND_SPEED, date, issuedAt,
                    observedWind + windError(model)
                )
            }
        }
        repository.recordForecasts(forecasts)
        repository.recordObservations(observations)
    }

    private fun assertPage(selection: BiasSelection?) {
        assertNotNull(selection)
        selection!!
        assertEquals(1, selection.localRank?.rank)
        assertEquals(3, selection.localRank?.modelCount)
        assertEquals(14, selection.reliability.sampleSize)
        assertEquals(14, selection.bias.sampleSize)
        assertEquals(14, selection.dailyForecast.size)
        assertEquals(14, selection.dailyObservation.size)
        assertNotNull(selection.multiModelReliability)
        assertEquals(14, selection.multiModelReliability?.sampleSize)
    }

    private fun temperatureError(model: WeatherModel): Double = when (model) {
        WeatherModel.ECMWF -> 0.5
        WeatherModel.GFS -> 1.0
        else -> 2.0
    }

    private fun precipitationError(model: WeatherModel): Double = when (model) {
        WeatherModel.GFS -> 0.2
        WeatherModel.ICON_GLOBAL -> 0.5
        else -> 1.0
    }

    private fun windError(model: WeatherModel): Double = when (model) {
        WeatherModel.ICON_GLOBAL -> 1.0
        WeatherModel.ECMWF -> 2.0
        else -> 3.0
    }

    companion object {
        private const val CITY_ID = "paris"
        private val FETCHED_AT = Instant.parse("2026-08-04T12:00:00Z")
    }
}
