package com.meteocompare.app.di

import com.meteocompare.app.data.radar.RadarRepository
import com.meteocompare.app.data.radar.RainViewerRadarRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RadarModule {
    @Binds
    @Singleton
    abstract fun bindRadarRepository(impl: RainViewerRadarRepository): RadarRepository
}
