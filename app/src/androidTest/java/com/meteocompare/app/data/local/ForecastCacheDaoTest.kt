package com.meteocompare.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForecastCacheDaoTest {
    private lateinit var database: MeteoCompareDatabase
    private lateinit var dao: ForecastCacheDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeteoCompareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.forecastCacheDao()
    }

    @After fun tearDown() = database.close()

    @Test
    fun upsert_replaces_same_city_model_and_keeps_other_models() = runTest {
        dao.upsertAll(
            listOf(
                ForecastCacheEntity("paris", "gfs", 10L, "old"),
                ForecastCacheEntity("paris", "icon", 11L, "icon")
            )
        )
        dao.upsertAll(listOf(ForecastCacheEntity("paris", "gfs", 20L, "new")))

        val rows = dao.getForCity("paris").associateBy { it.modelKey }
        assertEquals(2, rows.size)
        assertEquals("new", rows.getValue("gfs").responseJson)
        assertEquals(20L, rows.getValue("gfs").fetchedAtEpochMs)
    }

    @Test
    fun replace_requested_models_removes_missing_stale_rows_and_keeps_unrequested_rows() = runTest {
        dao.upsertAll(
            listOf(
                ForecastCacheEntity("paris", "gfs", 10L, "old-gfs"),
                ForecastCacheEntity("paris", "icon", 10L, "old-icon"),
                ForecastCacheEntity("paris", "ecmwf", 10L, "keep")
            )
        )

        dao.replaceRequestedModels(
            cityId = "paris",
            requestedModelKeys = listOf("gfs", "icon"),
            entities = listOf(ForecastCacheEntity("paris", "icon", 20L, "new-icon")),
            incomingFetchedAtEpochMs = 20L
        )

        val rows = dao.getForCity("paris").associateBy { it.modelKey }
        assertEquals(setOf("icon", "ecmwf"), rows.keys)
        assertEquals("new-icon", rows.getValue("icon").responseJson)
        assertEquals("keep", rows.getValue("ecmwf").responseJson)
    }


    @Test
    fun replace_requested_models_does_not_overwrite_a_newer_overlapping_row() = runTest {
        dao.upsertAll(
            listOf(
                ForecastCacheEntity("paris", "gfs", 30L, "newer-gfs"),
                ForecastCacheEntity("paris", "icon", 10L, "old-icon")
            )
        )

        dao.replaceRequestedModels(
            cityId = "paris",
            requestedModelKeys = listOf("gfs", "icon"),
            entities = listOf(
                ForecastCacheEntity("paris", "gfs", 20L, "stale-gfs"),
                ForecastCacheEntity("paris", "icon", 20L, "fresh-icon")
            ),
            incomingFetchedAtEpochMs = 20L
        )

        val rows = dao.getForCity("paris").associateBy(ForecastCacheEntity::modelKey)
        assertEquals("newer-gfs", rows.getValue("gfs").responseJson)
        assertEquals(30L, rows.getValue("gfs").fetchedAtEpochMs)
        assertEquals("fresh-icon", rows.getValue("icon").responseJson)
        assertEquals(20L, rows.getValue("icon").fetchedAtEpochMs)
    }

    @Test
    fun delete_older_than_and_delete_city_are_scoped() = runTest {
        dao.upsertAll(
            listOf(
                ForecastCacheEntity("paris", "gfs", 10L, "a"),
                ForecastCacheEntity("paris", "icon", 30L, "b"),
                ForecastCacheEntity("lyon", "gfs", 40L, "c")
            )
        )
        dao.deleteOlderThan(20L)
        assertEquals(listOf("icon"), dao.getForCity("paris").map { it.modelKey })

        dao.deleteForCity("paris")
        assertTrue(dao.getForCity("paris").isEmpty())
        assertEquals(1, dao.getForCity("lyon").size)
    }
}
