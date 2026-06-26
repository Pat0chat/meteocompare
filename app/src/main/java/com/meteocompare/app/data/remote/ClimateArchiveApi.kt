package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.ArchiveResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Historical Weather API (archive).
 *
 * Endpoint : https://archive-api.open-meteo.com/v1/archive
 *
 * Limites de gratuité : 10 000 req/jour (partagées avec les autres endpoints
 * Open-Meteo). Un fetch de normales = 1 requête pour ~10 ans de données, ce
 * qui reste très en dessous du quota.
 */
interface ClimateArchiveApi {
    @GET("v1/archive")
    suspend fun archive(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        /** Format yyyy-MM-dd */
        @Query("start_date") startDate: String,
        /** Format yyyy-MM-dd */
        @Query("end_date") endDate: String,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto"
    ): ArchiveResponseDto
}
