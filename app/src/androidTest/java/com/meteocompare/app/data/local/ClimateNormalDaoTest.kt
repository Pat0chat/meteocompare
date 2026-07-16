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

class ClimateNormalDaoTest {
    private lateinit var database: MeteoCompareDatabase
    private lateinit var dao: ClimateNormalDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeteoCompareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.climateNormalDao()
    }

    @After fun tearDown() = database.close()

    @Test
    fun replace_for_city_is_complete_and_does_not_touch_other_cities() = runTest {
        dao.insertAll(
            listOf(
                ClimateNormalEntity("paris", 1, 1, 10.0, 2.0, computedAt = 1L),
                ClimateNormalEntity("paris", 1, 2, 11.0, 3.0, computedAt = 1L),
                ClimateNormalEntity("lyon", 1, 1, 12.0, 4.0, computedAt = 2L)
            )
        )
        dao.replaceForCity(
            "paris",
            listOf(ClimateNormalEntity("paris", 7, 15, 28.0, 17.0, 1.2, 15.0, 50L))
        )

        val paris = dao.getForCity("paris")
        assertEquals(1, paris.size)
        assertEquals(7, paris.single().month)
        assertEquals(1, dao.getForCity("lyon").size)
        assertEquals(50L, dao.getOldestComputedAt("paris"))
    }

    @Test
    fun empty_city_has_no_oldest_timestamp() = runTest {
        assertTrue(dao.getForCity("missing").isEmpty())
        assertEquals(null, dao.getOldestComputedAt("missing"))
    }
}
