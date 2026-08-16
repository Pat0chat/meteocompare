package com.meteocompare.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForecastEvolutionDaoTest {
    private lateinit var database: MeteoCompareDatabase
    private lateinit var dao: ForecastEvolutionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeteoCompareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.forecastEvolutionDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun one_bucket_keeps_one_coherent_model_set() = runTest {
        assertTrue(
            dao.insertSnapshotBucketIfAbsent(
                cityId = "paris",
                snapshotBucket = 100L,
                samples = listOf(
                    entity("paris", "GFS", "TEMPERATURE", 1L, 100L, 1_000L, 20.0),
                    entity("paris", "ECMWF", "TEMPERATURE", 1L, 100L, 1_000L, 21.0)
                )
            )
        )

        // Un second refresh dans la même tranche ne fusionne pas un autre jeu de modèles.
        assertFalse(
            dao.insertSnapshotBucketIfAbsent(
                cityId = "paris",
                snapshotBucket = 100L,
                samples = listOf(
                    entity("paris", "ECMWF", "TEMPERATURE", 1L, 100L, 2_000L, 22.0),
                    entity("paris", "ICON_GLOBAL", "TEMPERATURE", 1L, 100L, 2_000L, 23.0)
                )
            )
        )

        val rows = dao.getHistoryWindow("paris", listOf("GFS", "ECMWF", "ICON_GLOBAL"), 1L, 1L, 100L, 100L)
        assertEquals(setOf("GFS", "ECMWF"), rows.map { it.modelKey }.toSet())
        assertTrue(rows.all { it.snapshotAtEpochMs == 1_000L })
    }

    @Test
    fun different_buckets_are_retained() = runTest {
        assertTrue(dao.insertSnapshotBucketIfAbsent(
            "paris", 99L,
            listOf(entity("paris", "GFS", "WIND", 1L, 99L, 900L, 20.0))
        ))
        assertTrue(dao.insertSnapshotBucketIfAbsent(
            "paris", 100L,
            listOf(entity("paris", "GFS", "WIND", 1L, 100L, 1_000L, 25.0))
        ))

        val rows = dao.getHistoryWindow("paris", listOf("GFS"), 1L, 1L, 99L, 100L)
        assertEquals(listOf(99L, 100L), rows.map { it.snapshotBucket })
    }

    @Test
    fun purge_and_delete_city_are_scoped() = runTest {
        dao.insertAll(
            listOf(
                entity("paris", "GFS", "WIND", 1L, 1L, 1_000L, 20.0),
                entity("paris", "GFS", "WIND", 1L, 2L, 3_000L, 25.0),
                entity("lyon", "GFS", "WIND", 1L, 2L, 3_000L, 30.0)
            )
        )

        dao.purgeCapturedBefore(2_000L)
        assertEquals(1, dao.getHistoryWindow("paris", listOf("GFS"), 1L, 1L, 0L, 10L).size)

        dao.deleteForCity("paris")
        assertTrue(dao.getHistoryWindow("paris", listOf("GFS"), 1L, 1L, 0L, 10L).isEmpty())
        assertEquals(1, dao.getHistoryWindow("lyon", listOf("GFS"), 1L, 1L, 0L, 10L).size)
    }

    private fun entity(
        cityId: String,
        modelKey: String,
        variable: String,
        date: Long,
        bucket: Long,
        capturedAt: Long,
        value: Double
    ) = ForecastEvolutionEntity(
        cityId = cityId,
        modelKey = modelKey,
        variable = variable,
        targetDateEpochDay = date,
        snapshotBucket = bucket,
        snapshotAtEpochMs = capturedAt,
        value = value
    )
}
