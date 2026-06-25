package com.meteocompare.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ForecastCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MeteoCompareDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
}
