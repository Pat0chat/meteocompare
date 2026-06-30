package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.ForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    /**
     * Récupère les prévisions horaires + journalières pour un seul modèle.
     *
     * Pour comparer plusieurs modèles, faire N appels en parallèle avec
     * un `models` différent — cela évite de devoir parser les suffixes
     * comme `temperature_2m_icon_seamless`.
     *
     * Doc : https://open-meteo.com/en/docs
     */
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        // weather_code (WMO 4677) ajouté pour permettre l'affichage iconique
        // du temps (soleil, nuages, pluie, etc.) en plus des chiffres.
        // Disponible sur tous les modèles utilisés par l'app (AROME, ARPEGE,
        // ICON, GFS, ECMWF) — donc pas de risque d'erreur 400 sélective.
        @Query("hourly") hourly: String =
            "temperature_2m,precipitation,wind_speed_10m,weather_code",
        @Query("daily") daily: String =
            "temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max,weather_code",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): ForecastResponseDto
}
