package com.meteocompare.app.data.remote

import com.meteocompare.app.data.remote.dto.BatchedForecastResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Client Retrofit pour Open-Meteo — https://open-meteo.com/en/docs
 *
 * ─── Un seul chemin réseau de production ─────────────────────────────────
 *
 *   [getForecastBatched] : un ou plusieurs modèles dans une seule requête HTTP.
 *                          Les variables peuvent être suffixées par la clé du
 *                          modèle (`temperature_2m_gfs_seamless`) et sont
 *                          décomposées côté domaine par [BatchedForecastSplitter].
 *                          Le splitter gère également le repli non suffixé pour
 *                          une réponse ne contenant qu'un modèle.
 *
 * ─── Pourquoi batcher ? ─────────────────────────────────────────────────
 * L'app compare plusieurs modèles météo. Le mode batched regroupe leurs
 * variables dans une seule réponse HTTP, ce qui réduit le nombre de connexions,
 * simplifie la cohérence temporelle et évite un retry indépendant par modèle.
 *
 * Contrepartie : la granularité de retry est globale. Le splitter tolère les
 * séries vides ou partielles et conserve les modèles réellement exploitables.
 */
interface OpenMeteoApi {

    /**
     * Prévisions multi-modèles en une seule requête HTTP. La réponse porte
     * les variables sous forme SUFFIXÉE par la clé du modèle, décomposée
     * ensuite par [BatchedForecastSplitter] en une réponse normalisée par
     * modèle demandé.
     *
     * @param models Liste des clés de modèles Open-Meteo séparées par des
     *   virgules. Exemple : `"arome_france_hd,arpege_europe,gfs_seamless"`.
     *   Construite depuis `WeatherModel.entries.joinToString(",") { it.apiKey }`.
     *
     * @param forecastDays Horizon commun à tous les modèles. Il faut passer
     *   `models.maxOf { it.maxForecastDays }` (borné par le forecastDays voulu
     *   côté UI) sinon les modèles à long horizon (GFS 16j, ECMWF 10j) sont
     *   tronqués. Les modèles à horizon plus court (AROME HD 2j) retournent
     *   simplement null au-delà, ce que le mapper gère déjà proprement.
     */
    @GET("v1/forecast")
    suspend fun getForecastBatched(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String,
        @Query("hourly") hourly: String = DEFAULT_HOURLY_VARS,
        @Query("daily") daily: String = DEFAULT_DAILY_VARS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("wind_speed_unit") windSpeedUnit: String = "kmh",
        @Query("temperature_unit") temperatureUnit: String = "celsius",
        @Query("precipitation_unit") precipitationUnit: String = "mm"
    ): BatchedForecastResponseDto

    companion object {
        /**
         * Liste des variables horaires demandées. Toute variable ajoutée
         * ici doit AUSSI être ajoutée aux `HourlyVar` de [BatchedForecastSplitter]
         * sinon elle sera visible dans la réponse mais silencieusement ignorée
         * côté domaine (surcoût réseau sans bénéfice).
         */
        const val DEFAULT_HOURLY_VARS =
            "temperature_2m,precipitation,precipitation_probability,cloud_cover," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code"

        const val DEFAULT_DAILY_VARS =
            "temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max," +
                "wind_direction_10m_dominant,weather_code,sunrise,sunset"
    }
}
