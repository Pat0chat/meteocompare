package com.meteocompare.app.di

import com.meteocompare.app.data.remote.ClimateArchiveApi
import com.meteocompare.app.data.remote.GeocodingApi
import com.meteocompare.app.data.remote.OpenMeteoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ForecastRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeocodingRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArchiveRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @ForecastRetrofit
    fun provideForecastRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @GeocodingRetrofit
    fun provideGeocodingRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @ArchiveRetrofit
    fun provideArchiveRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            // archive-api.open-meteo.com a un timeout plus généreux côté serveur
            // (peut prendre 1-3s pour 10 ans de données). Le client OkHttp partage
            // ses timeouts (15s) ce qui reste large.
            .baseUrl("https://archive-api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOpenMeteoApi(@ForecastRetrofit retrofit: Retrofit): OpenMeteoApi =
        retrofit.create(OpenMeteoApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(@GeocodingRetrofit retrofit: Retrofit): GeocodingApi =
        retrofit.create(GeocodingApi::class.java)

    @Provides
    @Singleton
    fun provideClimateArchiveApi(@ArchiveRetrofit retrofit: Retrofit): ClimateArchiveApi =
        retrofit.create(ClimateArchiveApi::class.java)
}
