package com.meteocompare.app.testutil

import com.meteocompare.app.data.radar.RadarRepository
import com.meteocompare.app.di.RadarModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RadarModule::class]
)
object TestRadarModule {
    @Provides
    @Singleton
    fun radarRepository(fake: FakeRadarRepository): RadarRepository = fake
}
