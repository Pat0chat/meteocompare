package com.meteocompare.app.di

import android.content.Context
import androidx.room.Room
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideForecastCacheDao(database: MeteoCompareDatabase): ForecastCacheDao =
        database.forecastCacheDao()

    @Provides
    fun provideClimateNormalDao(database: MeteoCompareDatabase): ClimateNormalDao =
        database.climateNormalDao()
}
