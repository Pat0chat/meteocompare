package com.meteocompare.app.di

import com.meteocompare.app.data.preferences.UserPreferencesRepositoryImpl
import com.meteocompare.app.data.repository.BiasSampleRepositoryImpl
import com.meteocompare.app.data.repository.CityRepositoryImpl
import com.meteocompare.app.data.repository.ClimateNormalsRepositoryImpl
import com.meteocompare.app.data.repository.ForecastRepositoryImpl
import com.meteocompare.app.domain.repository.BiasSampleRepository
import com.meteocompare.app.domain.repository.CityRepository
import com.meteocompare.app.domain.repository.ClimateNormalsRepository
import com.meteocompare.app.domain.repository.ForecastRepository
import com.meteocompare.app.domain.repository.UserPreferencesRepository
import com.meteocompare.app.domain.usecase.EqualWeighting
import com.meteocompare.app.domain.usecase.ModelWeightingStrategy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindForecastRepository(impl: ForecastRepositoryImpl): ForecastRepository

    @Binds
    @Singleton
    abstract fun bindCityRepository(impl: CityRepositoryImpl): CityRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindClimateNormalsRepository(
        impl: ClimateNormalsRepositoryImpl
    ): ClimateNormalsRepository

    @Binds
    @Singleton
    abstract fun bindBiasSampleRepository(
        impl: BiasSampleRepositoryImpl
    ): BiasSampleRepository

    @Binds
    @Singleton
    abstract fun bindWeightingStrategy(impl: EqualWeighting): ModelWeightingStrategy
}
