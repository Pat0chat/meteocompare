package com.meteocompare.app.testutil

import com.meteocompare.app.di.RepositoryModule
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.ForecastEvolutionRepository
import com.meteocompare.app.domain.repository.MarineRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.domain.usecase.ModelWeightingStrategy
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class]
)
object TestRepositoryModule {
    @Provides @Singleton fun cityRepository(fake: FakeCityRepository): CityRepository = fake
    @Provides @Singleton fun forecastRepository(fake: FakeForecastRepository): ForecastRepository = fake
    @Provides @Singleton fun forecastEvolutionRepository(fake: FakeForecastEvolutionRepository): ForecastEvolutionRepository = fake
    @Provides @Singleton fun marineRepository(fake: FakeMarineRepository): MarineRepository = fake
    @Provides @Singleton fun preferencesRepository(fake: FakeUserPreferencesRepository): UserPreferencesRepository = fake
    @Provides @Singleton fun climateRepository(fake: FakeClimateNormalsRepository): ClimateNormalsRepository = fake
    @Provides @Singleton fun biasRepository(fake: FakeBiasSampleRepository): BiasSampleRepository = fake
    @Provides @Singleton fun weighting(): ModelWeightingStrategy = EqualWeighting()
}
