package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.VigilanceResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Endpoints publics du Worker MeteoCompare. Aucune authentification embarquée. */
interface MeteoCompareApi {
    @GET("_mcx/vigilance")
    suspend fun getVigilance(
        @Query("department") department: String,
        @Query("coast") coast: Int? = null
    ): VigilanceResponseDto
}
