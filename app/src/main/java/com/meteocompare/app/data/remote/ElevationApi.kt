package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.ElevationDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Altitude du terrain Open-Meteo (Copernicus 90 m), pour choisir une station de calibration. */
interface ElevationApi {
    @GET("v1/elevation")
    suspend fun getElevation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double
    ): ElevationDto
}
