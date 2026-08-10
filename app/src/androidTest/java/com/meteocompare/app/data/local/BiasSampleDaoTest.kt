package com.meteocompare.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BiasSampleDaoTest {
    private lateinit var database: MeteoCompareDatabase
    private lateinit var dao: BiasSampleDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeteoCompareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.biasSampleDao()
    }

    @After fun tearDown() = database.close()

    @Test
    fun join_requires_matching_observation_and_orders_newest_issue_first() = runTest {
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "TEMPERATURE", 100L, 1_000L, 18.0))
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "TEMPERATURE", 100L, 2_000L, 19.0))
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "TEMPERATURE", 101L, 2_000L, 20.0))
        dao.insertObservation(ObservationSampleEntity("paris", "TEMPERATURE", 100L, 21.0, 3_000L))

        val rows = dao.observeJoinedSamples("paris", "GFS", "TEMPERATURE", 90L, 110L).first()
        assertEquals(2, rows.size)
        assertEquals(19.0, rows[0].forecast, 0.0)
        assertEquals(18.0, rows[1].forecast, 0.0)
        assertEquals(21.0, rows[0].observation, 0.0)
    }

    @Test
    fun missing_reference_query_detects_internal_gaps_and_respects_upper_bound() = runTest {
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "WIND_SPEED", 10L, 1L, 20.0))
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "TEMPERATURE", 10L, 1L, 18.0))
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "WIND_SPEED", 20L, 2L, 21.0))
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "WIND_SPEED", 30L, 3L, 22.0))
        dao.insertObservation(ObservationSampleEntity("paris", "WIND_SPEED", 20L, 20.0, 2L))
        dao.insertObservation(ObservationSampleEntity("paris", "TEMPERATURE", 10L, 17.0, 2L))

        // Le vent du jour 10 manque encore, malgré une référence plus récente au jour 20.
        assertEquals(10L, dao.getEarliestMissingReferenceEpochDay("paris", 20L))
        // Le forecast futur au jour 30 n'entre pas encore dans la recherche.
        assertEquals(null, dao.getEarliestMissingReferenceEpochDay("paris", 9L))

        dao.insertObservation(ObservationSampleEntity("paris", "WIND_SPEED", 10L, 19.0, 3L))
        assertEquals(null, dao.getEarliestMissingReferenceEpochDay("paris", 20L))
        assertEquals(30L, dao.getEarliestMissingReferenceEpochDay("paris", 30L))
    }

    @Test
    fun purge_respects_boundaries() = runTest {
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "WIND_SPEED", 10L, 1L, 20.0))
        dao.insertForecast(ForecastSampleEntity("paris", "GFS", "WIND_SPEED", 20L, 2L, 21.0))
        dao.insertObservation(ObservationSampleEntity("paris", "WIND_SPEED", 10L, 19.0, 1L))
        dao.insertObservation(ObservationSampleEntity("paris", "WIND_SPEED", 20L, 20.0, 2L))

        dao.purgeForecastsBefore(20L)
        dao.purgeObservationsBefore(20L)
        val rows = dao.observeJoinedSamples("paris", "GFS", "WIND_SPEED", 0L, 30L).first()
        assertEquals(1, rows.size)
        assertEquals(20L, rows.single().targetDateEpochDay)
    }

    @Test
    fun batch_inserts_are_visible_as_one_consistent_dataset() = runTest {
        dao.insertForecasts(
            listOf(
                ForecastSampleEntity("paris", "GFS", "TEMPERATURE", 100L, 1_000L, 18.0),
                ForecastSampleEntity("paris", "GFS", "TEMPERATURE", 101L, 1_000L, 19.0)
            )
        )
        dao.insertObservations(
            listOf(
                ObservationSampleEntity("paris", "TEMPERATURE", 100L, 20.0, 2_000L),
                ObservationSampleEntity("paris", "TEMPERATURE", 101L, 21.0, 2_000L)
            )
        )

        val rows = dao.observeJoinedSamples("paris", "GFS", "TEMPERATURE", 90L, 110L).first()
        assertEquals(2, rows.size)
        assertEquals(listOf(100L, 101L), rows.map { it.targetDateEpochDay })
    }
}
