package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Prévisions archivées par échéance via l'API Open-Meteo Previous Runs.
 *
 * `_previous_day1` représente la valeur prévue 24 h avant l'heure de validité,
 * … jusqu'à `_previous_day7`. Toutes les échéances sont demandées ensemble :
 * chaque modèle ne contribuera ensuite qu'aux leads réellement présents dans
 * sa réponse.
 */
interface PreviousRunsApi {

    @GET("v1/forecast")
    suspend fun getPreviousDayOne(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("hourly") hourly: String = ALL_LEAD_HOURLY_VARS,
        @Query("timezone") timezone: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): PreviousRunsResponseDto

    companion object {
        const val MIN_LEAD_DAY = 1
        const val MAX_LEAD_DAY = 7

        val ALL_LEAD_HOURLY_VARS: String = (MIN_LEAD_DAY..MAX_LEAD_DAY)
            .flatMap { lead ->
                listOf(
                    "temperature_2m_previous_day$lead",
                    "precipitation_previous_day$lead",
                    "wind_speed_10m_previous_day$lead"
                )
            }
            .joinToString(",")

        /** Alias conservé pour les tests/consommateurs historiques. */
        val DAY_ONE_HOURLY_VARS: String = ALL_LEAD_HOURLY_VARS
    }
}
