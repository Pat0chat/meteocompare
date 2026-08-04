package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Prévisions archivées à échéance fixe via l'API Open-Meteo Previous Runs.
 *
 * `_previous_day1` représente la valeur prévue 24 heures avant l'heure de
 * validité. C'est le jeu de données adapté au bootstrap du suivi local J+1 :
 * contrairement à une série historique "stitched", chaque point conserve la
 * même échéance de prévision.
 */
interface PreviousRunsApi {

    @GET("v1/forecast")
    suspend fun getPreviousDayOne(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("hourly") hourly: String = DAY_ONE_HOURLY_VARS,
        @Query("timezone") timezone: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): PreviousRunsResponseDto

    companion object {
        const val DAY_ONE_HOURLY_VARS =
            "temperature_2m_previous_day1," +
                "precipitation_previous_day1," +
                "wind_speed_10m_previous_day1"
    }
}
