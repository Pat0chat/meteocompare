package com.meteocompare.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Version 3 : ajoute les colonnes `precipMeanNormal` et `windMeanNormal` sur
 * `climate_normals` pour permettre l'affichage des références climatiques
 * pluie et vent sur les nouveaux graphes de bande de confiance.
 *
 * Stratégie de migration : `fallbackToDestructiveMigration` est configuré côté
 * `DatabaseModule` (Hilt) — Room recrée la DB en cas d'upgrade. Les caches
 * forecasts se perdent mais sont régénérables au prochain refresh, et les
 * normales seront re-fetchées à la première consultation d'une ville. Comme
 * pour la v2 : tout le state pérenne est dans DataStore (favoris, modèles
 * activés, préférences), pas dans Room.
 */
@Database(
    entities = [ForecastCacheEntity::class, ClimateNormalEntity::class],
    version = 3,
    exportSchema = false
)
abstract class MeteoCompareDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun climateNormalDao(): ClimateNormalDao
}
