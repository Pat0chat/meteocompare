package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.PreviousRunsResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Prévisions archivées à échéance fixe via l'API Open-Meteo Previous Runs.
 *
 * `_previous_day1` représente la valeur prévue 24 heures avant l'heure de
 * validité, `_previous_day2` 48 h avant et `_previous_day3` 72 h avant.
 * Ces séries servent à la fois au bootstrap du biais J+1 et à la carte
 * d'évolution de la prévision.
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

    /**
     * Même endpoint, mais demande en une seule fois les valeurs qui étaient
     * prévues 24/48/72 h avant chaque heure de validité.
     */
    @GET("v1/forecast")
    suspend fun getForecastEvolution(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("hourly") hourly: String = EVOLUTION_HOURLY_VARS,
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

        const val EVOLUTION_HOURLY_VARS =
            "temperature_2m_previous_day1,temperature_2m_previous_day2,temperature_2m_previous_day3," +
                "precipitation_previous_day1,precipitation_previous_day2,precipitation_previous_day3," +
                "wind_speed_10m_previous_day1,wind_speed_10m_previous_day2,wind_speed_10m_previous_day3"
    }
}
