package com.meteocompare.app.di

import android.content.Context
import androidx.room.Room
import com.meteocompare.app.data.local.BiasSampleDao
import com.meteocompare.app.data.local.ClimateNormalDao
import com.meteocompare.app.data.local.ForecastCacheDao
import com.meteocompare.app.data.local.MeteoCompareDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeteoCompareDatabase =
        Room.databaseBuilder(
            context,
            MeteoCompareDatabase::class.java,
            "meteocompare.db"
        )
            // Pas de migration pour le MVP — c'est un cache, on accepte les wipes
            // lors d'un changement de schéma. Pas de donnée critique.
            // dropAllTables = true : sémantique identique au comportement legacy
            // (drop-and-recreate). Room 2.7+ demande le paramètre explicite pour
            // clarifier qu'on accepte de perdre AUSSI les tables non listées
            // dans le schéma actuel (cache orphelin).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideForecastCacheDao(database: MeteoCompareDatabase): ForecastCacheDao =
        database.forecastCacheDao()

    @Provides
    fun provideClimateNormalDao(database: MeteoCompareDatabase): ClimateNormalDao =
        database.climateNormalDao()

    @Provides
    fun provideBiasSampleDao(database: MeteoCompareDatabase): BiasSampleDao =
        database.biasSampleDao()
}