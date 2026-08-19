package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.MarineResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface MarineApi {
    @GET("v1/marine")
    suspend fun getMarine(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = HOURLY_VARS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("cell_selection") cellSelection: String = "sea"
    ): MarineResponseDto

    companion object {
        const val HOURLY_VARS =
            "wave_height,wave_direction,wave_period,swell_wave_height,swell_wave_direction," +
                "swell_wave_period,sea_surface_temperature,sea_level_height_msl"
    }
}
