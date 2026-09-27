package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Client Retrofit pour l'Ensemble API Open-Meteo —
 * https://open-meteo.com/en/docs/ensemble-api
 *
 * Utilisé uniquement pour les modèles publiés exclusivement en ensemble
 * (`ForecastEndpoint.ENSEMBLE`, ex. Google WeatherNext 2). La réponse a la
 * même forme que celle de la Forecast API, avec en plus une série par membre :
 *
 * ```json
 * "hourly": {
 *   "time": [...],
 *   "temperature_2m": [...],            // membre de contrôle
 *   "temperature_2m_member01": [...],   // membres perturbés, ignorés
 *   ...
 * }
 * ```
 *
 * [BatchedForecastSplitter] ne lit que les clés exactes `V` / `V_<apiKey>` :
 * il extrait donc le membre de contrôle et ignore les `_memberNN` sans code
 * spécifique.
 *
 * ─── Volume ──────────────────────────────────────────────────────────────
 * Open-Meteo renvoie systématiquement tous les membres. Pour limiter la
 * réponse, seules les variables effectivement fournies par les modèles
 * d'ensemble du catalogue sont demandées (pas de rafales ni de probabilité
 * de précipitation, toujours nulles pour WeatherNext 2).
 */
interface EnsembleApi {

    /**
     * Même contrat que [OpenMeteoApi.getForecastBatched]. Le repository passe
     * le même `forecast_days` que pour la Forecast API : les deux réponses
     * partagent ainsi le même axe temporel local.
     */
    @GET("v1/ensemble")
    suspend fun getEnsembleBatched(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("hourly") hourly: String = ENSEMBLE_HOURLY_VARS,
        @Query("daily") daily: String = ENSEMBLE_DAILY_VARS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): BatchedForecastResponseDto

    companion object {
        /**
         * Sous-ensemble de [OpenMeteoApi.DEFAULT_HOURLY_VARS]. `cloud_cover`
         * total étant fourni, les couches basse/moyenne/haute (seulement
         * utilisées en repli par le splitter) ne sont pas demandées : chaque
         * variable coûte 64 séries dans la réponse.
         */
        const val ENSEMBLE_HOURLY_VARS =
            "temperature_2m,precipitation,cloud_cover," +
                "wind_speed_10m,wind_direction_10m,weather_code"

        /** Sous-ensemble de [OpenMeteoApi.DEFAULT_DAILY_VARS], même logique. */
        const val ENSEMBLE_DAILY_VARS =
            "temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "wind_speed_10m_max,wind_direction_10m_dominant,weather_code,sunrise,sunset"
    }
}
