package com.meteocompare.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ForecastCacheEntity::class, ClimateNormalEntity::class],
    // v2 ajoute la table climate_normals. Pas de migration fournie : on choisit
    // de laisser Room recréer la DB en cas d'upgrade (forecasts en cache se
    // perdent mais sont régénérables au prochain refresh). Acceptable car
    // tout le state pérenne est dans DataStore (favoris, modèles activés),
    // pas dans Room.
    version = 2,
    exportSchema = false
)
abstract class MeteoCompareDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun climateNormalDao(): ClimateNormalDao
}
